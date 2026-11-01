package ar.com.miflix.client

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
internal fun TvCollectionCard(item: TvResolvedCollection, focusKey: String, restoreKey: String?,
    width: Dp, onFocused: (String) -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    val candidates = remember(item) { item.posterCandidates }
    var imageIndex by remember(candidates) { mutableIntStateOf(0) }
    LaunchedEffect(restoreKey, focusKey) {
        if (restoreKey == focusKey) { withFrameNanos { }; requester.requestFocus() }
    }
    Box(Modifier.width(width).tvFocusTarget("collection:$focusKey").focusRequester(requester)
        .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused(focusKey) }
        .semantics { contentDescription = item.collection.nombre }
        .border(3.dp, if (focused) Color.White else Color.Transparent)
        .clickable(role = Role.Button, onClick = onClick).padding(4.dp)
        .aspectRatio(2f / 3f), contentAlignment = Alignment.Center) {
        val poster = candidates.getOrNull(imageIndex)
        if (poster != null) AsyncImage(poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
            onError = { imageIndex++ })
        else Icon(Icons.Default.Movie, null, tint = Color.LightGray, modifier = Modifier.size(32.dp))
    }
}

@Composable
internal fun TvCollectionsGrid(collections: List<TvResolvedCollection>, scroll: LazyGridState,
    restoreKey: String?, onFocused: (String) -> Unit, onSelect: (TvResolvedCollection) -> Unit, onHome: () -> Unit) {
    val emptyFocus = remember { FocusRequester() }
    var pending by remember { mutableStateOf(restoreKey?.takeIf { id -> collections.any { it.id == id } }
        ?: collections.firstOrNull()?.id) }
    LaunchedEffect(collections) {
        if (collections.isEmpty()) { withFrameNanos { }; emptyFocus.requestFocus() }
        else {
            val target = (pending ?: restoreKey)?.takeIf { id -> collections.any { it.id == id } }
                ?: collections.first().id.also { pending = it }
            pending = target
            scroll.scrollToItem(collections.indexOfFirst { it.id == target }.coerceAtLeast(0))
        }
    }
    Column(Modifier.fillMaxSize().padding(start = 100.dp, end = 28.dp, top = 24.dp)) {
        Text("Colecciones", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        if (collections.isEmpty()) {
            Text("Todavía no hay colecciones disponibles", color = Color.LightGray, modifier = Modifier.padding(vertical = 20.dp))
            TvAction("Volver a Inicio", Modifier.focusRequester(emptyFocus)) { onHome() }
        } else BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(top = 16.dp)) {
            val width = tvPosterWidth(maxWidth.value).dp
            LazyVerticalGrid(GridCells.Fixed(tvPosterColumns(maxWidth.value)), state = scroll,
                modifier = Modifier.fillMaxSize().focusGroup(), contentPadding = PaddingValues(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(TV_POSTER_GAP_DP.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(collections, key = { it.id }) { item ->
                    TvCollectionCard(item, item.id, pending, width, { pending = null; onFocused(it) }) { onSelect(item) }
                }
            }
        }
    }
}

@Composable
internal fun TvCollectionDetail(item: TvResolvedCollection?, ready: Boolean, scroll: LazyGridState,
    restoreKey: String?, onFocused: (String) -> Unit, onBack: () -> Unit, onSelect: (Title) -> Unit) {
    val backFocus = remember { FocusRequester() }
    val movies = item?.movies.orEmpty()
    var pending by remember { mutableStateOf(restoreKey?.takeIf { id -> movies.any { it.id == id } }) }
    val candidates = remember(item) { item?.backdropCandidates.orEmpty() }
    var imageIndex by remember(candidates) { mutableIntStateOf(0) }
    LaunchedEffect(item) {
        val target = pending ?: restoreKey
        val index = movies.indexOfFirst { it.id == target }
        if (index >= 0) { pending = target; scroll.scrollToItem(index) }
        else { withFrameNanos { }; backFocus.requestFocus() }
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF0B0D12))) {
        candidates.getOrNull(imageIndex)?.let { backdrop ->
            AsyncImage(backdrop, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                onError = { imageIndex++ })
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .82f)))
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 24.dp)) {
            TvAction("Volver", Modifier.focusRequester(backFocus), onClick = onBack)
            Text(item?.collection?.nombre ?: "Colección", color = Color.White,
                style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
            if (item == null) Text(if (ready) "Esta colección ya no está disponible" else "Cargando colección…", color = Color.LightGray)
            item?.collection?.descripcion?.takeIf(String::isNotBlank)?.let {
                Text(it, color = Color.LightGray, maxLines = 3, modifier = Modifier.padding(vertical = 8.dp))
            }
            if (item != null) Text("${movies.size} películas disponibles", color = Color.LightGray)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp)) {
                val width = tvPosterWidth(maxWidth.value).dp
                LazyVerticalGrid(GridCells.Fixed(tvPosterColumns(maxWidth.value)), state = scroll,
                    modifier = Modifier.fillMaxSize().focusGroup(), contentPadding = PaddingValues(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(TV_POSTER_GAP_DP.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(movies, key = { it.id }) { title ->
                        TvCard(title, title.id, pending, onFocused = { pending = null; onFocused(it) }, width = width) { onSelect(title) }
                    }
                }
            }
        }
    }
}
