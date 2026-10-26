package ar.com.miflix.admin

import com.google.gson.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Replay of real public API responses captured 2026-10-26; not a live API/device test. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest=Config.NONE,sdk=[28])
class FronteraVerdeReplayTest {
    @Test fun eightRealEpisodesOfferSixteenTextChangesWithoutReplacingArtworkOrRuntime() {
        val fixture=javaClass.getResourceAsStream("/tvmaze-frontera-verde.json")!!.bufferedReader().use {JsonParser.parseReader(it).asJsonObject}
        assertEquals(MetadataRecovery.LICENSE,fixture.text("license"));assertEquals(8,fixture.array("episodes").size())
        val local=JsonObject().apply {
            addProperty("id","serie-local-frontera");addProperty("tipo","serie");addProperty("titulo","Frontera Verde");addProperty("fecha_agregado","2026-10-01T00:00:00Z")
            add("temporadas",JsonArray().apply {add(JsonObject().apply {
                addProperty("numero",1);add("episodios",JsonArray().apply {(1..8).forEach {n ->add(JsonObject().apply {
                    addProperty("numero",n);addProperty("titulo","Episodio $n");addProperty("telegram_url","https://t.me/fixture/$n")
                    addProperty("duracion",40+n);addProperty("imagen_url","https://fixture.test/episode-$n.jpg")
                })}})
            })})
        }
        val server=MockWebServer();server.start()
        try {
            server.enqueue(MockResponse().setBody(fixture.getAsJsonObject("show").toString()))
            server.enqueue(MockResponse().setBody(fixture.array("episodes").toString()))
            server.enqueue(MockResponse().setBody(fixture.array("seasons").toString()));server.enqueue(MockResponse().setBody(fixture.array("images").toString()))
            val provider=TvmazeRecoveryProvider(base=server.url("/").toString().trimEnd('/'),limiter=ProviderRateLimit())
            val show=fixture.getAsJsonObject("show")
            val candidate=RecoveryCandidate("TVmaze",43202,show.text("name"),"2019",show.text("url"),"Sinopsis en inglés; títulos originales",show)
            val data=provider.retrieve(candidate,local,setOf(1),"en-US")
            val plan=MetadataRecovery.compare(local,data)
            val selected=plan.changes.filter {it.path.episode!=null && it.path.field in setOf("titulo","sinopsis")}.map {it.path.key}.toSet()
            assertEquals(16,selected.size);assertEquals(8,plan.episodeCount(selected));assertTrue(plan.selected(selected).none {it.safe})
            val after=MetadataRecovery.apply(plan,selected,(1..8).map {"1/$it"}.toSet(),true)
            val oldEpisodes=local.array("temporadas")[0].asJsonObject.array("episodios")
            val newEpisodes=after.array("temporadas")[0].asJsonObject.array("episodios")
            for(i in 0..7) {
                val old=oldEpisodes[i].asJsonObject;val fresh=newEpisodes[i].asJsonObject
                assertFalse(MetadataRecovery.generic(fresh.text("titulo")));assertTrue(fresh.text("sinopsis").isNotBlank())
                for(key in listOf("telegram_url","duracion","imagen_url","numero")) assertEquals(old.get(key),fresh.get(key))
            }
            assertEquals(local.get("fecha_agregado"),after.get("fecha_agregado"));assertEquals(8,newEpisodes.size())
        } finally {server.shutdown()}
    }
}
