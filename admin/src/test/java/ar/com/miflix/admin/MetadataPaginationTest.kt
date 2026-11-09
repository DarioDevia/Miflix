package ar.com.miflix.admin

import com.google.gson.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class MetadataPaginationTest {
    private fun query(type:String="pelicula",source:String="TMDB",year:String="")=MetadataQuery.create("Camino",type,source,year)
    private fun result(id:Int,source:String="TMDB")=SearchResult(source,id.toLong(),"Título $id","2008","",JsonObject())
    private open class Fake:Metadata {
        val pages=mutableMapOf<Int,MetadataPage>();val calls=mutableListOf<MetadataQuery>();val numbers=mutableListOf<Int>()
        var failure:Exception?=null;var callback:(()->Unit)?=null;var cancellations=0
        override fun search(query:String,type:String,source:String)=error("Legacy search must not be used")
        override fun detail(result:SearchResult,type:String)=error("Search never requests details automatically")
        override fun season(id:Long,number:Int)=error("Search never requests seasons")
        override fun searchPage(query:MetadataQuery,page:Int):MetadataPage {calls.add(query);numbers.add(page);callback?.invoke();failure?.let {throw it};return pages.getValue(page)}
        override fun cancelSearch() {cancellations++}
    }
    private fun fake(vararg sizes:Int,source:String="TMDB"):Fake=Fake().apply {
        var id=1
        sizes.forEachIndexed {i,size->pages[i+1]=MetadataPage(List(size) {result(id++,source)},i+1,i<sizes.lastIndex)}
    }
    @Test fun tenInitialTmdbResultsBufferRemainingTen() {val f=fake(20,5);val p=MetadataPager(query(),f);assertEquals((1L..10L).toList(),p.load().map {it.id});assertEquals(listOf(1),f.numbers);assertTrue(p.hasMore)}
    @Test fun secondTenUseBufferWithoutNetwork() {val f=fake(20,5);val p=MetadataPager(query(),f);p.load();assertEquals((11L..20L).toList(),p.load().map {it.id});assertEquals(listOf(1),f.numbers);assertTrue(p.hasMore)}
    @Test fun thirdGroupRequestsPageTwoAndEndsOnlyAfterBufferIsConsumed() {val f=fake(20,20);val p=MetadataPager(query(),f);repeat(3) {assertEquals(10,p.load().size)};assertTrue(p.hasMore);assertEquals(10,p.load().size);assertFalse(p.hasMore);assertTrue(p.load().isEmpty());assertEquals(listOf(1,2),f.numbers)}
    @Test fun incompleteFinalPagePreservesEveryResult() {val f=fake(20,3);val p=MetadataPager(query(),f);val all=(1..3).flatMap {p.load()};assertEquals(23,all.size);assertFalse(p.hasMore)}
    @Test fun shortIntermediatePageDoesNotMeanEnd() {val f=fake(3,2);val p=MetadataPager(query(),f);assertEquals(3,p.load().size);assertTrue(p.hasMore);assertEquals(2,p.load().size);assertFalse(p.hasMore)}
    @Test fun seriesHaveTheirOwnQueryAndCursor() {val f=fake(20,1);val p=MetadataPager(query("serie"),f);repeat(3){p.load()};assertEquals(listOf(1,2),f.numbers);assertTrue(f.calls.all {it.type=="serie"})}
    @Test fun animeTmdbKeepsCategoryIdentity() {val f=fake(20,1);val p=MetadataPager(query("anime"),f);repeat(3){p.load()};assertTrue(f.calls.all {it.type=="anime" && it.source=="TMDB"})}
    @Test fun anilistPagesAreTenEach() {val f=fake(10,10,2,source="AniList");val p=MetadataPager(query("anime","AniList"),f);assertEquals(listOf(10,10,2),(1..3).map {p.load().size});assertEquals(listOf(1,2,3),f.numbers);assertFalse(p.hasMore)}
    @Test fun emptyTerminalPageIsNotAnError() {val p=MetadataPager(query(),fake(0));assertTrue(p.load().isEmpty());assertFalse(p.hasMore)}
    @Test fun duplicatedIdsWithinAndBetweenPagesAreNotShownTwice() {val f=fake(3,3);f.pages[1]=MetadataPage(listOf(result(1),result(1),result(2)),1,true);f.pages[2]=MetadataPage(listOf(result(2),result(3),result(3)),2,false);val p=MetadataPager(query(),f);assertEquals(listOf(1L,2L,3L),p.load().plus(p.load()).map {it.id});assertFalse(p.hasMore)}
    @Test fun repeatedNonemptyPageDoesNotAdvanceOrFalselyEnd() {val f=fake(3,3);f.pages[2]=f.pages[1]!!.copy(page=2,hasNextPage=false);val p=MetadataPager(query(),f);p.load();assertThrows(IllegalStateException::class.java){p.load()};assertTrue(p.hasMore);f.pages[2]=MetadataPage(listOf(result(4)),2,false);assertEquals(4L,p.load().single().id);assertEquals(listOf(1,2,2),f.numbers)}
    @Test fun wrongPageNumberRetainsCursorForRetry() {val f=fake(2,1);val p=MetadataPager(query(),f);p.load();f.pages[2]=f.pages[2]!!.copy(page=1);assertThrows(IllegalStateException::class.java){p.load()};assertThrows(IllegalStateException::class.java){p.load()};assertEquals(listOf(1,2,2),f.numbers)}
    @Test fun emptyNonterminalPageIsAnErrorNotFalseExhaustion() {val f=fake(1,0,1);val p=MetadataPager(query(),f);p.load();assertThrows(IllegalStateException::class.java){p.load()};assertTrue(p.hasMore)}
    @Test fun failureRetriesSamePageWithoutRemovingPreviousResults() {val f=fake(2,1);val p=MetadataPager(query(),f);assertEquals(2,p.load().size);f.failure=IllegalStateException("429");assertThrows(IllegalStateException::class.java){p.load()};f.failure=null;assertEquals(3L,p.load().single().id);assertEquals(listOf(1,2,2),f.numbers)}
    @Test fun wrongSourceIsRejectedWithoutAdvancing() {val f=fake(1);f.pages[1]=MetadataPage(listOf(result(1,"AniList")),1,false);val p=MetadataPager(query(),f);assertThrows(IllegalStateException::class.java){p.load()};assertTrue(p.hasMore)}
    @Test fun validAndEmptyYearAreOptional() {assertNull(query().year);assertEquals(2008,query(year="2008").year);assertEquals(1000,query(year="1000").year);assertEquals(9999,query(year="9999").year)}
    @Test fun invalidYearAndEmptyTitleNeverBecomeQueries() {for(y in listOf("08","20080","2o08"," 2008","0000","0999")) assertThrows(IllegalArgumentException::class.java){query(year=y)};assertThrows(IllegalArgumentException::class.java){MetadataQuery.create(" ","pelicula","TMDB","")}}
    @Test fun controllerAppendsAndPreventsParallelClicks()=runTest {
        val f=fake(20,3);val c=MetadataSearchController(backgroundScope,{f},StandardTestDispatcher(testScheduler));c.query="Camino";c.search("pelicula");c.loadMore();runCurrent();assertEquals(10,c.results.size);assertEquals(listOf(1),f.numbers)
        c.loadMore();c.loadMore();runCurrent();assertEquals(20,c.results.size);c.loadMore();runCurrent();assertEquals(23,c.results.size);assertFalse(c.hasMore);c.loadMore();runCurrent();assertEquals(listOf(1,2),f.numbers)
    }
    @Test fun controllerFailureRetainsResultsAndAllowsRetry()=runTest {
        val f=fake(10,1);val c=MetadataSearchController(backgroundScope,{f},StandardTestDispatcher(testScheduler));c.query="Camino";c.search("pelicula");runCurrent();val before=c.results
        f.failure=IllegalStateException("HTTP 429");c.loadMore();runCurrent();assertEquals(before,c.results);assertTrue(c.error.contains("429"));assertTrue(c.hasMore)
        f.failure=null;c.loadMore();runCurrent();assertEquals(11,c.results.size);assertTrue(c.error.isEmpty());assertFalse(c.hasMore)
    }
    @Test fun titleYearAndSourceEditsInvalidateWithoutQuerying()=runTest {
        val f=fake(10,1);val c=MetadataSearchController(backgroundScope,{f},StandardTestDispatcher(testScheduler));c.query="Camino";c.search("anime");runCurrent()
        c.year="2008";assertTrue(c.results.isEmpty());assertFalse(c.completed);assertFalse(c.hasMore);c.source="AniList";c.query="Nuevo";runCurrent();assertEquals(1,f.calls.size)
    }
    @Test fun sourceSwitchRestartsAtPageOneAndNeverMixesSources()=runTest {
        val a=fake(20);val b=fake(10,source="AniList");var active:Metadata=a
        val c=MetadataSearchController(backgroundScope,{active},StandardTestDispatcher(testScheduler));c.query="Anime";c.search("anime");runCurrent();active=b;c.source="AniList";c.search("anime");runCurrent()
        assertEquals(10,c.results.size);assertTrue(c.results.all {it.source=="AniList"});assertEquals(listOf(1),b.numbers)
    }
    @Test fun responseInvalidatedDuringFetchCannotReplaceNewQuery()=runTest {
        val f=fake(10);lateinit var c:MetadataSearchController
        c=MetadataSearchController(backgroundScope,{f},StandardTestDispatcher(testScheduler));f.callback={c.query="Otra búsqueda"};c.query="Camino";c.search("pelicula");runCurrent()
        assertEquals("Otra búsqueda",c.query);assertTrue(c.results.isEmpty());assertFalse(c.loading);assertFalse(c.completed)
    }
    @Test fun leavingDuringRequestDiscardsUncertainCursorWithoutFalseEnd()=runTest {
        val f=fake(10);lateinit var c:MetadataSearchController;c=MetadataSearchController(backgroundScope,{f},StandardTestDispatcher(testScheduler));f.callback={c.pause()};c.query="Camino";c.search("pelicula");runCurrent()
        assertTrue(c.results.isEmpty());assertFalse(c.completed);assertTrue(c.error.contains("interrumpida"));assertTrue(f.cancellations>0)
    }
    @Test fun invalidYearKeepsTitleAndDoesNotCreateProvider()=runTest {
        var calls=0;val c=MetadataSearchController(backgroundScope,{calls++;fake(1)},StandardTestDispatcher(testScheduler));c.query="Camino";c.year="20x8";c.search("pelicula");runCurrent()
        assertEquals("Camino",c.query);assertTrue(c.error.contains("año"));assertEquals(0,calls)
    }

    private fun moviePage(page:Int,total:Int,ids:IntRange)=JsonObject().apply {addProperty("page",page);addProperty("total_pages",total);add("results",JsonArray().apply {ids.forEach {id->add(JsonObject().apply {addProperty("id",id);addProperty("title","Título $id");addProperty("name","Serie $id");addProperty("release_date","2008-01-01");addProperty("first_air_date","2008-01-01")})}})}.toString()
    private fun metadata(server:MockWebServer,direct:Boolean=true,client:OkHttpClient=OkHttpClient())=HttpMetadata(AdminConfig(server.url("/").toString().trimEnd('/'),"fake-admin",if(direct) "fake-tmdb" else ""),client,tmdbBase=server.url("/3").toString(),anilistUrl=server.url("/graphql").toString())
    @Test fun tmdbUsesAuthenticPagesAndMovieYearWithoutDiscardingTwenty() {val s=MockWebServer();s.start();try {s.enqueue(MockResponse().setBody(moviePage(2,3,21..40)));val page=metadata(s).searchPage(query(year="2008"),2);assertEquals(20,page.items.size);assertTrue(page.hasNextPage);val r=s.takeRequest();assertEquals("/3/search/movie",r.requestUrl!!.encodedPath);assertEquals("2",r.requestUrl!!.queryParameter("page"));assertEquals("2008",r.requestUrl!!.queryParameter("primary_release_year"));assertEquals("es-MX",r.requestUrl!!.queryParameter("language"));assertEquals("false",r.requestUrl!!.queryParameter("include_adult"))}finally{s.shutdown()}}
    @Test fun seriesAndAnimeTmdbUseTvAndFirstAirDateYear() {val s=MockWebServer();s.start();try {for(type in listOf("serie","anime")) {s.enqueue(MockResponse().setBody(moviePage(1,1,1..1)));metadata(s).searchPage(query(type,year="2008"),1);val r=s.takeRequest();assertEquals("/3/search/tv",r.requestUrl!!.encodedPath);assertEquals("2008",r.requestUrl!!.queryParameter("first_air_date_year"));assertNull(r.requestUrl!!.queryParameter("primary_release_year"))}}finally{s.shutdown()}}
    @Test fun emptyYearIsNotSent() {val s=MockWebServer();s.start();try{s.enqueue(MockResponse().setBody(moviePage(1,1,1..1)));metadata(s).searchPage(query(),1);val u=s.takeRequest().requestUrl!!;assertNull(u.queryParameter("primary_release_year"));assertNull(u.queryParameter("year"))}finally{s.shutdown()}}
    @Test fun legacyRelayKeepsImplicitFirstPageButNeverDropsYearOrLaterPage() {val s=MockWebServer();s.start();try {s.enqueue(MockResponse().setBody(moviePage(1,2,1..20)));val m=metadata(s,false);assertEquals(20,m.searchPage(query(),1).items.size);assertNull(s.takeRequest().requestUrl!!.queryParameter("page"));for(q in listOf(query(),query(year="2008"))) {s.enqueue(MockResponse().setResponseCode(400));val e=assertThrows(IllegalStateException::class.java){m.searchPage(q,if(q.year==null) 2 else 1)};assertTrue(e.message!!.contains("TMDB Bearer Token"));val u=s.takeRequest().requestUrl!!;if(q.year==null) assertEquals("2",u.queryParameter("page")) else assertEquals("2008",u.queryParameter("primary_release_year"))}}finally{s.shutdown()}}
    @Test fun anilistUsesPageInfoTenAndYearPrefixWithOriginalDetailFields() {val s=MockWebServer();s.start();try {s.enqueue(MockResponse().setBody("""{"data":{"Page":{"pageInfo":{"currentPage":2,"hasNextPage":true},"media":[{"id":55,"title":{"english":"Anime"},"startDate":{"year":2008},"coverImage":{"extraLarge":"https://example.org/a"}}]}}}"""));val p=metadata(s).searchPage(query("anime","AniList","2008"),2);assertEquals("2008",p.items.single().year);assertTrue(p.hasNextPage);val r=s.takeRequest();assertNull(r.getHeader("Authorization"));val body=JsonParser.parseString(r.body.readUtf8()).asJsonObject;val vars=body.getAsJsonObject("variables");assertEquals(2,vars.get("page").asInt);assertEquals("2008%",vars.text("year"));assertTrue(body.text("query").contains("perPage: 10"));assertTrue(body.text("query").contains("hasNextPage"));assertTrue(body.text("query").contains("startDate_like"));assertTrue(body.text("query").contains("staff(perPage: 15)"))}finally{s.shutdown()}}
    @Test fun anilistEmptyYearDoesNotSendFilter() {val s=MockWebServer();s.start();try{s.enqueue(MockResponse().setBody("""{"data":{"Page":{"pageInfo":{"hasNextPage":false},"media":[]}}}"""));assertFalse(metadata(s).searchPage(query("anime","AniList"),1).hasNextPage);assertFalse(JsonParser.parseString(s.takeRequest().body.readUtf8()).asJsonObject.getAsJsonObject("variables").has("year"))}finally{s.shutdown()}}
    @Test fun malformedMissingPaginationAndGraphqlErrorsAreNotEmptySuccess() {val s=MockWebServer();s.start();try {val m=metadata(s);for(raw in listOf("", "{}", "[]", "{bad", "{\"page\":1,\"results\":[]}",moviePage(1,0,1..1),moviePage(2,2,1..1))) {s.enqueue(MockResponse().setBody(raw));assertThrows(IllegalStateException::class.java){m.searchPage(query(),1)}};for(raw in listOf("""{"errors":[{"message":"private details must not be shown"}]}""","""{"data":{"Page":{"media":[]}}}""","""{"data":{"Page":{"pageInfo":{"hasNextPage":null},"media":[]}}}""")) {s.enqueue(MockResponse().setBody(raw));val e=assertThrows(IllegalStateException::class.java){m.searchPage(query("anime","AniList"),1)};assertFalse(e.message.orEmpty().contains("private details"))}}finally{s.shutdown()}}
    @Test fun httpFailuresAnd429HaveNoAutomaticRetries() {val s=MockWebServer();s.start();try {val m=metadata(s);for(status in listOf(401,404,429,500)) {s.enqueue(MockResponse().setResponseCode(status));assertThrows(IllegalStateException::class.java){m.searchPage(query(),1)}};assertEquals(4,s.requestCount)}finally{s.shutdown()}}
    @Test fun timeoutRetainsPagerCursorForManualRetry() {val s=MockWebServer();s.start();try {s.enqueue(MockResponse().setHeadersDelay(1,TimeUnit.SECONDS).setBody(moviePage(1,1,1..1)));val m=metadata(s,client=OkHttpClient.Builder().callTimeout(80,TimeUnit.MILLISECONDS).build());val p=MetadataPager(query(),m);assertThrows(IllegalStateException::class.java){p.load()};assertTrue(p.hasMore);s.enqueue(MockResponse().setBody(moviePage(1,1,1..1)));assertEquals(1L,p.load().single().id);assertFalse(p.hasMore)}finally{s.shutdown()}}
    @Test fun anilistRealDepthLimitIsExplicitAndDoesNotSendImpossibleRequest() {
        val s=MockWebServer();s.start();try {
            val error=assertThrows(IllegalArgumentException::class.java){metadata(s).searchPage(query("anime","AniList"),501)}
            assertTrue(error.message!!.contains("5.000"));assertEquals(0,s.requestCount)
        } finally {s.shutdown()}
    }

    @Test fun olderResponseCannotOverwriteAnAlreadyStartedNewSearch()=runTest {
        val old=fake(10);val fresh=fake(1).apply {pages[1]=MetadataPage(listOf(result(99)),1,false)}
        var active:Metadata=old;lateinit var c:MetadataSearchController
        c=MetadataSearchController(backgroundScope,{active},StandardTestDispatcher(testScheduler))
        old.callback={active=fresh;c.query="Nueva consulta";c.search("pelicula")}
        c.query="Anterior";c.search("pelicula");runCurrent()
        assertEquals(listOf(99L),c.results.map {it.id});assertEquals("Nueva consulta",c.query);assertFalse(c.loading);assertTrue(c.completed)
    }

    @Test fun resultsCanContinuePastFiftyWithoutArtificialCap() {
        val f=fake(*IntArray(8){20});val pager=MetadataPager(query(),f);val ids=mutableListOf<Long>()
        while(pager.hasMore) ids.addAll(pager.load().map {it.id})
        assertEquals((1L..160L).toList(),ids);assertEquals((1..8).toList(),f.numbers)
    }

}
