package ar.com.miflix.client

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Scale
import kotlinx.coroutines.delay

internal data class TvDynamicHeroState(val title: Title?, val rejectBackdrop: (String) -> Unit)

/** TV Home only. No network catalog lookup, no trailer, no player or playback state. */
@Composable
internal fun rememberTvDynamicHero(titles: List<Title>, visible: Boolean, focused: Boolean): TvDynamicHeroState {
    val context = LocalContext.current.applicationContext
    val session = remember { TvHeroProcess.session }
    val preferences = remember(context) { context.getSharedPreferences("miflix_tv_hero", 0) }
    val lastIds = remember { preferences.getStringSet("previous_ids", emptySet()).orEmpty().toSet() }
    val lastGenres = remember { preferences.getStringSet("previous_genres", emptySet()).orEmpty().toSet() }
    val movies = remember(titles) {
        titles.filter { tvHeroEligibleMovie(it.tipo, it.id, it.titulo, tvDetailArtwork(it).backgrounds) }.distinctBy { it.id }
    }
    val candidates = remember(movies) { movies.map { TvHeroCandidate(it.id, tvHeroGenres(it.generos.orEmpty())) } }
    var pool by remember { mutableStateOf<List<String>>(emptyList()) }
    var currentId by remember { mutableStateOf(session.currentId) }
    var pendingRejected by remember { mutableStateOf<Set<String>>(emptySet()) }
    fun reconcile() {
        // Catalog loading after Activity recreation must not erase the process draw/index.
        if (titles.isEmpty()) { pool = emptyList(); return }
        pool = session.update(candidates, lastIds, lastGenres)
        currentId = session.currentId
        if (pool.isNotEmpty()) {
            preferences.edit().putStringSet("previous_ids", pool.toSet())
                .putStringSet("previous_genres", candidates.firstOrNull { it.id == pool.first() }?.genres.orEmpty())
                .apply()
        }
    }
    LaunchedEffect(movies) { reconcile() }
    LaunchedEffect(focused, pendingRejected) {
        if (!focused && pendingRejected.isNotEmpty()) {
            pendingRejected.forEach(session::rejectBackdrop)
            pendingRejected = emptySet()
            reconcile()
        }
    }

    val owner = LocalLifecycleOwner.current
    var resumed by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val rotating = tvHeroCanRotate(pool.size, resumed, visible, focused)
    LaunchedEffect(pool, currentId, rotating) {
        if (rotating) {
            delay(TV_HERO_INTERVAL_MS)
            currentId = session.advance()
        }
    }
    val next = movies.firstOrNull { it.id == session.nextId }
    val nextArtwork = next?.let(::tvDetailArtwork)
    val imageLoader = context.imageLoader
    DisposableEffect(nextArtwork, resumed, visible, imageLoader) {
        // Bounded: only the next backdrop/logo; cancellation follows Home/lifecycle.
        val requests = if (resumed && visible) listOfNotNull(
            nextArtwork?.backgrounds?.firstOrNull()?.let { heroImageRequest(context, it, TvArtworkKind.BACKDROP) },
            nextArtwork?.logo?.let { heroImageRequest(context, it, TvArtworkKind.LOGO) }
        ).map(imageLoader::enqueue) else emptyList()
        onDispose { requests.forEach { it.dispose() } }
    }
    return TvDynamicHeroState(movies.firstOrNull { it.id == currentId }) { id ->
        // Never replace a focused action's movie just because its decorative image failed.
        if (focused && id == currentId) pendingRejected = pendingRejected + id
        else { session.rejectBackdrop(id); reconcile() }
    }
}

private fun heroImageRequest(context: android.content.Context, url: String, kind: TvArtworkKind) =
    ImageRequest.Builder(context).data(url).size(kind.width, kind.height)
        .scale(if (kind == TvArtworkKind.BACKDROP) Scale.FILL else Scale.FIT).crossfade(false).build()

/** Animate only decorative backgrounds: action buttons never duplicate or lose focus. */
@Composable
internal fun TvDynamicHeroBackdrop(title: Title?, onUnavailable: (String) -> Unit) {
    val base = Color(0xFF080D16)
    Crossfade(targetState = title, animationSpec = tween(TV_HERO_FADE_MS), label = "tvHeroBackdrop") { movie ->
        Box(Modifier.fillMaxSize().background(base)) {
            if (movie != null) key(movie.id) {
                val artwork = remember(movie) { tvDetailArtwork(movie) }
                var candidate by remember(artwork.backgrounds) { mutableIntStateOf(0) }
                val url = artwork.backgrounds.getOrNull(candidate)
                val context = LocalContext.current
                val unavailable by rememberUpdatedState(onUnavailable)
                if (url != null) key(url) {
                    val attempt = candidate
                    fun fail() { if (candidate == attempt) candidate++ }
                    AsyncImage(remember(context, url) { heroImageRequest(context, url, TvArtworkKind.BACKDROP) },
                        null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                        onError = { fail() }, onSuccess = { result ->
                            val image = result.result.drawable
                            if (image.intrinsicWidth <= image.intrinsicHeight) fail()
                        })
                } else LaunchedEffect(movie.id) { unavailable(movie.id) }
            }
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
                0f to base.copy(alpha = .94f), .52f to base.copy(alpha = .55f), 1f to base.copy(alpha = .03f))))
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                0f to Color.Transparent, .55f to base.copy(alpha = .15f), 1f to base.copy(alpha = .82f))))
        }
    }
}
