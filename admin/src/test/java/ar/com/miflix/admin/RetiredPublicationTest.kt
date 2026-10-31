package ar.com.miflix.admin

import com.google.gson.*
import okhttp3.*
import okhttp3.mockwebserver.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class RetiredPublicationTest {
    private fun first(root:JsonObject)=CatalogRules.items(root).first()
    private fun ready():Pair<AdminRepository,LegacyFixtures.Remote> {val remote=LegacyFixtures.Remote(LegacyFixtures.catalog());return AdminRepository(LegacyFixtures.Store()).apply {refresh(remote)} to remote}
    @Test fun normalMovieCreateEditPublishDownloadWorks() {val (repo,remote)=ready();val m=LegacyFixtures.item("movie","pelicula");repo.saveItem(m);val date=first(repo.catalog!!).get("fecha_agregado");repo.publish(remote);assertEquals(1,remote.writes);repo.saveItem(m.apply {addProperty("titulo","Editada")});repo.publish(remote);repo.refresh(remote);assertEquals("Editada",first(repo.catalog!!).text("titulo"));assertEquals(date,first(repo.catalog!!).get("fecha_agregado"))}
    @Test fun normalSeriesSeasonsEpisodesAndAnimeCanPublish() {val (repo,remote)=ready();repo.saveItem(LegacyFixtures.item());repo.saveItem(LegacyFixtures.item("a","anime"));repo.publish(remote);assertEquals(2,CatalogRules.items(remote.root).size);assertEquals(2,first(remote.root).array("temporadas").size());assertFalse(repo.pending)}
    @Test fun metadataCleanupRestoresNormalPublicationOfOtherTitles() {val base=LegacyFixtures.catalog(LegacyFixtures.item(),LegacyFixtures.item("p","pelicula"));val local=base.deepCopy();local.array("items")[0]=LegacyFixtures.marked(local.array("items")[0].asJsonObject);val store=LegacyFixtures.Store();store.data=LegacyFixtures.state(local,base);val repo=AdminRepository(store);val remote=LegacyFixtures.Remote(base);repo.refresh(remote);repo.saveItem(CatalogRules.items(repo.catalog!!)[1].deepCopy().apply {addProperty("titulo","Cambio legítimo")});repo.publish(remote);assertEquals(1,remote.writes);assertFalse(RetiredMetadata.contains(remote.root));assertEquals(2,CatalogRules.items(remote.root).size)}
    @Test fun historicalPendingWithoutSafeFieldStillPublishesIdentityAndLinks() {val base=LegacyFixtures.catalog();val local=LegacyFixtures.catalog(LegacyFixtures.marked(LegacyFixtures.item()));val store=LegacyFixtures.Store();store.data=LegacyFixtures.state(local,base);val repo=AdminRepository(store);val remote=LegacyFixtures.Remote(base);repo.refresh(remote);repo.publish(remote);assertEquals("s",first(remote.root).text("id"));assertEquals("https://t.me/synthetic/3",LegacyFixtures.target(first(remote.root),"1/1/titulo").text("telegram_url"));assertNull(LegacyFixtures.target(first(remote.root),"1/1/sinopsis").get("sinopsis"));assertFalse(repo.pending)}
    @Test fun failedNetworkKeepsPendingAndRetryWorksWithSameData() {val (repo,remote)=ready();repo.saveItem(LegacyFixtures.item("p","pelicula"));val before=repo.catalog!!.deepCopy();remote.fail=true;assertThrows(IllegalStateException::class.java){repo.publish(remote)};assertTrue(repo.pending);assertEquals(before,repo.catalog);remote.fail=false;repo.publish(remote);assertFalse(repo.pending);assertEquals(before,remote.root)}
    @Test fun unchangedSaveAndRepeatedSyncDoesNotRegenerateDate() {val (repo,remote)=ready();repo.saveItem(LegacyFixtures.item("p","pelicula"));repo.publish(remote);val old=first(repo.catalog!!).deepCopy();repo.saveItem(old);repo.publish(remote);repo.publish(remote);assertEquals(old.get("fecha_agregado"),first(repo.catalog!!).get("fecha_agregado"));assertEquals(2,remote.writes)}
    @Test fun newerRemoteUnknownFieldsRejectStalePutRatherThanLoseThem() {val (repo,remote)=ready();repo.saveItem(LegacyFixtures.item());remote.root.add("future_remote",LegacyFixtures.obj("""{"keep":true}"""));val before=remote.root.deepCopy();val error=assertThrows(IllegalStateException::class.java){repo.publish(remote)};assertTrue(error.message!!.contains("remoto cambió"));assertEquals(before,remote.root);assertTrue(repo.pending);assertEquals(0,remote.writes)}
    @Test fun newerRemoteCollectionsRejectStalePutAndKeepLocalPending() {val (repo,remote)=ready();repo.saveItem(LegacyFixtures.item());remote.root.array("colecciones").add(LegacyFixtures.obj("""{"id":"new-remote","titulos":["absent"]}"""));assertThrows(IllegalStateException::class.java){repo.publish(remote)};assertEquals(2,remote.root.array("colecciones").size());assertTrue(repo.pending)}
    @Test fun remoteAdditionalFieldsAndCollectionsSurviveNormalPut() {val (repo,remote)=ready();val collections=remote.root.get("colecciones").deepCopy();repo.saveItem(LegacyFixtures.item());repo.publish(remote);assertEquals(collections,remote.root.get("colecciones"));assertTrue(remote.root.getAsJsonObject("unknown_root").get("preserve").asBoolean)}
    @Test fun projectionSentBecomesCorrectBaseAfterAck() {val (repo,remote)=ready();repo.saveItem(LegacyFixtures.item().apply {addProperty("poster_local","/private/poster.jpg")});repo.publish(remote);assertEquals(remote.root,repo.state.get("base"));repo.saveItem(first(repo.catalog!!).deepCopy().apply {addProperty("sinopsis","Cambio normal")});repo.publish(remote);assertEquals(2,remote.writes);assertFalse(remote.root.array("items")[0].asJsonObject.has("poster_local"));assertTrue(first(repo.catalog!!).has("poster_local"))}
    @Test fun downloadFailureDoesNotClearPending() {val (repo,_)=ready();repo.saveItem(LegacyFixtures.item());val remote=object:AdminRemote {override fun download():JsonObject=error("synthetic offline");override fun publish(catalog:JsonObject)=error("must not PUT")};assertThrows(IllegalStateException::class.java){repo.publish(remote)};assertTrue(repo.pending)}
    @Test fun checkpointsAndDraftAreNotClearedByPublishing() {val (repo,remote)=ready();val cp=TelegramLinks.checkpoint(LegacyFixtures.item(),"https://t.me/synthetic/3");repo.saveItem(LegacyFixtures.item(),cp);repo.persistDraft(LegacyFixtures.item().apply {addProperty("sinopsis","Borrador sin guardar")});val draft=repo.draft();repo.publish(remote);assertEquals(draft,repo.draft());assertEquals(cp,repo.checkpoint("serie"));assertFalse(repo.pending)}
    @Test fun actualHttpPutUsesOriginalWorkerProtocolWithProjection() {
        val server=MockWebServer();var kv=LegacyFixtures.catalog();var puts=0
        server.dispatcher=object:okhttp3.mockwebserver.Dispatcher(){override fun dispatch(request:RecordedRequest):MockResponse {
            if(request.path=="/catalogo.json" && request.method=="GET") return MockResponse().setBody(kv.toString())
            if(request.path=="/admin/catalogo" && request.method=="PUT") {
                if(request.getHeader("Authorization")!="Bearer synthetic-admin") return MockResponse().setResponseCode(401)
                val parsed=JsonParser.parseString(request.body.readUtf8()).asJsonObject
                if(parsed.number("schema_version")!=1L || !parsed.has("items")) return MockResponse().setResponseCode(400)
                kv=parsed;puts++;return MockResponse().setBody("""{"ok":true}""")
            };return MockResponse().setResponseCode(404)
        }}
        server.start();try {
            val remote=HttpAdminRemote(AdminConfig(server.url("/").toString().trimEnd('/'),"synthetic-admin"),OkHttpClient())
            val store=LegacyFixtures.Store();val repo=AdminRepository(store);repo.refresh(remote);repo.saveItem(LegacyFixtures.item("p","pelicula"));repo.publish(remote)
            assertEquals(1,puts);assertEquals(1,CatalogRules.items(remote.download()).size);assertFalse(repo.pending);assertEquals(kv,repo.state.get("base"))
        } finally {server.shutdown()}
    }
    @Test fun writesSyntheticProjectionFixtureForExactWorkerSourceHarness() {
        val base=LegacyFixtures.catalog(LegacyFixtures.item(),LegacyFixtures.item("p","pelicula"),LegacyFixtures.item("a","anime"))
        val raw=base.deepCopy();raw.array("items")[0]=LegacyFixtures.marked(raw.array("items")[0].asJsonObject)
        val store=LegacyFixtures.Store();store.data=LegacyFixtures.state(raw,base);val repo=AdminRepository(store);val remote=LegacyFixtures.Remote(base);repo.refresh(remote);repo.publish(remote)
        val out=File("build/retired-worker-projection.json");out.parentFile!!.mkdirs();out.writeText(JsonObject().apply {add("baseline",base);add("publication",remote.root);addProperty("original_private_backup_exists",store.archives.isNotEmpty())}.toString())
        assertFalse(RetiredMetadata.contains(remote.root));assertTrue(store.archives.isNotEmpty())
    }
}
