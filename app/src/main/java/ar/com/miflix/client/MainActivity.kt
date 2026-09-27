package ar.com.miflix.client

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = CatalogRepository(this)
        val settings = getSharedPreferences("miflix_client", MODE_PRIVATE)
        setContent { ClientApp(repository, settings.getString("catalog_url", CatalogRepository.DEFAULT_URL).orEmpty()) { url ->
            settings.edit().putString("catalog_url", url).apply()
        } }
    }
}

private val ink = Color(0xFF0B101B)
private val accent = Color(0xFFE64C66)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClientApp(repository: CatalogRepository, initialUrl: String, saveUrl: (String) -> Unit) {
    var url by remember { mutableStateOf(initialUrl) }
    var editUrl by remember { mutableStateOf(initialUrl) }
    var catalog by remember { mutableStateOf(Catalog("1.2.0")) }
    var source by remember { mutableStateOf("Cargando…") }
    var problem by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Title?>(null) }
    var tab by remember { mutableStateOf("pelicula") }
    var query by remember { mutableStateOf("") }
    var settingsOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            busy = true
            val result = repository.load(url)
            catalog = result.catalog
            source = result.source
            problem = result.error
            busy = false
        }
    }

    LaunchedEffect(url) { refresh() }
    MaterialTheme(colorScheme = darkColorScheme(primary = accent, background = ink, surface = Color(0xFF172033))) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (selected == null) "MiFlix" else selected!!.titulo, maxLines = 1) },
                    navigationIcon = {
                        if (selected != null || settingsOpen) IconButton(onClick = { selected = null; settingsOpen = false }) {
                            Icon(Icons.Default.ArrowBack, "Volver")
                        }
                    },
                    actions = {
                        if (selected == null && !settingsOpen) {
                            IconButton(onClick = ::refresh, enabled = !busy) { Icon(Icons.Default.Refresh, "Actualizar") }
                            IconButton(onClick = { settingsOpen = true }) { Icon(Icons.Default.Settings, "Configuración") }
                        }
                    }
                )
            }
        ) { padding ->
            when {
                settingsOpen -> Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Catálogo remoto", style = MaterialTheme.typography.titleLarge)
                    Text("Pegá la dirección HTTPS del catalogo.json que publica el administrador.")
                    OutlinedTextField(value = editUrl, onValueChange = { editUrl = it }, label = { Text("URL del catálogo") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { url = editUrl.trim(); saveUrl(url); settingsOpen = false },
                        enabled = editUrl.trim().startsWith("https://", true)) { Text("Guardar y actualizar") }
                    Text("Origen actual: $source", style = MaterialTheme.typography.bodySmall)
                    problem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                selected != null -> Detail(selected!!, Modifier.padding(padding))
                else -> Column(Modifier.fillMaxSize().padding(padding)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("pelicula" to "Películas", "serie" to "Series", "anime" to "Anime").forEach { (key, label) ->
                            FilterChip(selected = tab == key, onClick = { tab = key }, label = { Text(label) })
                        }
                    }
                    OutlinedTextField(query, { query = it }, leadingIcon = { Icon(Icons.Default.Search, null) },
                        placeholder = { Text("Buscar títulos") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                    Text("$source · ${catalog.items.size} títulos", modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.labelMedium)
                    problem?.let { Text("No se pudo actualizar: $it", modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.error) }
                    val visible = catalog.items.filter { it.tipo == tab && it.titulo.contains(query, ignoreCase = true) }
                    if (visible.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (busy) "Cargando catálogo…" else "No hay títulos en esta sección")
                    } else LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(visible, key = { it.id }) { title ->
                            Card(Modifier.fillMaxWidth().clickable { selected = title }) {
                                Row(Modifier.height(140.dp)) {
                                    AsyncImage(title.posterUrl, title.titulo, contentScale = ContentScale.Crop,
                                        modifier = Modifier.width(96.dp).fillMaxHeight().background(Color.DarkGray))
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                        Text(title.titulo, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(listOfNotNull(title.year?.toString(), title.calidad).joinToString(" · "))
                                        Text(title.generos.joinToString(" · "), maxLines = 2, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Detail(title: Title, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    fun openTelegram(url: String?) {
        if (url.isNullOrBlank()) return
        val uri = Uri.parse(url)
        if (uri.scheme != "https" || uri.host !in listOf("t.me", "www.t.me", "telegram.me")) return
        try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        catch (_: ActivityNotFoundException) { android.widget.Toast.makeText(context, "No se pudo abrir el enlace", android.widget.Toast.LENGTH_SHORT).show() }
    }
    LazyColumn(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            AsyncImage(title.backdropUrl ?: title.posterUrl, title.titulo, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(230.dp).background(Color.DarkGray))
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title.titulo, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(listOfNotNull(title.year?.toString(), title.duracion, title.calidad).joinToString(" · "))
                title.sinopsis?.let { Text(it) }
                title.telegramUrl?.let { link ->
                    Button(onClick = { openTelegram(link) }) { Text("Abrir publicación en Telegram") }
                }
            }
        }
        title.temporadas.forEach { season ->
            item { Text(season.titulo ?: "Temporada ${season.numero}", modifier = Modifier.padding(horizontal = 20.dp),
                style = MaterialTheme.typography.titleLarge) }
            items(season.episodios, key = { it.id }) { episode ->
                ListItem(headlineContent = { Text(episode.titulo ?: "Episodio ${episode.numero}") },
                    supportingContent = { Text("Abrir en Telegram") },
                    modifier = Modifier.clickable { openTelegram(episode.telegramUrl) })
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}
