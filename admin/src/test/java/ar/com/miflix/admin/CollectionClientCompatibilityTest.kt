package ar.com.miflix.admin

import org.junit.Assert.*
import org.junit.Test

class CollectionClientCompatibilityTest {
    private val raw="""{"schema_version":1,"colecciones_version":1,"unknown":true,"colecciones":[{"id":"marvel","nombre":"Mundo Marvel","titulos":["p","s","a","absent"],"future":{"nested":[1]}}],"items":[{"id":"p","tipo":"pelicula","titulo":"Película","telegram_url":"https://t.me/demo/1","fecha_agregado":"2020-01-01T00:00:00Z"},{"id":"s","tipo":"serie","titulo":"Serie","temporadas":[]},{"id":"a","tipo":"anime","titulo":"Anime","temporadas":[]}]}"""
    @Test fun mobile0913ExactParserIgnoresAdditiveCollectionsWithoutBreakingTitles() {val catalog=ar.com.miflix.admin.compat.mobile.CatalogRepository.parseCatalog(raw);assertEquals(listOf("p","s","a"),catalog.items.map{it.id});assertEquals("Película",catalog.items.first().titulo)}
    @Test fun tv0113ExactParserIgnoresAdditiveCollectionsWithoutBreakingTitles() {val catalog=ar.com.miflix.admin.compat.tv.CatalogRepository.parseCatalog(raw);assertEquals(listOf("p","s","a"),catalog.items.map{it.id});assertEquals("Película",catalog.items.first().titulo)}
}
