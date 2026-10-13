package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

class TvPresentationTest {
    @Test fun fullHdTvAtTwoPixelDensityFitsSevenPostersWithoutOverflow() {
        val available = 1920f / 2 - 124
        assertEquals(7, tvPosterColumns(available))
        assertEquals(available, tvPosterWidth(available) * 7 + TV_POSTER_GAP_DP * 6, .01f)
    }
    @Test fun narrowAndWideViewportsAdaptAndKeepPositiveWidths() {
        for (available in listOf(240f, 516f, 836f, 1200f, 1800f)) {
            val columns = tvPosterColumns(available)
            assertTrue(tvPosterWidth(available) > 0)
            assertEquals(available, columns * tvPosterWidth(available) + (columns - 1) * TV_POSTER_GAP_DP, .01f)
        }
        assertTrue(tvPosterColumns(516f) < tvPosterColumns(836f))
        assertEquals(1, tvPosterColumns(Float.NaN))
    }
    @Test fun seriesAndAnimeRetainPublishedEpisodeData() {
        for (type in listOf("serie", "anime")) {
            val json = """{"schema_version":1,"items":[{"id":"x","titulo":"Ejemplo","tipo":"$type","temporadas":[{"numero":1,"episodios":[{"numero":2,"titulo":"Segundo","duracion":24,"sinopsis":"Una historia","imagen_url":"https://example.test/still.jpg"}]}]}]}"""
            val episode = CatalogRepository.parseCatalog(json).items.single().temporadas.single().episodios.single()
            val info = tvEpisodeInfo(episode)
            assertEquals("E2 · Segundo", info.heading)
            assertEquals("24 min", info.duration)
            assertEquals("Una historia", info.synopsis)
            assertEquals("https://example.test/still.jpg", info.image)
        }
    }
    @Test fun missingEpisodeDataDoesNotInventMetadata() {
        val info = tvEpisodeInfo(Episode(numero=0, titulo=" ", duracion=-1, sinopsis=" ", imagenUrl="file:///private"))
        assertEquals("Episodio", info.heading)
        assertNull(info.duration)
        assertNull(info.synopsis)
        assertNull(info.image)
    }
}
