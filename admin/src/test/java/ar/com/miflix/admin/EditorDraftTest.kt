package ar.com.miflix.admin

import com.google.gson.*
import org.junit.Assert.*
import org.junit.Test

class EditorDraftTest {
    private fun series()=JsonParser.parseString("""{"id":"s","tipo":"serie","fecha_agregado":"2020-01-01T00:00:00Z","unknown":{"keep":true},"temporadas":[{"numero":1,"episodios":[{"numero":1,"titulo":"old","future":[1,2]},{"numero":2,"titulo":"other"}]},{"numero":2,"episodios":[]}]}""").asJsonObject
    @Test fun fieldDoesNotCopySeasonsOrMutateOldSnapshot() {
        val old=series();val before=old.deepCopy();val next=EditorDraft.field(old,"titulo","nuevo")
        assertEquals(before,old);assertSame(old.get("temporadas"),next.get("temporadas"));assertEquals("nuevo",next.text("titulo"))
    }
    @Test fun episodeCopiesOnlyEditedPathAndPreservesUnknowns() {
        val old=series();val before=old.deepCopy();val next=EditorDraft.episode(old,1,1,"titulo","nuevo")
        assertEquals(before,old);assertSame(old.array("temporadas")[1],next.array("temporadas")[1])
        assertSame(old.array("temporadas")[0].asJsonObject.array("episodios")[1],next.array("temporadas")[0].asJsonObject.array("episodios")[1])
        assertEquals("nuevo",next.array("temporadas")[0].asJsonObject.array("episodios")[0].asJsonObject.text("titulo"))
        assertEquals(old.get("unknown"),next.get("unknown"));assertEquals(old.get("fecha_agregado"),next.get("fecha_agregado"))
    }
    @Test fun successiveEpisodeSnapshotsRemainIndependent() {
        val first=series();val second=EditorDraft.episode(first,1,1,"telegram_url","one");val third=EditorDraft.episode(second,1,1,"telegram_url","two")
        assertEquals("",first.array("temporadas")[0].asJsonObject.array("episodios")[0].asJsonObject.text("telegram_url"))
        assertEquals("one",second.array("temporadas")[0].asJsonObject.array("episodios")[0].asJsonObject.text("telegram_url"))
        assertEquals("two",third.array("temporadas")[0].asJsonObject.array("episodios")[0].asJsonObject.text("telegram_url"))
    }
    @Test fun genreAndBackdropRulesRemainUnchanged() {
        val old=series();val genres=EditorDraft.field(old,"generos"," Acción, , Drama ")
        assertEquals(listOf("Acción","Drama"),genres.array("generos").map {it.asString})
        val image=EditorDraft.field(old,"fondo_url"," https://example.org/image ")
        assertEquals("https://example.org/image",image.text("backdrop_url"))
        assertFalse(EditorDraft.field(image,"fondo_url","").has("backdrop_url"))
    }
    @Test fun seasonEditsDoNotMutateEpisodes() {
        val old=series();val next=EditorDraft.season(old,1,"nombre","Uno")
        assertEquals("",old.array("temporadas")[0].asJsonObject.text("nombre"))
        assertEquals("Uno",next.array("temporadas")[0].asJsonObject.text("nombre"))
        assertSame(old.array("temporadas")[0].asJsonObject.get("episodios"),next.array("temporadas")[0].asJsonObject.get("episodios"))
    }
}
