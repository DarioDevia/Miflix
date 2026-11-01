package ar.com.miflix.client

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

internal fun collectionOrigin(screen: Screen): Screen =
    if (screen == Screen.COLLECTIONS) Screen.COLLECTIONS else Screen.HOME
internal fun collectionMovieCount(count: Int): String = if (count == 1) "1 película" else "$count películas"

@Composable
private fun CollectionImage(candidates: List<String>, modifier: Modifier, scale: ContentScale = ContentScale.Crop) {
    var index by remember(candidates) { mutableIntStateOf(0) }
    Box(modifier.background(MiFlixStyle.surface), contentAlignment = Alignment.Center) {
        Text("MiFlix", color = MiFlixStyle.accent, fontWeight = FontWeight.Bold)
        candidates.getOrNull(index)?.let { image ->
            val attempt = index
            AsyncImage(image, null, modifier = Modifier.fillMaxSize(), contentScale = scale,
                onError = { if (index == attempt) index++ })
        }
    }
}

@Composable
internal fun CollectionCard(value: ResolvedCollection, modifier: Modifier = Modifier,
    onSelect: (ResolvedCollection) -> Unit) {
    val candidates = remember(value) { value.posterCandidates }
    Column(modifier.clickable { onSelect(value) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CollectionImage(candidates, Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)))
        Text(value.collection.nombre, color = MiFlixStyle.primaryText,
            style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(collectionMovieCount(value.movies.size), color = MiFlixStyle.secondaryText,
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun FeaturedCollectionsRow(collections: List<ResolvedCollection>, onSelect: (ResolvedCollection) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Sagas destacadas", Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(collections, key = { it.id }) { CollectionCard(it, Modifier.width(126.dp), onSelect) }
        }
    }
}

@Composable
internal fun CollectionsScreen(collections: List<ResolvedCollection>, modifier: Modifier,
    scrollState: LazyGridState, onSelect: (ResolvedCollection) -> Unit) {
    if (collections.isEmpty()) {
        Column(modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Todavía no hay colecciones", style = MaterialTheme.typography.titleLarge)
            Text("Las colecciones publicadas aparecerán acá.", color = MiFlixStyle.secondaryText)
        }
    } else LazyVerticalGrid(columns = GridCells.Adaptive(126.dp), modifier = modifier.fillMaxSize(),
        state = scrollState, contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        items(collections, key = { it.id }) { CollectionCard(it, onSelect = onSelect) }
    }
}

@Composable
internal fun CollectionDetailScreen(value: ResolvedCollection?, modifier: Modifier,
    scrollState: LazyGridState, onSelectMovie: (Title) -> Unit, onReturn: () -> Unit) {
    if (value == null) {
        Column(modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
            Text("Esta colección ya no está disponible", style = MaterialTheme.typography.titleLarge)
            Text("Podés seguir explorando el catálogo.", color = MiFlixStyle.secondaryText)
            TextButton(onClick = onReturn) { Text("Volver") }
        }
        return
    }
    val backdrop = remember(value) { value.backdropCandidates }
    val poster = remember(value) { value.posterCandidates }
    LazyVerticalGrid(columns = GridCells.Adaptive(120.dp), modifier = modifier.fillMaxSize(),
        state = scrollState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "collection:header", span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(12.dp))) {
                    CollectionImage(backdrop, Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                        listOf(Color.Transparent, MiFlixStyle.background))))
                    Row(Modifier.align(Alignment.BottomStart).padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
                        CollectionImage(poster, Modifier.width(90.dp).aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(8.dp)))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(value.collection.nombre, style = MaterialTheme.typography.headlineSmall,
                                color = MiFlixStyle.primaryText, fontWeight = FontWeight.Bold)
                            Text(collectionMovieCount(value.movies.size), color = MiFlixStyle.secondaryText)
                        }
                    }
                }
                value.collection.descripcion?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MiFlixStyle.secondaryText)
                }
            }
        }
        items(value.movies, key = { it.id }) { movie ->
            MovieCard(movie, onClick = { value.selectMovie(movie.id, onSelectMovie) })
        }
    }
}
