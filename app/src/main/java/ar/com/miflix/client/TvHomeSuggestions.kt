package ar.com.miflix.client

/** Stable catalog ties; invalid/future years cannot displace already released titles. */
internal fun <T> tvHomeSuggestions(items: List<T>, currentYear: Int,
    type: (T) -> String, year: (T) -> Int?): List<T> {
    val candidates = items.filter { type(it) == "pelicula" || type(it) == "serie" }
    val hasReleasedYear = candidates.any { year(it)?.let { y -> y in 1800..currentYear } == true }
    // A clock before every catalog release falls back to valid catalog years.
    val ceiling = if (hasReleasedYear) currentYear else 9999
    return candidates.sortedWith(compareByDescending<T> { item ->
        year(item)?.takeIf { it in 1800..ceiling } ?: Int.MIN_VALUE
    })
}
