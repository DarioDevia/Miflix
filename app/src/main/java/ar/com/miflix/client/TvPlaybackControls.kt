package ar.com.miflix.client

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp

@Composable
internal fun TvPlaybackControls(title: String, playing: Boolean, position: Long, duration: Long,
    visible: Boolean, onVisible: (Boolean) -> Unit, onPlayPause: () -> Unit,
    onPlay: () -> Unit, onPause: () -> Unit, onSeek: (Long) -> Unit,
    hasTracks: Boolean, onTracks: () -> Unit) {
    val rootFocus = remember { FocusRequester() }
    val pauseFocus = remember { FocusRequester() }
    LaunchedEffect(visible) {
        if (visible) pauseFocus.requestFocus() else rootFocus.requestFocus()
    }
    BackHandler(enabled = visible) { onVisible(false) }
    Box(Modifier.fillMaxSize().focusRequester(rootFocus).onPreviewKeyEvent { event ->
        val action = tvRemoteAction(event.nativeKeyEvent.keyCode, visible)
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
        if (visible) Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .background(Color.Black.copy(alpha = .85f)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text("${formatProgressTime(position)} / ${formatProgressTime(duration.coerceAtLeast(0))}")
            if (duration > 0) LinearProgressIndicator(
                progress = (position.toFloat() / duration).coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TvAction("−10 s") { onSeek(-10_000L) }
                TvAction(if (playing) "Pausar" else "Reproducir", Modifier.focusRequester(pauseFocus),
                    onClick = onPlayPause)
                TvAction("+10 s") { onSeek(10_000L) }
                if (hasTracks) TvAction("Audio y subtítulos", onClick = onTracks)
                TvAction("Ocultar") { onVisible(false) }
            }
            Text("Atrás: ocultar controles; nuevamente Atrás: volver al detalle.")
        }
    }
}
