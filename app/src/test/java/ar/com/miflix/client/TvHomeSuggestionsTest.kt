package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

class TvHomeSuggestionsTest {
    private data class Item(val id: String, val type: String = "pelicula", val year: Int? = null)
    private fun ordered(items: List<Item>, year: Int = 2026) =
        tvHomeSuggestions(items, year, { it.type }, { it.year }).map { it.id }
    @Test fun moviesAndSeriesUseTheirTitleYearAndStableCatalogTies() {
        assertEquals(listOf("series", "movie", "older", "oldest"), ordered(listOf(
            Item("oldest", year=2022), Item("series", "serie", 2026),
            Item("older", year=2025), Item("movie", year=2026))))
    }
    @Test fun noCurrentYearFallsBackToDescendingPastYears() {
        assertEquals(listOf("recent", "old"), ordered(listOf(Item("old", year=2020), Item("recent", year=2025))))
    }
    @Test fun invalidMissingAndFutureYearsCannotDisplaceReleasedTitles() {
        assertEquals(listOf("valid", "missing", "invalid", "future"), ordered(listOf(
            Item("missing"), Item("invalid", year=-1), Item("future", year=2027), Item("valid", year=2025))))
    }
    @Test fun wrongClockStillHasDeterministicCatalogFallback() {
        val input = listOf(Item("first", year=2025), Item("second", year=2026))
        assertEquals(listOf("second", "first"), ordered(input, 1970))
        assertEquals(listOf("second", "first"), ordered(input, 2099))
        assertEquals(listOf("first", "second"), input.map { it.id })
    }
    @Test fun emptyCatalogIsSafe() { assertTrue(ordered(emptyList()).isEmpty()) }
}
