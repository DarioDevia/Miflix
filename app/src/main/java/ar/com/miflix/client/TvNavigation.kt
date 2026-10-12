package ar.com.miflix.client

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun TvNavigationShell(enabled: Boolean, selected: String, onNavigate: (String) -> Unit,
    onConnect: () -> Unit, onSettings: () -> Unit, onRefresh: () -> Unit, busy: Boolean,
    content: @Composable () -> Unit) {
    if (!enabled) { content(); return }
    Box(Modifier.fillMaxSize()) {
        content()
        Column(Modifier.align(Alignment.CenterStart).padding(start = 12.dp)
            .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .85f), Color.Transparent))),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val destinations = listOf(Triple("HOME", "Inicio", Icons.Default.Home),
                Triple("SEARCH", "Buscar", Icons.Default.Search), Triple("SECTIONS", "Secciones", Icons.Default.List),
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
                        modifier = Modifier.size(48.dp).onFocusChanged { focused = it.isFocused }
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
