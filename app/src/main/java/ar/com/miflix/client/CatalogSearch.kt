package ar.com.miflix.client

import java.text.Normalizer
import java.util.Locale

internal fun normalizeSearchText(value: String): String = Normalizer
    .normalize(value.trim(), Normalizer.Form.NFD)
    .replace(SEARCH_MARKS, "").lowercase(Locale.ROOT)

private val SEARCH_MARKS = Regex("\\p{M}+")

internal enum class SearchOrder(val label: String) {
    AZ("A-Z"), ZA("Z-A"), NEWEST("Más recientes"), OLDEST("Más antiguos"),
    TOP_RATED("Mejor puntuados")
}

internal data class SearchFilters(
    val type: String? = null,
    val genre: String? = null,
    val year: Int? = null,
    val minimumRating: Int? = null,
    val latestReleases: Boolean = false
) {
    val active: Boolean get() = type != null || genre != null || year != null ||
        minimumRating != null || latestReleases
}

internal data class SearchGenre(val key: String, val label: String)

/** One index per catalog snapshot; episodes are not traversed or copied. */
internal class CatalogSearchIndex(titles: List<Title>) {
    private data class Entry(
        val title: Title,
        val name: String,
        val genres: Set<String>,
        val year: Int?,
        val rating: Double?
    )

    private val genreLabels = linkedMapOf<String, String>()
    private val entries = titles.map { title ->
        val genres = title.generos.orEmpty().mapNotNull { raw ->
            val key = normalizeSearchText(raw.orEmpty())
            key.takeIf { it.isNotEmpty() }?.also { genreLabels.putIfAbsent(it, raw.trim()) }
        }.toSet()
        Entry(title, normalizeSearchText(title.titulo.orEmpty()), genres,
            title.year?.takeIf { it > 0 }, title.puntuacion?.takeIf { it.isFinite() && it in 0.0..10.0 })
    }
    val genres: List<SearchGenre> = genreLabels.entries.sortedBy { it.key }
        .map { SearchGenre(it.key, it.value) }
    val years: List<Int> = entries.mapNotNull { it.year }.distinct().sortedDescending()
    val latestYear: Int? = years.firstOrNull()
    val size: Int get() = entries.size

    private val byName = compareBy<Entry> { it.name }.thenBy { it.title.id }
    // Missing metadata stays last in either direction; ties are deterministic.
    private val sorted = mapOf(
        SearchOrder.AZ to entries.sortedWith(byName),
        SearchOrder.ZA to entries.sortedWith(compareByDescending<Entry> { it.name }.thenBy { it.title.id }),
        SearchOrder.NEWEST to entries.sortedWith(compareBy<Entry> { it.year == null }
            .thenByDescending { it.year }.then(byName)),
        SearchOrder.OLDEST to entries.sortedWith(compareBy<Entry> { it.year == null }
            .thenBy { it.year }.then(byName)),
        SearchOrder.TOP_RATED to entries.sortedWith(compareBy<Entry> { it.rating == null }
            .thenByDescending { it.rating }.then(byName))
    )

    fun search(query: String, filters: SearchFilters = SearchFilters(),
        order: SearchOrder = SearchOrder.AZ): List<Title> {
        val needle = normalizeSearchText(query)
        val genre = filters.genre?.let(::normalizeSearchText)
        val maximumYear = latestYear
        return sorted.getValue(order).asSequence().filter { entry ->
            (needle.isEmpty() || needle in entry.name || entry.genres.any { needle in it }) &&
                (filters.type == null || entry.title.tipo == filters.type) &&
                (genre == null || genre in entry.genres) &&
                (filters.year == null || entry.year == filters.year) &&
                (filters.minimumRating == null ||
                    entry.rating?.let { it >= filters.minimumRating } == true) &&
                (!filters.latestReleases || (maximumYear != null && entry.year != null &&
                    (entry.year == maximumYear || entry.year == maximumYear - 1)))
        }.map { it.title }.toList()
    }
}
