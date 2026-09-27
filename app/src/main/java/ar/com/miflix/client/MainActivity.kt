package ar.com.miflix.client

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

private val ink = Color(0xFF09090B)
private val panel = Color(0xFF1A1A1E)
private val accent = Color(0xFFE50914)
private val muted = Color(0xFFBABAC2)
private val categories = listOf("Todos", "Películas", "Series", "Anime")

private fun validTelegramLink(text: String): Boolean = runCatching {
    val uri = Uri.parse(text.trim())
    uri.scheme.equals("https", true) && uri.host?.lowercase() in setOf("t.me", "www.t.me", "telegram.me") &&
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
    var selected by remember { mutableStateOf<Title?>(null) }
    var section by remember { mutableStateOf("Todos") }
    var query by remember { mutableStateOf("") }
    var settingsOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current

    fun refresh() {
        if (busy) return
        scope.launch {
            busy = true
            try {
                val loaded = repository.load(url)
                catalog = loaded.catalog
                source = loaded.source
                problem = loaded.error
            } finally { busy = false }
        }
    }

    LaunchedEffect(url) {
        refresh()
        // Mientras la app permanezca abierta, consulta la URL guardada cada 30 minutos.
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

    MaterialTheme(colorScheme = darkColorScheme(primary = accent, background = ink, surface = ink,
        surfaceVariant = panel, onSurfaceVariant = muted)) {
        Scaffold(containerColor = ink, topBar = {
            TopAppBar(
                title = {
                    Text(if (settingsOpen) "Configuración" else selected?.titulo ?: "MiFlix",
                        color = if (!settingsOpen && selected == null) accent else Color.White,
                        fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    if (selected != null || settingsOpen) IconButton(onClick = {
                        selected = null
                        settingsOpen = false
                    }) { Icon(Icons.Default.ArrowBack, "Volver") }
                },
                actions = {
                    if (!settingsOpen && selected == null) {
                        IconButton(onClick = ::refresh, enabled = !busy) { Icon(Icons.Default.Refresh, "Actualizar catálogo") }
                        IconButton(onClick = {
                            editUrl = url
                            editInvite = invite
                            settingsOpen = true
                        }) { Icon(Icons.Default.Settings, "Configuración") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ink)
            )
        }) { padding ->
            when {
                settingsOpen -> SettingsScreen(
                    modifier = Modifier.padding(padding), url = editUrl, invite = editInvite,
                    source = source, problem = problem,
                    onUrlChange = { editUrl = it }, onInviteChange = { editInvite = it },
                    onSave = {
                        val nextUrl = editUrl.trim()
                        val nextInvite = editInvite.trim()
                        url = nextUrl
                        invite = nextInvite
                        saveSettings(nextUrl, nextInvite)
                        settingsOpen = false
                        refresh()
                    }
                )
                selected != null -> Detail(selected!!, Modifier.padding(padding))
                else -> HomeScreen(
                    Modifier.padding(padding), catalog.items, section, query, source, problem,
                    busy, invite,
                    onSection = { section = it }, onQuery = { query = it },
                    onSelect = { selected = it },
                    onJoin = { openTelegram(context, invite) },
                    onSetup = {
                        editUrl = url
                        editInvite = invite
                        settingsOpen = true
                    }
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier, titles: List<Title>, section: String, query: String, source: String,
    problem: String?, busy: Boolean, invite: String,
    onSection: (String) -> Unit, onQuery: (String) -> Unit, onSelect: (Title) -> Unit,
    onJoin: () -> Unit, onSetup: () -> Unit
) {
    val filtered = titles.filter { title ->
        (section == "Todos" || title.tipo == when (section) {
            "Películas" -> "pelicula"
            "Series" -> "serie"
            else -> "anime"
        }) && (query.isBlank() || title.titulo.contains(query, true) ||
            title.generos.any { it.contains(query, true) })
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { option ->
                    FilterChip(selected = section == option, onClick = { onSection(option) },
                        label = { Text(option) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color.White, selectedLabelColor = Color.Black))
                }
            }
        }
        item {
            OutlinedTextField(query, onQuery, leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("Buscar películas, series y anime") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp))
        }
        if (invite.isNotBlank()) item {
            TextButton(onClick = onJoin, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text("Entrar al canal privado en Telegram")
            }
        }
        if (problem != null) item {
            Text("No se pudo actualizar; ${if (titles.isEmpty()) "revisá la configuración" else "se muestra la copia local"}. $problem",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 18.dp))
        }
        if (filtered.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (busy) "Cargando catálogo…" else if (titles.isEmpty())
                    "Tu videoteca empieza acá" else "No encontramos títulos",
                    style = MaterialTheme.typography.titleLarge)
                Text(if (titles.isEmpty()) "Configurá una vez la URL del catálogo que publica MiFlix Admin."
                    else "Probá otra búsqueda o sección.", color = muted)
                if (titles.isEmpty()) Button(onClick = onSetup) { Text("Configurar catálogo") }
            }
        } else {
            if (section == "Todos" && query.isBlank()) {
                val featured = filtered.first()
                item { FeaturedTitle(featured, onSelect = { onSelect(featured) }) }
                listOf("pelicula" to "Películas", "serie" to "Series", "anime" to "Anime").forEach { (key, label) ->
                    val group = filtered.filter { it.tipo == key }
                    if (group.isNotEmpty()) item { PosterRail(label, group, onSelect) }
                }
            } else {
                item { Text("${filtered.size} títulos", Modifier.padding(horizontal = 16.dp), color = muted) }
                item { PosterRail(section, filtered, onSelect) }
            }
        }
        item { Text("$source · ${titles.size} títulos", Modifier.padding(horizontal = 16.dp),
            color = muted, style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun FeaturedTitle(title: Title, onSelect: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(390.dp).padding(horizontal = 16.dp)
        .clip(RoundedCornerShape(14.dp)).background(panel).clickable(onClick = onSelect)) {
        AsyncImage(model = title.backdropUrl ?: title.posterUrl, contentDescription = title.titulo,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(Color.Transparent, Color(0x66000000), Color(0xF0000000)))))
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("DESTACADO", color = accent, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium)
            Text(title.titulo, style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(title.year?.toString(), title.calidad, title.generos.take(2).joinToString(" · ")
                .takeIf { it.isNotBlank() }).joinToString(" · "), color = Color.White)
            Button(onClick = onSelect, colors = ButtonDefaults.buttonColors(containerColor = Color.White,
                contentColor = Color.Black)) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text("Ver ficha")
            }
        }
    }
}

@Composable
private fun PosterRail(label: String, titles: List<Title>, onSelect: (Title) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(titles, key = { it.id }) { title ->
                Column(Modifier.width(126.dp).clickable { onSelect(title) },
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.fillMaxWidth().height(186.dp).clip(RoundedCornerShape(8.dp))
                        .background(panel), contentAlignment = Alignment.Center) {
                        Text("MiFlix", color = accent, fontWeight = FontWeight.Black)
                        AsyncImage(model = title.posterUrl, contentDescription = title.titulo,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Text(title.titulo, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier, url: String, invite: String, source: String, problem: String?,
    onUrlChange: (String) -> Unit, onInviteChange: (String) -> Unit, onSave: () -> Unit
) {
    val urlOk = url.isBlank() || runCatching {
        val uri = Uri.parse(url.trim())
        uri.scheme.equals("https", true) && !uri.host.isNullOrBlank()
    }.getOrDefault(false)
    val inviteOk = invite.isBlank() || validTelegramLink(invite)
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text("Catálogo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { Text("Pegá una vez la URL HTTPS de catalogo.json. Se actualiza al abrir la app y cada 30 minutos mientras la usás. La última copia válida queda disponible sin conexión.", color = muted) }
        item { OutlinedTextField(url, onUrlChange, label = { Text("URL HTTPS del catálogo") },
            isError = !urlOk, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item { Text("Canal de Telegram", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { Text("Pegá el enlace de invitación del canal privado. Cada persona debe abrirlo y unirse con su propia cuenta de Telegram.", color = muted) }
        item { OutlinedTextField(invite, onInviteChange, label = { Text("https://t.me/+…") },
            isError = !inviteOk, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item { Button(onClick = onSave, enabled = urlOk && inviteOk) { Text("Guardar y actualizar") } }
        item { Text("Origen: $source", color = muted) }
        if (problem != null) item { Text(problem, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun Detail(title: Title, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    LazyColumn(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(250.dp).background(panel)) {
                AsyncImage(title.backdropUrl ?: title.posterUrl, title.titulo,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                    listOf(Color.Transparent, ink))))
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title.titulo, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text(listOfNotNull(title.year?.toString(), title.duracion, title.calidad).joinToString(" · "), color = muted)
                title.sinopsis?.let { Text(it) }
                title.telegramUrl?.let { link ->
                    Button(onClick = { openTelegram(context, link) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Abrir en Telegram")
                    }
                }
            }
        }
        title.temporadas.forEach { season ->
            item { Text(season.titulo ?: "Temporada ${season.numero}",
                modifier = Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleLarge) }
            items(season.episodios, key = { it.id }) { episode ->
                ListItem(headlineContent = { Text(episode.titulo ?: "Episodio ${episode.numero}") },
                    supportingContent = { Text("Abrir publicación en Telegram") },
                    modifier = Modifier.clickable {
                        episode.telegramUrl?.let { openTelegram(context, it) }
                    })
            }
        }
    }
}
