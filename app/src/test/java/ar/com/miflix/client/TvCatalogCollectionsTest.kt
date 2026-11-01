package ar.com.miflix.client

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class TvCatalogCollectionsTest {
    private val a = Title(id="a", tipo="pelicula", titulo="A", telegramUrl="https://t.me/c/1/1")
    private val b = a.copy(id="b", titulo="B")
    private fun collection(id: String="s", order: Int=0, members: String="[\"b\",\"a\"]") =
        JsonParser.parseString("""{"id":"$id","nombre":"Saga $id","origen":"manual","orden":$order,"visible":true,"titulos":$members}""").asJsonObject
    private fun root(vararg entries: JsonObject) = JsonObject().apply {
        addProperty("schema_version",1); addProperty("colecciones_version",1)
        add("items", JsonParser.parseString("""[{"id":"a","tipo":"pelicula","titulo":"A"}]"""))
        add("colecciones",JsonArray().apply { entries.forEach { add(it) } })
    }
    private fun resolve(vararg entries: JsonObject) = resolveTvCollections(Catalog(items=listOf(a,b), tvCollections=parseTvCatalogCollections(root(*entries))))
    @Test fun absentEmptyAndMalformedOptionalEnvelopeAreSafe() {
        for (value in listOf("{}","{\"colecciones\":[]}","{\"colecciones\":{}}","{\"colecciones\":null}"))
            assertTrue(parseTvCatalogCollections(JsonParser.parseString(value).asJsonObject).isEmpty())
    }
    @Test fun numericSchemaAndTvOnlyIntegration() {
        val catalog = CatalogRepository.parseCatalog(root(collection()).toString())
        assertEquals("1",catalog.schemaVersion)
        assertEquals(if(BuildConfig.IS_TV) 1 else 0,catalog.tvCollections.size)
        assertEquals("a",catalog.items.single().id)
    }
    @Test fun invalidOptionalCollectionNeverDiscardsTitles() {
        val r=root(collection()); r.getAsJsonArray("colecciones").add(JsonObject()); r.getAsJsonArray("colecciones").add(42)
        assertEquals(1,parseTvCatalogCollections(r).size)
        assertEquals(1,CatalogRepository.parseCatalog(r.toString()).items.size)
    }
    @Test fun invalidRequiredFieldsFailClosedIndividually() {
        for ((field,value) in listOf("id" to "\" \"","nombre" to "null","origen" to "\"other\"","orden" to "-1",
            "orden" to "0.5","visible" to "\"true\"","titulos" to "{}")) {
            val c=collection(); c.add(field,JsonParser.parseString(value)); assertTrue(resolve(c).isEmpty())
        }
    }
    @Test fun versionUnknownAndUnknownPropertiesDoNotBreakTitles() {
        val r=root(collection());r.addProperty("colecciones_version",2)
        assertTrue(parseTvCatalogCollections(r).isEmpty()); assertEquals(1,CatalogRepository.parseCatalog(r.toString()).items.size)
        r.remove("colecciones_version");r.add("future",JsonObject());assertEquals(1,parseTvCatalogCollections(r).size)
    }
    @Test fun membersUseExactMiFlixIdsAndAdminOrderWithoutCopies() {
        val c=resolve(collection()).single();assertEquals(listOf("b","a"),c.movies.map { it.id })
        assertSame(b,c.movies[0]);assertSame(a,c.movies[1])
    }
    @Test fun missingDuplicateSeriesAndUnplayableMembersAreOmitted() {
        val c=collection(members="[\"missing\",\"b\",\"b\",\"series\",\"a\"]")
        assertEquals(listOf("b","a"),resolve(c).single().movies.map { it.id })
        val catalog=Catalog(items=listOf(a.copy(telegramUrl=null)),tvCollections=parseTvCatalogCollections(root(c)))
        assertTrue(resolveTvCollections(catalog).isEmpty())
    }
    @Test fun hiddenEmptyAndDuplicateCollectionsAreNotCards() {
        val hidden=collection().apply { addProperty("visible",false) }
        assertTrue(resolve(hidden,collection(members="[]")).isEmpty())
        assertEquals(1,resolve(collection(),collection()).size)
    }
    @Test fun adminOrderHasStableNameAndIdTieBreaks() {
        val cs=listOf(collection("z",2),collection("b",1),collection("a",1),collection("first",0))
        assertEquals(listOf("first","a","b","z"),resolve(*cs.toTypedArray()).map { it.id })
        assertEquals(resolve(*cs.toTypedArray()),resolve(*cs.reversed().toTypedArray()))
    }
    @Test fun noSectionLimitAndCatalogUpdatesResolveCurrentObjects() {
        val entries=(0..1100).map { collection("s$it",it) }.toTypedArray()
        assertEquals(1101,resolve(*entries).size)
        assertTrue(resolve().isEmpty());assertEquals(listOf("a"),resolve(collection(members="[\"a\"]")).single().movies.map { it.id })
    }
    @Test fun imageFallbackAndUrlValidation() {
        val c=resolve(collection().apply { addProperty("poster_url","http://invalid.test/x") }).single()
        assertTrue(c.posterCandidates.isEmpty())
        val fallback=c.copy(movies=listOf(a.copy(posterUrl="https://artwork.test/p.jpg")))
        assertEquals("https://artwork.test/p.jpg",fallback.posterCandidates.first())
        for (url in listOf(null,"","http://x.test/a","https://user:pass@x.test/a","https://x.test/a b")) assertNull(tvCollectionImageUrl(url))
    }
    @Test fun tmdbMetadataNeverReplacesMiFlixIdentity() {
        val c=collection().apply { addProperty("origen","tmdb");addProperty("tmdb_collection_id",123);addProperty("descripcion","Saga") }
        val parsed=parseTvCatalogCollections(root(c)).single();assertEquals("s",parsed.id);assertEquals(123L,parsed.tmdbCollectionId)
        assertEquals(listOf("b","a"),resolve(c).single().movies.map { it.id })
        c.remove("tmdb_collection_id");assertTrue(resolve(c).isEmpty())
    }
    @Test fun selectionUsesExistingTitleCallbackAndUnknownIdIsIgnored() {
        val c=resolve(collection()).single();var selected:Title?=null
        c.selectMovie("a") { selected=it };assertSame(a,selected)
        selected=null;c.selectMovie("missing") { selected=it };assertNull(selected)
    }
}
