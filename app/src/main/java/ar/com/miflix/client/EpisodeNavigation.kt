package ar.com.miflix.client

/** Solo el destino; el reproductor no recibe el catálogo. */
internal data class EpisodeNavigationTarget(
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
): EpisodeNavigationTarget? = findAdjacentPlayableEpisode(title, seasonIndex, episodeIndex,
    direction = 1, validLink = validLink)

internal fun findPreviousPlayableEpisode(
    title: Title,
    seasonIndex: Int,
    episodeIndex: Int,
    validLink: (String) -> Boolean
): EpisodeNavigationTarget? = findAdjacentPlayableEpisode(title, seasonIndex, episodeIndex,
    direction = -1, validLink = validLink)

private fun findAdjacentPlayableEpisode(
    title: Title,
    seasonIndex: Int,
    episodeIndex: Int,
    direction: Int,
    validLink: (String) -> Boolean
): EpisodeNavigationTarget? {
    val seasons = title.temporadas.orEmpty()
    if (title.tipo !in setOf("serie", "anime") || seasonIndex !in seasons.indices ||
        episodeIndex !in seasons[seasonIndex].episodios.orEmpty().indices) return null
    var seasonPosition = seasonIndex
    while (seasonPosition in seasons.indices) {
        val season = seasons[seasonPosition]
        val episodes = season.episodios.orEmpty()
        var episodePosition = if (seasonPosition == seasonIndex) episodeIndex + direction
            else if (direction > 0) 0 else episodes.lastIndex
        while (episodePosition in episodes.indices) {
            val episode = episodes[episodePosition]
            val link = episode.telegramUrl?.takeIf(validLink)
            if (link != null) {
                val name = episode.titulo?.takeIf(String::isNotBlank)
                    ?: "Episodio ${episode.numero}"
                val label = if (title.tipo == "anime" && season.numero == 0) name else
                    "T${season.numero} · E${episode.numero.toString().padStart(2, '0')} · $name"
                return EpisodeNavigationTarget(seasonPosition, episodePosition,
                    ProgressKeys.episode(title, season, episode), link, name, label)
            }
            episodePosition += direction
        }
        seasonPosition += direction
    }
    return null
}
