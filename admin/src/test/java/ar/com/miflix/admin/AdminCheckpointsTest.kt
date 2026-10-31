package ar.com.miflix.admin

import com.google.gson.*
import org.junit.Test
import org.junit.Assert.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse

class AdminCheckpointsTest {
    private val types=AdminCheckpoints.categories.map {it.first}
    private fun url(type:String,number:Int=99)="https://t.me/${type}_channel/7/$number?single&thread=7#original"
    private fun item(type:String)=JsonObject().apply {
        addProperty("id",type);addProperty("tipo",type);addProperty("titulo","Título $type");addProperty("anio",1999)
        addProperty("telegram_url",url(type));addProperty("tmdb_id",42);addProperty("anilist_id",55);addProperty("mal_id",66)
        add("unknown",JsonObject().apply {addProperty("private_field","keep")})
        add("temporadas",JsonArray().apply {add(JsonParser.parseString("""{"numero":1,"episodios":[{"numero":1,"telegram_url":"https://t.me/episodes/1"},{"numero":2,"telegram_url":"https://t.me/episodes/2"}]}"""))})
    }
    private fun catalog()=JsonObject().apply {addProperty("schema_version",1);addProperty("generated_at","old");addProperty("unknown_root",true);add("items",JsonArray().apply {types.forEach {add(item(it))}})}
    private class Store(var value:JsonObject?=null):StateStore {
        var writes=0;var fail=false
        override fun read()=value?.deepCopy()
        override fun write(state:JsonObject){if(fail) error("storage failure");writes++;value=state.deepCopy()}
    }
    private class Remote(val cat:JsonObject):AdminRemote {
        var uploads=0
        override fun download()=cat.deepCopy()
        override fun publish(catalog:JsonObject){uploads++}
    }
    private fun ready(store:Store=Store()):AdminRepository=AdminRepository(store).apply {refresh(Remote(catalog()))}
    private fun populated(repo:AdminRepository) {types.forEach {repo.setCheckpoint(TelegramLinks.checkpoint(item(it),url(it)))}}
    private fun advancesOnly(type:String) {
        val repo=ready();populated(repo)
        val before=types.associateWith {repo.checkpoint(it)}
        val entry=item(type);val cp=AdminCheckpoints.forSave(entry,url(type,150),true,false)!!
        repo.saveItem(entry,cp)
        assertEquals(cp,repo.checkpoint(type))
        types.filter {it!=type}.forEach {assertEquals(before[it],repo.checkpoint(it))}
    }
    @Test fun movieAdvancesOnlyMovies(){advancesOnly("pelicula")}
    @Test fun seriesAdvancesOnlySeries(){advancesOnly("serie")}
    @Test fun animeAdvancesOnlyAnime(){advancesOnly("anime")}
    @Test fun alternatingCategoriesPreserveAllOtherHistoricalPoints(){val repo=ready();populated(repo);listOf("serie","pelicula","anime","serie").forEachIndexed {n,type ->val old=types.associateWith {repo.checkpoint(it)};repo.setCheckpoint(TelegramLinks.checkpoint(item(type),url(type,n+200)));types.filter {it!=type}.forEach {assertEquals(old[it],repo.checkpoint(it))}}}
    @Test fun originalTopicQueryFragmentAndMessageIdArePreserved(){types.forEach {val cp=TelegramLinks.checkpoint(item(it),url(it));assertEquals(url(it),cp.text("telegram_url"));assertEquals(99L,cp.number("telegram_message_id"));assertEquals(it,cp.text("tipo"))}}
    @Test fun openingEachCategoryUsesItsOwnOriginalUrl(){val repo=ready();populated(repo);types.forEach {assertEquals(url(it),repo.checkpointLink(it))}}
    @Test fun missingCategoryHasNoCheckpointAndNoGlobalUrlFallback(){val repo=ready();repo.setCheckpoint(TelegramLinks.checkpoint(item("pelicula"),url("pelicula")));assertNull(repo.checkpoint("serie"));assertNull(repo.checkpointLink("serie"));assertNull(repo.checkpoint("anime"));assertNull(repo.checkpointLink("anime"))}
    @Test fun entirelyEmptyStateHasThreeEmptyCategories(){val repo=AdminRepository(Store());types.forEach {assertNull(repo.checkpoint(it));assertNull(repo.checkpointLink(it))}}
    private fun legacy(type:String)=TelegramLinks.checkpoint(item(type),url(type)).apply {addProperty("future_id","keep");add("extra",JsonArray().apply {add("unknown")})}
    private fun migrateLegacy(type:String){val old=legacy(type);val store=Store(JsonObject().apply {add("catalog",catalog());add("checkpoint",old);addProperty("checkpoint_pending",true)});val repo=AdminRepository(store);assertEquals(old,repo.checkpoint(type));assertEquals(old,store.value!!.get("checkpoint"));types.filter {it!=type}.forEach {assertNull(repo.checkpoint(it))};assertEquals(catalog(),repo.catalog);assertFalse(repo.pending)}
    @Test fun migratesOldMovie(){migrateLegacy("pelicula")}
    @Test fun migratesOldSeries(){migrateLegacy("serie")}
    @Test fun migratesOldAnime(){migrateLegacy("anime")}
    @Test fun migrationIsIdempotentAcrossRestarts(){val store=Store(JsonObject().apply {add("checkpoint",legacy("pelicula"))});AdminRepository(store);val once=store.value!!.deepCopy();val writes=store.writes;repeat(3){AdminRepository(store)};assertEquals(once,store.value);assertEquals(writes,store.writes)}
    @Test fun newFormatWinsOverLegacyEvenWhenCategoryEmpty(){val old=legacy("pelicula");val new=legacy("serie");val store=Store(JsonObject().apply {add("checkpoint",old);add("checkpoints",JsonObject().apply {add("serie",new)})});val repo=AdminRepository(store);assertNull(repo.checkpoint("pelicula"));assertEquals(new,repo.checkpoint("serie"));assertEquals(old,store.value!!.get("checkpoint"))}
    @Test fun unknownLegacyCategoryIsPreservedWithoutGuessing(){val old=legacy("pelicula").apply {remove("tipo")};val store=Store(JsonObject().apply {add("checkpoint",old)});val repo=AdminRepository(store);assertTrue(repo.unclassifiedCheckpoint);types.forEach {assertNull(repo.checkpoint(it))};assertEquals(old,store.value!!.get("checkpoint"));assertEquals(store.value,AdminCheckpoints.migrate(store.value!!))}
    @Test fun corruptNewFormatIsNotOverwrittenOrReplacedByLegacy(){val old=JsonObject().apply {add("checkpoint",legacy("pelicula"));addProperty("checkpoints","unrecognized")};val store=Store(old);val repo=AdminRepository(store);assertEquals(old,store.value);assertFalse(repo.writable);assertTrue(repo.recoveryNotice.isNotBlank());assertThrows(IllegalStateException::class.java){repo.refresh(Remote(catalog()))};assertEquals(old,store.value)}
    @Test fun failedMigrationWritePreservesOriginalAndCanRetryOnRestart(){val old=JsonObject().apply {add("checkpoint",legacy("anime"));add("catalog",catalog());addProperty("pending",true)};val store=Store(old).apply {fail=true};val failed=AdminRepository(store);assertEquals(old,store.value);assertFalse(failed.writable);assertTrue(failed.recoveryNotice.isNotBlank());store.fail=false;val restarted=AdminRepository(store);assertEquals(old.get("checkpoint"),restarted.checkpoint("anime"));assertTrue(restarted.pending)}
    @Test fun checkpointOnlyDoesNotChangeCatalogBaseFingerprintOrPending(){val repo=ready();val before=repo.catalog!!.deepCopy();val base=repo.state.get("base").deepCopy();val fingerprint=CatalogRules.fingerprint(before);populated(repo);assertEquals(before,repo.catalog);assertEquals(base,repo.state.get("base"));assertEquals(fingerprint,CatalogRules.fingerprint(repo.catalog!!));assertFalse(repo.pending);assertFalse(repo.state.has("checkpoint_pending"))}
    @Test fun deletingEachContentKeepsAllHistoricalCheckpoints(){val store=Store();val repo=ready(store);populated(repo);val points=repo.state.get("checkpoints").deepCopy();types.forEach {repo.removeItem(it);assertEquals(points,repo.state.get("checkpoints"))};assertEquals(0,CatalogRules.items(repo.catalog!!).size);assertEquals(points,AdminRepository(store).state.get("checkpoints"))}
    @Test fun episodeDeletionDoesNotChangeCheckpoints(){val repo=ready();populated(repo);val points=repo.state.get("checkpoints").deepCopy();repo.removePart("serie",1,1);repo.removePart("anime",1,2);assertEquals(points,repo.state.get("checkpoints"))}
    @Test fun draftsContinuationAndDiscardDoNotAdvanceCheckpoint(){val store=Store();val repo=ready(store);populated(repo);val points=repo.state.get("checkpoints").deepCopy();repo.persistDraft(item("serie").apply {addProperty("_admin_original",url("serie",500))});val resumed=AdminRepository(store);assertNotNull(resumed.draft());assertEquals(points,resumed.state.get("checkpoints"));resumed.persistDraft(null);assertEquals(points,resumed.state.get("checkpoints"))}
    @Test fun savingSeriesAndAnimeLeavesEpisodeLinksUnchanged(){types.filter {it!="pelicula"}.forEach {type ->val repo=ready();val entry=item(type);val seasons=entry.get("temporadas").deepCopy();repo.saveItem(entry,AdminCheckpoints.forSave(entry,url(type,250),true,false));assertEquals(seasons,CatalogRules.items(repo.catalog!!).first {it.text("tipo")==type}.get("temporadas"));assertEquals(url(type,250),repo.checkpointLink(type))}}
    @Test fun metadataOnlyExistingEditDoesNotAdvanceCheckpoint(){types.forEach {assertNull(AdminCheckpoints.forSave(item(it),"",false,false))}}
    @Test fun movieVersionUpdateKeepsExistingProcessingBoundary(){assertNotNull(AdminCheckpoints.forSave(item("pelicula"),url("pelicula"),false,true));assertNull(AdminCheckpoints.forSave(item("pelicula"),url("pelicula"),false,false))}
    @Test fun incomingPublicationOnMatchedExistingSeriesOrAnimeAdvancesAtSave(){types.filter {it!="pelicula"}.forEach {assertNotNull(AdminCheckpoints.forSave(item(it),url(it,300),false,false))}}
    @Test fun noPublicationDoesNotInventCheckpoint(){types.forEach {assertNull(AdminCheckpoints.forSave(item(it).apply {remove("telegram_url")},"",true,false))}}
    @Test fun checkpointPreservesAdministrativeIdentityAndTime(){val entry=item("anime").apply {addProperty("imdb_id","tt42")};val cp=TelegramLinks.checkpoint(entry,url("anime"));listOf("id","tmdb_id","imdb_id","anilist_id","mal_id","titulo","anio").forEach {assertEquals(entry.text(it),cp.text(it))};assertTrue(cp.text("procesado_at").isNotBlank())}
    @Test fun invalidOriginalOrMismatchedCategoryCannotPartiallySave(){val store=Store();val repo=ready(store);val before=store.value!!.deepCopy();assertThrows(IllegalStateException::class.java){AdminCheckpoints.forSave(item("serie"),"https://evil.test/1",true,false)};assertThrows(IllegalArgumentException::class.java){repo.saveItem(item("serie"),legacy("anime"))};assertEquals(before,store.value)}
    @Test fun failedAtomicSaveDoesNotAdvanceCheckpointOrChangeCatalog(){val store=Store();val repo=ready(store);populated(repo);val before=store.value!!.deepCopy();val oldCatalog=repo.catalog!!.deepCopy();val oldPoints=repo.state.get("checkpoints").deepCopy();store.fail=true;assertThrows(IllegalStateException::class.java){repo.saveItem(item("serie"),TelegramLinks.checkpoint(item("serie"),url("serie",500)))};assertEquals(before,store.value);assertEquals(oldCatalog,repo.catalog);assertEquals(oldPoints,repo.state.get("checkpoints"));assertFalse(repo.pending)}
    @Test fun checkpointReturnedToUiIsDetachedFromStoredState(){val repo=ready();populated(repo);repo.checkpoint("serie")!!.addProperty("telegram_url",url("anime"));assertEquals(url("serie"),repo.checkpointLink("serie"))}
    @Test fun refreshAndExplicitCatalogDiscardKeepLocalHistory(){val repo=ready();populated(repo);val points=repo.state.get("checkpoints").deepCopy();repo.saveItem(item("serie"));repo.refresh(Remote(catalog()),true);assertEquals(points,repo.state.get("checkpoints"));assertFalse(repo.pending)}
    @Test fun networkUsesOnlyCatalogRoutesAndNeverSendsCheckpoints(){val server=MockWebServer();server.start();try {server.enqueue(MockResponse().setBody(catalog().toString()));server.enqueue(MockResponse().setBody(catalog().toString()));server.enqueue(MockResponse().setBody("{\"ok\":true}"));val repo=AdminRepository(Store());val remote=HttpAdminRemote(AdminConfig(server.url("/").toString().trimEnd('/'),"fake"),OkHttpClient());repo.refresh(remote);populated(repo);repo.saveItem(item("serie"),TelegramLinks.checkpoint(item("serie"),url("serie",400)));repo.publish(remote);assertEquals(3,server.requestCount);assertEquals("/catalogo.json",server.takeRequest().path);assertEquals("/catalogo.json",server.takeRequest().path);val request=server.takeRequest();assertEquals("/admin/catalogo",request.path);val published=CatalogRules.parse(request.body.readUtf8());assertEquals(1L,published.number("schema_version"));assertFalse(published.has("checkpoints"));assertFalse(published.has("checkpoint"));assertEquals(3,CatalogRules.items(published).size);assertFalse(repo.pending);assertEquals(url("serie",400),repo.checkpointLink("serie"))} finally {server.shutdown()}}
}
