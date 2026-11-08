package ar.com.miflix.admin

import com.google.gson.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Synthetic JVM/temporary filesystem measurements, not Android frame latency. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest=Config.NONE,sdk=[28])
@OptIn(ExperimentalCoroutinesApi::class)
class EditorPersistencePerformanceTest {
    private class Meter(val file:File):StateStore {
        val backing=FileStateStore(file)
        var writes=0;var nanos=0L
        override fun read()=backing.read()
        override fun write(state:JsonObject) {val t=System.nanoTime();backing.write(state);nanos+=System.nanoTime()-t;writes++}
    }
    private fun catalog(size:Int)=JsonObject().apply {
        addProperty("schema_version",1);addProperty("future_root","preserve")
        add("colecciones",JsonArray())
        add("items",JsonArray().apply {repeat(size) {n->add(JsonObject().apply {
            addProperty("id","p$n");addProperty("tipo","pelicula");addProperty("titulo","Película $n")
            addProperty("sinopsis","Texto sintético de prueba. ".repeat(20));addProperty("fecha_agregado","2020-01-01T00:00:00Z")
            addProperty("telegram_url","https://t.me/synthetic/${n+1}");addProperty("future_item","preserve")
        })}})
    }
    @Test fun compare400And1000MoviesAndRecoverLatestDraft()=runTest {
        val rows=JsonArray()
        for(size in listOf(400,1000)) {
            val seed=JsonObject().apply {add("catalog",catalog(size));addProperty("pending",false)}
            val files=List(2) {File.createTempFile("miflix-synthetic-",".json").apply {writeText(seed.toString())}}
            try {
                val oldStore=Meter(files[0]);val old=AdminRepository(oldStore)
                val newStore=Meter(files[1]);val next=AdminRepository(newStore)
                oldStore.writes=0;oldStore.nanos=0;newStore.writes=0;newStore.nanos=0
                val initial=CatalogRules.items(old.catalog!!).first().deepCopy()
                val link="https://t.me/synthetic/9876543210"
                var oldPersistNanos=0L;var newPersistNanos=0L
                var draft=initial.deepCopy();val before=System.nanoTime()
                link.indices.forEach {i ->draft=AdminEdits.telegramLink(draft,link.take(i+1));val start=System.nanoTime();old.persistDraft(draft);oldPersistNanos+=System.nanoTime()-start;draft=old.draft()!!}
                val oldInput=System.nanoTime()-before
                var current=initial.deepCopy()
                val q=DraftPersistence(backgroundScope,StandardTestDispatcher(testScheduler),{v,p->val start=System.nanoTime();next.persistDraft(v,p);newPersistNanos+=System.nanoTime()-start},{throw it})
                val after=System.nanoTime()
                link.indices.forEach {i ->current=EditorDraft.field(current,"telegram_url",link.take(i+1)).apply {addProperty("_admin_original",link.take(i+1))};q.offer(current)}
                val newInput=System.nanoTime()-after
                assertEquals(0,newStore.writes);advanceTimeBy(501);runCurrent()
                assertEquals(link.length,oldStore.writes);assertEquals(1,newStore.writes)
                assertEquals(old.catalog,next.catalog);assertEquals(old.draft(),next.draft())
                assertEquals(next.draft(),AdminRepository(Meter(files[1])).draft());assertFalse(next.pending)
                rows.add(JsonObject().apply {
                    addProperty("movies",size);addProperty("characters",link.length)
                    addProperty("baseline_writes",oldStore.writes);addProperty("new_writes",newStore.writes)
                    addProperty("baseline_input_ms",oldInput/1e6);addProperty("new_input_ms",newInput/1e6)
                    addProperty("baseline_repository_persistence_ms",oldPersistNanos/1e6);addProperty("new_repository_persistence_ms",newPersistNanos/1e6)
                    addProperty("baseline_file_serialization_and_sync_ms",oldStore.nanos/1e6)
                    addProperty("new_file_serialization_and_sync_ms",newStore.nanos/1e6)
                })
            } finally {files.forEach {it.delete()}}
        }
        File("build/editor-persistence-measurements.json").apply {parentFile?.mkdirs();writeText(rows.toString())}
    }
    @Test fun sameDurableDraftDoesNotWriteAgain() {
        val file=File.createTempFile("miflix-identical-",".json")
        try {file.writeText("{}");val store=Meter(file);val repo=AdminRepository(store)
            val item=CatalogRules.items(catalog(1)).first();repo.persistDraft(item);val count=store.writes
            repo.persistDraft(item.deepCopy());assertEquals(count,store.writes)
        } finally {file.delete()}
    }
    @Test fun longSynopsisAndLargeEpisodeListPreserveAllOtherFields() {
        val item=CatalogRules.items(catalog(1)).first().deepCopy().apply {addProperty("tipo","serie");add("temporadas",JsonArray().apply {add(JsonObject().apply {addProperty("numero",1);add("episodios",JsonArray().apply {repeat(1000) {add(JsonObject().apply {addProperty("numero",it+1);addProperty("titulo","E${it+1}");addProperty("future","keep")})}})})})}
        val before=item.deepCopy();val synopsis="Sinopsis extensa. ".repeat(1000)
        val after=EditorDraft.episode(EditorDraft.field(item,"sinopsis",synopsis),1,900,"titulo","Nuevo título")
        assertEquals(before,item);assertEquals(synopsis,after.text("sinopsis"))
        assertEquals(1000,after.array("temporadas")[0].asJsonObject.array("episodios").size())
        assertEquals(item.get("fecha_agregado"),after.get("fecha_agregado"))
    }
}
