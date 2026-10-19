package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

class SeasonPresentationTest {
    private fun series(vararg seasons: Season) = Title(id = "series", tipo = "serie", temporadas = seasons.toList())

    @Test fun specialsMoveAfterAllNormalSeasonsWithOriginalIndexes() {
        val title = series(Season(numero = 0, nombre = "Specials"), Season(numero = 1), Season(numero = 2), Season(numero = 8))
        val options = detailSeasonOptions(title)
        assertEquals(listOf(1, 2, 8, 0), options.map { it.season.numero })
        assertEquals(listOf(1, 2, 3, 0), options.map { it.catalogIndex })
        assertEquals("Especiales", options.last().label)
        assertEquals(1, options.first().catalogIndex) // Initial selection uses catalog index, not visual position.
    }
    @Test fun normalSeasonOrderAndNamesAreNotRewritten() {
        val title = series(Season(numero = 3, nombre = "Nombre existente"), Season(numero = 0),
            Season(numero = 1, titulo = "Primera"), Season(numero = 2))
        val options = detailSeasonOptions(title)
        assertEquals(listOf(3, 1, 2, 0), options.map { it.season.numero })
        assertEquals(listOf("Nombre existente", "Primera", "Temporada 2", "Especiales"), options.map { it.label })
    }
    @Test fun seriesWithoutSpecialsKeepTheirPresentation() {
        val title = series(Season(numero = 1), Season(numero = 2))
        assertEquals(listOf(0, 1), detailSeasonOptions(title).map { it.catalogIndex })
        assertEquals(listOf("Temporada 1", "Temporada 2"), detailSeasonOptions(title).map { it.label })
    }
    @Test fun emptyAndOnlySpecialsCatalogsDoNotInventSeasons() {
        assertTrue(detailSeasonOptions(series()).isEmpty())
        val option = detailSeasonOptions(series(Season(numero = 0))).single()
        assertEquals(0, option.catalogIndex); assertEquals(0, option.season.numero)
        assertEquals("Especiales", option.label)
    }
    @Test fun alreadyLastSpecialsKeepOriginalIndices() {
        val title = series(Season(numero = 1), Season(numero = 2), Season(numero = 0))
        assertEquals(listOf(0, 1, 2), detailSeasonOptions(title).map { it.catalogIndex })
    }
    @Test fun episodeReferencesOrderLinksAndTmdbProgressKeysStayIntact() {
        val episodes = listOf(Episode(numero = 2, tmdbId = 123, telegramUrl = "https://t.me/c/1/21"),
            Episode(numero = 1, tmdbId = 456, telegramUrl = "https://t.me/c/1/22"))
        val special = Season(numero = 0, episodios = episodes)
        val title = series(special, Season(numero = 1))
        val option = detailSeasonOptions(title).last()
        assertSame(special, option.season)
        assertSame(episodes, title.temporadas[option.catalogIndex].episodios)
        assertEquals(listOf(2, 1), option.season.episodios.map { it.numero })
        assertEquals("episode:series:0:tmdb:123", ProgressKeys.episode(title, option.season, episodes[0]))
        val next = findNextPlayableEpisode(title, option.catalogIndex, 0) { it.startsWith("https://t.me/") }!!
        assertEquals("https://t.me/c/1/22", next.link)
        assertEquals(0, next.seasonIndex); assertEquals(1, next.episodeIndex)
        assertEquals(ProgressKeys.episode(title, special, episodes[1]), next.progressKey)
    }
    @Test fun legacySpecialEpisodeKeepsStoredIdentity() {
        val episode = Episode(id = "legacy", numero = 1, telegramUrl = "https://t.me/c/1/2")
        val title = series(Season(numero = 0, episodios = listOf(episode)), Season(numero = 1))
        val option = detailSeasonOptions(title).last()
        assertEquals("episode:series:0:legacy", ProgressKeys.episode(title, option.season, episode))
    }
    @Test fun realAnimeSpecialsAreAlsoPresentedLast() {
        val title = Title(id = "anime", tipo = "anime", temporadas = listOf(
            Season(numero = 0, titulo = "Specials", episodios = listOf(Episode(tmdbId = 1))), Season(numero = 1)))
        assertEquals(listOf(1, 0), detailSeasonOptions(title).map { it.season.numero })
        assertEquals("Especiales", detailSeasonOptions(title).last().label)
    }
    @Test fun schemaTwoSyntheticAnimePublicationsAreNotRelabeled() {
        val title = CatalogRepository.parseCatalog("""{"schema_version":2,"items":[{"id":"anime","tipo":"anime","titulo":"Anime","telegram_publicaciones":[{"telegram_url":"https://t.me/c/1/41"},{"telegram_url":"https://t.me/c/1/42"}]}]}""").items.single()
        val option = detailSeasonOptions(title).single()
        assertEquals("Publicaciones", option.label); assertEquals(0, option.catalogIndex)
        assertSame(title.temporadas[0], option.season)
        assertEquals(ProgressKeys.title(title), ProgressKeys.episode(title, option.season, option.season.episodios[0]))
    }
    @Test fun publicationAvailabilityIsNotChangedByPresentation() {
        val unavailable = Episode(numero = 1)
        val playable = Episode(numero = 2, telegramUrl = "https://t.me/c/1/8")
        val title = series(Season(numero = 0, episodios = listOf(unavailable, playable)), Season(numero = 1), Season(numero = 2))
        val options = detailSeasonOptions(title)
        assertEquals(title.temporadas.size, options.size)
        assertEquals(title.temporadas.toSet(), options.map { it.season }.toSet())
        assertSame(unavailable, options.last().season.episodios[0])
        assertNull(options.last().season.episodios[0].telegramUrl)
        assertSame(playable, options.last().season.episodios[1])
        val movie = title.copy(tipo = "pelicula", temporadas = listOf(Season(numero = 0, titulo = "Publicaciones")))
        assertEquals("Publicaciones", detailSeasonOptions(movie).single().label)
    }
}
