package ar.com.miflix.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable fun SagaDetectionScreen(c:CollectionController) {
    var refresh by remember {mutableStateOf(false)}
    val byId=c.catalogItems.associateBy {it.text("id")}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Text("Detectar sagas en mi catálogo",style=MaterialTheme.typography.titleLarge)
            Text("Solo películas con ID TMDB válido. Consulta pertenencias oficiales; no crea universos temáticos ni busca por parecido. No cambia el catálogo.")
            Button(onClick={c.detectSagas()},enabled=c.writable,modifier=Modifier.fillMaxWidth()) {Text("Analizar / reanudar con caché")}
            OutlinedButton(onClick={refresh=true},enabled=c.writable,modifier=Modifier.fillMaxWidth()) {Text("Volver a consultar TMDB sin caché")}
            Text("Una solicitud por vez; caché local por 7 días. Los fallos se reintentan al reanudar. No se continúa en segundo plano al salir.",style=MaterialTheme.typography.bodySmall)
            val p=c.sagaProgress
            Text("${p.phase}\nPelículas: ${p.total} · analizadas: ${p.analyzed} · pendientes: ${p.pending}\nSin ID válido: ${p.missing} · consultas fallidas: ${p.failed}\nSagas detectadas: ${p.detected} · respuestas reutilizadas: ${p.reused}")
            if(!p.complete && p.phase.isNotBlank()) Text("Resultado parcial o interrumpido. No se afirma cobertura completa.",color=MaterialTheme.colorScheme.tertiary)
            if(c.draft!=null) Text("Hay un borrador guardado. Podés analizar, pero terminá o descartá ese borrador antes de aplicar propuestas.")
            Text("Al aprobar en lote: nuevas colecciones usan los datos e imágenes principales de TMDB; las existentes solo agregan integrantes con ID exacto y único. Para elegir campos, versiones o imágenes alternativas usá Revisar en editor.")
            Button(onClick={c.reviewSagas()},enabled=c.writable && c.draft==null && c.selectedSagas.isNotEmpty(),modifier=Modifier.fillMaxWidth()) {Text("Revisar ${c.selectedSagas.size} propuestas seleccionadas")}
        }
        items(c.sagaProposals.filter {it.id !in c.ignoredSagas},key={it.id}) {p ->
            Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(p.id in c.selectedSagas,{yes ->c.selectedSagas=if(yes) c.selectedSagas+p.id else c.selectedSagas-p.id},enabled=c.writable && p.actionable)
                    Text(p.detail.text("name"),style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                }
                CollectionArtwork(TmdbCollections.url(p.detail.text("poster_path"),"poster_url"),"Póster propuesto TMDB",140)
                Text("TMDB ${p.id} · ${p.status}")
                Text("${p.matches.count {it.exact.isNotEmpty()}} películas TMDB presentes · ${p.exact.size} referencias seguras · ${p.matches.size} películas totales en TMDB")
                if(p.current!=null) {
                    Text("Actual: ${p.current!!.text("nombre")} · ${CollectionRules.ids(p.current!!).size} integrantes. Se conservarán sus imágenes, nombre y orden.")
                    Text("Nuevas referencias propuestas: ${p.additions.size}")
                }
                var expanded by remember(p.id) {mutableStateOf(false)}
                TextButton(onClick={expanded=!expanded}) {Text(if(expanded) "Ocultar integrantes" else "Ver presentes, ausentes y actuales")}
                if(expanded) {
                    p.current?.let {row ->Text("Integrantes actuales: "+CollectionRules.ids(row).joinToString {byId[it]?.text("titulo") ?: "Ausente temporal · $it"})}
                    p.matches.forEach {m ->Text("${m.part.text("title")} · "+when {m.exact.size>1 ->"Ambigua: ${m.exact.size} versiones, excluida del lote";m.exact.size==1 ->"Presente: ${byId[m.exact.single()]?.text("titulo")} [${m.exact.single()}]";else ->"Ausente por ID; no se creará"})}
                }
                OutlinedButton(onClick={c.editSaga(p.id)},enabled=c.writable && c.draft==null && p.existing.size<=1,modifier=Modifier.fillMaxWidth()) {Text("Revisar en editor / elegir campos")}
                TextButton(onClick={c.ignoreSaga(p.id)},enabled=c.writable) {Text("Ignorar en esta revisión")}
            }}
        }
        item {
            if(c.ignoredSagas.isNotEmpty()) Text("${c.ignoredSagas.size} propuestas ignoradas en esta revisión; vuelven al analizar de nuevo.")
            OutlinedButton(onClick={c.openList()},enabled=!c.busy,modifier=Modifier.fillMaxWidth()) {Text("Volver a colecciones")}
            CollectionTmdbCredits()
        }
    }
    if(refresh) AlertDialog(onDismissRequest={refresh=false},title={Text("Reconsultar sin caché")},text={Text("Se descarta solo la caché de detección y se consultan nuevamente las películas. Colecciones, borradores y catálogo permanecen intactos.")},confirmButton={TextButton(onClick={refresh=false;c.detectSagas(true)}) {Text("Volver a consultar")}},dismissButton={TextButton(onClick={refresh=false}) {Text("Cancelar")}})
    c.sagaConfirmation?.let {next ->
        val before=CollectionRules.list(c.data);val after=CollectionRules.list(next)
        val changed=after.filter {row ->before.none {it==row}}
        AlertDialog(onDismissRequest={c.cancelSagaConfirmation()},title={Text("Confirmar colecciones seleccionadas")},text={Column(Modifier.heightIn(max=320.dp).verticalScroll(rememberScrollState())) {
            Text("${changed.size} colecciones se crearán o actualizarán localmente. Ninguna película se modifica. Se conservará un respaldo del estado anterior. No se publica en Cloudflare.")
            changed.take(12).forEach {row ->val old=before.find {it.text("id")==row.text("id")};Text("${row.text("nombre")}: ${if(old==null) "nueva" else "actualización"}, +${CollectionRules.ids(row).size-(old?.let(CollectionRules::ids)?.size ?: 0)} integrantes")}
            if(changed.size>12) Text("Y ${changed.size-12} colecciones más revisadas en la lista.")
        }},confirmButton={TextButton(onClick={c.confirmSagas()},enabled=c.writable) {Text("Confirmar guardado local")}},dismissButton={TextButton(onClick={c.cancelSagaConfirmation()}) {Text("Cancelar")}})
    }
}
@Composable fun CollectionSyncStatusScreen(c:CollectionController) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Text("Sincronización de colecciones",style=MaterialTheme.typography.titleLarge)
            Text("No habilitada en 0.1.9: el Worker desplegado y su protección contra escritores antiguos/concurrentes no están verificados.")
            Text("Tus colecciones siguen locales y se pueden exportar/restaurar. Guardarlas o detectarlas no publica ni modifica películas. Mobile y TV actuales todavía no las muestran.")
            Text("Bloqueos comprobados en el Worker fuente: PUT reemplaza todo el catálogo; no hay revisión condicional, respaldo previo ni operación exclusiva de colecciones. Una publicación antigua puede borrar esos campos. KV no dispone de compare-and-swap atómico.")
            Text("La documentación técnica de esta versión incluye pruebas sintéticas que reproducen esos riesgos y un protocolo para resolverlos antes de desplegar. Esta pantalla no hace solicitudes al servidor ni representa una conexión verificada.")
            Text("TheTVDB: su restricción de publicación habitual permanece. Las colecciones TMDB se guardan sin ese bloqueo; la sincronización separada requiere el nuevo protocolo de servidor, aún pendiente.")
            OutlinedButton(onClick={c.openList()},modifier=Modifier.fillMaxWidth()) {Text("Volver y exportar respaldo")}
        }
    }
}
