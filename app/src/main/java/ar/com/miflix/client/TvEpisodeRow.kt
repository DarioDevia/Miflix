package ar.com.miflix.client

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/** Identical presentation for series and anime; the caller retains playback callbacks. */
@Composable
internal fun TvEpisodeRow(episode: Episode, modifier: Modifier = Modifier,
    enabled: Boolean, watched: Boolean = false, markEnabled: Boolean = true,
    onWatchedChange: (() -> Unit)? = null, onClick: () -> Unit) {
    val info = remember(episode) { tvEpisodeInfo(episode) }
    val playFocus = remember { FocusRequester() }
    val markFocus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    var imageFailed by remember(info.image) { mutableStateOf(false) }
    Row(modifier.fillMaxWidth().focusRequester(playFocus).onPreviewKeyEvent { event ->
        if (focused && markEnabled && onWatchedChange != null && event.nativeKeyEvent.keyCode == 22) {
            if (event.type == KeyEventType.KeyDown) markFocus.requestFocus()
            true
        } else false
    }.onFocusChanged { focused = it.isFocused }
        .border(3.dp, if (focused) Color.White else Color.Transparent)
        .background(if (focused) Color(0xFF252B37) else Color(0xCC0B0D12))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(176.dp).aspectRatio(16f / 9f), contentAlignment = Alignment.Center) {
            if (info.image != null && !imageFailed) AsyncImage(info.image, null, Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit, onError = { imageFailed = true })
            else Text("Sin miniatura", color = Color.LightGray)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(info.heading, color = Color.White, style = MaterialTheme.typography.titleMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            info.duration?.let { Text(it, color = Color.LightGray) }
            info.synopsis?.let { Text(it, color = Color.LightGray, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            if (watched) Text("✓ Visto", color = Color.White)
            if (!enabled) Text("No disponible", color = Color.LightGray)
        }
        if (onWatchedChange != null) TvAction(if (watched) "Marcar como no visto" else "Marcar como visto",
            Modifier.widthIn(max = 220.dp).focusRequester(markFocus).onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.keyCode == 21 && enabled) {
                    if (event.type == KeyEventType.KeyDown) playFocus.requestFocus()
                    true
                } else false
            }, enabled = markEnabled, onClick = onWatchedChange)
    }
}
