package ar.com.miflix.client

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun TvNavigationShell(enabled: Boolean, selected: String, onNavigate: (String) -> Unit,
    onConnect: () -> Unit, onSettings: () -> Unit, onRefresh: () -> Unit, busy: Boolean,
    content: @Composable () -> Unit) {
    if (!enabled) { content(); return }
    val memory = remember(selected) { TvFocusMemory() }
    val railEntry = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var railFocused by remember { mutableStateOf(false) }
    BackHandler(enabled = railFocused) { memory.restore() }
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalTvFocusMemory provides memory) {
            // Let text editors consume cursor-left before handling a boundary exit.
            Box(Modifier.fillMaxSize().onKeyEvent { event ->
                if (event.nativeKeyEvent.keyCode == 21) {
                    if (event.type == KeyEventType.KeyDown && !focusManager.moveFocus(FocusDirection.Left))
                        railEntry.requestFocus()
                    true
                } else false
            }) { content() }
        }
        Column(Modifier.align(Alignment.CenterStart).padding(start = 12.dp)
            .onFocusChanged { railFocused = it.hasFocus }
            .focusGroup()
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.keyCode == 22) {
                    if (event.type == KeyEventType.KeyDown) memory.restore()
                    true
                } else false
            }
            .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .85f), Color.Transparent))),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val destinations = listOf(Triple("HOME", "Inicio", Icons.Default.Home),
                Triple("SEARCH", "Buscar", Icons.Default.Search), Triple("SECTIONS", "Secciones", Icons.Default.List),
                Triple("LIBRARY", "Mi Lista", Icons.Default.Favorite),
                Triple("CONNECT", "Telegram", Icons.Default.AccountCircle),
                Triple("SETTINGS", "Configuración", Icons.Default.Settings),
                Triple("REFRESH", if (busy) "Actualizando…" else "Actualizar", Icons.Default.Refresh))
            destinations.forEach { (id, label, icon) ->
                var focused by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = {
                        when (id) { "CONNECT" -> onConnect(); "SETTINGS" -> onSettings()
                            "REFRESH" -> onRefresh(); else -> onNavigate(id) }
                    }, enabled = id != "REFRESH" || !busy,
                        modifier = (if (id == selected) Modifier.focusRequester(railEntry) else Modifier)
                            .size(48.dp).onFocusChanged { focused = it.isFocused }
                            .background(if (focused) Color(0xFFEF4B4B) else if (id == selected)
                                Color(0xAA8A2929) else Color.Transparent, RoundedCornerShape(10.dp))
                            .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(10.dp))) {
                        Icon(icon, label, tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                    if (focused) Text(label, color = Color.White, fontSize = 17.sp,
                        modifier = Modifier.offset(x = 58.dp).requiredWidth(150.dp)
                            .background(Color(0xEE0B0D12), RoundedCornerShape(8.dp)).padding(12.dp))
                }
            }
        }
    }
}
