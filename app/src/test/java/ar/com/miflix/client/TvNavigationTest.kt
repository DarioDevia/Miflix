package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

class TvNavigationTest {
    private val link = "https://t.me/c/1/1"
    @Test fun missingMovieVideoDoesNotOfferEpisodes() {
        assertEquals("Ver detalle", tvFeaturedActionLabel(Title(tipo="pelicula")))
        assertEquals("▶ Reproducir", tvFeaturedActionLabel(Title(tipo="pelicula", telegramUrl=link)))
    }
    @Test fun unavailableSeriesAndAnimeStillAllowBrowsingTheirEpisodes() {
        for (type in listOf("serie", "anime")) {
            assertEquals("Ver detalle", tvFeaturedActionLabel(Title(tipo=type)))
            assertEquals("Elegir episodio", tvFeaturedActionLabel(Title(tipo=type,
                temporadas=listOf(Season(episodios=listOf(Episode(numero=1)))))))
        }
    }
    @Test fun featuredSkipsEmptySeasonsWithoutLosingTheEpisodeDestination() {
        for (type in listOf("serie", "anime")) {
            val episode = Episode(id="later", numero=7, telegramUrl=link)
            val season = Season(numero=2, episodios=listOf(episode))
            val title = Title(id=type, tipo=type, temporadas=listOf(Season(numero=1), season))
            val target = tvFeaturedTarget(title)!!
            assertEquals(1, target.seasonIndex)
            assertEquals(0, target.episodeIndex)
            assertEquals(ProgressKeys.episode(title, season, episode), target.progressKey)
        }
    }
    @Test fun featuredMovieUsesItsOrdinaryProgressKey() {
        val title = Title(id="movie", tipo="pelicula", titulo="Película", telegramUrl=link)
        val target = tvFeaturedTarget(title)!!
        assertEquals(ProgressKeys.title(title), target.progressKey)
        assertEquals(-1, target.seasonIndex)
        assertNull(tvFeaturedTarget(title.copy(telegramUrl="javascript:bad")))
    }
    @Test fun seriesAndAnimePreferFirstPlayableEpisodeWithItsOwnProgressKey() {
        for (type in listOf("serie", "anime")) {
            val episode = Episode(id="e2", numero=2, titulo="Segundo", telegramUrl=link)
            val season = Season(numero=3, episodios=listOf(Episode(numero=1), episode))
            val title = Title(id=type, tipo=type, titulo=type, telegramUrl=link, temporadas=listOf(season))
            val target = tvFeaturedTarget(title)!!
            assertEquals(1,target.episodeIndex)
            assertEquals(0,target.seasonIndex)
            assertEquals(ProgressKeys.episode(title,season,episode),target.progressKey)
        }
    }
    @Test fun seriesWithoutPlayableEpisodesDoesNotInventADestination() {
        assertNull(tvFeaturedTarget(Title(tipo="serie", telegramUrl=link,
            temporadas=listOf(Season(episodios=listOf(Episode(numero=1)))))))
    }
    @Test fun verticalNavigationSkipsUnavailableEpisodesInBothDirections() {
        val episodes=listOf(Episode(telegramUrl=link),Episode(),Episode(telegramUrl="bad"),Episode(telegramUrl=link))
        val valid: (String)->Boolean={it==link}
        assertEquals(3,tvAdjacentPlayableIndex(episodes,0,1,valid))
        assertEquals(0,tvAdjacentPlayableIndex(episodes,3,-1,valid))
        assertNull(tvAdjacentPlayableIndex(episodes,0,-1,valid))
        assertNull(tvAdjacentPlayableIndex(episodes,3,1,valid))
    }
}
