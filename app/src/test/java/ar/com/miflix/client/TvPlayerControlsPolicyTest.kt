package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

class TvPlayerControlsPolicyTest {
    @Test fun legacyPlayAndTracksRouteIsPreservedAndProgressIsReachable() {
        assertEquals(TvPlayerFocusTarget.TRACKS, tvPlayerFocusDestination(TvPlayerFocusTarget.PLAY, true, true))
        assertEquals(TvPlayerFocusTarget.PLAY, tvPlayerFocusDestination(TvPlayerFocusTarget.TRACKS, false, true))
        assertEquals(TvPlayerFocusTarget.PROGRESS, tvPlayerFocusDestination(TvPlayerFocusTarget.TRACKS, true, true))
        assertEquals(TvPlayerFocusTarget.TRACKS, tvPlayerFocusDestination(TvPlayerFocusTarget.PROGRESS, false, true))
    }
    @Test fun noTracksStillAllowsProgressAndReturnToPlay() {
        assertEquals(TvPlayerFocusTarget.PROGRESS, tvPlayerFocusDestination(TvPlayerFocusTarget.PLAY, true, false))
        assertEquals(TvPlayerFocusTarget.PLAY, tvPlayerFocusDestination(TvPlayerFocusTarget.PROGRESS, false, false))
    }
    @Test fun edgesDoNotEscapeControlsAndRemovedTracksRemainSafe() {
        assertEquals(TvPlayerFocusTarget.PLAY, tvPlayerFocusDestination(TvPlayerFocusTarget.PLAY, false, true))
        assertEquals(TvPlayerFocusTarget.PROGRESS, tvPlayerFocusDestination(TvPlayerFocusTarget.PROGRESS, true, true))
        assertEquals(TvPlayerFocusTarget.PROGRESS, tvPlayerFocusDestination(TvPlayerFocusTarget.TRACKS, true, false))
    }
    @Test fun leftRightAndMediaKeysKeepOriginalSeekPlayPauseBehavior() {
        assertEquals(TvRemoteAction.REWIND, tvPlaybackRemoteAction(21, true))
        assertEquals(TvRemoteAction.FORWARD, tvPlaybackRemoteAction(22, true))
        assertEquals(TvRemoteAction.PLAY_PAUSE, tvPlaybackRemoteAction(85, true))
        assertEquals(TvRemoteAction.PLAY, tvPlaybackRemoteAction(126, true))
        assertEquals(TvRemoteAction.PAUSE, tvPlaybackRemoteAction(127, true))
        assertNull(tvPlaybackRemoteAction(23, true))
    }
    @Test fun hiddenControlsRevealBeforeActionAndPanelBlocksTimer() {
        for (key in listOf(19,20,21,22,23,66))
            assertEquals(TvRemoteAction.SHOW_CONTROLS, tvPlaybackRemoteAction(key, false))
        assertEquals(4000L, TV_CONTROLS_TIMEOUT_MS)
        assertTrue(tvControlsAutoHide(true, true, false))
        assertFalse(tvControlsAutoHide(true, true, true))
        assertFalse(tvControlsAutoHide(true, false, false))
    }
    @Test fun unknownDurationDoesNotFocusAnUnusableProgressBar() {
        assertEquals(TvPlayerFocusTarget.PLAY, tvPlayerFocusDestination(TvPlayerFocusTarget.PLAY, true, false, false))
        assertEquals(TvPlayerFocusTarget.TRACKS, tvPlayerFocusDestination(TvPlayerFocusTarget.TRACKS, true, true, false))
    }
}
