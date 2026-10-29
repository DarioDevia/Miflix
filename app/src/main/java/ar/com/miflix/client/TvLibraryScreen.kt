package ar.com.miflix.client

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun TvLibraryScreen(titles: List<Title>, watchedTab: Boolean, onTab: (Boolean) -> Unit,
    scroll: LazyGridState, restoreKey: String?, onFocused: (String) -> Unit, onSelect: (Title) -> Unit) {
    val library = LocalTvLibrary.current ?: return
    val revision = LocalTvLibraryRevision.current
    val items = remember(titles, watchedTab, library, revision) {
        if (watchedTab) library.watchedTitles(titles) else library.favorites(titles)
    }
    val tabFocus = remember { FocusRequester() }
    var pendingReturn by remember { mutableStateOf(restoreKey?.takeIf { key -> items.any { it.id == key } }) }
    LaunchedEffect(watchedTab, items) {
        val index = items.indexOfFirst { it.id == pendingReturn }
        if (index >= 0) scroll.scrollToItem(index)
        else { withFrameNanos { }; tabFocus.requestFocus() }
    }
    Column(Modifier.fillMaxSize().padding(start = 100.dp, end = 28.dp, top = 24.dp)) {
        Text("Mi Lista", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvAction(if (!watchedTab) "✓ Favoritos" else "Favoritos",
                if (!watchedTab) Modifier.focusRequester(tabFocus) else Modifier) { pendingReturn = null; onTab(false) }
            TvAction(if (watchedTab) "✓ Vistos" else "Vistos",
                if (watchedTab) Modifier.focusRequester(tabFocus) else Modifier) { pendingReturn = null; onTab(true) }
        }
        val warning = library.readOnlyReason ?: library.lastError
        if (warning != null) Text(warning, color = Color.LightGray)
        if (items.isEmpty()) Text(if (watchedTab) "Todavía no hay contenido marcado como visto"
            else "Agregá títulos a Mi Lista desde sus fichas", color = Color.White)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val width = tvPosterWidth(maxWidth.value).dp
            LazyVerticalGrid(columns = GridCells.Fixed(tvPosterColumns(maxWidth.value)), state = scroll,
                modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(TV_POSTER_GAP_DP.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.id }) { title ->
                    TvCard(title, title.id, pendingReturn,
                        subtitle = if (watchedTab) if (library.isTitleWatched(title)) "Vista" else "Episodios vistos" else null,
                        onFocused = { pendingReturn = null; onFocused(it) }, width = width) { onSelect(title) }
                }
            }
        }
    }
}

@Composable
internal fun TvLibraryTitleActions(title: Title) {
    val library = LocalTvLibrary.current ?: return
    LocalTvLibraryRevision.current
    val favorite = library.isFavorite(title.id)
    TvDetailAction(if (favorite) "Quitar de Mi Lista" else "Agregar a Mi Lista", enabled = library.canWrite) {
        library.setFavorite(title.id, !favorite)
    }
    if (title.tipo == "pelicula") {
        val watched = library.isTitleWatched(title)
        if (watched) Text("✓ Película vista", color = Color.White)
        TvDetailAction(if (watched) "Marcar como no vista" else "Marcar como vista", enabled = library.canWrite) {
            library.setTitleWatched(title, !watched)
        }
    } else if (library.hasWatchedEpisode(title)) {
        val count = title.temporadas.orEmpty().sumOf { season -> season.episodios.orEmpty().count {
            library.isWatched(ProgressKeys.episode(title, season, it))
        } }
        Text("✓ $count episodios vistos", color = Color.White)
    }
    (library.readOnlyReason ?: library.lastError)?.let { Text(it, color = Color.LightGray) }
}
