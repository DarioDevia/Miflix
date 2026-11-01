package ar.com.miflix.admin

import com.google.gson.JsonObject
import org.junit.Test
import org.junit.Assert.*

class CollectionCatalogCompatibilityTest {
    private fun ready():Pair<AdminRepository,LegacyFixtures.Remote> {
        val remote=LegacyFixtures.Remote(LegacyFixtures.catalog(LegacyFixtures.item("p1","pelicula")))
        return AdminRepository(LegacyFixtures.Store()).apply {refresh(remote)} to remote
    }
    @Test fun collectionsPublishedBetweenMovieDownloadAndSaveDoNotBlockMoviePublication() {
        val (repo,server)=ready();val movie=CatalogRules.items(repo.catalog!!).single().deepCopy();val date=movie.get("fecha_agregado");val link=movie.get("telegram_url")
        server.root.add("colecciones",SyncFixtures.doc(SyncFixtures.row()).get("colecciones"))
        repo.saveItem(movie.apply {addProperty("titulo","Corrección legítima")})
        var sent:JsonObject?=null
        val remote=object:AdminRemote {override fun download()=server.download();override fun publish(catalog:JsonObject){sent=catalog.deepCopy();server.publish(catalog)}}
        repo.publish(remote);assertFalse(sent!!.has("colecciones"));assertFalse(sent!!.has("colecciones_version"));assertFalse(repo.pending)
        assertEquals(server.root,repo.state.get("base"));assertEquals(server.root.get("colecciones"),repo.catalog!!.get("colecciones"))
        val saved=CatalogRules.items(server.root).single();assertEquals(date,saved.get("fecha_agregado"));assertEquals(link,saved.get("telegram_url"))
        repo.saveItem(saved.deepCopy().apply {addProperty("sinopsis","Edición posterior")});repo.publish(remote);assertEquals(2,server.writes)
    }
    @Test fun refreshWhileMoviePendingKeepsLocalEditAndUpdatesOnlyCollectionMirror() {
        val (repo,server)=ready();repo.saveItem(CatalogRules.items(repo.catalog!!).single().deepCopy().apply {addProperty("titulo","Pendiente local")})
        server.root.add("colecciones",SyncFixtures.doc(SyncFixtures.row()).get("colecciones"));repo.refresh(server)
        assertTrue(repo.pending);assertEquals("Pendiente local",CatalogRules.items(repo.catalog!!).single().text("titulo"));assertEquals(server.root.get("colecciones"),repo.catalog!!.get("colecciones"));repo.publish(server);assertFalse(repo.pending)
    }
    @Test fun concurrentAudiovisualEditStillBlocksStalePublication() {
        val (repo,server)=ready();repo.saveItem(CatalogRules.items(repo.catalog!!).single().deepCopy().apply {addProperty("titulo","Local")})
        CatalogRules.items(server.root).single().addProperty("titulo","Cambio remoto")
        assertThrows(IllegalStateException::class.java){repo.publish(server)};assertTrue(repo.pending);assertEquals(0,server.writes)
    }
    @Test fun unrelatedUnknownRemoteFieldStillBlocksRatherThanLosesData() {
        val (repo,server)=ready();repo.saveItem(CatalogRules.items(repo.catalog!!).single().deepCopy());server.root.addProperty("future",42)
        assertThrows(IllegalStateException::class.java){repo.publish(server)};assertTrue(repo.pending);assertEquals(42,server.root.get("future").asInt)
    }
    @Test fun failedMoviePublicationKeepsCheckpointsDraftCollectionsAndPending() {
        val (repo,server)=ready();repo.persistDraft(LegacyFixtures.item("draft","anime"));repo.saveItem(CatalogRules.items(repo.catalog!!).single().deepCopy())
        val state=repo.state.deepCopy();server.fail=true;assertThrows(IllegalStateException::class.java){repo.publish(server)};assertEquals(state,repo.state);server.fail=false;repo.publish(server);assertFalse(repo.pending);assertEquals(state.get("draft"),repo.state.get("draft"))
    }
}
