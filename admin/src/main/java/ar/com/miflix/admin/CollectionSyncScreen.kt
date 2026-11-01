package ar.com.miflix.admin

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable fun CollectionSyncStatusScreen(c:CollectionController) {
    val review=c.cloudReview
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Text("Sincronizar colecciones con Cloudflare",style=MaterialTheme.typography.titleLarge)
            Text("Publicación manual del conjunto completo de colecciones guardadas. No incluye borradores ni publica películas. Usá un solo administrador a la vez: el GET previo no garantiza evitar conflictos concurrentes y KV puede devolver una copia anterior.")
            Text("Antes de la primera sincronización, exportá un respaldo desde Colecciones. Consultar no modifica ninguna copia.",style=MaterialTheme.typography.bodySmall)
            Button(onClick={c.readCloudCollections()},enabled=c.writable,modifier=Modifier.fillMaxWidth()) {Text("Consultar y comparar con Cloudflare")}
        }
        if(review!=null) {
            item {
                Text("Locales: ${CollectionRules.list(review.local).size} · remotas: ${CollectionRules.list(review.remote).size}",style=MaterialTheme.typography.titleMedium)
                Text("Publicar: ${review.changes.count {it.before==null}} agregar · ${review.changes.count {it.before!=null && it.after!=null}} cambiar · ${review.changes.count {it.after==null}} eliminar")
                if(review.orderChanged) Text("También cambia el orden del arreglo completo. Actual: ${CollectionRules.list(review.remote).map {it.text("id")}}. Propuesto: ${CollectionRules.list(review.local).map {it.text("id")}}.")
                if(review.local==review.remote) Text("Las colecciones coinciden en la lectura recibida.")
                if(review.empty) Text("ATENCIÓN: publicar la lista local vacía elimina todas las colecciones remotas.",color=MaterialTheme.colorScheme.error)
            }
            items(review.changes,key={"diff-${it.id}"}) {change -> CollectionChangeCard(change)}
            item {
                Button(onClick={c.requestCloudPublish()},enabled=c.writable && c.cloudAck.isBlank(),modifier=Modifier.fillMaxWidth()) {Text("Revisar publicación de ${CollectionRules.list(review.local).size} colecciones")}
                Text("Recuperación desde Cloudflare",style=MaterialTheme.typography.titleMedium)
                Text("Elegí qué colecciones recuperar. Las demás se conservan; nunca se reemplazan automáticamente colecciones ni borradores locales. Los IDs e integrantes remotos se mantienen incluso si algún título no aparece temporalmente en el catálogo.")
            }
            items(CollectionRules.list(review.remote),key={"remote-${it.text("id")}"}) {row ->
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(row.text("id") in c.selectedRemote,{yes ->c.selectedRemote=if(yes) c.selectedRemote+row.text("id") else c.selectedRemote-row.text("id")},enabled=!c.busy)
                    Text("${row.text("nombre")} · ${row.text("id")} · ${CollectionRules.ids(row).size} integrantes",Modifier.weight(1f))
                }
            }
            item {
                if(CollectionRules.list(review.remote).isEmpty()) Text("El conjunto remoto está vacío. No hay colecciones para recuperar.")
                OutlinedButton(onClick={c.requestCloudRecovery()},enabled=c.writable && c.selectedRemote.isNotEmpty(),modifier=Modifier.fillMaxWidth()) {Text("Comparar ${c.selectedRemote.size} seleccionadas para recuperar")}
            }
        }
        item {OutlinedButton(onClick={c.openList()},enabled=!c.busy,modifier=Modifier.fillMaxWidth()) {Text("Volver a colecciones")}}
    }
    if(c.cloudConfirmation && review!=null) {
        var destructive by remember(review) {mutableStateOf(false)}
        var empty by remember(review) {mutableStateOf(false)}
        AlertDialog(onDismissRequest={c.cancelCloudConfirmation()},title={Text("Confirmar publicación en Cloudflare")},text={Column(Modifier.heightIn(max=350.dp).verticalScroll(rememberScrollState())) {
            Text("Se enviarán ${CollectionRules.list(review.local).size} colecciones guardadas mediante PUT /admin/colecciones. Se reemplaza el conjunto remoto completo. La copia local y el catálogo audiovisual se conservan.")
            review.changes.forEach {Text("${it.kind}: ${it.after?.text("nombre") ?: it.before?.text("nombre")} · ${it.id}")}
            if(review.orderChanged) Text("Cambia también el orden del arreglo.")
            if(review.destructive) Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(destructive,{destructive=it});Text("Confirmo los cambios y eliminaciones remotos indicados.",Modifier.weight(1f))}
            if(review.empty) Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(empty,{empty=it});Text("Confirmo publicar CERO colecciones y eliminar todas las remotas.",Modifier.weight(1f))}
            Text("Se vuelve a consultar antes del PUT. Esto no impide absolutamente escrituras concurrentes ni lecturas antiguas de KV.",style=MaterialTheme.typography.bodySmall)
        }},confirmButton={TextButton(onClick={c.confirmCloudPublish(destructive,empty)},enabled=!c.busy && (!review.destructive || destructive) && (!review.empty || empty)) {Text("Confirmar publicación")}},dismissButton={TextButton(onClick={c.cancelCloudConfirmation()}) {Text("Cancelar")}})
    }
    c.cloudRecovery?.let {recovery ->
        AlertDialog(onDismissRequest={c.cancelCloudConfirmation()},title={Text("Confirmar recuperación local")},text={Column(Modifier.heightIn(max=350.dp).verticalScroll(rememberScrollState())) {
            Text("${recovery.changes.size} colecciones locales se agregarán o cambiarán. No se elimina ninguna colección no seleccionada. Se conserva el estado anterior para revertir desde Colecciones. No se publica nada.")
            recovery.changes.forEach {CollectionChangeCard(it)}
        }},confirmButton={TextButton(onClick={c.confirmCloudRecovery()},enabled=c.writable) {Text("Confirmar recuperación")}},dismissButton={TextButton(onClick={c.cancelCloudConfirmation()}) {Text("Cancelar")}})
    }
}

@Composable private fun CollectionChangeCard(change:CollectionChange) {
    Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text("${change.kind}: ${change.after?.text("nombre") ?: change.before?.text("nombre")}",style=MaterialTheme.typography.titleMedium)
        Text("ID: ${change.id}",style=MaterialTheme.typography.bodySmall)
        change.fields.forEach {field ->
            Text(field,style=MaterialTheme.typography.labelLarge)
            Text("Actual: ${change.before?.get(field) ?: "Sin dato"}")
            Text("Propuesto: ${change.after?.get(field) ?: "Eliminar campo"}")
        }
    }}
}
