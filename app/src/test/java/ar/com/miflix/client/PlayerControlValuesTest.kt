package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

class PlayerControlValuesTest {
    @Test fun scrubTargetsClampToKnownDuration() {
        assertEquals(0L, scrubPosition(-.2f, 100_000))
        assertEquals(25_000L, scrubPosition(.25f, 100_000))
        assertEquals(100_000L, scrubPosition(1.5f, 100_000))
        assertNull(scrubPosition(.5f, 0))
        assertNull(scrubPosition(.5f, -1))
    }

    @Test fun verticalThumbMapsTopMiddleBottomAndOutside() {
        assertEquals(1f, verticalControlFraction(8f, 100f, 8f), 0f)
        assertEquals(.5f, verticalControlFraction(50f, 100f, 8f), 0f)
        assertEquals(0f, verticalControlFraction(92f, 100f, 8f), 0f)
        assertEquals(1f, verticalControlFraction(-50f, 100f, 8f), 0f)
        assertEquals(0f, verticalControlFraction(200f, 100f, 8f), 0f)
        assertEquals(0f, verticalControlFraction(0f, 0f, 8f), 0f)
    }

    @Test fun allInteractionsPauseAndReleaseAllowsAutoHide() {
        assertTrue(canAutoHideControls(true, true, false, false))
        assertFalse(canAutoHideControls(true, true, false, true))
        assertTrue(canAutoHideControls(true, true, false, false))
        assertFalse(canAutoHideControls(false, true, false, false))
        assertFalse(canAutoHideControls(true, false, false, false))
        assertFalse(canAutoHideControls(true, true, true, false))
    }

    @Test fun brightnessDoesNotWriteOnEntryAndRestoresInheritedValueOnce() {
        val writes = mutableListOf<Float>()
        val brightness = TemporaryPlayerBrightness(-1f) { writes += it }
        assertTrue(writes.isEmpty())
        brightness.restore()
        assertTrue(writes.isEmpty())
        brightness.set(-.2f)
        brightness.set(.35f)
        brightness.set(1.2f)
        brightness.restore()
        brightness.restore()
        assertEquals(listOf(0f, .35f, 1f, -1f), writes)
    }

    @Test fun brightnessRestoresExistingOverride() {
        var windowA = .4f
        val brightness = TemporaryPlayerBrightness(windowA) { windowA = it }
        brightness.set(1f)
        assertEquals(1f, windowA, 0f)
        brightness.restore()
        assertEquals(.4f, windowA, 0f)
    }

    @Test fun volumeUsesDeviceRangeAndRoundTripsDiscreteLevels() {
        assertEquals(0, volumeIndex(-1f, 0, 15))
        assertEquals(8, volumeIndex(.5f, 0, 15))
        assertEquals(15, volumeIndex(2f, 0, 15))
        assertEquals(2, volumeIndex(0f, 2, 10))
        assertEquals(6, volumeIndex(.5f, 2, 10))
        assertEquals(10, volumeIndex(1f, 2, 10))
        for (index in 0..15) {
            assertEquals(index, volumeIndex(volumeFraction(index, 0, 15), 0, 15))
        }
        assertEquals(0f, volumeFraction(0, 0, 0), 0f)
    }

    @Test fun nonFiniteInputNeverProducesInvalidBrightness() {
        assertEquals(0f, controlFraction(Float.NaN), 0f)
        assertEquals(0f, controlFraction(Float.POSITIVE_INFINITY), 0f)
    }
}
