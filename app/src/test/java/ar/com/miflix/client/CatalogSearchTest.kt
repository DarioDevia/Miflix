package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

class CatalogSearchTest {
    private val titles = listOf(
        Title(id = "a", tipo = "pelicula", titulo = "Señor de los Anillos", year = 2020,
            generos = listOf("Fantasía", "Acción"), puntuacion = 8.2),
        Title(id = "b", tipo = "pelicula", titulo = "Águila", year = 2026,
            generos = listOf("Terror", " ACCION "), puntuacion = 7.4),
        Title(id = "c", tipo = "serie", titulo = "Zorro", year = 2025,
            generos = listOf("terror"), puntuacion = 9.1),
        Title(id = "d", tipo = "anime", titulo = "Búsqueda", year = 2010,
            generos = listOf("Aventuras"), puntuacion = 6.0),
        Title(id = "e", tipo = "pelicula", titulo = "Sin datos")
    )
    private val index = CatalogSearchIndex(titles)
    private fun ids(query: String = "", filters: SearchFilters = SearchFilters(),
        order: SearchOrder = SearchOrder.AZ) = index.search(query, filters, order).map { it.id }

    @Test fun searchIgnoresAccentsAndMatchesEnye() {
        assertEquals(listOf("a"), ids("senor"))
        assertEquals(listOf("b"), ids("aguila"))
    }
    @Test fun searchIgnoresCase() { assertEquals(listOf("b", "a"), ids("ACCION")) }
    @Test fun searchByTitle() { assertEquals(listOf("a"), ids("Anillos")) }
    @Test fun searchByGenre() { assertEquals(listOf("a"), ids("fantasia")) }
    @Test fun decomposedUnicodeAndTrimmedQueryMatch() {
        assertEquals(listOf("b"), ids("  A\u0301GUILA  "))
    }
    @Test fun typeFilterWorksWithoutQuery() {
        assertEquals(listOf("b", "a", "e"), ids(filters = SearchFilters(type = "pelicula")))
        assertEquals(listOf("c"), ids(filters = SearchFilters(type = "serie")))
        assertEquals(listOf("d"), ids(filters = SearchFilters(type = "anime")))
    }
    @Test fun genreFilterIgnoresCaseAndAccents() {
        assertEquals(listOf("b", "a"), ids(filters = SearchFilters(genre = "ACCIÓN")))
    }
    @Test fun yearFilter() {
        assertEquals(listOf("c"), ids(filters = SearchFilters(year = 2025)))
    }
    @Test fun minimumRatingIsInclusiveAndExcludesMissingRating() {
        assertEquals(listOf("b", "d", "a", "c"), ids(filters = SearchFilters(minimumRating = 6)))
        assertEquals(listOf("a", "c"), ids(filters = SearchFilters(minimumRating = 8)))
    }
    @Test fun queryAndAllFiltersCombine() {
        assertEquals(listOf("b"), ids("AGUILA", SearchFilters(type = "pelicula", genre = "Terror",
            year = 2026, minimumRating = 7, latestReleases = true)))
        assertTrue(ids("AGUILA", SearchFilters(type = "serie", genre = "Terror")).isEmpty())
    }
    @Test fun alphabeticalAscending() {
        assertEquals(listOf("b", "d", "a", "e", "c"), ids(order = SearchOrder.AZ))
    }
    @Test fun alphabeticalDescending() {
        assertEquals(listOf("c", "e", "a", "d", "b"), ids(order = SearchOrder.ZA))
    }
    @Test fun newestSortIncludesOldTitlesAndPutsMissingYearLast() {
        assertEquals(listOf("b", "c", "a", "d", "e"), ids(order = SearchOrder.NEWEST))
    }
    @Test fun oldestSortPutsMissingYearLast() {
        assertEquals(listOf("d", "a", "c", "b", "e"), ids(order = SearchOrder.OLDEST))
    }
    @Test fun highestRatingSortPutsMissingRatingLast() {
        assertEquals(listOf("c", "a", "b", "d", "e"), ids(order = SearchOrder.TOP_RATED))
    }
    @Test fun latestReleasesRestrictsInsteadOfJustSorting() {
        assertEquals(listOf("b", "c"), ids(filters = SearchFilters(latestReleases = true)))
        assertEquals(listOf("c"), ids(filters = SearchFilters(latestReleases = true,
            type = "serie", genre = "terror", minimumRating = 9)))
    }
    @Test fun latestReleasesWindowMovesWithCatalogMaximum() {
        val newer = Title(id = "f", titulo = "Futuro", year = 2027)
        val updated = CatalogSearchIndex(titles + newer)
        assertEquals(listOf("b", "f"), updated.search("", SearchFilters(latestReleases = true)).map { it.id })
    }
    @Test fun latestWindowUsesPreviousCalendarYearNotPreviousAvailableYear() {
        val sparse = CatalogSearchIndex(listOf(titles[0], titles[1]))
        assertEquals(listOf("b"), sparse.search("", SearchFilters(latestReleases = true)).map { it.id })
    }
    @Test fun genreOptionsDeduplicateAndSortNormalizedNames() {
        assertEquals(listOf("accion", "aventuras", "fantasia", "terror"), index.genres.map { it.key })
        assertEquals("Acción", index.genres.first().label)
        assertEquals(listOf(2026, 2025, 2020, 2010), index.years)
    }
    @Test fun catalogWithoutYearsHasNoReleaseWindow() {
        val noYears = CatalogSearchIndex(titles.map { it.copy(year = null) })
        assertEquals(5, noYears.search("").size)
        assertNull(noYears.latestYear)
        assertTrue(noYears.search("", SearchFilters(latestReleases = true)).isEmpty())
    }
    @Test fun catalogWithoutRatingsStillSearchesButDoesNotPassRatingFilter() {
        val noRatings = CatalogSearchIndex(titles.map { it.copy(puntuacion = null) })
        assertEquals(5, noRatings.search("", order = SearchOrder.TOP_RATED).size)
        assertTrue(noRatings.search("", SearchFilters(minimumRating = 6)).isEmpty())
    }
    @Test fun catalogWithoutGenresStillSearchesByTitle() {
        val noGenres = CatalogSearchIndex(titles.map { it.copy(generos = emptyList()) })
        assertTrue(noGenres.genres.isEmpty())
        assertEquals(listOf("a"), noGenres.search("senor").map { it.id })
    }
    @Test fun emptyAndInvalidMetadataAreSafe() {
        val empty = CatalogSearchIndex(emptyList())
        assertTrue(empty.search("").isEmpty())
        val invalid = CatalogSearchIndex(listOf(Title(id = "bad", year = 0,
            puntuacion = Double.NaN, generos = listOf("", "  "))))
        assertTrue(invalid.years.isEmpty())
        assertTrue(invalid.genres.isEmpty())
        assertEquals(1, invalid.search("").size)
        assertTrue(invalid.search("", SearchFilters(minimumRating = 6)).isEmpty())
    }
    @Test fun indexHandlesThousandTitlesWithoutIndexingEpisodes() {
        val many = (1..1000).map { number -> Title(id = "$number", tipo = "serie",
            titulo = "Título $number", year = 2025, generos = listOf("Acción"),
            temporadas = listOf(Season(numero = 1, episodios = listOf(Episode(titulo = "Otro nombre"))))) }
        val large = CatalogSearchIndex(many)
        assertEquals(1000, large.search("accion", SearchFilters(type = "serie", year = 2025)).size)
        assertTrue(large.search("Otro nombre").isEmpty())
    }
    @Test fun legacySchemasKeepSearchableMetadata() {
        for (version in listOf("\"1.2.0\"", "2")) {
            val catalog = CatalogRepository.parseCatalog("""{"schema_version":$version,"items":[
                {"id":"legacy","tipo":"pelicula","titulo":"Señor","generos":["Acción"]}]}""")
            assertEquals(listOf("legacy"), CatalogSearchIndex(catalog.items).search("senor").map { it.id })
        }
    }
}
