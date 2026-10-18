package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

class TvIntroPolicyTest {
    @Test fun freshProcessPlaysExactlyOnceAcrossNavigationAndActivityRecreation() {
        val session=TvIntroSession()
        assertTrue(session.claim(false))
        repeat(5) { assertFalse(session.claim(false)) } // Detail, player, trailer, Home/recomposition.
        assertFalse(session.claim(true))
    }
    @Test fun restorationDoesNotInterruptAnExistingTaskEvenAfterProcessDeath() {
        val restored=TvIntroSession()
        assertFalse(restored.claim(true))
        assertFalse(restored.claim(false))
    }
    @Test fun aRealFreshProcessCanPlayAgain() {
        assertTrue(TvIntroSession().claim(false))
        assertTrue(TvIntroSession().claim(false))
    }
    @Test fun concurrentClaimsCannotStartTwoIntros() {
        val session=TvIntroSession()
        val results=java.util.Collections.synchronizedList(mutableListOf<Boolean>())
        val threads=(1..8).map { Thread { results.add(session.claim(false)) }.apply { start() } }
        threads.forEach { it.join() }
        assertEquals(1,results.count { it })
    }
    @Test fun finishReleasesBeforeHomeAndDuplicateCallbacksAreIgnored() {
        val events=mutableListOf<String>()
        val life=TvIntroLifetime({events+="release"},{events+="Home"})
        life.finish();life.finish();life.dispose()
        assertTrue(life.closed)
        assertEquals(listOf("release","Home"),events)
    }
    @Test fun backgroundCancellationCompletesOnceAndCannotRestart() {
        var releases=0;var completions=0
        val life=TvIntroLifetime({releases++},{completions++})
        life.finish();life.finish();life.dispose()
        assertEquals(1,releases);assertEquals(1,completions)
    }
    @Test fun disposalReleasesWithoutNavigatingFromAnObsoleteActivity() {
        var releases=0;var completions=0
        val life=TvIntroLifetime({releases++},{completions++})
        life.dispose();life.dispose();life.finish()
        assertEquals(1,releases);assertEquals(0,completions)
    }
    @Test fun releaseFailureStillAllowsHomeAndRemainsIdempotent() {
        var completed=0
        val life=TvIntroLifetime({throw IllegalStateException("synthetic")},{completed++})
        runCatching { life.finish() }
        life.finish();life.dispose()
        assertEquals(1,completed)
    }
    @Test fun okEnterAndBackAreSkipKeysButHomeIsNot() {
        listOf(4,23,66,160).forEach { assertTrue(tvIntroSkipKey(it)) }
        listOf(3,19,20,21,22,85).forEach { assertFalse(tvIntroSkipKey(it)) }
    }
    @Test fun decoderFailureAndTimeoutShareSafeCompletion() {
        var completed=0
        val life=TvIntroLifetime({}, {completed++})
        life.finish();life.finish()
        assertEquals(1,completed)
        assertEquals(10_000L,TV_INTRO_TIMEOUT_MS)
        assertEquals("miflix_tv_intro.mp4",TV_INTRO_ASSET)
    }
}
