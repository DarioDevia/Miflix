package ar.com.miflix.client

import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class TvPlaybackCoverageObserverTest {
    private var now=0L
    private var position=0L
    private var playing=true
    private var failReads=false
    private val calls=mutableListOf<String>()
    private val player=Proxy.newProxyInstance(Player::class.java.classLoader,arrayOf(Player::class.java)) { _,method,_ ->
        calls+=method.name
        check(!failReads)
        when(method.name) {
            "getCurrentPosition" -> position
            "getDuration" -> 100_000L
            "isPlaying" -> playing
            "getPlaybackParameters" -> PlaybackParameters(1f)
            else -> error("Unexpected player operation: ${method.name}")
        }
    } as Player
    private val store=TvPersonalLibraryStore(object:TvLibraryStorage {
        var json:String?=null
        override fun read()=json
        override fun write(json:String) { this.json=json }
    })
    @Test fun actualPlaybackCanMarkWatchedWithoutAnyPlayerMutation() {
        val observer=TvPlaybackCoverageObserver(player,"title:a",store) { now }
        repeat(95) { now+=1000;position+=1000;observer.sample() }
        assertTrue(store.isWatched("title:a"))
        observer.finish()
        assertTrue(calls.all { it in setOf("getCurrentPosition","getDuration","isPlaying","getPlaybackParameters") })
        failReads=true
        observer.sample();observer.finish() // Closed observers never touch a released player.
    }
    @Test fun seekAndBackgroundGapsAreNotCoverageAndUnmarkStartsFresh() {
        val observer=TvPlaybackCoverageObserver(player,"title:a",store) { now }
        now=1000;position=95_000;observer.sample()
        now=2000;position=100_000;observer.sample()
        assertFalse(store.isWatched("title:a"))
        store.setWatched("title:a",true);store.setWatched("title:a",false)
        now=3000;observer.sample()
        assertFalse(store.isWatched("title:a"))
        now=30_000;position=100_000;playing=false;observer.sample();observer.finish()
        assertFalse(store.isWatched("title:a"))
    }
}
