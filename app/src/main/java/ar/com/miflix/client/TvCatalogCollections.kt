package ar.com.miflix.client

import com.google.gson.JsonObject
import java.net.URI

/** Public catalog metadata. Members are MiFlix IDs, never TMDB IDs or movie names. */
data class TvCatalogCollection(
    val id: String,
    val nombre: String,
    val descripcion: String?,
    val origen: String,
    val tmdbCollectionId: Long?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val orden: Int,
    val visible: Boolean,
    val titulos: List<String>
)

internal fun tvCollectionImageUrl(value: String?): String? = value?.trim()?.takeIf { raw ->
    raw.length <= 2000 && raw.none(Char::isWhitespace) && runCatching {
        val uri = URI(raw)
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null
    }.getOrDefault(false)
}

/** One malformed optional collection cannot discard the valid title catalog. No total-count cap. */
internal fun parseTvCatalogCollections(root: JsonObject): List<TvCatalogCollection> {
    val version = root.get("colecciones_version")
    if (version != null && (!version.isJsonPrimitive || !version.asJsonPrimitive.isNumber ||
            version.asString != "1")) return emptyList()
    val entries = root.get("colecciones")?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
    val result = mutableListOf<TvCatalogCollection>()
    val ids = mutableSetOf<String>()
    entries.forEach { entry ->
        val parsed = runCatching {
            val item = entry.asJsonObject
            fun text(key: String, max: Int, required: Boolean = false): String? {
                val value = item.get(key)
                if (value == null || value.isJsonNull) {
                    require(!required)
                    return null
                }
                require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
                val raw = value.asString
                require(raw.length <= max && (!required || raw.isNotBlank()))
                return raw
            }
            val id = text("id", 300, true)!!
            val name = text("nombre", 200, true)!!
            val origin = text("origen", 20, true)!!
            require(origin == "manual" || origin == "tmdb")
            val tmdb = item.get("tmdb_collection_id")?.takeUnless { it.isJsonNull }?.let {
                require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber)
                it.asBigDecimal.longValueExact().also { number -> require(number > 0) }
            }
            require(origin != "tmdb" || tmdb != null)
            val order = item.get("orden")!!
            require(order.isJsonPrimitive && order.asJsonPrimitive.isNumber)
            val position = order.asBigDecimal.intValueExact().also { require(it in 0..1_000_000) }
            val visible = item.get("visible")!!
            require(visible.isJsonPrimitive && visible.asJsonPrimitive.isBoolean)
            val members = item.get("titulos")!!.asJsonArray.map {
                require(it.isJsonPrimitive && it.asJsonPrimitive.isString)
                it.asString.also { idValue -> require(idValue.isNotBlank() && idValue.length <= 300) }
            }.distinct()
            TvCatalogCollection(id, name, text("descripcion", 12000), origin, tmdb,
                tvCollectionImageUrl(text("poster_url", 2000)), tvCollectionImageUrl(text("backdrop_url", 2000)),
                position, visible.asBoolean, members)
        }.getOrNull()
        if (parsed != null && ids.add(parsed.id)) result += parsed
    }
    return result
}

internal data class TvResolvedCollection(val collection: TvCatalogCollection, val movies: List<Title>) {
    val id get() = collection.id
    val posterCandidates: List<String> get() = (listOf(collection.posterUrl) +
        movies.map { it.posterUrl } + movies.map { it.backdropUrl }).mapNotNull(::tvCollectionImageUrl).distinct()
    val backdropCandidates: List<String> get() = (listOf(collection.backdropUrl) +
        movies.map { it.backdropUrl } + posterCandidates).mapNotNull(::tvCollectionImageUrl).distinct()
    fun selectMovie(id: String, onSelect: (Title) -> Unit) { movies.firstOrNull { it.id == id }?.let(onSelect) }
}

internal fun resolveTvCollections(catalog: Catalog): List<TvResolvedCollection> {
    val movies = catalog.items.orEmpty().filter { it.tipo == "pelicula" && tvFeaturedTarget(it) != null }.associateBy { it.id }
    return catalog.tvCollections.filter { it.visible }.distinctBy { it.id }.mapNotNull { collection ->
        collection.titulos.distinct().mapNotNull(movies::get).takeIf { it.isNotEmpty() }
            ?.let { TvResolvedCollection(collection, it) }
    }.sortedWith(compareBy<TvResolvedCollection> { it.collection.orden }
        .thenBy { normalizeSearchText(it.collection.nombre) }.thenBy { it.id })
}
