package ar.com.miflix.client

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class Catalog(
    @SerializedName("schema_version") val schemaVersion: String = "",
    @SerializedName("actualizado_en") val updatedAt: String? = null,
    val items: List<Title> = emptyList()
)

data class Title(
    val id: String = "",
    val tipo: String = "",
    val titulo: String = "",
    val year: Int? = null,
    val generos: List<String> = emptyList(),
    val calidad: String? = null,
    val duracion: String? = null,
    val sinopsis: String? = null,
    val puntuacion: Double? = null,
    val director: String? = null,
    val reparto: List<String> = emptyList(),
    @SerializedName("trailer_url") val trailerUrl: String? = null,
    @SerializedName("poster_url") val posterUrl: String? = null,
    @SerializedName("backdrop_url") val backdropUrl: String? = null,
    @SerializedName("telegram_url") val telegramUrl: String? = null,
    val temporadas: List<Season> = emptyList()
)

data class Season(
    val numero: Int = 0,
    val titulo: String? = null,
    val nombre: String? = null,
    @SerializedName("cantidad_episodios") val cantidadEpisodios: Int? = null,
    @SerializedName("fecha_emision") val fechaEmision: String? = null,
    val sinopsis: String? = null,
    @SerializedName("poster_url") val posterUrl: String? = null,
    val episodios: List<Episode> = emptyList()
)
data class Episode(
    val id: String? = null, val numero: Int = 0, val titulo: String? = null,
    @SerializedName("tmdb_id") val tmdbId: Long? = null,
    val temporada: Int? = null,
    val sinopsis: String? = null,
    val duracion: Int? = null,
    @SerializedName("fecha_emision") val fechaEmision: String? = null,
    @SerializedName("imagen_url") val imagenUrl: String? = null,
    @SerializedName("telegram_url") val telegramUrl: String? = null
)
data class CatalogLoad(val catalog: Catalog, val source: String, val error: String? = null)

/** Un JSON válido se guarda de forma atómica; una respuesta HTML nunca reemplaza la copia buena. */
class CatalogRepository(private val context: Context) {
    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    private val cache get() = File(context.filesDir, "catalogo.json")

    suspend fun loadCached(): Catalog? = withContext(Dispatchers.IO) {
        runCatching { parseCatalog(cache.readText()) }.getOrNull()
    }

    suspend fun load(url: String): CatalogLoad = withContext(Dispatchers.IO) {
        if (url.isBlank()) {
            val cached = runCatching { parseCatalog(cache.readText()) }.getOrNull()
            return@withContext if (cached != null) CatalogLoad(cached, "Copia local")
            else CatalogLoad(Catalog("1.2.0"), "Configurá el catálogo")
        }
        try {
            require(url.startsWith("https://", true)) { "Usá una dirección HTTPS del catálogo." }
            val request = Request.Builder().url(url).header("Cache-Control", "no-cache").build()
            val raw = client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "El servidor respondió ${response.code}." }
                response.body?.string() ?: error("La respuesta está vacía.")
            }
            val catalog = parseCatalog(raw)
            val pending = File(context.filesDir, "catalogo.json.tmp")
            pending.writeText(raw)
            check(pending.renameTo(cache)) { "No se pudo guardar la copia del catálogo." }
            CatalogLoad(catalog, "En línea")
        } catch (failure: Exception) {
            val cached = runCatching { parseCatalog(cache.readText()) }.getOrNull()
            if (cached != null) CatalogLoad(cached, "Copia local", failure.message)
            else CatalogLoad(Catalog("1.2.0"), "Sin catálogo", failure.message)
        }
    }

    companion object {
        const val DEFAULT_URL = "https://miflix-catalogo.deviadario.workers.dev/catalogo.json"
        const val AUTO_CHECK_INTERVAL_MS = 10 * 60 * 1000L

        fun shouldAutoCheck(now: Long, lastCheck: Long): Boolean =
            lastCheck <= 0L || now < lastCheck || now - lastCheck >= AUTO_CHECK_INTERVAL_MS

        private fun parseRating(item: JsonObject): Double? {
            val value = item.get("puntuacion")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
                ?: return null
            val raw = value.asString.trim()
            val number = if (value.isNumber) raw else
                Regex("""^(\d+(?:[.,]\d+)?)(?:\s*/\s*10)?$""")
                    .matchEntire(raw)?.groupValues?.get(1)?.replace(',', '.') ?: return null
            return number.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..10.0 }
        }

        private fun optionalText(item: JsonObject, field: String): String? =
            item.get(field)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                ?.asString?.trim()?.takeIf(String::isNotBlank)

        private fun optionalInt(item: JsonObject, field: String): Int? =
            item.get(field)?.takeIf { it.isJsonPrimitive }
                ?.asString?.trim()?.toIntOrNull()

        private fun normalizeOptionalInt(item: JsonObject, field: String) {
            val value = optionalInt(item, field)
            if (value == null) item.remove(field) else item.addProperty(field, value)
        }

        private fun normalizeOptionalLong(item: JsonObject, field: String) {
            val value = item.get(field)?.takeIf { it.isJsonPrimitive }
                ?.asString?.trim()?.toLongOrNull()
            if (value == null) item.remove(field) else item.addProperty(field, value)
        }

        private fun normalizeAdminFields(item: JsonObject) {
            val year = optionalInt(item, "year") ?: optionalInt(item, "anio")
            if (year == null) item.remove("year") else item.addProperty("year", year)
            if (optionalText(item, "director") == null) {
                val director = optionalText(item, "direccion")
                if (director == null) item.remove("director") else item.addProperty("director", director)
            }
            if (item.get("reparto")?.isJsonArray != true) {
                val cast = optionalText(item, "protagonistas")?.split(',')
                    ?.map(String::trim)?.filter(String::isNotBlank).orEmpty()
                item.add("reparto", Gson().toJsonTree(cast))
            }
            for (field in listOf("trailer_url")) {
                if (item.get(field)?.let { !it.isJsonNull && !it.isJsonPrimitive } == true)
                    item.remove(field)
            }
            item.get("temporadas")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { seasonEntry ->
                if (!seasonEntry.isJsonObject) return@forEach
                val season = seasonEntry.asJsonObject
                normalizeOptionalInt(season, "cantidad_episodios")
                for (field in listOf("nombre", "fecha_emision", "sinopsis", "poster_url")) {
                    if (season.get(field)?.let { !it.isJsonNull && !it.isJsonPrimitive } == true)
                        season.remove(field)
                }
                season.get("episodios")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { episodeEntry ->
                    if (!episodeEntry.isJsonObject) return@forEach
                    val episode = episodeEntry.asJsonObject
                    normalizeOptionalLong(episode, "tmdb_id")
                    for (field in listOf("temporada", "duracion"))
                        normalizeOptionalInt(episode, field)
                    for (field in listOf("id", "titulo", "sinopsis", "fecha_emision", "imagen_url", "telegram_url")) {
                        if (episode.get(field)?.let { !it.isJsonNull && !it.isJsonPrimitive } == true)
                            episode.remove(field)
                    }
                }
            }
        }

        fun parseCatalog(raw: String): Catalog {
            val root = JsonParser.parseString(raw).asJsonObject
            val version = root.get("schema_version")?.asString ?: error("Falta schema_version.")
            require(Regex("^1(\\.[0-9]+){0,2}$").matches(version) || version == "2") {
                "Versión de catálogo incompatible: $version"
            }
            val array = root.getAsJsonArray("items") ?: error("Falta items.")
            val ids = mutableSetOf<String>()
            for (entry in array) {
                val item = entry.asJsonObject
                val id = item.get("id")?.asString?.trim().orEmpty()
                require(id.isNotEmpty() && ids.add(id)) { "ID vacío o repetido: $id" }
                require(item.get("titulo")?.asString?.isNotBlank() == true) { "Falta título en $id" }
                require(item.get("tipo")?.asString in setOf("pelicula", "serie", "anime")) { "Tipo inválido en $id" }
                // Este campo es opcional: un formato desconocido no invalida el catálogo.
                val rating = parseRating(item)
                if (rating == null) item.remove("puntuacion")
                else item.addProperty("puntuacion", rating)
                if (version != "2") normalizeAdminFields(item)
            }
            if (version != "2") return Gson().fromJson(root, Catalog::class.java)

            // El Worker del índice de anime publica una lista de publicaciones por título.
            // No inventamos temporadas o episodios: cada enlace es una publicación independiente.
            val titles = array.map { entry ->
                val item = entry.asJsonObject
                // Gson omite los valores por defecto de Kotlin cuando faltan campos.
                // Este formato solo aporta los campos que aparecen aquí.
                val id = item.get("id").asString
                val directLink = item.get("telegram_url")?.takeUnless { it.isJsonNull }?.asString
                val publications = item.getAsJsonArray("telegram_publicaciones")
                    ?.mapIndexedNotNull { index, publication ->
                        val link = publication.asJsonObject.get("telegram_url")?.asString
                            ?.takeIf { it.startsWith("https://t.me/") }
                        link?.let {
                            Episode(
                                id = "$id-publicacion-$index",
                                numero = index + 1,
                                titulo = publication.asJsonObject.get("texto_indice")?.asString
                                    ?.trim()?.take(100)?.takeIf(String::isNotBlank) ?: "Publicación ${index + 1}",
                                telegramUrl = it
                            )
                        }
                    }.orEmpty()
                Title(
                    id = id,
                    tipo = item.get("tipo").asString,
                    titulo = item.get("titulo").asString,
                    year = item.get("year")?.takeUnless { it.isJsonNull }?.asInt,
                    generos = item.getAsJsonArray("generos")?.map { it.asString }.orEmpty(),
                    calidad = item.get("calidad")?.takeUnless { it.isJsonNull }?.asString,
                    duracion = item.get("duracion")?.takeUnless { it.isJsonNull }?.asString,
                    sinopsis = item.get("sinopsis")?.takeUnless { it.isJsonNull }?.asString,
                    puntuacion = item.get("puntuacion")?.takeUnless { it.isJsonNull }?.asDouble,
                    director = item.get("director")?.takeUnless { it.isJsonNull }?.asString,
                    reparto = item.getAsJsonArray("reparto")?.map { it.asString }.orEmpty(),
                    posterUrl = item.get("poster_url")?.takeUnless { it.isJsonNull }?.asString,
                    backdropUrl = item.get("backdrop_url")?.takeUnless { it.isJsonNull }?.asString,
                    telegramUrl = directLink ?: publications.firstOrNull()?.telegramUrl,
                    temporadas = if (publications.size > 1) listOf(
                        Season(numero = 0, titulo = "Publicaciones", episodios = publications)
                    ) else emptyList()
                )
            }
            return Catalog(schemaVersion = version, updatedAt = root.get("generated_at")?.asString,
                items = titles)
        }
    }
}
