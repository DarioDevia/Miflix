package ar.com.miflix.admin

import android.app.Application
import com.google.gson.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class RetiredStorageTest {
    private fun app()=RuntimeEnvironment.getApplication() as Application
    @Test fun privateArchiveSurvivesRestartAndIsImmutable() {val dir=File(app().noBackupFilesDir,"test-archives").apply {deleteRecursively()};val archives=RetiredMetadataArchives(dir);val raw=LegacyFixtures.state(LegacyFixtures.catalog(LegacyFixtures.marked(LegacyFixtures.item())));val id=archives.preserve(raw);assertEquals(raw.toString(),RetiredMetadataArchives(dir).read(id));assertEquals(id,archives.preserve(raw));assertEquals(1,dir.listFiles()!!.size)}
    @Test fun distinctWhitespaceInsideDataGetsDistinctBackups() {val dir=File(app().noBackupFilesDir,"whitespace-archives").apply {deleteRecursively()};val a=JsonObject().apply {addProperty("sinopsis","a  b")};val b=JsonObject().apply {addProperty("sinopsis","a b")};val store=RetiredMetadataArchives(dir);assertNotEquals(store.preserve(a),store.preserve(b));assertEquals(2,dir.listFiles()!!.size)}
    @Test fun corruptedExistingArchiveIsNotOverwritten() {val dir=File(app().noBackupFilesDir,"corrupt-archives").apply {deleteRecursively()};val store=RetiredMetadataArchives(dir);val raw=LegacyFixtures.state(LegacyFixtures.catalog());val id=store.preserve(raw);File(dir,"$id.json").writeText("damaged");assertThrows(IllegalStateException::class.java){store.preserve(raw)};assertEquals("damaged",File(dir,"$id.json").readText())}
    @Test fun readArchiveRejectsTraversal() {assertThrows(IllegalArgumentException::class.java){RetiredMetadataArchives(app().noBackupFilesDir).read("../../admin-config.enc")}}
    @Test fun androidMigrationBacksUpWithoutTouchingCollectionsCacheOrOtherConfiguration() {
        val app=app();val store=AndroidStateStore(app);val raw=LegacyFixtures.state(LegacyFixtures.catalog(LegacyFixtures.marked(LegacyFixtures.item())),LegacyFixtures.catalog(LegacyFixtures.item()))
        for(name in listOf("collections-state.json","collections-tmdb-cache.json")) File(app.filesDir,name).writeText("keep-$name")
        File(app.noBackupFilesDir,"admin-config.enc").writeText("encrypted-server-placeholder")
        store.write(raw);val repo=AdminRepository(store);assertFalse(RetiredMetadata.contains(repo.catalog));assertTrue(repo.cleanupBackups.isNotEmpty());assertEquals(raw.toString(),repo.privateCleanupBackup(repo.cleanupBackups.first()))
        for(name in listOf("collections-state.json","collections-tmdb-cache.json")) assertEquals("keep-$name",File(app.filesDir,name).readText())
        assertEquals("encrypted-server-placeholder",File(app.noBackupFilesDir,"admin-config.enc").readText())
    }
    @Test fun credentialCleanupOnlyDeletesExactRetiredFilesAndAlias() {
        val app=app();val dir=app.noBackupFilesDir
        for(name in listOf("thetvdb-config.enc","thetvdb-config.enc.bak","thetvdb-config.enc.new")) File(dir,name).writeText("synthetic-encrypted-old-key")
        File(dir,"admin-config.enc").writeText("keep-server-and-tmdb")
        File(dir,"other.enc").writeText("keep-other")
        var removed=0;RetiredCredentials.remove(app) {removed++}
        assertEquals(1,removed);assertFalse(File(dir,"thetvdb-config.enc").exists());assertFalse(File(dir,"thetvdb-config.enc.bak").exists());assertFalse(File(dir,"thetvdb-config.enc.new").exists());assertEquals("keep-server-and-tmdb",File(dir,"admin-config.enc").readText());assertEquals("keep-other",File(dir,"other.enc").readText())
    }
    @Test fun credentialCleanupDoesNotReadOrDecryptInvalidCiphertextAndCanRepeat() {val app=app();File(app.noBackupFilesDir,"thetvdb-config.enc").writeText("invalid old ciphertext");RetiredCredentials.remove(app) {};RetiredCredentials.remove(app) {};assertFalse(File(app.noBackupFilesDir,"thetvdb-config.enc").exists())}
    @Test fun failedKeyDeletionDoesNotDeleteOtherSecrets() {val app=app();File(app.noBackupFilesDir,"admin-config.enc").writeText("untouched");assertThrows(IllegalStateException::class.java){RetiredCredentials.remove(app){error("simulated locked keystore")}};assertEquals("untouched",File(app.noBackupFilesDir,"admin-config.enc").readText())}
    @Test fun collection018StateAndDraftRemainIdenticalAfterCatalogCleanup() {
        val app=app();val collections=CollectionRepository(CollectionFileStore(app));
        // Seed a valid 0.1.8 collection with the actual contract, all other store bytes stay independent.
        collections.restore(CollectionRules.empty().toString());val row=CollectionRules.fresh().apply {addProperty("nombre","Depredador");addProperty("tmdb_collection_id",10);addProperty("origen","tmdb");addProperty("poster_url","https://image.tmdb.org/t/p/w500/poster.jpg");add("titulos",JsonArray().apply {(1..5).forEach {add("movie-$it")}})}
        collections.save(row,null);collections.stage(row,row.text("id"));val file=File(app.filesDir,"collections-state.json");val before=file.readBytes()
        val state=AndroidStateStore(app);state.write(LegacyFixtures.state(LegacyFixtures.catalog(LegacyFixtures.marked(LegacyFixtures.item())),LegacyFixtures.catalog(LegacyFixtures.item())));AdminRepository(state)
        assertArrayEquals(before,file.readBytes());val reopened=CollectionRepository(CollectionFileStore(app));assertEquals(row,reopened.draft());assertEquals(row,CollectionRules.list(reopened.data()).single());assertEquals(5,CollectionRules.ids(row).size)
    }
}
