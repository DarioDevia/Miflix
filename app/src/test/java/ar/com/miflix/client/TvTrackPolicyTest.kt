package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

class TvTrackPolicyTest {
    @Test fun dpadTraversesSubtitleAudioAndCloseRowsInBothDirections() {
        var index = 0
        for (expected in 1..6) {
            index = tvPanelNextIndex(index, 7, true)
            assertEquals(expected, index)
        }
        assertEquals(6, tvPanelNextIndex(index, 7, true))
        for (expected in 5 downTo 0) {
            index = tvPanelNextIndex(index, 7, false)
            assertEquals(expected, index)
        }
        assertEquals(0, tvPanelNextIndex(index, 7, false))
    }
    @Test fun emptyOrSingleRowIsSafe() {
        assertEquals(0, tvPanelNextIndex(0, 0, true))
        assertEquals(0, tvPanelNextIndex(0, 1, false))
    }
    @Test fun onlyHandledFormatsCanBeSubmitted() {
        assertNull(tvTrackUnavailableReason(4))
        for (support in 0..3) assertNotNull(tvTrackUnavailableReason(support))
        assertNotEquals(tvTrackUnavailableReason(1), tvTrackUnavailableReason(3))
    }
    @Test fun channelCountDoesNotInventLanguage() {
        val name = tvTrackLabel(null, null, "Audio 1", 6, true, false)
        assertTrue(name.contains("Surround"))
        assertTrue(name.contains("6 canales"))
        assertFalse(name.contains("Español"))
    }
    @Test fun languageLabelAndStereoArePreserved() {
        val name = tvTrackLabel("Stereo", "es", "Audio 1", 2, true, false)
        assertTrue(name.contains("Español"))
        assertTrue(name.contains("Stereo"))
    }
    @Test fun captionsAreOnlyReportedFromMetadata() {
        assertTrue(tvTrackLabel("English", "en", "Subtítulo 1", -1, false, true).contains("CC"))
        assertFalse(tvTrackLabel(null, null, "Subtítulo 1", -1, false, false).contains("CC"))
    }
    @Test fun ac3ExplanationNeverChangesCompatibility() {
        assertTrue(tvTrackUnavailableReason(1, "audio/ac3")!!.contains("AC3"))
        assertNull(tvTrackUnavailableReason(4, "audio/ac3"))
        assertNotNull(tvTrackUnavailableReason(3, "audio/ac3"))
        assertFalse(tvTrackUnavailableReason(1, "audio/mp4a-latm")!!.contains("AC3"))
    }
}
