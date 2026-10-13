package ar.com.miflix.client

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
private fun TvPosterGrid(titles: List<Title>, scroll: LazyGridState, onSelect: (Title) -> Unit,
    modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
    val posterWidth = tvPosterWidth(maxWidth.value).dp
    LazyVerticalGrid(columns = GridCells.Fixed(tvPosterColumns(maxWidth.value)), state = scroll,
        modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(TV_POSTER_GAP_DP.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(titles, key = { it.id }) { title ->
            TvCard(title, title.id, null, onFocused = {}, width = posterWidth) { onSelect(title) }
        }
    }
    }
}

@Composable
internal fun TvSearch(index: CatalogSearchIndex, scroll: LazyGridState, query: String,
    onQuery: (String) -> Unit, filters: SearchFilters, onFilters: (SearchFilters) -> Unit,
    order: SearchOrder, onOrder: (SearchOrder) -> Unit, onSelect: (Title) -> Unit) {
    val results = remember(index, query, filters, order) { index.search(query, filters, order) }
    Column(Modifier.fillMaxSize().padding(start = 100.dp, end = 28.dp, top = 24.dp)) {
        Text("Buscar", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        TvEditableField(query, onQuery, "Título o género", Modifier.fillMaxWidth().padding(vertical = 12.dp), initialFocus = true)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SearchChoice("Tipo", filters.type, listOf(null to "Todos", "pelicula" to "Películas", "serie" to "Series", "anime" to "Anime"), filters.type != null) { onFilters(filters.copy(type = it)) } }
            item { SearchChoice("Género", filters.genre, listOf(null to "Todos") + index.genres.map { it.key to it.label }, filters.genre != null) { onFilters(filters.copy(genre = it)) } }
            item { SearchChoice("Año", filters.year, listOf(null to "Todos") + index.years.map { it to it.toString() }, filters.year != null) { onFilters(filters.copy(year = it)) } }
            item { SearchChoice("Puntuación", filters.minimumRating, listOf(null to "Todas", 6 to "6+", 7 to "7+", 8 to "8+", 9 to "9+"), filters.minimumRating != null) { onFilters(filters.copy(minimumRating = it)) } }
            item { TvAction(if (filters.latestReleases) "✓ Últimos estrenos" else "Últimos estrenos", enabled = index.latestYear != null) { onFilters(filters.copy(latestReleases = !filters.latestReleases)) } }
            item { SearchChoice("Orden", order, SearchOrder.entries.map { it to it.label }, false, onOrder) }
            item { TvAction("Limpiar") { onQuery(""); onFilters(SearchFilters()) } }
        }
        Text("${results.size} resultados", color = Color.LightGray, modifier = Modifier.padding(top = 12.dp))
        if (results.isEmpty()) Text("Sin resultados", color = Color.White)
        TvPosterGrid(results, scroll, onSelect, Modifier.weight(1f))
    }
}

@Composable
internal fun TvSections(titles: List<Title>, scroll: LazyGridState, category: String,
    onCategory: (String) -> Unit, onSelect: (Title) -> Unit) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; firstFocus.requestFocus() }
    Column(Modifier.fillMaxSize().padding(start = 100.dp, end = 28.dp, top = 24.dp)) {
        Text("Secciones", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        LazyRow(contentPadding = PaddingValues(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("Todos", "Películas", "Series", "Anime").forEach { name ->
                item { TvAction((if (name == category) "✓ " else "") + name,
                    if (name == "Todos") Modifier.focusRequester(firstFocus) else Modifier) { onCategory(name) } }
            }
        }
        val filtered = remember(titles, category) { titles.filter { it.matchesCategory(category) } }
        if (filtered.isEmpty()) Text("Todavía no hay títulos en esta sección", color = Color.White)
        TvPosterGrid(filtered, scroll, onSelect, Modifier.weight(1f))
    }
}
