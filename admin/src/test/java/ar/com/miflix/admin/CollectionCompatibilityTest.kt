package ar.com.miflix.admin

import com.google.gson.*
import org.junit.Assert.*
import org.junit.Test

/** CatalogRules/AdminRepository/AdminStorage/TVDB policy are unchanged from 0.1.7.
 * Exercising those exact classes characterizes old Admin roundtrips, not an invented serializer. */
class CollectionCompatibilityTest {
    private fun obj(raw:String)=JsonParser.parseString(raw).asJsonObject
    @Test fun oldAdminParseSaveAndPublicCopyPreserveUnknownRootCollections() {
        val root=obj("""{"schema_version":1,"future_root":{"a":1},"colecciones":[{"id":"col-future","nombre":"Universo","titulos":["a","absent"],"future_collection":true}],"items":[{"id":"a","tipo":"pelicula","titulo":"Original","fecha_agregado":"2020-01-01T00:00:00Z","telegram_url":"https://t.me/demo/1","future_item":{"x":2}}]}""")
        val read=CatalogRules.parse(root.toString());val item=CatalogRules.items(read).single().deepCopy().apply {addProperty("titulo","Edited")}
        val published=CatalogRules.publicCopy(CatalogRules.save(read,item))
        assertEquals(root.get("colecciones"),published.get("colecciones"));assertEquals(root.get("future_root"),published.get("future_root"))
        val saved=CatalogRules.items(published).single();assertEquals("2020-01-01T00:00:00Z",saved.text("fecha_agregado"));assertEquals("https://t.me/demo/1",saved.text("telegram_url"));assertEquals(item.get("future_item"),saved.get("future_item"))
    }
    @Test fun oldCatalogWithoutCollectionsKeepsItsShapeAndNoAutomaticMigration() {val raw=obj("""{"schema_version":1,"items":[]}""");assertEquals(raw,CatalogRules.parse(raw.toString()));assertFalse(CatalogRules.publicCopy(raw).has("colecciones"))}
}
