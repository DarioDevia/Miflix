package ar.com.miflix.client

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
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

/** Gate, not an overlay on ClientApp: no Home/player/auth composition before completion. */
@Composable
internal fun TvStartupGate(showInitially: Boolean, content: @Composable () -> Unit) {
    if (!showInitially) { content(); return }
    val owner = LocalLifecycleOwner.current
    var done by remember { mutableStateOf(false) }
    var enteredApp by remember { mutableStateOf(false) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && done) enteredApp = true
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    when {
        enteredApp -> content() // Never unmount the app again on later pause/resume.
        !done -> {
            Box(Modifier.fillMaxSize().background(Color.Black))
            TvCinematicIntro {
                done = true
                if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) enteredApp = true
            }
        }
        else -> Box(Modifier.fillMaxSize().background(Color.Black)) // Inactive: defer app initialization.
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun TvCinematicIntro(onFinished: () -> Unit) {
    val latestFinished by rememberUpdatedState(onFinished)
    val context = LocalContext.current.applicationContext
    val playback = remember { TvIntroPlayback(context) { latestFinished() } }
    Dialog(onDismissRequest = { playback.finish("SKIP_BACK") }, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false
    )) {
        val owner = LocalLifecycleOwner.current
        val view = LocalView.current
        val focus = remember { FocusRequester() }
        DisposableEffect(playback, owner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> playback.resume()
                    Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> playback.finish("INACTIVE")
                    else -> Unit
                }
            }
            owner.lifecycle.addObserver(observer)
            playback.start(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
            onDispose {
                owner.lifecycle.removeObserver(observer)
                playback.dispose()
            }
        }
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.let {
                it.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                val bars = WindowCompat.getInsetsController(it, view)
                bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                bars.hide(WindowInsetsCompat.Type.systemBars())
            }
            // Only the intro Dialog window is affected; no changes to the Activity/player window.
            onDispose { }
        }
        LaunchedEffect(playback) {
            delay(TV_INTRO_TIMEOUT_MS)
            playback.finish("TIMEOUT")
        }
        LaunchedEffect(Unit) { withFrameNanos { }; focus.requestFocus() }
        Box(Modifier.fillMaxSize().background(Color.Black).focusRequester(focus)
            .onPreviewKeyEvent { event ->
                val key = event.nativeKeyEvent
                if (tvIntroSkipKey(key.keyCode)) {
                    if (key.action == KeyEvent.ACTION_UP) playback.finish("SKIP_KEY")
                    true
                } else false
            }.focusable()) {
            AndroidView(factory = { ctx ->
                // TV-only resource resolved by name so Mobile has no resource/dependency changes.
                val resource = ctx.resources.getIdentifier("miflix_tv_intro_player", "layout", ctx.packageName)
                if (resource == 0) {
                    android.view.View(ctx).also { it.post { playback.finish("MISSING_LAYOUT") } }
                } else try {
                    (LayoutInflater.from(ctx).inflate(resource, null) as PlayerView).apply {
                        isFocusable = false
                        isFocusableInTouchMode = false
                        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        playback.attach(this)
                    }
                } catch (_: RuntimeException) {
                    android.view.View(ctx).also { it.post { playback.finish("VIEW_ERROR") } }
                }
            }, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Separate local-file instance. Never uses TDLib, streaming ranges or PlaybackScreen state. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private class TvIntroPlayback(private val context: Context, onFinished: () -> Unit) {
    private var player: ExoPlayer? = null
    private var view: PlayerView? = null
    private var started = false
    private val lifetime = TvIntroLifetime(::release, onFinished)
    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) finish("ENDED")
        }
        override fun onPlayerError(error: PlaybackException) { finish("PLAYER_ERROR") }
    }
    fun attach(target: PlayerView) {
        if (lifetime.closed) return
        view = target
        target.player = player
    }
    fun start(resumed: Boolean) {
        if (started || lifetime.closed) return
        started = true
        try {
            val instance = ExoPlayer.Builder(context).build()
            player = instance
            instance.addListener(listener)
            instance.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            instance.repeatMode = Player.REPEAT_MODE_OFF
            instance.volume = 1f // Original audio gain, respecting system volume and mute.
            view?.player = instance
            instance.setMediaItem(MediaItem.fromUri("asset:///$TV_INTRO_ASSET"))
            instance.prepare()
            if (resumed) instance.play()
        } catch (_: RuntimeException) { finish("PREPARE_ERROR") }
    }
    fun resume() {
        if (!lifetime.closed) try { player?.play() } catch (_: RuntimeException) { finish("RESUME_ERROR") }
    }
    fun finish(reason: String) {
        if (lifetime.closed) return
        Log.d("MiFlixTvIntro", "FINISH reason=$reason") // Only fixed reason codes; no URLs or credentials.
        lifetime.finish()
    }
    fun dispose() { lifetime.dispose() }
    private fun release() {
        val instance = player
        player = null
        runCatching { view?.player = null }
        view = null
        instance?.let {
            runCatching { it.removeListener(listener) }
            runCatching { it.release() }
            Log.d("MiFlixTvIntro", "RELEASED")
        }
    }
}
