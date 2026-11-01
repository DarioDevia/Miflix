package ar.com.miflix.admin

import com.google.gson.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.util.concurrent.TimeUnit

internal object SyncFixtures {
    fun row(id:String="predator",name:String="Depredador")=CollectionRules.fresh().apply {
        addProperty("id",id);addProperty("nombre",name);addProperty("poster_url","https://images.example/p.jpg")
        addProperty("backdrop_url","https://images.example/b.jpg");addProperty("orden",7)
        add("titulos",JsonArray().apply {listOf("p1","p2","p3","p4","p5").forEach {add(it)}})
    }
    fun doc(vararg rows:JsonObject)=CollectionRules.empty().apply {add("colecciones",JsonArray().apply {rows.forEach {add(it.deepCopy())}})}
    fun ack(count:Int)= """{"ok":true,"mensaje":"OK","colecciones":$count,"updated_at":"2026-10-31T20:00:00Z"}"""
}

class CollectionSyncTest {
    @Test fun comparisonUsesIdsAndCompleteContent() {
        val old=SyncFixtures.row();val changed=old.deepCopy().apply {addProperty("orden",9)}
        val review=CollectionSync.review(SyncFixtures.doc(changed,SyncFixtures.row("add")),SyncFixtures.doc(old,SyncFixtures.row("remove")))
        assertEquals(setOf("predator","add","remove"),review.changes.map {it.id}.toSet())
        assertEquals(listOf("orden"),review.changes.first {it.id=="predator"}.fields);assertTrue(review.destructive)
    }
    @Test fun objectPropertyOrderDoesNotCauseFalseDifference() {
        val a=SyncFixtures.row();val b=JsonObject().apply {a.entrySet().reversed().forEach {add(it.key,it.value)}}
        assertTrue(CollectionSync.review(SyncFixtures.doc(a),SyncFixtures.doc(b)).changes.isEmpty())
    }
    @Test fun memberOrderIsARealChange() {
        val a=SyncFixtures.row();val b=CollectionRules.members(a,CollectionRules.ids(a).reversed())
        assertEquals(listOf("titulos"),CollectionSync.review(SyncFixtures.doc(a),SyncFixtures.doc(b)).changes.single().fields)
    }
    @Test fun rootOrderIsReviewedAndRequiresConfirmation() {
        val a=SyncFixtures.row();val b=SyncFixtures.row("other");val local=SyncFixtures.doc(a,b)
        val r=CollectionSync.review(local,SyncFixtures.doc(b,a));assertTrue(r.orderChanged);assertTrue(r.destructive)
        assertThrows(IllegalStateException::class.java){CollectionSync.authorize(r,local,false,false)}
    }
    @Test fun emptyRemoteAllowsNonDestructiveFirstPublication() {
        val local=SyncFixtures.doc(SyncFixtures.row());val review=CollectionSync.review(local,SyncFixtures.doc())
        assertFalse(review.destructive);assertEquals(local,CollectionSync.authorize(review,local,false,false))
    }
    @Test fun destructiveReplacementRequiresExplicitApproval() {
        val local=SyncFixtures.doc(SyncFixtures.row());val r=CollectionSync.review(local,SyncFixtures.doc(SyncFixtures.row("lost")))
        assertThrows(IllegalStateException::class.java){CollectionSync.authorize(r,local,false,false)}
        assertEquals(local,CollectionSync.authorize(r,local,true,false))
    }
    @Test fun emptyPublicationNeedsSeparateExplicitApproval() {
        val local=SyncFixtures.doc();val r=CollectionSync.review(local,SyncFixtures.doc(SyncFixtures.row()))
        assertThrows(IllegalStateException::class.java){CollectionSync.authorize(r,local,true,false)}
        assertEquals(local,CollectionSync.authorize(r,local,true,true))
    }
    @Test fun emptyToEmptyStillRequiresEmptyConfirmation() {
        val empty=SyncFixtures.doc();val r=CollectionSync.review(empty,empty)
        assertThrows(IllegalStateException::class.java){CollectionSync.authorize(r,empty,false,false)}
    }
    @Test fun localChangesAfterReviewAreRejected() {
        val local=SyncFixtures.doc(SyncFixtures.row());val r=CollectionSync.review(local,SyncFixtures.doc())
        assertThrows(IllegalStateException::class.java){CollectionSync.authorize(r,SyncFixtures.doc(),true,true)}
    }
    @Test fun recoveryOnlyMergesSelectedIdsAndPreservesUnknownFields() {
        val a=SyncFixtures.row().apply {addProperty("future",77)};val b=SyncFixtures.row("other")
        val local=SyncFixtures.doc(a,b);val remote=SyncFixtures.doc(SyncFixtures.row("new"),SyncFixtures.row("other","Changed"))
        val merged=CollectionSync.merge(local,remote,setOf("new"));assertEquals(a,CollectionRules.list(merged)[0]);assertEquals(b,CollectionRules.list(merged)[1]);assertEquals("new",CollectionRules.list(merged)[2].text("id"));assertEquals(2,CollectionRules.list(local).size)
    }
    @Test fun recoveryCanExplicitlyReplaceOneIdWithoutDeletingOthers() {
        val local=SyncFixtures.doc(SyncFixtures.row(),SyncFixtures.row("keep"));val remote=SyncFixtures.doc(SyncFixtures.row("predator","Changed"))
        val merged=CollectionSync.merge(local,remote,setOf("predator"));assertEquals("Changed",CollectionRules.list(merged)[0].text("nombre"));assertEquals("keep",CollectionRules.list(merged)[1].text("id"))
    }
    @Test fun recoveryRejectsUnknownSelection() {assertThrows(IllegalArgumentException::class.java){CollectionSync.merge(SyncFixtures.doc(),SyncFixtures.doc(),setOf("missing"))}}
    @Test fun invalidOrDuplicateCollectionsAreNeverPublished() {
        assertThrows(IllegalArgumentException::class.java){CollectionSync.document(SyncFixtures.doc(SyncFixtures.row(),SyncFixtures.row()))}
        assertThrows(IllegalArgumentException::class.java){CollectionSync.document(SyncFixtures.doc(SyncFixtures.row().apply {remove("visible")}))}
    }
    @Test fun javaScriptUnsafeTmdbIdsAreRejectedLocally() {
        assertThrows(IllegalArgumentException::class.java){CollectionSync.document(SyncFixtures.doc(SyncFixtures.row().apply {addProperty("tmdb_collection_id",9007199254740992L)}))}
    }
    @Test fun existingRetiredProviderImageProtectionIsPreserved() {assertThrows(IllegalArgumentException::class.java){CollectionSync.document(SyncFixtures.doc(SyncFixtures.row().apply {addProperty("poster_url","https://artworks.thetvdb.com/p.jpg")}))}}
    @Test fun noLocalEnvelopeDraftOrCredentialsInPublication() {
        val root=SyncFixtures.doc(SyncFixtures.row()).apply {addProperty("private","synthetic-secret")}
        assertEquals(setOf("format","version","colecciones"),CollectionSync.document(root).keySet())
    }
    @Test fun reviewIsDetachedFromMutableInputs() {
        val local=SyncFixtures.doc(SyncFixtures.row());val remote=SyncFixtures.doc();val r=CollectionSync.review(local,remote)
        local.getAsJsonArray("colecciones").remove(0);assertEquals(1,CollectionRules.list(r.local).size)
    }
    @Test fun writesRealAndroidProjectionForExactProductionWorkerHarness() {
        val local=SyncFixtures.doc(SyncFixtures.row().apply {addProperty("future_field","preserved")})
        val base=LegacyFixtures.catalog(LegacyFixtures.item("p1","pelicula"),LegacyFixtures.item("series","serie"),LegacyFixtures.item("anime","anime"))
        val publication=CatalogCollections.without(CatalogRules.publicCopy(base))
        File("build/collections-worker-contract.json").apply {parentFile!!.mkdirs();writeText(JsonObject().apply {add("collections",CollectionSync.document(local));add("catalog",base);add("publication",publication)}.toString())}
        assertFalse(publication.has("colecciones"));assertFalse(publication.has("colecciones_version"))
    }
}

class HttpCollectionsRemoteTest {
    private fun withServer(block:(MockWebServer,HttpCollectionsRemote)->Unit) {
        val server=MockWebServer();server.start()
        try {block(server,HttpCollectionsRemote(AdminConfig(server.url("/").toString().trimEnd('/'),"synthetic-admin"),OkHttpClient.Builder().callTimeout(1,TimeUnit.SECONDS).retryOnConnectionFailure(false).build()))} finally {server.shutdown()}
    }
    @Test fun authenticatedGetUsesExactProductionDocument()=withServer {s,r ->
        val doc=SyncFixtures.doc(SyncFixtures.row());s.enqueue(MockResponse().setBody(doc.toString()));assertEquals(doc,r.download())
        val req=s.takeRequest();assertEquals("GET",req.method);assertEquals("/admin/colecciones",req.path);assertEquals("Bearer synthetic-admin",req.getHeader("Authorization"));assertNull(req.getHeader("If-Match"))
    }
    @Test fun putUsesExactEnvelopeAndVerifiesServerAcknowledgment()=withServer {s,r ->
        s.enqueue(MockResponse().setBody(SyncFixtures.ack(1)));val doc=SyncFixtures.doc(SyncFixtures.row());assertEquals("2026-10-31T20:00:00Z",r.publish(doc))
        val req=s.takeRequest();assertEquals("PUT",req.method);assertEquals("/admin/colecciones",req.path);assertEquals(doc,JsonParser.parseString(req.body.readUtf8()));assertEquals("Bearer synthetic-admin",req.getHeader("Authorization"));assertNull(req.getHeader("Idempotency-Key"))
    }
    @Test fun emptyRemoteIsValid()=withServer {s,r ->s.enqueue(MockResponse().setBody(SyncFixtures.doc().toString()));assertTrue(CollectionRules.list(r.download()).isEmpty())}
    @Test fun allRequiredHttpErrorsHaveSafeSpecificMessages()=withServer {s,r ->
        for(code in listOf(401,403,400,404,409,413,503)) {
            s.enqueue(MockResponse().setResponseCode(code).setBody("synthetic-admin should never be echoed"))
            val e=assertThrows(IllegalStateException::class.java){r.download()};assertTrue(e.message!!.contains(code.toString()));assertFalse(e.message!!.contains("synthetic-admin"))
        }
    }
    @Test fun connectionFailureIsNotTreatedAsSuccess()=withServer {s,r ->s.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));assertThrows(IllegalStateException::class.java){r.download()}}
    @Test fun lostPutResponseDoesNotAutomaticallyRetry()=withServer {s,r ->
        s.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));assertThrows(IllegalStateException::class.java){r.publish(SyncFixtures.doc(SyncFixtures.row()))};assertEquals(1,s.requestCount)
    }
    @Test fun timeoutHasNoCredentialOrRawNetworkDetails()=withServer {s,r ->s.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));val e=assertThrows(IllegalStateException::class.java){r.download()};assertFalse(e.message!!.contains("synthetic-admin"))}
    @Test fun invalidJsonAndSchemaAreRejected()=withServer {s,r ->
        for(body in listOf("broken","{}","[]","""{"format":"miflix-collections","version":2,"colecciones":[]}""")) {s.enqueue(MockResponse().setBody(body));assertThrows(IllegalStateException::class.java){try {r.download()} catch(e:IllegalArgumentException){throw IllegalStateException(e)}}}
    }
    @Test fun falseMissingAndMismatchedAcknowledgmentsAreRejected()=withServer {s,r ->
        for(ack in listOf("{}","""{"ok":false}""",SyncFixtures.ack(2),"""{"ok":true,"colecciones":1,"updated_at":"not-a-date"}""")) {s.enqueue(MockResponse().setBody(ack));assertThrows(IllegalStateException::class.java){r.publish(SyncFixtures.doc(SyncFixtures.row()))}}
    }
    @Test fun oversizedResponseIsRejected()=withServer {s,r ->s.enqueue(MockResponse().setBody(" ".repeat(CollectionRules.MAX_BYTES+1)));assertThrows(IllegalStateException::class.java){r.download()}}
    @Test fun invalidLocalPayloadNeverReachesHttp()=withServer {s,r ->assertThrows(IllegalArgumentException::class.java){r.publish(SyncFixtures.doc(SyncFixtures.row().apply {remove("id")}))};assertEquals(0,s.requestCount)}
}
