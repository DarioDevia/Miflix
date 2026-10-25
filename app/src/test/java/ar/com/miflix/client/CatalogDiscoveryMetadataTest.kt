package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

class CatalogDiscoveryMetadataTest {
    private fun parse(version: String, field: String = ""): Title = CatalogRepository.parseCatalog(
        """{"schema_version":"$version","items":[{"id":"movie","tipo":"pelicula","titulo":"Movie","year":1987$field}]}""")
        .items.single()

    @Test fun existingAddedFieldIsReadInBothSupportedSchemasWithoutChangingItsDate() {
        listOf("1", "1.2.0", "2").forEach { schema ->
            val title = parse(schema, ",\"fecha_agregado\":\"2026-10-25T12:00:00-03:00\"")
            assertEquals("2026-10-25T12:00:00-03:00", title.addedAt)
            assertEquals(1987, title.year)
        }
    }
    @Test fun missingOrNullAddedDateIsCompatibleWithOldCatalogs() {
        listOf("1", "2").forEach { schema ->
            assertNull(parse(schema).addedAt)
            assertNull(parse(schema, ""","fecha_agregado":null""").addedAt)
        }
    }
    @Test fun nonStringAddedDatesCannotBreakTheCatalog() {
        listOf("1", "2").forEach { schema ->
            listOf("true", "12345", "{}", "[]").forEach { value ->
                assertNull(parse(schema, ",\"fecha_agregado\":$value").addedAt)
            }
        }
    }
    @Test fun malformedDateTextIsPreservedButIgnoredByDiscovery() {
        listOf("1", "2").forEach { schema ->
            val title = parse(schema, ",\"fecha_agregado\":\"unknown\"")
            assertEquals("unknown", title.addedAt)
            assertNull(catalogAddedInstant(title.addedAt))
        }
    }
    @Test fun catalogUpdateDateIsNotUsedAsAnIndividualAddedDate() {
        val catalog = CatalogRepository.parseCatalog("""{"schema_version":"1","actualizado_en":"2026-10-25T12:00:00Z","items":[{"id":"a","tipo":"pelicula","titulo":"A"}]}""")
        assertNotNull(catalog.updatedAt)
        assertNull(catalog.items.single().addedAt)
        assertTrue(buildHomeDiscoveryRows(catalog.items).latestAdded.isEmpty())
    }
}
