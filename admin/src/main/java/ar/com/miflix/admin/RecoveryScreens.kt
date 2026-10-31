package ar.com.miflix.admin

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.gson.JsonObject

@Composable fun RecoverySourceMenu(vm: AdminViewModel, modifier: Modifier=Modifier,season: Long?=null) {
    var expanded by remember {mutableStateOf(false)}
    Box(modifier) {
        OutlinedButton(onClick={expanded=true},enabled=vm.writable,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Fuentes ▾")}
        DropdownMenu(expanded,onDismissRequest={expanded=false}) {
            DropdownMenuItem(text={Text("TMDB · español")},onClick={expanded=false;vm.startRecovery("TMDB",season,"es-MX")})
            DropdownMenuItem(text={Text("TMDB · consultar otros idiomas")},onClick={expanded=false;vm.startRecovery("TMDB",season,"en-US")})
            DropdownMenuItem(text={Text("TVmaze · series y episodios")},enabled=vm.category!="pelicula",onClick={expanded=false;vm.startRecovery("TVmaze",season)})
        }
    }
}
@Composable internal fun SourceLink(url: String,label: String) {
    val context=LocalContext.current
    TextButton(onClick={
        val uri=Uri.parse(url)
        if(uri.scheme=="https" && uri.host in setOf("www.tvmaze.com","tvmaze.com","www.themoviedb.org","creativecommons.org"))
            try {context.startActivity(Intent(Intent.ACTION_VIEW,uri))} catch(_:Exception) {android.widget.Toast.makeText(context,"No se pudo abrir el enlace.",android.widget.Toast.LENGTH_SHORT).show()}
    }) {Text(label)}
}
@Composable fun MetadataCredits(item: JsonObject) {
    val records=item.get("fuentes_metadata")?.takeIf {it.isJsonObject}?.asJsonObject
        ?.get("recuperacion_selectiva")?.takeIf {it.isJsonObject}?.asJsonObject ?: return
    val maze=records.entrySet().map {it.value}.filter {it.isJsonObject && it.asJsonObject.text("fuente")=="TVmaze"}.map {it.asJsonObject}
    if(maze.isNotEmpty()) {
        Text("Datos seleccionados de TVmaze · CC BY-SA 4.0. Se conservaron la procedencia y los avisos en el catálogo. Sin garantías.",style=MaterialTheme.typography.bodySmall)
        maze.map {it.text("url")}.distinct().forEach {SourceLink(it,"Fuente TVmaze")}
        SourceLink(MetadataRecovery.LICENSE,"Licencia y ShareAlike")
    }
}
@Composable fun RecoverySearchScreen(vm: AdminViewModel) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Text("Recuperar desde ${vm.recoverySource}",style=MaterialTheme.typography.titleLarge)
            Text("La consulta no guarda ni sincroniza. Confirmá la serie o película correcta antes de comparar.")
            OutlinedTextField(vm.recoveryQuery,{vm.recoveryQuery=it},label={Text("Título para buscar")},enabled=!vm.busy,modifier=Modifier.fillMaxWidth())
            if(vm.recoverySource=="TMDB") {
                OutlinedTextField(vm.recoveryLanguage,{vm.recoveryLanguage=it},label={Text("Idioma TMDB: es-MX, en-US, es-ES…")},enabled=!vm.busy,singleLine=true,modifier=Modifier.fillMaxWidth())
                Text("Código ISO del idioma solicitado. No se traduce ni se inventan textos. Si existe tmdb_id se consulta esa identidad.",style=MaterialTheme.typography.bodySmall)
            } else {
                Text("Sinopsis habitualmente en inglés; títulos originales. TVmaze no ofrece selector de traducción. Primero se busca por IMDb cuando está disponible.",style=MaterialTheme.typography.bodySmall)
                SourceLink("https://www.tvmaze.com/api","TVmaze · API pública y atribución")
            }
        }
        val seasons=vm.draft?.array("temporadas")?.mapNotNull {it.asJsonObject.number("numero")}?.distinct().orEmpty()
        if(seasons.isNotEmpty()) item {Text("Temporadas existentes a consultar (máximo 20)",style=MaterialTheme.typography.titleMedium)}
        items(seasons,key={"season-$it"}) {number ->
            Row(verticalAlignment=Alignment.CenterVertically) {
                Checkbox(number in vm.recoverySeasons,{selected ->vm.recoverySeasons=if(selected) vm.recoverySeasons+number else vm.recoverySeasons-number},enabled=!vm.busy)
                Text(if(number==0L) "Especiales (0) · correspondencia manual" else "Temporada $number")
            }
        }
        item {
            Button(onClick={vm.searchRecovery()},enabled=!vm.busy && vm.recoverySeasons.size<=20,modifier=Modifier.fillMaxWidth()) {Text("Buscar candidatos")}
            OutlinedButton(onClick={vm.cancelRecovery()},modifier=Modifier.fillMaxWidth()) {Text(if(vm.busy) "Cancelar consulta" else "Volver sin cambios")}
        }
        items(vm.recoveryCandidates,key={"${it.provider}/${it.id}"}) {candidate ->
            Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(candidate.title,style=MaterialTheme.typography.titleMedium)
                Text("${candidate.year.ifBlank {"Año no informado"}} · ${candidate.provider} · ID ${candidate.id}")
                SourceLink(candidate.url,"Ver ficha de la fuente")
                Button(onClick={vm.retrieveRecovery(candidate)},enabled=!vm.busy,modifier=Modifier.fillMaxWidth()) {Text("Es esta ficha · comparar datos")}
            }}
        }
    }
}
@Composable fun RecoveryPreviewScreen(vm: AdminViewModel) {
    val plan=vm.recoveryPlan ?: return
    var confirm by remember(plan) {mutableStateOf(false)}
    val selected=plan.selected(vm.recoverySelection)
    val allowed=selected.isNotEmpty() && selected.all {it.safe || "${it.path.season}/${it.path.episode}" in vm.recoveryMappings} &&
        (plan.data.candidate.provider !in setOf("TVmaze") || vm.recoveryLicense)
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Text("Comparar y elegir datos",style=MaterialTheme.typography.titleLarge)
            Text("${plan.data.candidate.title} · ${plan.data.candidate.year} · ${plan.data.candidate.provider}")
            Text(plan.data.candidate.language)
            SourceLink(plan.data.candidate.url,"Ver procedencia")
            Text("Solo se seleccionan inicialmente campos faltantes con datos válidos. Los episodios sin ID coincidente requieren confirmar su correspondencia. Nada se guarda al consultar.")
            Text("${selected.size} campos · ${plan.episodeCount(vm.recoverySelection)} episodios seleccionados",style=MaterialTheme.typography.titleMedium)
        }
        items(plan.warnings) {Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.tertiary)}
        plan.changes.groupBy {it.path.season to it.path.episode}.forEach { (coordinate,changes) ->
            if(coordinate.second!=null) item(key="map-$coordinate") {
                val id="${coordinate.first}/${coordinate.second}";val safe=changes.all {it.safe}
                Text(changes.first().mapping.orEmpty(),style=MaterialTheme.typography.titleMedium)
                if(!safe) Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(id in vm.recoveryMappings,{vm.confirmRecoveryMapping(id,it)},enabled=!vm.busy)
                    Text("Confirmo que es el mismo episodio y orden. No es un doble, especial reubicado ni otra numeración.",modifier=Modifier.weight(1f))
                } else Text("Correspondencia por ID TMDB y numeración coincidentes.",style=MaterialTheme.typography.bodySmall)
            }
            items(changes,key={it.path.key}) {change ->
                val canSelect=change.safe || "${change.path.season}/${change.path.episode}" in vm.recoveryMappings
                Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(change.path.key in vm.recoverySelection,{yes ->vm.recoverySelection=if(yes) vm.recoverySelection+change.path.key else vm.recoverySelection-change.path.key},enabled=canSelect && !vm.busy)
                        Text(change.path.label,style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                    }
                    Text("Actual",style=MaterialTheme.typography.labelLarge)
                    Text(change.before?.takeIf {it.isJsonPrimitive}?.asString?.ifBlank {"Vacío"} ?: "Sin dato o formato no textual conservado")
                    Text("Nuevo · ${plan.data.candidate.provider} · ${plan.data.candidate.language}",style=MaterialTheme.typography.labelLarge)
                    Text(change.after.asString)
                    if(change.path.field.endsWith("_url")) {
                        change.before?.takeIf {it.isJsonPrimitive && it.asString.startsWith("https://")}?.let {AsyncImage(it.asString,"Imagen actual",modifier=Modifier.fillMaxWidth().height(120.dp))}
                        AsyncImage(change.after.asString,"Imagen propuesta",modifier=Modifier.fillMaxWidth().height(120.dp))
                    }
                }}
            }
        }
        if(plan.changes.isEmpty()) item {Text("No hay campos nuevos válidos para los episodios existentes. No se crearon ni eliminaron episodios.")}
        if(plan.data.candidate.provider=="TVmaze") item {
            Text("TVmaze · CC BY-SA 4.0. Al importar se conservan fuente, enlace, licencia e indicación de cambios junto a cada campo en fuentes_metadata. Esos avisos viajan en el JSON. Las adaptaciones de esos datos deben compartirse bajo la misma licencia y sin restricciones adicionales; no se atribuyen a TVmaze los videos ni datos de otras fuentes.")
            SourceLink(MetadataRecovery.LICENSE,"Leer licencia CC BY-SA 4.0")
            Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(vm.recoveryLicense,{vm.recoveryLicense=it});Text("Conservar atribución y cumplir ShareAlike al redistribuir esos datos.",Modifier.weight(1f))}
        }
        item {
            Button(onClick={confirm=true},enabled=allowed && vm.writable,modifier=Modifier.fillMaxWidth()) {Text("Aplicar datos seleccionados")}
            OutlinedButton(onClick={vm.cancelRecovery()},modifier=Modifier.fillMaxWidth()) {Text("Cancelar sin aplicar")}
        }
    }
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("Resumen antes de aplicar")},text={Column {
        Text("${selected.size} campos de ${plan.episodeCount(vm.recoverySelection)} episodios. Se aplicarán solo a la revisión y se conservará un respaldo local.")
        Text("Falta después Confirmar y guardar y, por separado, Sincronizar. Los enlaces, IDs y fecha_agregado no se modifican.")
        Text(selected.groupBy {it.path.field}.entries.joinToString("\n") {"${it.key}: ${it.value.size}"})
    }},confirmButton={TextButton(onClick={confirm=false;vm.applyRecovery()}) {Text("Confirmar aplicación")}},dismissButton={TextButton(onClick={confirm=false}) {Text("Volver a comparar")}})
}
