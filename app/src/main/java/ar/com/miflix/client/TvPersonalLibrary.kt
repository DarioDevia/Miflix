package ar.com.miflix.client

import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal interface TvLibraryStorage {
    fun read(): String?
    fun write(json: String)
}

/** Adapted from Mobile 0.9.13. IDs and coverage only; a separate TV store, never the catalog. */
internal class TvPersonalLibraryStore(private val storage: TvLibraryStorage) {
    private val favorites = linkedSetOf<String>()
    private val watched = linkedSetOf<String>()
    private data class Coverage(val durationMs: Long, val intervals: List<WatchedInterval>)
    private val coverage = linkedMapOf<String, Coverage>()
    private val generations = mutableMapOf<String, Long>()
    private val version = MutableStateFlow(0L)
    val revision = version.asStateFlow()
    var readOnlyReason: String? = null
        private set
    var lastError: String? = null
        private set
    private var dirty = false
    private var original: String? = null
    val canWrite: Boolean get() = readOnlyReason == null

    init {
        try {
            original = storage.read()
            val root = JsonParser.parseString(original ?: "{}").asJsonObject
            val schema = root.get("schemaVersion")?.let {
                require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber && it.asString.matches(Regex("[1-9][0-9]*")))
                it.asString.toLong()
            } ?: 1L
            if (schema > 1L) readOnlyReason = "Biblioteca de una versión más reciente; se conserva sin cambios."
            if (root.keySet().any { it !in setOf("schemaVersion", "favorites", "watched", "coverage") })
                readOnlyReason = "Formato de biblioteca desconocido; se conserva sin cambios."
            fun ids(field: String): Set<String> {
                val array = root.get(field) ?: return emptySet()
                require(array.isJsonArray)
                return array.asJsonArray.map { value ->
                    require(value.isJsonPrimitive && value.asJsonPrimitive.isString && value.asString.isNotBlank())
                    value.asString
                }.toSet()
            }
            val loadedFavorites = ids("favorites")
            val loadedWatched = ids("watched")
            // Keep recognized IDs visible even if a separate coverage entry is corrupt.
            favorites.addAll(loadedFavorites); watched.addAll(loadedWatched)
            val loadedCoverage = linkedMapOf<String, Coverage>()
            root.get("coverage")?.let { field ->
                require(field.isJsonObject)
                field.asJsonObject.entrySet().forEach { (key, json) ->
                    require(key.isNotBlank() && json.isJsonObject)
                    val c = json.asJsonObject
                    val duration = c.get("durationMs").asString.toLong()
                    require(duration > 0)
                    val ranges = c.getAsJsonArray("intervals").map {
                        val a = it.asJsonArray
                        require(a.size() == 2)
                        WatchedInterval(a[0].asString.toLong(), a[1].asString.toLong())
                    }
                    loadedCoverage[key] = Coverage(duration, mergeWatchedIntervals(ranges, duration))
                }
            }
            coverage.putAll(loadedCoverage)
        } catch (_: Exception) {
            readOnlyReason = "No se pudo leer la biblioteca local. Los datos se conservan; no se sobrescriben."
        }
    }

    fun isFavorite(id: String) = id in favorites
    fun favorites(titles: List<Title>): List<Title> = titles.distinctBy { it.id }.filter { it.id in favorites }
        .sortedWith(compareBy<Title> { normalizeSearchText(it.titulo) }.thenBy { it.id })
    fun isWatched(key: String) = key in watched
    fun isTitleWatched(title: Title): Boolean = isWatched("title:${title.id}") ||
        (title.tipo == "pelicula" && (isWatched(ProgressKeys.title(title)) || hasWatchedEpisode(title)))
    fun hasWatchedEpisode(title: Title): Boolean = title.temporadas.orEmpty().any { season ->
        season.episodios.orEmpty().any { isWatched(ProgressKeys.episode(title, season, it)) }
    }
    fun watchedTitles(titles: List<Title>): List<Title> = titles.distinctBy { it.id }.filter {
        isTitleWatched(it) || hasWatchedEpisode(it)
    }.sortedWith(compareBy<Title> { normalizeSearchText(it.titulo) }.thenBy { it.id })
    fun generation(key: String): Long = generations[key] ?: 0L

    private fun change(action: () -> Unit) {
        if (!canWrite) return
        val f = favorites.toSet(); val w = watched.toSet(); val c = coverage.toMap(); val g = generations.toMap()
        val wasDirty = dirty
        action()
        try {
            storage.write(exportJson())
            dirty = false; lastError = null
        } catch (_: Exception) {
            favorites.clear(); favorites.addAll(f); watched.clear(); watched.addAll(w)
            coverage.clear(); coverage.putAll(c); generations.clear(); generations.putAll(g)
            dirty = wasDirty
            lastError = "No se pudo guardar la biblioteca. Intentá nuevamente."
        }
        version.value++
    }
    fun setFavorite(id: String, favorite: Boolean) {
        if (id.isBlank() || favorite == isFavorite(id)) return
        change { if (favorite) favorites.add(id) else favorites.remove(id) }
    }
    private fun mark(key: String, seen: Boolean) {
        coverage.remove(key)
        generations[key] = generation(key) + 1
        if (seen) watched.add(key) else watched.remove(key)
    }
    fun setWatched(key: String, seen: Boolean) {
        if (key.isNotBlank()) change { mark(key, seen) }
    }
    fun setTitleWatched(title: Title, seen: Boolean) {
        if (title.id.isBlank()) return
        change {
            val keys = mutableSetOf("title:${title.id}")
            if (title.tipo == "pelicula" && !seen) {
                keys += ProgressKeys.title(title)
                title.temporadas.orEmpty().forEach { season -> season.episodios.orEmpty().forEach {
                    keys += ProgressKeys.episode(title, season, it)
                } }
            }
            keys.forEach { mark(it, seen) }
        }
    }
    fun record(key: String, interval: WatchedInterval, durationMs: Long) {
        if (!canWrite || key.isBlank() || durationMs <= 0 || isWatched(key)) return
        val ranges = mergeWatchedIntervals(coverage[key]?.intervals.orEmpty() + interval, durationMs)
        if (sufficientlyWatched(ranges, durationMs)) change {
            watched.add(key); coverage.remove(key)
        } else {
            coverage[key] = Coverage(durationMs, ranges); dirty = true
        }
    }
    fun flush() {
        if (!dirty || !canWrite) return
        try { storage.write(exportJson()); dirty = false; lastError = null }
        catch (_: Exception) { lastError = "No se pudo guardar la biblioteca. Intentá nuevamente."; version.value++ }
    }
    fun exportJson(): String = if (!canWrite) original.orEmpty() else Gson().toJson(mapOf(
        "schemaVersion" to 1, "favorites" to favorites.toList(), "watched" to watched.toList(),
        "coverage" to coverage.mapValues { (_, c) -> mapOf("durationMs" to c.durationMs,
            "intervals" to c.intervals.map { listOf(it.startMs, it.endMs) }) }))
}
