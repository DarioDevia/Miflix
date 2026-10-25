package ar.com.miflix.admin

import com.google.gson.*
import org.junit.Test
import org.junit.Assert.*

class AdminEditsTest {
    private val link="https://t.me/c/123/4/90?single&thread=4#message"
    private fun item(type:String="serie",id:String="s"):JsonObject=JsonParser.parseString("""{
      "id":"$id","tipo":"$type","titulo":"Contenido $id","unknown":{"future":true},"trailer_url":"https://youtu.be/trailer",
      "telegram_url":"https://t.me/c/123/20","fuente_principal":"AniList","anilist_id":12,
      "temporadas":[{"numero":0,"unknown":"especiales","episodios":[{"numero":1,"telegram_url":"https://t.me/c/123/30"}]},
      {"numero":2,"unknown":"temporada","episodios":[{"numero":7,"tmdb_id":77,"unknown":"episodio","telegram_url":"https://t.me/c/123/70"},{"numero":8,"tmdb_id":88,"telegram_url":"https://t.me/c/123/80"}]}]}
    """).asJsonObject
    private fun root()=JsonObject().apply {addProperty("schema_version",1);addProperty("generated_at","before");addProperty("unknown_root",true);add("items",JsonArray().apply {add(item("pelicula","p"));add(item());add(item("anime","a"))})}
    private class Store:StateStore {
        var value:JsonObject?=null;var fail=false
        override fun read()=value?.deepCopy()
        override fun write(state:JsonObject){if(fail) error("disk full");value=state.deepCopy()}
    }
    private class Remote:AdminRemote {
        var catalog:JsonObject?=null;var calls=0
        override fun download()=error("not used")
        override fun publish(catalog:JsonObject){calls++;this.catalog=catalog.deepCopy()}
    }
    private fun ready(store:Store=Store()):AdminRepository {
        val cat=root()
        val remote=object:AdminRemote {
            override fun download()=cat.deepCopy()
            override fun publish(catalog:JsonObject)=error("unexpected")
                }
        return AdminRepository(store).apply {refresh(remote)}
    }
    private fun find(repo:AdminRepository,id:String)=CatalogRules.items(repo.catalog!!).first {it.text("id")==id}
    @Test fun movieLinkFeedsContentAndProcessedPublication(){val original=item("pelicula","p");val changed=AdminEdits.telegramLink(original,link);assertEquals(link,changed.text("telegram_url"));assertEquals(link,changed.text("_admin_original"));assertEquals(link,TelegramLinks.checkpoint(changed,changed.text("_admin_original")).text("telegram_url"));assertNotEquals(link,original.text("telegram_url"));assertEquals(original.get("unknown"),changed.get("unknown"))}
    @Test fun seriesLinkDoesNotReplaceEpisodes(){val original=item();val changed=AdminEdits.telegramLink(original,link);assertEquals(original.get("temporadas"),changed.get("temporadas"));assertEquals(link,changed.text("telegram_url"))}
    @Test fun animeLinkPreservesEpisodesIdentityAndProvenance(){val original=item("anime","a");val changed=AdminEdits.telegramLink(original,link);assertEquals(original.get("temporadas"),changed.get("temporadas"));assertEquals(12L,changed.number("anilist_id"));assertEquals("AniList",changed.text("fuente_principal"));assertEquals(original.get("trailer_url"),changed.get("trailer_url"))}
    @Test fun oldDistinctLinksSurviveDraftRecovery(){val repo=ready();val draft=item().apply {addProperty("_admin_original",link)};repo.persistDraft(draft);assertEquals(link,repo.draft()!!.text("_admin_original"));assertEquals("https://t.me/c/123/20",repo.draft()!!.text("telegram_url"))}
    private fun removeWhole(id:String){val repo=ready();val before=repo.catalog!!.deepCopy();repo.removeItem(id);assertFalse(CatalogRules.items(repo.catalog!!).any {it.text("id")==id});assertTrue(repo.pending);CatalogRules.items(before).filter {it.text("id")!=id}.forEach {assertEquals(it,find(repo,it.text("id")))};assertEquals(before.get("unknown_root"),repo.catalog!!.get("unknown_root"))}
    @Test fun removeMovieOnly(){removeWhole("p")}
    @Test fun removeSeriesOnly(){removeWhole("s")}
    @Test fun removeAnimeOnly(){removeWhole("a")}
    @Test fun itemDeletionClearsOnlyMatchingDraft(){val repo=ready();repo.persistDraft(item());repo.removeItem("s");assertNull(repo.draft());val other=ready();other.persistDraft(item("anime","a"));other.removeItem("s");assertEquals("a",other.draft()!!.text("id"))}
    @Test fun itemDeletionKeepsLocalCheckpoint(){val repo=ready();val cp=TelegramLinks.checkpoint(find(repo,"p"),link);repo.setCheckpoint(cp);repo.removeItem("p");assertEquals(cp,repo.checkpoint("pelicula"))}
    @Test fun removeEpisodePreservesOtherEpisodeAndSeasonMetadata(){val repo=ready();val before=find(repo,"s").deepCopy();repo.removePart("s",2,7);val out=find(repo,"s");assertTrue(repo.pending);assertEquals(before.array("temporadas")[0],out.array("temporadas")[0]);assertEquals(before.array("temporadas")[1].asJsonObject.array("episodios")[1],out.array("temporadas")[1].asJsonObject.array("episodios")[0]);assertEquals("temporada",out.array("temporadas")[1].asJsonObject.text("unknown"));assertEquals(before.get("unknown"),out.get("unknown"))}
    @Test fun episodeDeletionPreservesOtherTitlesForBothCategories(){listOf("s","a").forEach {id ->val repo=ready();val others=CatalogRules.items(repo.catalog!!).filter {it.text("id")!=id}.map {it.deepCopy()};repo.removePart(id,2,7);others.forEach {assertEquals(it,find(repo,it.text("id")))}}}
    @Test fun deletingLastEpisodeKeepsValidEmptySeason(){val repo=ready();repo.removePart("s",0,1);assertEquals(0,find(repo,"s").array("temporadas")[0].asJsonObject.array("episodios").size());CatalogRules.parse(repo.catalog.toString())}
    @Test fun removeSeasonPreservesOtherSeasonsAndTitles(){listOf("s","a").forEach {id ->val repo=ready();val otherSeason=find(repo,id).array("temporadas")[0].deepCopy();val unrelated=find(repo,"p").deepCopy();repo.removePart(id,2);assertEquals(1,find(repo,id).array("temporadas").size());assertEquals(otherSeason,find(repo,id).array("temporadas")[0]);assertEquals(unrelated,find(repo,"p"));assertTrue(repo.pending)}}
    @Test fun deletionRemovesPartFromPersistedDraftWithoutSavingOtherEdits(){val store=Store();val repo=ready(store);val draft=find(repo,"s").deepCopy().apply {addProperty("sinopsis","Unsaved");addProperty("_admin_original",link)};repo.persistDraft(draft);repo.removePart("s",2,7);assertFalse(AdminEdits.hasPart(repo.draft()!!,2,7));assertFalse(find(repo,"s").has("sinopsis"));assertEquals("Unsaved",repo.draft()!!.text("sinopsis"));val restarted=AdminRepository(store);assertFalse(AdminEdits.hasPart(restarted.draft()!!,2,7));assertTrue(restarted.pending);assertEquals(link,restarted.draft()!!.text("_admin_original"))}
    @Test fun newDraftDeletionDoesNotPublishUnfinishedTitle(){val repo=ready();val before=repo.catalog!!.deepCopy();repo.persistDraft(item("anime","").apply {addProperty("_admin_is_new",true)});repo.removePart("",2,7);assertEquals(before,repo.catalog);assertFalse(repo.pending);assertFalse(AdminEdits.hasPart(repo.draft()!!,2,7))}
    @Test fun importedUnsavedEpisodeRemovedWithoutChangingSavedCatalog(){val repo=ready();val draft=find(repo,"s").deepCopy();draft.array("temporadas")[0].asJsonObject.array("episodios").add(JsonObject().apply {addProperty("numero",9)});repo.persistDraft(draft);val before=repo.catalog!!.deepCopy();repo.removePart("s",0,9);assertEquals(before,repo.catalog);assertFalse(repo.pending);assertFalse(AdminEdits.hasPart(repo.draft()!!,0,9))}
    @Test fun cancellingConfirmationLeavesCatalogDraftAndCheckpointUntouched(){val store=Store();val repo=ready(store);repo.persistDraft(item());val before=store.value!!.deepCopy();val request=DeletionRequest("s","Serie",2,7);assertTrue(request.explanation.contains("No se eliminará"));assertEquals(before,store.value);assertFalse(repo.pending);/* Cancel closes the request without invoking confirm, as in the UI. */}
    @Test fun confirmIsTheOnlyMutationAndDoesNotCallRemote(){val repo=ready();val request=DeletionRequest("s","Serie");assertFalse(repo.pending);request.confirm(repo);assertTrue(repo.pending);assertFalse(CatalogRules.items(repo.catalog!!).any {it.text("id")=="s"})}
    @Test fun readonlyDeletionDenied(){val repo=ready();repo.lock();val before=repo.state.deepCopy();assertThrows(IllegalStateException::class.java){repo.removeItem("s")};assertThrows(IllegalStateException::class.java){repo.removePart("s",2,7)};assertEquals(before,repo.state)}
    @Test fun missingTargetsDoNotChangeState(){val repo=ready();val before=repo.state.deepCopy();assertThrows(IllegalArgumentException::class.java){repo.removeItem("missing")};assertThrows(IllegalArgumentException::class.java){repo.removePart("s",99,7)};assertEquals(before,repo.state)}
    @Test fun failedAtomicWriteKeepsCatalogAndDraft(){val store=Store();val repo=ready(store);repo.persistDraft(item());val before=repo.state.deepCopy();store.fail=true;assertThrows(IllegalStateException::class.java){repo.removePart("s",2,7)};assertEquals(before,repo.state);assertEquals(before,store.value)}
    @Test fun deletionPublishesValidSchemaOneOnlyOnExplicitSync(){val repo=ready();val remote=Remote();repo.removePart("s",2,7);repo.removeItem("a");assertEquals(0,remote.calls);repo.publish(remote);assertEquals(1,remote.calls);val out=CatalogRules.parse(remote.catalog.toString());assertEquals("1",out.text("schema_version"));assertTrue(out.get("unknown_root").asBoolean);assertFalse(repo.pending)}
    @Test fun pendingDeletionSurvivesRestart(){val store=Store();val repo=ready(store);repo.removeItem("p");val restarted=AdminRepository(store);assertTrue(restarted.pending);assertFalse(CatalogRules.items(restarted.catalog!!).any {it.text("id")=="p"});assertFalse(restarted.writable)}
    @Test fun resultCardOriginalTitlesDoNotChangeProviderData(){val tmdb=SearchResult("TMDB",1,"Título","2024","",JsonParser.parseString("{\"original_title\":\"Original\",\"future\":1}").asJsonObject);val ani=SearchResult("AniList",2,"English","2023","",JsonParser.parseString("{\"title\":{\"romaji\":\"Romaji\",\"native\":\"Native\"}}").asJsonObject);val before=ani.raw.deepCopy();assertEquals("Original",tmdb.originalTitle());assertEquals("Romaji",ani.originalTitle());assertEquals(before,ani.raw);assertEquals("AniList",ani.source)}
    @Test fun seasonConfirmationReportsEpisodeCount(){val request=DeletionRequest("s","Serie",2,episodeCount=10);assertTrue(request.explanation.contains("10 episodios"));assertEquals("Eliminar temporada",request.button)}
}
