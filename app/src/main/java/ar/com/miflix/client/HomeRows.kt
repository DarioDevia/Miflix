package ar.com.miflix.client

internal sealed class HomeMoreTarget {
    data class Category(val label: String) : HomeMoreTarget()
    data class Genre(val key: String) : HomeMoreTarget()
    object Library : HomeMoreTarget()
}

internal data class HomeRow(val key: String, val label: String, val titles: List<Title>, val more: HomeMoreTarget) {
    fun select(id: String, onSelect: (Title) -> Unit) { titles.firstOrNull { it.id == id }?.let(onSelect) }
}
internal data class HomeCatalogRows(val general: List<HomeRow>, val genres: List<HomeRow>)

/** Pure grouping, computed once per catalog snapshot; no I/O, ranking service or favorite writes. */
internal fun buildHomeRows(catalog: List<Title>, genreLimit: Int = 8): HomeCatalogRows {
    val ordered = catalog.filter { it.id.isNotBlank() && it.titulo.isNotBlank() }.distinctBy { it.id }
        .sortedWith(compareBy<Title> { normalizeSearchText(it.titulo) }.thenBy { it.id })
    val types = listOf("pelicula" to "Películas", "serie" to "Series", "anime" to "Anime")
    val general = types.mapNotNull { (type, label) ->
        ordered.filter { it.tipo == type }.takeIf { it.isNotEmpty() }?.let {
            HomeRow("type:$type", label, it, HomeMoreTarget.Category(label))
        }
    }
    val groups = linkedMapOf<String, MutableList<Title>>()
    val labels = linkedMapOf<String, MutableSet<String>>()
    ordered.forEach { title ->
        title.generos.orEmpty().forEach { raw ->
            val key = normalizeCatalogGenre(raw.orEmpty())
            if (key.isNotEmpty()) {
                labels.getOrPut(key) { linkedSetOf() }.add(raw.trim())
                val group = groups.getOrPut(key) { mutableListOf() }
                if (group.lastOrNull()?.id != title.id) group.add(title)
            }
        }
    }
    // Do not repeat a type row or another genre with exactly the same catalog membership.
    val signatures = general.map { row -> row.titles.map { it.id }.toSet() }.toMutableSet()
    val selected = groups.entries.sortedWith(compareByDescending<Map.Entry<String, MutableList<Title>>> { it.value.size }
        .thenBy { it.key }).mapNotNull { (key, titles) ->
        if (!signatures.add(titles.map { it.id }.toSet())) null
        else HomeRow("genre:$key", labels.getValue(key).sortedWith(
            compareBy<String> { if (it.any(Char::isLowerCase)) 0 else 1 }.thenBy { it }).first(),
            titles, HomeMoreTarget.Genre(key))
    }.take(genreLimit.coerceAtLeast(0)).sortedBy { (it.more as HomeMoreTarget.Genre).key }
    return HomeCatalogRows(general, selected)
}

internal fun homeFavoriteRow(titles: List<Title>, store: PersonalLibraryStore?): HomeRow? =
    store?.favorites(titles)?.takeIf { it.isNotEmpty() }?.let {
        HomeRow("library:favorites", "Mi Lista", it, HomeMoreTarget.Library)
    }
