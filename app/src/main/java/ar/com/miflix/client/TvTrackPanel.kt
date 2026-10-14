package ar.com.miflix.client

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

internal data class TvTrackPanelRow(val key: String, val section: String, val label: String,
    val selected: Boolean = false, val unavailable: String? = null, val onSelect: () -> Unit)

/** Overlay in the existing video Box: it never owns, pauses, seeks or releases the Player. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun BoxScope.TvTrackPanel(rows: List<TvTrackPanelRow>, unsupportedAudio: Boolean,
    onClose: () -> Unit) {
    val keys = rows.map { it.key } + "close"
    val requesters = remember(keys) { keys.map { FocusRequester() } }
    var focusedIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(keys) { withFrameNanos { }; requesters[focusedIndex.coerceIn(keys.indices)].requestFocus() }
    BackHandler { onClose() }
    Column(Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(.40f)
        .background(Color(0xF20D111A)).padding(20.dp)
        .onPreviewKeyEvent { event ->
            val code = event.nativeKeyEvent.keyCode
            if (code in listOf(19, 20, 21, 22)) {
                if (event.type == KeyEventType.KeyDown && code in listOf(19, 20)) {
                    val next = tvPanelNextIndex(focusedIndex, keys.size, code == 20)
                    requesters[next].requestFocus()
                }
                true // Horizontal arrows must not seek or escape into the player.
            } else false
        }) {
        Text("Audio y subtítulos", color = Color.White, style = MaterialTheme.typography.titleLarge)
        if (unsupportedAudio) Text("Este dispositivo no puede reproducir las pistas de audio de este archivo.",
            color = Color(0xFFFFD08A), modifier = Modifier.padding(vertical = 8.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            var previousSection = ""
            rows.forEachIndexed { index, row ->
                if (row.section != previousSection) Text(row.section, color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
                previousSection = row.section
                key(row.key) {
                    val bring = remember { BringIntoViewRequester() }
                    val scope = rememberCoroutineScope()
                    var focused by remember { mutableStateOf(false) }
                    Column(Modifier.fillMaxWidth().bringIntoViewRequester(bring)
                        .focusRequester(requesters[index]).onFocusChanged {
                            focused = it.isFocused
                            if (it.isFocused) {
                                focusedIndex = index
                                scope.launch { bring.bringIntoView() }
                            }
                        }.semantics { selected = row.selected }
                        .border(2.dp, if (focused) Color.White else Color.Transparent)
                        .background(if (focused) Color(0xFF303C50) else Color.Transparent)
                        // Unsupported rows remain focusable to explain why; selection is never submitted.
                        .clickable(role = Role.Button) { if (row.unavailable == null) row.onSelect() }
                        .padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text((if (row.selected) "✓  " else "    ") + row.label,
                            color = if (row.unavailable == null) Color.White else Color.LightGray)
                        row.unavailable?.let { Text(it, color = Color(0xFFFFD08A),
                            style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            var closeFocused by remember { mutableStateOf(false) }
            val closeBring = remember { BringIntoViewRequester() }
            val closeScope = rememberCoroutineScope()
            Text("Cerrar", color = Color.White,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).bringIntoViewRequester(closeBring)
                    .focusRequester(requesters.last()).onFocusChanged {
                        closeFocused = it.isFocused
                        if (it.isFocused) {
                            focusedIndex = keys.lastIndex
                            closeScope.launch { closeBring.bringIntoView() }
                        }
                    }.border(2.dp, if (closeFocused) Color.White else Color.Transparent)
                    .clickable(role = Role.Button, onClick = onClose).padding(12.dp))
        }
    }
}
