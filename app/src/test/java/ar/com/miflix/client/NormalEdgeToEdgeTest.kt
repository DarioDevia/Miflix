package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

class NormalEdgeToEdgeTest {
    @Test fun normalScreensUseEdgeToEdge() {
        for (screen in Screen.values().filter { it != Screen.PLAYER })
            assertTrue(screen.name, normalEdgeToEdgeActive(screen, false, false))
    }
    @Test fun playerNeverUsesTheNormalWindowEffect() {
        assertFalse(normalEdgeToEdgeActive(Screen.PLAYER, false, false))
        assertFalse(normalEdgeToEdgeActive(Screen.PLAYER, true, false))
    }
    @Test fun fullscreenEpisodeBridgeIsAlsoExcluded() {
        val fullscreen = playbackFullscreenActive(Screen.DETAIL, true, true)
        assertTrue(fullscreen)
        assertFalse(normalEdgeToEdgeActive(Screen.DETAIL, fullscreen, false))
    }
    @Test fun introDoesNotShareActivityWindowOwnership() {
        for (screen in Screen.values())
            assertFalse(normalEdgeToEdgeActive(screen, false, true))
    }
    @Test fun leavingPlaybackRestoresOnlyNormalScreenPolicy() {
        assertTrue(normalEdgeToEdgeActive(Screen.HOME, false, false))
        assertFalse(normalEdgeToEdgeActive(Screen.PLAYER, false, false))
        assertFalse(normalEdgeToEdgeActive(Screen.PLAYER, true, false))
        assertFalse(normalEdgeToEdgeActive(Screen.PLAYER, false, false))
        assertTrue(normalEdgeToEdgeActive(Screen.DETAIL, false, false))
    }
}
