package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

class TvHomeDiscoveryTest {
    private fun title(id: String, date: String?, type: String = "pelicula") = Title(id = id, titulo = id,
        tipo = type, addedAt = date, telegramUrl = "https://t.me/c/1/1")
    @Test fun zonesSortByActualInstantInsteadOfLexicalOrder() {
        val a = title("a", "2026-01-01T12:00:00+03:00")
        val b = title("b", "2026-01-01T10:00:00Z")
        assertEquals(listOf("b", "a"), tvLatestAdded(listOf(a,b)).map { it.id })
        assertEquals(tvCatalogAddedInstant("2026-01-01T10:00:00Z"), tvCatalogAddedInstant("2026-01-01T07:00:00-03:00"))
    }
    @Test fun missingMalformedAndZonelessDatesNeverGetFallbacks() {
        for (date in listOf(null, "", "invalid", "2026", "2026-01-01", "2026-01-01T12:00:00", "2026-02-30T12:00:00Z")) {
            assertNull(date, tvCatalogAddedInstant(date))
            assertTrue(tvLatestAdded(listOf(title("a", date).copy(year=2026))).isEmpty())
        }
    }
    @Test fun allTypesAvailableDeduplicatedAndNoArtificialLimit() {
        val titles = (1..100).map { title("a$it", "2026-01-01T12:00:00Z", listOf("pelicula","serie","anime")[it%3]) }
        assertEquals(100, tvLatestAdded(titles+titles).size)
        assertEquals(setOf("pelicula","serie","anime"), tvLatestAdded(titles).map { it.tipo }.toSet())
    }
    @Test fun unpublishedOrInvalidLinksDoNotAppearButValidEpisodesDo() {
        val date = "2026-01-01T12:00:00Z"
        val unavailable = title("none", date).copy(telegramUrl=null)
        val invalid = title("bad", date).copy(telegramUrl="https://example.com/not-telegram")
        val series = title("series", date, "serie").copy(telegramUrl=null, temporadas=listOf(Season(numero=1,
            episodios=listOf(Episode(telegramUrl="https://t.me/c/1/2")))))
        assertEquals(listOf(series), tvLatestAdded(listOf(unavailable,invalid,series)))
    }
}
