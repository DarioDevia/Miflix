package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

class TvFocusMemoryTest {
    @Test fun leavingRailRestoresLastPosterForEveryCategory() {
        for (type in listOf("pelicula", "serie", "anime")) {
            val memory = TvFocusMemory()
            var destination = ""
            memory.register("hero", true) { destination = "hero"; true }
            memory.register("$type:7", false) { destination = "$type:7"; true }
            memory.focused("$type:7")
            assertTrue(memory.restore())
            assertEquals("$type:7", destination)
        }
    }
    @Test fun removedLazyCardFallsBackToMountedEntry() {
        val memory = TvFocusMemory()
        var entered = false
        memory.register("search", true) { entered = true; true }
        memory.register("poster", false) { error("Disposed requester called") }
        memory.focused("poster")
        memory.unregister("poster")
        assertTrue(memory.restore())
        assertTrue(entered)
    }
    @Test fun staleEntryDoesNotBlockOtherContentAndEmptyScreenTerminates() {
        val memory = TvFocusMemory()
        assertFalse(memory.restore())
        memory.register("stale", true) { false }
        memory.register("visible-row", false) { true }
        assertTrue(memory.restore())
    }
    @Test fun movingBetweenRowsUpdatesReturnDestination() {
        val memory = TvFocusMemory()
        var destination = ""
        for (key in listOf("pelicula:1", "serie:3", "anime:2"))
            memory.register(key, false) { destination = key; true }
        memory.focused("serie:3")
        memory.focused("anime:2")
        assertTrue(memory.restore())
        assertEquals("anime:2", destination)
    }
    @Test fun missingBuildConfigurationIsNotAnAuthorizationFailure() {
        assertNotNull(tvTelegramSetupMessage(0, false))
        assertNotNull(tvTelegramSetupMessage(1, false))
        assertNotNull(tvTelegramSetupMessage(0, true))
        assertNull(tvTelegramSetupMessage(1, true))
    }
}
