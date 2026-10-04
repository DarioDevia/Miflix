package ar.com.miflix.client

import android.content.ActivityNotFoundException
import android.content.pm.ActivityInfo
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d("MiFlixPlayback", "ORIENTATION_CHANGE orientation=${newConfig.orientation} " +
            "activity=${System.identityHashCode(this)}")
    }

    override fun onDestroy() {
        Log.d("MiFlixPlayback", "ACTIVITY_DESTROY changingConfigurations=$isChangingConfigurations " +
            "activity=${System.identityHashCode(this)}")
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = getSharedPreferences("miflix_client", MODE_PRIVATE)
        val repository = CatalogRepository(this)
        setContent {
            ClientApp(
                repository = repository,
                initialUrl = startupCatalogUrl(preferences.getString("catalog_url", null)),
                initialInvite = preferences.getString("channel_invite", "").orEmpty(),
                saveSettings = { url, invite ->
                    preferences.edit().putString("catalog_url", url).putString("channel_invite", invite).apply()
                }
            )
        }
    }
}

private object MiFlixStyle {
    val background = Color(0xFF0B0D12)
    val surface = Color(0xFF181C25)
    val raised = Color(0xFF252B37)
    val accent = Color(0xFFEF4B4B)
    val primaryText = Color(0xFFF6F7F9)
    val secondaryText = Color(0xFFB8BEC9)
}

internal enum class Screen { HOME, SEARCH, SECTIONS, DETAIL, PLAYER, SETTINGS, CONNECT }
internal fun startupCatalogUrl(savedUrl: String?): String =
    savedUrl?.trim()?.takeIf { it.isNotBlank() } ?: CatalogRepository.DEFAULT_URL

internal fun startupScreen(state: TelegramSession.State): Screen =
    if (state is TelegramSession.State.Ready) Screen.HOME else Screen.CONNECT
private data class PendingResume(val key: String, val link: String, val name: String,
    val positionMs: Long, val previousEpisode: EpisodeNavigationTarget?,
    val nextEpisode: EpisodeNavigationTarget?)
private val categories = listOf("Todos", "Películas", "Series", "Anime")

private fun Title.matchesCategory(category: String) = category == "Todos" || tipo == when (category) {
    "Películas" -> "pelicula"
    "Series" -> "serie"
    else -> "anime"
}

private fun Title.metadata(): String = listOfNotNull(
    year?.toString(), duracion?.takeIf { it.isNotBlank() }, calidad?.takeIf { it.isNotBlank() }
).joinToString(" · ")

private fun validTelegramLink(text: String): Boolean = runCatching {
    val uri = Uri.parse(text.trim())
    uri.scheme.equals("https", true) &&
        uri.host?.lowercase() in setOf("t.me", "www.t.me", "telegram.me") &&
        !uri.path.isNullOrBlank() && uri.path != "/"
}.getOrDefault(false)

private fun openTelegram(context: Context, link: String) {
    if (!validTelegramLink(link)) {
        Toast.makeText(context, "Enlace de Telegram no válido", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.trim())))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Instalá Telegram o un navegador para abrir el enlace", Toast.LENGTH_LONG).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClientApp(
    repository: CatalogRepository,
    initialUrl: String,
    initialInvite: String,
    saveSettings: (String, String) -> Unit
) {
    val context = LocalContext.current
    val telegramSession = remember { TelegramSession.get(context) }
    val telegramState by telegramSession.state.collectAsState()
    var url by remember { mutableStateOf(initialUrl) }
    var invite by remember { mutableStateOf(initialInvite) }
    var editUrl by remember { mutableStateOf(initialUrl) }
    var editInvite by remember { mutableStateOf(initialInvite) }
    var catalog by remember { mutableStateOf(Catalog("1.2.0")) }
    var source by remember { mutableStateOf("Cargando…") }
    var problem by remember { mutableStateOf<String?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var screen by rememberSaveable { mutableStateOf(startupScreen(telegramState)) }
    var returnScreen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var detailReturnScreen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var playbackLink by rememberSaveable { mutableStateOf("") }
    var playbackTitle by rememberSaveable { mutableStateOf("") }
    var playbackKey by rememberSaveable { mutableStateOf("") }
    var playbackStartPosition by rememberSaveable { mutableLongStateOf(0L) }
    var previousEpisode by remember { mutableStateOf<EpisodeNavigationTarget?>(null) }
    var nextEpisode by remember { mutableStateOf<EpisodeNavigationTarget?>(null) }
    var pendingNavigation by remember { mutableStateOf<EpisodeNavigationTarget?>(null) }
    var pendingResume by remember { mutableStateOf<PendingResume?>(null) }
    var playbackIssue by remember { mutableStateOf<PlaybackIssue?>(null) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var category by rememberSaveable { mutableStateOf("Todos") }
    var query by rememberSaveable { mutableStateOf("") }
    var searchType by rememberSaveable { mutableStateOf<String?>(null) }
    var searchGenre by rememberSaveable { mutableStateOf<String?>(null) }
    var searchYear by rememberSaveable { mutableStateOf<Int?>(null) }
    var searchRating by rememberSaveable { mutableStateOf<Int?>(null) }
    var searchLatest by rememberSaveable { mutableStateOf(false) }
    var searchOrder by rememberSaveable { mutableStateOf(SearchOrder.AZ) }
    var busy by remember { mutableStateOf(false) }
    var cacheLoaded by remember { mutableStateOf(false) }
    val homeScroll = rememberLazyListState()
    val searchScroll = rememberLazyGridState()
    val sectionsScroll = rememberLazyGridState()
    val progressStore = remember(context) {
        PlaybackProgressStore(SharedPreferencesProgressStorage(context))
    }
    var continuing by remember { mutableStateOf(emptyList<ContinueWatchingItem>()) }
    val refreshPreferences = remember(context) {
        context.getSharedPreferences("miflix_client", Context.MODE_PRIVATE)
    }
    var autoplayTrailers by remember {
        mutableStateOf(refreshPreferences.getBoolean("autoplay_trailers", true))
    }
    var lastSuccessfulUpdate by remember {
        mutableLongStateOf(refreshPreferences.getLong("catalog_last_success", 0L))
    }
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    val activity = context as? ComponentActivity
    val titles = catalog.items.orEmpty()
    val searchIndex = remember(titles) { CatalogSearchIndex(titles) }
    LaunchedEffect(searchIndex, query, searchType, searchGenre, searchYear, searchRating,
        searchLatest, searchOrder) { searchScroll.scrollToItem(0) }
    val selected = titles.firstOrNull { it.id == selectedId }
    LaunchedEffect(screen, telegramState) {
        if (screen == Screen.CONNECT && telegramState is TelegramSession.State.Ready)
            screen = Screen.HOME
    }

    fun refresh() {
        if (busy) return
        busy = true
        refreshPreferences.edit().putLong("catalog_last_check", System.currentTimeMillis()).apply()
        scope.launch {
            try {
                val loaded = repository.load(url)
                if (loaded.source == "En línea") {
                    lastSuccessfulUpdate = System.currentTimeMillis()
                    refreshPreferences.edit().putLong("catalog_last_success", lastSuccessfulUpdate).apply()
                }
                if (loaded.source == "En línea" || loaded.source == "Copia local") {
                    catalog = loaded.catalog
                }
                source = loaded.source
                problem = loaded.error
            } finally {
                busy = false
            }
        }
    }
    fun refreshIfDue() {
        if (url.isNotBlank() && CatalogRepository.shouldAutoCheck(
                System.currentTimeMillis(), refreshPreferences.getLong("catalog_last_check", 0L)
            )) refresh()
    }
    LaunchedEffect(url) {
        repository.loadCached()?.let { catalog = it; source = "Copia local" }
        cacheLoaded = true
        // La primera carga no depende de terminar la autorización de Telegram.
        if (screen == Screen.HOME || screen == Screen.CONNECT) refreshIfDue()
    }
    LaunchedEffect(screen, cacheLoaded, url) {
        if (cacheLoaded && screen == Screen.HOME) refreshIfDue()
    }
    LaunchedEffect(screen, catalog) {
        if (screen == Screen.HOME) continuing = continueWatching(titles, progressStore)
    }
    DisposableEffect(owner, url, screen, cacheLoaded, catalog) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && cacheLoaded && screen == Screen.HOME) {
                refreshIfDue()
                continuing = continueWatching(catalog.items.orEmpty(), progressStore)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    fun openDetail(title: Title) {
        detailReturnScreen = screen
        selectedId = title.id
        screen = Screen.DETAIL
    }
    fun startPlayback(key: String, link: String, name: String, positionMs: Long,
        previous: EpisodeNavigationTarget?, next: EpisodeNavigationTarget?) {
        pendingResume = null
        if (positionMs == 0L) progressStore.clear(key)
        playbackKey = key
        playbackStartPosition = positionMs
        playbackLink = link
        playbackTitle = name
        previousEpisode = previous
        nextEpisode = next
        fullscreen = false
        screen = Screen.PLAYER
    }
    fun requestPlayback(key: String, link: String, name: String,
        previous: EpisodeNavigationTarget?, next: EpisodeNavigationTarget?) {
        val position = progressStore.resumablePosition(key)
        if (position == null) startPlayback(key, link, name, 0L, previous, next)
        else pendingResume = PendingResume(key, link, name, position, previous, next)
    }
    LaunchedEffect(screen, pendingNavigation) {
        val target = pendingNavigation
        if (screen == Screen.DETAIL && target != null) {
            // Esperar a que Compose desmonte el Player anterior y ejecute
            // release() → video.clear() antes de abrir otro enlace.
            withFrameNanos { }
            if (screen != Screen.DETAIL || pendingNavigation != target) return@LaunchedEffect
            pendingNavigation = null
            val title = selected
            val previous = title?.let { findPreviousPlayableEpisode(it, target.seasonIndex,
                target.episodeIndex, ::validTelegramLink) }
            val next = title?.let { findNextPlayableEpisode(it, target.seasonIndex,
                target.episodeIndex, ::validTelegramLink) }
            Log.d("MiFlixPlayback", "EPISODE_NAV_OPEN key=${target.progressKey}")
            requestPlayback(target.progressKey, target.link, target.name, previous, next)
        }
    }
    fun openSettings() {
        returnScreen = screen
        editUrl = url
        editInvite = invite
        screen = Screen.SETTINGS
    }
    fun goBack() {
        Log.d("MiFlixPlayback", "NAV_BACK screen=$screen selectedId=$selectedId")
        if (screen == Screen.PLAYER && fullscreen) {
            fullscreen = false
            return
        }
        when (screen) {
            Screen.PLAYER -> screen = Screen.DETAIL
            Screen.DETAIL -> {
                screen = detailReturnScreen
                selectedId = null
            }
            Screen.SETTINGS -> screen = returnScreen
            Screen.CONNECT -> screen = Screen.HOME
            else -> screen = Screen.HOME
        }
    }
    BackHandler(enabled = screen != Screen.HOME) { goBack() }
    DisposableEffect(fullscreen, screen, activity) {
        if (screen == Screen.PLAYER && fullscreen && activity != null) {
            val previousOrientation = activity.requestedOrientation
            Log.d("MiFlixPlayback", "FULLSCREEN_ENTER previousOrientation=$previousOrientation")
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            onDispose {
                Log.d("MiFlixPlayback", "FULLSCREEN_EXIT restoreOrientation=$previousOrientation")
                controller.show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(activity.window, true)
                activity.requestedOrientation = previousOrientation
            }
        } else onDispose { }
    }
    if (screen == Screen.DETAIL && selected == null && source != "Cargando…" && !busy) {
        LaunchedEffect(selectedId, catalog) { goBack() }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = MiFlixStyle.accent,
        background = MiFlixStyle.background,
        surface = MiFlixStyle.background,
        surfaceVariant = MiFlixStyle.surface,
        onSurface = MiFlixStyle.primaryText,
        onSurfaceVariant = MiFlixStyle.secondaryText
    )) {
        playbackIssue?.let { issue ->
            AlertDialog(
                onDismissRequest = { playbackIssue = null },
                title = { Text(issue.title) },
                text = { Text(issue.message) },
                confirmButton = { TextButton(onClick = { playbackIssue = null }) {
                    Text("Entendido")
                } }
            )
        }
        pendingResume?.let { pending ->
            AlertDialog(
                onDismissRequest = { pendingResume = null },
                title = { Text("Retomar reproducción") },
                text = { Text("Este video tiene una reproducción pendiente.") },
                confirmButton = { TextButton(onClick = {
                    startPlayback(pending.key, pending.link, pending.name, pending.positionMs,
                        pending.previousEpisode, pending.nextEpisode)
                }) { Text("Continuar desde " + formatProgressTime(pending.positionMs)) } },
                dismissButton = { TextButton(onClick = {
                    startPlayback(pending.key, pending.link, pending.name, 0L,
                        pending.previousEpisode, pending.nextEpisode)
                }) { Text("Empezar desde el principio") } }
            )
        }
        Scaffold(
            containerColor = MiFlixStyle.background,
            topBar = {
                if (!(screen == Screen.PLAYER && fullscreen)) TopAppBar(
                    title = {
                        Text(
                            when (screen) {
                                Screen.HOME -> "MiFlix"
                                Screen.SEARCH -> "Buscar"
                                Screen.SECTIONS -> "Secciones"
                                Screen.SETTINGS -> "Configuración"
                                Screen.DETAIL -> selected?.titulo.orEmpty()
                                Screen.PLAYER -> playbackTitle
                                Screen.CONNECT -> "Conectar con Telegram"
                            },
                            color = if (screen == Screen.HOME) MiFlixStyle.accent else MiFlixStyle.primaryText,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        if (screen == Screen.DETAIL || screen == Screen.PLAYER ||
                            screen == Screen.SETTINGS || screen == Screen.CONNECT) {
                            IconButton(onClick = ::goBack) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                            }
                        }
                    },
                    actions = {
                        if (screen == Screen.HOME || screen == Screen.SEARCH || screen == Screen.SECTIONS) {
                            if (screen != Screen.SEARCH) {
                                IconButton(onClick = { screen = Screen.SEARCH }) {
                                    Icon(Icons.Default.Search, contentDescription = "Buscar")
                                }
                            }
                            IconButton(onClick = ::openSettings) {
                                Icon(Icons.Default.Settings, contentDescription = "Configuración")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MiFlixStyle.background)
                )
            },
            bottomBar = {
                if (screen == Screen.HOME || screen == Screen.SEARCH || screen == Screen.SECTIONS) {
                    NavigationBar(containerColor = MiFlixStyle.surface) {
                        listOf(
                            Triple(Screen.HOME, "Inicio", Icons.Default.Home),
                            Triple(Screen.SEARCH, "Buscar", Icons.Default.Search),
                            Triple(Screen.SECTIONS, "Secciones", Icons.Default.List)
                        ).forEach { (destination, label, icon) ->
                            NavigationBarItem(
                                selected = screen == destination,
                                onClick = { screen = destination },
                                icon = { Icon(icon, contentDescription = null) },
                                label = { Text(label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MiFlixStyle.primaryText,
                                    selectedTextColor = MiFlixStyle.primaryText,
                                    indicatorColor = MiFlixStyle.raised,
                                    unselectedIconColor = MiFlixStyle.secondaryText,
                                    unselectedTextColor = MiFlixStyle.secondaryText
                                )
                            )
                        }
                    }
                }
            }
        ) { padding ->
            when (screen) {
                Screen.HOME -> HomeScreen(
                    modifier = Modifier.padding(padding),
                    scrollState = homeScroll,
                    titles = titles,
                    problem = problem,
                    busy = busy,
                    onRefresh = { refresh() },
                    telegramConnected = telegramState is TelegramSession.State.Ready,
                    onConnect = { screen = Screen.CONNECT },
                    onSelect = ::openDetail,
                    continuing = continuing,
                    onContinue = { item ->
                        openDetail(item.title)
                        val season = item.seasonIndex
                        val episode = item.episodeIndex
                        val link = item.episode?.telegramUrl
                        if (season != null && episode != null && link != null && validTelegramLink(link)) {
                            requestPlayback(item.key, link,
                                item.episode?.titulo?.takeIf { it.isNotBlank() } ?: "Episodio ${item.episode?.numero}",
                                findPreviousPlayableEpisode(item.title, season, episode, ::validTelegramLink),
                                findNextPlayableEpisode(item.title, season, episode, ::validTelegramLink))
                        }
                    },
                    onBrowse = { category = it; screen = Screen.SECTIONS }
                )
                Screen.SEARCH -> SearchScreen(
                    modifier = Modifier.padding(padding),
                    index = searchIndex,
                    scrollState = searchScroll,
                    query = query,
                    onQueryChange = { query = it },
                    filters = SearchFilters(searchType, searchGenre, searchYear, searchRating, searchLatest),
                    onFiltersChange = {
                        searchType = it.type
                        searchGenre = it.genre
                        searchYear = it.year
                        searchRating = it.minimumRating
                        searchLatest = it.latestReleases
                    },
                    order = searchOrder,
                    onOrderChange = { searchOrder = it },
                    onSelect = ::openDetail
                )
                Screen.SECTIONS -> SectionsScreen(
                    modifier = Modifier.padding(padding),
                    titles = titles,
                    scrollState = sectionsScroll,
                    category = category,
                    onCategoryChange = { category = it },
                    onSelect = ::openDetail
                )
                Screen.DETAIL -> if (selected != null) Detail(
                    title = selected,
                    modifier = Modifier.padding(padding),
                    autoplayTrailers = autoplayTrailers && pendingResume == null && pendingNavigation == null,
                    onPlay = ::requestPlayback
                )
                // Cada episodio posee su Player/TelegramVideo; Dispose libera el anterior
                // antes de que el efecto del nuevo episodio resuelva su archivo.
                Screen.PLAYER -> key(playbackKey, playbackLink) { PlaybackScreen(
                    link = playbackLink,
                    title = playbackTitle,
                    progressKey = playbackKey,
                    startPosition = playbackStartPosition,
                    progressStore = progressStore,
                    previousEpisode = previousEpisode,
                    nextEpisode = nextEpisode,
                    onNavigateEpisode = { target ->
                        if (screen == Screen.PLAYER && pendingNavigation == null &&
                            (target == previousEpisode || target == nextEpisode)) {
                            Log.d("MiFlixPlayback", "EPISODE_NAV_REQUEST key=${target.progressKey}")
                            pendingNavigation = target
                            screen = Screen.DETAIL
                        }
                    },
                    fullscreen = fullscreen,
                    onFullscreenToggle = { fullscreen = !fullscreen },
                    onOpenTelegram = { openTelegram(context, playbackLink) },
                    onPlaybackFailure = { issue ->
                        if (screen == Screen.PLAYER) {
                            playbackIssue = issue
                            fullscreen = false
                            screen = Screen.DETAIL
                        }
                    }
                ) }
                Screen.CONNECT -> Box(Modifier.padding(padding)) { TelegramConnectScreen() }
                Screen.SETTINGS -> SettingsScreen(
                    modifier = Modifier.padding(padding),
                    url = editUrl,
                    invite = editInvite,
                    autoplayTrailers = autoplayTrailers,
                    onAutoplayTrailersChange = {
                        autoplayTrailers = it
                        refreshPreferences.edit().putBoolean("autoplay_trailers", it).apply()
                    },
                    source = source,
                    problem = problem,
                    busy = busy,
                    count = titles.size,
                    lastSuccessfulUpdate = lastSuccessfulUpdate,
                    telegramState = telegramState,
                    onUrlChange = { editUrl = it },
                    onInviteChange = { editInvite = it },
                    onRefresh = { refresh() },
                    onJoin = { openTelegram(context, editInvite) },
                    onDisconnectTelegram = {
                        if (telegramState is TelegramSession.State.Ready) {
                            scope.launch {
                                try {
                                    telegramSession.logout()
                                } catch (_: Exception) {
                                    telegramSession.reset()
                                }
                            }
                        } else telegramSession.reset()
                    },
                    onSave = {
                        val nextUrl = editUrl.trim()
                        val nextInvite = editInvite.trim()
                        val previousUrl = url
                        url = nextUrl
                        invite = nextInvite
                        saveSettings(nextUrl, nextInvite)
                        screen = returnScreen
                        if (nextUrl != previousUrl) refreshPreferences.edit()
                            .remove("catalog_last_check").apply()
                        if (nextUrl == previousUrl) refresh()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun HomeScreen(
    modifier: Modifier,
    scrollState: LazyListState,
    titles: List<Title>,
    problem: String?,
    busy: Boolean,
    onRefresh: () -> Unit,
    telegramConnected: Boolean,
    onConnect: () -> Unit,
    onSelect: (Title) -> Unit,
    continuing: List<ContinueWatchingItem>,
    onContinue: (ContinueWatchingItem) -> Unit,
    onBrowse: (String) -> Unit
) {
    val featured = titles.firstOrNull { !it.backdropUrl.isNullOrBlank() }
        ?: titles.firstOrNull { !it.posterUrl.isNullOrBlank() }
        ?: titles.firstOrNull()
    val pullState = rememberPullRefreshState(refreshing = busy, onRefresh = onRefresh)
    Box(modifier.fillMaxSize().pullRefresh(pullState)) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = scrollState,
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        if (!telegramConnected) item {
            Surface(color = MiFlixStyle.surface, shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Conectá tu cuenta de Telegram para reproducir",
                        Modifier.weight(1f), color = MiFlixStyle.primaryText, fontSize = 14.sp)
                    TextButton(onClick = onConnect) { Text("Conectar") }
                }
            }
        }
        if (problem != null && titles.isNotEmpty()) {
            item {
                Surface(
                    color = MiFlixStyle.surface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text("No se pudo actualizar · Mostrando la copia guardada",
                        Modifier.padding(12.dp), color = MiFlixStyle.secondaryText, fontSize = 13.sp)
                }
            }
        }
        if (titles.isEmpty()) {
            item {
                EmptyState(
                    title = if (busy) "Cargando catálogo…" else "Tu videoteca empieza acá",
                    message = when {
                        busy -> "Buscando novedades de MiFlix."
                        problem != null -> "No pudimos cargar el catálogo. " + problem
                        else -> "Todavía no hay títulos en el catálogo."
                    },
                    action = if (!busy && problem != null) "Reintentar" else null,
                    onAction = onRefresh
                )
            }
        } else {
            if (featured != null) item { Hero(featured, onClick = { onSelect(featured) }) }
            if (continuing.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Continuar viendo", Modifier.padding(horizontal = 16.dp),
                        fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(continuing, key = { it.key }) { item ->
                            Column(Modifier.width(126.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                MovieCard(item.title, onClick = { onContinue(item) })
                                item.label?.let { Text(it, fontSize = 12.sp, maxLines = 2,
                                    color = MiFlixStyle.secondaryText, overflow = TextOverflow.Ellipsis) }
                                item.fraction?.let { LinearProgressIndicator(progress = it,
                                    modifier = Modifier.fillMaxWidth()) }
                            }
                        }
                    }
                }
            }
            item {
                Text("Explorá tu videoteca", Modifier.padding(horizontal = 16.dp),
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MiFlixStyle.primaryText)
            }
            listOf("Películas", "Series", "Anime").forEach { name ->
                val group = titles.filter { it.matchesCategory(name) }
                if (group.isNotEmpty()) item {
                    ContentRow(name, group, onSelect, onMore = { onBrowse(name) })
                }
            }
        }
    }
        PullRefreshIndicator(busy, pullState, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun Hero(title: Title, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(350.dp).padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp)).background(MiFlixStyle.surface)
            .clickable(onClick = onClick)
    ) {
        if (!title.backdropUrl.isNullOrBlank()) {
            AsyncImage(title.backdropUrl, null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize())
        } else if (!title.posterUrl.isNullOrBlank()) {
            AsyncImage(title.posterUrl, null, contentScale = ContentScale.Fit,
                modifier = Modifier.align(Alignment.CenterEnd).width(215.dp).fillMaxHeight())
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(Color.Transparent, Color(0x660B0D12), MiFlixStyle.background)
        )))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            listOf(Color(0xBB0B0D12), Color.Transparent)
        )))
        Column(
            Modifier.align(Alignment.BottomStart).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("DESTACADO EN MIFLIX", color = MiFlixStyle.accent,
                fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(title.titulo, color = MiFlixStyle.primaryText, fontSize = 30.sp,
                lineHeight = 34.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
            val info = listOfNotNull(
                title.year?.toString(),
                title.generos.orEmpty().take(2).joinToString(" · ").takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            if (info.isNotBlank()) Text(info, color = MiFlixStyle.primaryText,
                fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Button(onClick = onClick, colors = ButtonDefaults.buttonColors(
                containerColor = MiFlixStyle.primaryText, contentColor = MiFlixStyle.background
            )) { Text("Ver ficha", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun ContentRow(
    label: String, titles: List<Title>, onSelect: (Title) -> Unit, onMore: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                color = MiFlixStyle.primaryText)
            TextButton(onClick = onMore) { Text("Ver todo", color = MiFlixStyle.secondaryText) }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(titles, key = { it.id }) { title ->
                MovieCard(title, Modifier.width(126.dp), onClick = { onSelect(title) })
            }
        }
    }
}

@Composable
private fun MovieCard(title: Title, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(8.dp)).background(MiFlixStyle.surface),
            contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("M", color = MiFlixStyle.accent, fontWeight = FontWeight.Black, fontSize = 38.sp)
                Text("MiFlix", color = MiFlixStyle.secondaryText, fontSize = 12.sp)
            }
            if (!title.posterUrl.isNullOrBlank()) AsyncImage(title.posterUrl, null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Text(title.titulo, color = MiFlixStyle.primaryText, fontSize = 14.sp,
            lineHeight = 18.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SearchScreen(
    modifier: Modifier, index: CatalogSearchIndex, scrollState: LazyGridState, query: String,
    onQueryChange: (String) -> Unit, filters: SearchFilters,
    onFiltersChange: (SearchFilters) -> Unit, order: SearchOrder,
    onOrderChange: (SearchOrder) -> Unit, onSelect: (Title) -> Unit
) {
    val results = remember(index, query, filters, order) {
        index.search(query, filters, order)
    }
    val types = listOf(null to "Todos", "pelicula" to "Películas", "serie" to "Series", "anime" to "Anime")
    val genreOptions = remember(index) { listOf(null to "Todos") + index.genres.map { it.key to it.label } }
    val yearOptions = remember(index) { listOf(null to "Todos") + index.years.map { it to it.toString() } }
    Column(modifier.fillMaxSize()) {
        OutlinedTextField(query, onQueryChange,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("Películas, series y anime") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            shape = RoundedCornerShape(12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { SearchChoice("Tipo", filters.type, types, filters.type != null) {
                onFiltersChange(filters.copy(type = it))
            } }
            item { SearchChoice("Género", filters.genre, genreOptions, filters.genre != null) {
                onFiltersChange(filters.copy(genre = it))
            } }
            item { SearchChoice("Año", filters.year, yearOptions, filters.year != null) {
                onFiltersChange(filters.copy(year = it))
            } }
            item { SearchChoice("Puntuación", filters.minimumRating,
                listOf(null to "Todas", 6 to "6+", 7 to "7+", 8 to "8+", 9 to "9+"),
                filters.minimumRating != null) { onFiltersChange(filters.copy(minimumRating = it)) } }
            item { FilterChip(selected = filters.latestReleases,
                enabled = index.latestYear != null,
                onClick = { onFiltersChange(filters.copy(latestReleases = !filters.latestReleases)) },
                label = { Text(if (filters.latestReleases && index.latestYear != null)
                    "Últimos estrenos (${index.latestYear - 1}–${index.latestYear})" else "Últimos estrenos") }) }
            item { SearchChoice("Orden", order, SearchOrder.entries.map { it to it.label }, false,
                onOrderChange) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${results.size} resultados · ${order.label}", Modifier.weight(1f),
                color = MiFlixStyle.secondaryText, fontSize = 13.sp)
            if (filters.active || query.isNotBlank()) TextButton(onClick = {
                onQueryChange("")
                onFiltersChange(SearchFilters())
            }) { Text("Limpiar") }
        }
        if (query.isBlank() && !filters.active) Text("Explorá por filtros o buscá un título o género.",
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = MiFlixStyle.secondaryText, fontSize = 13.sp)
        if (results.isEmpty()) EmptyState("Sin resultados", "Probá con otro texto o cambiá los filtros.")
        else MovieGrid(results, onSelect, Modifier.weight(1f), scrollState)
    }
}

@Composable
private fun <T> SearchChoice(label: String, value: T, options: List<Pair<T, String>>,
    active: Boolean, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(selected = active, onClick = { expanded = true },
            label = { Text("$label: ${options.firstOrNull { it.first == value }?.second ?: value}") })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 280.dp)) {
            options.forEach { (option, name) ->
                DropdownMenuItem(text = { Text(if (option == value) "$name ✓" else name) },
                    onClick = { expanded = false; onSelect(option) })
            }
        }
    }
}

@Composable
private fun SectionsScreen(
    modifier: Modifier, titles: List<Title>, scrollState: LazyGridState, category: String,
    onCategoryChange: (String) -> Unit, onSelect: (Title) -> Unit
) {
    val filtered = remember(titles, category) { titles.filter { it.matchesCategory(category) } }
    Column(modifier.fillMaxSize()) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { name ->
                FilterChip(selected = category == name, onClick = { onCategoryChange(name) },
                    label = { Text(name) }, colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MiFlixStyle.primaryText,
                        selectedLabelColor = MiFlixStyle.background))
            }
        }
        if (filtered.isEmpty()) EmptyState("Todavía no hay títulos",
            "Cuando MiFlix Admin publique contenido de esta sección, aparecerá acá.")
        else MovieGrid(filtered, onSelect, Modifier.weight(1f), scrollState)
    }
}

@Composable
private fun MovieGrid(
    titles: List<Title>, onSelect: (Title) -> Unit, modifier: Modifier = Modifier,
    scrollState: LazyGridState
) {
    LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 120.dp),
        modifier = modifier.fillMaxWidth(),
        state = scrollState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        items(titles, key = { it.id }) { title ->
            MovieCard(title, onClick = { onSelect(title) })
        }
    }
}

@Composable
private fun EmptyState(
    title: String, message: String, action: String? = null, onAction: () -> Unit = {}
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = MiFlixStyle.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(message, color = MiFlixStyle.secondaryText, fontSize = 14.sp)
        if (action != null) Button(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier, url: String, invite: String, source: String,
    problem: String?, busy: Boolean, count: Int,
    autoplayTrailers: Boolean, onAutoplayTrailersChange: (Boolean) -> Unit,
    lastSuccessfulUpdate: Long,
    telegramState: TelegramSession.State,
    onUrlChange: (String) -> Unit, onInviteChange: (String) -> Unit,
    onRefresh: () -> Unit, onJoin: () -> Unit,
    onDisconnectTelegram: () -> Unit, onSave: () -> Unit
) {
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    val urlOk = url.isBlank() || runCatching {
        val uri = Uri.parse(url.trim())
        uri.scheme.equals("https", true) && !uri.host.isNullOrBlank()
    }.getOrDefault(false)
    val inviteOk = invite.isBlank() || validTelegramLink(invite)
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { SectionHeading("Catálogo") }
        item { Text("La última copia válida queda disponible sin conexión. MiFlix busca cambios al abrir y al volver a Inicio.", color = MiFlixStyle.secondaryText) }
        item {
            Text(if (lastSuccessfulUpdate > 0L) "Última actualización: " +
                java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,
                    java.text.DateFormat.SHORT).format(java.util.Date(lastSuccessfulUpdate))
                else "Aún no se actualizó desde Internet", color = MiFlixStyle.secondaryText,
                fontSize = 13.sp)
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(source + " · " + count + " títulos", Modifier.weight(1f),
                    color = MiFlixStyle.secondaryText, fontSize = 13.sp)
                IconButton(onClick = onRefresh, enabled = !busy) {
                    Icon(Icons.Default.Refresh, contentDescription = "Actualizar catálogo")
                }
            }
        }
        if (problem != null) item { Text(problem, color = MaterialTheme.colorScheme.error) }
        item { SectionHeading("Reproducción") }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Reproducir trailers automáticamente", Modifier.weight(1f),
                    color = MiFlixStyle.primaryText)
                Switch(checked = autoplayTrailers, onCheckedChange = onAutoplayTrailersChange)
            }
        }
        item { SectionHeading("Canal de Telegram") }
        item { Text("Cada persona abre la invitación y se une con su propia cuenta de Telegram.",
            color = MiFlixStyle.secondaryText) }
        item { OutlinedTextField(invite, onInviteChange, label = { Text("Enlace de invitación") },
            isError = !inviteOk, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        if (invite.isNotBlank() && inviteOk) item {
            TextButton(onClick = onJoin) { Text("Entrar al canal en Telegram") }
        }
        if (telegramState !is TelegramSession.State.NeedsApi) {
            item { SectionHeading("Sesión en MiFlix") }
            item {
                Text(
                    if (telegramState is TelegramSession.State.Ready) "Cuenta Telegram conectada"
                    else "Sesión Telegram pendiente de autorización",
                    color = MiFlixStyle.secondaryText
                )
            }
            item {
                TextButton(onClick = onDisconnectTelegram) {
                    Text(if (telegramState is TelegramSession.State.Ready)
                        "Cerrar sesión Telegram" else "Reiniciar conexión Telegram")
                }
            }
        }
        item { TextButton(onClick = { showAdvanced = !showAdvanced }) {
            Text(if (showAdvanced) "Ocultar opciones avanzadas" else "Opciones avanzadas")
        } }
        if (showAdvanced) item {
            OutlinedTextField(url, onUrlChange, label = { Text("URL HTTPS del catálogo") },
                isError = !urlOk, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item { Button(onClick = onSave, enabled = urlOk && inviteOk,
            modifier = Modifier.fillMaxWidth()) { Text("Guardar y actualizar") } }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(text, color = MiFlixStyle.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun DetailBackdrop(title: Title) {
    Box(Modifier.fillMaxWidth().height(310.dp).background(MiFlixStyle.surface)) {
        if (!title.backdropUrl.isNullOrBlank()) AsyncImage(title.backdropUrl, null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else if (!title.posterUrl.isNullOrBlank()) AsyncImage(title.posterUrl, null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.align(Alignment.Center).fillMaxHeight())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(Color.Transparent, MiFlixStyle.background))))
    }
}

@Composable
private fun Detail(title: Title, modifier: Modifier = Modifier, autoplayTrailers: Boolean,
    onPlay: (String, String, String, EpisodeNavigationTarget?, EpisodeNavigationTarget?) -> Unit) {
    val seasons = title.temporadas.orEmpty()
    var selectedSeason by remember(title.id) { mutableIntStateOf(0) }
    val trailer = remember(title.trailerUrl, autoplayTrailers) {
        TrailerLink.autoplayTarget(title.trailerUrl, autoplayTrailers)
    }
    var delayed by remember(title.id) { mutableStateOf(false) }
    var trailerFinished by remember(title.id) { mutableStateOf(false) }
    val detailScroll = rememberLazyListState()
    val heroVisible by remember(detailScroll) { derivedStateOf {
        val layout = detailScroll.layoutInfo
        val hero = layout.visibleItemsInfo.firstOrNull { it.index == 0 }
        hero != null && hero.size > 0 &&
            (minOf(hero.offset + hero.size, layout.viewportEndOffset) -
                maxOf(hero.offset, layout.viewportStartOffset)) * 2 > hero.size
    } }
    LaunchedEffect(title.id, trailer) {
        delayed = false
        if (trailer != null) {
            android.util.Log.d("MiFlixTrailer", "TRAILER_DELAY_START id=${trailer.videoId}")
            kotlinx.coroutines.delay(2_000)
            delayed = true
        }
    }
    LazyColumn(modifier.fillMaxSize(), state = detailScroll,
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            if (delayed && heroVisible && !trailerFinished && trailer != null) {
                TrailerPreview(trailer, backdrop = { DetailBackdrop(title) },
                    onFinished = { trailerFinished = true })
            } else DetailBackdrop(title)
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(title.titulo, color = MiFlixStyle.primaryText, fontSize = 30.sp,
                    lineHeight = 35.sp, fontWeight = FontWeight.Bold)
                val rating = title.puntuacion?.takeIf { it.isFinite() && it in 0.0..10.0 }
                    ?.let { "★ " + java.text.DecimalFormat("0.0").format(it) }
                val info = listOfNotNull(title.metadata().takeIf(String::isNotBlank), rating)
                    .joinToString(" · ")
                if (info.isNotBlank()) Text(info, color = MiFlixStyle.secondaryText, fontSize = 14.sp)
                val genres = title.generos.orEmpty().filter { it.isNotBlank() }
                if (genres.isNotEmpty()) Text(genres.joinToString(" · "),
                    color = MiFlixStyle.secondaryText, fontSize = 14.sp)
                title.audio?.takeIf(String::isNotBlank)?.let { audio ->
                    Text("Audio: $audio", color = MiFlixStyle.secondaryText, fontSize = 14.sp)
                }
                title.telegramUrl?.takeIf { title.tipo != "serie" && validTelegramLink(it) }?.let { link ->
                    Button(onClick = {
                        val publication = if (title.tipo == "anime") seasons.withIndex()
                            .firstNotNullOfOrNull { (seasonIndex, season) ->
                                season.episodios.orEmpty().indexOfFirst { it.telegramUrl == link }
                                    .takeIf { it >= 0 }?.let { seasonIndex to it }
                            } else null
                        onPlay(ProgressKeys.title(title), link, title.titulo,
                            publication?.let { (seasonIndex, episodeIndex) ->
                                findPreviousPlayableEpisode(title, seasonIndex, episodeIndex,
                                    ::validTelegramLink)
                            },
                            publication?.let { (seasonIndex, episodeIndex) ->
                                findNextPlayableEpisode(title, seasonIndex, episodeIndex,
                                    ::validTelegramLink)
                            })
                    },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MiFlixStyle.primaryText,
                            contentColor = MiFlixStyle.background)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Reproducir", fontWeight = FontWeight.Bold)
                    }
                }
                title.sinopsis?.takeIf { it.isNotBlank() }?.let {
                    SectionHeading("Sinopsis")
                    Text(it, color = MiFlixStyle.secondaryText, fontSize = 16.sp, lineHeight = 24.sp)
                }
                title.director?.takeIf { it.isNotBlank() }?.let {
                    DetailCredit("Dirección", it)
                }
                title.reparto.orEmpty().filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.let {
                    DetailCredit("Reparto", it.joinToString(" · "))
                }
                if (title.telegramUrl.isNullOrBlank() &&
                    seasons.none { season -> season.episodios.orEmpty().any { !it.telegramUrl.isNullOrBlank() } }) {
                    Text("Todavía no disponible para reproducir.",
                        color = MiFlixStyle.secondaryText, fontSize = 14.sp)
                }
            }
        }
        if (seasons.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeading(if (title.tipo == "pelicula") "Publicaciones" else "Episodios")
                    if (seasons.size > 1) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(seasons.size) { index ->
                            val season = seasons[index]
                            FilterChip(selected = index == selectedSeason,
                                onClick = { selectedSeason = index },
                                label = { Text(season.nombre?.takeIf(String::isNotBlank)
                                    ?: season.titulo?.takeIf(String::isNotBlank)
                                    ?: "Temporada " + season.numero) })
                        }
                    } else Text(seasons.first().nombre?.takeIf(String::isNotBlank)
                        ?: seasons.first().titulo?.takeIf(String::isNotBlank)
                        ?: "Temporada " + seasons.first().numero,
                        color = MiFlixStyle.secondaryText)
                }
            }
            val episodes = seasons.getOrNull(selectedSeason)?.episodios.orEmpty()
            // Algunos catálogos de Admin no incluyen Episode.id; el índice evita claves duplicadas.
            items(episodes.size) { index ->
                val episode = episodes[index]
                EpisodeCard(episode, onClick = {
                    episode.telegramUrl?.let { link ->
                        seasons.getOrNull(selectedSeason)?.let { season ->
                            onPlay(ProgressKeys.episode(title, season, episode), link,
                                episode.titulo ?: title.titulo,
                                findPreviousPlayableEpisode(title, selectedSeason, index,
                                    ::validTelegramLink),
                                findNextPlayableEpisode(title, selectedSeason, index,
                                    ::validTelegramLink))
                        }
                    }
                })
            }
        }
    }
}

@Composable
private fun DetailCredit(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = MiFlixStyle.secondaryText, fontSize = 13.sp)
        Text(value, color = MiFlixStyle.primaryText, fontSize = 15.sp, lineHeight = 21.sp)
    }
}

@Composable
private fun EpisodeCard(episode: Episode, onClick: () -> Unit) {
    val playable = episode.telegramUrl?.let(::validTelegramLink) == true
    Surface(color = MiFlixStyle.surface, shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .then(if (playable) Modifier.clickable(onClick = onClick) else Modifier)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!episode.imagenUrl.isNullOrBlank()) {
                AsyncImage(episode.imagenUrl, contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(88.dp).height(52.dp).clip(RoundedCornerShape(6.dp)))
            }
            Column(Modifier.weight(1f)) {
                Text("E${episode.numero.toString().padStart(2, '0')} · " +
                    (episode.titulo?.takeIf { it.isNotBlank() } ?: "Episodio " + episode.numero),
                    color = MiFlixStyle.primaryText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val duration = episode.duracion?.takeIf { it > 0 }?.let { "$it min" }
                Text(listOfNotNull(duration,
                    if (playable) "Reproducir en MiFlix" else "Sin enlace disponible")
                    .joinToString(" · "),
                    color = MiFlixStyle.secondaryText, fontSize = 12.sp)
                episode.sinopsis?.takeIf(String::isNotBlank)?.let {
                    Text(it, color = MiFlixStyle.secondaryText, fontSize = 12.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (playable) Icon(Icons.Default.PlayArrow,
                contentDescription = "Reproducir episodio", tint = MiFlixStyle.primaryText)
        }
    }
}
