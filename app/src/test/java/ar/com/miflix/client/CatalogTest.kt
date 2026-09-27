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
}
