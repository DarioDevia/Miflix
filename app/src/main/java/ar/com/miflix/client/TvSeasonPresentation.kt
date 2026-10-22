package ar.com.miflix.client

/** Indices always address the original catalog, including playback navigation callbacks. */
internal fun tvSeasonIndices(seasons: List<Season>): List<Int> = seasons.indices
    .sortedWith(compareBy<Int> { seasons[it].numero == 0 }.thenBy { seasons[it].numero })

internal fun tvSeasonLabel(season: Season): String =
    if (season.numero == 0) "Especiales" else "Temporada ${season.numero}"
