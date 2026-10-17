package ar.com.miflix.client

import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

/** Local, independent Player; never shares Telegram resources or the main Player's lifecycle. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun IntroPreview(onFinished: () -> Unit) {
    Dialog(onDismissRequest = onFinished, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false
    )) {
        val context = LocalContext.current
        val owner = LocalLifecycleOwner.current
        val view = LocalView.current
        val finished by rememberUpdatedState(onFinished)
        var firstFrame by remember { mutableStateOf(false) }
        val player = remember {
            runCatching { ExoPlayer.Builder(context.applicationContext).build().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
                repeatMode = Player.REPEAT_MODE_OFF
            } }.getOrNull()
        }
        if (player == null) {
            LaunchedEffect(Unit) {
                Log.w("MiFlixIntro", "INTRO_FINISH reason=CREATE_ERROR")
                finished()
            }
            return@Dialog
        }
        var playerView by remember { mutableStateOf<PlayerView?>(null) }
        var released by remember { mutableStateOf(false) }
        fun release() {
            if (released) return
            released = true
            playerView?.player = null
            player.release()
            Log.d("MiFlixIntro", "INTRO_RELEASED")
        }
        fun finish(reason: String) {
            if (released) return
            Log.d("MiFlixIntro", "INTRO_FINISH reason=$reason")
            release()
            finished()
        }
        DisposableEffect(player, owner) {
            val listener = object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    firstFrame = true
                    Log.d("MiFlixIntro", "INTRO_FIRST_FRAME")
                }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) finish("ENDED")
                }
                override fun onPlayerError(error: PlaybackException) {
                    Log.w("MiFlixIntro", "INTRO_ERROR code=${error.errorCodeName}")
                    finish("ERROR")
                }
            }
            val observer = LifecycleEventObserver { _, event ->
                // Do not keep audio or a decoder alive behind another app or a locked screen.
                if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP)
                    finish("BACKGROUND")
            }
            player.addListener(listener)
            owner.lifecycle.addObserver(observer)
            Log.d("MiFlixIntro", "INTRO_CREATE")
            try {
                player.setMediaItem(MediaItem.fromUri(Uri.parse(
                    "android.resource://${context.packageName}/${R.raw.miflix_intro}")))
                player.prepare()
                player.playWhenReady = true
            } catch (_: RuntimeException) {
                finish("PREPARE_ERROR")
            }
            onDispose {
                owner.lifecycle.removeObserver(observer)
                player.removeListener(listener)
                release()
            }
        }
        // Bound a broken decoder/local load instead of blocking entry indefinitely.
        LaunchedEffect(player) {
            delay(8_000)
            finish("TIMEOUT")
        }
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.let {
                it.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                val bars = WindowCompat.getInsetsController(it, view)
                bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                bars.hide(WindowInsetsCompat.Type.systemBars())
            }
            // The separate Dialog window is removed; the Activity window is never modified.
            onDispose { }
        }
        Box(Modifier.fillMaxSize().background(Color(0xFF0B0D12)), contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { ctx ->
                    (LayoutInflater.from(ctx).inflate(R.layout.miflix_intro_player, null) as PlayerView).apply {
                        this.player = if (released) null else player
                        playerView = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            if (!firstFrame) {
                Box(Modifier.fillMaxSize().background(Color(0xFF0B0D12)), contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.miflix_icon), "MiFlix", Modifier.size(96.dp))
                }
            }
        }
    }
}
