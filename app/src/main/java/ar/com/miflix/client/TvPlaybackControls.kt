package ar.com.miflix.client

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import coil.size.Scale
import kotlinx.coroutines.delay

internal const val TV_CONTROLS_TIMEOUT_MS = 4_000L
internal fun tvControlsAutoHide(visible: Boolean, playing: Boolean, blocked: Boolean) =
    visible && playing && !blocked
internal fun tvPlaybackRemoteAction(code: Int, visible: Boolean): TvRemoteAction? =
    if (visible && code == 21) TvRemoteAction.REWIND
    else if (visible && code == 22) TvRemoteAction.FORWARD
    else tvRemoteAction(code, visible)

@Composable
private fun PlaybackIcon(label: String, play: Boolean?, modifier: Modifier = Modifier,
    onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    IconButton(onClick, modifier.size(60.dp).onFocusChanged { focused = it.isFocused }
        .background(if (focused) Color(0xFFEF4B4B) else Color(0xD9252B37), CircleShape)
        .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent, CircleShape)) {
        Icon(if (play == null) Icons.Default.Settings else if (play) Icons.Default.PlayArrow
            else Icons.Default.Pause, label, Modifier.size(36.dp), tint = Color.White)
    }
}

@Composable
private fun PauseIdentity(title: String, logoUrl: String?) {
    val logo = remember(logoUrl) { tvArtworkUrl(logoUrl, TvArtworkKind.LOGO) }
    val context = LocalContext.current
    val request = remember(context, logo) { ImageRequest.Builder(context).data(logo)
        .size(800, 320).scale(Scale.FIT).crossfade(false).build() }
    Box(Modifier.padding(32.dp).width(300.dp).heightIn(max = 120.dp)
        .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .55f), Color.Transparent)))) {
        val fallback: @Composable () -> Unit = { Text(title, color = Color.White, fontSize = 26.sp) }
        if (logo == null) fallback() else SubcomposeAsyncImage(request, "Logo de $title",
            Modifier.fillMaxWidth().height(110.dp), alignment = Alignment.CenterStart,
            contentScale = ContentScale.Fit, loading = { fallback() }, error = { fallback() },
            success = { SubcomposeAsyncImageContent() })
    }
}

/** TV presentation only: callbacks retain the existing player/seek/progress engine. */
@Composable
internal fun TvPlaybackControls(title: String, playing: Boolean, position: Long, duration: Long,
    visible: Boolean, onVisible: (Boolean) -> Unit, onPlayPause: () -> Unit,
    onPlay: () -> Unit, onPause: () -> Unit, onSeek: (Long) -> Unit,
    hasTracks: Boolean, onTracks: () -> Unit, pauseTitle: String = title,
    logoUrl: String? = null, blocked: Boolean = false, paused: Boolean = !playing) {
    val rootFocus = remember { FocusRequester() }
    val pauseFocus = remember { FocusRequester() }
    val tracksFocus = remember { FocusRequester() }
    var interaction by remember { mutableIntStateOf(0) }
    LaunchedEffect(playing) { onVisible(true) }
    LaunchedEffect(visible, playing, blocked, interaction) {
        if (tvControlsAutoHide(visible, playing, blocked)) {
            delay(TV_CONTROLS_TIMEOUT_MS)
            onVisible(false)
        }
    }
    LaunchedEffect(visible) {
        withFrameNanos { }
        if (visible) pauseFocus.requestFocus() else rootFocus.requestFocus()
    }
    BackHandler(enabled = visible && !blocked) { interaction++; onVisible(false) }
    Box(Modifier.fillMaxSize().focusRequester(rootFocus).onPreviewKeyEvent { event ->
        if (blocked) return@onPreviewKeyEvent false
        val code = event.nativeKeyEvent.keyCode
        if (visible && hasTracks && code in listOf(19,20)) {
            if (event.type == KeyEventType.KeyDown) {
                interaction++
                if (code == 20) tracksFocus.requestFocus() else pauseFocus.requestFocus()
            }
            return@onPreviewKeyEvent true
        }
        val action = tvPlaybackRemoteAction(code, visible)
        if (event.type == KeyEventType.KeyDown && code in listOf(19, 20, 21, 22, 23, 66, 85, 89, 90, 126, 127))
            interaction++
        if (action == null) false else {
            if (event.type == KeyEventType.KeyDown) when (action) {
                TvRemoteAction.PLAY_PAUSE -> onPlayPause()
                TvRemoteAction.PLAY -> onPlay()
                TvRemoteAction.PAUSE -> onPause()
                TvRemoteAction.REWIND -> onSeek(-10_000L)
                TvRemoteAction.FORWARD -> onSeek(10_000L)
                TvRemoteAction.SHOW_CONTROLS -> onVisible(true)
            }
            true
        }
    }.focusable()) {
        if (paused) PauseIdentity(pauseTitle, logoUrl)
        if (visible) Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .9f))))
            .padding(horizontal = 40.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                PlaybackIcon(if (playing) "Pausar" else "Reproducir", !playing,
                    Modifier.focusRequester(pauseFocus)) { interaction++; onPlayPause() }
                Text("${formatProgressTime(position)} / ${formatProgressTime(duration.coerceAtLeast(0))}",
                    color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f))
                if (hasTracks) PlaybackIcon("Audio y subtítulos", null, Modifier.focusRequester(tracksFocus)) { interaction++; onTracks() }
            }
            LinearProgressIndicator(progress = if (duration > 0)
                (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                modifier = Modifier.fillMaxWidth(), color = Color(0xFFEF4B4B), trackColor = Color(0xFF50545C))
        }
    }
}
