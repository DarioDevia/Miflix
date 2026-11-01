package ar.com.miflix.client

import kotlin.random.Random

internal interface TvSagaStorage {
    fun read(): List<String>
    fun write(ids: List<String>)
}

/** IDs only. A provisional loading frame is never an authoritative deletion. */
internal class TvCollectionsSession(private val storage: TvSagaStorage,
    private val random: Random = Random.Default) {
    private val previous = runCatching { storage.read().distinct().take(10) }.getOrDefault(emptyList())
    private var initialized = false
    private var selected = emptyList<String>()

    @Synchronized fun select(collections: List<TvResolvedCollection>, ready: Boolean = true): List<TvResolvedCollection> {
        if (!ready) return emptyList()
        val candidates = collections.filter { it.collection.visible && it.movies.isNotEmpty() }.distinctBy { it.id }
        val byId = candidates.associateBy { it.id }
        val before = selected
        if (!initialized && candidates.isNotEmpty()) {
            val draw = candidates.shuffled(random).take(10).map { it.id }.toMutableList()
            // With >10, vary membership, not just card order, if the random draw repeats.
            if (draw.toSet() == previous.toSet()) {
                val alternative = candidates.firstOrNull { it.id !in draw }
                if (alternative != null) draw[draw.lastIndex] = alternative.id
                else if (draw == previous && draw.size > 1) {
                    val first = draw[0]; draw[0] = draw[1]; draw[1] = first
                }
            }
            selected = draw
            initialized = true
        } else if (initialized) {
            val survivors = selected.filter { it in byId }
            selected = survivors + candidates.filter { it.id !in survivors }.shuffled(random)
                .take((10 - survivors.size).coerceAtLeast(0)).map { it.id }
        }
        if (selected != before) runCatching { storage.write(selected) }
        return selected.mapNotNull(byId::get)
    }
}

/** Process lifetime: no Activity, Context, View or player retained. */
internal object TvProcessCollections {
    private var session: TvCollectionsSession? = null
    @Synchronized fun get(storage: TvSagaStorage): TvCollectionsSession =
        session ?: TvCollectionsSession(storage).also { session = it }
}
