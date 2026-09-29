package ar.com.miflix.client

/** Solo los datos necesarios para continuar; el reproductor no recibe el catálogo. */
internal data class NextEpisode(
    val seasonIndex: Int,
    val episodeIndex: Int,
    val progressKey: String,
    val link: String,
    val name: String,
    val label: String
)

internal fun findNextPlayableEpisode(
    title: Title,
    seasonIndex: Int,
    episodeIndex: Int,
    validLink: (String) -> Boolean
): NextEpisode? {
    if (title.tipo != "serie" || seasonIndex !in title.temporadas.orEmpty().indices) return null
    for (seasonPosition in seasonIndex until title.temporadas.size) {
        val season = title.temporadas[seasonPosition]
        val episodes = season.episodios.orEmpty()
        val first = if (seasonPosition == seasonIndex) episodeIndex + 1 else 0
        for (episodePosition in first.coerceAtLeast(0) until episodes.size) {
            val episode = episodes[episodePosition]
            val link = episode.telegramUrl?.takeIf(validLink) ?: continue
            val name = episode.titulo?.takeIf(String::isNotBlank)
                ?: "Episodio ${episode.numero}"
            return NextEpisode(seasonPosition, episodePosition,
                ProgressKeys.episode(title, season, episode), link, name,
                "T${season.numero} · E${episode.numero.toString().padStart(2, '0')} · $name")
        }
    }
    return null
}
