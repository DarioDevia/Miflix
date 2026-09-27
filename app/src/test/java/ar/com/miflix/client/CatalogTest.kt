package ar.com.miflix.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CatalogTest {
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
}
