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
import androidx.compose.ui.input.key.*
import kotlinx.coroutines.launch
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
            else Color(0xFF252B37), contentColor = Color.White,
            disabledContentColor = Color.LightGray)) { Text(label) }
}

@Composable
internal fun TvCard(title: Title, focusKey: String, restoreKey: String?, subtitle: String? = null,
    fraction: Float? = null, onFocused: (String) -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    LaunchedEffect(restoreKey, focusKey) {
        if (restoreKey == focusKey) { withFrameNanos { }; requester.requestFocus() }
    }
    Column(Modifier.width(154.dp).focusRequester(requester)
        .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused(focusKey) }
        .border(if (focused) 4.dp else 1.dp,
            if (focused) Color.White else Color(0xFF252B37), RoundedCornerShape(10.dp))
        .clip(RoundedCornerShape(10.dp))
        .background(if (focused) Color(0xFF8A2929) else Color(0xFF181C25))
        .clickable(onClick = onClick).padding(8.dp)) {
        AsyncImage(tvArtworkUrl(title.posterUrl, TvArtworkKind.POSTER), title.titulo,
            Modifier.fillMaxWidth().aspectRatio(2f / 3f), contentScale = ContentScale.Fit)
        Text(title.titulo, Modifier.padding(top = 6.dp), maxLines = 2,
            overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, color = Color.White)
        subtitle?.let { Text(it, color = Color.LightGray, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        fraction?.let { LinearProgressIndicator(progress = it,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) }
    }
}

@Composable
internal fun TvHome(titles: List<Title>, featured: Title?, continuing: List<ContinueWatchingItem>,
    scroll: LazyListState, restoreKey: String?, onFocused: (String) -> Unit,
    busy: Boolean, problem: String?, onRefresh: () -> Unit, onSelect: (Title) -> Unit,
    onContinue: (ContinueWatchingItem) -> Unit, onPlayFeatured: (Title) -> Unit) {
    val returnKey = remember {
        restoreKey?.takeIf { key -> continuing.any { "continue:${it.key}" == key } ||
            titles.any { "${it.tipo}:${it.id}" == key } }
    }
    // Consume restoration once at screen level; lazy items may leave/re-enter
    // composition while the user moves through other rows.
    var pendingReturnKey by remember { mutableStateOf(returnKey) }
    val recordFocus: (String) -> Unit = { key ->
        if (pendingReturnKey == key) pendingReturnKey = null
        onFocused(key)
    }
    val featuredFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val firstCategory = listOf("pelicula", "serie", "anime").firstOrNull { type -> titles.any { it.tipo == type } }
    val returnToHero = Modifier.onPreviewKeyEvent { event ->
        if (featured != null && event.nativeKeyEvent.keyCode == 19) {
            if (event.type == KeyEventType.KeyDown) scope.launch {
                scroll.animateScrollToItem(0); withFrameNanos { }; featuredFocus.requestFocus()
            }; true
        } else false
    }
    LaunchedEffect(featured) {
        if (returnKey == null && featured != null) {
            scroll.scrollToItem(0)
            withFrameNanos { }
            featuredFocus.requestFocus()
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heroHeight = (maxHeight * .60f).coerceAtLeast(310.dp)
        featured?.let { TvCinemaBackdrop(remember(it) { tvDetailArtwork(it) }, it.id) }
        LazyColumn(state = scroll, modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 100.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item(key = "hero") {
                Column(Modifier.heightIn(min = heroHeight).padding(top = 22.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("MiFlix", color = Color(0xFFEF4B4B), fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineSmall)
                    Text("TV ${BuildConfig.VERSION_NAME}", color = Color.LightGray,
                        style = MaterialTheme.typography.labelMedium)
                    if (featured != null) Column(Modifier.widthIn(max = 430.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TvContentIdentity(featured, tvDetailArtwork(featured).logo)
                        Text(listOfNotNull(featured.year?.toString(), tvDetailGenres(featured)
                            .takeIf(String::isNotBlank)).joinToString(" · "), color = Color.LightGray)
                        featured.sinopsis?.takeIf(String::isNotBlank)?.let {
                            Text(it, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TvDetailAction(tvFeaturedActionLabel(featured),
                                Modifier.focusRequester(featuredFocus)) { onPlayFeatured(featured) }
                            TvDetailAction("Ver ficha") { onSelect(featured) }
                        }
                    }
                    problem?.let { Text(it, color = Color.LightGray) }
                    if (titles.isEmpty()) {
                        Text(if (busy) "Cargando catálogo…" else "Sin catálogo", color = Color.White)
                        TvAction("Actualizar", enabled = !busy, onClick = onRefresh)
                    }
                }
            }
            if (continuing.isNotEmpty()) item(key = "continue") {
                Text("Continuar viendo", color = Color.White, style = MaterialTheme.typography.titleLarge)
                val initial = continuing.indexOfFirst { "continue:${it.key}" == returnKey }.coerceAtLeast(0)
                LazyRow(state = rememberLazyListState(initial), modifier = returnToHero.focusGroup(),
                    contentPadding = PaddingValues(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(continuing, key = { it.key }) { item ->
                        TvCard(item.title, "continue:${item.key}", pendingReturnKey, item.label, item.fraction,
                            recordFocus) { onContinue(item) }
                    }
                }
            }
            listOf("pelicula" to "Películas", "serie" to "Series", "anime" to "Anime").forEach { (type, label) ->
                val row = titles.filter { it.tipo == type }
                if (row.isNotEmpty()) item(key = type) {
                    Text(label, color = Color.White, style = MaterialTheme.typography.titleLarge)
                    val initial = row.indexOfFirst { "$type:${it.id}" == returnKey }.coerceAtLeast(0)
                    LazyRow(state = rememberLazyListState(initial), modifier = (if (continuing.isEmpty() && type == firstCategory) returnToHero else Modifier).focusGroup(),
                        contentPadding = PaddingValues(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(row, key = { it.id }) { title ->
                            TvCard(title, "$type:${title.id}", pendingReturnKey, onFocused = recordFocus) { onSelect(title) }
                        }
                    }
                }
            }
        }
    }
}
