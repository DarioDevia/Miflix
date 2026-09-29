package ar.com.miflix.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextEpisodeTest {
    private val playable: (String) -> Boolean = { it.startsWith("https://t.me/c/") }
    private fun episode(number: Int, link: String? = "https://t.me/c/1/$number",
        tmdbId: Long? = null) = Episode(numero = number, titulo = "Capítulo $number",
        telegramUrl = link, tmdbId = tmdbId)

    @Test fun findsFollowingEpisodeInCatalogOrder() {
        val title = Title(id = "series", tipo = "serie", temporadas = listOf(
            Season(numero = 1, episodios = listOf(episode(1), episode(2)))))
        val next = findNextPlayableEpisode(title, 0, 0, playable)!!
        assertEquals(1, next.episodeIndex)
        assertEquals("https://t.me/c/1/2", next.link)
        assertEquals("T1 · E02 · Capítulo 2", next.label)
    }

    @Test fun skipsMissingAndInvalidLinks() {
        val title = Title(id = "series", tipo = "serie", temporadas = listOf(
            Season(numero = 1, episodios = listOf(episode(1), episode(2, ""),
                episode(3, "https://example.test/not-telegram"), episode(4)))))
        assertEquals(3, findNextPlayableEpisode(title, 0, 0, playable)?.episodeIndex)
    }

    @Test fun crossesSeasonAndSkipsEmptySeasons() {
        val title = Title(id = "series", tipo = "serie", temporadas = listOf(
            Season(numero = 1, episodios = listOf(episode(8))),
            Season(numero = 2, episodios = emptyList()),
            Season(numero = 3, episodios = listOf(episode(1)))))
        val next = findNextPlayableEpisode(title, 0, 0, playable)!!
        assertEquals(2, next.seasonIndex)
        assertEquals(0, next.episodeIndex)
        assertEquals("T3 · E01 · Capítulo 1", next.label)
    }

    @Test fun crossesDirectlyIntoNextSeason() {
        val title = Title(id = "series", tipo = "serie", temporadas = listOf(
            Season(numero = 1, episodios = listOf(episode(8))),
            Season(numero = 2, episodios = listOf(episode(1)))))
        val next = findNextPlayableEpisode(title, 0, 0, playable)!!
        assertEquals(1, next.seasonIndex)
        assertEquals("T2 · E01 · Capítulo 1", next.label)
    }

    @Test fun lastEpisodeAndMovieHaveNoNextEpisode() {
        val seasons = listOf(Season(numero = 1, episodios = listOf(episode(1))))
        assertNull(findNextPlayableEpisode(Title(id = "series", tipo = "serie",
            temporadas = seasons), 0, 0, playable))
        assertNull(findNextPlayableEpisode(Title(id = "movie", tipo = "pelicula",
            temporadas = seasons), 0, 0, playable))
        assertNull(findNextPlayableEpisode(Title(id = "anime", tipo = "anime",
            temporadas = seasons), 0, 0, playable))
    }

    @Test fun nextEpisodeUsesSameTmdbProgressKeyAsManualPlayback() {
        val season = Season(numero = 1, episodios = listOf(episode(1, tmdbId = 1458520),
            episode(2, tmdbId = 1482178)))
        val title = Title(id = "serie-imdb-tt6656238", tipo = "serie",
            temporadas = listOf(season))
        val next = findNextPlayableEpisode(title, 0, 0, playable)!!
        assertEquals(ProgressKeys.episode(title, season, season.episodios[1]), next.progressKey)
    }
}
