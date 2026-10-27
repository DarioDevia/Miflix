package ar.com.miflix.admin

import com.google.gson.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class TvdbRecoveryProviderTest {
    private fun obj(raw: String)=JsonParser.parseString(raw).asJsonObject
    private fun item()=obj("""{"id":"local","tipo":"serie","titulo":"Serie","fecha_agregado":"2026-01-01T00:00:00Z","temporadas":[{"numero":1,"episodios":[{"numero":1,"titulo":"Episodio 1","telegram_url":"https://t.me/test/8","duracion":"42","imagen_url":"https://image.invalid/original.jpg"}]}]}""")
    private fun candidate(type: String="series")=RecoveryCandidate("TheTVDB",5,"Serie","2019","https://thetvdb.com/series/serie","spa",obj("""{"_kind":"$type"}"""))
    private fun season(number: Int=1,type: Int=1)="""{"id":10,"seriesId":5,"number":$number,"type":{"id":$type},"image":"https://image.invalid/season.jpg"}"""
    private fun root(number: Int=1,type: Int=1)="""{"id":5,"name":"Base English","overview":"English base","defaultSeasonType":1,"originalLanguage":"spa","seasons":[${season(number,type)}]}"""
    private fun episode(number: Int=1,season: Int=1,id: Int=8,series: Int=5)="""{"id":$id,"seriesId":$series,"seasonNumber":$season,"number":$number,"name":"Base English episode","overview":"English base episode","image":"https://image.invalid/new.jpg","runtime":42,"aired":"2019-08-16"}"""
    private fun enqueue(s: MockWebServer,data: String)=s.enqueue(MockResponse().setBody("""{"status":"success","data":$data}"""))
    private fun missing(s: MockWebServer)=s.enqueue(MockResponse().setResponseCode(404))
    private fun provider(s: MockWebServer): TvdbRecoveryProvider {
        enqueue(s,"""{"token":"synthetic-session"}""")
        return TvdbRecoveryProvider(TvdbApi({TvdbCredentials("synthetic-key")},OkHttpClient.Builder().callTimeout(1,TimeUnit.SECONDS).build(),s.url("/v4").toString(),spacingMs=0))
    }
    private fun withServer(test: (MockWebServer,TvdbRecoveryProvider)->Unit) {val s=MockWebServer();s.start();try{test(s,provider(s))}finally{s.shutdown()}}
    private fun seriesResponses(s: MockWebServer,ep: String=episode(),translation: String="""{"language":"spa","name":"El árbol","overview":"<p>Descripción &amp; acción.</p>"}""") {
        enqueue(s,root());enqueue(s,"""{"language":"spa","name":"Serie","overview":"Sinopsis española"}""");enqueue(s,season());missing(s)
        enqueue(s,"""{"series":{"id":5},"episodes":[$ep]}""");enqueue(s,translation)
    }
    @Test fun ambiguousSeriesSearchReturnsAllCandidates()=withServer {s,p ->
        enqueue(s,"""[{"tvdb_id":"5","type":"series","name":"Una","year":"2019"},{"tvdb_id":"6","type":"series","name":"Otra","year":"2020"},{"tvdb_id":"7","type":"movie","name":"Otra película"}]""")
        val old=item();val copy=old.deepCopy();val c=p.search(old,"Una")
        assertEquals(listOf(5L,6L),c.map{it.id});assertEquals(copy,old);assertEquals("2019",c[0].year)
        s.takeRequest();assertTrue(s.takeRequest().path!!.contains("type=series"))
    }
    @Test fun nonexistentReturnsEmpty()=withServer {s,p ->enqueue(s,"[]");assertTrue(p.search(item(),"No existe").isEmpty())}
    @Test fun imdbIsPreferredOverTextSearch()=withServer {s,p ->enqueue(s,"""[{"series":{"id":5,"name":"Identificada","year":"2019"}}]""");assertEquals(5L,p.search(item().apply{addProperty("imdb_id","tt12345")},"Wrong").single().id);s.takeRequest();assertEquals("/v4/search/remoteid/tt12345",s.takeRequest().path)}
    @Test fun tvdbIdentityIsValidated()=withServer {s,p ->enqueue(s,"""{"id":6,"name":"Otra"}""");assertThrows(IllegalStateException::class.java){p.search(item().apply {addProperty("tvdb_id",5)},"Serie")}}
    @Test fun incompleteSearchRejected()=withServer {s,p ->enqueue(s,"{}");assertThrows(IllegalStateException::class.java){p.search(item(),"Serie")}}
    @Test fun spanishTranslationsAndOnlySelectedSynopsisApplied()=withServer {s,p ->
        seriesResponses(s);val before=item();val plan=MetadataRecovery.compare(before,p.retrieve(candidate(),before,setOf(1),"spa"))
        val ep=plan.changes.single {it.path==RecoveryPath(1,1,"sinopsis")};assertEquals("Descripción & acción.",ep.after.asString);assertFalse(ep.initiallySelected)
        val selected=setOf(ep.path.key)
        assertThrows(IllegalArgumentException::class.java){MetadataRecovery.apply(plan,selected,emptySet(),true)}
        val after=MetadataRecovery.apply(plan,selected,setOf("1/1"),true)
        val old=MetadataRecovery.target(before,ep.path);val newer=MetadataRecovery.target(after,ep.path)
        for(field in listOf("titulo","duracion","imagen_url","telegram_url","numero")) assertEquals(old.get(field),newer.get(field))
        assertEquals(before.text("fecha_agregado"),after.text("fecha_agregado"));assertEquals("Episodio 1",old.text("titulo"));assertTrue(MetadataPublicationPolicy.containsTvdb(after))
        assertEquals(before,MetadataRecovery.undo(after,MetadataRecovery.backup(plan,selected,after)))
    }
    @Test fun absentSpanishNeverFallsBackToEnglish()=withServer {s,p ->seriesResponses(s,translation="""{"language":"eng","name":"English","overview":"English"}""");val d=p.retrieve(candidate(),item(),setOf(1),"spa");val ep=d.seasons.single().array("episodios")[0].asJsonObject;assertFalse(ep.has("titulo"));assertFalse(ep.has("sinopsis"));assertTrue(d.warnings.any{it.contains("no hay traducción española")})}
    @Test fun emptyAndGenericSpanishIgnored()=withServer {s,p ->seriesResponses(s,translation="""{"language":"spa","name":"Episodio 1","overview":" <p> </p> "}""");val ep=p.retrieve(candidate(),item(),setOf(1),"spa").seasons.single().array("episodios")[0].asJsonObject;assertFalse(ep.has("titulo"));assertFalse(ep.has("sinopsis"))}
    @Test fun seasonMismatchFailsWithoutChangingItem()=withServer {s,p ->enqueue(s,root());missing(s);enqueue(s,season(2));val old=item();val copy=old.deepCopy();assertThrows(IllegalStateException::class.java){p.retrieve(candidate(),old,setOf(1),"spa")};assertEquals(copy,old)}
    @Test fun alternateOrderExcluded()=withServer {s,p ->enqueue(s,root(type=2));missing(s);val d=p.retrieve(candidate(),item(),setOf(1),"spa");assertTrue(d.seasons.isEmpty());assertTrue(d.warnings.any{it.contains("orden predeterminado")})}
    @Test fun episodeDifferentSeasonExcluded()=withServer {s,p ->enqueue(s,root());missing(s);enqueue(s,season());missing(s);enqueue(s,"""{"series":{"id":5},"episodes":[${episode(season=2)}]}""");val out=p.retrieve(candidate(),item(),setOf(1),"spa");assertEquals(0,out.seasons.single().array("episodios").size());assertTrue(out.warnings.any{it.contains("otro orden")})}
    @Test fun episodeDifferentNumberExcludedWithoutCreatingOne()=withServer {s,p ->enqueue(s,root());missing(s);enqueue(s,season());missing(s);enqueue(s,"""{"series":{"id":5},"episodes":[${episode(number=2)}]}""");val old=item();assertEquals(0,p.retrieve(candidate(),old,setOf(1),"spa").seasons.single().array("episodios").size());assertEquals(1,old.array("temporadas")[0].asJsonObject.array("episodios").size())}
    @Test fun duplicateEpisodeNumbersExcluded()=withServer {s,p ->enqueue(s,root());missing(s);enqueue(s,season());missing(s);enqueue(s,"""{"series":{"id":5},"episodes":[${episode()},${episode(id=9)}]}""");assertEquals(0,p.retrieve(candidate(),item(),setOf(1),"spa").seasons.single().array("episodios").size())}
    @Test fun seasonZeroAlwaysNeedsExplicitMapping()=withServer {s,p ->
        val old=item();old.array("temporadas")[0].asJsonObject.addProperty("numero",0)
        enqueue(s,root(number=0));missing(s);enqueue(s,season(0));missing(s);enqueue(s,"""{"series":{"id":5},"episodes":[${episode(season=0)}]}""");enqueue(s,"""{"language":"spa","name":"Especial real","overview":"Especial"}""")
        val plan=MetadataRecovery.compare(old,p.retrieve(candidate(),old,setOf(0),"spa"));assertTrue(plan.changes.isNotEmpty());assertTrue(plan.changes.all{!it.safe});assertTrue(plan.changes.none{it.initiallySelected})
    }
    @Test fun baseAlternativeExplicitAndNotInitiallySelected()=withServer {s,p ->
        enqueue(s,root());enqueue(s,season());enqueue(s,"""{"series":{"id":5},"episodes":[${episode()}]}""")
        val old=item();val plan=MetadataRecovery.compare(old,p.retrieve(candidate(),old,setOf(1),"base"));assertTrue(plan.data.candidate.language.contains("no verificado"));assertTrue(plan.changes.none{it.initiallySelected});assertTrue(plan.changes.any{it.after.asString=="English base episode"})
    }
    @Test fun movieSearchAndTranslationSupported()=withServer {s,p ->
        val old=item().apply{addProperty("tipo","pelicula")};enqueue(s,"""[{"type":"movie","tvdb_id":"5","name":"Película","year":"2020"}]""")
        val c=p.search(old,"Película").single();enqueue(s,"""{"id":5,"runtime":100,"image":"https://image.invalid/movie.jpg"}""");enqueue(s,"""{"language":"spa","name":"Película española","overview":"Una sinopsis"}""")
        val out=p.retrieve(c,old,emptySet(),"spa");assertEquals("Película española",out.fields.text("titulo"));assertEquals("100",out.fields.text("duracion"));assertFalse(out.fields.has("poster_url"));assertTrue(out.seasons.isEmpty())
    }
    @Test fun canceledProviderMakesNoQueriesOrChanges()=withServer {s,p ->val old=item();val copy=old.deepCopy();p.cancel();assertThrows(IllegalStateException::class.java){p.search(old,"Serie")};assertEquals(0,s.requestCount);assertEquals(copy,old)}
    @Test fun wrongContentTypeRejected()=withServer {_,p ->assertThrows(IllegalArgumentException::class.java){p.retrieve(candidate("movie"),item(),setOf(1),"spa")}}
    @Test fun spanishTranslation404IsMissingNotAutomaticOriginal()=withServer {s,p ->enqueue(s,"""{"id":5,"runtime":90,"name":"English"}""");missing(s);val data=p.retrieve(candidate("movie"),item().apply{addProperty("tipo","pelicula")},emptySet(),"spa");assertFalse(data.fields.has("titulo"));assertFalse(data.fields.has("sinopsis"));assertTrue(data.warnings.any{it.contains("traducción española")})}
}
