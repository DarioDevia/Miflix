package ar.com.miflix.admin

import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class DraftPersistenceTest {
    private fun item(value:String)=JsonObject().apply {addProperty("telegram_url",value)}
    @Test fun rapidTypingWritesOnlyLatestAfterQuietPeriod()=runTest {
        val saved=mutableListOf<JsonObject?>()
        val q=DraftPersistence(backgroundScope,StandardTestDispatcher(testScheduler),{v,_->saved.add(v)}, {throw it})
        repeat(100) {q.offer(item("https://t.me/channel/$it"));advanceTimeBy(20)}
        assertTrue(saved.isEmpty());advanceTimeBy(500);runCurrent()
        assertEquals(listOf(item("https://t.me/channel/99")),saved)
        advanceTimeBy(5000);runCurrent();assertEquals(1,saved.size)
    }
    @Test fun commandFlushesBeforeManualSave()=runTest {
        val events=mutableListOf<String>()
        val q=DraftPersistence(backgroundScope,StandardTestDispatcher(testScheduler),{v,_->events.add(v!!.text("telegram_url"))},{throw it})
        q.offer(item("last"));q.command {events.add("save")}
        advanceTimeBy(1000);runCurrent();assertEquals(listOf("last","save"),events)
    }
    @Test fun discardCannotBeResurrectedByOldTimer()=runTest {
        var disk:JsonObject?=null
        val q=DraftPersistence(backgroundScope,StandardTestDispatcher(testScheduler),{v,_->disk=v},{throw it})
        q.offer(item("pending"));q.command {disk=null}
        advanceTimeBy(1000);runCurrent();assertNull(disk)
    }
    @Test fun failedWriteIsRetainedForExplicitRetry()=runTest {
        var fail=true;var disk:JsonObject?=item("old");var errors=0
        val q=DraftPersistence(backgroundScope,StandardTestDispatcher(testScheduler),{v,_->if(fail) error("disk full") else disk=v},{errors++})
        q.offer(item("latest"));advanceTimeBy(501);runCurrent()
        assertEquals(1,errors);assertEquals(item("old"),disk)
        fail=false;q.command {};assertEquals(item("latest"),disk)
    }
    @Test fun failedFlushPreventsCriticalAction()=runTest {
        var ran=false
        val q=DraftPersistence(backgroundScope,StandardTestDispatcher(testScheduler),{_,_->error("full")},{})
        q.offer(item("pending"))
        try {q.command {ran=true};fail("Expected write failure")} catch(_:IllegalStateException) {}
        assertFalse(ran)
    }
    @Test fun navigationAndBackgroundFlushWithoutWaiting500ms()=runTest {
        var writes=0
        val q=DraftPersistence(backgroundScope,StandardTestDispatcher(testScheduler),{_,_->writes++},{throw it})
        q.offer(item("pending"));q.flushSoon();runCurrent();assertEquals(1,writes)
        q.flushSoon();runCurrent();advanceTimeBy(1000);runCurrent();assertEquals(1,writes)
    }
    @Test fun verifiedProviderSnapshotCannotBeReassignedToManualEdit()=runTest {
        val saved=mutableListOf<Pair<String,Set<String>>>()
        val q=DraftPersistence(backgroundScope,StandardTestDispatcher(testScheduler),{v,p->saved.add(v!!.text("telegram_url") to p)},{throw it})
        q.offer(item("provider"),setOf("title/season/titulo"));q.offer(item("manual1"));q.offer(item("manual2"))
        q.command {}
        assertEquals(listOf("provider" to setOf("title/season/titulo"),"manual2" to emptySet<String>()),saved)
    }
    @Test fun samePendingContentDoesNotRestartQuietPeriod()=runTest {
        var writes=0
        val q=DraftPersistence(backgroundScope,StandardTestDispatcher(testScheduler),{_,_->writes++},{throw it})
        q.offer(item("same"));advanceTimeBy(300);q.offer(item("same"));advanceTimeBy(201);runCurrent();assertEquals(1,writes)
    }
    @Test fun inFlightWriteFinishesBeforeNewerEditAndNeverRunsConcurrently()=runBlocking {
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        val saved=java.util.Collections.synchronizedList(mutableListOf<String>())
        val q=DraftPersistence(scope,Dispatchers.IO,{v,_->
            if(v!!.text("telegram_url")=="old") {entered.countDown();check(release.await(5,TimeUnit.SECONDS))}
            saved.add(v.text("telegram_url"))
        },{throw it})
        try {
            q.offer(item("old"));q.flushSoon();assertTrue(entered.await(5,TimeUnit.SECONDS))
            q.offer(item("new"));release.countDown();q.command {saved.add("critical")}
            assertEquals(listOf("old","new","critical"),saved)
        } finally {release.countDown();scope.cancel()}
    }
}
