package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

class SimilarTitlesTest {
    private val source=Title(id="a",titulo="A",tipo="pelicula",generos=listOf("Acción"),year=2024)
    private fun candidate(id:String, type:String="pelicula", genres:List<String> = listOf("Accion")) =
        Title(id=id,titulo=id,tipo=type,generos=genres,telegramUrl="https://t.me/c/1/2",year=2024)
    @Test fun excludesSelfDuplicatesUnplayableAndUnrelated() {
        val b=candidate("b")
        val result=similarTitles(source,listOf(source,b,b,candidate("x",genres=listOf("Drama")),b.copy(id="empty",telegramUrl=null)))
        assertEquals(listOf("b"),result.map { it.id })
    }
    @Test fun catalogOrderDoesNotChangeRanking() {
        val items=listOf(candidate("z"),candidate("b"),candidate("c",type="anime"))
        assertEquals(similarTitles(source,items),similarTitles(source,items.reversed()))
        assertEquals("b",similarTitles(source,items).first().id)
    }
    @Test fun episodeLinkMakesSeriesPlayableButLandingPageDoesNot() {
        val series=candidate("series",type="serie")
        assertFalse(playableCatalogTitle(series))
        assertTrue(playableCatalogTitle(series.copy(temporadas=listOf(Season(episodios=listOf(Episode(telegramUrl="https://t.me/c/1/3")))))))
    }
    @Test fun yearAndRatingAreSecondaryAndMissingMetadataIsSafe() {
        assertTrue(similarTitles(source,listOf(candidate("x",genres=emptyList()))).isEmpty())
        assertEquals(1,similarTitles(source,listOf(candidate("b").copy(year=null,puntuacion=Double.NaN))).size)
        assertTrue(similarTitles(source,emptyList()).isEmpty())
        assertTrue(similarTitles(source,listOf(candidate("b")),-1).isEmpty())
    }
    @Test fun sharedGenresRankBeforeTypeAndThemeBreaksTies() {
        val s=source.copy(generos=listOf("Acción","Drama"),sinopsis="Una expedición marciana peligrosa")
        val a=candidate("a1").copy(sinopsis="Expedición marciana")
        val b=candidate("b",type="anime",genres=listOf("Acción","Drama"))
        val c=candidate("c")
        assertEquals(listOf("b","a1","c"),similarTitles(s,listOf(c,a,b)).map { it.id })
    }
}
