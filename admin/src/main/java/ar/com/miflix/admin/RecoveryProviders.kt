package ar.com.miflix.admin

import com.google.gson.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

interface RecoveryProvider {
    fun search(item: JsonObject, query: String): List<RecoveryCandidate>
    fun retrieve(candidate: RecoveryCandidate, item: JsonObject, seasons: Set<Long>, language: String): RecoveryData
    fun cancel()
}
class TmdbRecoveryProvider(private val metadata: HttpMetadata): RecoveryProvider {
    private val stopped=AtomicBoolean(false)
    override fun cancel() {stopped.set(true);metadata.cancelRecovery()}
    private fun active() {check(!stopped.get()) {"Consulta cancelada."}}
    override fun search(item: JsonObject, query: String): List<RecoveryCandidate> {
        active();val type=item.text("tipo");val id=item.number("tmdb_id")
        val matches=if(id!=null && id>0) listOf(SearchResult("TMDB",id,item.text("titulo"),item.text("anio"),"",JsonObject()))
            else metadata.search(query,type,"TMDB")
        active()
        return matches.map {RecoveryCandidate("TMDB",it.id,it.title,it.year,"https://www.themoviedb.org/${if(type=="pelicula") "movie" else "tv"}/${it.id}","Idioma solicitado",it.raw)}
    }
    override fun retrieve(candidate: RecoveryCandidate,item: JsonObject,seasons: Set<Long>,language: String): RecoveryData {
        require(seasons.size<=20 && seasons.all {it in 0..2147483647L}) {"Elegí como máximo 20 temporadas existentes."}
        active();val path="/${if(item.text("tipo")=="pelicula") "movie" else "tv"}/${candidate.id}"
        val raw=metadata.recoveryRequest(path,language);active()
        val root=JsonObject().apply {
            addProperty("titulo",raw.text(if(item.text("tipo")=="pelicula") "title" else "name"))
            addProperty("sinopsis",raw.text("overview"));addProperty("poster_url",HttpMetadata.image(raw.text("poster_path")))
            addProperty("backdrop_url",HttpMetadata.image(raw.text("backdrop_path")))
            if(item.text("tipo")=="pelicula") raw.number("runtime")?.let {addProperty("duracion",it.toString())}
        }
        val warnings=mutableListOf<String>();val found=mutableListOf<JsonObject>()
        if(item.text("tipo")!="pelicula") for(number in seasons.sorted()) {
            active()
            // Failure is visible; never silently present a partial fetch as complete.
            val season=metadata.recoveryRequest("$path/season/$number",language);active()
            require(season.number("season_number")==number) {"TMDB devolvió otra temporada; consulta descartada."}
            found.add(HttpMetadata.seasonSummary(season).apply {
                add("episodios",JsonArray().apply {season.array("episodes").forEach {entry ->
                    val e=entry.asJsonObject
                    if(e.number("season_number")!=number) {warnings.add("TMDB: episodio de otra temporada excluido.");return@forEach}
                    add(JsonObject().apply {
                        addProperty("numero",e.number("episode_number"));addProperty("tmdb_id",e.number("id"))
                        addProperty("titulo",e.text("name"));addProperty("sinopsis",e.text("overview"))
                        addProperty("duracion",e.number("runtime"));addProperty("fecha_emision",e.text("air_date"))
                        addProperty("imagen_url",HttpMetadata.image(e.text("still_path")))
                        addProperty("_source_url","${candidate.url}/season/$number/episode/${e.number("episode_number")}")
                    })
                }})
            })
        }
        return RecoveryData(candidate.copy(language="$language (solicitado; la fuente puede conservar el original)"),root,found,warnings)
    }
}

class ProviderRateLimit(private val clock: ()->Long = {System.nanoTime()/1_000_000}) {
    private var until=0L
    @Synchronized fun acquire() {
        val now=clock();check(now>=until) {"Límite de consultas: esperá ${((until-now+999)/1000).coerceAtLeast(1)} segundos y reintentá."}
        until=now+600 // At most 17 calls/10s, shared across the TVmaze provider instances.
    }
    @Synchronized fun backoff(seconds: Long) {until=maxOf(until,clock()+seconds.coerceIn(2,60)*1000)}
    fun waitForSlot(cancelled: ()->Boolean) {
        // Bounded pacing within an explicit multi-request query, never a retry loop.
        repeat(61) {
            check(!cancelled()) {"Consulta cancelada."}
            synchronized(this) {if(clock()>=until) {acquire();return}}
            Thread.sleep(100)
        }
        error("La fuente pidió esperar. Reintentá más tarde.")
    }
}
class TvmazeRecoveryProvider(private val client: OkHttpClient=OkHttpClient.Builder().callTimeout(25,TimeUnit.SECONDS)
    .followRedirects(false).followSslRedirects(false).build(),private val base: String="https://api.tvmaze.com",
    private val limiter: ProviderRateLimit=sharedLimiter): RecoveryProvider {
    private val stopped=AtomicBoolean(false)
    override fun cancel() {stopped.set(true);client.dispatcher.cancelAll()}
    private var first=true
    private fun request(path: String, allowMissing: Boolean=false): JsonElement? {
        check(!stopped.get()) {"Consulta cancelada."}
        if(first) {limiter.acquire();first=false} else limiter.waitForSlot {stopped.get()}
        try {
            fun execute(path: String)=client.newCall(Request.Builder().url(base+path).header("User-Agent","MiFlix-Admin-Android/0.1.6").header("Accept","application/json").build()).execute()
            var response=execute(path)
            if(response.code in setOf(301,302)) {
                val location=response.header("Location");response.close()
                val url=location?.toHttpUrl();val origin=base.toHttpUrl()
                require(url!=null && url.scheme==origin.scheme && url.host==origin.host && url.port==origin.port && url.encodedPath.matches(Regex("/shows/[1-9]\\d*")) && url.query==null) {"TVmaze devolvió una redirección no permitida."}
                limiter.waitForSlot {stopped.get()};response=execute(url.encodedPath)
            }
            return response.use { r ->
                if(r.code==404 && allowMissing) return@use null
                if(r.code==429) {limiter.backoff(r.header("Retry-After")?.toLongOrNull() ?: 5);error("TVmaze limitó las consultas (HTTP 429). Esperá y reintentá.")}
                check(r.isSuccessful) {"TVmaze: HTTP ${r.code}. No se aplicaron datos."}
                val body=r.body ?: error("TVmaze devolvió una respuesta vacía.")
                val input=body.byteStream();val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(out.size()<=CatalogRules.MAX_BYTES) {val n=input.read(buffer,0,minOf(buffer.size,CatalogRules.MAX_BYTES+1-out.size()));if(n<0) break;out.write(buffer,0,n)}
                check(out.size()<=CatalogRules.MAX_BYTES) {"Respuesta TVmaze demasiado grande."}
                check(!stopped.get()) {"Consulta cancelada."}
                JsonParser.parseString(out.toString("UTF-8"))
            }
        } catch(e:IllegalStateException) {throw e} catch(_:java.io.InterruptedIOException) {error("TVmaze agotó el tiempo de espera. Reintentá.")}
        catch(_:Exception) {error("No se pudo consultar TVmaze. Revisá la conexión o la respuesta de la fuente.")}
    }
    private fun candidate(show: JsonObject)=RecoveryCandidate("TVmaze",show.number("id") ?: error("TVmaze: resultado sin ID."),show.text("name"),show.text("premiered").take(4),
        show.text("url").takeIf {it.startsWith("https://www.tvmaze.com/shows/")} ?: "https://www.tvmaze.com/shows/${show.number("id")}",
        "Sin traducción: sinopsis habitualmente en inglés; títulos originales",show)
    override fun search(item: JsonObject,query: String): List<RecoveryCandidate> {
        require(item.text("tipo") in setOf("serie","anime")) {"TVmaze ofrece series; no películas."}
        val imdb=item.text("imdb_id").takeIf {it.matches(Regex("tt[0-9]+"))}
        if(imdb!=null) request("/lookup/shows?imdb=$imdb",true)?.let {r ->
            val show=r.asJsonObject
            require(show.getAsJsonObject("externals")?.text("imdb")==imdb) {"TVmaze devolvió un ID externo distinto."}
            return listOf(candidate(show))
        }
        require(query.trim().isNotEmpty() && query.length<=200) {"Escribí un título de hasta 200 caracteres."}
        val q=java.net.URLEncoder.encode(query.trim(),"UTF-8")
        return request("/search/shows?q=$q")!!.asJsonArray.take(20).map {candidate(it.asJsonObject.getAsJsonObject("show"))}
    }
    override fun retrieve(candidate: RecoveryCandidate,item: JsonObject,seasons: Set<Long>,language: String): RecoveryData {
        require(item.text("tipo")!="pelicula") {"TVmaze no ofrece películas."}
        val show=request("/shows/${candidate.id}")!!.asJsonObject
        require(show.number("id")==candidate.id) {"TVmaze devolvió otra serie."}
        val episodes=request("/shows/${candidate.id}/episodes?specials=1")!!.asJsonArray.map {it.asJsonObject}
        val root=JsonObject().apply {
            addProperty("titulo",show.text("name"));addProperty("sinopsis",show.text("summary"))
            show.get("image")?.takeIf {it.isJsonObject}?.asJsonObject?.text("original")?.let {addProperty("poster_url",it)}
        }
        val warnings=mutableListOf("TVmaze usa orden estándar de emisión. Confirmá cada correspondencia antes de seleccionar campos; especiales sin número se excluyen.")
        fun optional(path: String,label: String): List<JsonObject> = try {
            request(path,true)?.asJsonArray?.filter {it.isJsonObject}?.map {it.asJsonObject}.orEmpty()
        } catch(e:IllegalStateException) {
            if(e.message?.contains("429")==true || stopped.get()) throw e
            warnings.add("$label no disponibles en esta consulta: ${e.message}");emptyList()
        }
        val seasonInfo=if(seasons.isNotEmpty()) optional("/shows/${candidate.id}/seasons","Fichas de temporadas") else emptyList()
        val images=optional("/shows/${candidate.id}/images","Fondos")
        images.filter {it.text("type")=="background"}.sortedWith(compareByDescending<JsonObject> {it.text("main")=="true"}.thenBy {it.number("id") ?: 0})
            .firstOrNull()?.getAsJsonObject("resolutions")?.getAsJsonObject("original")?.text("url")?.let {root.addProperty("backdrop_url",it)}
        val found=seasons.sorted().map {s ->JsonObject().apply {
            addProperty("numero",s)
            val metadata=seasonInfo.filter {it.number("number")==s}.singleOrNull()
            if(metadata!=null) {
                addProperty("nombre",metadata.text("name"));addProperty("sinopsis",metadata.text("summary"))
                metadata.get("image")?.takeIf {it.isJsonObject}?.asJsonObject?.text("original")?.let {addProperty("poster_url",it)}
            }
            // TVmaze specials have no regular episode number; never renumber or map them to season zero.
            add("episodios",JsonArray().apply {episodes.filter {it.number("season")==s}.forEach {e ->add(JsonObject().apply {
                addProperty("numero",e.number("number"));addProperty("_special",e.text("type")!="regular")
                addProperty("titulo",e.text("name"));addProperty("sinopsis",e.text("summary"))
                addProperty("duracion",e.number("runtime"));addProperty("fecha_emision",e.text("airdate"))
                e.get("image")?.takeIf {it.isJsonObject}?.asJsonObject?.text("original")?.let {addProperty("imagen_url",it)}
                addProperty("_source_url",e.text("url").takeIf {it.startsWith("https://www.tvmaze.com/episodes/")} ?: candidate.url)
            })}})
        }}
        return RecoveryData(candidate,root,found,warnings)
    }
    companion object {private val sharedLimiter=ProviderRateLimit()}
}
