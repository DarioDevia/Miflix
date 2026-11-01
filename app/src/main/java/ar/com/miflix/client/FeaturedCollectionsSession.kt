package ar.com.miflix.client

import kotlin.random.Random

internal interface SagaSelectionStorage {
    fun read(): List<String>
    fun write(ids: List<String>)
}

/** Session state holds IDs only; refresh resolves current metadata without reshuffling survivors. */
internal class FeaturedCollectionsSession(private val storage: SagaSelectionStorage,
    private val random: Random = Random.Default) {
    private val previous = storage.read().distinct().take(10)
    private var initialized = false
    private var selected = emptyList<String>()

    @Synchronized
    fun select(collections: List<ResolvedCollection>, catalogReady: Boolean = true): List<ResolvedCollection> {
        // A reconstructed Activity starts with a loading placeholder, not an authoritative deletion.
        if (!catalogReady) return emptyList()
        val candidates = collections.filter { it.collection.visible && it.movies.isNotEmpty() }
            .distinctBy { it.id }
        val byId = candidates.associateBy { it.id }
        val before = selected
        if (!initialized && candidates.isNotEmpty()) {
            val draw = candidates.shuffled(random).take(10).map { it.id }.toMutableList()
            if (draw == previous && draw.size > 1) {
                val first = draw[0]; draw[0] = draw[1]; draw[1] = first
            }
            selected = draw
            initialized = true
        } else if (initialized) {
            val survivors = selected.filter { it in byId }
            val additions = candidates.filter { it.id !in survivors }.shuffled(random)
                .take((10 - survivors.size).coerceAtLeast(0)).map { it.id }
            selected = survivors + additions
        }
        if (selected != before && selected.isNotEmpty()) storage.write(selected)
        return selected.mapNotNull(byId::get)
    }
}

/** Process lifetime, not onResume/Activity lifetime. Holds no Activity/View/Window/Player. */
internal object ProcessSagaSession {
    private var session: FeaturedCollectionsSession? = null
    @Synchronized fun get(storage: SagaSelectionStorage): FeaturedCollectionsSession =
        session ?: FeaturedCollectionsSession(storage).also { session = it }
}
