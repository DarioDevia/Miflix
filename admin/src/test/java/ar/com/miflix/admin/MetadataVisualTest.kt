package ar.com.miflix.admin

import com.google.gson.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.junit.Test
import org.junit.Assert.*

class MetadataVisualTest {
    private fun obj(raw:String)=JsonParser.parseString(raw).asJsonObject
    private fun logo(lang:String?,path:String,votes:Int=1,score:Double=5.0,width:Int=500)=JsonObject().apply {
        addProperty("iso_639_1",lang);addProperty("file_path",path);addProperty("vote_count",votes);addProperty("vote_average",score);addProperty("width",width)
    }
    private fun chosen(vararg entries:JsonObject)=HttpMetadata.logo(JsonObject().apply {add("logos",JsonArray().apply {entries.forEach {add(it)}})})
    private fun detail(type:String="pelicula",backdrop:String?="/back.jpg",logos:String="""{"logos":[{"file_path":"/logo.png","iso_639_1":"es"}]}""",status:Int=200,relay:Boolean=false):JsonObject {
        val server=MockWebServer();val paths=mutableListOf<String>()
        server.dispatcher=object:Dispatcher(){override fun dispatch(request:RecordedRequest):MockResponse {
            paths.add(request.path!!)
            val path=request.requestUrl!!.encodedPath
            if(path.endsWith("/images")) {assertNull(request.requestUrl!!.queryParameter("language"));return MockResponse().setResponseCode(status).setBody(logos)}
            val body=when {
                path.endsWith("/external_ids") -> """{"imdb_id":"tt42"}"""
                path.endsWith("/credits") -> """{"cast":[],"crew":[]}"""
                path.endsWith("/videos") -> """{"results":[{"site":"YouTube","type":"Trailer","key":"fixture"}]}"""
                else -> JsonObject().apply {
                    addProperty("title","Película");addProperty("name","Serie");addProperty("poster_path","/poster.jpg");addProperty("backdrop_path",backdrop)
                    addProperty("release_date","2020-01-01");addProperty("first_air_date","2021-01-01");add("genres",JsonArray());add("seasons",JsonArray())
                }.toString()
            }
            return MockResponse().setBody(body)
        }}
        server.start()
        try {
            val base=server.url("/").toString().trimEnd('/')
            val out=HttpMetadata(AdminConfig(base,"fake-admin",if(relay) "" else "fake-provider"),OkHttpClient(),base).detail(SearchResult("TMDB",42,"","","",JsonObject()),type)
            val route=if(type=="pelicula") "movie" else "tv"
            assertEquals(5,paths.size) // Existing detail/external IDs/credits/videos plus one optional logos request.
            assertEquals(1,paths.count {it.substringBefore('?').endsWith("/$route/42")})
            assertTrue(paths.last().endsWith("/$route/42/images"))
            if(relay) assertTrue(paths.all {it.startsWith("/admin/metadata/tmdb/")})
            assertEquals(HttpMetadata.image("/poster.jpg"),out.text("poster_url"))
            return out
        } finally {server.shutdown()}
    }
    @Test fun movieBackdropReusesDetail(){val out=detail();assertEquals(HttpMetadata.image("/back.jpg"),out.text("backdrop_url"));assertEquals(out.text("backdrop_url"),out.text("fondo_url"))}
    @Test fun movieWithoutBackdropStillLoads(){assertFalse(detail(backdrop=null).has("backdrop_url"))}
    @Test fun movieLogoUsesReturnedPath(){assertEquals(HttpMetadata.image("/logo.png"),detail().text("logo_url"))}
    @Test fun movieWithoutLogoStillLoads(){assertFalse(detail(logos="{\"logos\":[]}").has("logo_url"))}
    @Test fun optionalImagesFailureKeepsMetadata(){val out=detail(status=400);assertFalse(out.has("logo_url"));assertEquals("Película",out.text("titulo"));assertEquals(42L,out.number("tmdb_id"));assertEquals(HttpMetadata.image("/back.jpg"),out.text("backdrop_url"))}
    @Test fun seriesBackdrop(){assertEquals(HttpMetadata.image("/back.jpg"),detail("serie").text("backdrop_url"))}
    @Test fun seriesLogoThroughRelay(){assertEquals(HttpMetadata.image("/logo.png"),detail("serie",relay=true).text("logo_url"))}
    @Test fun animeTmdbUsesExistingTvIntegration(){val out=detail("anime");assertEquals("anime-tmdb-42",out.text("id"));assertEquals(HttpMetadata.image("/logo.png"),out.text("logo_url"))}
    @Test fun spanishPreferredToOtherLanguages(){assertEquals(HttpMetadata.image("/es.png"),chosen(logo("en","/en.png",99),logo(null,"/none.png"),logo("es","/es.png")))}
    @Test fun untaggedPreferredToEnglish(){assertEquals(HttpMetadata.image("/none.png"),chosen(logo("en","/en.png"),logo(null,"/none.png"),logo("ja","/ja.png")))}
    @Test fun englishPreferredToOtherLanguages(){assertEquals(HttpMetadata.image("/en.png"),chosen(logo("ja","/ja.png"),logo("en","/en.png")))}
    @Test fun otherLanguageIsValidFallback(){assertEquals(HttpMetadata.image("/ja.png"),chosen(logo("ja","/ja.png")))}
    @Test fun deterministicQualityAndPathTieBreak(){val a=logo("es","/a.png",4,7.0,1000);val b=logo("es","/b.png",4,7.0,1000);val low=logo("es","/low.png",20,5.0,2000);assertEquals(HttpMetadata.image("/a.png"),chosen(b,low,a));assertEquals(chosen(b,low,a),chosen(a,low,b))}
    @Test fun invalidOrMissingPathsAreSkipped(){assertNull(chosen(logo("es",""),logo("en","https://invalid.test/a.png")));assertNull(HttpMetadata.logo(obj("{}")))}
    @Test fun oldCatalogWithoutVisualFieldsLoads(){val root=CatalogRules.parse("""{"schema_version":1,"items":[{"id":"old","tipo":"pelicula","titulo":"Antigua"}]}""");assertFalse(CatalogRules.items(root).single().has("logo_url"));assertFalse(CatalogRules.items(root).single().has("backdrop_url"))}
    @Test fun editAndPublicRoundTripPreserveVisualsAndUnknownFields(){
        val root=CatalogRules.parse("""{"schema_version":1,"items":[{"id":"old","tipo":"serie","titulo":"Antigua","backdrop_url":"https://image.tmdb.org/t/p/original/b.jpg","logo_url":"https://image.tmdb.org/t/p/original/l.png","unknown":true,"temporadas":[{"numero":1,"episodios":[{"numero":2,"telegram_url":"https://t.me/test/2"}]}]}]}""")
        val item=CatalogRules.items(root).single().deepCopy().apply {addProperty("titulo","Editada")}
        val out=CatalogRules.items(CatalogRules.parse(CatalogRules.publicCopy(CatalogRules.save(root,item)).toString())).single()
        assertEquals(item.get("backdrop_url"),out.get("backdrop_url"));assertEquals(item.get("logo_url"),out.get("logo_url"));assertEquals(item.get("temporadas"),out.get("temporadas"));assertTrue(out.get("unknown").asBoolean)
    }
    @Test fun anilistEnrichmentRetainsIdentityAndGetsVisualsInExistingBackgroundGroup(){
        val base=obj("""{"id":"anime-7","tipo":"anime","anilist_id":7,"fuente_principal":"AniList","poster_url":"https://fixture.test/anilist.jpg"}""")
        val out=Enrichment.apply(base,detail("anime"),setOf("fondo"));assertEquals("anime-7",out.text("id"));assertEquals(7L,out.number("anilist_id"));assertEquals("AniList",out.text("fuente_principal"));assertEquals(base.text("poster_url"),out.text("poster_url"));assertEquals(HttpMetadata.image("/back.jpg"),out.text("backdrop_url"));assertEquals(HttpMetadata.image("/logo.png"),out.text("logo_url"))
    }
    @Test fun missingEnrichmentImagesNeverClearExistingVisuals(){val base=obj("""{"backdrop_url":"https://fixture.test/b.jpg","logo_url":"https://fixture.test/l.png"}""");assertEquals(base.get("logo_url"),Enrichment.apply(base,obj("{}"),setOf("fondo")).get("logo_url"));assertEquals(base.get("backdrop_url"),Enrichment.apply(base,obj("{\"backdrop_url\":null,\"logo_url\":\"\"}"),setOf("fondo")).get("backdrop_url"))}
}
