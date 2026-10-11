package ar.com.miflix.client

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/** Presentation only: catalog, progress and playback callbacks belong to ClientApp. */
@Composable
internal fun TvAction(label: String, modifier: Modifier = Modifier, enabled: Boolean = true,
    onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Button(onClick, enabled = enabled, modifier = modifier
        .onFocusChanged { focused = it.isFocused }
        .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent,
            RoundedCornerShape(12.dp)),
        colors = ButtonDefaults.buttonColors(containerColor = if (focused) Color(0xFFEF4B4B)
            else Color(0xFF252B37))) { Text(label) }
}

@Composable
private fun TvCard(title: Title, focusKey: String, restoreKey: String?, subtitle: String? = null,
    fraction: Float? = null, onFocused: (String) -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (restoreKey == focusKey) requester.requestFocus() }
    Column(Modifier.width(150.dp).focusRequester(requester)
        .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused(focusKey) }
        .border(if (focused) 4.dp else 1.dp,
            if (focused) Color.White else Color(0xFF252B37), RoundedCornerShape(10.dp))
        .clip(RoundedCornerShape(10.dp))
        .background(if (focused) Color(0xFF8A2929) else Color(0xFF181C25))
        .clickable(onClick = onClick).padding(8.dp)) {
        AsyncImage(title.posterUrl ?: title.backdropUrl, title.titulo,
            Modifier.fillMaxWidth().height(154.dp), contentScale = ContentScale.Crop)
        Text(title.titulo, Modifier.padding(top = 6.dp), maxLines = 2,
            overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
        subtitle?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        fraction?.let { LinearProgressIndicator(progress = it,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) }
    }
}

@Composable
internal fun TvHome(titles: List<Title>, featured: Title?, continuing: List<ContinueWatchingItem>,
    scroll: LazyListState, restoreKey: String?, onFocused: (String) -> Unit,
    busy: Boolean, problem: String?, onRefresh: () -> Unit, onSelect: (Title) -> Unit,
    onContinue: (ContinueWatchingItem) -> Unit) {
    // Snapshot the return target; focusing another card must not steal focus back.
    val returnKey = remember { restoreKey }
    val featuredFocus = remember { FocusRequester() }
    LaunchedEffect(featured) {
        if (returnKey == null && featured != null) featuredFocus.requestFocus()
    }
    LazyColumn(state = scroll, modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("MiFlix TV", Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge,
                    color = Color(0xFFEF4B4B), fontWeight = FontWeight.Bold)
                TvAction(if (busy) "Actualizando…" else "Actualizar", enabled = !busy, onClick = onRefresh)
            }
            problem?.let { Text(it, color = Color.LightGray) }
            if (titles.isEmpty()) Text(if (busy) "Cargando catálogo…" else "Sin catálogo. Usá Actualizar.")
        }
        featured?.let { title -> item {
            Box(Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(14.dp))) {
                AsyncImage(title.backdropUrl ?: title.posterUrl, null, Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop)
                Column(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .65f)).padding(24.dp),
                    verticalArrangement = Arrangement.Center) {
                    Text("Destacado", color = Color.LightGray)
                    Text(title.titulo, style = MaterialTheme.typography.headlineLarge, maxLines = 2)
                    Text(listOfNotNull(title.year?.toString(), title.generos.orEmpty().joinToString(" · "))
                        .joinToString(" · "))
                    TvAction("Ver detalle", Modifier.focusRequester(featuredFocus), onClick = { onSelect(title) })
                }
            }
        } }
        if (continuing.isNotEmpty()) item {
            Text("Continuar viendo", style = MaterialTheme.typography.headlineSmall)
            val initial = continuing.indexOfFirst { "continue:${it.key}" == returnKey }.coerceAtLeast(0)
            LazyRow(state = rememberLazyListState(initial), modifier = Modifier.focusGroup(),
                contentPadding = PaddingValues(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(continuing, key = { it.key }) { item ->
                    TvCard(item.title, "continue:${item.key}", returnKey, item.label, item.fraction,
                        onFocused) { onContinue(item) }
                }
            }
        }
        listOf("pelicula" to "Películas", "serie" to "Series", "anime" to "Anime").forEach { (type, label) ->
            val row = titles.filter { it.tipo == type }
            if (row.isNotEmpty()) item(key = type) {
                Text(label, style = MaterialTheme.typography.headlineSmall)
                val initial = row.indexOfFirst { "$type:${it.id}" == returnKey }.coerceAtLeast(0)
                LazyRow(state = rememberLazyListState(initial), modifier = Modifier.focusGroup(),
                    contentPadding = PaddingValues(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(row, key = { it.id }) { title ->
                        TvCard(title, "$type:${title.id}", returnKey, onFocused = onFocused) { onSelect(title) }
                    }
                }
            }
        }
    }
}
