package ar.com.miflix.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.gson.JsonElement

@Composable internal fun DirectMetadataScreen(vm:AdminViewModel) {
    val preview=vm.directPreview ?: return
    val kind=when(preview.link.mediaType) {"movie"->"Película TMDB";"tv"->"Serie TMDB";else->"Anime AniList"}
    fun display(value:JsonElement?):String=when {
        value==null || value.isJsonNull -> "Sin dato"
        value.isJsonPrimitive -> value.asString.ifBlank {"Sin dato"}.take(800)
        value.isJsonArray -> value.asJsonArray.joinToString(", ") {if(it.isJsonPrimitive) it.asString else ""}.ifBlank {"Sin dato"}.take(800)
        else -> "Dato no disponible"
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Text("Revisar ficha del enlace",style=MaterialTheme.typography.titleLarge)
            Text("${preview.candidate.text("titulo")} · ${preview.candidate.text("anio")}")
            Text("$kind · ID ${preview.link.id}",style=MaterialTheme.typography.titleMedium)
            Text(preview.link.canonicalUrl,style=MaterialTheme.typography.bodySmall)
            AsyncImage(preview.candidate.text("poster_url"),contentDescription="Portada de la ficha",modifier=Modifier.height(180.dp))
            Text("Elegí los campos que querés incorporar. Solo se marcan inicialmente datos faltantes. Los enlaces Telegram, fechas, audio, calidad y temporadas existentes se conservan.")
            Text("Esta consulta no agrega temporadas ni episodios. Para consultarlos después, usá las funciones existentes con su revisión habitual.",style=MaterialTheme.typography.bodySmall)
        }
        items(DirectMetadata.available(preview.candidate).toList(),key={it}) {key ->
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(key in vm.directSelection,{checked->vm.directSelection=if(checked) vm.directSelection+key else vm.directSelection-key},enabled=vm.writable)
                    Text(DirectMetadata.labels.getValue(key),style=MaterialTheme.typography.titleMedium)
                }
                Text("Actual: ${display(preview.before.get(key))}",style=MaterialTheme.typography.bodySmall)
                Text("Fuente: ${display(preview.candidate.get(key))}",style=MaterialTheme.typography.bodySmall)
                HorizontalDivider()
            }
        }
        item {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Checkbox(vm.directConfirmed,{vm.directConfirmed=it},enabled=vm.writable)
                Text(if(vm.category=="anime" && preview.link.source=="TMDB") "Confirmo que esta ficha corresponde al anime correcto y al formato indicado: $kind." else "Confirmo que ésta es la ficha y el tipo de contenido correctos.")
            }
            Button(onClick={vm.applyDirectMetadata()},enabled=vm.writable && vm.directConfirmed,modifier=Modifier.fillMaxWidth()) {Text("Aplicar selección y continuar revisión")}
            OutlinedButton(onClick={vm.cancelDirectPreview()},enabled=!vm.busy,modifier=Modifier.fillMaxWidth()) {Text("Volver sin aplicar")}
            Text("La identificación de la fuente queda vinculada al aplicar. Revertir los datos conserva esa identificación aprobada. El catálogo solo cambia al confirmar y guardar; Sincronizar sigue siendo manual.",style=MaterialTheme.typography.bodySmall)
        }
    }
}
