package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

class HomeDiscoveryTest {
    private fun title(id: String, year: Int? = null, added: String? = null, type: String = "pelicula") =
        Title(id = id, titulo = "Título $id", tipo = type, year = year, addedAt = added,
            telegramUrl = "https://t.me/c/1/1")
    private fun ids(titles: List<Title>) = titles.map { it.id }

    @Test fun emptyAndSingleCatalogAreSafe() {
        assertEquals(HomeDiscoveryRows(emptyList(), emptyList()), buildHomeDiscoveryRows(emptyList()))
        val only = title("one", 1987)
        assertEquals(listOf(only), buildHomeDiscoveryRows(listOf(only)).latestReleases)
        assertTrue(buildHomeDiscoveryRows(listOf(only)).latestAdded.isEmpty())
    }

    @Test fun releasesReuseSearchWindowAndSortDescendingByKnownYear() {
        val titles = listOf(title("old", 1987), title("b", 2026), title("a", 2026), title("previous", 2025))
        val row = buildHomeDiscoveryRows(titles).latestReleases
        assertEquals(listOf("a", "b", "previous"), ids(row))
        assertEquals(CatalogSearchIndex(titles).search("", SearchFilters(latestReleases = true),
            SearchOrder.NEWEST), row)
    }

    @Test fun releasesDoNotSubstituteAddedDatesOrInventDatesForMissingYears() {
        val oldAddedToday = title("old", 1987, "2026-10-25T12:00:00Z")
        val noYear = title("unknown", added = "2026-10-25T13:00:00Z")
        val recent = title("recent", 2026)
        val rows = buildHomeDiscoveryRows(listOf(oldAddedToday, noYear, recent))
        assertEquals(listOf(recent), rows.latestReleases)
        assertEquals(listOf(noYear, oldAddedToday), rows.latestAdded)
        assertNull(noYear.year)
    }

    @Test fun releasesUsePreviousCalendarYearNotPreviousAvailableYear() {
        assertEquals(listOf("new"), ids(buildHomeDiscoveryRows(
            listOf(title("new", 2026), title("old", 2020))).latestReleases))
    }

    @Test fun invalidOrMissingYearsCannotProduceAReleaseRow() {
        assertTrue(buildHomeDiscoveryRows(listOf(title("a"), title("b", -1), title("c", 0)))
            .latestReleases.isEmpty())
    }

    @Test fun moviesSeriesAndAnimeRequireExistingPlayableContent() {
        val series = title("series", 2026, type = "serie").copy(telegramUrl = null,
            temporadas = listOf(Season(numero = 1, episodios = listOf(
                Episode(numero = 1, telegramUrl = "https://t.me/c/1/2")))))
        val movie = title("movie", 2026)
        val anime = title("anime", 2025, type = "anime")
        val unavailable = listOf(title("no-link", 2030).copy(telegramUrl = null),
            title("bad-link", 2030).copy(telegramUrl = "https://example.test/unpublished"),
            title("series-without-episodes", 2030, type = "serie"),
            title("wrong-type", 2030, type = "music"), title("", 2030), title("blank", 2030).copy(titulo = ""))
        val row = buildHomeDiscoveryRows(unavailable + listOf(series, movie, anime)).latestReleases
        assertEquals(setOf("series", "movie", "anime"), ids(row).toSet())
        assertSame(series, row.first { it.id == "series" })
    }

    @Test fun datesUseRealInstantsAndOffsetsInsteadOfLexicographicalLocalTime() {
        val a = title("later", 1987, "2026-10-25T10:00:00-03:00")
        val b = title("earlier", 2026, "2026-10-25T12:30:00Z")
        assertEquals(listOf(a, b), buildHomeDiscoveryRows(listOf(b, a)).latestAdded)
    }

    @Test fun missingEmptyInvalidAndUnzonedAddedDatesAreExcludedWithoutMutation() {
        val invalid = listOf<String?>(null, "", " ", "not-a-date", "2026", "2026-10-25",
            "2026-10-25T12:00:00", "2026-02-30T12:00:00Z")
        val titles = invalid.mapIndexed { i, date -> title("$i", 2026, date) }
        val before = titles.map { it.addedAt }
        assertTrue(buildHomeDiscoveryRows(titles).latestAdded.isEmpty())
        assertEquals(before, titles.map { it.addedAt })
    }

    @Test fun validAddedDatesArePreservedAndRemainIndependentOfReleaseYear() {
        val newerAddition = title("1987", 1987, "2026-10-25T12:00:00.123Z")
        val olderAddition = title("2026", 2026, "2026-10-24T12:00:00Z")
        val original = listOf(olderAddition, newerAddition)
        val row = buildHomeDiscoveryRows(original).latestAdded
        assertEquals(listOf(newerAddition, olderAddition), row)
        assertEquals("2026-10-25T12:00:00.123Z", newerAddition.addedAt)
        assertSame(newerAddition, row.first())
    }

    @Test fun duplicatesDoNotEnterEitherRowAndOrderingIsStable() {
        val a = title("a", 2026, "2026-10-25T12:00:00Z")
        val b = title("b", 2026, a.addedAt)
        val original = listOf(b, a, b, a)
        val rows = buildHomeDiscoveryRows(original)
        assertEquals(listOf("a", "b"), ids(rows.latestReleases))
        assertEquals(listOf("a", "b"), ids(rows.latestAdded))
        assertEquals(rows, buildHomeDiscoveryRows(original.reversed()))
    }

    @Test fun unavailableTitlesWithDatesDoNotEnterAddedRow() {
        val title = title("unavailable", 2026, "2026-10-25T12:00:00Z").copy(telegramUrl = null)
        assertTrue(buildHomeDiscoveryRows(listOf(title)).latestAdded.isEmpty())
    }

    @Test fun discoveryDoesNotChangeGeneralRowsOrCatalogObjects() {
        val catalog = listOf(title("movie", 2026), title("anime", 2026, type = "anime"),
            title("series", 2026, type = "serie"))
        val before = buildHomeRows(catalog)
        buildHomeDiscoveryRows(catalog)
        assertEquals(before, buildHomeRows(catalog))
        assertEquals(listOf("Películas", "Series", "Anime"), before.general.map { it.label })
    }
}
