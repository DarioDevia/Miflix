package ar.com.miflix.client

import android.content.Context
import com.google.gson.Gson
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
    @SerializedName("poster_url") val posterUrl: String? = null,
    @SerializedName("backdrop_url") val backdropUrl: String? = null,
    @SerializedName("telegram_url") val telegramUrl: String? = null,
    val temporadas: List<Season> = emptyList()
)

data class Season(val numero: Int = 0, val titulo: String? = null, val episodios: List<Episode> = emptyList())
data class Episode(
    val id: String = "", val numero: Int = 0, val titulo: String? = null,
    @SerializedName("telegram_url") val telegramUrl: String? = null
)
data class CatalogLoad(val catalog: Catalog, val source: String, val error: String? = null)

/** Un JSON válido se guarda de forma atómica; una respuesta HTML nunca reemplaza la copia buena. */
class CatalogRepository(private val context: Context) {
    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    private val cache get() = File(context.filesDir, "catalogo.json")

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
                check(response.header("Content-Type").orEmpty().contains("json", true)) {
                    "El servidor devolvió una página en vez de JSON."
                }
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
        // Se configura cuando exista una URL estable que realmente publique catalogo.json.
        const val DEFAULT_URL = ""

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
