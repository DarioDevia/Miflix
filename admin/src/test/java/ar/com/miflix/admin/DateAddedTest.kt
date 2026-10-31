package ar.com.miflix.admin

import com.google.gson.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

class DateAddedTest {
    private fun item(type: String = "pelicula", id: String = "$type-test") = JsonObject().apply {
        addProperty("id", id); addProperty("tipo", type); addProperty("titulo", "Prueba $type")
        add("temporadas", JsonArray())
    }
    private fun catalog(vararg entries: JsonObject) = JsonObject().apply {
        addProperty("schema_version", 1)
        add("items", JsonArray().apply { entries.forEach { add(it) } })
    }
    private fun saved(root: JsonObject) = CatalogRules.items(root).single()
    private fun assertFreshDate(type: String) {
        val root = catalog(); val candidate = item(type)
        val before = Instant.now()
        val result = saved(CatalogRules.save(root, candidate))
        val after = Instant.now(); val value = result.text("fecha_agregado")
        val instant = OffsetDateTime.parse(value).toInstant()
        assertTrue(value.endsWith("Z")); assertFalse(instant.isBefore(before)); assertFalse(instant.isAfter(after))
        assertFalse(candidate.has("fecha_agregado")); assertTrue(CatalogRules.items(root).isEmpty())
    }
    @Test fun newMovieGetsUtcDate() = assertFreshDate("pelicula")
    @Test fun newSeriesGetsUtcDate() = assertFreshDate("serie")
    @Test fun newAnimeGetsUtcDate() = assertFreshDate("anime")

    @Test fun generatedManualIdIsMatchedBeforeDecidingWhetherItIsNew() {
        val candidate = item().apply { remove("id") }
        val first = CatalogRules.save(catalog(), candidate)
        val second = CatalogRules.save(first, candidate)
        assertEquals(saved(first).get("fecha_agregado"), saved(second).get("fecha_agregado"))
        assertEquals(saved(first).text("id"), saved(second).text("id"))
    }
    @Test fun editingAnyCategoryPreservesExactOriginalDateEvenWithPartialOrChangedCandidate() {
        for (type in listOf("pelicula", "serie", "anime")) {
            val old = item(type).apply { addProperty("fecha_agregado", "2025-03-04T01:02:03.120-03:00") }
            for (replacement in listOf<String?>(null, "2099-01-01T00:00:00Z")) {
                val candidate = item(type).apply {
                    addProperty("titulo", "Editado"); addProperty("poster_url", "https://fixture.test/new.jpg")
                    replacement?.let { addProperty("fecha_agregado", it) }
                }
                val result = saved(CatalogRules.save(catalog(old), candidate))
                assertEquals(old.get("fecha_agregado"), result.get("fecha_agregado"))
            }
        }
    }
    @Test fun historicalRecordStaysUndatedEvenIfCandidateSuggestsDate() {
        for (type in listOf("pelicula", "serie", "anime")) {
            val old = item(type)
            val candidate = old.deepCopy().apply { addProperty("fecha_agregado", "2099-01-01T00:00:00Z") }
            assertFalse(saved(CatalogRules.save(catalog(old), candidate)).has("fecha_agregado"))
        }
    }
    @Test fun historicalNullAndEmptyDatesArePreservedWithoutNormalization() {
        for (date in listOf<JsonElement>(JsonNull.INSTANCE, JsonPrimitive(""))) {
            val old = item().apply { add("fecha_agregado", date) }
            assertEquals(date, saved(CatalogRules.save(catalog(old), item())).get("fecha_agregado"))
        }
    }
    @Test fun firstInsertionUsesSaveTimeRatherThanAnImportedOrDraftDate() {
        val candidate = item().apply { addProperty("fecha_agregado", "2000-01-01T00:00:00Z") }
        val before = Instant.now()
        val date = Instant.parse(saved(CatalogRules.save(catalog(), candidate)).text("fecha_agregado"))
        assertFalse(date.isBefore(before))
    }
    @Test fun seasonsAndEpisodesDoNotReceiveNewDateFields() {
        val candidate = item("serie").apply {
            add("temporadas", JsonParser.parseString("""[{"numero":1,"episodios":[{"numero":1,"titulo":"Episodio"}]}]"""))
        }
        val first = CatalogRules.save(catalog(), candidate)
        val edited = saved(first).deepCopy().apply {
            array("temporadas")[0].asJsonObject.array("episodios")[0].asJsonObject.addProperty("titulo", "Editado")
        }
        val result = saved(CatalogRules.save(first, edited))
        assertEquals(saved(first).get("fecha_agregado"), result.get("fecha_agregado"))
        val season = result.array("temporadas")[0].asJsonObject
        assertFalse(season.has("fecha_agregado")); assertFalse(season.array("episodios")[0].asJsonObject.has("fecha_agregado"))
    }
    @Test fun failedValidationDoesNotMutateCatalogOrDraft() {
        val root = catalog(); val candidate = item().apply { addProperty("tipo", "invalid") }
        val snapshot = root.deepCopy()
        assertThrows(IllegalArgumentException::class.java) { CatalogRules.save(root, candidate) }
        assertEquals(snapshot, root); assertFalse(candidate.has("fecha_agregado"))
    }
    @Test fun failedStoreWriteDoesNotPersistInsertionOrDate() {
        val store = object : StateStore {
            var state: JsonObject? = null; var fail = false
            override fun read() = state?.deepCopy()
            override fun write(state: JsonObject) { if (fail) error("simulated write failure"); this.state = state.deepCopy() }
        }
        val repo = AdminRepository(store); repo.refresh(AdminTest.Fake(catalog()))
        val snapshot = store.state!!.deepCopy(); store.fail = true
        val candidate = item()
        assertThrows(IllegalStateException::class.java) { repo.saveItem(candidate) }
        assertEquals(snapshot, store.state); assertTrue(CatalogRules.items(repo.catalog!!).isEmpty())
        assertFalse(repo.pending); assertFalse(candidate.has("fecha_agregado"))
        assertTrue(CatalogRules.items(AdminRepository(store).catalog!!).isEmpty())
    }
    @Test fun draftPersistenceAndCancellationNeverDateOrInsertContent() {
        val store = AdminTest.Memory(); val repo = AdminRepository(store)
        repo.refresh(AdminTest.Fake(catalog())); repo.persistDraft(item())
        assertFalse(repo.draft()!!.has("fecha_agregado")); assertTrue(CatalogRules.items(repo.catalog!!).isEmpty())
        repo.persistDraft(null)
        assertNull(AdminRepository(store).draft()); assertTrue(CatalogRules.items(AdminRepository(store).catalog!!).isEmpty())
    }
    @Test fun repeatedHttpPublicationAndRestartPreserveDateAndHistoricalAbsence() {
        val historical = item("anime", "old-anime")
        val store = AdminTest.Memory(); val repo = AdminRepository(store)
        repo.refresh(AdminTest.Fake(catalog(historical))); repo.saveItem(item())
        val date = CatalogRules.items(repo.catalog!!).first { it.text("tipo") == "pelicula" }.get("fecha_agregado").deepCopy()
        val server = MockWebServer(); server.start()
        try {
            val remote = HttpAdminRemote(AdminConfig(server.url("/").toString().trimEnd('/'), "fake-test-token"))
            var storedRemote=catalog(historical)
            repeat(2) {
                server.enqueue(MockResponse().setBody(storedRemote.toString()))
                server.enqueue(MockResponse().setBody("""{"ok":true}""")); repo.publish(remote)
                val check=server.takeRequest(5,TimeUnit.SECONDS)!!
                assertEquals("GET",check.method);assertEquals("/catalogo.json",check.path)
                val request = server.takeRequest(5, TimeUnit.SECONDS)!!
                assertEquals("PUT", request.method); assertEquals("/admin/catalogo", request.path)
                val json = CatalogRules.parse(request.body.readUtf8())
                storedRemote=json.deepCopy()
                val movie = CatalogRules.items(json).first { it.text("tipo") == "pelicula" }
                assertEquals(date, movie.get("fecha_agregado"))
                assertFalse(CatalogRules.items(json).first { it.text("id") == "old-anime" }.has("fecha_agregado"))
                if (it == 0) repo.saveItem(movie.deepCopy().apply { addProperty("sinopsis", "Cambio para otra sincronización") })
            }
            val restarted = AdminRepository(store)
            assertEquals(date, CatalogRules.items(restarted.catalog!!).first { it.text("tipo") == "pelicula" }.get("fecha_agregado"))
        } finally { server.shutdown() }
    }
}
