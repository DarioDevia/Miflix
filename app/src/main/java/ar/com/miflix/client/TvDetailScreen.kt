package ar.com.miflix.client

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import coil.size.Scale
import kotlinx.coroutines.launch

private val detailBase = Color(0xFF0B0D12)

@Composable
private fun artworkRequest(url: String, kind: TvArtworkKind): ImageRequest {
    val context = LocalContext.current
    return remember(context, url, kind) {
        ImageRequest.Builder(context).data(url).size(kind.width, kind.height)
            .scale(if (kind == TvArtworkKind.BACKDROP) Scale.FILL else Scale.FIT)
            .crossfade(false).build()
    }
}

@Composable
private fun DetailBackground(artwork: TvDetailArtwork, contentId: String) {
    var candidate by remember(contentId, artwork.backgrounds) { mutableIntStateOf(0) }
    var posterFailed by remember(contentId, artwork.poster) { mutableStateOf(false) }
    val background = artwork.backgrounds.getOrNull(candidate)
    Box(Modifier.fillMaxSize().background(detailBase)) {
        if (background != null) key(background) {
            val attempt = candidate
            AsyncImage(artworkRequest(background, TvArtworkKind.BACKDROP), null,
                Modifier.fillMaxSize(), alignment = Alignment.Center, contentScale = ContentScale.Crop,
                onError = { if (candidate == attempt) candidate++ })
        }
        else if (artwork.poster != null && !posterFailed) {
            AsyncImage(artworkRequest(artwork.poster, TvArtworkKind.POSTER), null,
                Modifier.align(Alignment.CenterEnd).padding(end = 48.dp).width(210.dp).height(310.dp),
                contentScale = ContentScale.Fit, onError = { posterFailed = true })
        }
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
            0f to detailBase.copy(alpha = .94f), .52f to detailBase.copy(alpha = .55f),
            1f to detailBase.copy(alpha = .03f))))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0f to Color.Transparent, .55f to detailBase.copy(alpha = .15f),
            1f to detailBase.copy(alpha = .82f))))
    }
}

@Composable
private fun DetailTitle(title: Title, logo: String?) {
    val text: @Composable () -> Unit = {
        Text(title.titulo, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    if (logo == null) text()
    else SubcomposeAsyncImage(artworkRequest(logo, TvArtworkKind.LOGO), "Logo de ${title.titulo}",
        modifier = Modifier.fillMaxWidth().height(120.dp), alignment = Alignment.CenterStart,
        contentScale = ContentScale.Fit, loading = { text() }, error = { text() },
        success = { SubcomposeAsyncImageContent() })
}

/** Keep the existing focus/action implementation, with legible detail-only colors/type. */
@Composable
private fun DetailAction(label: String, modifier: Modifier = Modifier, enabled: Boolean = true,
    onClick: () -> Unit) {
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(onPrimary = Color.White),
        typography = MaterialTheme.typography.copy(
            labelLarge = MaterialTheme.typography.labelLarge.copy(fontSize = 18.sp))) {
        TvAction(label, modifier, enabled, onClick)
    }
}

/** TV presentation only. All playback/progress/episode callbacks are the existing ones. */
@Composable
internal fun TvDetail(title: Title, validLink: (String) -> Boolean,
    onPlay: (String, String, String, EpisodeNavigationTarget?, EpisodeNavigationTarget?) -> Unit) {
    val artwork = remember(title) { tvDetailArtwork(title) }
    var seasonIndex by rememberSaveable(title.id) { mutableIntStateOf(0) }
    var synopsisOverflow by remember(title.id, title.sinopsis) { mutableStateOf(false) }
    var fullSynopsis by rememberSaveable(title.id) { mutableStateOf(false) }
    var restoreSynopsis by remember { mutableStateOf(false) }
    val actionFocus = remember(title.id) { FocusRequester() }
    val seasonFocus = remember(title.id) { FocusRequester() }
    val synopsisFocus = remember(title.id) { FocusRequester() }
    val scroll = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val seasons = title.temporadas.orEmpty()
    val directLink = title.telegramUrl?.takeIf(validLink)?.takeIf { title.tipo != "serie" }
    val synopsis = title.sinopsis?.trim().orEmpty()
    LaunchedEffect(fullSynopsis) {
        if (!fullSynopsis && restoreSynopsis) {
            // Return focus after the dialog's window has been removed.
            withFrameNanos { }
            synopsisFocus.requestFocus()
            restoreSynopsis = false
        }
    }
    if (fullSynopsis) FullSynopsis(synopsis) { fullSynopsis = false; restoreSynopsis = true }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heroHeight = maxHeight.coerceAtLeast(420.dp)
        val infoWidth = maxWidth * .58f
        DetailBackground(artwork, title.id)
        Text("MiFlix TV ${BuildConfig.VERSION_NAME}", color = Color.White.copy(alpha = .65f),
            fontSize = 12.sp, modifier = Modifier.align(Alignment.TopEnd).padding(20.dp))
        LazyColumn(state = scroll, modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
            item(key = "hero") {
                Column(Modifier.heightIn(min = heroHeight).width(infoWidth)
                    .padding(start = 48.dp, top = 32.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailTitle(title, artwork.logo)
                    val facts = tvDetailFacts(title)
                    if (facts.isNotBlank()) Text(facts, color = Color.White, fontSize = 18.sp)
                    val genres = tvDetailGenres(title)
                    if (genres.isNotBlank()) Text(genres, color = Color(0xFFD7DAE0), fontSize = 17.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (synopsis.isNotBlank()) Text(synopsis, color = Color(0xFFE5E7EB), fontSize = 18.sp,
                        maxLines = 3, overflow = TextOverflow.Ellipsis,
                        onTextLayout = { synopsisOverflow = it.hasVisualOverflow })
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (directLink != null) {
                            LaunchedEffect(title.id) { actionFocus.requestFocus() }
                            DetailAction("▶ Reproducir", Modifier.focusRequester(actionFocus)) {
                                onPlay(ProgressKeys.title(title), directLink, title.titulo, null, null)
                            }
                        } else if (seasons.isNotEmpty()) {
                            LaunchedEffect(title.id) { actionFocus.requestFocus() }
                            DetailAction("Elegir episodio", Modifier.focusRequester(actionFocus)) {
                                scope.launch { scroll.animateScrollToItem(1); seasonFocus.requestFocus() }
                            }
                        } else Text("Sin video disponible", color = Color.LightGray,
                            modifier = Modifier.padding(vertical = 12.dp))
                        if (synopsisOverflow) {
                            if (directLink == null && seasons.isEmpty())
                                LaunchedEffect(title.id) { synopsisFocus.requestFocus() }
                            DetailAction("Sinopsis completa", Modifier.focusRequester(synopsisFocus)) {
                                fullSynopsis = true
                            }
                        }
                    }
                }
            }
            if (seasons.isNotEmpty()) {
                item(key = "seasons") {
                    Column(Modifier.fillMaxWidth().background(detailBase.copy(alpha = .9f)).padding(horizontal = 48.dp)) {
                        Text("Temporadas y episodios", color = Color.White,
                            style = MaterialTheme.typography.headlineSmall)
                        LazyRow(modifier = Modifier.focusGroup().padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(seasons.size) { index ->
                                val season = seasons[index]
                                DetailAction((if (index == seasonIndex) "✓ " else "") + "Temporada ${season.numero}",
                                    if (index == 0) Modifier.focusRequester(seasonFocus) else Modifier) { seasonIndex = index }
                            }
                        }
                    }
                }
                val season = seasons.getOrNull(seasonIndex)
                season?.episodios.orEmpty().forEachIndexed { index, episode ->
                    item(key = "episode:$seasonIndex:$index") {
                        val link = episode.telegramUrl?.takeIf(validLink)
                        DetailAction("E${episode.numero} · ${episode.titulo.orEmpty()}",
                            Modifier.padding(horizontal = 48.dp), enabled = link != null) {
                            if (link != null) onPlay(ProgressKeys.episode(title, season!!, episode), link,
                                episode.titulo ?: "Episodio ${episode.numero}",
                                findPreviousPlayableEpisode(title, seasonIndex, index, validLink),
                                findNextPlayableEpisode(title, seasonIndex, index, validLink))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FullSynopsis(text: String, onDismiss: () -> Unit) {
    val closeFocus = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(Unit) { closeFocus.requestFocus() }
        Column(Modifier.fillMaxWidth(.8f).height(420.dp).background(detailBase).padding(24.dp)
            .onPreviewKeyEvent { event ->
                val direction = when (event.nativeKeyEvent.keyCode) { 19 -> -1; 20 -> 1; else -> 0 }
                if (direction == 0) false else {
                    if (event.type == KeyEventType.KeyDown) scope.launch { scroll.animateScrollBy(direction * 180f) }
                    true
                }
            }, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Sinopsis completa", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            Text(text, color = Color.White, fontSize = 18.sp,
                modifier = Modifier.weight(1f).verticalScroll(scroll))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("↑ ↓ para leer · Atrás para cerrar", color = Color.LightGray, modifier = Modifier.weight(1f))
                DetailAction("Cerrar", Modifier.focusRequester(closeFocus), onClick = onDismiss)
            }
        }
    }
}
