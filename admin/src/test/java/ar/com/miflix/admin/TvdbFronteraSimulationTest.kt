package ar.com.miflix.admin

import com.google.gson.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Entirely SYNTHETIC payload, NOT an account/API coverage claim or copied TheTVDB episode text. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class TvdbFronteraSimulationTest {
    @Test fun eightExistingEpisodesSevenSyntheticSpanishSynopsesNoPlaybackOrImageChanges() {
        val s=MockWebServer()
        fun response(data:JsonElement)=MockResponse().setBody(JsonObject().apply{addProperty("status","success");add("data",data)}.toString())
        fun obj(raw:String)=JsonParser.parseString(raw).asJsonObject
        val old=obj("""{"id":"frontera-synthetic","tipo":"serie","titulo":"Frontera Verde","fecha_agregado":"2026-10-20T00:00:00Z","temporadas":[{"numero":1,"episodios":[]}]}""")
        val eps=old.array("temporadas")[0].asJsonObject.getAsJsonArray("episodios")
        for(n in 1..8) eps.add(obj("""{"numero":$n,"titulo":"Episodio $n","sinopsis":"","telegram_url":"https://t.me/synthetic_only/$n","telegram_message_id":$n,"imagen_url":"https://images.invalid/original-$n.jpg","duracion":"42"}"""))
        val snapshot=old.deepCopy()
        s.dispatcher=object:Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse = when(val path=request.path!!.substringBefore('?')) {
                "/v4/login" -> response(obj("""{"token":"synthetic-session"}"""))
                "/v4/series/367507/extended" -> response(obj("""{"id":367507,"defaultSeasonType":1,"seasons":[{"id":100,"number":1,"type":{"id":1}}]}"""))
                "/v4/seasons/100/extended" -> response(obj("""{"id":100,"seriesId":367507,"number":1,"type":{"id":1}}"""))
                "/v4/series/367507/episodes/default" -> response(obj("""{"series":{"id":367507},"episodes":[]}""").apply {getAsJsonArray("episodes").let {all ->for(n in 1..8) all.add(obj("""{"id":$n,"seriesId":367507,"seasonNumber":1,"number":$n,"runtime":42}"""))}})
                else -> {
                    val n=Regex("/v4/episodes/(\\d+)/translations/spa").matchEntire(path)?.groupValues?.get(1)?.toIntOrNull()
                    if(n!=null && n<=7) response(obj("""{"language":"spa","name":"Nombre sintético $n","overview":"Sinopsis sintética de prueba $n."}"""))
                    else MockResponse().setResponseCode(404)
                }
            }
        }
        s.start();try {
            val api=TvdbApi({TvdbCredentials("synthetic-key")},OkHttpClient(),s.url("/v4").toString(),spacingMs=0)
            val c=RecoveryCandidate("TheTVDB",367507,"Frontera Verde","2019","https://thetvdb.com/series/frontera-verde","spa",obj("""{"_kind":"series"}"""))
            val data=TvdbRecoveryProvider(api).retrieve(c,old,setOf(1),"spa")
            val plan=MetadataRecovery.compare(old,data)
            assertEquals(snapshot,old)
            val choices=plan.changes.filter {it.path.field=="sinopsis" && it.path.episode!=null}.map {it.path.key}.toSet()
            assertEquals(7,choices.size);assertTrue(data.warnings.any{it.contains("T1 E8")})
            assertThrows(IllegalArgumentException::class.java){MetadataRecovery.apply(plan,choices,emptySet(),true)}
            val after=MetadataRecovery.apply(plan,choices,(1..7).map{"1/$it"}.toSet(),true)
            val newer=after.array("temporadas")[0].asJsonObject.array("episodios")
            for(n in 0..7) for(field in listOf("numero","titulo","telegram_url","telegram_message_id","imagen_url","duracion")) assertEquals(eps[n].asJsonObject.get(field),newer[n].asJsonObject.get(field))
            assertEquals("",newer[7].asJsonObject.text("sinopsis"));assertEquals(snapshot.text("fecha_agregado"),after.text("fecha_agregado"));assertTrue(MetadataPublicationPolicy.containsTvdb(after))
            assertEquals(snapshot,MetadataRecovery.undo(after,MetadataRecovery.backup(plan,choices,after)))
        }finally{s.shutdown()}
    }
}
