package ar.com.miflix.admin

import com.google.gson.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class DirectMetadataTest {
    private fun obj(text:String)=JsonParser.parseString(text).asJsonObject
    private fun link(url:String="https://www.themoviedb.org/movie/42-camino",type:String="pelicula")=MetadataLink.parse(url,type)
    @Test fun movieLinkExtractsIdentityWithoutSlug() {assertEquals(MetadataLink("TMDB","movie",42),link("https://themoviedb.org/movie/42"))}
    @Test fun tvLinkMatchesSeries() {assertEquals(MetadataLink("TMDB","tv",42),link("https://www.themoviedb.org/tv/42","serie"))}
    @Test fun animeSupportsMovieAndTvWithExplicitKinds() {assertEquals("movie",link(type="anime").mediaType);assertEquals("tv",link("https://themoviedb.org/tv/42-anime","anime").mediaType)}
    @Test fun anilistLinkWithFriendlyTitle() {assertEquals(MetadataLink("AniList","anime",1),link("https://www.anilist.co/anime/1/Cowboy-Bebop/","anime"))}
    @Test fun languageTrackingAndFragmentDoNotChangeCanonicalIdentity() {val l=link(" HTTPS://WWW.THEMOVIEDB.ORG:443/es-ES/movie/00042-el-ni%C3%B1o?language=es-ES&utm_source=chrome#overview ");assertEquals("https://www.themoviedb.org/movie/42",l.canonicalUrl)}
    @Test fun forgedDomainsAndCredentialsAreRejected() {for(url in listOf("https://themoviedb.org.evil.test/movie/42","https://evilthemoviedb.org/movie/42","https://themoviedb.org@evil.test/movie/42","https://user:secret@themoviedb.org/movie/42","https://www.themoviedb.org./movie/42","https://anilist.co.evil.test/anime/42","https://127.0.0.1/movie/42")) assertThrows(IllegalArgumentException::class.java){link(url)}}
    @Test fun dangerousProtocolsAndPortsAreRejected() {for(url in listOf("http://themoviedb.org/movie/42","javascript:alert(1)","file:///movie/42","//themoviedb.org/movie/42","https://themoviedb.org:444/movie/42")) assertThrows(IllegalArgumentException::class.java){link(url)}}
    @Test fun unexpectedRoutesEncodedSlashesAndControlsAreRejected() {for(path in listOf("/movie/42/credits","/tv/42/season/1","/movie/42///","/movie/%34%32","/movie/42%2fcredits","/movie/42-%0afoo","/collection/42","/movie/42 extra")) assertThrows(IllegalArgumentException::class.java){link("https://themoviedb.org$path")}}
    @Test fun anilistMangaAndOtherSubpagesAreRejected() {for(path in listOf("/manga/1","/anime/1/name/characters","/anime/1/..","/anime/1/%2E%2E","/user/1","/es/anime/1")) assertThrows(IllegalArgumentException::class.java){link("https://anilist.co$path","anime")}}
    @Test fun unknownDuplicateAndRedirectParametersAreRejected() {for(query in listOf("redirect=https://evil.test","language=es&language=en","language=bad_code","page=2","year=2008","utm_source=%0a","utm_source=x&")) assertThrows(IllegalArgumentException::class.java){link("https://themoviedb.org/movie/42?$query")}}
    @Test fun incompatibleCategoriesAreRejected() {for(pair in listOf("https://themoviedb.org/movie/42" to "serie","https://themoviedb.org/tv/42" to "pelicula","https://anilist.co/anime/1" to "pelicula","https://anilist.co/anime/1" to "serie")) assertThrows(IllegalArgumentException::class.java){link(pair.first,pair.second)}}
    @Test fun idsMustFitPositiveApiIntegers() {for(id in listOf("0","-1","2147483648","999999999999999999999999","42.5","+42","")) assertThrows(IllegalArgumentException::class.java){link("https://themoviedb.org/movie/$id")}}

    private fun base(type:String="pelicula")=obj("""{"id":"local","tipo":"$type","titulo":"Personalizado","fecha_agregado":"2020-01-01T00:00:00Z","telegram_url":"https://t.me/channel/3","telegram_message_id":3,"audio":"Dual","calidad":"HD","versiones":[{"x":1}],"future":{"preserve":true},"temporadas":[{"numero":1,"future":true,"episodios":[{"numero":7,"telegram_url":"https://t.me/channel/7","titulo":"Manual"}]}]}""")
    private fun candidate(type:String="pelicula")=obj("""{"id":"movie-new","tipo":"$type","titulo":"Camino","anio":"2008","sinopsis":"Texto fuente","tmdb_id":42,"poster_url":"https://example.org/poster","fondo_url":"https://example.org/bg","backdrop_url":"https://example.org/bg","puntuacion":"8/10","fuente_puntuacion":"TMDB","audio":"WRONG","telegram_url":"WRONG","fecha_agregado":"2099","temporadas":[{"numero":2,"episodios":[{"numero":1}]}]}""")
    @Test fun onlyExplicitFieldsChangeAndProtectedValuesAreUntouched() {val b=base();val before=b.deepCopy();val p=DirectMetadataPreview(link(),b,candidate());val result=DirectMetadata.apply(p,setOf("sinopsis"),true).after;assertEquals(before,b);for(key in listOf("id","tipo","titulo","fecha_agregado","telegram_url","telegram_message_id","audio","calidad","versiones","future","temporadas")) assertEquals(key,b.get(key),result.get(key));assertEquals("Texto fuente",result.text("sinopsis"));assertEquals(42L,result.number("tmdb_id"))}
    @Test fun confirmationIsRequiredEvenForMissingFields() {assertThrows(IllegalArgumentException::class.java){DirectMetadata.apply(DirectMetadataPreview(link(),base(),candidate()),setOf("sinopsis"),false)}}
    @Test fun protectedFieldsCannotBeInjectedIntoSelection() {for(key in listOf("id","tipo","fecha_agregado","telegram_url","audio","calidad","temporadas","tmdb_id","future")) assertThrows(IllegalArgumentException::class.java){DirectMetadata.apply(DirectMetadataPreview(link(),base(),candidate()),setOf(key),true)}}
    @Test fun existingExternalIdentityCannotBeReplaced() {val b=base().apply {addProperty("tmdb_id",99)};assertThrows(IllegalArgumentException::class.java){DirectMetadata.checkIdentity(b,link())};assertThrows(IllegalArgumentException::class.java){DirectMetadata.apply(DirectMetadataPreview(link(),b,candidate()),setOf("sinopsis"),true)}}
    @Test fun conflictingImdbIdentityIsBlocked() {val b=base().apply {addProperty("imdb_id","ttOld")};val c=candidate().apply {addProperty("imdb_id","ttNew")};assertThrows(IllegalArgumentException::class.java){DirectMetadata.apply(DirectMetadataPreview(link(),b,c),emptySet(),true)}}
    @Test fun wrongSourceIdCannotBeApplied() {val c=candidate().apply {addProperty("tmdb_id",99)};assertThrows(IllegalArgumentException::class.java){DirectMetadata.apply(DirectMetadataPreview(link(),base(),c),emptySet(),true)}}
    @Test fun initialSelectionDoesNotOverwriteLocalFieldsOrCanonicalBackdrop() {val b=base().apply {addProperty("backdrop_url","https://example.org/local")};val selected=DirectMetadata.initialSelection(b,candidate());assertFalse("titulo" in selected);assertFalse("fondo_url" in selected);assertTrue("sinopsis" in selected)}
    @Test fun imageAliasesAndRatingSourceAreAppliedTogether() {val c=candidate();val result=DirectMetadata.apply(DirectMetadataPreview(link(),base(),c),setOf("fondo_url","puntuacion"),true);for(key in listOf("fondo_url","backdrop_url","puntuacion","fuente_puntuacion")) assertEquals(c.get(key),result.after.get(key));assertTrue(result.fields.containsAll(listOf("fondo_url","backdrop_url","puntuacion","fuente_puntuacion")))}
    @Test fun provenanceUnknownFieldsAndUntouchedTvmazeCreditsSurvive() {val b=base().apply {add("fuentes_metadata",obj("""{"future":{"a":1},"enlaces_directos":{"TMDB":{"unknown":true}},"recuperacion_selectiva":{"title/season/titulo":{"fuente":"TVmaze","licencia":"keep"}}}"""))};val r=DirectMetadata.apply(DirectMetadataPreview(link(),b,candidate()),setOf("sinopsis"),true).after.getAsJsonObject("fuentes_metadata");assertEquals(b.getAsJsonObject("fuentes_metadata").get("future"),r.get("future"));assertTrue(r.getAsJsonObject("enlaces_directos").getAsJsonObject("TMDB").get("unknown").asBoolean);assertEquals(b.getAsJsonObject("fuentes_metadata").getAsJsonObject("recuperacion_selectiva").get("title/season/titulo"),r.getAsJsonObject("recuperacion_selectiva").get("title/season/titulo"))}
    @Test fun malformedLocalProvenanceIsNotReplaced() {val b=base().apply {addProperty("fuentes_metadata","unknown")};assertThrows(IllegalArgumentException::class.java){DirectMetadata.apply(DirectMetadataPreview(link(),b,candidate()),setOf("sinopsis"),true)}}
    @Test fun animeMovieBindingSurvivesAnilistAssociationAndPreservesPrimarySource() {val b=base("anime").apply {addProperty("fuente_principal","AniList");addProperty("anilist_id",55)};val movie=DirectMetadata.apply(DirectMetadataPreview(link(type="anime"),b,candidate("anime")),setOf("sinopsis"),true).after;assertTrue(DirectMetadata.animeMovie(movie));assertEquals("AniList",movie.text("fuente_principal"));val ani=candidate("anime").apply {remove("tmdb_id");addProperty("anilist_id",55)};val after=DirectMetadata.apply(DirectMetadataPreview(link("https://anilist.co/anime/55","anime"),movie,ani),emptySet(),true).after;assertTrue(DirectMetadata.animeMovie(after));assertEquals(42L,after.number("tmdb_id"))}
    @Test fun animeTvIdentityCannotBecomeMovieWithSameNumericId() {val b=base("anime").apply {addProperty("tmdb_id",42)};assertThrows(IllegalArgumentException::class.java){DirectMetadata.checkIdentity(b,link(type="anime"))}}

    private fun meta(s:MockWebServer,direct:Boolean=false,client:OkHttpClient=OkHttpClient())=HttpMetadata(AdminConfig(s.url("/").toString().trimEnd('/'),"fake-admin",if(direct) "fake-tmdb" else ""),client,tmdbBase=s.url("/3").toString(),anilistUrl=s.url("/graphql").toString())
    private fun startServer():MockWebServer=MockWebServer().apply {dispatcher=object:Dispatcher(){override fun dispatch(request:RecordedRequest):MockResponse {
        val path=request.requestUrl!!.encodedPath
        val body=when {
            path.endsWith("/movie/42") -> """{"id":42,"title":"Camino","release_date":"2008-01-01","genres":[{"name":"Drama"}]}"""
            path.endsWith("/tv/42") -> """{"id":42,"name":"Serie","genres":[],"seasons":[{"season_number":1,"name":"Primera"}]}"""
            path.endsWith("/external_ids") -> """{"imdb_id":"tt42"}"""
            path.endsWith("/credits") -> """{"cast":[],"crew":[]}"""
            path.endsWith("/videos") -> """{"results":[]}"""
            path.endsWith("/images") -> """{"logos":[]}"""
            else -> return MockResponse().setResponseCode(404)
        };return MockResponse().setBody(body)
    }};start()}
    @Test fun tmdbDirectUsesDetailsThroughRelayWithoutSearchOrExtraParameters() {val s=startServer();try{val out=meta(s).direct(link(),"pelicula");assertEquals("Camino",out.text("titulo"));assertEquals(42L,out.number("tmdb_id"));val r=s.takeRequest();assertEquals("/admin/metadata/tmdb/movie/42",r.requestUrl!!.encodedPath);assertEquals(setOf("language"),r.requestUrl!!.queryParameterNames);assertEquals("es-MX",r.requestUrl!!.queryParameter("language"));assertEquals("Bearer fake-admin",r.getHeader("Authorization"));while(s.requestCount>1) {val q=s.takeRequest(20,TimeUnit.MILLISECONDS) ?: break;assertFalse(q.path!!.contains("search"))}}finally{s.shutdown()}}
    @Test fun tmdbDirectTokenUsesExistingOptionalTransport() {val s=startServer();try {meta(s,true).direct(link(),"pelicula");val r=s.takeRequest();assertEquals("/3/movie/42",r.requestUrl!!.encodedPath);assertEquals("Bearer fake-tmdb",r.getHeader("Authorization"))}finally{s.shutdown()}}
    @Test fun seriesAndAnimeTvRetrieveTvDetails() {val s=startServer();try {for(type in listOf("serie","anime")) {val out=meta(s).direct(link("https://themoviedb.org/tv/42",type),type);assertEquals(type,out.text("tipo"));assertEquals(1,out.array("temporadas").size())}}finally{s.shutdown()}}
    @Test fun animeMovieUsesMovieEndpointAndDistinctInternalIdentity() {val s=startServer();try {val out=meta(s).direct(link(type="anime"),"anime");assertEquals("anime",out.text("tipo"));assertEquals("anime-tmdb-movie-42",out.text("id"));assertTrue(out.array("temporadas").isEmpty);assertTrue(s.takeRequest().path!!.contains("/movie/42"))}finally{s.shutdown()}}
    @Test fun missingIdTitleGenresOrTvSeasonsIsRejectedBeforeReview() {val s=MockWebServer();s.start();try {val m=meta(s);for(raw in listOf("{}","""{"id":99,"title":"Wrong","genres":[]}""","""{"id":42.5,"title":"Wrong","genres":[]}""","""{"id":42,"genres":[]}""","""{"id":42,"title":"No genres"}""")) {s.enqueue(MockResponse().setBody(raw));assertThrows(IllegalArgumentException::class.java){m.direct(link(),"pelicula")}};s.enqueue(MockResponse().setBody("""{"id":42,"name":"No seasons","genres":[]}"""));assertThrows(IllegalArgumentException::class.java){m.direct(link("https://themoviedb.org/tv/42","serie"),"serie")}}finally{s.shutdown()}}
    @Test fun nonexistentUnauthorizedRateLimitedAndServerErrorsAreVisible() {val s=MockWebServer();s.start();try {for(code in listOf(401,403,404,429,500)) {s.enqueue(MockResponse().setResponseCode(code));val e=assertThrows(IllegalStateException::class.java){meta(s).direct(link(),"pelicula")};assertTrue(e.message!!.contains(code.toString()))};assertEquals(5,s.requestCount)}finally{s.shutdown()}}
    @Test fun arbitraryRedirectsAreNeverFollowedEvenWithInjectedDefaultClient() {val s=MockWebServer();val target=MockWebServer();s.start();target.start();try{s.enqueue(MockResponse().setResponseCode(302).addHeader("Location",target.url("/steal")));assertThrows(IllegalStateException::class.java){meta(s).direct(link(),"pelicula")};assertEquals(0,target.requestCount)}finally{s.shutdown();target.shutdown()}}
    @Test fun timeoutAndMalformedJsonDoNotReturnCandidates() {val s=MockWebServer();s.start();try {s.enqueue(MockResponse().setHeadersDelay(1,TimeUnit.SECONDS).setBody("{}"));assertThrows(IllegalStateException::class.java){meta(s,client=OkHttpClient.Builder().callTimeout(80,TimeUnit.MILLISECONDS).build()).direct(link(),"pelicula")};s.enqueue(MockResponse().setBody("bad json"));assertThrows(IllegalStateException::class.java){meta(s).direct(link(),"pelicula")}}finally{s.shutdown()}}
    private fun anime()=obj("""{"id":55,"type":"ANIME","format":"MOVIE","idMal":null,"title":{"english":"Anime","romaji":"Original","native":"Native"},"seasonYear":2008,"genres":["Drama"],"description":"<b>Texto</b>","averageScore":80,"episodes":1,"coverImage":{"extraLarge":"https://example.org/poster"},"characters":{"edges":[{"role":"MAIN","node":{"name":{"full":"Hero"}}}]},"staff":{"edges":[{"role":"Director","node":{"name":{"full":"Director"}}}]},"trailer":{"site":"youtube","id":"clip"}}""")
    @Test fun anilistDirectRetrievesAllAdapterFieldsByIdWithoutAuthorization() {val s=MockWebServer();s.start();try {s.enqueue(MockResponse().setBody(JsonObject().apply {add("data",JsonObject().apply {add("Media",anime())})}.toString()));val out=meta(s).direct(link("https://anilist.co/anime/55/Anime","anime"),"anime");assertEquals(55L,out.number("anilist_id"));assertEquals("Texto",out.text("sinopsis"));assertEquals("80/100",out.text("puntuacion"));assertEquals("Director",out.text("direccion"));assertEquals("Hero",out.text("protagonistas"));assertEquals(1L,out.number("cantidad_episodios"));val r=s.takeRequest();assertNull(r.getHeader("Authorization"));val q=obj(r.body.readUtf8());assertEquals(55L,q.getAsJsonObject("variables").number("id"));assertTrue(q.text("query").contains("Media(id:"));assertTrue(q.text("query").contains("type: ANIME"));assertFalse(q.text("query").contains("Page("));assertEquals(q.text("query").count {it=='{'},q.text("query").count {it=='}'})}finally{s.shutdown()}}
    @Test fun anilistNullMediaWrongTypeAndMissingFieldsAreRejected() {val s=MockWebServer();s.start();try {val m=meta(s);for(media in listOf(JsonNull.INSTANCE,JsonObject(),anime().apply {addProperty("type","MANGA")},anime().apply {remove("characters")},anime().apply {remove("episodes")})) {s.enqueue(MockResponse().setBody(JsonObject().apply {add("data",JsonObject().apply {add("Media",media)})}.toString()));assertThrows(IllegalArgumentException::class.java){m.direct(link("https://anilist.co/anime/55","anime"),"anime")}}}finally{s.shutdown()}}
    @Test fun graphqlErrorsDoNotExposePrivateMessages() {val s=MockWebServer();s.start();try {s.enqueue(MockResponse().setBody("""{"errors":[{"message":"private upstream data"}]}"""));val e=assertThrows(IllegalArgumentException::class.java){meta(s).direct(link("https://anilist.co/anime/55","anime"),"anime")};assertFalse(e.message.orEmpty().contains("private upstream"))}finally{s.shutdown()}}
    @Test fun undoRestoresOnlySelectedMetadataAndRetainsApprovedMovieIdentity() {
        val b=base("anime");val c=candidate("anime");val change=DirectMetadata.apply(DirectMetadataPreview(link(type="anime"),b,c),setOf("sinopsis","anio","puntuacion","fondo_url"),true)
        val restored=DirectMetadata.undo(change.after,change.backup())
        assertEquals(change.before,restored);assertTrue(DirectMetadata.animeMovie(restored));assertEquals(42L,restored.number("tmdb_id"));assertEquals(b.get("temporadas"),restored.get("temporadas"))
    }
    @Test fun undoRefusesSubsequentEditsToSelectedFields() {
        val change=DirectMetadata.apply(DirectMetadataPreview(link(),base(),candidate()),setOf("sinopsis"),true)
        val later=change.after.deepCopy().apply {addProperty("sinopsis","Manual después")}
        assertThrows(IllegalArgumentException::class.java){DirectMetadata.undo(later,change.backup())}
    }
    @Test fun emptySelectionOnlyBindsIdentityAndNeverImportsSeasons() {
        val b=base();val after=DirectMetadata.apply(DirectMetadataPreview(link(),b,candidate()),emptySet(),true).after
        for(key in DirectMetadata.labels.keys) assertEquals(key,b.get(key),after.get(key))
        assertEquals(b.get("temporadas"),after.get("temporadas"));assertEquals(42L,after.number("tmdb_id"))
    }
    @Test fun productionDirectTransportGeneratesOfflineRelayRequestFixture() {
        val s=startServer();try {
            val m=meta(s);m.direct(link(),"pelicula");m.direct(link("https://themoviedb.org/tv/42","serie"),"serie");m.direct(link(type="anime"),"anime")
            val paths=JsonArray();repeat(s.requestCount) {paths.add(s.takeRequest().path)}
            java.io.File("build/direct-relay-requests.json").apply {parentFile?.mkdirs();writeText(paths.toString())}
            assertTrue(paths.size()>0)
        } finally {s.shutdown()}
    }
    @Test fun traditionalTmdbStillReturnsTenAndSendsNoPageOrYear() {
        val s=MockWebServer();s.start();try {
            val body=JsonObject().apply {add("results",JsonArray().apply {repeat(20) {add(JsonObject().apply {addProperty("id",it+1);addProperty("title","Movie $it");addProperty("name","TV $it")})}})}
            for(type in listOf("pelicula","serie","anime")) {
                s.enqueue(MockResponse().setBody(body.toString()));assertEquals(10,meta(s).search("Título",type,"TMDB").size)
                val r=s.takeRequest();assertEquals(setOf("query","language","include_adult"),r.requestUrl!!.queryParameterNames)
            }
        } finally {s.shutdown()}
    }
    @Test fun traditionalAnilistStillUsesPageOneAndFiveResults() {
        val s=MockWebServer();s.start();try {
            val body=JsonObject().apply {add("data",JsonObject().apply {add("Page",JsonObject().apply {add("media",JsonArray().apply {repeat(5) {add(anime().apply {addProperty("id",it+1)})}})})})}
            s.enqueue(MockResponse().setBody(body.toString()));assertEquals(5,meta(s).search("Anime","anime","AniList").size)
            val q=obj(s.takeRequest().body.readUtf8());assertTrue(q.text("query").contains("Page(page: 1, perPage: 5)"));assertEquals(setOf("search"),q.getAsJsonObject("variables").keySet())
        } finally {s.shutdown()}
    }

    @Test fun directUndoRejectsProtectedPathsAndChangedIdentities() {
        val change=DirectMetadata.apply(DirectMetadataPreview(link(),base(),candidate()),setOf("sinopsis"),true)
        val backup=change.backup().apply {getAsJsonArray("paths").add(obj("""{"field":"telegram_url"}"""))}
        assertThrows(IllegalArgumentException::class.java){DirectMetadata.undo(change.after,backup)}
        val changed=change.after.deepCopy().apply {addProperty("tmdb_id",99)}
        assertThrows(IllegalArgumentException::class.java){DirectMetadata.undo(changed,change.backup())}
    }

    @Test fun directProvenanceRemainsCompatibleWithExistingClientParsers() {
        val root=JsonObject().apply {addProperty("schema_version",1);add("colecciones",JsonArray());add("items",JsonArray().apply {
            for((index,type) in listOf("pelicula","serie","anime").withIndex()) {
                val base=obj("""{"id":"local-$index","tipo":"$type","titulo":"Local","telegram_url":"https://t.me/demo/1","fecha_agregado":"2020-01-01T00:00:00Z","temporadas":[]}""")
                val url=if(type=="serie") "https://themoviedb.org/tv/42" else "https://themoviedb.org/movie/42"
                add(DirectMetadata.apply(DirectMetadataPreview(link(url,type),base,candidate(type)),setOf("sinopsis"),true).after)
            }
        })}
        val raw=CatalogRules.publicCopy(CatalogRules.parse(root.toString())).toString()
        assertEquals(listOf("local-0","local-1","local-2"),ar.com.miflix.admin.compat.mobile.CatalogRepository.parseCatalog(raw).items.map {it.id})
        assertEquals(listOf("local-0","local-1","local-2"),ar.com.miflix.admin.compat.tv.CatalogRepository.parseCatalog(raw).items.map {it.id})
    }

}
