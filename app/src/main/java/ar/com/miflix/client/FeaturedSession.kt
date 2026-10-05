package ar.com.miflix.client

import java.net.URI
import kotlin.random.Random

/** One instance per ClientApp session; navigation/refresh do not create a new draw. */
internal class FeaturedSession(private val random: Random = Random.Default) {
    private var selectedId: String? = null

    fun select(titles: List<Title>): Title? {
        val candidates = titles.filter(::isFeaturedCandidate).distinctBy { it.id }
        candidates.firstOrNull { it.id == selectedId }?.let { return it }
        val selected = candidates.randomOrNull(random)
        selectedId = selected?.id
        // Preserve the previous safe Hero fallback if no fully usable candidate exists.
        return selected ?: titles.firstOrNull { !it.backdropUrl.isNullOrBlank() }
            ?: titles.firstOrNull { !it.posterUrl.isNullOrBlank() } ?: titles.firstOrNull()
    }
}

internal fun isFeaturedCandidate(title: Title): Boolean {
    if (title.id.isBlank() || title.titulo.isBlank()) return false
    // Match Hero's actual image choice: a nonblank backdrop takes precedence over poster.
    val image = title.backdropUrl?.takeIf { it.isNotBlank() } ?: title.posterUrl ?: return false
    return runCatching {
        val uri = URI(image)
        (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) && !uri.host.isNullOrBlank()
    }.getOrDefault(false)
}
