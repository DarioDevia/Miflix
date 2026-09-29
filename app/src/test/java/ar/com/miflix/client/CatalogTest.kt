package ar.com.miflix.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CatalogTest {
    @Test fun automaticChecksAreLimitedToTenMinutes() {
        val now = 1_700_000_000_000L
        assertEquals(true, CatalogRepository.shouldAutoCheck(now, 0L))
        assertEquals(false, CatalogRepository.shouldAutoCheck(now, now - 9 * 60_000L))
        assertEquals(true, CatalogRepository.shouldAutoCheck(now, now - 10 * 60_000L))
        assertEquals(true, CatalogRepository.shouldAutoCheck(now, now + 60_000L))
    }

    @Test fun catalogUrlIsAvailableOnFreshInstall() {
        assertEquals("https://miflix-catalogo.deviadario.workers.dev/catalogo.json",
            CatalogRepository.DEFAULT_URL)
    }

    @Test fun existingCatalogContractParsesEpisodes() {
        val json = """{"schema_version":"1.2.0","items":[{"id":"a1","tipo":"anime","titulo":"Ejemplo","temporadas":[{"numero":1,"episodios":[{"id":"e1","numero":1,"telegram_url":"https://t.me/c/123/45"}]}]}]}"""
        val catalog = CatalogRepository.parseCatalog(json)
        assertEquals("e1", catalog.items.single().temporadas.single().episodios.single().id)
    }

    @Test fun htmlCannotReplaceCatalog() {
        assertThrows(Exception::class.java) { CatalogRepository.parseCatalog("<html>Worker error</html>") }
    }

    @Test fun duplicateIdsAreRejected() {
        val json = """{"schema_version":"1","items":[{"id":"a","tipo":"anime","titulo":"Uno"},{"id":"a","tipo":"anime","titulo":"Dos"}]}"""
        assertThrows(IllegalArgumentException::class.java) { CatalogRepository.parseCatalog(json) }
    }

    @Test fun workerVersionTwoMapsMultiplePublications() {
        val json = """{"schema_version":2,"generated_at":"2026-09-20T01:00:00Z","items":[{"id":"a","tipo":"anime","titulo":"Anime","telegram_publicaciones":[{"telegram_message_id":42,"telegram_url":"https://t.me/c/1692370597/42","texto_indice":"Temporada 1"},{"telegram_message_id":43,"telegram_url":"https://t.me/c/1692370597/43","texto_indice":"Temporada 2"}]}]}"""
        val catalog = CatalogRepository.parseCatalog(json)
        assertEquals("2", catalog.schemaVersion)
        assertEquals("https://t.me/c/1692370597/42", catalog.items.single().telegramUrl)
        assertEquals(2, catalog.items.single().temporadas.single().episodios.size)
        assertEquals("Temporada 2", catalog.items.single().temporadas.single().episodios[1].titulo)
    }

    @Test fun workerVersionTwoKeepsDirectLinksAndEmptyEntries() {
        val json = """{"schema_version":2,"items":[{"id":"a","tipo":"anime","titulo":"Disponible","telegram_url":"https://t.me/c/123/1"},{"id":"b","tipo":"anime","titulo":"Sin enlace","telegram_publicaciones":[]}]}"""
        val titles = CatalogRepository.parseCatalog(json).items
        assertEquals("https://t.me/c/123/1", titles.first().telegramUrl)
        assertEquals(null, titles.last().telegramUrl)
    }

    @Test fun adminMessageLinkWithTopicIsKeptForTdlibResolution() {
        val json = """{"schema_version":1,"items":[{"id":"dune","tipo":"pelicula","titulo":"Dune","telegram_url":"https://t.me/c/1973601153/506211/547791","temporadas":[]}]}"""
        val title = CatalogRepository.parseCatalog(json).items.single()
        assertEquals("https://t.me/c/1973601153/506211/547791", title.telegramUrl)
    }

    @Test fun optionalCreditsDoNotChangeOldCatalogs() {
        val old = """{"schema_version":"1.2.0","items":[{"id":"a","tipo":"pelicula","titulo":"Ejemplo","telegram_url":"https://t.me/c/1/2"}]}"""
        val title = CatalogRepository.parseCatalog(old).items.single()
        assertEquals(null, title.puntuacion)
        assertEquals(null, title.audio)
        assertEquals(null, title.director)
        assertEquals(emptyList<String>(), title.reparto.orEmpty())
        assertEquals("https://t.me/c/1/2", title.telegramUrl)
    }

    @Test fun audioIsReadFromCatalogForMoviesSeriesAndAnime() {
        for (version in listOf("1", "2")) {
            val json = """{"schema_version":"$version","items":[{"id":"movie","tipo":"pelicula","titulo":"Película","audio":"Español Latino","telegram_url":"https://t.me/c/1/1"},{"id":"series","tipo":"serie","titulo":"The Rain","audio":"Latino","telegram_url":"https://t.me/c/1/2","temporadas":[{"numero":1,"episodios":[{"numero":1,"telegram_url":"https://t.me/c/1/3"}]}]},{"id":"anime","tipo":"anime","titulo":"Anime","audio":"Dual","telegram_url":"https://t.me/c/1/4"}]}"""
            val items = CatalogRepository.parseCatalog(json).items
            assertEquals("Español Latino", items[0].audio)
            assertEquals("Latino", items[1].audio)
            assertEquals("Dual", items[2].audio)
            assertEquals("https://t.me/c/1/1", items[0].telegramUrl)
            if (version == "1") assertEquals("https://t.me/c/1/3",
                items[1].temporadas[0].episodios[0].telegramUrl)
            assertEquals("https://t.me/c/1/4", items[2].telegramUrl)
        }
    }

    @Test fun nullBlankAndInvalidAudioNeverInvalidateAnOldCatalog() {
        for (version in listOf("1.2.0", "2")) {
            for (value in listOf("null", "\"\"", "\"   \"", "{}", "42")) {
                val json = """{"schema_version":"$version","items":[{"id":"a","tipo":"pelicula","titulo":"Ejemplo","audio":$value,"telegram_url":"https://t.me/c/1/2"}]}"""
                val title = CatalogRepository.parseCatalog(json).items.single()
                assertEquals(null, title.audio)
                assertEquals("https://t.me/c/1/2", title.telegramUrl)
            }
        }
    }

    @Test fun optionalCreditsAreReadInBothCatalogVersions() {
        for (version in listOf("1.2.0", "2")) {
            val json = """{"schema_version":"$version","items":[{"id":"a","tipo":"pelicula","titulo":"Ejemplo","year":2025,"duracion":"1 h 35 min","puntuacion":7.4,"director":"Directora","reparto":["Actriz","Actor"],"telegram_url":"https://t.me/c/1/2"}]}"""
            val title = CatalogRepository.parseCatalog(json).items.single()
            assertEquals(2025, title.year)
            assertEquals("1 h 35 min", title.duracion)
            assertEquals(7.4, title.puntuacion!!, 0.0)
            assertEquals("Directora", title.director)
            assertEquals(listOf("Actriz", "Actor"), title.reparto)
        }
    }

    @Test fun ratingFormatsAndMissingValuesNeverBreakEitherCatalogVersion() {
        val cases = listOf(
            "6.9" to 6.9,
            "\"6.9\"" to 6.9,
            "\"6.9/10\"" to 6.9,
            null to null,
            "null" to null,
            "\"\"" to null,
            "\"desconocida\"" to null
        )
        for (version in listOf("1.2.0", "2")) {
            for ((ratingJson, expected) in cases) {
                val field = ratingJson?.let { ",\"puntuacion\":$it" }.orEmpty()
                val json = """{"schema_version":"$version","items":[{"id":"a","tipo":"pelicula","titulo":"Ejemplo","telegram_url":"https://t.me/c/1/2"$field}]}"""
                val title = CatalogRepository.parseCatalog(json).items.single()
                assertEquals("version=$version rating=$ratingJson", expected, title.puntuacion)
                assertEquals("https://t.me/c/1/2", title.telegramUrl)
            }
        }
    }

    @Test fun adminNineteenSeriesMapsSeasonsEpisodesAndCredits() {
        val json = """{"schema_version":1,"items":[{"id":"serie-imdb-tt6656238","tipo":"serie","titulo":"The Rain","anio":"2018","protagonistas":"Alba August, Lucas Lynggaard Tønnesen","direccion":"Directora","trailer_url":"https://example.test/trailer","puntuacion":"7.2/10","telegram_url":"https://t.me/c/1/99","temporadas":[{"numero":1,"nombre":"Temporada 1","cantidad_episodios":null,"fecha_emision":"2018-05-04","poster_url":"https://example.test/season.jpg","episodios":[{"tmdb_id":1458520,"numero":1,"temporada":1,"titulo":"No salgan","sinopsis":"Un virus mortal","duracion":46,"fecha_emision":"2018-05-04","imagen_url":"https://example.test/episode.jpg","telegram_url":"https://t.me/c/1/100"},{"tmdb_id":1482178,"numero":2,"titulo":"Quédense juntos","telegram_url":"https://t.me/c/1/101"}]},{"numero":2,"nombre":"Temporada 2","cantidad_episodios":6,"episodios":[]}]}]}"""
        val title = CatalogRepository.parseCatalog(json).items.single()
        assertEquals(2018, title.year)
        assertEquals(7.2, title.puntuacion!!, 0.0)
        assertEquals(listOf("Alba August", "Lucas Lynggaard Tønnesen"), title.reparto)
        assertEquals("Directora", title.director)
        assertEquals("https://example.test/trailer", title.trailerUrl)
        assertEquals("Temporada 1", title.temporadas[0].nombre)
        assertEquals("2018-05-04", title.temporadas[0].fechaEmision)
        assertEquals("https://example.test/season.jpg", title.temporadas[0].posterUrl)
        val episode = title.temporadas[0].episodios[0]
        assertEquals(null, episode.id)
        assertEquals(1458520L, episode.tmdbId)
        assertEquals(1, episode.temporada)
        assertEquals("No salgan", episode.titulo)
        assertEquals("Un virus mortal", episode.sinopsis)
        assertEquals(46, episode.duracion)
        assertEquals("2018-05-04", episode.fechaEmision)
        assertEquals("https://example.test/episode.jpg", episode.imagenUrl)
        assertEquals("https://t.me/c/1/100", episode.telegramUrl)
        assertEquals("https://t.me/c/1/99", title.telegramUrl)
        assertEquals(2, title.temporadas.size)
        assertEquals(6, title.temporadas[1].cantidadEpisodios)
        assertEquals(emptyList<Episode>(), title.temporadas[1].episodios)
    }

    @Test fun missingOrMalformedOptionalAdminMetadataDoesNotInvalidateCatalog() {
        val json = """{"schema_version":1,"items":[{"id":"series","tipo":"serie","titulo":"Serie","anio":"desconocido","protagonistas":null,"temporadas":[{"numero":1,"titulo":"Antigua","episodios":[{"id":"old","numero":1,"telegram_url":"https://t.me/c/1/2"},{"numero":2,"tmdb_id":"?","duracion":"?","imagen_url":null,"sinopsis":null,"fecha_emision":null,"telegram_url":"https://t.me/c/1/3"}]}]},{"id":"movie","tipo":"pelicula","titulo":"Película","year":2020,"director":"Director","reparto":["Actor"],"telegram_url":"https://t.me/c/1/4"}]}"""
        val titles = CatalogRepository.parseCatalog(json).items
        assertEquals(null, titles[0].year)
        assertEquals("Antigua", titles[0].temporadas[0].titulo)
        assertEquals("old", titles[0].temporadas[0].episodios[0].id)
        val optional = titles[0].temporadas[0].episodios[1]
        assertEquals(null, optional.tmdbId)
        assertEquals(null, optional.duracion)
        assertEquals(null, optional.imagenUrl)
        assertEquals(2020, titles[1].year)
        assertEquals("Director", titles[1].director)
        assertEquals(listOf("Actor"), titles[1].reparto)
    }
}
