package ar.com.miflix.client

import java.util.Locale
import kotlin.random.Random

internal const val TV_HERO_LIMIT = 10
internal const val TV_HERO_INTERVAL_MS = 8_000L
internal const val TV_HERO_FADE_MS = 500

internal fun tvHeroEligibleMovie(type: String, id: String, name: String, backgrounds: List<String>): Boolean =
    type == "pelicula" && id.isNotBlank() && name.isNotBlank() && backgrounds.isNotEmpty()

internal data class TvHeroCandidate(val id: String, val genres: Set<String>)

internal fun tvHeroGenres(genres: List<String>): Set<String> = genres
    .map { it.trim().lowercase(Locale.ROOT) }.filter(String::isNotBlank).toSet()

/** Process session: only IDs/genres, never an Activity, player or Telegram session. */
internal class TvFeaturedSession(private val random: Random = Random.Default) {
    private var ids: List<String> = emptyList()
    private var index = 0
    private var previousIds: Set<String>? = null
    private var previousGenres: Set<String> = emptySet()
    private val failedBackdrops = mutableSetOf<String>()
    val currentId: String? get() = ids.getOrNull(index)
    val nextId: String? get() = if (ids.size > 1) ids[(index + 1) % ids.size] else null

    /** Keep surviving IDs/order/index; only fill vacancies on catalog refresh. */
    fun update(candidates: List<TvHeroCandidate>, lastIds: Set<String>, lastGenres: Set<String>): List<String> {
        val available = candidates.filter { it.id.isNotBlank() && it.id !in failedBackdrops }
            .distinctBy { it.id }
        if (available.isEmpty()) { ids = emptyList(); index = 0; return ids }
        if (previousIds == null) { previousIds = lastIds.toSet(); previousGenres = lastGenres.toSet() }
        val byId = available.associateBy { it.id }
        val current = currentId
        val chosen = ids.filter { it in byId }.toMutableList()
        val remaining = available.filter { it.id !in chosen }.shuffled(random).toMutableList()
        val usedGenres = chosen.flatMap { byId.getValue(it).genres }.toMutableSet()
        var last = chosen.lastOrNull()?.let { byId.getValue(it).genres } ?: previousGenres
        while (chosen.size < TV_HERO_LIMIT && remaining.isNotEmpty()) {
            // Random order breaks ties; no rating, release year or catalog-date ranking.
            val next = remaining.minWithOrNull(compareBy<TvHeroCandidate> {
                if (it.id in previousIds.orEmpty()) 1 else 0
            }.thenBy { item -> item.genres.count { it in last } }
                .thenBy { item -> item.genres.count { it in usedGenres } })!!
            remaining.remove(next)
            chosen += next.id
            usedGenres += next.genres
            last = next.genres
        }
        ids = chosen.toList()
        index = ids.indexOf(current).coerceAtLeast(0)
        return ids
    }

    fun advance(): String? {
        if (ids.size > 1) index = (index + 1) % ids.size
        return currentId
    }

    fun rejectBackdrop(id: String) { failedBackdrops += id }
}

/** Recomposition, navigation and Activity recreation reuse the same draw and index. */
internal object TvHeroProcess {
    val session = TvFeaturedSession()
}

internal fun tvHeroCanRotate(count: Int, resumed: Boolean, visible: Boolean, focused: Boolean): Boolean =
    count > 1 && resumed && visible && !focused
