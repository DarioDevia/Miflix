package ar.com.miflix.admin

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.google.gson.JsonObject

class MainActivity: ComponentActivity() {
    private val model: AdminViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(savedInstanceState==null) receive(intent)
        setContent {MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFF38BDF8),background=Color(0xFF111827),surface=Color(0xFF1F2937))) {AdminApp(model)}}
    }
    override fun onNewIntent(intent: Intent) {super.onNewIntent(intent);setIntent(intent);receive(intent)}
    private fun receive(intent: Intent) {
        if(intent.action==Intent.ACTION_SEND && intent.type=="text/plain") model.receive(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
    }
}

@Composable private fun AdminApp(vm: AdminViewModel) {
    val context=LocalContext.current
    var discardRefresh by remember {mutableStateOf(false)}
    var discardDraft by remember {mutableStateOf(false)}
    var saveConfirmation by remember {mutableStateOf(false)}
    val rev=vm.revision // reads pending/readonly flags after repository commits
    fun open(url: String) { TelegramLinks.valid(url)?.let {try {context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(it)))} catch(_:Exception) { android.widget.Toast.makeText(context,"No hay una aplicación para abrir Telegram.",android.widget.Toast.LENGTH_LONG).show() }} }
    BackHandler(enabled=vm.screen !in setOf("home","startup")) {
        if(vm.busy) return@BackHandler
        vm.screen=when(vm.screen) {"review","search","enrichment" ->"home";"detail" ->"list";else ->"home"}
    }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("MiFlix Admin",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                if(vm.screen!="startup") TextButton(onClick={vm.screen="home"},enabled=!vm.busy) {Text("Inicio")}
            }
            if(vm.message.isNotBlank()) Text(vm.message,Modifier.fillMaxWidth().background(Color(0xFF263449)).padding(12.dp),style=MaterialTheme.typography.bodyMedium)
            if(vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(!vm.writable && vm.screen !in setOf("startup","config")) {
                Text("SIN SINCRONIZAR · solo lectura",Modifier.padding(12.dp),color=Color(0xFFFBBF24))
            }
            Box(Modifier.weight(1f)) {
                when(vm.screen) {
                    "startup" -> Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {CircularProgressIndicator();Spacer(Modifier.height(20.dp));Text("Actualizando catálogo…")}
                    "config" -> ConfigScreen(vm)
                    "list" -> CatalogScreen(vm)
                    "detail" -> DetailScreen(vm) {open(it)}
                    "search" -> SearchScreen(vm)
                    "review" -> ReviewScreen(vm,onSave={saveConfirmation=true},onDiscard={discardDraft=true})
                    "enrichment" -> EnrichmentScreen(vm)
                    else -> HomeScreen(vm,onRefresh={if(vm.pending) discardRefresh=true else vm.refresh()},onOpen={open(it)})
                }
            }
        }
    }
    if(discardRefresh) AlertDialog(onDismissRequest={discardRefresh=false},title={Text("Cambios pendientes")},text={Text("Actualizar reemplazará tu trabajo local por el remoto. Podés cancelar y sincronizar primero. El borrador de revisión se conserva.")},confirmButton={TextButton(onClick={discardRefresh=false;vm.refresh(discard=true)}) {Text("Descartar pendientes y actualizar")}},dismissButton={TextButton(onClick={discardRefresh=false}) {Text("Cancelar")}})
    if(discardDraft) AlertDialog(onDismissRequest={discardDraft=false},title={Text("Descartar borrador")},text={Text("Se perderán las correcciones de esta revisión. El catálogo guardado no cambia.")},confirmButton={TextButton(onClick={discardDraft=false;vm.discardDraft()}) {Text("Descartar")}},dismissButton={TextButton(onClick={discardDraft=false}) {Text("Conservar")}})
    if(saveConfirmation) AlertDialog(onDismissRequest={saveConfirmation=false},title={Text("Confirmar datos")},text={Text("¿Revisaste la portada, la ficha y los enlaces? Se guardará en el teléfono y quedará pendiente de sincronizar.")},confirmButton={TextButton(onClick={saveConfirmation=false;vm.save()}) {Text("Confirmar y guardar")}},dismissButton={TextButton(onClick={saveConfirmation=false}) {Text("Volver a revisar")}})
    vm.deletion?.let {request -> AlertDialog(
        onDismissRequest={vm.cancelDeletion()},title={Text(request.heading)},text={Text(request.explanation)},
        confirmButton={TextButton(onClick={vm.confirmDeletion()},enabled=vm.writable,colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)) {Text(request.button)}},
        dismissButton={TextButton(onClick={vm.cancelDeletion()}) {Text("Cancelar")}}
    )}
    vm.duplicate?.let {existing -> AlertDialog(onDismissRequest={vm.cancelDuplicate()},title={Text("Esta película ya existe")},text={Column {Text(existing.text("titulo"));Text("${existing.text("anio")} · ${existing.text("audio")} · ${existing.text("calidad")}");Text(existing.text("telegram_url"));Text("Actualizar versión conserva la ficha y requiere el nuevo enlace. Mantener actual solo registra la publicación procesada.")}},confirmButton={Column {
        TextButton(onClick={vm.updateDuplicate()}) {Text("Actualizar versión")}
        TextButton(onClick={vm.keepDuplicate()},enabled=TelegramLinks.valid(vm.original)!=null) {Text("Mantener actual")}
    }},dismissButton={TextButton(onClick={vm.cancelDuplicate()}) {Text("Cancelar")}}) }
    if(vm.sharedText.isNotBlank()) {
        val links=TelegramLinks.extract(vm.sharedText)
        AlertDialog(onDismissRequest={vm.dismissShare()},title={Text("Publicación compartida")},text={Column {
            Text("Elegí la publicación y la categoría. No se guardará automáticamente.")
            TypeRow(vm.category) {vm.category=it}
            if(links.isEmpty()) Text("No recibimos un enlace de mensaje válido. Podés pegarlo en el formulario.")
            links.forEach {link -> TextButton(onClick={vm.consumeShared(link)},enabled=vm.writable) {Text(link)} }
            if(vm.draft!=null) Text("Se aplicará al borrador que conservaste; los demás datos seguirán intactos.")
            if(!vm.writable) Text("Primero configurá y actualizá el catálogo. Este texto quedará disponible durante la sesión.")
        }},confirmButton={TextButton(onClick={vm.dismissShare()}) {Text("Cerrar")}})
    }
}

@Composable private fun ScrollContent(content: @Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
}
@Composable private fun TypeRow(selected: String,onSelect: (String)->Unit) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        listOf("pelicula" to "Películas","serie" to "Series","anime" to "Anime").forEach {(type,label) ->
            FilterChip(selected=selected==type,onClick={onSelect(type)},label={Text(label)})
        }
    }
}
@Composable private fun Poster(url: String,large: Boolean=false) {
    var failed by remember(url) {mutableStateOf(false)}
    if(url.isBlank() || failed) Box(Modifier.height(if(large) 240.dp else 100.dp).fillMaxWidth().background(Color(0xFF374151),RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center) {
        Text(if(url.isBlank()) "Sin portada" else "No se pudo cargar la imagen",Modifier.padding(12.dp))
    } else AsyncImage(model=url,contentDescription="Portada seleccionada",contentScale=ContentScale.Fit,modifier=Modifier.fillMaxWidth().height(if(large) 320.dp else 120.dp),onError={failed=true})
    if(failed) TextButton(onClick={failed=false}) {Text("Reintentar imagen")}
}
@Composable private fun HomeScreen(vm: AdminViewModel,onRefresh:()->Unit,onOpen:(String)->Unit) {
    ScrollContent {
        val catalog=vm.catalog
        Text(if(vm.writable) {if(vm.pending) "CAMBIOS PENDIENTES" else "✓ Sincronizado"} else "SIN SINCRONIZAR",style=MaterialTheme.typography.titleMedium,color=if(vm.pending) Color(0xFFFBBF24) else MaterialTheme.colorScheme.primary)
        Text("${catalog?.let {CatalogRules.items(it).size} ?: 0} títulos",style=MaterialTheme.typography.headlineMedium)
        listOf("pelicula" to "Películas","serie" to "Series","anime" to "Anime").forEach {(type,label) ->
            val count=catalog?.let {CatalogRules.items(it).count {x->x.text("tipo")==type}} ?:0
            OutlinedButton(onClick={vm.openCategory(type)},modifier=Modifier.fillMaxWidth(),enabled=catalog!=null && !vm.busy) {Text("$label · $count")}
        }
        if(vm.draft!=null) OutlinedButton(onClick={vm.resumeDraft()},enabled=!vm.busy,modifier=Modifier.fillMaxWidth()) {Text("Continuar revisión guardada")}
        if(vm.draft!=null) Text("Tu borrador se conserva. Guardá la revisión para incluirla al sincronizar.",style=MaterialTheme.typography.bodySmall)
        Button(onClick={vm.sync()},enabled=vm.writable && vm.pending,modifier=Modifier.fillMaxWidth()) {Text("Sincronizar cambios")}
        OutlinedButton(onClick=onRefresh,enabled=!vm.busy && vm.config!=null,modifier=Modifier.fillMaxWidth()) {Text(if(vm.writable) "Actualizar catálogo" else "Reintentar")}
        Text("Última sincronización: ${vm.lastSync.ifBlank {"todavía no hay una publicación confirmada"}}",style=MaterialTheme.typography.bodySmall)
        HorizontalDivider();Text("Últimos contenidos procesados",style=MaterialTheme.typography.titleMedium)
        Text("Avances locales independientes por tipo de contenido.",style=MaterialTheme.typography.bodySmall)
        AdminCheckpoints.categories.forEach { (type,label) ->
            Text(label,style=MaterialTheme.typography.titleSmall)
            val cp=vm.checkpoint(type)
            if(cp==null) Text("Sin contenido procesado",style=MaterialTheme.typography.bodySmall)
            else {
                Text(cp.text("titulo") + cp.text("anio").takeIf {it.isNotBlank()}?.let {" ($it)"}.orEmpty())
                cp.text("telegram_message_id").takeIf {it.isNotBlank()}?.let {Text("Mensaje $it",style=MaterialTheme.typography.bodySmall)}
                val link=vm.checkpointLink(type)
                if(link!=null) OutlinedButton(onClick={onOpen(link)}) {Text("Abrir publicación en Telegram")}
                else Text("Enlace original no disponible",style=MaterialTheme.typography.bodySmall)
            }
        }
        if(vm.unclassifiedCheckpoint) Text("Se conserva un checkpoint anterior sin categoría identificable; no se asignó a ningún tipo.",style=MaterialTheme.typography.bodySmall)
        Text("Los checkpoints se conservan en este teléfono. El checkpoint compartido queda pendiente.",style=MaterialTheme.typography.bodySmall)
        TextButton(onClick={vm.screen="config"},enabled=!vm.busy) {Text("Configuración del servidor")}
        Text("Administrá en un solo dispositivo a la vez y sincronizá antes de cambiar. Esta versión no bloquea escrituras simultáneas.",style=MaterialTheme.typography.bodySmall)
    }
}
@Composable private fun ConfigScreen(vm: AdminViewModel) {
    var base by rememberSaveable {mutableStateOf(vm.config?.baseUrl ?: "https://miflix-catalogo.deviadario.workers.dev")}
    var token by remember {mutableStateOf(vm.config?.token.orEmpty())}
    var tmdb by remember {mutableStateOf(vm.config?.tmdbToken.orEmpty())}
    ScrollContent {
        Text("Conectar con el catálogo compartido",style=MaterialTheme.typography.titleLarge)
        OutlinedTextField(base,{base=it},label={Text("URL base del Worker")},modifier=Modifier.fillMaxWidth(),enabled=!vm.busy,singleLine=true)
        OutlinedTextField(token,{token=it},label={Text("Credencial administrativa")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth(),enabled=!vm.busy,singleLine=true)
        Text("La credencial se guarda cifrada en este teléfono. No se incorpora a la APK.",style=MaterialTheme.typography.bodySmall)
        Text("Metadatos",style=MaterialTheme.typography.titleMedium)
        Text("Preferido: TMDB configurado en el Worker. Para probar sin su extensión, podés ingresar tu TMDB Bearer Token aquí; quedará cifrado localmente.",style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(tmdb,{tmdb=it},label={Text("TMDB Bearer Token local (opcional)")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth(),enabled=!vm.busy,singleLine=true)
        Button(onClick={vm.configure(base,token,tmdb)},enabled=!vm.busy,modifier=Modifier.fillMaxWidth()) {Text("Guardar y comprobar conexión")}
        Text("La descarga pública comprueba el catálogo. La credencial administrativa se verifica al publicar; los checkpoints permanecen locales.",style=MaterialTheme.typography.bodySmall)
    }
}
@Composable private fun CatalogScreen(vm: AdminViewModel) {
    var query by rememberSaveable(vm.category) {mutableStateOf("")}
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TypeRow(vm.category) {vm.category=it}
        OutlinedTextField(query,{query=it},label={Text("Buscar en el catálogo")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        Button(onClick={vm.newItem()},enabled=vm.writable,modifier=Modifier.fillMaxWidth().padding(vertical=8.dp)) {Text("Agregar contenido")}
        val titles=vm.catalog?.let {CatalogRules.items(it)}.orEmpty().filter {it.text("tipo")==vm.category && it.text("titulo").contains(query,true)}
        Text("${titles.size} elementos",style=MaterialTheme.typography.bodySmall)
        LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            items(titles,key={it.text("id")}) {item ->
                Card(Modifier.fillMaxWidth().clickable {vm.select(item)}) {Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.width(72.dp)) {Poster(item.text("poster_url"))}
                    Column(Modifier.weight(1f).padding(start=12.dp)) {Text(item.text("titulo"),style=MaterialTheme.typography.titleMedium);Text(item.text("anio"));Text(item.text("calidad"),style=MaterialTheme.typography.bodySmall)}
                }}
            }
        }
    }
}
@Composable private fun DetailScreen(vm: AdminViewModel,onOpen:(String)->Unit) {
    val item=vm.selected ?: return
    ScrollContent {
        Poster(item.text("poster_url"),true);Text(item.text("titulo"),style=MaterialTheme.typography.headlineSmall)
        Text("${item.text("anio")} · ${item.array("generos").joinToString {it.asString}}")
        Text(item.text("sinopsis"));Text("${item.text("puntuacion")} · ${item.text("fuente_puntuacion")}")
        Text(item.text("direccion"));Text(item.text("protagonistas"))
        Text("${item.array("temporadas").size()} temporadas",style=MaterialTheme.typography.bodySmall)
        Button(onClick={vm.edit(item)},enabled=vm.writable,modifier=Modifier.fillMaxWidth()) {Text("Editar ficha y episodios")}
        OutlinedButton(onClick={onOpen(item.text("telegram_url"))},enabled=TelegramLinks.valid(item.text("telegram_url"))!=null) {Text("Abrir publicación en Telegram")}
        OutlinedButton(onClick={vm.requestItemDeletion()},enabled=vm.writable,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.error)) {Text("Eliminar del catálogo")}
    }
}
@Composable private fun SearchScreen(vm: AdminViewModel) {
    ScrollContent {
        Text("Buscar metadatos",style=MaterialTheme.typography.titleLarge)
        TelegramField(vm)
        Text("Pegá el enlace y buscá el título. Después podrás revisar y corregir los datos.",style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(vm.query,{vm.query=it},label={Text("Título para buscar")},modifier=Modifier.fillMaxWidth(),enabled=vm.writable)
        if(vm.category=="anime") Row {FilterChip(vm.source=="AniList",{vm.source="AniList"},label={Text("AniList")});Spacer(Modifier.width(8.dp));FilterChip(vm.source=="TMDB",{vm.source="TMDB"},label={Text("TMDB")})}
        Button(onClick={vm.search()},enabled=vm.writable && vm.query.isNotBlank()) {Text("Buscar en ${vm.source}")}
        OutlinedButton(onClick={vm.screen="review"},enabled=!vm.busy) {Text(if(vm.draft?.text("titulo").isNullOrBlank()) "Completar datos manualmente" else "Volver a revisar datos")}
        if(vm.results.isNotEmpty()) {Text("Elegí un resultado",style=MaterialTheme.typography.titleMedium);Text("Tocá la tarjeta para revisar sus datos. Todavía no se guardará.",style=MaterialTheme.typography.bodySmall)}
        vm.results.forEach {result -> Card(Modifier.fillMaxWidth().clickable(enabled=vm.writable,role=androidx.compose.ui.semantics.Role.Button) {vm.use(result)}) {
            Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.width(80.dp)) {Poster(result.poster)}
                Column(Modifier.weight(1f).padding(start=12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(result.title,style=MaterialTheme.typography.titleMedium)
                    if(result.year.isNotBlank()) Text(result.year)
                    result.originalTitle().takeIf {it.isNotBlank()}?.let {Text(it,style=MaterialTheme.typography.bodySmall)}
                    Text("${result.source} · Revisar datos",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.bodySmall)
                }
            }
        } }
    }
}
@Composable private fun TelegramField(vm: AdminViewModel) {
    OutlinedTextField(vm.draft?.text("telegram_url").orEmpty(),{vm.changeTelegramLink(it)},label={Text("Enlace de Telegram")},modifier=Modifier.fillMaxWidth(),enabled=vm.writable)
}
@Composable private fun EditorField(vm: AdminViewModel,key: String,label: String,multiline: Boolean=false) {
    val value=if(key=="generos") vm.draft?.array("generos")?.joinToString {it.asString}.orEmpty() else vm.draft?.text(key).orEmpty()
    OutlinedTextField(value,{vm.field(key,it)},label={Text(label)},modifier=Modifier.fillMaxWidth(),enabled=vm.writable,singleLine=!multiline,minLines=if(multiline) 3 else 1)
}
@Composable private fun ReviewScreen(vm: AdminViewModel,onSave:()->Unit,onDiscard:()->Unit) {
    val item=vm.draft ?: return
    var advanced by rememberSaveable {mutableStateOf(false)}
    var newSeason by rememberSaveable {mutableStateOf("1")}
    ScrollContent {
        Text("Revisar antes de guardar",style=MaterialTheme.typography.titleLarge)
        Poster(item.text("poster_url"),true)
        EditorField(vm,"titulo","Título");EditorField(vm,"titulo_original","Título original");EditorField(vm,"anio","Año");EditorField(vm,"generos","Géneros, separados por coma")
        EditorField(vm,"puntuacion","Puntuación");EditorField(vm,"fuente_puntuacion","Fuente de puntuación")
        EditorField(vm,"sinopsis","Sinopsis",true);EditorField(vm,"direccion","Dirección / créditos");EditorField(vm,"protagonistas","Reparto / protagonistas")
        TelegramField(vm)
        EditorField(vm,"audio","Audio (metadato)");EditorField(vm,"calidad","Calidad")
        TextButton(onClick={advanced=!advanced}) {Text(if(advanced) "Ocultar imágenes y procedencia" else "Imágenes y procedencia")}
        if(advanced) {
            EditorField(vm,"poster_url","URL de portada");EditorField(vm,"fondo_url","URL de fondo");Poster(item.text("fondo_url"))
            EditorField(vm,"trailer_url","URL de trailer (solo metadato)")
            Text("ID: ${item.text("id")}\nTMDB: ${item.text("tmdb_id")} · AniList: ${item.text("anilist_id")} · IMDb: ${item.text("imdb_id")}",style=MaterialTheme.typography.bodySmall)
            Text("Fuente principal: ${item.text("fuente_principal")}\n${item.get("fuentes_metadata") ?: "Sin procedencia registrada"}",style=MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick={vm.query=item.text("titulo");vm.screen="search"},enabled=vm.writable) {Text(if(vm.category=="anime") "Buscar / enriquecer metadatos" else "Buscar otros metadatos")}
        if(vm.category!="pelicula") {
            HorizontalDivider();Text("Temporadas y episodios",style=MaterialTheme.typography.titleLarge)
            Text("La temporada 0 corresponde a especiales. Los enlaces se conservan al consultar metadatos.",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(newSeason,{newSeason=it},label={Text("Número de temporada")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={newSeason.toIntOrNull()?.let {vm.addSeason(it)}},enabled=vm.writable) {Text("Agregar manual")}
                Button(onClick={newSeason.toIntOrNull()?.let {vm.seasonNumber=it;vm.fetchSeason()}},enabled=vm.writable && item.number("tmdb_id")!=null) {Text("Obtener TMDB")}
            }
            item.array("temporadas").forEach {entry -> key(entry.asJsonObject.number("numero")) {SeasonEditor(vm,entry.asJsonObject)} }
        }
        Button(onClick=onSave,enabled=vm.writable && item.text("titulo").isNotBlank(),modifier=Modifier.fillMaxWidth()) {Text("Confirmar datos y guardar")}
        OutlinedButton(onClick={vm.screen="home"},enabled=!vm.busy,modifier=Modifier.fillMaxWidth()) {Text("Guardar revisión para después")}
        TextButton(onClick=onDiscard,enabled=!vm.busy) {Text("Descartar borrador")}
    }
}
@Composable private fun SeasonEditor(vm: AdminViewModel,season: JsonObject) {
    val num=season.number("numero") ?: return
    var expanded by rememberSaveable {mutableStateOf(false)}
    var nextEpisode by rememberSaveable {mutableStateOf("1")}
    Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        TextButton(onClick={expanded=!expanded}) {Text("Temporada $num · ${season.array("episodios").size()} episodios ${if(expanded) "−" else "+"}")}
        if(expanded) {
            OutlinedButton(onClick={vm.requestPartDeletion(num)},enabled=vm.writable,colors=ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.error)) {Text("Eliminar temporada del catálogo")}
            OutlinedTextField(season.text("nombre"),{vm.seasonField(num,"nombre",it)},label={Text("Nombre")},enabled=vm.writable,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(season.text("sinopsis"),{vm.seasonField(num,"sinopsis",it)},label={Text("Sinopsis de temporada")},enabled=vm.writable,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(season.text("poster_url"),{vm.seasonField(num,"poster_url",it)},label={Text("Portada de temporada")},enabled=vm.writable,modifier=Modifier.fillMaxWidth());Poster(season.text("poster_url"))
            season.array("episodios").forEach {entry ->
                val e=entry.asJsonObject;val ep=e.number("numero") ?: return@forEach
                key(num,ep) {
                var editMore by rememberSaveable(num,ep) {mutableStateOf(false)}
                HorizontalDivider();Text("E$ep · ${e.text("titulo")}",style=MaterialTheme.typography.titleMedium)
                OutlinedTextField(e.text("telegram_url"),{vm.episodeField(num,ep,"telegram_url",it)},label={Text("Telegram E$ep")},enabled=vm.writable,modifier=Modifier.fillMaxWidth())
                TextButton(onClick={editMore=!editMore}) {Text(if(editMore) "Ocultar ficha" else "Ver / editar ficha")}
                if(editMore) {
                    Poster(e.text("imagen_url"))
                    listOf("titulo" to "Título","sinopsis" to "Sinopsis","duracion" to "Duración (minutos)","fecha_emision" to "Fecha de emisión","imagen_url" to "URL de imagen").forEach {(key,label) ->OutlinedTextField(e.text(key),{vm.episodeField(num,ep,key,it)},label={Text(label)},enabled=vm.writable,modifier=Modifier.fillMaxWidth())}
                    Text("TMDB ID: ${e.text("tmdb_id")}",style=MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick={vm.requestPartDeletion(num,ep)},enabled=vm.writable,colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)) {Text("Eliminar episodio del catálogo")}
                }
            }
            OutlinedTextField(nextEpisode,{nextEpisode=it},label={Text("Número de episodio nuevo")},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedButton(onClick={nextEpisode.toIntOrNull()?.let {vm.addEpisode(num,it)}},enabled=vm.writable) {Text("Agregar episodio manual")}
        }
    }}
}
@Composable private fun EnrichmentScreen(vm: AdminViewModel) {
    var groups by remember {mutableStateOf(setOf("fondo","trailer","episodios"))}
    val labels=mapOf("datos_generales" to "Título, año y géneros","sinopsis" to "Sinopsis","poster" to "Portada","fondo" to "Fondo","trailer" to "URL de trailer","creditos" to "Créditos","puntuacion" to "Puntuación","episodios" to "Temporadas / episodios como bloque")
    ScrollContent {
        Text("Enriquecer desde TMDB",style=MaterialTheme.typography.titleLarge)
        Text("La identidad AniList y la fuente principal se conservan. Elegí qué datos reemplazar.")
        vm.enrichment?.let {Poster(it.text("poster_url"),true);Text(it.text("titulo"));Text(it.text("sinopsis"))}
        labels.forEach {(group,label) ->Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(group in groups,{selected ->groups=if(selected) groups+group else groups-group});Text(label)}}
        Button(onClick={vm.applyEnrichment(groups)},enabled=vm.writable) {Text("Aplicar a la revisión")}
        OutlinedButton(onClick={vm.cancelEnrichment()},enabled=!vm.busy) {Text("Cancelar")}
    }
}
