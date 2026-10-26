package ar.com.miflix.admin

import android.app.Application
import androidx.compose.runtime.MutableState
import com.google.gson.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** Runs the production ViewModel, HTTP metadata, AtomicFile and save/sync callbacks. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest=Config.NONE,sdk=[28])
@OptIn(ExperimentalCoroutinesApi::class)
class AdminSeriesFlowTest {
    private lateinit var app:Application
    private lateinit var server:MockWebServer
    private lateinit var vm:AdminViewModel
    private lateinit var remoteCatalog:JsonObject
    private var published:JsonObject?=null
    private var artwork=true
    private var imagesStatus=200
    private val first="https://t.me/c/123/7/501?single&thread=7#first"
    private val last="https://t.me/c/123/7/550?single&thread=7#last"
    private val moviePoint=JsonParser.parseString("""{"tipo":"pelicula","titulo":"El legado","anio":"2018","telegram_message_id":239,"telegram_url":"https://t.me/movies/239","unknown":"preserve"}""").asJsonObject
    private fun emptyCatalog()=JsonParser.parseString("""{"schema_version":1,"items":[],"unknown_root":true}""").asJsonObject

    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app=RuntimeEnvironment.getApplication()
        app.filesDir.listFiles()?.forEach {it.deleteRecursively()}
        app.noBackupFilesDir.listFiles()?.forEach {it.deleteRecursively()}
        remoteCatalog=emptyCatalog()
        // The same legacy movie point observed on device; production init performs migration.
        File(app.filesDir,"admin-state.json").writeText(JsonObject().apply {add("checkpoint",moviePoint)}.toString())
        server=MockWebServer()
        server.dispatcher=object:Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                val path=request.path!!.substringBefore('?')
                if(path.endsWith("/images")) return MockResponse().setResponseCode(imagesStatus).setBody(if(artwork) """{"logos":[{"file_path":"/logo.png","iso_639_1":"es"}]}""" else """{"logos":[]} """)
                val body=when {
                    path=="/catalogo.json" -> remoteCatalog.toString()
                    path=="/admin/catalogo" -> {published=JsonParser.parseString(request.body.readUtf8()).asJsonObject;remoteCatalog=published!!.deepCopy();"{\"ok\":true}"}
                    path.endsWith("/search/tv") -> """{"results":[{"id":42,"name":"The Wonderfools","first_air_date":"2026-01-01"}]}"""
                    path.endsWith("/search/movie") -> """{"results":[{"id":42,"title":"Película nueva","release_date":"2026-01-01"}]}"""
                    path.endsWith("/tv/42") -> """{"id":42,"name":"The Wonderfools","first_air_date":"2026-01-01","genres":[],"seasons":[{"season_number":1,"name":"Temporada 1","episode_count":2}]}"""
                    path.endsWith("/movie/42") -> """{"id":42,"title":"Película nueva","release_date":"2026-01-01","genres":[]}"""
                    path.endsWith("/external_ids") -> "{\"imdb_id\":\"tt42\"}"
                    path.endsWith("/credits") -> "{\"cast\":[],\"crew\":[]}"
                    path.endsWith("/videos") -> "{\"results\":[]}"
                    else -> return MockResponse().setResponseCode(404)
                }
                val response=if(artwork && (path.endsWith("/tv/42") || path.endsWith("/movie/42"))) JsonParser.parseString(body).asJsonObject.apply {addProperty("poster_path","/poster.jpg");addProperty("backdrop_path","/back.jpg")}.toString() else body
                return MockResponse().setBody(response)
            }
        }
        server.start()
        vm=newModel()
    }
    @After fun teardown(){server.shutdown();Dispatchers.resetMain()}
    private fun newModel():AdminViewModel {
        val model=AdminViewModel(app)
        // Only replace encrypted credential setup; all workflow methods/providers remain production.
        @Suppress("UNCHECKED_CAST")
        val config=AdminViewModel::class.java.getDeclaredField("config\$delegate").apply {isAccessible=true}.get(model) as MutableState<AdminConfig?>
        config.value=AdminConfig(server.url("/").toString().trimEnd('/'),"fake-test-token")
        model.refresh();await(model)
        assertTrue(model.message,model.writable)
        return model
    }
    private fun await(model:AdminViewModel=vm){runBlocking {withTimeout(10_000){while(model.busy) delay(5)}}}
    private fun searchAndReview(type:String="serie",root:String?=null) {
        vm.openCategory(type);vm.newItem();root?.let {vm.changeTelegramLink(it)};vm.query=if(type=="serie") "The Wonderfools" else "Película nueva"
        vm.search();await();assertEquals(1,vm.results.size)
        vm.use(vm.results.single());await();assertEquals("review",vm.screen)
        assertEquals(type,vm.draft!!.text("tipo"))
    }
    private fun episode(number:Int,link:String){vm.addEpisode(1,number);vm.episodeField(1,number.toLong(),"telegram_url",link)}
    private fun assertSeriesPoint(model:AdminViewModel=vm,link:String=last){val cp=model.checkpoint("serie");assertNotNull("Confirmed series workflow must produce a checkpoint",cp);assertEquals("The Wonderfools",cp!!.text("titulo"));assertEquals(link,model.checkpointLink("serie"));assertEquals(TelegramLinks.messageId(link),cp.number("telegram_message_id"));assertEquals(moviePoint,model.checkpoint("pelicula"));assertNull(model.checkpoint("anime"))}

    @Test fun tmdbSeriesWithEpisodeOnlyLinksAdvancesOnConfirmedSaveAndSurvivesSyncRestart(){
        searchAndReview();episode(1,first);episode(2,last)
        assertNull(vm.checkpoint("serie"));assertEquals("",vm.draft!!.text("telegram_url"))
        vm.save();assertEquals("home",vm.screen);assertSeriesPoint();assertTrue(vm.pending)
        val saved=CatalogRules.items(vm.catalog!!).single();assertEquals("",saved.text("telegram_url"))
        assertEquals(listOf(first,last),saved.array("temporadas")[0].asJsonObject.array("episodios").map {it.asJsonObject.text("telegram_url")})
        vm.sync();await();assertFalse(vm.pending);assertNotNull(published);assertFalse(published!!.has("checkpoints"))
        assertSeriesPoint(newModel())
    }
    @Test fun seriesRootLinkFlowStillAdvancesOnlySeries(){searchAndReview(root=last);assertNull(vm.checkpoint("serie"));vm.save();assertSeriesPoint()}
    @Test fun metadataOrEpisodeTitleEditDoesNotReprocessOldPublication(){searchAndReview();episode(1,first);vm.save();val before=vm.checkpoint("serie")!!;vm.edit(CatalogRules.items(vm.catalog!!).single());vm.field("titulo","Título corregido");vm.episodeField(1,1,"titulo","Nombre corregido");vm.save();assertEquals(before,vm.checkpoint("serie"));assertEquals("Título corregido",CatalogRules.items(vm.catalog!!).single().text("titulo"))}
    @Test fun matchedExistingSeriesAlsoRecordsExplicitEpisodePublication(){
        remoteCatalog.add("items",JsonArray().apply {add(JsonParser.parseString("""{"id":"serie-imdb-tt42","tipo":"serie","titulo":"The Wonderfools","temporadas":[{"numero":1,"episodios":[]}],"unknown":"keep"}"""))})
        vm.refresh();await();searchAndReview();assertFalse(vm.isNew);episode(1,last);vm.save();assertSeriesPoint()
        assertEquals("keep",CatalogRules.items(vm.catalog!!).single().text("unknown"))
    }
    @Test fun episodeDraftDoesNotAdvanceUntilResumedReviewIsSaved(){
        searchAndReview();episode(1,last);assertNull(vm.checkpoint("serie"))
        vm=newModel();assertNull(vm.checkpoint("serie"));vm.resumeDraft();assertEquals("review",vm.screen)
        vm.save();assertSeriesPoint()
    }
    @Test fun lastPublicationEnteredWinsRatherThanHighestEpisodeNumber(){searchAndReview();episode(7,first);episode(2,last);vm.save();assertSeriesPoint()}
    @Test fun discardingEpisodeDraftLeavesAllCheckpointsUntouched(){searchAndReview();episode(1,last);vm.discardDraft();assertNull(vm.checkpoint("serie"));assertEquals(moviePoint,vm.checkpoint("pelicula"));assertNull(vm.checkpoint("anime"));assertEquals(0,CatalogRules.items(vm.catalog!!).size)}
    @Test fun clearingEpisodeLinkDoesNotInventProcessedPublication(){searchAndReview();episode(1,last);vm.episodeField(1,1,"telegram_url","");vm.save();assertEquals("home",vm.screen);assertNull(vm.checkpoint("serie"));assertEquals(moviePoint,vm.checkpoint("pelicula"))}
    @Test fun invalidEpisodeLinkCannotSaveCatalogOrCheckpoint(){searchAndReview();episode(1,"https://invalid.test/2");vm.save();assertEquals("review",vm.screen);assertEquals(0,CatalogRules.items(vm.catalog!!).size);assertNull(vm.checkpoint("serie"));assertFalse(vm.pending)}
    @Test fun movieFlowStillUsesOriginalRootPublication(){vm.openCategory("pelicula");vm.newItem();vm.changeTelegramLink(last);vm.query="Película nueva";vm.search();await();vm.use(vm.results.single());await();vm.save();assertEquals("home",vm.screen);assertEquals(last,vm.checkpointLink("pelicula"));assertNull(vm.checkpoint("serie"));assertNull(vm.checkpoint("anime"))}
    @Test fun sharedAnimeEpisodeEditorUsesSameSaveIntegration(){vm.openCategory("anime");vm.newItem();vm.field("titulo","Anime de prueba");vm.addSeason(1);episode(1,last);assertNull(vm.checkpoint("anime"));vm.save();assertEquals("home",vm.screen);assertEquals(last,vm.checkpointLink("anime"));assertEquals(moviePoint,vm.checkpoint("pelicula"));assertNull(vm.checkpoint("serie"))}
    @Test fun dateIsAssignedOnlyAfterRealReviewIsConfirmedAndSurvivesEditSyncRestart(){
        searchAndReview("pelicula",last)
        assertFalse(vm.draft!!.has("fecha_agregado"));assertTrue(CatalogRules.items(vm.catalog!!).isEmpty())
        vm=newModel();vm.resumeDraft();assertFalse(vm.draft!!.has("fecha_agregado"))
        val before=java.time.Instant.now();vm.save();assertEquals("home",vm.screen)
        val saved=CatalogRules.items(vm.catalog!!).single();val date=saved.text("fecha_agregado")
        assertTrue(date.endsWith("Z"));assertFalse(java.time.Instant.parse(date).isBefore(before))
        vm.edit(saved);vm.field("titulo","Título corregido");vm.save();vm.sync();await()
        assertEquals(date,CatalogRules.items(published!!).single().text("fecha_agregado"))
        assertEquals(date,CatalogRules.items(newModel().catalog!!).single().text("fecha_agregado"))
    }
    @Test fun existingUndatedSeriesMatchedByMetadataDoesNotBecomeNew(){
        remoteCatalog.add("items",JsonArray().apply {add(JsonParser.parseString("""{"id":"serie-imdb-tt42","tipo":"serie","titulo":"The Wonderfools","temporadas":[{"numero":1,"episodios":[]}]}"""))})
        vm.refresh();await();searchAndReview();assertFalse(vm.isNew)
        episode(1,last);vm.save();vm.sync();await()
        assertFalse(CatalogRules.items(published!!).single().has("fecha_agregado"))
    }
    private fun recoveryFixture(slow: Boolean=false) {
        vm.recoveryProviderFactory={object:RecoveryProvider {
            override fun cancel() {}
            override fun search(item:JsonObject,query:String):List<RecoveryCandidate> {
                if(slow) Thread.sleep(150)
                return listOf(RecoveryCandidate("TMDB",42,"The Wonderfools","2026","https://www.themoviedb.org/tv/42","en-US",JsonObject()))
            }
            override fun retrieve(candidate:RecoveryCandidate,item:JsonObject,seasons:Set<Long>,language:String)=RecoveryData(candidate,JsonObject(),listOf(JsonParser.parseString("""{"numero":1,"episodios":[{"numero":1,"titulo":"A real name","sinopsis":"An actual synopsis"}]}""").asJsonObject))
        }}
    }
    @Test fun selectiveRecoveryQueriesDoNotPersistAndOnlyConfirmedFieldsReachPublication(){
        searchAndReview();episode(1,first);vm.save();val before=vm.catalog!!.deepCopy();val cp=vm.checkpoint("serie")!!.deepCopy()
        vm.edit(CatalogRules.items(vm.catalog!!).single());val initialDraft=vm.draft!!.deepCopy();recoveryFixture()
        vm.startRecovery("TMDB",1);vm.searchRecovery();await();vm.retrieveRecovery(vm.recoveryCandidates.single());await()
        assertEquals(before,vm.catalog);assertEquals(initialDraft,vm.draft);assertNull(published)
        vm.confirmRecoveryMapping("1/1",true);vm.recoverySelection=setOf("1/1/sinopsis");vm.applyRecovery()
        assertEquals(before,vm.catalog);assertEquals("Episodio 1",vm.draft!!.array("temporadas")[0].asJsonObject.array("episodios")[0].asJsonObject.text("titulo"));assertTrue(vm.canUndoRecovery)
        vm.save();vm.sync();await();val saved=CatalogRules.items(published!!).single()
        assertEquals(before.array("items")[0].asJsonObject.get("fecha_agregado"),saved.get("fecha_agregado"))
        val e=saved.array("temporadas")[0].asJsonObject.array("episodios")[0].asJsonObject
        assertEquals(first,e.text("telegram_url"));assertEquals("An actual synopsis",e.text("sinopsis"));assertEquals(cp,vm.checkpoint("serie"))
        vm=newModel();vm.edit(CatalogRules.items(vm.catalog!!).single());assertTrue(vm.canUndoRecovery);vm.undoRecovery();assertEquals("",vm.draft!!.array("temporadas")[0].asJsonObject.array("episodios")[0].asJsonObject.text("sinopsis"))
    }
    @Test fun cancellingPreviewKeepsSavedAndDraftRecordsExactly(){
        searchAndReview();episode(1,first);val before=vm.catalog!!.deepCopy();val draft=vm.draft!!.deepCopy();recoveryFixture()
        vm.startRecovery("TMDB");vm.searchRecovery();await();vm.retrieveRecovery(vm.recoveryCandidates.single());await();vm.cancelRecovery()
        assertEquals(before,vm.catalog);assertEquals(draft,vm.draft);assertFalse(vm.canUndoRecovery)
    }
    @Test fun cancelledLateQueryCannotReplaceCurrentScreenOrDraft(){
        searchAndReview();val before=vm.draft!!.deepCopy();recoveryFixture(true);vm.startRecovery("TMDB");vm.searchRecovery();vm.cancelRecovery()
        Thread.sleep(250);assertEquals("review",vm.screen);assertTrue(vm.recoveryCandidates.isEmpty());assertEquals(before,vm.draft);assertFalse(vm.busy)
    }
    @Test fun newManualRecordBackupRemainsUsableAfterFirstSaveAssignsItsId(){
        vm.openCategory("serie");vm.newItem();vm.field("titulo","Manual");vm.addSeason(1);episode(1,first);recoveryFixture()
        vm.startRecovery("TMDB");vm.searchRecovery();await();vm.retrieveRecovery(vm.recoveryCandidates.single());await();vm.confirmRecoveryMapping("1/1",true);vm.recoverySelection=setOf("1/1/titulo");vm.applyRecovery()
        assertTrue(vm.canUndoRecovery);vm.save();val stored=CatalogRules.items(vm.catalog!!).single();assertTrue(stored.text("id").isNotBlank());assertFalse(stored.toString().contains("_admin_metadata_recovery"))
        vm.edit(stored);assertTrue(vm.canUndoRecovery);vm.undoRecovery();assertEquals("Episodio 1",vm.draft!!.array("temporadas")[0].asJsonObject.array("episodios")[0].asJsonObject.text("titulo"));assertEquals(stored.get("fecha_agregado"),vm.draft!!.get("fecha_agregado"))
    }
    @Test fun traditionalTmdbButtonProtectsAnAlreadyPopulatedSeasonByOpeningComparison(){
        searchAndReview();episode(1,first);val before=vm.draft!!.deepCopy();recoveryFixture();vm.seasonNumber=1;vm.fetchSeason();await()
        assertEquals("recoverySearch",vm.screen);assertEquals("es-MX",vm.recoveryLanguage);assertEquals(before,vm.draft);assertEquals(1,vm.recoveryCandidates.size)
    }
    private fun assertArtwork(item:JsonObject){assertEquals(HttpMetadata.image("/poster.jpg"),item.text("poster_url"));assertEquals(HttpMetadata.image("/back.jpg"),item.text("backdrop_url"));assertEquals(HttpMetadata.image("/logo.png"),item.text("logo_url"))}
    @Test fun movieVisualsSurviveRealReviewDraftSaveEditSyncAndRestart(){
        searchAndReview("pelicula",last);assertArtwork(vm.draft!!);assertEquals(0,CatalogRules.items(vm.catalog!!).size)
        vm=newModel();vm.resumeDraft();assertArtwork(vm.draft!!);vm.save();assertArtwork(CatalogRules.items(vm.catalog!!).single())
        val cp=vm.checkpoint("pelicula")!!.deepCopy();vm.edit(CatalogRules.items(vm.catalog!!).single());vm.field("titulo","Corregida");vm.save()
        assertEquals(cp,vm.checkpoint("pelicula"));vm.sync();await();assertArtwork(CatalogRules.items(published!!).single());assertEquals("1",published!!.text("schema_version"));assertArtwork(CatalogRules.items(newModel().catalog!!).single())
    }
    @Test fun seriesVisualsSurviveRealEpisodeSaveAndSyncWithoutChangingOtherCheckpoints(){
        searchAndReview();assertArtwork(vm.draft!!);episode(1,first);episode(2,last);vm.save();assertSeriesPoint();vm.sync();await()
        val item=CatalogRules.items(published!!).single();assertArtwork(item);assertEquals(listOf(first,last),item.array("temporadas")[0].asJsonObject.array("episodios").map {it.asJsonObject.text("telegram_url")});assertSeriesPoint(newModel())
    }
    @Test fun absentVisualsDoNotBlockConfirmedSave(){artwork=false;searchAndReview(root=last);assertFalse(vm.draft!!.has("backdrop_url"));assertFalse(vm.draft!!.has("logo_url"));vm.save();assertEquals("home",vm.screen);assertSeriesPoint();assertTrue(vm.pending)}
    @Test fun unavailableImagesRelayDoesNotBlockConfirmedSave(){imagesStatus=400;searchAndReview(root=last);assertFalse(vm.draft!!.has("logo_url"));assertEquals(HttpMetadata.image("/back.jpg"),vm.draft!!.text("backdrop_url"));vm.save();assertSeriesPoint()}
    @Test fun existingVisualsSurviveMetadataRefreshWithoutImages(){searchAndReview(root=last);vm.save();vm.edit(CatalogRules.items(vm.catalog!!).single());artwork=false;vm.query="The Wonderfools";vm.search();await();vm.use(vm.results.single());await();assertArtwork(vm.draft!!);vm.field("sinopsis","Corregida");vm.save();assertArtwork(CatalogRules.items(vm.catalog!!).single())}
    @Test fun existingBackgroundEditorKeepsCanonicalReferenceConsistent(){searchAndReview(root=last);vm.field("fondo_url","https://fixture.test/custom.jpg");assertEquals(vm.draft!!.text("fondo_url"),vm.draft!!.text("backdrop_url"));assertEquals(HttpMetadata.image("/logo.png"),vm.draft!!.text("logo_url"));vm.field("fondo_url","");assertFalse(vm.draft!!.has("backdrop_url"));vm.save();assertSeriesPoint()}

}
