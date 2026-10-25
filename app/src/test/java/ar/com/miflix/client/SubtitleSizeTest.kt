package ar.com.miflix.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleSizeTest {
    @Test fun missingOrInvalidPreferenceUsesMedium() {
        for (value in listOf(null, "", "unknown", "small", "99")) {
            assertEquals(SubtitleSize.MEDIUM, SubtitleSize.fromStored(value))
        }
    }

    @Test fun stableStoredNamesRoundTrip() {
        for (size in SubtitleSize.values()) assertEquals(size, SubtitleSize.fromStored(size.name))
    }

    @Test fun sizesHaveExpectedRelativeValues() {
        assertEquals(0.042f, SubtitleSize.SMALL.fractionOfHeight, 0.000001f)
        assertEquals(0.0533f, SubtitleSize.MEDIUM.fractionOfHeight, 0.000001f)
        assertEquals(0.067f, SubtitleSize.LARGE.fractionOfHeight, 0.000001f)
        assertTrue(SubtitleSize.SMALL.fractionOfHeight < SubtitleSize.MEDIUM.fractionOfHeight)
        assertTrue(SubtitleSize.MEDIUM.fractionOfHeight < SubtitleSize.LARGE.fractionOfHeight)
    }
}
