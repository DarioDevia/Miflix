package ar.com.miflix.client

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.saveable.rememberSaveable
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
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = getSharedPreferences("miflix_client", MODE_PRIVATE)
        val repository = CatalogRepository(this)
        setContent {
            ClientApp(
                repository = repository,
                initialUrl = preferences.getString("catalog_url", CatalogRepository.DEFAULT_URL).orEmpty(),
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

private enum class Screen { HOME, SEARCH, SECTIONS, DETAIL, SETTINGS }
private val categories = listOf("Todos", "Películas", "Series", "Anime")

private fun Title.matchesCategory(category: String) = category == "Todos" || tipo == when (category) {
    "Películas" -> "pelicula"
    "Series" -> "serie"
    else -> "anime"
}

private fun Title.matchesQuery(query: String): Boolean =
    query.isBlank() || titulo.contains(query, ignoreCase = true) ||
        generos.orEmpty().any { it.contains(query, ignoreCase = true) }

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
    var url by remember { mutableStateOf(initialUrl) }
    var invite by remember { mutableStateOf(initialInvite) }
    var editUrl by remember { mutableStateOf(initialUrl) }
    var editInvite by remember { mutableStateOf(initialInvite) }
    var catalog by remember { mutableStateOf(Catalog("1.2.0")) }
    var source by remember { mutableStateOf("Cargando…") }
    var problem by remember { mutableStateOf<String?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var returnScreen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var category by rememberSaveable { mutableStateOf("Todos") }
    var query by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val homeScroll = rememberLazyListState()
    val searchScroll = rememberLazyGridState()
    val sectionsScroll = rememberLazyGridState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    val titles = catalog.items.orEmpty()
    val selected = titles.firstOrNull { it.id == selectedId }

    fun refresh() {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val loaded = repository.load(url)
                catalog = loaded.catalog
                source = loaded.source
                problem = loaded.error
            } finally {
                busy = false
            }
        }
    }
    LaunchedEffect(url) {
        refresh()
        while (true) {
            delay(30 * 60 * 1000L)
            if (url.isNotBlank()) refresh()
        }
    }
    DisposableEffect(owner, url) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && url.isNotBlank()) refresh()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    fun openDetail(title: Title) {
        returnScreen = screen
        selectedId = title.id
        screen = Screen.DETAIL
    }
    fun openSettings() {
        returnScreen = screen
        editUrl = url
        editInvite = invite
        screen = Screen.SETTINGS
    }
    fun goBack() {
        screen = if (screen == Screen.DETAIL || screen == Screen.SETTINGS) returnScreen else Screen.HOME
        selectedId = null
    }
    BackHandler(enabled = screen != Screen.HOME) { goBack() }
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
        Scaffold(
            containerColor = MiFlixStyle.background,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (screen) {
                                Screen.HOME -> "MiFlix"
                                Screen.SEARCH -> "Buscar"
                                Screen.SECTIONS -> "Secciones"
                                Screen.SETTINGS -> "Configuración"
                                Screen.DETAIL -> selected?.titulo.orEmpty()
                            },
                            color = if (screen == Screen.HOME) MiFlixStyle.accent else MiFlixStyle.primaryText,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        if (screen == Screen.DETAIL || screen == Screen.SETTINGS) {
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
                    onSelect = ::openDetail,
                    onSetup = ::openSettings,
                    onBrowse = { category = it; screen = Screen.SECTIONS }
                )
                Screen.SEARCH -> SearchScreen(
                    modifier = Modifier.padding(padding),
                    titles = titles,
                    scrollState = searchScroll,
                    query = query,
                    onQueryChange = { query = it },
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
                Screen.DETAIL -> {
                    if (selected != null) Detail(selected, Modifier.padding(padding))
                }
                Screen.SETTINGS -> SettingsScreen(
                    modifier = Modifier.padding(padding),
                    url = editUrl,
                    invite = editInvite,
                    source = source,
                    problem = problem,
                    busy = busy,
                    count = titles.size,
                    onUrlChange = { editUrl = it },
                    onInviteChange = { editInvite = it },
                    onRefresh = ::refresh,
                    onJoin = { openTelegram(context, editInvite) },
                    onSave = {
                        val nextUrl = editUrl.trim()
                        val nextInvite = editInvite.trim()
                        val previousUrl = url
                        url = nextUrl
                        invite = nextInvite
                        saveSettings(nextUrl, nextInvite)
                        screen = returnScreen
                        if (nextUrl == previousUrl) refresh()
                    }
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    scrollState: LazyListState,
    titles: List<Title>,
    problem: String?,
    busy: Boolean,
    onSelect: (Title) -> Unit,
    onSetup: () -> Unit,
    onBrowse: (String) -> Unit
) {
    val featured = titles.firstOrNull { !it.backdropUrl.isNullOrBlank() }
        ?: titles.firstOrNull { !it.posterUrl.isNullOrBlank() }
        ?: titles.firstOrNull()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = scrollState,
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
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
                        else -> "Conectá la dirección de tu catálogo en Configuración."
                    },
                    action = if (busy) null else "Configurar catálogo",
                    onAction = onSetup
                )
            }
        } else {
            if (featured != null) item { Hero(featured, onClick = { onSelect(featured) }) }
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
    modifier: Modifier, titles: List<Title>, scrollState: LazyGridState, query: String,
    onQueryChange: (String) -> Unit, onSelect: (Title) -> Unit
) {
    val results = remember(titles, query) {
        if (query.isBlank()) emptyList() else titles.filter { it.matchesQuery(query.trim()) }
    }
    Column(modifier.fillMaxSize()) {
        OutlinedTextField(query, onQueryChange,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("Películas, series y anime") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            shape = RoundedCornerShape(12.dp))
        if (query.isBlank()) {
            EmptyState("¿Qué querés ver?", "Buscá un título o un género del catálogo.")
        } else if (results.isEmpty()) {
            EmptyState("Sin resultados", "Probá con otro título o género.")
        } else {
            Text(results.size.toString() + " resultados",
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MiFlixStyle.secondaryText, fontSize = 13.sp)
            MovieGrid(results, onSelect, Modifier.weight(1f), scrollState)
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
    onUrlChange: (String) -> Unit, onInviteChange: (String) -> Unit,
    onRefresh: () -> Unit, onJoin: () -> Unit, onSave: () -> Unit
) {
    val urlOk = url.isBlank() || runCatching {
        val uri = Uri.parse(url.trim())
        uri.scheme.equals("https", true) && !uri.host.isNullOrBlank()
    }.getOrDefault(false)
    val inviteOk = invite.isBlank() || validTelegramLink(invite)
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { SectionHeading("Catálogo") }
        item { Text("La última copia válida queda disponible sin conexión. MiFlix busca cambios al abrir y al volver a la app.", color = MiFlixStyle.secondaryText) }
        item { OutlinedTextField(url, onUrlChange, label = { Text("URL HTTPS del catálogo") },
            isError = !urlOk, singleLine = true, modifier = Modifier.fillMaxWidth()) }
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
        item { SectionHeading("Canal de Telegram") }
        item { Text("Cada persona abre la invitación y se une con su propia cuenta de Telegram.",
            color = MiFlixStyle.secondaryText) }
        item { OutlinedTextField(invite, onInviteChange, label = { Text("Enlace de invitación") },
            isError = !inviteOk, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        if (invite.isNotBlank() && inviteOk) item {
            TextButton(onClick = onJoin) { Text("Entrar al canal en Telegram") }
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
private fun Detail(title: Title, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val seasons = title.temporadas.orEmpty()
    var selectedSeason by remember(title.id) { mutableIntStateOf(0) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(265.dp).background(MiFlixStyle.surface)) {
                if (!title.backdropUrl.isNullOrBlank()) AsyncImage(title.backdropUrl, null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else if (!title.posterUrl.isNullOrBlank()) AsyncImage(title.posterUrl, null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.align(Alignment.Center).fillMaxHeight())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                    listOf(Color.Transparent, MiFlixStyle.background))))
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(title.titulo, color = MiFlixStyle.primaryText, fontSize = 28.sp,
                    lineHeight = 32.sp, fontWeight = FontWeight.Bold)
                val info = title.metadata()
                if (info.isNotBlank()) Text(info, color = MiFlixStyle.secondaryText, fontSize = 14.sp)
                title.telegramUrl?.takeIf(::validTelegramLink)?.let { link ->
                    Button(onClick = { openTelegram(context, link) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MiFlixStyle.primaryText,
                            contentColor = MiFlixStyle.background)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Abrir en Telegram", fontWeight = FontWeight.Bold)
                    }
                }
                title.sinopsis?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = MiFlixStyle.primaryText, fontSize = 16.sp, lineHeight = 23.sp)
                }
                val genres = title.generos.orEmpty().filter { it.isNotBlank() }
                if (genres.isNotEmpty()) Text(genres.joinToString(" · "),
                    color = MiFlixStyle.secondaryText, fontSize = 14.sp)
                if (title.telegramUrl.isNullOrBlank() &&
                    seasons.none { season -> season.episodios.orEmpty().any { !it.telegramUrl.isNullOrBlank() } }) {
                    Text("Todavía no disponible para abrir en Telegram.",
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
                                label = { Text(season.titulo ?: "Temporada " + season.numero) })
                        }
                    } else Text(seasons.first().titulo ?: "Temporada " + seasons.first().numero,
                        color = MiFlixStyle.secondaryText)
                }
            }
            val episodes = seasons.getOrNull(selectedSeason)?.episodios.orEmpty()
            items(episodes, key = { it.id }) { episode ->
                EpisodeCard(episode, onClick = {
                    episode.telegramUrl?.let { openTelegram(context, it) }
                })
            }
        }
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
            Text(episode.numero.toString(), color = MiFlixStyle.secondaryText,
                fontWeight = FontWeight.Bold)
            Column(Modifier.weight(1f)) {
                Text(episode.titulo?.takeIf { it.isNotBlank() } ?: "Episodio " + episode.numero,
                    color = MiFlixStyle.primaryText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if (playable) "Abrir en Telegram" else "Sin enlace disponible",
                    color = MiFlixStyle.secondaryText, fontSize = 12.sp)
            }
            if (playable) Icon(Icons.Default.PlayArrow,
                contentDescription = "Abrir episodio en Telegram", tint = MiFlixStyle.primaryText)
        }
    }
}
