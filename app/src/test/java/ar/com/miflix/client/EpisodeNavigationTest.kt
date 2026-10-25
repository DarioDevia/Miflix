package ar.com.miflix.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpisodeNavigationTest {
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
        assertEquals("https://t.me/c/1/1",
            findPreviousPlayableEpisode(title, 0, 1, playable)?.link)
    }

    @Test fun skipsMissingAndInvalidLinks() {
        val title = Title(id = "series", tipo = "serie", temporadas = listOf(
            Season(numero = 1, episodios = listOf(episode(1), episode(2, ""),
                episode(3, "https://example.test/not-telegram"), episode(4)))))
        assertEquals(3, findNextPlayableEpisode(title, 0, 0, playable)?.episodeIndex)
        assertEquals(0, findPreviousPlayableEpisode(title, 0, 3, playable)?.episodeIndex)
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
        val previous = findPreviousPlayableEpisode(title, 2, 0, playable)!!
        assertEquals(0, previous.seasonIndex)
        assertEquals(0, previous.episodeIndex)
    }

    @Test fun crossesDirectlyIntoNextSeason() {
        val title = Title(id = "series", tipo = "serie", temporadas = listOf(
            Season(numero = 1, episodios = listOf(episode(8))),
            Season(numero = 2, episodios = listOf(episode(1)))))
        val next = findNextPlayableEpisode(title, 0, 0, playable)!!
        assertEquals(1, next.seasonIndex)
        assertEquals("T2 · E01 · Capítulo 1", next.label)
        val previous = findPreviousPlayableEpisode(title, 1, 0, playable)!!
        assertEquals(0, previous.seasonIndex)
        assertEquals("https://t.me/c/1/8", previous.link)
    }

    @Test fun lastEpisodeAndMovieHaveNoNextEpisode() {
        val seasons = listOf(Season(numero = 1, episodios = listOf(episode(1))))
        assertNull(findNextPlayableEpisode(Title(id = "series", tipo = "serie",
            temporadas = seasons), 0, 0, playable))
        assertNull(findPreviousPlayableEpisode(Title(id = "series", tipo = "serie",
            temporadas = seasons), 0, 0, playable))
        assertNull(findNextPlayableEpisode(Title(id = "movie", tipo = "pelicula",
            temporadas = seasons), 0, 0, playable))
        assertNull(findPreviousPlayableEpisode(Title(id = "movie", tipo = "pelicula",
            temporadas = seasons), 0, 0, playable))
    }

    @Test fun nextEpisodeUsesSameTmdbProgressKeyAsManualPlayback() {
        val season = Season(numero = 1, episodios = listOf(episode(1, tmdbId = 1458520),
            episode(2, tmdbId = 1482178)))
        val title = Title(id = "serie-imdb-tt6656238", tipo = "serie",
            temporadas = listOf(season))
        val next = findNextPlayableEpisode(title, 0, 0, playable)!!
        assertEquals(ProgressKeys.episode(title, season, season.episodios[1]), next.progressKey)
        assertEquals(ProgressKeys.episode(title, season, season.episodios[0]),
            findPreviousPlayableEpisode(title, 0, 1, playable)?.progressKey)
    }

    @Test fun animePublicationsKeepLinkBasedIdentityInBothDirections() {
        val first = Episode(id = "anime-publicacion-0", numero = 1,
            telegramUrl = "https://t.me/c/1/41")
        val second = Episode(id = "anime-publicacion-1", numero = 2,
            telegramUrl = "https://t.me/c/1/42")
        val season = Season(numero = 0, titulo = "Publicaciones", episodios = listOf(first, second))
        val title = Title(id = "anime", tipo = "anime", telegramUrl = first.telegramUrl,
            temporadas = listOf(season))
        assertEquals(ProgressKeys.episode(title, season, second),
            findNextPlayableEpisode(title, 0, 0, playable)?.progressKey)
        assertEquals(ProgressKeys.title(title),
            findPreviousPlayableEpisode(title, 0, 1, playable)?.progressKey)
    }

    @Test fun schemaTwoAnimePublicationsNavigateInPublishedOrder() {
        val json = """{"schema_version":2,"items":[{"id":"anime","tipo":"anime","titulo":"Anime","telegram_publicaciones":[{"telegram_url":"https://t.me/c/1/41"},{"telegram_url":"https://t.me/c/1/42"}]}]}"""
        val title = CatalogRepository.parseCatalog(json).items.single()
        val second = findNextPlayableEpisode(title, 0, 0, playable)!!
        assertEquals("https://t.me/c/1/42", second.link)
        assertEquals(ProgressKeys.episode(title, title.temporadas[0],
            title.temporadas[0].episodios[1]), second.progressKey)
        assertEquals("https://t.me/c/1/41",
            findPreviousPlayableEpisode(title, 0, 1, playable)?.link)
    }
}
