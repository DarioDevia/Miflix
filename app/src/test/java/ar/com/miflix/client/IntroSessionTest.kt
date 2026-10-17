package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

class IntroSessionTest {
    @Test fun freshLaunchPlaysIntro() { assertTrue(IntroSession(false).active) }
    @Test fun restoringActivityNeverRestartsIntro() { assertFalse(IntroSession(true).active) }
    @Test fun completionErrorOrBackgroundEndsSessionIdempotently() {
        val session = IntroSession(false)
        session.finish()
        assertFalse(session.active)
        session.finish()
        assertFalse(session.active)
    }
    @Test fun onlyANewLaunchCanPlayAgain() {
        val session = IntroSession(false)
        session.finish()
        assertFalse(session.active)
        assertTrue(IntroSession(false).active)
    }
}
