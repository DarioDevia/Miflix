package ar.com.miflix.client

/** Presentation only. catalogIndex always addresses the original, unmodified title.temporadas. */
internal data class SeasonOption(val catalogIndex: Int, val season: Season, val label: String)

internal fun detailSeasonOptions(title: Title): List<SeasonOption> = title.temporadas.orEmpty()
    .withIndex().sortedBy { (_, season) -> isSpecialSeason(title, season) }
    .map { (index, season) ->
        SeasonOption(index, season, if (isSpecialSeason(title, season)) "Especiales"
            else season.nombre?.takeIf(String::isNotBlank)
                ?: season.titulo?.takeIf(String::isNotBlank) ?: "Temporada ${season.numero}")
    }

private fun isSpecialSeason(title: Title, season: Season): Boolean {
    if (season.numero != 0 || title.tipo !in setOf("serie", "anime")) return false
    // Schema 2 builds this synthetic grouping for indexed anime publications, not TMDB specials.
    val publications = title.tipo == "anime" && season.titulo == "Publicaciones" &&
        season.episodios.orEmpty().isNotEmpty() && season.episodios.orEmpty().all {
            it.id?.startsWith("${title.id}-publicacion-") == true
        }
    return !publications
}
