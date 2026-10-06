package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

class ProgressScrubTest {
    @Test fun tapAtQuarterAndThreeQuartersCommitsExpectedTarget() {
        val scrub = ProgressScrub()
        scrub.preview(.25f)
        assertEquals(25_000L, scrub.finish(100_000))
        scrub.preview(.75f)
        assertEquals(75_000L, scrub.finish(100_000))
    }

    @Test fun forwardDragOnlyKeepsProvisionalValueUntilFinish() {
        val scrub = ProgressScrub()
        assertNull(scrub.fraction)
        listOf(.2f, .4f, .6f, .75f).forEach {
            scrub.preview(it)
            assertEquals(it, scrub.fraction!!, 0f)
        }
        assertEquals(75_000L, scrub.finish(100_000))
        assertNull(scrub.fraction)
        assertNull(scrub.finish(100_000))
    }

    @Test fun backwardDragCommitsOnlyLastProvisionalValue() {
        val scrub = ProgressScrub()
        listOf(.8f, .6f, .4f, .25f).forEach(scrub::preview)
        assertEquals(.25f, scrub.fraction!!, 0f)
        assertEquals(25_000L, scrub.finish(100_000))
        assertNull(scrub.finish(100_000))
    }

    @Test fun targetsAreBoundedByZeroAndDuration() {
        val scrub = ProgressScrub()
        scrub.preview(-1f)
        assertEquals(0f, scrub.fraction!!, 0f)
        assertEquals(0L, scrub.finish(100_000))
        scrub.preview(2f)
        assertEquals(1f, scrub.fraction!!, 0f)
        assertEquals(100_000L, scrub.finish(100_000))
    }

    @Test fun unknownDurationDoesNotSeekAndClearsPreview() {
        val scrub = ProgressScrub()
        scrub.preview(.5f)
        assertNull(scrub.finish(-1))
        assertNull(scrub.fraction)
        assertNull(scrub.finish(100_000))
        scrub.preview(.5f)
        assertNull(scrub.finish(0))
    }

    @Test fun provisionalInteractionProtectsAutoHideUntilFinish() {
        val scrub = ProgressScrub()
        assertTrue(canAutoHideControls(true, true, false, scrub.fraction != null))
        scrub.preview(.5f)
        assertFalse(canAutoHideControls(true, true, false, scrub.fraction != null))
        scrub.finish(100_000)
        assertTrue(canAutoHideControls(true, true, false, scrub.fraction != null))
    }
}
