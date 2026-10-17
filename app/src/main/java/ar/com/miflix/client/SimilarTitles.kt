package ar.com.miflix.client

import java.net.URI
import kotlin.math.abs

internal fun playableCatalogTitle(title: Title): Boolean {
    fun link(value: String?): Boolean = runCatching {
        val uri = URI(value?.trim().orEmpty())
        uri.scheme.equals("https", true) && uri.host?.lowercase() in
            setOf("t.me", "www.t.me", "telegram.me") && !uri.path.isNullOrBlank() && uri.path != "/"
    }.getOrDefault(false)
    return (title.tipo != "serie" && link(title.telegramUrl)) ||
        title.temporadas.orEmpty().any { s -> s.episodios.orEmpty().any { link(it.telegramUrl) } }
}

/** Local explainable ranking: shared genres, type, synopsis terms, year proximity, rating. */
internal fun similarTitles(title: Title, catalog: List<Title>, limit: Int = 12): List<Title> {
    fun genres(t: Title) = t.generos.orEmpty().map(::normalizeSearchText).filter(String::isNotBlank).toSet()
    val stop = setOf("para", "como", "pero", "esta", "este", "entre", "sobre", "cuando", "donde",
        "desde", "hasta", "tiene", "todos", "todo", "their", "with", "that", "from", "they", "which")
    fun terms(t: Title) = normalizeSearchText(t.sinopsis.orEmpty()).split(Regex("[^a-z0-9]+"))
        .filter { it.length >= 4 && it !in stop }.toSet()
    val sourceGenres = genres(title)
    val sourceTerms = terms(title)
    data class Match(val title: Title, val shared: Int, val sameType: Boolean, val themes: Int, val year: Int)
    return catalog.asSequence().filter { it.id.isNotBlank() && it.id != title.id && it.titulo.isNotBlank() }
        .distinctBy { it.id }.filter(::playableCatalogTitle).map { candidate ->
            Match(candidate, (sourceGenres intersect genres(candidate)).size, candidate.tipo == title.tipo,
                (sourceTerms intersect terms(candidate)).size,
                if (title.year != null && candidate.year != null) abs(title.year.toLong() - candidate.year).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                else Int.MAX_VALUE)
        }.filter { it.shared > 0 } // Do not fabricate a relationship from year/rating alone.
        .sortedWith(compareByDescending<Match> { it.shared }.thenByDescending { it.sameType }
            .thenByDescending { it.themes }.thenBy { it.year }
            .thenByDescending { it.title.puntuacion?.takeIf { n -> n.isFinite() && n in 0.0..10.0 } ?: -1.0 }
            .thenBy { normalizeSearchText(it.title.titulo) }.thenBy { it.title.id })
        .take(limit.coerceAtLeast(0)).map { it.title }.toList()
}
