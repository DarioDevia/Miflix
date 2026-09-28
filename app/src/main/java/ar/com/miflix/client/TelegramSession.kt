package ar.com.miflix.client

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
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
    private val mutableProgress = MutableStateFlow(0)
    val progress = mutableProgress.asStateFlow()
    private var client: Client? = null
    private var apiId = 0
    private var apiHash = ""

    init {
        val savedId = preferences.getInt("api_id", 0)
        val savedHash = preferences.getString("api_hash", "").orEmpty()
        if (savedId > 0 && savedHash.isNotBlank()) configure(savedId, savedHash)
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
        preferences.edit().putInt("api_id", id).putString("api_hash", apiHash).apply()
        mutableState.value = State.Starting
        try {
            System.loadLibrary("tdjni")
            client = Client.create({ update ->
                when (update) {
                    is TdApi.UpdateAuthorizationState -> handleAuthorization(update.authorizationState)
                    is TdApi.UpdateFile -> {
                        val file = update.file
                        val size = file.expectedSize.takeIf { it > 0 } ?: file.size
                        if (activeFileId == file.id && size > 0) {
                            mutableProgress.value = (file.local.downloadedSize.toLong() * 100L / size)
                                .toInt().coerceIn(0, 100)
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

    @Volatile private var activeFileId: Int? = null

    private fun handleAuthorization(state: TdApi.AuthorizationState) {
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
                mutableState.value = State.NeedsApi
            }
        }
    }

    suspend fun submit(value: String) {
        require(value.isNotBlank()) { "Completá el dato solicitado por Telegram." }
        when (state.value) {
            State.Phone -> request(TdApi.SetAuthenticationPhoneNumber(value.trim(), null))
            State.Email -> request(TdApi.SetAuthenticationEmailAddress(value.trim()))
            State.EmailCode -> request(TdApi.CheckAuthenticationEmailCode(
                TdApi.EmailAddressAuthenticationCode(value.trim())
            ))
            State.Code -> request(TdApi.CheckAuthenticationCode(value.trim()))
            State.Password -> request(TdApi.CheckAuthenticationPassword(value))
            else -> error("Telegram no está esperando un código en este momento.")
        }
    }

    suspend fun obtainVideo(link: String): File {
        require(state.value is State.Ready) { "Primero iniciá sesión en Telegram." }
        val info = request(TdApi.GetMessageLinkInfo(link))
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
        activeFileId = file.id
        mutableProgress.value = 0
        try {
            val downloaded = if (file.local.isDownloadingCompleted) file
                else request(TdApi.DownloadFile(file.id, 16, 0L, 0L, true))
            val path = downloaded.local.path
            require(downloaded.local.isDownloadingCompleted && path.isNotBlank()) {
                "No se completó la descarga del video."
            }
            return File(path).also { require(it.isFile) { "No se encontró el video descargado." } }
        } finally {
            activeFileId = null
        }
    }

    suspend fun logout() {
        if (client != null) request(TdApi.LogOut())
    }

    private suspend fun <T : TdApi.Object> request(function: TdApi.Function<T>): T =
        suspendCancellableCoroutine { continuation ->
            val current = client ?: run {
                continuation.resumeWithException(IllegalStateException("Telegram no está iniciado."))
                return@suspendCancellableCoroutine
            }
            current.send(function, { response ->
                if (!continuation.isActive) return@send
                if (response is TdApi.Error) continuation.resumeWithException(
                    IllegalStateException(response.message)
                ) else {
                    @Suppress("UNCHECKED_CAST")
                    continuation.resume(response as T)
                }
            })
        }

    companion object {
        @Volatile private var instance: TelegramSession? = null
        fun get(context: Context): TelegramSession = instance ?: synchronized(this) {
            instance ?: TelegramSession(context.applicationContext).also { instance = it }
        }
    }
}
