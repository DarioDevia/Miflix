package ar.com.miflix.admin

import com.google.gson.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.TimeUnit

class RecoveryProvidersTest {
    private fun item(type: String="serie")=JsonObject().apply {addProperty("tipo",type);addProperty("titulo","Serie");addProperty("id","local");addProperty("anio","2019")}
    private fun maze(server: MockWebServer,client: OkHttpClient=OkHttpClient.Builder().callTimeout(2,TimeUnit.SECONDS).followRedirects(false).build()) =
        TvmazeRecoveryProvider(client,server.url("/").toString().trimEnd('/'),ProviderRateLimit())
    private fun show(id: Int=5)="""{"id":$id,"name":"Serie $id","url":"https://www.tvmaze.com/shows/$id/serie","premiered":"2019-01-01","summary":"<p>General</p>","externals":{"imdb":"tt123"}}"""
    @Test fun ambiguousSearchReturnsAllCandidatesWithoutSelecting() {
        val server=MockWebServer();server.start();try {
            server.enqueue(MockResponse().setBody("""[{"show":${show(5)}},{"show":${show(6)}}]"""))
            val before=item();val found=maze(server).search(before,"Serie á")
            assertEquals(listOf(5L,6L),found.map {it.id});assertEquals("Serie",before.text("titulo"));assertEquals("2019",found[0].year)
            val req=server.takeRequest();assertTrue(req.path!!.startsWith("/search/shows?q="));assertNull(req.getHeader("Authorization"));assertTrue(req.getHeader("User-Agent")!!.contains("MiFlix"))
        } finally {server.shutdown()}
    }
    @Test fun noResultsReturnsEmptyList() {val server=MockWebServer();server.start();try{server.enqueue(MockResponse().setBody("[]"));assertTrue(maze(server).search(item(),"Absent").isEmpty())}finally{server.shutdown()}}
    @Test fun imdbLookupRedirectIsValidatedAndPreferred() {
        val server=MockWebServer();server.start();try {
            server.enqueue(MockResponse().setResponseCode(301).addHeader("Location",server.url("/shows/5")))
            server.enqueue(MockResponse().setBody(show()))
            assertEquals(5L,maze(server).search(item().apply {addProperty("imdb_id","tt123")},"Wrong name").single().id)
            assertEquals("/lookup/shows?imdb=tt123",server.takeRequest().path);assertEquals("/shows/5",server.takeRequest().path)
        } finally {server.shutdown()}
    }
    @Test fun missingExternalLookupFallsBackToCandidateSearch() {val server=MockWebServer();server.start();try{server.enqueue(MockResponse().setResponseCode(404));server.enqueue(MockResponse().setBody("[]"));assertTrue(maze(server).search(item().apply {addProperty("imdb_id","tt123")},"Serie").isEmpty());assertEquals(2,server.requestCount)}finally{server.shutdown()}}
    @Test fun redirectToAnotherHostIsRejectedWithoutFollowing() {val server=MockWebServer();server.start();try{server.enqueue(MockResponse().setResponseCode(301).addHeader("Location","https://evil.test/shows/5"));assertThrows(IllegalStateException::class.java){maze(server).search(item().apply {addProperty("imdb_id","tt123")},"Serie")};assertEquals(1,server.requestCount)}finally{server.shutdown()}}
    @Test fun unexpectedHttpIsVisible() {val server=MockWebServer();server.start();try{server.enqueue(MockResponse().setResponseCode(503));val e=assertThrows(IllegalStateException::class.java){maze(server).search(item(),"Serie")};assertTrue(e.message!!.contains("503"))}finally{server.shutdown()}}
    @Test fun timeoutIsRecoverableAndBounded() {val server=MockWebServer();server.start();try{server.enqueue(MockResponse().setHeadersDelay(1,TimeUnit.SECONDS).setBody("[]"));val client=OkHttpClient.Builder().callTimeout(100,TimeUnit.MILLISECONDS).build();val e=assertThrows(IllegalStateException::class.java){maze(server,client).search(item(),"Serie")};assertTrue(e.message!!.contains("tiempo"))}finally{server.shutdown()}}
    @Test fun malformedJsonIsNotPresentedAsAnEmptySuccess() {val server=MockWebServer();server.start();try{server.enqueue(MockResponse().setBody("<html>wrong</html>"));assertThrows(IllegalStateException::class.java){maze(server).search(item(),"Serie")}}finally{server.shutdown()}}
    @Test fun http429DoesNotStartRetryLoop() {val server=MockWebServer();server.start();try{server.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After","30"));val e=assertThrows(IllegalStateException::class.java){maze(server).search(item(),"Serie")};assertTrue(e.message!!.contains("429"));assertEquals(1,server.requestCount)}finally{server.shutdown()}}
    @Test fun localRateLimitAndBackoffAreEnforced() {var time=1000L;val limit=ProviderRateLimit {time};limit.acquire();assertThrows(IllegalStateException::class.java){limit.acquire()};time+=600;limit.acquire();limit.backoff(10);time+=600;assertThrows(IllegalStateException::class.java){limit.acquire()};time+=10000;limit.acquire()}
    @Test fun moviesNeverSentToTvmaze() {val server=MockWebServer();server.start();try{assertThrows(IllegalArgumentException::class.java){maze(server).search(item("pelicula"),"Movie")};assertEquals(0,server.requestCount)}finally{server.shutdown()}}
    @Test fun cancelledProviderDoesNotIssueRequests() {val server=MockWebServer();server.start();try{val provider=maze(server);provider.cancel();assertThrows(IllegalStateException::class.java){provider.search(item(),"Serie")};assertEquals(0,server.requestCount)}finally{server.shutdown()}}
    @Test fun retrievalRetainsRawNumberingAndDoesNotInventSeasonOrBackgroundData() {
        val server=MockWebServer();server.start();try {
            server.enqueue(MockResponse().setBody(show()));server.enqueue(MockResponse().setBody("""[{"id":71,"season":1,"number":1,"type":"regular","name":"Árbol","summary":"<p>Real</p>","runtime":42,"airdate":"2019-01-01"},{"id":72,"season":1,"number":null,"type":"significant_special","name":"Especial"}]"""))
            server.enqueue(MockResponse().setBody("[]"));server.enqueue(MockResponse().setBody("[]"))
            val c=RecoveryCandidate("TVmaze",5,"Serie","2019","https://www.tvmaze.com/shows/5","English",JsonObject())
            val data=maze(server).retrieve(c,item(),setOf(1,0),"en-US")
            assertFalse(data.fields.has("backdrop_url"));assertFalse(data.seasons.first {it.number("numero")==1L}.has("nombre"))
            val eps=data.seasons.first {it.number("numero")==1L}.array("episodios")
            assertEquals(2,eps.size());assertTrue(eps[1].asJsonObject.get("numero").isJsonNull)
            assertEquals(0,data.seasons.first {it.number("numero")==0L}.array("episodios").size())
        } finally {server.shutdown()}
    }
    @Test fun tmdbUsesExistingIdentityAndLanguageThroughEstablishedRelay() {
        val server=MockWebServer();server.start();try {
            server.enqueue(MockResponse().setBody("""{"id":42,"name":"Serie","overview":"English synopsis","poster_path":null}"""))
            server.enqueue(MockResponse().setBody("""{"season_number":1,"episodes":[{"id":11,"season_number":1,"episode_number":1,"name":"Real","overview":"Synopsis","runtime":42}]}"""))
            val provider=TmdbRecoveryProvider(HttpMetadata(AdminConfig(server.url("/").toString().trimEnd('/'),"fake-test-token")))
            val old=item().apply {addProperty("tmdb_id",42)}
            val candidates=provider.search(old,"Ignored name");assertEquals(0,server.requestCount)
            val data=provider.retrieve(candidates.single(),old,setOf(1),"en-US")
            assertEquals("English synopsis",data.fields.text("sinopsis"));assertEquals(11L,data.seasons.single().array("episodios")[0].asJsonObject.number("tmdb_id"))
            val root=server.takeRequest();assertEquals("/admin/metadata/tmdb/tv/42?language=en-US",root.path);assertEquals("Bearer fake-test-token",root.getHeader("Authorization"))
            assertEquals("/admin/metadata/tmdb/tv/42/season/1?language=en-US",server.takeRequest().path)
        } finally {server.shutdown()}
    }
    @Test fun tmdbRejectsSeasonWithDifferentNumber() {val server=MockWebServer();server.start();try{server.enqueue(MockResponse().setBody("{}"));server.enqueue(MockResponse().setBody("""{"season_number":2}"""));val provider=TmdbRecoveryProvider(HttpMetadata(AdminConfig(server.url("/").toString().trimEnd('/'),"fake")));val old=item().apply{addProperty("tmdb_id",42)};assertThrows(IllegalArgumentException::class.java){provider.retrieve(provider.search(old,"Serie").single(),old,setOf(1),"en-US")}}finally{server.shutdown()}}
    @Test fun tmdbUnsupportedLanguageOrPathCannotLeaveClient() {val metadata=HttpMetadata(AdminConfig("https://worker.invalid","fake"));assertThrows(IllegalArgumentException::class.java){metadata.recoveryRequest("/tv/42","bad / language")};assertThrows(IllegalArgumentException::class.java){metadata.recoveryRequest("https://evil.test","en-US")}}
    @Test fun tvmazeSeasonArtworkAndBackgroundAreOfferedOnlyWhenReturned() {
        val server=MockWebServer();server.start();try {
            server.enqueue(MockResponse().setBody(show()));server.enqueue(MockResponse().setBody("[]"))
            server.enqueue(MockResponse().setBody("""[{"number":1,"name":"Named season","summary":"<p>Season synopsis</p>","image":{"original":"https://image.test/season.jpg"}}]"""))
            server.enqueue(MockResponse().setBody("""[{"id":1,"type":"background","main":true,"resolutions":{"original":{"url":"https://image.test/back.jpg"}}}]"""))
            val c=RecoveryCandidate("TVmaze",5,"Serie","2019","https://www.tvmaze.com/shows/5","English",JsonObject())
            val out=maze(server).retrieve(c,item(),setOf(1),"en-US")
            assertEquals("Named season",out.seasons.single().text("nombre"));assertEquals("https://image.test/back.jpg",out.fields.text("backdrop_url"));assertEquals("https://image.test/season.jpg",out.seasons.single().text("poster_url"))
        }finally{server.shutdown()}
    }
}
