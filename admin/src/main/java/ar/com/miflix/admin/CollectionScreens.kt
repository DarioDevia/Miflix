package ar.com.miflix.admin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable fun CollectionsScreen(c:CollectionController) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var delete by remember {mutableStateOf<JsonObject?>(null)}
    var discard by remember {mutableStateOf(false)}
    var undo by remember {mutableStateOf(false)}
    var exporting by remember {mutableStateOf<String?>(null)}
    var transfer by remember {mutableStateOf(false)}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {uri ->
        val raw=exporting;exporting=null
        if(uri!=null && raw!=null) scope.launch {
            transfer=true
            try {withContext(Dispatchers.IO) {context.contentResolver.openOutputStream(uri,"wt")?.use {it.write(raw.toByteArray(Charsets.UTF_8))} ?: error("No se pudo abrir el archivo.")};c.notify("Respaldo de colecciones exportado. No incluye películas, enlaces ni credenciales; conservá también el catálogo por separado.")}
            catch(_:Exception) {c.notify("No se pudo exportar el respaldo. Las colecciones locales se conservaron.")}
            finally {transfer=false}
        }
    }
    val restore=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri ->
        if(uri!=null) scope.launch {
            transfer=true
            try {
                val raw=withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use {stream ->
                        val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                        while(out.size()<=CollectionRules.MAX_BYTES) {val n=stream.read(buffer,0,minOf(buffer.size,CollectionRules.MAX_BYTES+1-out.size()));if(n<0) break;out.write(buffer,0,n)}
                        require(out.size()<=CollectionRules.MAX_BYTES);String(out.toByteArray(),Charsets.UTF_8)
                    } ?: error("Archivo no disponible.")
                };c.readRestore(raw)
            }catch(_:Exception) {c.notify("No se pudo leer el respaldo o supera 2 MB. No se modificó nada.")}
            finally {transfer=false}
        }
    }
    Column(Modifier.fillMaxSize()) {
        Text("Colecciones · solo este teléfono",Modifier.padding(horizontal=16.dp),style=MaterialTheme.typography.titleLarge)
        Text("No se publican todavía en Mobile/TV ni afectan la sincronización normal del catálogo.",Modifier.padding(16.dp),style=MaterialTheme.typography.bodySmall)
        if(c.message.isNotBlank()) Text(c.message,Modifier.padding(horizontal=16.dp),color=MaterialTheme.colorScheme.tertiary)
        if(c.busy || transfer) LinearProgressIndicator(Modifier.fillMaxWidth())
        Box(Modifier.weight(1f)) { when(c.page) {
            "list" -> LazyColumn(Modifier.fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                item {
                    if(c.draft!=null) {
                        Button(onClick={c.resume()},enabled=!c.busy) {Text("Continuar borrador de colección")}
                        OutlinedButton(onClick={discard=true},enabled=c.writable) {Text("Descartar borrador")}
                    } else {
                        Button(onClick={c.begin()},enabled=c.writable,modifier=Modifier.fillMaxWidth()) {Text("Nueva colección manual")}
                        OutlinedButton(onClick={c.begin("tmdb")},enabled=c.writable,modifier=Modifier.fillMaxWidth()) {Text("Importar saga TMDB")}
                        OutlinedButton(onClick={c.begin("hybrid")},enabled=c.writable,modifier=Modifier.fillMaxWidth()) {Text("Importar y personalizar")}
                    }
                    OutlinedButton(onClick={c.showSagas()},enabled=c.writable,modifier=Modifier.fillMaxWidth()) {Text("Detectar sagas en mi catálogo")}
                    OutlinedButton(onClick={c.pageSyncInfo()},enabled=!c.busy,modifier=Modifier.fillMaxWidth()) {Text("Estado de sincronización de colecciones")}
                    OutlinedButton(onClick={try {exporting=c.export();export.launch("MiFlix-colecciones.json")} catch(e:Exception) {c.notify(e.message.orEmpty())}},enabled=c.writable && !transfer,modifier=Modifier.fillMaxWidth()) {Text("Exportar respaldo de colecciones")}
                    OutlinedButton(onClick={restore.launch(arrayOf("application/json","text/plain","application/octet-stream"))},enabled=!c.busy && !transfer,modifier=Modifier.fillMaxWidth()) {Text("Restaurar respaldo desde archivo")}
                    if(c.hasBackup) OutlinedButton(onClick={undo=true},enabled=c.writable,modifier=Modifier.fillMaxWidth()) {Text("Restaurar estado anterior de colecciones")}
                    Text("El respaldo exporta solo colecciones guardadas. Un borrador no está incluido. Para Mundo Marvel, elegí manual o híbrida.",style=MaterialTheme.typography.bodySmall)
                }
                items(CollectionRules.list(c.data).sortedWith(compareBy<JsonObject>{it.number("orden") ?: 0}.thenBy {it.text("nombre")}),key={it.text("id")}) {row ->
                    Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp)) {
                        CollectionArtwork(row.text("poster_url"),"Póster",120)
                        Text(row.text("nombre"),style=MaterialTheme.typography.titleMedium)
                        Text("${CollectionRules.ids(row).size} títulos · ${row.text("origen")} · orden ${row.text("orden")} · ${if(row.text("visible")=="true") "visible (futuro cliente)" else "oculta"}")
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick={c.edit(row.text("id"))},enabled=c.writable) {Text("Editar")}
                            TextButton(onClick={delete=row},enabled=c.writable) {Text("Eliminar")}
                        }
                    }}
                }
            }
            "edit" -> CollectionEditor(c,onDiscard={discard=true})
            "preview" -> CollectionPreview(c)
            "tmdb" -> CollectionSearchScreen(c)
            "importPreview" -> CollectionImportScreen(c)
            "images" -> CollectionImagesScreen(c)
            "sagas" -> SagaDetectionScreen(c)
            "collectionSync" -> CollectionSyncStatusScreen(c)
        }}
        if(c.busy) OutlinedButton(onClick={if(c.page=="sagas") c.cancelRequest() else {c.cancelRequest();c.backToEdit()}},modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp)) {Text("Cancelar consulta sin aplicar")}
    }
    delete?.let {row ->AlertDialog(onDismissRequest={delete=null},title={Text("Eliminar colección")},text={Text("¿Eliminar ${row.text("nombre")}? Sus películas, series y anime permanecen intactos en el catálogo. Se conservará el estado anterior de colecciones.")},confirmButton={TextButton(onClick={delete=null;c.delete(row.text("id"))}) {Text("Eliminar colección")}},dismissButton={TextButton(onClick={delete=null}) {Text("Cancelar")}})}
    if(discard) AlertDialog(onDismissRequest={discard=false},title={Text("Descartar borrador")},text={Text("Se perderán las ediciones de esta colección sin guardar. El catálogo y las colecciones guardadas se conservan.")},confirmButton={TextButton(onClick={discard=false;c.discard()}) {Text("Descartar")}},dismissButton={TextButton(onClick={discard=false}) {Text("Cancelar")}})
    if(undo) AlertDialog(onDismissRequest={undo=false},title={Text("Restaurar estado anterior")},text={Text("Se reemplazará la lista local de colecciones por el estado anterior. No cambia ningún título del catálogo. Terminá o descartá el borrador antes de restaurar.")},confirmButton={TextButton(onClick={undo=false;c.undo()}) {Text("Restaurar")}},dismissButton={TextButton(onClick={undo=false}) {Text("Cancelar")}})
    c.restorePreview?.let {root ->AlertDialog(onDismissRequest={c.cancelRestore()},title={Text("Revisar respaldo antes de restaurar")},text={Text("${CollectionRules.list(root).size} colecciones. Se reemplazarán las colecciones locales y su borrador. Los títulos del catálogo, IDs y enlaces no cambian. Los IDs de títulos ausentes se conservarán. Se respalda el estado local anterior si es válido.")},confirmButton={TextButton(onClick={c.confirmRestore()}) {Text("Confirmar restauración")}},dismissButton={TextButton(onClick={c.cancelRestore()}) {Text("Cancelar")}})}
}
@Composable internal fun CollectionArtwork(url:String,label:String,height:Int=150) {
    if(url.isNotBlank() && CollectionRules.validUrl(url)) {
        var failed by remember(url) {mutableStateOf(false)}
        if(failed) Text("No se pudo cargar $label; podés cambiar la URL.") else AsyncImage(url,label,modifier=Modifier.fillMaxWidth().height(height.dp),onError={failed=true})
    }
}
@Composable private fun CollectionEditor(c:CollectionController,onDiscard:()->Unit) {
    val d=c.draft ?: return
    var search by rememberSaveable {mutableStateOf("")};var filter by rememberSaveable {mutableStateOf("todos")}
    var selected by remember(d.text("id")) {mutableStateOf<Set<String>>(emptySet())}
    var order by remember(d.text("orden")) {mutableStateOf(d.text("orden"))}
    val ids=CollectionRules.ids(d);val local=c.catalogItems;val byId=local.associateBy {it.text("id")}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {
            Text("Editar colección",style=MaterialTheme.typography.titleMedium)
            Text("ID estable: ${d.text("id")}",style=MaterialTheme.typography.bodySmall)
            for((key,label) in listOf("nombre" to "Nombre","descripcion" to "Descripción","poster_url" to "URL HTTPS del póster","backdrop_url" to "URL HTTPS del fondo"))
                OutlinedTextField(d.text(key),{c.field(key,it)},label={Text(label)},modifier=Modifier.fillMaxWidth(),enabled=c.writable)
            OutlinedTextField(order,{order=it},label={Text("Orden de presentación (0 a 1000000)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            OutlinedButton(onClick={c.field("orden",order)},enabled=c.writable) {Text("Aplicar orden")}
            Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(d.text("visible")=="true",{c.visible(it)},enabled=c.writable);Text("Visible en futuros clientes compatibles")}
            CollectionArtwork(d.text("poster_url"),"Póster",150);CollectionArtwork(d.text("backdrop_url"),"Fondo",140)
            OutlinedButton(onClick={c.startTmdb()},enabled=c.writable,modifier=Modifier.fillMaxWidth()) {Text("Consultar colección TMDB / personalizar")}
            if(d.number("tmdb_collection_id")!=null) OutlinedButton(onClick={c.loadImages()},enabled=c.writable,modifier=Modifier.fillMaxWidth()) {Text("Elegir imágenes alternativas TMDB")}
            Text("Integrantes · ${ids.size}",style=MaterialTheme.typography.titleMedium)
        }
        items(ids,key={"member-$it"}) {id ->val item=byId[id];val index=ids.indexOf(id)
            Card {Column(Modifier.padding(10.dp)) {
                Text(item?.text("titulo") ?: "Título temporalmente ausente · $id")
                Text(item?.let {"${it.text("tipo")} · ${it.text("anio")}"} ?: "Referencia conservada; no se elimina por ausencia del catálogo.",style=MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick={c.move(index,-1)},enabled=c.writable && index>0) {Text("↑")}
                    OutlinedButton(onClick={c.move(index,1)},enabled=c.writable && index<ids.lastIndex) {Text("↓")}
                    TextButton(onClick={c.remove(id)},enabled=c.writable) {Text("Quitar")}
                }
            }}
        }
        item {
            Text("Agregar desde MiFlix",style=MaterialTheme.typography.titleMedium)
            OutlinedTextField(search,{search=it},label={Text("Buscar título o año")},modifier=Modifier.fillMaxWidth())
            Row {listOf("todos" to "Todos","pelicula" to "Pelís").forEach {(key,label)->FilterChip(filter==key,{filter=key},label={Text(label)})}}
            Row {listOf("serie" to "Series","anime" to "Anime").forEach {(key,label)->FilterChip(filter==key,{filter=key},label={Text(label)})}}
            Button(onClick={c.add(selected);selected=emptySet()},enabled=c.writable && selected.isNotEmpty(),modifier=Modifier.fillMaxWidth()) {Text("Agregar ${selected.size} seleccionados")}
        }
        items(local.filter {(filter=="todos" || it.text("tipo")==filter) && (search.isBlank() || "${it.text("titulo")} ${it.text("anio")}".contains(search,true))},key={"pick-${it.text("id")}"}) {item ->
            val id=item.text("id");val included=id in ids
            Row(verticalAlignment=Alignment.CenterVertically) {
                Checkbox(included || id in selected,{yes->selected=if(yes) selected+id else selected-id},enabled=c.writable && !included)
                Text("${item.text("titulo")} · ${item.text("anio")} · ${item.text("tipo")}${if(included) " · incluido" else ""}",Modifier.weight(1f))
            }
        }
        item {
            Button(onClick={c.preview()},enabled=c.writable,modifier=Modifier.fillMaxWidth()) {Text("Previsualizar antes de guardar")}
            OutlinedButton(onClick={c.openList()},enabled=!c.busy,modifier=Modifier.fillMaxWidth()) {Text("Volver · conservar borrador")}
            TextButton(onClick=onDiscard,enabled=c.writable) {Text("Descartar borrador de colección")}
        }
    }
}
@Composable private fun CollectionPreview(c:CollectionController) {
    val d=c.draft ?: return
    val titles=c.catalogItems.associateBy {it.text("id")}
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text(d.text("nombre"),style=MaterialTheme.typography.headlineSmall);Text(d.text("descripcion"));CollectionArtwork(d.text("poster_url"),"Póster",220);CollectionArtwork(d.text("backdrop_url"),"Fondo",160)
            Text("${d.text("origen")} · ${CollectionRules.ids(d).size} integrantes · orden ${d.text("orden")}")}
        items(CollectionRules.ids(d)) {Text(titles[it]?.text("titulo") ?: "ID ausente conservado: $it")}
        item {Text("Guardar afecta solo a esta colección local. No guarda ni publica películas ni modifica sus metadatos.")
            Button(onClick={c.save()},enabled=c.writable,modifier=Modifier.fillMaxWidth()) {Text("Confirmar y guardar colección local")}
            OutlinedButton(onClick={c.backToEdit()},modifier=Modifier.fillMaxWidth()) {Text("Volver a editar")}}
    }
}
@Composable private fun CollectionSearchScreen(c:CollectionController) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Text("Buscar saga oficial TMDB",style=MaterialTheme.typography.titleMedium)
            Text("Mundo Marvel/DC no equivale a una saga oficial. Usá manual o híbrida para combinar películas, series y anime.")
            OutlinedTextField(c.query,{c.query=it},label={Text("Nombre o ID de colección")},enabled=!c.busy,modifier=Modifier.fillMaxWidth())
            Row {FilterChip(c.language=="es-AR",{c.language="es-AR"},label={Text("Español AR")});FilterChip(c.language=="en-US",{c.language="en-US"},label={Text("Inglés (explícito)")})}
            Text("Idioma solicitado: la fuente puede devolver texto original si no tiene traducción. Revisalo antes de aplicar. Si falta información, podés consultar inglés explícitamente. Admin no cambia de idioma ni sobrescribe datos manuales al consultar.",style=MaterialTheme.typography.bodySmall)
            Button(onClick={c.search()},enabled=!c.busy,modifier=Modifier.fillMaxWidth()) {Text("Buscar colecciones")}
            OutlinedButton(onClick={c.backToEdit()},modifier=Modifier.fillMaxWidth()) {Text("Cancelar consulta · volver a editar")}
        }
        items(c.results,key={it.number("id")!!}) {row ->Card {Column(Modifier.padding(12.dp)) {
            CollectionArtwork(TmdbCollections.url(row.text("poster_path"),"poster_url"),"Colección TMDB",120)
            Text("${row.text("name")} · ID ${row.number("id")}",style=MaterialTheme.typography.titleMedium)
            Button(onClick={c.retrieve(row.number("id")!!)},enabled=!c.busy) {Text("Es esta saga · comparar")}
        }}}
        if(c.searchPages>1) item {Row {
            OutlinedButton(onClick={c.search(c.searchPage-1)},enabled=!c.busy && c.searchPage>1) {Text("Anterior")}
            Text(" ${c.searchPage}/${c.searchPages} ")
            OutlinedButton(onClick={c.search(c.searchPage+1)},enabled=!c.busy && c.searchPage<c.searchPages) {Text("Siguiente")}
        }}
        item {CollectionTmdbCredits()}
    }
}
@Composable private fun CollectionImportScreen(c:CollectionController) {
    val detail=c.details ?: return
    var confirmation by remember(detail) {mutableStateOf(false)}
    val draft=c.draft ?: return
    val titles=c.catalogItems.associateBy {it.text("id")}
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text("Comparar · idioma solicitado ${c.detailsLanguage}",style=MaterialTheme.typography.titleLarge)
            Text("${detail.text("name")} · ID ${detail.number("id")}. Se conservan los integrantes y datos actuales salvo los campos seleccionados. Ninguna película se crea.")}
        for((field,label) in listOf("nombre" to "Nombre","descripcion" to "Descripción","poster_url" to "Póster","backdrop_url" to "Fondo")) item(key=field) {
            val proposed=c.proposed(field)
            if(proposed.isNotBlank()) Card {Column(Modifier.padding(10.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(field in c.selectedFields,{yes->c.selectedFields=if(yes) c.selectedFields+field else c.selectedFields-field},enabled=!c.busy);Text(label)}
                Text("Actual: ${draft.text(field).ifBlank {"Sin dato"}}")
                Text("TMDB (solicitado ${c.detailsLanguage}): $proposed")
            }} else Text("$label: no disponible en esta respuesta; el actual se conserva.")
        }
        items(c.matches,key={it.part.number("id")!!}) {match ->Card {Column(Modifier.padding(10.dp)) {
            Text("${match.part.text("title")} · ${match.part.text("release_date").take(4)} · TMDB ${match.part.number("id")}")
            Text(when {match.exact.size==1 -> "Coincidencia por TMDB ID y tipo película; seleccioná para agregar.";match.exact.size>1 ->"Coincidencia ambigua: varias versiones con ese TMDB ID; elegí explícitamente.";match.possible.isNotEmpty()->"Coincidencia dudosa por nombre y año, sin TMDB ID. Confirmá comparando las fichas.";else->"No encontrada en MiFlix. No se creará."},style=MaterialTheme.typography.bodySmall)
            val options=match.exact.ifEmpty {match.possible}
            options.forEach {id ->val row=titles[id];val included=id in CollectionRules.ids(draft)
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(included || id in c.selectedMembers,{yes->c.selectedMembers=if(yes) c.selectedMembers+id else c.selectedMembers-id},enabled=!included && !c.busy)
                    Text("${row?.text("titulo")} · ${row?.text("anio")} · ${row?.text("calidad")} · ID $id${if(included) " · ya incluido" else ""}",Modifier.weight(1f))
                }
            }
        }}}
        item {
            Text("${c.selectedFields.size} campos y ${c.selectedMembers.size} títulos elegidos.")
            Button(onClick={confirmation=true},enabled=!c.busy && (c.selectedFields.isNotEmpty() || c.selectedMembers.isNotEmpty()),modifier=Modifier.fillMaxWidth()) {Text("Aplicar selección al borrador")}
            OutlinedButton(onClick={c.backToEdit()},modifier=Modifier.fillMaxWidth()) {Text("Cancelar importación sin aplicar")}
            CollectionTmdbCredits()
        }
    }
    if(confirmation) AlertDialog(onDismissRequest={confirmation=false},title={Text("Confirmar selección TMDB")},text={Text("Se aplicarán ${c.selectedFields.size} campos y se agregarán ${c.selectedMembers.size} referencias a títulos ya existentes. Todavía falta previsualizar y guardar la colección local. Los registros audiovisuales permanecen intactos.")},confirmButton={TextButton(onClick={confirmation=false;c.applyImport()}) {Text("Confirmar selección")}},dismissButton={TextButton(onClick={confirmation=false}) {Text("Volver a comparar")}})
}
@Composable private fun CollectionImagesScreen(c:CollectionController) {
    var filter by rememberSaveable {mutableStateOf("todos")}
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text("Elegir póster o fondo",style=MaterialTheme.typography.titleLarge)
            Text("Todas las alternativas provienen de la colección TMDB elegida. La miniatura usa un tamaño reducido; se guarda solo la URL.")
            Row {listOf("todos","es","en","sin idioma").forEach {lang->FilterChip(filter==lang,{filter=lang},label={Text(lang)})}}
            OutlinedButton(onClick={c.backToEdit()}) {Text("Cancelar · conservar imágenes actuales")}}
        items(c.images.filter {filter=="todos" || it.language==filter},key={it.field+it.url}) {image ->Card {Column(Modifier.padding(12.dp)) {
            CollectionArtwork(image.preview,if(image.field=="poster_url") "Póster TMDB" else "Fondo TMDB",180)
            Text("${if(image.field=="poster_url") "Póster" else "Fondo"} · ${image.language}")
            Button(onClick={c.chooseImage(image)},enabled=!c.busy) {Text("Usar esta imagen")}
        }}}
        if(c.images.isEmpty()) item {Text("No hay imágenes disponibles. Podés conservar las actuales o introducir una URL HTTPS personalizada.")}
        item {CollectionTmdbCredits()}
    }
}
@Composable internal fun CollectionTmdbCredits() {
    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.tmdb_logo),"TMDB",modifier=Modifier.width(90.dp).height(30.dp))
    Text("This product uses the TMDB API but is not endorsed or certified by TMDB.",style=MaterialTheme.typography.bodySmall)
    SourceLink("https://www.themoviedb.org","Datos e imágenes de TMDB")
}
