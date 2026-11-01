package ar.com.miflix.client

internal data class TvGenreRow(val key: String, val label: String, val titles: List<Title>)

/** Same spelling normalization as Mobile, without aliases or inferred genre/type assignments. */
internal fun tvGenreKey(raw: String): String = normalizeSearchText(raw).replace(Regex("\\s+"), " ")

internal fun tvGenreRows(titles: List<Title>): List<TvGenreRow> {
    val labels = linkedMapOf<String, String>()
    val groups = linkedMapOf<Pair<String, String>, MutableList<Title>>()
    titles.asSequence().filter { it.id.isNotBlank() && tvFeaturedTarget(it) != null }
        .distinctBy { it.id }.forEach { title ->
            title.generos.orEmpty().filterNotNull().map { it.trim().replace(Regex("\\s+"), " ") }
                .filter { it.isNotEmpty() && it.toLongOrNull() == null }.distinctBy(::tvGenreKey).forEach { raw ->
                    val key = tvGenreKey(raw)
                    labels.putIfAbsent(key, raw)
                    groups.getOrPut(title.tipo to key) { mutableListOf() }.add(title)
                }
        }
    return listOf("pelicula" to "Películas", "serie" to "Series", "anime" to "Anime").flatMap { (type, name) ->
        groups.keys.filter { it.first == type }.sortedBy { it.second }.map { pair ->
            TvGenreRow("genre:$type:${pair.second}", "$name · ${labels.getValue(pair.second)}",
                groups.getValue(pair).sortedWith(compareBy<Title> { normalizeSearchText(it.titulo) }.thenBy { it.id }))
        }
    }
}
