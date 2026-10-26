package ar.com.miflix.admin

import com.google.gson.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest=Config.NONE,sdk=[28])
class MetadataRecoveryTest {
    private fun json(s: String)=JsonParser.parseString(s).asJsonObject
    private fun item()=json("""{"id":"serie-1","tipo":"serie","titulo":"Serie","fecha_agregado":"2025-01-01T10:00:00Z","telegram_url":"https://t.me/source/1","unknown":{"keep":true},"temporadas":[{"numero":1,"nombre":"Temporada 1","episodios":[{"numero":1,"tmdb_id":11,"titulo":"Episodio 1","sinopsis":"","imagen_url":"https://fixture.test/old.jpg","duracion":45,"telegram_url":"https://t.me/source/2","id":"play-1"}]}]}""")
    private fun data(provider: String="TMDB", ep: String="""{"numero":1,"tmdb_id":11,"titulo":"El árbol","sinopsis":"<p>Sinopsis &amp; acción.</p>","duracion":46}""") =
        RecoveryData(RecoveryCandidate(provider,5,"Serie","2025","https://www.tvmaze.com/shows/5","en-US",JsonObject()),JsonObject(),
            listOf(json("""{"numero":1,"nombre":"Nombre real","episodios":[$ep]}""")))
    private fun plan()=MetadataRecovery.compare(item(),data())
    private fun defaults(p: RecoveryPlan)=p.changes.filter {it.initiallySelected}.map {it.path.key}.toSet()
    @Test fun missingTitlesAndSynopsisSelectedButRealImageAndRuntimeUntouched() {
        val p=plan();val selected=defaults(p)
        assertTrue("1/1/titulo" in selected);assertTrue("1/1/sinopsis" in selected);assertFalse("1/1/duracion" in selected)
        val after=MetadataRecovery.apply(p,selected,emptySet(),false)
        val old=p.before.array("temporadas")[0].asJsonObject.array("episodios")[0].asJsonObject
        val fresh=after.array("temporadas")[0].asJsonObject.array("episodios")[0].asJsonObject
        assertEquals("El árbol",fresh.text("titulo"));assertEquals("Sinopsis & acción.",fresh.text("sinopsis"))
        for(key in listOf("telegram_url","id","tmdb_id","duracion","imagen_url")) assertEquals(old.get(key),fresh.get(key))
        assertEquals(p.before.get("fecha_agregado"),after.get("fecha_agregado"));assertEquals(p.before.get("unknown"),after.get("unknown"))
    }
    @Test fun oneCheckboxChangesExactlyOneFieldAndProvenance() {
        val p=plan();val out=MetadataRecovery.apply(p,setOf("1/1/sinopsis"),emptySet(),false)
        val expected=p.before.deepCopy();MetadataRecovery.target(expected,RecoveryPath(1,1,"sinopsis")).addProperty("sinopsis","Sinopsis & acción.")
        out.remove("fuentes_metadata");assertEquals(expected,out)
    }
    @Test fun comparingDoesNotMutateInputsOrCatalog() {val before=item();val snapshot=before.deepCopy();val incoming=data();MetadataRecovery.compare(before,incoming);assertEquals(snapshot,before);assertEquals("<p>Sinopsis &amp; acción.</p>",incoming.seasons.single().array("episodios")[0].asJsonObject.text("sinopsis"))}
    @Test fun blankAndGenericNewValuesNotOffered() {
        val p=MetadataRecovery.compare(item(),data(ep="""{"numero":1,"tmdb_id":11,"titulo":"Episode 1","sinopsis":"<p> </p>","duracion":0,"fecha_emision":"unknown"}"""))
        assertTrue(p.changes.none {it.path.episode!=null})
    }
    @Test fun knownGenericNamesDetected() {listOf("Episodio 1","Episode 12","Capítulo 4","Season 1","TBA","Sin título").forEach {assertTrue(it,MetadataRecovery.generic(it))};assertFalse(MetadataRecovery.generic("La luz"))}
    @Test fun htmlUnicodeAndEntitiesAreNormalizedWithoutTranslation() {assertEquals("Árbol & acción © ñ",MetadataRecovery.clean("<p>Árbol &amp; acción &copy; &#241;</p>"));assertEquals("Texto",MetadataRecovery.clean("<script>alert(1)</script><p>Texto</p>"))}
    @Test fun existingRealTitleRemainsInitiallyUnchecked() {val before=item();MetadataRecovery.target(before,RecoveryPath(1,1,"titulo")).addProperty("titulo","Nombre manual");assertFalse(MetadataRecovery.compare(before,data()).changes.first {it.path.key=="1/1/titulo"}.initiallySelected)}
    @Test fun tvmazeRequiresManualEpisodeCorrespondence() {val p=MetadataRecovery.compare(item(),data("TVmaze"));assertTrue(p.changes.filter {it.path.episode!=null}.none {it.initiallySelected});assertThrows(IllegalArgumentException::class.java){MetadataRecovery.apply(p,setOf("1/1/titulo"),emptySet(),true)}}
    @Test fun tvmazeLicenseCannotBeSilentlySkipped() {val p=MetadataRecovery.compare(item(),data("TVmaze"));assertThrows(IllegalArgumentException::class.java){MetadataRecovery.apply(p,setOf("1/1/titulo"),setOf("1/1"),false)}}
    @Test fun licenseAndFieldAttributionSurvivePublicJson() {val p=MetadataRecovery.compare(item(),data("TVmaze"));val out=MetadataRecovery.apply(p,setOf("1/1/titulo"),setOf("1/1"),true);val root=json("""{"schema_version":1,"items":[]}""");root.array("items").add(out);val public=CatalogRules.publicCopy(root);assertTrue(public.toString().contains(MetadataRecovery.LICENSE));assertTrue(public.toString().contains("https://www.tvmaze.com/shows/5"))}
    @Test fun tmdbWrongEpisodeIdExcluded() {val p=MetadataRecovery.compare(item(),data(ep="""{"numero":1,"tmdb_id":99,"titulo":"Otro"}"""));assertTrue(p.changes.none {it.path.episode!=null});assertTrue(p.warnings.isNotEmpty())}
    @Test fun differentEpisodeNumbersExcludedWithoutCreatingAnything() {val p=MetadataRecovery.compare(item(),data(ep="""{"numero":2,"tmdb_id":11,"titulo":"Otro"}"""));assertTrue(p.changes.none {it.path.episode!=null});assertEquals(1,p.before.array("temporadas")[0].asJsonObject.array("episodios").size())}
    @Test fun missingSeasonIsNeverCreated() {val incoming=data().copy(seasons=listOf(json("""{"numero":2,"episodios":[{"numero":1,"titulo":"Real"}]}""")));val p=MetadataRecovery.compare(item(),incoming);assertTrue(p.changes.isEmpty());assertTrue(p.warnings.isNotEmpty())}
    @Test fun duplicateSeasonsExcluded() {val d=data();val p=MetadataRecovery.compare(item(),d.copy(seasons=d.seasons+d.seasons));assertTrue(p.changes.isEmpty())}
    @Test fun duplicateEpisodeNumbersExcluded() {val d=data();val s=d.seasons.single().deepCopy();s.array("episodios").add(s.array("episodios")[0].deepCopy());assertTrue(MetadataRecovery.compare(item(),d.copy(seasons=listOf(s))).changes.none {it.path.episode!=null})}
    @Test fun seasonZeroNeverAutomatic() {val before=item();before.array("temporadas")[0].asJsonObject.addProperty("numero",0);val d=data();d.seasons.single().addProperty("numero",0);val p=MetadataRecovery.compare(before,d);assertTrue(p.changes.filter {it.path.episode!=null}.none {it.initiallySelected})}
    @Test fun specialInRegularSeasonExcluded() {val p=MetadataRecovery.compare(item(),data("TVmaze","""{"numero":1,"titulo":"Especial","_special":true}"""));assertTrue(p.changes.none {it.path.episode!=null})}
    @Test fun tmdbWithoutExistingEpisodeIdRequiresManualMapping() {val before=item();MetadataRecovery.target(before,RecoveryPath(1,1,"titulo")).remove("tmdb_id");assertFalse(MetadataRecovery.compare(before,data()).changes.first {it.path.key=="1/1/titulo"}.safe)}
    @Test fun invalidDurationsAndDatesNotInvented() {for(v in listOf("0","-1","no","999999")) assertNull(MetadataRecovery.valid("duracion",JsonPrimitive(v)));assertNull(MetadataRecovery.valid("fecha_emision",JsonPrimitive("2025")));assertNotNull(MetadataRecovery.valid("fecha_emision",JsonPrimitive("2025-02-01")))}
    @Test fun unsafeImageUrlsAreNotOffered() {for(v in listOf("http://image.test/a","javascript:alert(1)","https://user:pass@image.test/x")) assertNull(MetadataRecovery.valid("imagen_url",JsonPrimitive(v)))}
    @Test fun protectedFieldsCannotBeTargeted() {for(f in listOf("id","telegram_url","fecha_agregado","tmdb_id")) assertThrows(IllegalArgumentException::class.java){MetadataRecovery.target(item(),RecoveryPath(field=f))}}
    @Test fun undoRestoresSelectedFieldsAndLeavesLaterUnrelatedEdits() {val p=plan();val selected=setOf("1/1/titulo");val after=MetadataRecovery.apply(p,selected,emptySet(),false);val backup=MetadataRecovery.backup(p,selected,after);after.addProperty("calidad","1080p");val restored=MetadataRecovery.undo(after,backup);val expected=p.before.deepCopy().apply {addProperty("calidad","1080p")};assertEquals(expected,restored)}
    @Test fun undoDoesNotClobberLaterChangesToSameField() {val p=plan();val after=MetadataRecovery.apply(p,setOf("1/1/titulo"),emptySet(),false);val backup=MetadataRecovery.backup(p,setOf("1/1/titulo"),after);MetadataRecovery.target(after,RecoveryPath(1,1,"titulo")).addProperty("titulo","Otra edición");assertThrows(IllegalArgumentException::class.java){MetadataRecovery.undo(after,backup)}}
    @Test fun atomicBackupAndDraftSurviveRestartAndCanBeUndoneAfterSave() {
        val store=AdminTest.Memory();val repo=AdminRepository(store);val root=json("""{"schema_version":1,"items":[]}""").apply {array("items").add(item())};repo.refresh(AdminTest.Fake(root))
        val p=plan();val selected=setOf("1/1/titulo");val after=MetadataRecovery.apply(p,selected,emptySet(),false)
        repo.stageRecovery(after,MetadataRecovery.backup(p,selected,after));assertFalse(repo.pending);assertEquals(root,repo.catalog)
        repo.saveItem(after);repo.persistDraft(null)
        val restarted=AdminRepository(store);assertTrue(restarted.canUndoRecovery(after));restarted.refresh(AdminTest.Fake(root))
        val reverted=restarted.undoRecovery(after);assertEquals(p.before,reverted)
        assertEquals("2025-01-01T10:00:00Z",CatalogRules.items(restarted.catalog!!).single().text("fecha_agregado"))
    }
    @Test fun failedAtomicStageLeavesPreviousDraftAndCatalogUnchanged() {
        val store=object:StateStore {var value:JsonObject?=null;var fail=false;override fun read()=value?.deepCopy();override fun write(state:JsonObject){check(!fail);value=state.deepCopy()}}
        val repo=AdminRepository(store);repo.refresh(AdminTest.Fake(json("""{"schema_version":1,"items":[]}""")));repo.persistDraft(item());val before=store.value!!.deepCopy();store.fail=true
        val p=plan();val after=MetadataRecovery.apply(p,setOf("1/1/titulo"),emptySet(),false)
        assertThrows(IllegalStateException::class.java){repo.stageRecovery(after,MetadataRecovery.backup(p,setOf("1/1/titulo"),after))};assertEquals(before,store.value);assertEquals(item(),repo.draft())
    }
    @Test fun discardingARevertedDraftDoesNotDestroyTheSavedRecordBackup() {
        val store=AdminTest.Memory();val repo=AdminRepository(store)
        val root=json("""{"schema_version":1,"items":[]}""").apply {array("items").add(item())};repo.refresh(AdminTest.Fake(root))
        val p=plan();val after=MetadataRecovery.apply(p,setOf("1/1/titulo"),emptySet(),false)
        repo.stageRecovery(after,MetadataRecovery.backup(p,setOf("1/1/titulo"),after));repo.saveItem(after);repo.persistDraft(null)
        val reverted=repo.undoRecovery(after);assertFalse(repo.canUndoRecovery(reverted));repo.persistDraft(null)
        assertTrue(AdminRepository(store).canUndoRecovery(CatalogRules.items(repo.catalog!!).single()))
    }
    @Test fun movieAndAnimeGeneralFieldsUseSameSelectionProtection() {for(type in listOf("pelicula","anime")){val before=item().apply {addProperty("tipo",type)};val d=data().copy(fields=json("""{"titulo":"Nuevo","sinopsis":"Descripción útil","poster_url":"https://image.test/new.jpg"}"""),seasons=emptyList());val p=MetadataRecovery.compare(before,d);val out=MetadataRecovery.apply(p,setOf("title/season/sinopsis"),emptySet(),false);assertEquals("Serie",out.text("titulo"));assertEquals(before.get("temporadas"),out.get("temporadas"));assertEquals("Descripción útil",out.text("sinopsis"))}}
}
