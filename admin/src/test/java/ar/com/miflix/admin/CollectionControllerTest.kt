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
class CollectionControllerTest {
    private lateinit var app:Application
    private lateinit var scope:CoroutineScope
    private lateinit var c:CollectionController
    private fun obj(raw:String)=JsonParser.parseString(raw).asJsonObject
    private fun catalog()=obj("""{"schema_version":1,"items":[{"id":"movie","tipo":"pelicula","titulo":"Movie","tmdb_id":1,"telegram_url":"https://t.me/demo/1","fecha_agregado":"2020-01-01T00:00:00Z"},{"id":"series","tipo":"serie","titulo":"Series"},{"id":"anime","tipo":"anime","titulo":"Anime"}]}""")
    @Before fun setup() {Dispatchers.setMain(UnconfinedTestDispatcher());app=RuntimeEnvironment.getApplication();File(app.filesDir,"collections-state.json").delete();scope=CoroutineScope(SupervisorJob()+Dispatchers.Main);c=controller()}
    @After fun after() {c.close();scope.cancel();Dispatchers.resetMain()}
    private fun controller()=CollectionController(app,scope,{AdminConfig("https://worker.invalid","synthetic")},{catalog()})
    private fun await() {val end=System.currentTimeMillis()+5000;while(c.busy && System.currentTimeMillis()<end) Thread.sleep(10);assertFalse(c.busy)}
    private fun fixture(slow:Boolean=false)=object:CollectionProvider {
        override fun cancel() {}
        override fun search(query:String,page:Int,language:String):CollectionSearch {if(slow) Thread.sleep(150);return CollectionSearch(listOf(obj("""{"id":10,"name":"Saga"}""")),1,1)}
        override fun detail(id:Long,language:String)=obj("""{"id":10,"name":"Saga","overview":"Sinopsis","parts":[{"id":1,"title":"Movie"}]}""")
        override fun images(id:Long)=listOf(CollectionImage("poster_url","https://image.invalid/p.jpg","https://image.invalid/small.jpg","es"))
    }
    @Test fun manualMarvelMixedMembersSaveRestartEditAndReorder() {
        c.begin();c.field("nombre","Mundo Marvel");c.add(linkedSetOf("movie","series","anime"));c.move(2,-1);c.preview();assertEquals("preview",c.page);c.save()
        c=controller();val saved=CollectionRules.list(c.data).single();assertEquals(listOf("movie","anime","series"),CollectionRules.ids(saved));c.edit(saved.text("id"));c.field("nombre","Mundo Marvel familiar");c.preview();c.save();assertEquals(saved.text("id"),CollectionRules.list(c.data).single().text("id"))
    }
    @Test fun draftSurvivesRestartAndDiscardChangesNoSavedCollection() {c.begin();c.field("nombre","Borrador");c=controller();assertEquals("Borrador",c.draft!!.text("nombre"));c.discard();assertTrue(CollectionRules.list(c.data).isEmpty());assertNull(c.draft)}
    @Test fun queryAndCompareDoNotChangeEvenCollectionDraft() {c.begin("tmdb");c.query="Saga";c.providerFactory={fixture()};val before=c.draft!!.deepCopy();c.search();await();c.retrieve(10);await();assertEquals(before,c.draft);assertTrue(CollectionRules.list(c.data).isEmpty());assertTrue(c.selectedMembers.isEmpty());c.backToEdit();assertEquals(before,c.draft)}
    @Test fun importOnlyExplicitlySelectedFieldsAndExistingMembers() {c.begin("hybrid");c.field("nombre","Marvel manual");c.providerFactory={fixture()};c.search();await();c.retrieve(10);await();assertFalse("nombre" in c.selectedFields);c.selectedFields=setOf("descripcion");c.selectedMembers=setOf("movie");c.applyImport();assertEquals("Marvel manual",c.draft!!.text("nombre"));assertEquals(listOf("movie"),CollectionRules.ids(c.draft!!));assertTrue(CollectionRules.list(c.data).isEmpty());c.preview();c.save();assertEquals("tmdb",CollectionRules.list(c.data).single().text("origen"))}
    @Test fun requeryDoesNotOverwriteCustomizedNameOrMembers() {c.begin();c.field("nombre","Mundo Marvel");c.add(setOf("anime"));c.providerFactory={fixture()};c.search();await();c.retrieve(10);await();c.selectedFields=emptySet();c.selectedMembers=emptySet();c.applyImport();assertEquals("Mundo Marvel",c.draft!!.text("nombre"));assertEquals(listOf("anime"),CollectionRules.ids(c.draft!!))}
    @Test fun imageSelectionChangesOnlyDraftImage() {c.begin();c.field("nombre","Saga");c.providerFactory={fixture()};c.search();await();c.retrieve(10);await();c.selectedFields=setOf("nombre");c.applyImport();c.loadImages();await();assertEquals("images",c.page);c.chooseImage(c.images.single());assertEquals("https://image.invalid/p.jpg",c.draft!!.text("poster_url"));assertTrue(CollectionRules.list(c.data).isEmpty())}
    @Test fun lateCanceledQueryCannotReplaceScreenOrDraft() {c.begin("tmdb");val old=c.draft!!.deepCopy();c.providerFactory={fixture(true)};c.query="Saga";c.search();c.backToEdit();Thread.sleep(250);assertEquals("edit",c.page);assertEquals(old,c.draft);assertTrue(c.results.isEmpty())}
    @Test fun restoreRequiresPreviewThenExplicitConfirmation() {c.begin();c.field("nombre","Primera");c.preview();c.save();val first=c.export();c.begin();c.field("nombre","Segunda");c.preview();c.save();c.readRestore(first);assertEquals(2,CollectionRules.list(c.data).size);c.confirmRestore();assertEquals(1,CollectionRules.list(c.data).size);c.undo();assertEquals(2,CollectionRules.list(c.data).size)}
    @Test fun deletionAndBackupAreCollectionsOnly() {c.begin();c.field("nombre","Temporal");c.add(setOf("movie"));c.preview();c.save();val id=CollectionRules.list(c.data).single().text("id");c.delete(id);assertTrue(CollectionRules.list(c.data).isEmpty());c.undo();assertEquals(id,CollectionRules.list(c.data).single().text("id"));assertEquals(3,c.catalogItems.size)}
    @Test fun collectionSaveDoesNotSetCatalogPendingOrBlockPublish() {
        val store=object:StateStore {var value:JsonObject?=null;override fun read()=value;override fun write(state:JsonObject){value=state}}
        var calls=0;val remote=object:AdminRemote {override fun download()=catalog();override fun publish(catalog:JsonObject){calls++}}
        val admin=AdminRepository(store);admin.refresh(remote);val before=admin.catalog!!.deepCopy()
        c.begin();c.field("nombre","Colección local");c.add(setOf("movie"));c.preview();c.save();assertFalse(admin.pending);assertEquals(before,admin.catalog)
        admin.saveItem(CatalogRules.items(admin.catalog!!).first().deepCopy().apply{addProperty("titulo","Corregido")});admin.publish(remote);assertEquals(1,calls);assertEquals(1,CollectionRules.list(c.data).size)
    }
    @Test fun tvdbBlockIsNotBypassedByCollections() {val root=catalog();root.add("source",obj("""{"fuente":"TheTVDB","publicacion_bloqueada":true}"""));c.begin();c.field("nombre","Manual");c.preview();c.save();assertThrows(IllegalStateException::class.java){CatalogRules.publicCopy(root)}}
    @Test fun corruptedCollectionFileDoesNotBlockRegularCatalog() {
        File(app.filesDir,"collections-state.json").writeText("bad json");c=controller();assertFalse(c.writable)
        val root=catalog();assertEquals(root,CatalogRules.publicCopy(CatalogRules.parse(root.toString())));assertEquals("bad json",File(app.filesDir,"collections-state.json").readText())
    }
}
