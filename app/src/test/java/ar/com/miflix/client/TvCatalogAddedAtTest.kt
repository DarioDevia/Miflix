package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

class TvCatalogAddedAtTest {
    private fun catalog(schema: String, date: String) = """{"schema_version":"$schema","items":[{"id":"movie","tipo":"pelicula","titulo":"Movie","fecha_agregado":$date}]}"""
    @Test fun dateIsReadForBothSupportedCatalogSchemasOnlyByTv() {
        for (schema in listOf("1", "2")) {
            val movie = CatalogRepository.parseCatalog(catalog(schema, "\"2026-10-01T12:00:00Z\"")).items.single()
            assertEquals(if (BuildConfig.IS_TV) "2026-10-01T12:00:00Z" else null, movie.addedAt)
        }
    }
    @Test fun invalidOptionalMetadataNeverBlocksCatalogLoading() {
        for (schema in listOf("1", "2")) for (date in listOf("{}", "[]", "null", "\"invalid\"", "42", "true")) {
            val movie = CatalogRepository.parseCatalog(catalog(schema, date)).items.single()
            assertEquals("movie", movie.id)
            assertNull(tvCatalogAddedInstant(movie.addedAt))
        }
    }
}
