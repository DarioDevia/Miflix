package ar.com.miflix.client

/** Retain the hero across refresh/navigation; apply priority on a new session/removal. */
internal class TvFeaturedSession {
    private var selectedId: String? = null
    fun select(titles: List<Title>, currentYear: Int): Title? {
        val ordered = tvHomeSuggestions(titles, currentYear, { it.tipo }, { it.year })
        ordered.firstOrNull { it.id == selectedId }?.let { return it }
        val selected = ordered.firstOrNull(::isFeaturedCandidate) ?: ordered.firstOrNull()
        selectedId = selected?.id
        return selected
    }
}
