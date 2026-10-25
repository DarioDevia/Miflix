package ar.com.miflix.client

import java.time.Instant
import java.time.OffsetDateTime

internal data class HomeDiscoveryRows(val latestReleases: List<Title>, val latestAdded: List<Title>)

/** ISO date-time with explicit zone only: never infer a date from order, ID or a release year. */
internal fun catalogAddedInstant(value: String?): Instant? = value?.trim()?.takeIf { it.isNotEmpty() }
    ?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }

/** Local catalog projection; caller remembers it per snapshot, never writes or fetches metadata. */
internal fun buildHomeDiscoveryRows(catalog: List<Title>): HomeDiscoveryRows {
    val available = catalog.filter {
        it.id.isNotBlank() && it.titulo.isNotBlank() && it.tipo in setOf("pelicula", "serie", "anime") &&
            playableCatalogTitle(it)
    }.distinctBy { it.id }
    // Same two-year window and year/name ordering as Search, over available content.
    // Title exposes only a release year, not a reliable full release date.
    val releases = CatalogSearchIndex(available).search("", SearchFilters(latestReleases = true), SearchOrder.NEWEST)
    val added = available.mapNotNull { title -> catalogAddedInstant(title.addedAt)?.let { title to it } }
        .sortedWith(compareByDescending<Pair<Title, Instant>> { it.second }
            .thenBy { normalizeSearchText(it.first.titulo) }.thenBy { it.first.id })
        .map { it.first }
    return HomeDiscoveryRows(releases, added)
}
