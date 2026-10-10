package ar.com.miflix.client

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * An account session lives only on this phone. The Cloudflare catalog contains message links,
 * never Telegram session tokens or direct file addresses.
 */
internal class TelegramSession private constructor(private val context: Context) {
    private val playbackTag = "MiFlixPlayback"
    private val pendingPlayback = PendingPlaybackRequests()
    @Volatile private var connection = "Unknown"
    internal fun logPlaybackDiagnostics(event: String) {
        val now = android.os.SystemClock.elapsedRealtime()
        Log.d("MiFlixLifecycle", "TD_SNAPSHOT event=$event authorization=${state.value.javaClass.simpleName} " +
            "connection=$connection clientPresent=${client != null} generation=$generation " +
            "pending=${pendingPlayback.count()} oldestPendingMs=${pendingPlayback.oldestAgeMs(now)}")
    }
    @Volatile private var diagnosticFileId: Int? = null
    sealed interface State {
        data object NeedsApi : State
        data object Starting : State
        data object Phone : State
        data object Email : State
        data object EmailCode : State
        data object Code : State
        data object Password : State
        data class OtherDevice(val link: String) : State
        data object Ready : State
        data class Failed(val message: String) : State
    }

    private val preferences = context.getSharedPreferences("miflix_telegram", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow<State>(State.NeedsApi)
    val state = mutableState.asStateFlow()
    private var client: Client? = null
    @Volatile private var generation = 0
    private var apiId = 0
    private var apiHash = ""
    private val preconfigured = BuildConfig.TELEGRAM_API_ID > 0 &&
        BuildConfig.TELEGRAM_API_HASH.isNotBlank()

    init {
        val savedId = preferences.getInt("api_id", 0)
        val savedHash = preferences.getString("api_hash", "").orEmpty()
        if (preconfigured) configure(BuildConfig.TELEGRAM_API_ID, BuildConfig.TELEGRAM_API_HASH)
        else if (savedId > 0 && savedHash.isNotBlank()) configure(savedId, savedHash)
    }

    @Synchronized
    fun configure(id: Int, hash: String) {
        require(id > 0 && hash.isNotBlank()) { "Ingresá el API ID y el API hash de Telegram." }
        if (client != null) {
            require(id == apiId && hash == apiHash) {
                "Para cambiar las credenciales de la aplicación, cerrá la sesión de MiFlix."
            }
            return
        }
        apiId = id
        apiHash = hash.trim()
        if (!preconfigured) preferences.edit().putInt("api_id", id)
            .putString("api_hash", apiHash).apply()
        mutableState.value = State.Starting
        try {
            System.loadLibrary("tdjni")
            val myGeneration = ++generation
            Log.d("MiFlixLifecycle", "TD_CLIENT_CREATE generation=$myGeneration")
            client = Client.create({ update ->
                if (generation == myGeneration) {
                    when (update) {
                        is TdApi.UpdateAuthorizationState -> handleAuthorization(update.authorizationState)
                        is TdApi.UpdateConnectionState -> {
                            val next = update.state.javaClass.simpleName
                            if (next != connection) {
                                connection = next
                                logPlaybackDiagnostics("CONNECTION_CHANGED")
                            }
                        }
                        is TdApi.UpdateFile -> if (update.file.id == diagnosticFileId) {
                            val local = update.file.local
                            Log.d(playbackTag, "TD_UPDATE fileId=${update.file.id} " +
                                "offset=${local.downloadOffset} prefix=${local.downloadedPrefixSize} " +
                                "downloadedSize=${local.downloadedSize} active=${local.isDownloadingActive} " +
                                "complete=${local.isDownloadingCompleted} path=${local.path}")
                        }
                    }
                }
            }, { error -> mutableState.value = State.Failed(error.message ?: "Error de Telegram") },
                { error -> mutableState.value = State.Failed(error.message ?: "Error de Telegram") })
        } catch (failure: Throwable) {
            mutableState.value = State.Failed(
                "No se pudo iniciar TDLib: " + (failure.message ?: "biblioteca nativa no disponible")
            )
        }
    }

    private fun handleAuthorization(state: TdApi.AuthorizationState) {
        Log.d("MiFlixLifecycle", "TD_AUTHORIZATION type=${state.javaClass.simpleName} generation=$generation")
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> scope.launch {
                val params = TdApi.SetTdlibParameters().apply {
                    databaseDirectory = File(context.filesDir, "tdlib/database").absolutePath
                    filesDirectory = File(context.filesDir, "tdlib/files").absolutePath
                    useFileDatabase = true
                    useChatInfoDatabase = true
                    useMessageDatabase = true
                    useSecretChats = false
                    apiId = this@TelegramSession.apiId
                    apiHash = this@TelegramSession.apiHash
                    systemLanguageCode = "es"
                    deviceModel = Build.MODEL
                    systemVersion = Build.VERSION.RELEASE
                    applicationVersion = "MiFlix 0.4.0"
                }
                runCatching { request(params) }.onFailure {
                    mutableState.value = State.Failed(it.message ?: "No se pudo configurar Telegram")
                }
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> mutableState.value = State.Phone
            is TdApi.AuthorizationStateWaitEmailAddress -> mutableState.value = State.Email
            is TdApi.AuthorizationStateWaitEmailCode -> mutableState.value = State.EmailCode
            is TdApi.AuthorizationStateWaitCode -> mutableState.value = State.Code
            is TdApi.AuthorizationStateWaitPassword -> mutableState.value = State.Password
            is TdApi.AuthorizationStateWaitOtherDeviceConfirmation ->
                mutableState.value = State.OtherDevice(state.link)
            is TdApi.AuthorizationStateReady -> mutableState.value = State.Ready
            is TdApi.AuthorizationStateWaitRegistration ->
                mutableState.value = State.Failed("Esta cuenta necesita registrarse primero en Telegram.")
            is TdApi.AuthorizationStateLoggingOut, is TdApi.AuthorizationStateClosing ->
                mutableState.value = State.Starting
            is TdApi.AuthorizationStateClosed -> {
                client = null
                if (preconfigured) configure(BuildConfig.TELEGRAM_API_ID, BuildConfig.TELEGRAM_API_HASH)
                else mutableState.value = State.NeedsApi
            }
        }
    }

    suspend fun submit(value: String) {
        require(value.isNotBlank()) { "Completá el dato solicitado por Telegram." }
        when (state.value) {
            State.Phone -> request(TdApi.SetAuthenticationPhoneNumber(normalizeArgentinaPhone(value), null))
            State.Email -> request(TdApi.SetAuthenticationEmailAddress(value.trim()))
            State.EmailCode -> request(TdApi.CheckAuthenticationEmailCode(
                TdApi.EmailAddressAuthenticationCode(value.trim())
            ))
            State.Code -> request(TdApi.CheckAuthenticationCode(value.trim()))
            State.Password -> request(TdApi.CheckAuthenticationPassword(value))
            else -> error("Telegram no está esperando un código en este momento.")
        }
    }

    suspend fun obtainVideo(link: String): TelegramVideo {
        require(state.value is State.Ready) { "Primero iniciá sesión en Telegram." }
        Log.d(playbackTag, "RESOLVE_START telegramReady=${state.value is State.Ready}")
        val info = request(TdApi.GetMessageLinkInfo(link))
        Log.d(playbackTag, "RESOLVE_RESULT message=${info.message?.id} content=${info.message?.content?.javaClass?.simpleName}")
        val message = info.message ?: error(
            "Este enlace abre un tema o una publicación que no contiene un mensaje accesible."
        )
        val file = when (val content = message.content) {
            is TdApi.MessageVideo -> content.video.video
            is TdApi.MessageDocument -> {
                require(content.document.mimeType.startsWith("video/")) {
                    "La publicación contiene un archivo que no es video."
                }
                content.document.document
            }
            is TdApi.MessageAnimation -> content.animation.animation
            else -> error("La publicación no contiene un video. Revisá su enlace en MiFlix Admin.")
        }
        val size = file.size.toLong().takeIf { it > 0 }
            ?: file.expectedSize.toLong().takeIf { it > 0 }
            ?: error("Telegram no informó el tamaño del video; no se puede avanzar por rangos.")
        Log.d(playbackTag, "VIDEO_FILE fileId=${file.id} size=${file.size} expectedSize=${file.expectedSize} " +
            "remoteIdPresent=${file.remote.id.isNotBlank()} localPath=${file.local.path} " +
            "completed=${file.local.isDownloadingCompleted}")
        return TelegramVideo(file.id, size, this)
    }

    internal suspend fun downloadRange(fileId: Int, offset: Long, count: Int): ByteArray {
        require(state.value is State.Ready) { "Se cerró la sesión de Telegram." }
        val started = android.os.SystemClock.elapsedRealtime()
        diagnosticFileId = fileId
        Log.d(playbackTag, "RANGE_REQUEST fileId=$fileId offset=$offset limit=$count synchronous=true")
        try {
            val result = withTimeout(45_000) {
                request(TdApi.DownloadFile(fileId, 16, offset, count.toLong(), true))
            }
            val local = result.local
            val disk = local.path.takeIf { it.isNotBlank() }?.let { File(it) }
            Log.d(playbackTag, "RANGE_RESULT fileId=$fileId elapsedMs=${android.os.SystemClock.elapsedRealtime() - started} " +
                "offset=${local.downloadOffset} prefix=${local.downloadedPrefixSize} " +
                "downloadedSize=${local.downloadedSize} active=${local.isDownloadingActive} " +
                "complete=${local.isDownloadingCompleted} canDelete=${local.canBeDeleted} " +
                "path=${local.path} exists=${disk?.exists()} fileLength=${disk?.length()}")
            val available = local.isDownloadingCompleted ||
                (local.downloadOffset <= offset &&
                    local.downloadOffset + local.downloadedPrefixSize >= offset + count)
            require(available && local.path.isNotBlank()) {
                "Telegram no entregó el fragmento solicitado."
            }
            return java.io.RandomAccessFile(local.path, "r").use { input ->
                input.seek(offset)
                ByteArray(count).also {
                    input.readFully(it)
                    Log.d(playbackTag, "RANGE_READ fileId=$fileId offset=$offset bytes=${it.size} " +
                        "header=${if (offset == 0L) it.take(16).joinToString("") { b -> "%02x".format(b) } else "n/a"}")
                }
            }
        } catch (failure: Throwable) {
            Log.e(playbackTag, "RANGE_ERROR fileId=$fileId offset=$offset limit=$count", failure)
            throw failure
        } finally {
            // TDLib can keep sparse partial files between seeks. Drop each temporary copy
            // after moving the requested range into the bounded playback cache.
            try {
                withTimeout(5_000) { request(TdApi.DeleteFile(fileId)) }
                Log.d(playbackTag, "RANGE_DELETE fileId=$fileId success")
            } catch (failure: Throwable) {
                Log.e(playbackTag, "RANGE_DELETE_ERROR fileId=$fileId", failure)
                throw failure
            } finally {
                diagnosticFileId = null
            }
        }
    }

    suspend fun logout() {
        if (client != null) request(TdApi.LogOut())
        if (!preconfigured) reset()
    }

    @Synchronized
    fun reset() {
        Log.w(playbackTag, "SESSION_RESET", Throwable("Caller stack"))
        if (preconfigured) {
            preferences.edit().clear().apply()
            if (client == null) configure(BuildConfig.TELEGRAM_API_ID, BuildConfig.TELEGRAM_API_HASH)
            else {
                mutableState.value = State.Starting
                client?.send(TdApi.Close(), { _ -> })
            }
            return
        }
        generation++
        val old = client
        client = null
        old?.send(TdApi.Close(), { _ -> })
        preferences.edit().clear().apply()
        apiId = 0
        apiHash = ""
        mutableState.value = State.NeedsApi
    }

    private suspend fun <T : TdApi.Object> request(function: TdApi.Function<T>): T {
        val diagnostic = function is TdApi.GetMessageLinkInfo || function is TdApi.DownloadFile ||
            function is TdApi.DeleteFile
        val started = android.os.SystemClock.elapsedRealtime()
        val requestId = if (diagnostic) pendingPlayback.begin(started) else null
        val operation = function.javaClass.simpleName
        var outcome = "COMPLETE"
        if (requestId != null) {
            Log.d("MiFlixLifecycle", "TD_REQUEST_START request=$requestId operation=$operation " +
                "connection=$connection generation=$generation pending=${pendingPlayback.count()}")
        }
        try {
            return suspendCancellableCoroutine { continuation ->
                val current = client ?: run {
                    continuation.resumeWithException(IllegalStateException("Telegram no está iniciado."))
                    return@suspendCancellableCoroutine
                }
                current.send(function, { response ->
                    if (response is TdApi.Error &&
                        (function is TdApi.GetMessageLinkInfo || function is TdApi.DownloadFile ||
                            function is TdApi.DeleteFile)) {
                        Log.e(playbackTag, "TD_ERROR function=${function.javaClass.simpleName} " +
                            "code=${response.code}")
                    }
                    if (!continuation.isActive) {
                        if (requestId != null) Log.d("MiFlixLifecycle",
                            "TD_LATE_RESPONSE request=$requestId operation=$operation response=${response.javaClass.simpleName}")
                        return@send
                    }
                    if (response is TdApi.Error) continuation.resumeWithException(
                        TelegramRequestException(response.code, response.message, function.javaClass.simpleName)
                    ) else {
                        @Suppress("UNCHECKED_CAST")
                        continuation.resume(response as T)
                    }
                })
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            outcome = if (cancelled is kotlinx.coroutines.TimeoutCancellationException) "TIMEOUT" else "CANCELLED"
            throw cancelled
        } catch (failure: Exception) {
            outcome = "ERROR_${failure.javaClass.simpleName}"
            throw failure
        } finally {
            if (requestId != null) {
                pendingPlayback.end(requestId)
                Log.d("MiFlixLifecycle", "TD_REQUEST_END request=$requestId operation=$operation " +
                    "outcome=$outcome elapsedMs=${android.os.SystemClock.elapsedRealtime() - started} " +
                    "pending=${pendingPlayback.count()}")
            }
        }
    }

    companion object {
        @Volatile private var instance: TelegramSession? = null
        fun get(context: Context): TelegramSession = instance ?: synchronized(this) {
            instance ?: TelegramSession(context.applicationContext).also { instance = it }
        }
    }
}
