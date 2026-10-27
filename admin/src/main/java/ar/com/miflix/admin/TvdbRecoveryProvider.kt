package ar.com.miflix.admin

import com.google.gson.*
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicBoolean

/** Reuses the 0.1.6 field diff and mapping flow. Only the series' default order is retrieved. */
class TvdbRecoveryProvider(private val api: TvdbApi): RecoveryProvider {
    private val stopped=AtomicBoolean()
    private var requests=0
    override fun cancel() {stopped.set(true);api.cancel()}
    private fun get(path: String,missing: Boolean=false): JsonObject? {
        check(!stopped.get()) {"Consulta TheTVDB cancelada."}
        check(++requests<=250) {"TheTVDB: consulta demasiado extensa. Elegí menos temporadas y reintentá."}
        return api.get(path,missing)
    }
    private fun objectData(path: String,missing: Boolean=false): JsonObject? {
        val response=get(path,missing) ?: return null
        return response.get("data")?.takeIf {it.isJsonObject}?.asJsonObject ?: error("TheTVDB: detalle incompleto.")
    }
    private fun objects(element: JsonElement?): List<JsonObject> = element?.takeIf {it.isJsonArray}?.asJsonArray
        ?.mapNotNull {it.takeIf {it.isJsonObject}?.asJsonObject}.orEmpty()
    private fun kind(item: JsonObject)=if(item.text("tipo")=="pelicula") "movie" else "series"
    private fun candidate(record: JsonObject,type: String): RecoveryCandidate? {
        val id=(record.number("tvdb_id") ?: record.number("id"))?.takeIf {it>0} ?: return null
        val title=record.get("translations")?.takeIf {it.isJsonObject}?.asJsonObject?.text("spa")?.takeIf {it.isNotBlank()}
            ?: record.text("name").ifBlank {record.text("title")}
        if(title.isBlank()) return null
        val raw=record.deepCopy().apply {addProperty("_kind",type)}
        val slug=record.text("slug").takeIf {it.matches(Regex("[A-Za-z0-9_-]+"))}
        // /dereferrer routes work without relying on a slug returned by search.
        val url=if(slug!=null) "https://thetvdb.com/${if(type=="movie") "movies" else "series"}/$slug"
            else "https://thetvdb.com/dereferrer/${if(type=="movie") "movie" else "series"}/$id"
        return RecoveryCandidate("TheTVDB",id,title,record.text("year").ifBlank {record.text("firstAired").take(4)},url,"Español (spa)",raw)
    }
    override fun search(item: JsonObject,query: String): List<RecoveryCandidate> {
        val type=kind(item);requests=0
        item.number("tvdb_id")?.takeIf {it>0}?.let {id ->
            val row=objectData("/${if(type=="movie") "movies" else "series"}/$id")!!
            check(row.number("id")==id) {"TheTVDB: identidad distinta; no se seleccionó."}
            return listOfNotNull(candidate(row,type))
        }
        val imdb=item.text("imdb_id").takeIf {it.matches(Regex("tt[0-9]+"))}
        if(imdb!=null) {
            val reply=get("/search/remoteid/$imdb",true)
            val found=objects(reply?.get("data")).mapNotNull {it.get(type)?.takeIf {r ->r.isJsonObject}?.asJsonObject}
                .mapNotNull {candidate(it,type)}.distinctBy {it.id}
            if(found.isNotEmpty()) return found
        }
        require(query.trim().isNotEmpty()) {"Indicá el título para buscar."}
        val q=URLEncoder.encode(query.trim(),"UTF-8")
        val reply=get("/search?query=$q&type=$type&limit=50")!!
        val data=reply.get("data");check(data.isJsonArray) {"TheTVDB: búsqueda incompleta."}
        return objects(data).filter {it.text("type")==type}.mapNotNull {candidate(it,type)}.distinctBy {it.id}
    }
    override fun retrieve(candidate: RecoveryCandidate,item: JsonObject,seasons: Set<Long>,language: String): RecoveryData {
        require(candidate.provider=="TheTVDB" && candidate.id>0 && candidate.raw.text("_kind")==kind(item)) {"TheTVDB: tipo o identidad no compatible."}
        require(language in setOf("spa","base")) {"Elegí Español (spa) o texto base sin idioma verificado."}
        require(seasons.size<=20 && seasons.all {it>=0}) {"Consultá como máximo 20 temporadas existentes."}
        requests=0
        val warnings=mutableListOf("TheTVDB: la publicación y exportación para distribución quedan bloqueadas hasta verificar licencia y atribución en los clientes.",
            "Las imágenes de TheTVDB no se importan: la licencia API no otorga por sí sola sus derechos. Se conservan las imágenes existentes.")
        val type=kind(item);val entity=if(type=="movie") "movies" else "series"
        val row=objectData("/$entity/${candidate.id}/extended")!!
        check(row.number("id")==candidate.id) {"TheTVDB: identidad distinta a la seleccionada."}
        val actual=candidate.copy(language=if(language=="spa") "Español (spa) · traducciones verificadas" else "Texto base · idioma no verificado; selección manual")
        fun texts(base: JsonObject,path: String,label: String,titleField: String="titulo"): JsonObject {
            val translation=if(language=="spa") objectData("$path/translations/spa",true) else base
            val out=JsonObject()
            if(translation==null || (language=="spa" && translation.text("language")!="spa")) {
                warnings.add("$label: no hay traducción española verificable; no se usó texto de otro idioma.");return out
            }
            for((src,dst) in listOf("name" to titleField,"overview" to "sinopsis")) {
                val value=MetadataRecovery.valid(dst,translation.get(src))
                if(value!=null) out.add(dst,value) else warnings.add("$label: falta ${if(dst=="sinopsis") "sinopsis" else "título descriptivo"} ${if(language=="spa") "en español" else "base"}.")
            }
            return out
        }
        val fields=texts(row,"/$entity/${candidate.id}","Ficha")
        if(type=="movie") row.get("runtime")?.let {MetadataRecovery.valid("duracion",it)?.let {value ->fields.add("duracion",value)}}
        if(type=="movie") return RecoveryData(actual,fields,warnings=warnings,manualSelection=language=="base")
        val defaultType=row.number("defaultSeasonType")
        if(defaultType==null || row.get("isOrderRandomized")?.takeIf {it.isJsonPrimitive}?.asBoolean==true) {
            warnings.add("Orden de episodios desconocido o aleatorio: temporadas y episodios excluidos.")
            return RecoveryData(actual,fields,warnings=warnings,manualSelection=language=="base")
        }
        val result=mutableListOf<JsonObject>()
        for(number in seasons.sorted()) {
            val local=item.array("temporadas").filter {it.isJsonObject && it.asJsonObject.number("numero")==number}.map {it.asJsonObject}.singleOrNull()
            if(local==null) {warnings.add("T$number: no hay una temporada local única; excluida.");continue}
            val remote=objects(row.get("seasons")).filter {s ->s.number("number")==number && s.get("type")?.takeIf {it.isJsonObject}?.asJsonObject?.number("id")==defaultType}.singleOrNull()
            val seasonId=remote?.number("id")
            if(seasonId==null) {warnings.add("T$number: sin temporada única en el orden predeterminado de TheTVDB; excluida.");continue}
            val season=objectData("/seasons/$seasonId/extended")!!
            check(season.number("seriesId")==candidate.id && season.number("number")==number && season.getAsJsonObject("type")?.number("id")==defaultType) {"TheTVDB: temporada u orden distintos. Consulta descartada."}
            val out=texts(season,"/seasons/$seasonId","T$number","nombre").apply {addProperty("numero",number)}
            warnings.add("T$number: orden predeterminado TheTVDB (tipo $defaultType). Confirmá cada correspondencia; no se renumeran episodios.")
            val episodes=mutableListOf<JsonObject>();var page=0
            while(true) {
                val reply=get("/series/${candidate.id}/episodes/default?season=$number&page=$page")!!
                val data=reply.get("data")?.takeIf {it.isJsonObject}?.asJsonObject ?: error("TheTVDB: episodios incompletos.")
                check(data.get("series")?.takeIf {it.isJsonObject}?.asJsonObject?.number("id")==candidate.id) {"TheTVDB: episodios de otra serie; descartados."}
                check(data.get("episodes")?.isJsonArray==true) {"TheTVDB: lista de episodios ausente."}
                episodes.addAll(objects(data.get("episodes")))
                check(episodes.size<=2000) {"TheTVDB: temporada demasiado extensa."}
                val links=reply.get("links")?.takeIf {it.isJsonObject}?.asJsonObject
                if(links?.text("next").isNullOrBlank()) break
                check(++page<40) {"TheTVDB: demasiadas páginas; consulta descartada."}
                // Never follow a provider-supplied URL with credentials. Advance only our fixed endpoint.
            }
            val targets=local.array("episodios").filter {it.isJsonObject}.map {it.asJsonObject}.groupBy {it.number("numero")}
            val grouped=episodes.groupBy {it.number("number")}
            val values=JsonArray()
            for((ep,old) in targets) {
                val matches=grouped[ep].orEmpty()
                if(ep==null || ep<1 || old.size!=1 || matches.size!=1) {warnings.add("T$number E${ep ?: "?"}: numeración distinta, ausente o repetida; excluido.");continue}
                val fresh=matches.single();val id=fresh.number("id")
                if(id==null || fresh.number("seriesId")!=candidate.id || fresh.number("seasonNumber")!=number || episodes.count {it.number("id")==id}!=1) {
                    warnings.add("T$number E$ep: identidad o numeración de otro orden; excluido.");continue
                }
                val record=texts(fresh,"/episodes/$id","T$number E$ep").apply {
                    addProperty("numero",ep);addProperty("_source_url","https://thetvdb.com/dereferrer/episode/$id")
                    MetadataRecovery.valid("duracion",fresh.get("runtime"))?.let {add("duracion",it)}
                    MetadataRecovery.valid("fecha_emision",fresh.get("aired"))?.let {add("fecha_emision",it)}
                }
                values.add(record)
            }
            if(episodes.any {it.number("number") !in targets.keys}) warnings.add("T$number: hay episodios de la fuente sin contraparte local; no se crean ni importan.")
            out.add("episodios",values);result.add(out)
        }
        return RecoveryData(actual,fields,result,warnings,manualSelection=language=="base")
    }
}
