package ar.com.miflix.client

import java.time.Instant
import java.time.OffsetDateTime

internal fun tvCatalogAddedInstant(value: String?): Instant? = value?.trim()?.takeIf(String::isNotEmpty)
    ?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }

/** Only available titles with an explicit valid incorporation date. No fallback date or limit. */
internal fun tvLatestAdded(titles: List<Title>): List<Title> = titles.filter {
    it.id.isNotBlank() && it.titulo.isNotBlank() && it.tipo in setOf("pelicula", "serie", "anime") &&
        tvFeaturedTarget(it) != null
}.distinctBy { it.id }.mapNotNull { title -> tvCatalogAddedInstant(title.addedAt)?.let { title to it } }
    .sortedWith(compareByDescending<Pair<Title, Instant>> { it.second }
        .thenBy { normalizeSearchText(it.first.titulo) }.thenBy { it.first.id }).map { it.first }
