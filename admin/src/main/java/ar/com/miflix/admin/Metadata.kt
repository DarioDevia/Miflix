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
    fun detail(result: SearchResult,type: String): JsonObject
    fun season(id: Long,number: Int): JsonObject
}
class HttpMetadata(private val config: AdminConfig,private val client: OkHttpClient=OkHttpClient.Builder()
    .callTimeout(25,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build(),
    private val tmdbBase: String="https://api.themoviedb.org/3",private val anilistUrl: String="https://graphql.anilist.co",
    private val jikanBase: String="https://api.jikan.moe/v4"): Metadata {
    private fun json(request: Request): JsonObject {
        try { return client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { when(response.code) {401,403 -> "La fuente rechazó la configuración de metadatos.";404 -> "La función de metadatos no está disponible; revisá configuración.";429 -> "La fuente pidió esperar antes de consultar otra vez.";else -> "Metadatos: HTTP ${response.code}."} }
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
    fun cancelRecovery() { client.dispatcher.cancelAll() }
    override fun search(query: String,type: String,source: String): List<SearchResult> {
        require(query.trim().isNotBlank()) { "Escribí un título." }
        require(query.length <= 200) { "La búsqueda es demasiado larga." }
        if(source == "AniList") {
            require(type == "anime") { "AniList es para anime." }
            val body=JsonObject().apply {
                addProperty("query",ANILIST_QUERY);add("variables",JsonObject().apply {addProperty("search",query.trim())})
            }
            val data=json(Request.Builder().url(anilistUrl).post(body.toString().toRequestBody("application/json".toMediaType())).build())
            check(!data.has("errors")) { "AniList informó un error." }
            return data.getAsJsonObject("data").getAsJsonObject("Page").array("media").map { entry ->
                val obj=entry.asJsonObject;val titles=obj.getAsJsonObject("title")
                SearchResult("AniList",obj.number("id")!!, titles.text("english").ifBlank { titles.text("romaji").ifBlank {titles.text("native")} },obj.text("seasonYear"),obj.getAsJsonObject("coverImage").text("extraLarge"),obj)
            }
        }
        val tv=type != "pelicula"
        return tmdb(if(tv) "/search/tv" else "/search/movie",mapOf("query" to query.trim(),"language" to "es-MX","include_adult" to "false")).array("results").take(10).map { entry ->
            val obj=entry.asJsonObject
            SearchResult("TMDB",obj.number("id")!!,obj.text(if(tv) "name" else "title"),obj.text(if(tv) "first_air_date" else "release_date").take(4),image(obj.text("poster_path")),obj)
        }
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
        private const val ANILIST_QUERY="""query (${'$'}search: String!) { Page(page: 1, perPage: 5) { media(search: ${'$'}search, type: ANIME) { id idMal title { romaji english native } seasonYear genres description averageScore episodes coverImage { extraLarge large } trailer { id site } characters(perPage: 6, sort: [ROLE, RELEVANCE]) { edges { role node { name { full } } } } staff(perPage: 15) { edges { role node { name { full } } } } } } }"""
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
