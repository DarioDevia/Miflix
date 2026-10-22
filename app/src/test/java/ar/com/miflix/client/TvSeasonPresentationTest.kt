package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

class TvSeasonPresentationTest {
    @Test fun normalSeasonsAreAscending() {
        val seasons = listOf(Season(numero = 3), Season(numero = 1), Season(numero = 2))
        assertEquals(listOf(1, 2, 0), tvSeasonIndices(seasons))
    }
    @Test fun specialsAreLastWithOriginalIndexAndLabel() {
        val seasons = listOf(Season(numero = 0), Season(numero = 2), Season(numero = 1))
        assertEquals(listOf(2, 1, 0), tvSeasonIndices(seasons))
        assertEquals("Especiales", tvSeasonLabel(seasons[0]))
        assertEquals(0, seasons[0].numero)
        assertEquals("Temporada 2", tvSeasonLabel(seasons[1]))
    }
    @Test fun onlySpecialsAndEmptyCatalogRemainUnchanged() {
        assertEquals(emptyList<Int>(), tvSeasonIndices(emptyList()))
        assertEquals(listOf(0), tvSeasonIndices(listOf(Season(numero = 0))))
    }
    @Test fun mappingPreservesEpisodeIdentityFileAndProgressKey() {
        val episode = Episode(id = "original", numero = 2, tmdbId = 42, telegramUrl = "https://t.me/c/1/42")
        val special = Season(numero = 0, episodios = listOf(episode))
        val title = Title(id = "series", temporadas = listOf(special, Season(numero = 1)))
        val original = ProgressKeys.episode(title, special, episode)
        val index = tvSeasonIndices(title.temporadas).last()
        assertSame(special, title.temporadas[index])
        assertSame(episode, title.temporadas[index].episodios[0])
        assertEquals(original, ProgressKeys.episode(title, title.temporadas[index], episode))
        assertEquals("https://t.me/c/1/42", title.temporadas[index].episodios[0].telegramUrl)
    }
    @Test fun duplicatesKeepRelativeOrderAndNoSeasonsOrEpisodesAreAdded() {
        val seasons = listOf(Season(numero = 2), Season(numero = 0), Season(numero = 2))
        assertEquals(listOf(0, 2, 1), tvSeasonIndices(seasons))
        assertEquals(seasons.indices.toSet(), tvSeasonIndices(seasons).toSet())
        assertTrue(seasons.all { it.episodios.isEmpty() })
    }
}
