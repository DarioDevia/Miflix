package ar.com.miflix.client

import kotlin.math.floor

internal const val TV_POSTER_GAP_DP = 8f
internal fun tvPosterColumns(availableWidthDp: Float): Int =
    if (!availableWidthDp.isFinite() || availableWidthDp <= 0f) 1
    else floor((availableWidthDp + TV_POSTER_GAP_DP) / 120f).toInt().coerceIn(1, 20)
internal fun tvPosterWidth(availableWidthDp: Float): Float {
    val width = availableWidthDp.takeIf { it.isFinite() && it > 0f } ?: 112f
    val columns = tvPosterColumns(width)
    return (width - TV_POSTER_GAP_DP * (columns - 1)) / columns
}

internal data class TvEpisodeInfo(val heading: String, val duration: String?,
    val synopsis: String?, val image: String?)
internal fun tvEpisodeInfo(episode: Episode): TvEpisodeInfo {
    val number = episode.numero.takeIf { it > 0 }?.let { "E$it" } ?: "Episodio"
    val title = episode.titulo?.trim()?.takeIf(String::isNotEmpty)
    return TvEpisodeInfo(if (title == null) number else "$number · $title",
        episode.duracion?.takeIf { it > 0 }?.let { "$it min" },
        episode.sinopsis?.trim()?.takeIf(String::isNotEmpty),
        tvArtworkUrl(episode.imagenUrl, TvArtworkKind.BACKDROP))
}
