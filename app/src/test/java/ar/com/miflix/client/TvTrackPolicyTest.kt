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
    @Test fun isoCodesUseSpanishNames() {
        for ((codes, name) in listOf(
            listOf("es", "spa", "es-MX") to "Español",
            listOf("en", "eng") to "Inglés", listOf("pl", "pol") to "Polaco",
            listOf("zh", "zho", "chi") to "Chino", listOf("ja", "jpn") to "Japonés",
            listOf("pt", "por", "pt_BR") to "Portugués"))
            codes.forEach { assertEquals(name, tvLanguageName(it)) }
        assertEquals("Alemán", tvLanguageName("ger"))
        assertEquals("Finés", tvLanguageName("fin"))
    }
    @Test fun untrustedMetadataCannotBecomeVisibleText() {
        for (language in listOf(null, "", "und", "zzz", "https://t.me/channel",
            "es https://t.me/channel", "@FromT4Latino", "/folder/es", "Surround"))
            assertEquals("Idioma desconocido", tvLanguageName(language))
    }
    @Test fun duplicateLabelsKeepOriginalOrderAndCount() {
        val languages = listOf("es", "en", "spa", null, "es", "und")
        val labels = tvNumberTrackLabels(languages.map(::tvLanguageName))
        assertEquals(listOf("Español", "Inglés", "Español (2)", "Idioma desconocido",
            "Español (3)", "Idioma desconocido (2)"), labels)
        assertEquals(labels, tvNumberTrackLabels(languages.map(::tvLanguageName)))
        assertEquals(emptyList<String>(), tvNumberTrackLabels(emptyList()))
    }
    @Test fun ac3ExplanationNeverChangesCompatibility() {
        assertTrue(tvTrackUnavailableReason(1, "audio/ac3")!!.contains("AC3"))
        assertNull(tvTrackUnavailableReason(4, "audio/ac3"))
        assertNotNull(tvTrackUnavailableReason(3, "audio/ac3"))
        assertFalse(tvTrackUnavailableReason(1, "audio/mp4a-latm")!!.contains("AC3"))
    }
}
