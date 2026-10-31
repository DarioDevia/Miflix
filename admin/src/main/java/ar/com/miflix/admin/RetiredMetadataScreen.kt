package ar.com.miflix.admin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

@Composable fun RetiredMetadataScreen(vm:AdminViewModel) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var exportId by remember {mutableStateOf<String?>(null)}
    var status by remember {mutableStateOf("")}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {uri->
        val id=exportId;exportId=null
        if(uri!=null && id!=null) scope.launch {
            try {withContext(Dispatchers.IO) {
                val raw=vm.privateCleanupBackup(id)
                context.contentResolver.openOutputStream(uri,"wt")?.use {it.write(raw.toByteArray(Charsets.UTF_8))} ?: error("Archivo no disponible")
            };status="Respaldo privado exportado. Contiene el catálogo y datos históricos restringidos: no publicarlo ni compartirlo."}
            catch(_:Exception) {status="No se pudo exportar. El respaldo interno permanece intacto."}
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Text("Revisión de metadatos históricos",style=MaterialTheme.typography.titleLarge)
            Text("TheTVDB fue retirado. Los campos identificados se apartaron con un respaldo privado previo; si había un original verificable, se repuso. No se borraron títulos, temporadas, episodios, enlaces ni fechas de incorporación.")
            Text("La publicación normal continúa con los campos seguros. Los datos faltantes se pueden completar desde TMDB, TVmaze o mediante edición propia. Un nombre «Contenido … · revisar título» es una etiqueta de seguridad, no un título recuperado.")
            Text("La lista conserva el historial de la limpieza, aunque después completes el campo. Consultá la ficha para ver su valor actual. No se atribuyen a TMDB datos antiguos por cambiar una etiqueta.",style=MaterialTheme.typography.bodySmall)
            if(status.isNotBlank()) Text(status)
        }
        items(vm.cleanupIssues.groupBy {it.text("identity")}.entries.toList(),key={it.key}) {(_,rows)->
            Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                val id=rows.first().text("item_id")
                val current=vm.catalog?.let {CatalogRules.items(it).find {r->r.text("id")==id}}
                Text(current?.text("titulo") ?: rows.first().text("titulo"),style=MaterialTheme.typography.titleMedium)
                if(current!=null && current.text("titulo")!=rows.first().text("titulo")) Text("Nombre local anterior: ${rows.first().text("titulo")} (solo para identificar la ficha; no se publica desde este informe).",style=MaterialTheme.typography.bodySmall)
                if(id.isNotBlank()) Text("ID: $id",style=MaterialTheme.typography.bodySmall)
                rows.forEach {row->Text("${RetiredMetadata.label(row.text("path"))}\n${row.text("action")}")}
                if(current!=null) OutlinedButton(onClick={vm.editCleanupItem(id)},enabled=vm.writable && vm.draft==null) {Text("Abrir ficha para completar datos")}
                if(vm.draft!=null) Text("Hay una revisión pendiente: terminála o descartála antes de abrir otra ficha.",style=MaterialTheme.typography.bodySmall)
            }}
        }
        item {Text("Respaldos privados previos",style=MaterialTheme.typography.titleMedium);Text("No contienen credenciales de APIs. Sí pueden contener enlaces privados y los valores retirados. Su exportación no es una publicación; conserválos privadamente.")}
        items(vm.cleanupBackups,key={it}) {id->OutlinedButton(onClick={exportId=id;export.launch("MiFlix-respaldo-privado-${id.take(12)}.json")},modifier=Modifier.fillMaxWidth()) {Text("Exportar respaldo ${id.take(12)}")}}
        item {OutlinedButton(onClick={vm.screen="home"},modifier=Modifier.fillMaxWidth()) {Text("Volver a Inicio")}}
    }
}
