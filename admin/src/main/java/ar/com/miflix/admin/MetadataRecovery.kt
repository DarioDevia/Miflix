package ar.com.miflix.admin

import android.text.Html
import com.google.gson.*
import java.time.LocalDate

data class RecoveryCandidate(val provider: String, val id: Long, val title: String, val year: String,
    val url: String, val language: String, val raw: JsonObject)
data class RecoveryData(val candidate: RecoveryCandidate, val fields: JsonObject,
    val seasons: List<JsonObject> = emptyList(), val warnings: List<String> = emptyList())
data class RecoveryPath(val season: Long? = null, val episode: Long? = null, val field: String) {
    val key get() = "${season ?: "title"}/${episode ?: "season"}/$field"
    val label get() = (season?.let { "${if(it==0L) "Especiales" else "T$it"}${episode?.let { e -> " E$e" }.orEmpty()} · " }.orEmpty()) +
        mapOf("titulo" to "Título", "sinopsis" to "Sinopsis", "nombre" to "Nombre de temporada", "poster_url" to "Portada",
            "backdrop_url" to "Fondo", "imagen_url" to "Imagen", "duracion" to "Duración (min)", "fecha_emision" to "Fecha de emisión").getValue(field)
}
data class RecoveryChange(val path: RecoveryPath, val before: JsonElement?, val after: JsonElement,
    val sourceUrl: String, val mapping: String? = null, val safe: Boolean = true) {
    val initiallySelected get() = safe && MetadataRecovery.missing(path.field, before)
}
data class RecoveryPlan(val before: JsonObject, val data: RecoveryData, val changes: List<RecoveryChange>, val warnings: List<String>) {
    fun selected(keys: Set<String>) = changes.filter { it.path.key in keys }
    fun episodeCount(keys: Set<String>) = selected(keys).filter { it.path.episode != null }.map { it.path.season to it.path.episode }.distinct().size
}

/** The only writable paths in a recovery; never copy provider identity or playback fields. */
object MetadataRecovery {
    const val LICENSE = "https://creativecommons.org/licenses/by-sa/4.0/"
    val rootFields = setOf("titulo", "sinopsis", "poster_url", "backdrop_url", "duracion")
    val seasonFields = setOf("nombre", "sinopsis", "poster_url")
    val episodeFields = setOf("titulo", "sinopsis", "imagen_url", "duracion", "fecha_emision")
    fun clean(value: String): String = Html.fromHtml(value.replace(Regex("(?is)<(script|style)\\b[^>]*>.*?</\\1>"),""), Html.FROM_HTML_MODE_LEGACY).toString()
        .replace('\u00a0', ' ').replace(Regex("[\\t ]+"), " ").replace(Regex("\\n{3,}"), "\n\n").trim()
    fun generic(value: String): Boolean = value.trim().matches(Regex("(?i)(?:(?:episodio|episode|cap[ií]tulo|chapter|temporada|season)\\s*#?\\s*\\d+[a-z]?|tba|tbd|untitled|sin t[ií]tulo)"))
    fun missing(field: String, value: JsonElement?): Boolean = value == null || value.isJsonNull ||
        (value.isJsonPrimitive && (value.asString.isBlank() || (field in setOf("titulo", "nombre") && generic(value.asString))))
    fun valid(field: String, value: JsonElement?): JsonElement? {
        if (value == null || !value.isJsonPrimitive || value.isJsonNull) return null
        val text = value.asString.trim()
        return when {
            field == "duracion" -> text.toLongOrNull()?.takeIf { it in 1..1440 }?.let { JsonPrimitive(it.toString()) }
            field == "fecha_emision" -> runCatching { LocalDate.parse(text) }.getOrNull()?.let { JsonPrimitive(text) }
            field.endsWith("_url") -> text.takeIf { runCatching { val u=java.net.URI(it);u.scheme=="https" && !u.host.isNullOrBlank() && u.userInfo==null }.getOrDefault(false) }?.let(::JsonPrimitive)
            else -> clean(text).takeIf { it.isNotBlank() && (field !in setOf("titulo", "nombre") || !generic(it)) }?.let(::JsonPrimitive)
        }
    }
    private fun seasons(item: JsonObject, number: Long) = item.array("temporadas").filter { it.isJsonObject && it.asJsonObject.number("numero")==number }.map { it.asJsonObject }
    fun target(item: JsonObject, path: RecoveryPath): JsonObject {
        val allowed = if(path.episode!=null) episodeFields else if(path.season!=null) seasonFields else rootFields
        require(path.field in allowed && (path.episode==null || path.season!=null)) { "Campo de recuperación no permitido." }
        val season = path.season?.let { seasons(item,it).singleOrNull() ?: error("La temporada cambió o es ambigua.") } ?: return item
        return path.episode?.let { number -> season.array("episodios").filter { it.isJsonObject && it.asJsonObject.number("numero")==number }
            .map { it.asJsonObject }.singleOrNull() ?: error("El episodio cambió o es ambiguo.") } ?: season
    }
    fun compare(current: JsonObject, data: RecoveryData): RecoveryPlan {
        val changes=mutableListOf<RecoveryChange>();val warnings=data.warnings.toMutableList()
        fun add(old: JsonObject, incoming: JsonObject, fields: Set<String>, s: Long?=null,e: Long?=null,safe: Boolean=true,url: String=data.candidate.url) {
            fields.forEach { field -> valid(field,incoming.get(field))?.let { value ->
                if(old.get(field)!=value) changes.add(RecoveryChange(RecoveryPath(s,e,field),old.get(field)?.deepCopy(),value,url,
                    e?.let { "T$s E$e → T$s E$e" },safe))
            } }
        }
        add(current,data.fields,rootFields)
        data.seasons.groupBy { it.number("numero") }.forEach { (number, list) ->
            if(number==null || list.size!=1 || seasons(current,number).size!=1) { warnings.add("Temporada ${number ?: "sin número"}: no existe una correspondencia única; excluida.");return@forEach }
            val old=seasons(current,number).single();val fresh=list.single()
            add(old,fresh,seasonFields,number)
            val oldEpisodes=old.array("episodios").filter {it.isJsonObject}.map {it.asJsonObject}
            val newEpisodes=fresh.array("episodios").filter {it.isJsonObject}.map {it.asJsonObject}
            newEpisodes.groupBy {it.number("numero")}.forEach episodes@{ (ep, incoming) ->
                val existing=oldEpisodes.filter {it.number("numero")==ep}
                if(ep==null || ep<1 || incoming.size!=1 || existing.size!=1) { warnings.add("T$number E${ep ?: "?"}: numeración ausente, distinta o duplicada; excluido.");return@episodes }
                val a=existing.single();val b=incoming.single()
                if(b.text("_special")=="true" && number!=0L) {warnings.add("T$number E$ep: especial de la fuente; excluido.");return@episodes}
                val oldId=a.number("tmdb_id");val newId=b.number("tmdb_id")
                if(data.candidate.provider=="TMDB" && oldId!=null && (oldEpisodes.count {it.number("tmdb_id")==oldId}!=1 || newEpisodes.count {it.number("tmdb_id")==oldId}!=1)) {warnings.add("T$number E$ep: ID repetido o movido; excluido.");return@episodes}
                if(data.candidate.provider=="TMDB" && oldId!=null && oldId!=newId) {warnings.add("T$number E$ep: ID TMDB distinto; excluido.");return@episodes}
                val safe=data.candidate.provider=="TMDB" && oldId!=null && oldId==newId && number!=0L
                add(a,b,episodeFields,number,ep,safe,b.text("_source_url").ifBlank {data.candidate.url})
            }
        }
        return RecoveryPlan(current.deepCopy(),data,changes,warnings)
    }
    fun apply(plan: RecoveryPlan, selected: Set<String>, confirmed: Set<String>, licenseAccepted: Boolean): JsonObject {
        val choices=plan.selected(selected)
        require(choices.isNotEmpty()) {"Seleccioná al menos un campo."}
        require(choices.all {it.safe || "${it.path.season}/${it.path.episode}" in confirmed}) {"Confirmá la correspondencia de cada episodio seleccionado."}
        require(plan.data.candidate.provider!="TVmaze" || licenseAccepted) {"Aceptá conservar la atribución y ShareAlike de TVmaze."}
        val result=plan.before.deepCopy()
        require(!result.has("fuentes_metadata") || result.get("fuentes_metadata").isJsonObject) {"La procedencia existente necesita revisión; no se sobrescribió."}
        choices.forEach { change ->
            target(result,change.path).add(change.path.field,change.after.deepCopy())
            val sources=result.get("fuentes_metadata")?.takeIf {it.isJsonObject}?.asJsonObject ?: JsonObject().also {result.add("fuentes_metadata",it)}
            require(!sources.has("recuperacion_selectiva") || sources.get("recuperacion_selectiva").isJsonObject) {"El registro de procedencia existente es incompatible; conservado sin cambios."}
            val recovery=sources.get("recuperacion_selectiva")?.asJsonObject ?: JsonObject().also {sources.add("recuperacion_selectiva",it)}
            recovery.add(change.path.key,JsonObject().apply {
                addProperty("fuente",plan.data.candidate.provider);addProperty("url",change.sourceUrl)
                addProperty("idioma",plan.data.candidate.language);addProperty("modificaciones","Selección y normalización de HTML/espacios; sin traducción")
                if(plan.data.candidate.provider=="TVmaze") {addProperty("licencia",LICENSE);addProperty("atribucion","TVmaze — datos importados y sus adaptaciones bajo CC BY-SA 4.0; sin garantías; conservar avisos al redistribuir.")}
            })
        }
        return result
    }
    fun backup(plan: RecoveryPlan, selected: Set<String>, after: JsonObject) = JsonObject().apply {
        add("before",plan.before.deepCopy());add("after",after.deepCopy())
        add("paths",JsonArray().apply {plan.selected(selected).forEach {c ->add(JsonObject().apply {
            c.path.season?.let {addProperty("season",it)};c.path.episode?.let {addProperty("episode",it)};addProperty("field",c.path.field)
        })}})
    }
    fun undo(current: JsonObject, backup: JsonObject): JsonObject {
        val before=backup.getAsJsonObject("before");val after=backup.getAsJsonObject("after")
        require(current.text("id")==before.text("id")) {"El respaldo corresponde a otra ficha."}
        require(current.text("id").isNotBlank() || (current.text("_admin_metadata_recovery").isNotBlank() && current.text("_admin_metadata_recovery")==before.text("_admin_metadata_recovery"))) {"El respaldo corresponde a otro borrador."}
        val copy=current.deepCopy()
        backup.array("paths").forEach {entry ->
            val p=entry.asJsonObject;val path=RecoveryPath(p.number("season"),p.number("episode"),p.text("field"))
            val destination=target(copy,path);require(destination.get(path.field)==target(after,path).get(path.field)) {"Hay cambios posteriores en un campo recuperado. No se sobrescribieron."}
            val old=target(before,path).get(path.field)
            if(old==null) destination.remove(path.field) else destination.add(path.field,old.deepCopy())
        }
        require(copy.get("fuentes_metadata")==after.get("fuentes_metadata")) {"La procedencia cambió después de la recuperación."}
        val sources=before.get("fuentes_metadata")
        if(sources==null) copy.remove("fuentes_metadata") else copy.add("fuentes_metadata",sources.deepCopy())
        return copy
    }
}
