package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

class TvRemoteTest {
    @Test fun hiddenControlsCanBeRecoveredWithEveryDpadDirectionAndSelect() {
        listOf(19, 20, 21, 22, 23, 66).forEach {
            assertEquals(TvRemoteAction.SHOW_CONTROLS, tvRemoteAction(it, false))
        }
    }
    @Test fun visibleControlsLeaveNavigationAndSelectToFocusedButtons() {
        listOf(19, 20, 21, 22, 23, 66).forEach { assertNull(tvRemoteAction(it, true)) }
    }
    @Test fun mediaKeysWorkRegardlessOfControlVisibility() {
        listOf(false, true).forEach { visible ->
            assertEquals(TvRemoteAction.PLAY_PAUSE, tvRemoteAction(85, visible))
            assertEquals(TvRemoteAction.PLAY, tvRemoteAction(126, visible))
            assertEquals(TvRemoteAction.PAUSE, tvRemoteAction(127, visible))
            assertEquals(TvRemoteAction.REWIND, tvRemoteAction(89, visible))
            assertEquals(TvRemoteAction.FORWARD, tvRemoteAction(90, visible))
            assertNull(tvRemoteAction(4, visible)) // Back remains an Android/Compose action.
            assertNull(tvRemoteAction(24, visible)) // Volume remains a system action.
        }
    }
}
