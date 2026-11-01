package ar.com.miflix.client

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class CatalogCollectionsTest {
    private fun collection(id: String = "c", members: List<String> = listOf("b", "a"),
        order: Int = 0, visible: Boolean = true, name: String = "Saga $id"): JsonObject = JsonObject().apply {
        addProperty("id", id); addProperty("nombre", name); addProperty("origen", "manual")
        addProperty("orden", order); addProperty("visible", visible)
        add("titulos", JsonArray().apply { members.forEach { add(it) } })
    }
    private fun root(collections: JsonArray? = null, version: String = "1"): JsonObject = JsonObject().apply {
        addProperty("schema_version", version)
        add("items", JsonParser.parseString("""[{"id":"a","tipo":"pelicula","titulo":"A"},{"id":"b","tipo":"pelicula","titulo":"B"},{"id":"series","tipo":"serie","titulo":"Series"}]"""))
        if (collections != null) { addProperty("colecciones_version", 1); add("colecciones", collections) }
    }
    private fun parse(vararg entries: JsonObject, version: String = "1"): Catalog =
        CatalogRepository.parseCatalog(root(JsonArray().apply { entries.forEach { add(it) } }, version).toString())

    @Test fun oldCatalogAndEmptyArrayRemainCompatible() {
        assertTrue(CatalogRepository.parseCatalog(root().toString()).collections.isEmpty())
        assertTrue(parse().collections.isEmpty())
        assertEquals(3, parse().items.size)
    }
    @Test fun numericSchemaOneUsedByProductionContractIsAccepted() {
        val root = root(JsonArray().apply { add(collection()) }).apply { addProperty("schema_version", 1) }
        assertEquals("c", CatalogRepository.parseCatalog(root.toString()).collections.single().id)
    }
    @Test fun validMetadataAndOptionalFieldsAreRead() {
        val entry = collection().apply {
            addProperty("descripcion", "Una saga")
            addProperty("origen", "tmdb"); addProperty("tmdb_collection_id", 123)
            addProperty("poster_url", "https://images.example/poster.jpg")
            addProperty("backdrop_url", "https://images.example/backdrop.jpg")
        }
        val value = parse(entry).collections.single()
        assertEquals("Una saga", value.descripcion)
        assertEquals(123L, value.tmdbCollectionId)
        assertEquals("https://images.example/poster.jpg", value.posterUrl)
        assertEquals("https://images.example/backdrop.jpg", value.backdropUrl)
    }
    @Test fun missingOptionalFieldsAreSafeAndDoNotInventImages() {
        val value = parse(collection()).collections.single()
        assertNull(value.descripcion); assertNull(value.tmdbCollectionId)
        assertNull(value.posterUrl); assertNull(value.backdropUrl)
    }
    @Test fun malformedCollectionsCannotDiscardMoviesOrOtherCollections() {
        val entries = JsonArray().apply {
            add("invalid"); add(12); add(JsonObject()); add(collection("good"))
            add(collection("bad").apply { add("titulos", JsonObject()) })
        }
        val catalog = CatalogRepository.parseCatalog(root(entries).toString())
        assertEquals(listOf("good"), catalog.collections.map { it.id })
        assertEquals(3, catalog.items.size)
    }
    @Test fun invalidRequiredFieldsAreRejectedIndividually() {
        val invalid = listOf(
            collection().apply { remove("id") }, collection().apply { addProperty("id", " ") },
            collection().apply { addProperty("nombre", "") }, collection().apply { addProperty("origen", "unknown") },
            collection().apply { addProperty("visible", "true") }, collection().apply { remove("visible") },
            collection().apply { addProperty("orden", -1) }, collection().apply { addProperty("orden", 0.5) },
            collection().apply { addProperty("orden", "0") }, collection().apply { addProperty("orden", 1000001) },
            collection().apply { addProperty("origen", "tmdb") },
            collection().apply { addProperty("tmdb_collection_id", -1) },
            collection().apply { add("titulos", JsonArray().apply { add(123) }) }
        )
        invalid.forEach { assertTrue(parse(it).collections.isEmpty()) }
    }
    @Test fun hiddenCollectionsNeverReachPublicResolution() {
        val catalog = parse(collection("hidden", visible = false), collection("public"))
        assertEquals(listOf("public"), resolveCollections(catalog).map { it.id })
    }
    @Test fun membersResolveMiFlixIdsInExactAdminOrderAndKeepOriginalObjects() {
        val catalog = parse(collection(members = listOf("b", "a")))
        val resolved = resolveCollections(catalog).single()
        assertEquals(listOf("b", "a"), resolved.movies.map { it.id })
        assertSame(catalog.items[1], resolved.movies[0])
        assertSame(catalog.items[0], resolved.movies[1])
    }
    @Test fun missingDuplicateAndNonMovieMembersAreOmittedWithoutInventingRecords() {
        val catalog = parse(collection(members = listOf("missing", "b", "b", "series", "a", "a")))
        assertEquals(listOf("b", "a"), resolveCollections(catalog).single().movies.map { it.id })
    }
    @Test fun unresolvedAndEmptyCollectionsAreNotPublic() {
        assertTrue(resolveCollections(parse(collection(members = emptyList()))).isEmpty())
        assertTrue(resolveCollections(parse(collection(members = listOf("unknown", "series")))).isEmpty())
    }
    @Test fun duplicateCollectionIdsDoNotCreateDuplicateCards() {
        assertEquals(1, resolveCollections(parse(collection(), collection())).size)
    }
    @Test fun sectionOrderUsesAdminOrderThenNameAndStableId() {
        val entries = arrayOf(collection("z", order = 2), collection("b", order = 1, name = "Alpha"),
            collection("a", order = 1, name = "alpha"), collection("first", order = 0))
        val expected = listOf("first", "a", "b", "z")
        assertEquals(expected, resolveCollections(parse(*entries)).map { it.id })
        assertEquals(expected, resolveCollections(parse(*entries.reversedArray())).map { it.id })
    }
    @Test fun noArtificialTotalCollectionLimitOrFeaturedLimitOnSection() {
        val entries = (0..1099).map { collection("c$it", order = it) }.toTypedArray()
        assertEquals(1100, resolveCollections(parse(*entries)).size)
    }
    @Test fun unknownFieldsDoNotInterfereWithCollectionsOrMovies() {
        val r = root(JsonArray().apply { add(collection().apply { addProperty("future", "kept in raw cache") }) })
            .apply { add("future_root", JsonObject()) }
        val raw = r.toString()
        val catalog = CatalogRepository.parseCatalog(raw)
        assertEquals(3, catalog.items.size); assertEquals(1, catalog.collections.size)
        assertTrue(JsonParser.parseString(raw).asJsonObject.has("future_root"))
    }
    @Test fun unsupportedCollectionVersionAndMalformedEnvelopeFailClosedOnlyForCollections() {
        listOf("2", "\"1\"", "null", "{}").forEach { version ->
            val r = root(JsonArray().apply { add(collection()) }).apply {
                add("colecciones_version", JsonParser.parseString(version))
            }
            val c = CatalogRepository.parseCatalog(r.toString())
            assertTrue(c.collections.isEmpty()); assertEquals(3, c.items.size)
        }
        val r = root().apply { add("colecciones", JsonObject()) }
        assertTrue(CatalogRepository.parseCatalog(r.toString()).collections.isEmpty())
    }
    @Test fun collectionSupportIsCompatibleWithSchemaOneXAndTwo() {
        listOf("1", "1.2.0", "2").forEach { version ->
            assertEquals(listOf("b", "a"), resolveCollections(parse(collection(), version = version))
                .single().movies.map { it.id })
        }
    }
    @Test fun absentCollectionVersionIsCompatibleWhenArrayIsPresent() {
        val r = root(JsonArray().apply { add(collection()) }).apply { remove("colecciones_version") }
        assertEquals(1, CatalogRepository.parseCatalog(r.toString()).collections.size)
    }
    @Test fun movieSelectionCallsExistingCallbackWithSameObjectAndIgnoresUnknownIds() {
        val catalog = parse(collection())
        val collection = resolveCollections(catalog).single()
        var selected: Title? = null
        collection.selectMovie("a") { selected = it }
        assertSame(catalog.items[0], selected)
        selected = null
        collection.selectMovie("removed") { selected = it }
        assertNull(selected)
    }
    @Test fun catalogRefreshUpdatesMetadataAndMembersAndRemovesHiddenDeletedCollections() {
        val old = parse(collection())
        val updated = parse(collection(members = listOf("a"), name = "New name").apply {
            addProperty("poster_url", "https://images.example/new.jpg")
        })
        assertEquals("New name", resolveCollections(updated).single().collection.nombre)
        assertEquals(listOf("a"), resolveCollections(updated).single().movies.map { it.id })
        assertEquals("https://images.example/new.jpg", resolveCollections(updated).single().posterCandidates.first())
        assertTrue(resolveCollections(parse()).isEmpty())
        assertEquals(2, resolveCollections(old).single().movies.size)
    }
    @Test fun posterFallbackUsesMembersAndRejectsUnsafeUrls() {
        val c = parse(collection().apply { addProperty("poster_url", "http://not-https.example/a") })
        val movies = c.items.map { it.copy(posterUrl = "https://images.example/${it.id}.jpg") }
        val resolved = resolveCollections(c.copy(items = movies)).single()
        assertEquals("https://images.example/b.jpg", resolved.posterCandidates.first())
        assertEquals(2, resolved.posterCandidates.size)
        assertTrue(resolveCollections(c).single().posterCandidates.isEmpty())
        listOf(null, "", "http://example.test/a", "https://user:pass@example.test/a", "https://example.test/a b")
            .forEach { assertNull(collectionImageUrl(it)) }
    }
    @Test fun collectionNavigationOriginsAreIndependentOfMovieAndPlayerReturnState() {
        assertEquals(Screen.HOME, collectionOrigin(Screen.HOME))
        assertEquals(Screen.COLLECTIONS, collectionOrigin(Screen.COLLECTIONS))
        assertEquals("1 película", collectionMovieCount(1))
        assertEquals("5 películas", collectionMovieCount(5))
    }
}
