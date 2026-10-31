package ar.com.miflix.admin

import android.app.Application
import com.google.gson.*
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
class SagaControllerTest {
    private lateinit var app:Application;private lateinit var scope:CoroutineScope;private lateinit var c:CollectionController
    private fun obj(s:String)=JsonParser.parseString(s).asJsonObject
    private var catalog=JsonObject()
    private fun fixture(slow:Boolean=false)=object:SagaProvider {
        override fun cancel() {}
        override fun membership(movieId:Long):Long? {if(slow) Thread.sleep(200);return 10}
        override fun detail(collectionId:Long)=obj("""{"id":10,"name":"Depredador","overview":"Description","parts":[{"id":1,"title":"Uno"}]}""")
    }
    private fun controller()=CollectionController(app,scope,{AdminConfig("https://worker.invalid","synthetic")},{catalog})
    @Before fun before() {Dispatchers.setMain(UnconfinedTestDispatcher());app=RuntimeEnvironment.getApplication();listOf("collections-state.json","collections-tmdb-cache.json").forEach {File(app.filesDir,it).delete()};scope=CoroutineScope(SupervisorJob()+Dispatchers.Main);catalog=obj("""{"schema_version":1,"items":[{"id":"p","tipo":"pelicula","titulo":"Uno","tmdb_id":1,"telegram_url":"https://t.me/synthetic/1","fecha_agregado":"2024-01-01T00:00:00Z"}]}""");c=controller();c.sagaProviderFactory={fixture()}}
    @After fun after() {c.close();scope.cancel();Dispatchers.resetMain()}
    private fun await() {val end=System.currentTimeMillis()+6000;while(c.busy && System.currentTimeMillis()<end) Thread.sleep(10);assertFalse(c.busy)}
    @Test fun detectionDoesNotSaveCollectionsOrAlterCatalog() {val before=catalog.deepCopy();c.detectSagas();await();assertEquals(1,c.sagaProposals.size);assertTrue(CollectionRules.list(c.data).isEmpty());assertNull(c.draft);assertTrue(c.selectedSagas.isEmpty());assertEquals(before,catalog)}
    @Test fun reviewAndCancelDoNotSaveAndConfirmationDoes() {c.detectSagas();await();c.selectedSagas=setOf(10);c.reviewSagas();assertNotNull(c.sagaConfirmation);assertTrue(CollectionRules.list(c.data).isEmpty());c.cancelSagaConfirmation();assertTrue(CollectionRules.list(c.data).isEmpty());c.reviewSagas();c.confirmSagas();assertEquals(1,CollectionRules.list(c.data).size);assertEquals("p",CollectionRules.ids(CollectionRules.list(c.data).single()).single());c=controller();assertEquals(1,CollectionRules.list(c.data).size)}
    @Test fun changedCatalogRejectsPreviouslyReviewedPlan() {c.detectSagas();await();c.selectedSagas=setOf(10);c.reviewSagas();catalog.array("items")[0].asJsonObject.addProperty("titulo","Changed");c.confirmSagas();assertTrue(CollectionRules.list(c.data).isEmpty());assertTrue(c.message.contains("cambiaron"))}
    @Test fun ignoreDoesNotDeleteAnythingAndCannotBeApproved() {c.detectSagas();await();c.selectedSagas=setOf(10);c.ignoreSaga(10);assertTrue(c.selectedSagas.isEmpty());assertTrue(CollectionRules.list(c.data).isEmpty())}
    @Test fun editorAllowsFieldLevelApprovalRatherThanBatchMetadata() {c.detectSagas();await();c.editSaga(10);assertEquals("importPreview",c.page);assertTrue(c.selectedFields.isEmpty());assertTrue(c.selectedMembers.isEmpty());assertTrue(CollectionRules.list(c.data).isEmpty());c.selectedFields=setOf("nombre");c.selectedMembers=setOf("p");c.applyImport();c.preview();c.save();assertEquals(10L,CollectionRules.list(c.data).single().number("tmdb_collection_id"))}
    @Test fun canceledLateResponseCannotChangeScreenOrSaveAnything() {c.sagaProviderFactory={fixture(true)};c.detectSagas();c.openList();Thread.sleep(600);assertEquals("list",c.page);assertTrue(CollectionRules.list(c.data).isEmpty());assertFalse(c.sagaProgress.complete)}
    @Test fun pendingDraftSurvivesAnalysisAndPreventsBulkSave() {c.begin();c.field("nombre","Borrador importante");val before=c.draft!!.deepCopy();c.detectSagas();await();c.selectedSagas=setOf(10);c.reviewSagas();assertNull(c.sagaConfirmation);assertEquals(before,c.draft);assertTrue(CollectionRules.list(c.data).isEmpty())}
    @Test fun tvdbRestrictedCatalogDoesNotBlockLocalSagaCreation() {catalog.addProperty("fuente","TheTVDB");catalog.addProperty("publicacion_bloqueada",true);c.detectSagas();await();c.selectedSagas=setOf(10);c.reviewSagas();c.confirmSagas();assertEquals(1,CollectionRules.list(c.data).size);assertFalse(CatalogRules.publicCopy(catalog).has("publicacion_bloqueada"))}
    @Test fun syncStatusDoesNotContactServerOrChangeSavedCollections() {val before=c.data.deepCopy();c.pageSyncInfo();assertEquals("collectionSync",c.page);assertEquals(before,c.data);assertFalse(c.busy)}
}
