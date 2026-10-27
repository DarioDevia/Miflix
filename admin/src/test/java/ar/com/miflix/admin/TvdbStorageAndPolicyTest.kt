package ar.com.miflix.admin

import com.google.gson.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class TvdbStorageAndPolicyTest {
    private fun obj(s:String)=JsonParser.parseString(s).asJsonObject
    private fun item()=obj("""{"id":"s","tipo":"serie","titulo":"Serie","fecha_agregado":"2020-01-01T00:00:00Z","temporadas":[{"numero":1,"episodios":[{"numero":1,"titulo":"Episodio 1","telegram_url":"https://t.me/demo/1","telegram_message_id":1,"playback_id":"private-reference","sinopsis":"","duracion":"42"}]}]}""")
    private fun catalog()=obj("""{"schema_version":1,"items":[]}""").apply {getAsJsonArray("items").add(item())}
    private fun plan():RecoveryPlan {
        val data=RecoveryData(RecoveryCandidate("TheTVDB",5,"Serie","2019","https://thetvdb.com/series/example","Español (spa)",JsonObject()),JsonObject(),listOf(obj("""{"numero":1,"episodios":[{"numero":1,"titulo":"Un nombre real","sinopsis":"Sinopsis española"}]}""")))
        return MetadataRecovery.compare(item(),data)
    }
    private class MemoryStore: StateStore {var data:JsonObject?=null;var fail=false;override fun read()=data?.deepCopy();override fun write(state: JsonObject) {if(fail) error("simulated write failure");data=state.deepCopy()}}
    private class Remote(var value:JsonObject):AdminRemote {var calls=0;override fun download()=value.deepCopy();override fun publish(catalog:JsonObject) {calls++;value=catalog.deepCopy()}}
    @Test fun protectedStorePersistsModifiesAndDeletesOnlyItsCredentials() {
        val app=RuntimeEnvironment.getApplication();val key=KeyGenerator.getInstance("AES").apply{init(256)}.generateKey()
        val store=SecureTvdbConfig(app) {key};store.clear();assertNull(store.load())
        File(app.noBackupFilesDir,"admin-config.enc").writeText("existing-configuration-marker")
        store.save(TvdbCredentials("synthetic-key","synthetic-pin"))
        val file=File(app.noBackupFilesDir,"thetvdb-config.enc");assertTrue(file.exists());assertFalse(file.readText().contains("synthetic"));assertFalse(file.readText().contains("token"))
        assertFalse(File(app.filesDir,"thetvdb-config.enc").exists());assertEquals("synthetic-key",store.load()!!.apiKey)
        store.save(TvdbCredentials("synthetic-replacement"));assertEquals("synthetic-replacement",store.load()!!.apiKey);assertEquals("",store.load()!!.pin)
        store.clear();assertNull(store.load());assertEquals("existing-configuration-marker",File(app.noBackupFilesDir,"admin-config.enc").readText())
    }
    @Test fun corruptProtectedStoreIsNotSilentlyTreatedAsUnconfigured() {
        val app=RuntimeEnvironment.getApplication();val key=KeyGenerator.getInstance("AES").generateKey();val store=SecureTvdbConfig(app){key}
        File(app.noBackupFilesDir,"thetvdb-config.enc").writeText("bad-ciphertext")
        val msg=assertThrows(IllegalStateException::class.java){store.load()}.message!!;assertTrue(msg.contains("descifrar"));assertFalse(msg.contains("bad-ciphertext"));store.clear()
    }
    @Test fun explicitLicenseAcknowledgmentRequired() {val p=plan();assertThrows(IllegalArgumentException::class.java){MetadataRecovery.apply(p,setOf("1/1/sinopsis"),setOf("1/1"),false)}}
    @Test fun individualSelectionPreservesAllProtectedFieldsAndDate() {
        val p=plan();val selected=setOf("1/1/sinopsis");val after=MetadataRecovery.apply(p,selected,setOf("1/1"),true)
        val expected=item().getAsJsonArray("temporadas")[0].asJsonObject.getAsJsonArray("episodios")[0].asJsonObject.deepCopy().apply{addProperty("sinopsis","Sinopsis española")}
        assertEquals(expected,after.array("temporadas")[0].asJsonObject.array("episodios")[0]);assertEquals(p.before.text("fecha_agregado"),after.text("fecha_agregado"))
        assertEquals("TheTVDB",after.getAsJsonObject("fuentes_metadata").getAsJsonObject("recuperacion_selectiva").getAsJsonObject("1/1/sinopsis").text("fuente"))
        assertEquals(p.before,MetadataRecovery.undo(after,MetadataRecovery.backup(p,selected,after)))
    }
    @Test fun queryCompareAndCancellationDoNotPersistAnything() {val store=MemoryStore();val repo=AdminRepository(store);val remote=Remote(catalog());repo.refresh(remote);val before=store.data!!.deepCopy();plan();assertEquals(before,store.data);assertEquals(0,remote.calls)}
    @Test fun failedStageDoesNotPersistMetadataOrBackup() {val store=MemoryStore();val repo=AdminRepository(store);repo.refresh(Remote(catalog()));val before=store.data!!.deepCopy();val p=plan();val s=setOf("1/1/sinopsis");val after=MetadataRecovery.apply(p,s,setOf("1/1"),true);store.fail=true;assertThrows(IllegalStateException::class.java){repo.stageRecovery(after,MetadataRecovery.backup(p,s,after))};assertEquals(before,store.data);assertEquals(before,repo.state)}
    @Test fun existingRecordSavesLocallyButCannotPublishAndUndoSurvivesRestart() {
        val store=MemoryStore();val repo=AdminRepository(store);val remote=Remote(catalog());repo.refresh(remote)
        val p=plan();val selected=setOf("1/1/sinopsis");val after=MetadataRecovery.apply(p,selected,setOf("1/1"),true)
        repo.stageRecovery(after,MetadataRecovery.backup(p,selected,after));repo.saveItem(after)
        assertEquals("2020-01-01T00:00:00Z",CatalogRules.items(repo.catalog!!).single().text("fecha_agregado"));assertTrue(repo.pending)
        val snap=store.data!!.deepCopy();assertThrows(IllegalStateException::class.java){repo.publish(remote)};assertEquals(snap,store.data);assertEquals(0,remote.calls)
        assertThrows(IllegalStateException::class.java){CatalogRules.publicCopy(repo.catalog!!)}
        val resumed=AdminRepository(store);resumed.refresh(remote);val saved=CatalogRules.items(resumed.catalog!!).single();assertTrue(resumed.canUndoRecovery(saved));val restored=resumed.undoRecovery(saved);resumed.saveItem(restored);resumed.publish(remote);assertEquals(1,remote.calls);assertFalse(resumed.pending)
        assertFalse(MetadataPublicationPolicy.containsTvdb(remote.value));assertEquals("https://t.me/demo/1",CatalogRules.items(remote.value).single().array("temporadas")[0].asJsonObject.array("episodios")[0].asJsonObject.text("telegram_url"))
    }
    @Test fun legacyTmdbAndTvmazeCatalogsRemainPublishable() {val c=catalog();c.add("source",obj("""{"fuente":"TVmaze","licencia":"https://creativecommons.org/licenses/by-sa/4.0/"}"""));MetadataPublicationPolicy.requirePublishable(c);assertEquals(c,CatalogRules.publicCopy(c))}
    @Test fun nestedBlockedMarkerCannotBeExported() {val c=catalog().apply{add("legacy",obj("""{"nested":{"publicacion_bloqueada":true}}"""))};assertThrows(IllegalStateException::class.java){CatalogRules.publicCopy(c)}}
}
