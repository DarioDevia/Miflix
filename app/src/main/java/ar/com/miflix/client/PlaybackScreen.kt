package ar.com.miflix.client

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.launch

@Composable
internal fun PlaybackScreen(
    link: String,
    title: String,
    onOpenTelegram: () -> Unit
) {
    val tag = "MiFlixPlayback"
    val context = LocalContext.current
    val session = remember { TelegramSession.get(context) }
    val state by session.state.collectAsState()
    var video by remember(link) { mutableStateOf<TelegramVideo?>(null) }
    var failure by remember(link) { mutableStateOf<String?>(null) }
    var downloading by remember(link) { mutableStateOf(false) }
    var attempt by remember(link) { mutableIntStateOf(0) }

    LaunchedEffect(link, state is TelegramSession.State.Ready, attempt) {
        Log.d(tag, "SCREEN_EFFECT_START link=$link state=$state attempt=$attempt videoId=${video?.fileId}")
        if (state is TelegramSession.State.Ready && video == null) {
            downloading = true
            failure = null
            try {
                video = session.obtainVideo(link)
                Log.d(tag, "SCREEN_VIDEO_READY fileId=${video?.fileId} size=${video?.size}")
            } catch (error: Exception) {
                Log.e(tag, "SCREEN_RESOLVE_ERROR", error)
                failure = error.message ?: "No se pudo obtener el video de Telegram."
            } finally {
                downloading = false
            }
        }
    }

    when {
        video != null -> VideoPlayer(video!!, title)
        state is TelegramSession.State.Ready -> {
            Column(Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if (downloading) "Preparando video…" else "No se pudo reproducir",
                    style = MaterialTheme.typography.titleLarge)
                if (downloading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Conectando con Telegram. La reproducción empieza con el primer fragmento.")
                }
                if (failure != null) {
                    Text(failure!!, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { attempt++ }) { Text("Reintentar") }
                    TextButton(onClick = onOpenTelegram) { Text("Abrir publicación en Telegram") }
                }
            }
        }
        else -> TelegramLogin(
            session = session,
            state = state,
            onOpenTelegram = onOpenTelegram
        )
    }
}

@Composable
private fun TelegramLogin(
    session: TelegramSession,
    state: TelegramSession.State,
    onOpenTelegram: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var id by remember { mutableStateOf("") }
    var hash by remember { mutableStateOf("") }
    var input by remember(state) { mutableStateOf("") }
    var problem by remember(state) { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Conectar Telegram", style = MaterialTheme.typography.headlineSmall)
        Text("Iniciá sesión con una cuenta que pertenezca al canal. Esta sesión es independiente de la app oficial de Telegram.")
        when (state) {
            TelegramSession.State.NeedsApi -> {
                Text("Primero ingresá el API ID y API hash de tu aplicación de Telegram (my.telegram.org). Se guardan solo en este teléfono.")
                OutlinedTextField(id, { id = it }, label = { Text("API ID") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(hash, { hash = it }, label = { Text("API hash") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    try {
                        session.configure(id.trim().toInt(), hash)
                    } catch (error: Exception) {
                        problem = error.message ?: "API ID o hash inválidos."
                    }
                }, enabled = id.toIntOrNull() != null && hash.isNotBlank()) {
                    Text("Conectar")
                }
            }
            TelegramSession.State.Starting -> CircularProgressIndicator()
            TelegramSession.State.Phone,
            TelegramSession.State.Email,
            TelegramSession.State.EmailCode,
            TelegramSession.State.Code,
            TelegramSession.State.Password -> {
                val label = when (state) {
                    TelegramSession.State.Phone -> "Número con código de país (por ejemplo, +54…)"
                    TelegramSession.State.Email -> "Correo solicitado por Telegram"
                    TelegramSession.State.EmailCode -> "Código enviado por correo"
                    TelegramSession.State.Code -> "Código enviado por Telegram"
                    else -> "Contraseña de verificación en dos pasos"
                }
                OutlinedTextField(
                    value = input, onValueChange = { input = it },
                    label = { Text(label) },
                    singleLine = true,
                    visualTransformation = if (state is TelegramSession.State.Password)
                        PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (state is TelegramSession.State.Phone) KeyboardType.Phone
                        else KeyboardType.Text
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = {
                    submitting = true
                    problem = null
                    scope.launch {
                        try {
                            session.submit(input)
                            input = ""
                        } catch (error: Exception) {
                            problem = error.message ?: "Telegram rechazó el dato."
                        } finally {
                            submitting = false
                        }
                    }
                }, enabled = input.isNotBlank() && !submitting) { Text("Continuar") }
            }
            is TelegramSession.State.OtherDevice -> {
                Text("Telegram pide confirmar el inicio de sesión en otro dispositivo:")
                Text(state.link, color = MaterialTheme.colorScheme.primary)
                Text("Abrí Telegram en otro teléfono y confirmá la nueva sesión.")
            }
            is TelegramSession.State.Failed -> {
                Text(state.message, color = MaterialTheme.colorScheme.error)
                Button(onClick = { session.reset() }) { Text("Cambiar credenciales Telegram") }
            }
            TelegramSession.State.Ready -> Unit
        }
        if (problem != null) Text(problem!!, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onOpenTelegram) { Text("Abrir publicación en Telegram") }
    }
}

@Composable
private fun VideoPlayer(video: TelegramVideo, title: String) {
    val tag = "MiFlixPlayback"
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val player = remember(video) {
        Log.d(tag, "PLAYER_CREATE fileId=${video.fileId} size=${video.size}")
        ExoPlayer.Builder(context).build().apply {
            setMediaSource(ProgressiveMediaSource.Factory(video.factory()).createMediaSource(
                MediaItem.fromUri(Uri.parse("miflix://telegram/${video.fileId}"))
            ))
            prepare()
            playWhenReady = true
            Log.d(tag, "PLAYER_PREPARE state=$playbackState duration=$duration")
        }
    }
    var error by remember(video) { mutableStateOf<String?>(null) }
    var buffering by remember(video) { mutableStateOf(true) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(exception: PlaybackException) {
                Log.e(tag, "PLAYER_ERROR code=${exception.errorCode} name=${exception.errorCodeName} " +
                    "state=${player.playbackState} duration=${player.duration} position=${player.currentPosition}", exception)
                error = exception.cause?.message ?: exception.message ?: "No se pudo reproducir el video."
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.d(tag, "PLAYER_STATE state=$playbackState duration=${player.duration} " +
                    "position=${player.currentPosition} buffered=${player.bufferedPosition}")
                buffering = playbackState == Player.STATE_BUFFERING
            }
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                Log.d(tag, "PLAYER_TIMELINE reason=$reason windows=${timeline.windowCount} " +
                    "duration=${player.duration} seekable=${player.isCurrentMediaItemSeekable}")
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            Log.d(tag, "PLAYER_LIFECYCLE event=$event state=${player.playbackState}")
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        player.addListener(listener)
        Log.d(tag, "PLAYER_LISTENER_ATTACHED state=${player.playbackState} duration=${player.duration}")
        owner.lifecycle.addObserver(observer)
        onDispose {
            Log.w(tag, "PLAYER_DISPOSE state=${player.playbackState} duration=${player.duration}",
                Throwable("Dispose caller stack"))
            owner.lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
            Log.d(tag, "PLAYER_RELEASED_CLEAR_VIDEO fileId=${video.fileId} " +
                "videoIdentity=${System.identityHashCode(video)}")
            video.clear()
        }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        AndroidView(factory = { PlayerView(it).apply { this.player = player } },
            update = { it.player = player },
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Text(title, Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
        if (buffering && error == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            Text("Cargando fragmento desde Telegram…", Modifier.padding(16.dp))
        }
        if (error != null) Text(error!!, Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.error)
    }
}
