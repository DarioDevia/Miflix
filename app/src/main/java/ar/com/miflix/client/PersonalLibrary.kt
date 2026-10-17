package ar.com.miflix.client

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal interface LibraryStorage {
    fun read(): String?
    fun write(json: String)
}

/** Independent of playback progress; only stable IDs and consumed intervals, never Telegram URLs. */
internal class PersonalLibraryStore(private val storage: LibraryStorage) {
    private val favorites = linkedSetOf<String>()
    private val watched = linkedSetOf<String>()
    private data class Coverage(val durationMs: Long, val intervals: List<WatchedInterval>)
    private val coverage = linkedMapOf<String, Coverage>()
    private val version = MutableStateFlow(0L)
    val revision = version.asStateFlow()
    private var dirty = false
    private val original = storage.read()
    private var writable = true

    init {
        val root = runCatching { JsonParser.parseString(original ?: "{}").asJsonObject }.getOrDefault(JsonObject())
        writable = runCatching { root.get("schemaVersion")?.asInt ?: 1 }.getOrDefault(1) <= 1
        fun readIds(field: String, into: MutableSet<String>) {
            runCatching { root.getAsJsonArray(field) }.getOrNull()?.forEach { value ->
                runCatching { value.asString }.getOrNull()?.takeIf { it.isNotBlank() }?.let(into::add)
            }
        }
        readIds("favorites", favorites)
        readIds("watched", watched)
        runCatching { root.getAsJsonObject("coverage") }.getOrNull()?.entrySet()?.forEach { (key, json) ->
            runCatching {
                val value = json.asJsonObject
                val duration = value.get("durationMs").asLong
                val ranges = value.getAsJsonArray("intervals").mapNotNull {
                    runCatching { val a = it.asJsonArray; WatchedInterval(a[0].asLong, a[1].asLong) }.getOrNull()
                }
                if (key.isNotBlank() && duration > 0) coverage[key] = Coverage(duration, mergeWatchedIntervals(ranges, duration))
            }
        }
    }
    fun isFavorite(id: String) = id in favorites
    fun setFavorite(id: String, favorite: Boolean) {
        if (!writable || id.isBlank()) return
        val modified = if (favorite) favorites.add(id) else favorites.remove(id)
        if (modified) changed()
    }
    fun favorites(titles: List<Title>): List<Title> = titles.distinctBy { it.id }
        .filter { it.id in favorites }.sortedWith(compareBy<Title> { normalizeSearchText(it.titulo) }.thenBy { it.id })

    fun isWatched(key: String) = key in watched
    // A manual title mark never fans out to episodes. An episode mark never marks the parent series.
    fun isTitleWatched(title: Title): Boolean = isWatched("title:${title.id}") ||
        (title.tipo == "pelicula" && (isWatched(ProgressKeys.title(title)) || hasWatchedEpisode(title)))
    fun hasWatchedEpisode(title: Title): Boolean = title.temporadas.orEmpty().any { season ->
        season.episodios.orEmpty().any { isWatched(ProgressKeys.episode(title, season, it)) }
    }
    fun setWatched(key: String, seen: Boolean) {
        if (!writable || key.isBlank()) return
        coverage.remove(key) // Unmark starts fresh; old coverage must not immediately mark it again.
        if (seen) watched.add(key) else watched.remove(key)
        changed()
    }
    fun setTitleWatched(title: Title, seen: Boolean) {
        if (!writable || title.id.isBlank()) return
        val keys = mutableSetOf("title:${title.id}")
        // Movie publications are alternative playable identities of the movie, not series episodes.
        if (title.tipo == "pelicula" && !seen) {
            keys += ProgressKeys.title(title)
            title.temporadas.orEmpty().forEach { season ->
                season.episodios.orEmpty().forEach { keys += ProgressKeys.episode(title, season, it) }
            }
        }
        keys.forEach { key ->
            coverage.remove(key)
            if (seen) watched.add(key) else watched.remove(key)
        }
        changed()
    }
    fun record(key: String, interval: WatchedInterval, durationMs: Long) {
        if (!writable || key.isBlank() || durationMs <= 0 || isWatched(key)) return
        val ranges = mergeWatchedIntervals(coverage[key]?.intervals.orEmpty() + interval, durationMs)
        coverage[key] = Coverage(durationMs, ranges)
        dirty = true
        if (sufficientlyWatched(ranges, durationMs)) {
            watched.add(key)
            coverage.remove(key)
            changed()
        }
    }
    fun flush() {
        if (dirty && writable) { storage.write(exportJson()); dirty = false }
    }
    fun exportJson(): String {
        if (!writable) return original.orEmpty()
        return Gson().toJson(mapOf("schemaVersion" to 1, "favorites" to favorites.toList(),
            "watched" to watched.toList(), "coverage" to coverage.mapValues { (_, c) ->
                mapOf("durationMs" to c.durationMs, "intervals" to c.intervals.map { listOf(it.startMs, it.endMs) })
            }))
    }
    private fun changed() {
        dirty = true
        flush()
        version.value++
    }
}
