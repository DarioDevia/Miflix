package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

class PlaybackFullscreenTest {
    @Test fun fullscreenStaysActiveAcrossTheEpisodeDetailBridge() {
        assertTrue(playbackFullscreenActive(Screen.PLAYER, true, false))
        assertTrue(playbackFullscreenActive(Screen.DETAIL, true, true))
        assertTrue(playbackFullscreenActive(Screen.PLAYER, true, false))
    }

    @Test fun verticalPlaybackAndVerticalTransitionsNeverForceFullscreen() {
        assertFalse(playbackFullscreenActive(Screen.PLAYER, false, false))
        assertFalse(playbackFullscreenActive(Screen.DETAIL, false, true))
    }

    @Test fun leavingOrCancellingTheTransitionEndsFullscreen() {
        assertFalse(playbackFullscreenActive(Screen.DETAIL, true, false))
        assertFalse(playbackFullscreenActive(Screen.HOME, true, true))
        assertFalse(playbackFullscreenActive(Screen.DETAIL, false, true))
    }
}
