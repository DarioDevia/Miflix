package ar.com.miflix.admin

import com.google.gson.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class SearchResult(val source: String,val id: Long,val title: String,val year: String,val poster: String,val raw: JsonObject)
interface Metadata {
    fun search(query: String,type: String,source: String): List<SearchResult>
    fun searchPage(query:MetadataQuery,page:Int):MetadataPage = error("Esta fuente no admite búsqueda paginada.")
    fun cancelSearch() {}
    fun detail(result: SearchResult,type: String): JsonObject
    fun season(id: Long,number: Int): JsonObject
}
class HttpMetadata(private val config: AdminConfig,private val client: OkHttpClient=OkHttpClient.Builder()
    .callTimeout(25,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build(),
    private val tmdbBase: String="https://api.themoviedb.org/3",private val anilistUrl: String="https://graphql.anilist.co",
    private val jikanBase: String="https://api.jikan.moe/v4"): Metadata {
    private fun json(request: Request): JsonObject {
        try { return client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { when(response.code) {401,403 -> "La fuente rechazó la configuración de metadatos.";404 -> "Metadatos: HTTP 404. La función no está disponible; revisá configuración.";429 -> "HTTP 429: la fuente pidió esperar antes de consultar otra vez.";else -> "Metadatos: HTTP ${response.code}."} }
            val raw=response.body?.string() ?: error("Metadatos vacíos.")
            check(raw.length <= CatalogRules.MAX_BYTES) { "Metadatos demasiado grandes." }
            JsonParser.parseString(raw).asJsonObject
        } } catch(e:IllegalStateException) {throw e} catch(_:Exception) {error("No se pudieron consultar los metadatos. Reintentá.")}
    }
    private fun tmdb(path: String,params: Map<String,String> = emptyMap()): JsonObject {
        val direct=config.tmdbToken.isNotBlank()
        val url=(if(direct) tmdbBase+path else config.baseUrl+"/admin/metadata/tmdb"+path).toHttpUrl().newBuilder()
        params.forEach { (key,value) -> url.addQueryParameter(key,value) }
        return json(Request.Builder().url(url.build()).header("Authorization","Bearer ${if(direct) config.tmdbToken else config.token}").build())
    }
    // Same fixed upstream/relay and encrypted credentials as the established metadata flow.
    fun recoveryRequest(path: String, language: String): JsonObject {
        require(language.matches(Regex("[a-z]{2}(?:-[A-Z]{2})?"))) {"Idioma inválido."}
        require(path.matches(Regex("/(movie|tv)/[1-9]\\d*(?:/season/(?:0|[1-9]\\d*))?"))) {"Consulta no permitida."}
        return tmdb(path,mapOf("language" to language))
    }
    // Collections reuse the same encrypted TMDB token/relay; no second credential configuration.
    fun collectionRequest(path:String,language:String="es-AR",query:String?=null,page:Int=1):JsonObject {
        require(language in setOf("es-AR","en-US")) {"Idioma de colección no permitido."}
        require(path=="/search/collection" || path.matches(Regex("/collection/[1-9]\\d*(?:/images)?"))) {"Consulta de colección no permitida."}
        require(page in 1..500 && (query==null || query.trim().length in 1..200)) {"Búsqueda o página inválidas."}
        val params=mutableMapOf("language" to language)
        if(path=="/search/collection") {require(query!=null) {"Escribí una colección."};params["query"]=query.trim();params["page"]=page.toString();params["include_adult"]="false"}
        // All image languages are intentionally requested: /images without a language filter.
        if(path.endsWith("/images")) params.clear()
        return try {tmdb(path,params)} catch(e:IllegalStateException) {
            if(config.tmdbToken.isBlank() && (e.message.orEmpty().contains("400") || e.message.orEmpty().contains("404")))
                error("El relay actual no admite colecciones. Usá el TMDB Bearer Token local opcional de Configuración; no hace falta otra clave ni modificar el Worker.")
            throw e
        }
    }
    // Isolated bulk transport: preserve status and Retry-After without exposing bodies/credentials.
    fun sagaRequest(path:String):JsonObject {
        require(path.matches(Regex("/(?:movie|collection)/[1-9][0-9]*"))) {"Consulta de saga no permitida."}
        val direct=config.tmdbToken.isNotBlank()
        val url=(if(direct) tmdbBase+path else config.baseUrl+"/admin/metadata/tmdb"+path).toHttpUrl().newBuilder().addQueryParameter("language","es-AR").build()
        try {
            return client.newCall(Request.Builder().url(url).header("Authorization","Bearer ${if(direct) config.tmdbToken else config.token}").build()).execute().use {response ->
                if(!response.isSuccessful) {
                    if(!direct && path.startsWith("/collection/") && response.code in setOf(400,404)) error("El relay no admite colecciones. Configurá el TMDB Bearer Token local existente.")
                    throw SagaRequestFailure(response.code,SagaDetection.retryAfter(response.header("Retry-After")))
                }
                val source=response.body?.source() ?: error("Respuesta TMDB vacía.")
                val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(out.size()<=CatalogRules.MAX_BYTES) {
                    val n=source.read(buffer,0,minOf(buffer.size,CatalogRules.MAX_BYTES+1-out.size()));if(n<0) break;out.write(buffer,0,n)
                }
                check(out.size()<=CatalogRules.MAX_BYTES) {"Respuesta TMDB demasiado grande."}
                try {JsonParser.parseString(out.toString("UTF-8")).asJsonObject} catch(_:Exception) {error("Respuesta TMDB incompleta.")}
            }
        } catch(e:IllegalStateException) {throw e} catch(_:java.io.IOException) {throw SagaRequestFailure()} catch(_:Exception) {error("No se pudo consultar TMDB.")}
    }
    fun cancelRecovery() { client.dispatcher.cancelAll() }
    override fun cancelSearch() {client.dispatcher.cancelAll()}
    override fun search(query:String,type:String,source:String)=searchPage(MetadataQuery.create(query,type,source,""),1).items
    override fun searchPage(query:MetadataQuery,page:Int):MetadataPage {
        MetadataQuery.create(query.title,query.type,query.source,query.year?.toString().orEmpty())
        require(page>=1) {"Página inválida."}
        if(query.source=="AniList") {
            require(page<=500) {"AniList limita la profundidad a 5.000 resultados. Refiná el título o el año; no se indicó un fin de resultados falso."}
            val variables=JsonObject().apply {
                addProperty("search",query.title);addProperty("page",page)
                if(query.year!=null) addProperty("year","${query.year}%")
            }
            val body=JsonObject().apply {addProperty("query",ANILIST_QUERY);add("variables",variables)}
            val response=json(Request.Builder().url(anilistUrl).post(body.toString().toRequestBody("application/json".toMediaType())).build())
            check(!response.has("errors")) {"AniList informó un error. Reintentá; no se avanzó de página."}
            return parseSearchPage {
                val data=response.getAsJsonObject("data").getAsJsonObject("Page")
                val info=data.getAsJsonObject("pageInfo")
                val current=info.get("currentPage")?.takeUnless {it.isJsonNull}?.let {positivePage(it)} ?: page
                check(current==page)
                val more=info.get("hasNextPage")
                check(more!=null && more.isJsonPrimitive && more.asJsonPrimitive.isBoolean)
                val media=data.getAsJsonArray("media") ?: error("Falta media")
                MetadataPage(media.map {entry ->
                    val obj=entry.asJsonObject;val titles=obj.getAsJsonObject("title")
                    SearchResult("AniList",positiveId(obj),titles.text("english").ifBlank {titles.text("romaji").ifBlank {titles.text("native")}},
                        obj.get("startDate")?.takeIf {it.isJsonObject}?.asJsonObject?.text("year").orEmpty().ifBlank {obj.text("seasonYear")},
                        obj.getAsJsonObject("coverImage").text("extraLarge"),obj)
                },current,more.asBoolean)
            }
        }
        val tv=query.type!="pelicula"
        val params=mutableMapOf("query" to query.title,"language" to "es-MX","include_adult" to "false")
        // Legacy relay permits the implicit first page. Subsequent pages use the real page parameter.
        if(page>1 || config.tmdbToken.isNotBlank()) params["page"]=page.toString()
        query.year?.let {params[if(tv) "first_air_date_year" else "primary_release_year"]=it.toString()}
        val response=try {tmdb(if(tv) "/search/tv" else "/search/movie",params)} catch(e:IllegalStateException) {
            if(config.tmdbToken.isBlank() && (page>1 || query.year!=null) && (e.message.orEmpty().contains("400") || e.message.orEmpty().contains("404")))
                error("El relay actual no admite página o año. Configurá el TMDB Bearer Token local opcional en Configuración y volvé a buscar. Los resultados anteriores se conservaron.")
            throw e
        }
        return parseSearchPage {
            val current=positivePage(response.get("page"))
            check(current==page)
            val total=positivePage(response.get("total_pages"),allowZero=true)
            val entries=response.getAsJsonArray("results") ?: error("Faltan resultados")
            check((current<=total) || (current==1 && total==0 && entries.size()==0))
            MetadataPage(entries.map {entry ->
                val obj=entry.asJsonObject
                SearchResult("TMDB",positiveId(obj),obj.text(if(tv) "name" else "title"),obj.text(if(tv) "first_air_date" else "release_date").take(4),image(obj.text("poster_path")),obj)
            },current,current<total)
        }
    }
    private fun positiveId(obj:JsonObject):Long {
        val raw=obj.get("id") ?: error("Falta ID")
        check(raw.isJsonPrimitive && raw.asJsonPrimitive.isNumber && raw.toString().matches(Regex("[1-9][0-9]*")))
        return (raw.toString().toLongOrNull() ?: error("ID inválido")).also {check(it>0)}
    }
    private fun positivePage(value:JsonElement?,allowZero:Boolean=false):Int {
        check(value!=null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber && value.toString().matches(Regex("[0-9]+")))
        return (value.toString().toIntOrNull() ?: error("Página inválida")).also {check(it >= (if(allowZero) 0 else 1))}
    }
    private fun parseSearchPage(block:()->MetadataPage):MetadataPage = try {block()} catch(_:Exception) {
        error("La fuente devolvió una página inválida o incompleta. Reintentá; se conservaron los resultados.")
    }
    override fun detail(result: SearchResult,type: String): JsonObject {
        if(result.source == "AniList") return anime(result)
        val tv=type != "pelicula";val path="/${if(tv) "tv" else "movie"}/${result.id}"
        val obj=tmdb(path,mapOf("language" to "es-MX"))
        val external=runCatching {tmdb("$path/external_ids")}.getOrNull()
        val imdb=external?.text("imdb_id").orEmpty()
        val item=JsonObject().apply {
            val id=if(type=="anime") "anime-tmdb-${result.id}" else "$type-${if(imdb.isNotBlank()) "imdb-$imdb" else "tmdb-${result.id}"}"
            addProperty("id",id);addProperty("tipo",type);addProperty("tmdb_id",result.id)
            if(imdb.isNotBlank()) addProperty("imdb_id",imdb)
            addProperty("titulo",obj.text(if(tv) "name" else "title"));addProperty("titulo_original",obj.text(if(tv) "original_name" else "original_title"))
            addProperty("anio",obj.text(if(tv) "first_air_date" else "release_date").take(4));addProperty("sinopsis",obj.text("overview"))
            add("generos",JsonArray().apply {obj.array("genres").forEach {add(it.asJsonObject.text("name"))}})
            addProperty("poster_url",image(obj.text("poster_path")));addProperty("fondo_url",image(obj.text("backdrop_path")))
            obj.text("backdrop_path").takeIf {it.isNotBlank()}?.let {addProperty("backdrop_url",image(it))}
            val score=obj.text("vote_average").toDoubleOrNull()
            if(score!=null && score>0) {addProperty("puntuacion",String.format(java.util.Locale.US,"%.1f/10",score));addProperty("fuente_puntuacion","TMDB")}
            addProperty("fuente","MANUAL");addProperty("fuente_principal","TMDB")
            add("fuentes_metadata",provenance("TMDB"));add("temporadas",JsonArray())
        }
        runCatching {tmdb("$path/credits",mapOf("language" to "es-MX"))}.getOrNull()?.let { credits ->
            item.addProperty("protagonistas",credits.array("cast").take(5).joinToString(", ") {it.asJsonObject.text("name")})
            item.addProperty("direccion",credits.array("crew").filter { it.asJsonObject.text("job") in setOf("Director","Series Director") }.take(4).map {it.asJsonObject.text("name")}.distinct().joinToString(", "))
        }
        val videos=runCatching {
            val first=tmdb("$path/videos",mapOf("language" to "es-MX")).array("results")
            if(first.size()>0) first else tmdb("$path/videos",mapOf("language" to "en-US")).array("results")
        }.getOrNull()
        videos?.map {it.asJsonObject}?.filter {it.text("site")=="YouTube" && it.text("type")=="Trailer"}
            ?.sortedByDescending {it.text("official")=="true"}?.firstOrNull()?.text("key")?.takeIf {it.isNotBlank()}?.let {item.addProperty("trailer_url","https://www.youtube.com/watch?v=$it")}
        if(tv) {
            item.add("temporadas",JsonArray().apply {obj.array("seasons").forEach {add(seasonSummary(it.asJsonObject))}})
            item.addProperty("fuente_episodios","TMDB")
        }
        // Images are optional: an unavailable relay/provider must not block review or erase existing artwork.
        runCatching {logo(tmdb("$path/images"))}.getOrNull()?.let {item.addProperty("logo_url",it)}
        return item
    }
    override fun season(id: Long,number: Int): JsonObject {
        require(id>0 && number>=0) { "Temporada inválida." }
        val obj=tmdb("/tv/$id/season/$number",mapOf("language" to "es-MX"))
        return seasonSummary(obj).apply {
            addProperty("numero",number);add("episodios",JsonArray().apply {
                obj.array("episodes").forEach { entry ->
                    val e=entry.asJsonObject
                    add(JsonObject().apply {
                        addProperty("tmdb_id",e.number("id"));addProperty("numero",e.number("episode_number"));addProperty("temporada",number)
                        addProperty("titulo",e.text("name"));addProperty("sinopsis",e.text("overview"));addProperty("duracion",e.number("runtime"))
                        addProperty("fecha_emision",e.text("air_date"));addProperty("imagen_url",image(e.text("still_path")))
                    })
                }
            })
        }
    }
    private fun anime(result: SearchResult): JsonObject {
        val obj=result.raw;val titles=obj.getAsJsonObject("title")
        return JsonObject().apply {
            addProperty("id","anime-${result.id}");addProperty("tipo","anime");addProperty("anilist_id",result.id)
            obj.number("idMal")?.let {addProperty("mal_id",it)}
            obj.number("episodes")?.let {addProperty("cantidad_episodios",it)}
            addProperty("titulo",result.title);addProperty("titulo_original",titles.text("native").ifBlank {titles.text("romaji")})
            addProperty("anio",result.year);addProperty("poster_url",result.poster)
            addProperty("sinopsis",obj.text("description").replace(Regex("<[^>]+>")," ").replace(Regex("\\s+")," ").trim())
            add("generos",obj.array("genres").deepCopy());addProperty("fuente","MANUAL");addProperty("fuente_principal","AniList");add("fuentes_metadata",provenance("AniList"));add("temporadas",JsonArray())
            if(obj.number("averageScore")!=null) {addProperty("puntuacion","${obj.number("averageScore")}/100");addProperty("fuente_puntuacion","AniList")}
            obj.number("idMal")?.let { mal ->
                runCatching {json(Request.Builder().url("$jikanBase/anime/$mal").build()).getAsJsonObject("data").text("score")}.getOrNull()?.takeIf {it.toDoubleOrNull()!=null}?.let {addProperty("puntuacion",it);addProperty("fuente_puntuacion","MyAnimeList")}
            }
            addProperty("protagonistas",obj.getAsJsonObject("characters")?.array("edges")?.filter {it.asJsonObject.text("role")=="MAIN"}?.joinToString(", ") {it.asJsonObject.getAsJsonObject("node").getAsJsonObject("name").text("full")}.orEmpty())
            addProperty("direccion",obj.getAsJsonObject("staff")?.array("edges")?.filter {it.asJsonObject.text("role").contains("Director")}?.map {it.asJsonObject.getAsJsonObject("node").getAsJsonObject("name").text("full")}?.distinct()?.joinToString(", ").orEmpty())
            obj.get("trailer")?.takeIf {it.isJsonObject}?.asJsonObject?.let {tr ->
                if(tr.text("site").equals("youtube",true)) addProperty("trailer_url","https://www.youtube.com/watch?v=${tr.text("id")}")
                if(tr.text("site").equals("dailymotion",true)) addProperty("trailer_url","https://www.dailymotion.com/video/${tr.text("id")}")
            }
        }
    }
    companion object {
        fun image(path: String): String = if(path.isBlank()) "" else "https://image.tmdb.org/t/p/original$path"
        fun logo(images: JsonObject): String? = images.array("logos").filter {it.isJsonObject}.map {it.asJsonObject}
            .filter {it.text("file_path").startsWith("/") && !it.text("file_path").startsWith("//")}
            .sortedWith(compareBy<JsonObject> {
                when(it.text("iso_639_1")) {"es" -> 0;"" -> 1;"en" -> 2;else -> 3}
            }.thenByDescending {it.text("vote_average").toDoubleOrNull() ?: 0.0}
                .thenByDescending {it.number("vote_count") ?: 0}
                .thenByDescending {it.number("width") ?: 0}
                .thenBy {it.text("file_path")})
            .firstOrNull()?.text("file_path")?.let(::image)
        fun seasonSummary(obj: JsonObject): JsonObject = JsonObject().apply {
            addProperty("numero",obj.number("season_number"));addProperty("nombre",obj.text("name"));addProperty("sinopsis",obj.text("overview"))
            addProperty("fecha_emision",obj.text("air_date"));addProperty("poster_url",image(obj.text("poster_path")));addProperty("cantidad_episodios",obj.number("episode_count"))
        }
        fun provenance(source: String)=JsonObject().apply {listOf("datos_generales","sinopsis","poster","fondo","trailer","creditos","puntuacion").forEach {addProperty(it,source)}}
        private const val ANILIST_QUERY="""query (${'$'}search: String!, ${'$'}page: Int!, ${'$'}year: String) { Page(page: ${'$'}page, perPage: 10) { pageInfo { currentPage hasNextPage } media(search: ${'$'}search, startDate_like: ${'$'}year, type: ANIME) { id idMal title { romaji english native } seasonYear startDate { year } genres description averageScore episodes coverImage { extraLarge large } trailer { id site } characters(perPage: 6, sort: [ROLE, RELEVANCE]) { edges { role node { name { full } } } } staff(perPage: 15) { edges { role node { name { full } } } } } } }"""
    }
}

object Enrichment {
    fun withEpisodes(metadata: Metadata,candidate: JsonObject): JsonObject {
        val copy=candidate.deepCopy()
        val id=copy.number("tmdb_id") ?: error("Falta el ID TMDB para episodios.")
        val summaries=copy.array("temporadas").map {it.asJsonObject}
        val complete=JsonArray()
        summaries.forEach {summary ->
            val n=summary.number("numero") ?: error("Temporada inválida.")
            require(n in 0..Int.MAX_VALUE.toLong()) {"Temporada inválida."}
            complete.add(summary.deepCopy().apply {metadata.season(id,n.toInt()).entrySet().forEach {(key,value) ->add(key,value.deepCopy())}})
        }
        copy.add("temporadas",complete)
        return copy
    }
    val groups=linkedMapOf("datos_generales" to listOf("titulo","titulo_original","anio","generos"),"sinopsis" to listOf("sinopsis"),"poster" to listOf("poster_url"),"fondo" to listOf("fondo_url","backdrop_url","logo_url"),"trailer" to listOf("trailer_url"),"creditos" to listOf("protagonistas","direccion"),"puntuacion" to listOf("puntuacion","fuente_puntuacion"))
    fun apply(base: JsonObject,candidate: JsonObject,selected: Set<String>): JsonObject {
        val copy=base.deepCopy();val sources=copy.get("fuentes_metadata")?.takeIf {it.isJsonObject}?.asJsonObject ?: JsonObject()
        selected.forEach {group ->
            groups[group]?.forEach {key ->candidate.get(key)?.takeIf {!it.isJsonNull && it.toString() !in setOf("\"\"","[]") }?.let {copy.add(key,it.deepCopy())}}
            if(group in groups) sources.addProperty(group,"TMDB")
        }
        copy.add("fuentes_metadata",sources)
        candidate.get("tmdb_id")?.let {copy.add("tmdb_id",it.deepCopy())};candidate.get("imdb_id")?.let {copy.add("imdb_id",it.deepCopy())}
        if("episodios" in selected) {
            candidate.array("temporadas").forEach {season ->
                val merged=CatalogRules.mergeSeason(copy,season.asJsonObject);copy.add("temporadas",merged.array("temporadas"))
            };copy.addProperty("fuente_episodios","TMDB")
        }
        return copy
    }
}
