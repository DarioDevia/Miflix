package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

class TvPlaybackControlsTest {
    @Test fun timeoutIsFourSecondsAndOnlyPlayingVisibleUnblockedCanHide() {
        assertEquals(4000L, TV_CONTROLS_TIMEOUT_MS)
        for (visible in listOf(true,false)) for (playing in listOf(true,false))
            for (blocked in listOf(true,false))
                assertEquals(visible && playing && !blocked, tvControlsAutoHide(visible,playing,blocked))
    }
    @Test fun hiddenDirectionAndOkRevealBeforeSeekingOrToggling() {
        for (key in listOf(19,20,21,22,23,66))
            assertEquals(TvRemoteAction.SHOW_CONTROLS,tvPlaybackRemoteAction(key,false))
    }
    @Test fun visibleLeftRightSeekWithoutAddingButtons() {
        assertEquals(TvRemoteAction.REWIND,tvPlaybackRemoteAction(21,true))
        assertEquals(TvRemoteAction.FORWARD,tvPlaybackRemoteAction(22,true))
        assertNull(tvPlaybackRemoteAction(23,true))
    }
    @Test fun standardMediaKeysRemainAvailableInBothStates() {
        for (visible in listOf(true,false)) {
            assertEquals(TvRemoteAction.PLAY_PAUSE,tvPlaybackRemoteAction(85,visible))
            assertEquals(TvRemoteAction.PLAY,tvPlaybackRemoteAction(126,visible))
            assertEquals(TvRemoteAction.PAUSE,tvPlaybackRemoteAction(127,visible))
            assertEquals(TvRemoteAction.REWIND,tvPlaybackRemoteAction(89,visible))
            assertEquals(TvRemoteAction.FORWARD,tvPlaybackRemoteAction(90,visible))
        }
    }
    @Test fun parentContentArtworkIsIndependentOfEpisodeProgressIdentity() {
        val title=Title(id="serie",titulo="Título principal",logoUrl="https://cdn.test/logo.png")
        val season=Season(numero=2);val episode=Episode(id="episode",numero=1,titulo="Episodio")
        assertEquals(ProgressKeys.episode(title,season,episode),
            ProgressKeys.episode(title.copy(logoUrl=null),season,episode))
        assertEquals("https://cdn.test/logo.png",tvDetailArtwork(title).logo)
    }
}
