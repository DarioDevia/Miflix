package ar.com.miflix.admin

import android.app.Application
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class CollectionCloudFlowTest {
    private lateinit var app:Application
    private lateinit var scope:CoroutineScope
    private lateinit var c:CollectionController
    private var cfg:AdminConfig?=AdminConfig("https://worker.invalid","synthetic-admin")
    private val catalog=LegacyFixtures.catalog(LegacyFixtures.item("p1","pelicula"))
    private class Remote:CollectionsRemote {
        var root=SyncFixtures.doc();var reads=0;var writes=0;var failRead=false;var failPut=false;var delay=0L
        override fun download():JsonObject {reads++;if(delay>0) Thread.sleep(delay);check(!failRead) {"synthetic offline"};return root.deepCopy()}
        override fun publish(document:JsonObject):String {check(!failPut) {"synthetic HTTP 503"};writes++;root=document.deepCopy();return "2026-10-31T20:00:00Z"}
    }
    private lateinit var remote:Remote
    private fun controller()=CollectionController(app,scope,{cfg},{catalog}).also {it.syncRemoteFactory={remote}}
    private fun file()=File(app.filesDir,"collections-state.json")
    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher());app=RuntimeEnvironment.getApplication()
        listOf("collections-state.json","collections-state.json.bak","collections-state.json.new").forEach {File(app.filesDir,it).delete()}
        scope=CoroutineScope(SupervisorJob()+Dispatchers.Main);remote=Remote();c=controller()
    }
    @After fun finish() {c.close();scope.cancel();Dispatchers.resetMain()}
    private fun waitDone() {val end=System.currentTimeMillis()+5000;while(c.busy && System.currentTimeMillis()<end) Thread.sleep(10);assertFalse(c.busy)}
    private fun local() {c.begin();c.field("nombre","Depredador");c.add(setOf("p1"));c.preview();c.save()}
    private fun read() {c.pageSyncInfo();c.readCloudCollections();waitDone()}
    @Test fun merelyOpeningPageNeverConnects() {c.pageSyncInfo();assertEquals(0,remote.reads);assertEquals(0,remote.writes);assertFalse(file().exists())}
    @Test fun queryDoesNotWriteCollectionFileCatalogOrDraft() {
        local();c.begin();c.field("nombre","Borrador intacto");val raw=file().readText();val old=catalog.deepCopy();remote.root=SyncFixtures.doc(SyncFixtures.row());read()
        assertEquals(raw,file().readText());assertEquals(old,catalog);assertEquals("Borrador intacto",c.draft!!.text("nombre"));assertEquals(0,remote.writes)
    }
    @Test fun publishRequiresSummaryAndPreservesLocalDraftAndSavedData() {
        local();c.begin();c.field("nombre","Borrador");val raw=file().readText();read();c.confirmCloudPublish(true,true);assertEquals(0,remote.writes)
        c.requestCloudPublish();c.confirmCloudPublish(false,false);waitDone()
        assertEquals(1,remote.writes);assertEquals(2,remote.reads);assertEquals(c.data,remote.root);assertEquals(raw,file().readText());assertFalse(c.cloudPublishing);assertTrue(c.cloudAck.isNotBlank())
    }
    @Test fun cancelSummaryNeverPublishes() {local();read();c.requestCloudPublish();c.cancelCloudConfirmation();assertEquals(0,remote.writes);assertFalse(c.cloudConfirmation)}
    @Test fun remoteDeletionRequiresDestructiveConsent() {
        local();remote.root=SyncFixtures.doc(SyncFixtures.row());read();c.requestCloudPublish();c.confirmCloudPublish(false,false);assertEquals(0,remote.writes)
        c.confirmCloudPublish(true,false);waitDone();assertEquals(1,remote.writes)
    }
    @Test fun emptySetNeedsBothApplicableConfirmations() {
        remote.root=SyncFixtures.doc(SyncFixtures.row());read();c.requestCloudPublish();c.confirmCloudPublish(true,false);assertEquals(0,remote.writes)
        c.confirmCloudPublish(true,true);waitDone();assertEquals(1,remote.writes);assertTrue(CollectionRules.list(remote.root).isEmpty())
    }
    @Test fun visibleRemoteChangeDuringReviewPreventsPut() {
        local();read();remote.root=SyncFixtures.doc(SyncFixtures.row());c.requestCloudPublish();c.confirmCloudPublish(true,true);waitDone()
        assertEquals(0,remote.writes);assertTrue(c.message.contains("remotas cambiaron"));assertEquals(1,CollectionRules.list(c.data).size)
    }
    @Test fun changedLocalAfterReviewMustBeReviewedAgain() {local();read();c.begin();c.field("nombre","Otra");c.preview();c.save();c.requestCloudPublish();assertFalse(c.cloudConfirmation);assertEquals(0,remote.writes)}
    @Test fun configurationChangeAfterReviewPreventsUseOfOldServer() {local();read();cfg=cfg!!.copy(baseUrl="https://other.invalid");c.requestCloudPublish();assertFalse(c.cloudConfirmation);assertEquals(0,remote.writes)}
    @Test fun missingConfigurationIsAnActionableLocalError() {cfg=null;c.pageSyncInfo();c.readCloudCollections();assertEquals(0,remote.reads);assertTrue(c.message.contains("Configurá"));assertFalse(c.busy)}
    @Test fun failedGetAndPutRetainExactLocalStateAndCanRetry() {
        local();val raw=file().readText();remote.failRead=true;read();assertNull(c.cloudReview);assertEquals(raw,file().readText())
        remote.failRead=false;read();remote.failPut=true;c.requestCloudPublish();c.confirmCloudPublish(false,false);waitDone()
        assertEquals(raw,file().readText());assertTrue(c.cloudAck.isBlank());assertFalse(c.cloudPublishing)
        remote.failPut=false;read();c.requestCloudPublish();c.confirmCloudPublish(false,false);waitDone();assertEquals(1,remote.writes);assertEquals(raw,file().readText())
    }
    @Test fun recoveryRequiresSelectionAndConfirmationThenKeepsBackup() {
        local();val before=c.data.deepCopy();remote.root=SyncFixtures.doc(SyncFixtures.row());read();c.requestCloudRecovery();assertNull(c.cloudRecovery)
        c.selectedRemote=setOf("predator");c.requestCloudRecovery();assertEquals(before,c.data);assertNotNull(c.cloudRecovery);c.confirmCloudRecovery()
        assertEquals(2,CollectionRules.list(c.data).size);assertEquals(0,remote.writes);assertTrue(c.hasBackup);c.undo();assertEquals(before,c.data)
    }
    @Test fun recoveryDoesNotOverwriteExistingDraft() {
        local();c.begin();c.field("nombre","Edición pendiente");val raw=file().readText();remote.root=SyncFixtures.doc(SyncFixtures.row());read();c.selectedRemote=setOf("predator");c.requestCloudRecovery()
        assertNull(c.cloudRecovery);assertEquals(raw,file().readText());assertEquals("Edición pendiente",c.draft!!.text("nombre"))
    }
    @Test fun canceledRecoveryLeavesEverythingUntouched() {local();val raw=file().readText();remote.root=SyncFixtures.doc(SyncFixtures.row());read();c.selectedRemote=setOf("predator");c.requestCloudRecovery();c.cancelCloudConfirmation();assertEquals(raw,file().readText());assertEquals(0,remote.writes)}
    @Test fun lateCanceledGetCannotApplyRemoteResults() {local();val raw=file().readText();remote.delay=200;c.pageSyncInfo();c.readCloudCollections();c.cancelRequest();Thread.sleep(300);assertNull(c.cloudReview);assertEquals(raw,file().readText())}
    @Test fun collectionsSurviveRestartAfterPublicationFailure() {local();read();remote.failPut=true;c.requestCloudPublish();c.confirmCloudPublish(false,false);waitDone();val before=c.data;c.close();c=controller();assertEquals(before,c.data)}
    @Test fun failedRecoveryDiskWritePreservesInMemoryData() {
        val store=object:StateStore {var state:JsonObject?=null;var fail=false;override fun read()=state;override fun write(state:JsonObject){check(!fail);this.state=state.deepCopy()}}
        val repo=CollectionRepository(store);repo.replaceApproved(SyncFixtures.doc(SyncFixtures.row()));val before=repo.data();store.fail=true
        assertThrows(IllegalStateException::class.java){repo.replaceApproved(SyncFixtures.doc(SyncFixtures.row("new")))};assertEquals(before,repo.data());assertEquals(before,CollectionRepository(store).data())
    }
}
