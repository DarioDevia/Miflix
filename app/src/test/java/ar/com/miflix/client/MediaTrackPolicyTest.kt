package ar.com.miflix.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaTrackPolicyTest {
    @Test fun explicitLatinAmericanSpanishWinsOverGenericAndEuropean() {
        for (latin in listOf("Latin American Spanish", "Spanish Latin America", "Latino",
                "Castellano Latino", "Español latinoamericano")) {
            assertEquals(2, preferredSpanishAudio(listOf(
                AudioTrackMetadata("European Spanish", "es"),
                AudioTrackMetadata("Spanish", "es", selected = true),
                AudioTrackMetadata(latin, null))))
        }
    }

    @Test fun regionalLatinLanguageWinsEvenWithMissingLabel() {
        for (language in listOf("es-419", "es-MX", "es_AR")) {
            assertEquals(1, preferredSpanishAudio(listOf(
                AudioTrackMetadata("Spanish", "es"), AudioTrackMetadata(null, language))))
        }
    }

    @Test fun genericSpanishWinsOverEuropeanRegardlessOfOrder() {
        assertEquals(1, preferredSpanishAudio(listOf(
            AudioTrackMetadata("European Spanish", "es", selected = true),
            AudioTrackMetadata("Spanish", "es"))))
        assertEquals(0, preferredSpanishAudio(listOf(
            AudioTrackMetadata("Spanish", "es"), AudioTrackMetadata("Español de España", "es"))))
    }

    @Test fun europeanRegionIsNotAssumedLatinFromGenericLabel() {
        assertEquals(1, preferredSpanishAudio(listOf(
            AudioTrackMetadata("Spanish", "es-ES"), AudioTrackMetadata("Castellano", "es"))))
    }

    @Test fun europeanSpanishIsUsedIfItIsTheOnlySpanish() {
        assertEquals(1, preferredSpanishAudio(listOf(
            AudioTrackMetadata("English", "en"), AudioTrackMetadata("European Spanish", "es"))))
    }

    @Test fun unsupportedSpanishIsIgnored() {
        assertEquals(1, preferredSpanishAudio(listOf(
            AudioTrackMetadata("Latino", "es-419", supported = false),
            AudioTrackMetadata("Spanish", "es"))))
        assertNull(preferredSpanishAudio(listOf(AudioTrackMetadata("Spanish", "es", supported = false))))
    }

    @Test fun noSpanishLeavesMedia3DefaultUntouched() {
        assertNull(preferredSpanishAudio(listOf(AudioTrackMetadata("Korean [Original]", "ko", selected = true),
            AudioTrackMetadata("English", "en"))))
        assertNull(preferredSpanishAudio(emptyList()))
        assertNull(preferredSpanishAudio(listOf(AudioTrackMetadata(null, null))))
    }

    @Test fun singleAudioWorksWithoutAssumingItsLanguage() {
        assertEquals(0, preferredSpanishAudio(listOf(AudioTrackMetadata("Castellano", null))))
        assertNull(preferredSpanishAudio(listOf(AudioTrackMetadata("English", "en"))))
    }

    @Test fun equalPriorityKeepsAlreadySelectedSpanish() {
        assertEquals(1, preferredSpanishAudio(listOf(AudioTrackMetadata("Spanish", "es"),
            AudioTrackMetadata("Spanish", "es", selected = true))))
    }

    @Test fun friendlyNamesRetainUsefulQualifiers() {
        assertEquals("Coreano · Original", mediaTrackName("Korean [Original]", "ko", "Audio 1"))
        assertEquals("Español · Forzados", mediaTrackName("Spanish [ForcedNarrative]", "es", "Subtítulo 1"))
        assertEquals("Coreano · CC", mediaTrackName("Korean [CC]", "ko", "Subtítulo 2"))
        assertEquals("Español de España", mediaTrackName("European Spanish", "es", "Audio 2"))
        assertEquals("Español latino", mediaTrackName("Latin American Spanish", "es", "Audio 2"))
    }

    @Test fun unfamiliarLabelsAndQualifiersArePreserved() {
        assertEquals("Director commentary · Stereo", mediaTrackName("Director commentary [Stereo]", "en", "Audio 1"))
        assertEquals("Spanish [CC] commentary", mediaTrackName("Spanish [CC] commentary", "es", "Audio 1"))
    }

    @Test fun missingMetadataHasSafeNames() {
        assertEquals("Español", mediaTrackName(null, "es", "Audio 1"))
        assertEquals("Inglés", mediaTrackName(" ", "en", "Audio 2"))
        assertEquals("Audio 3", mediaTrackName(null, null, "Audio 3"))
        assertEquals("Subtítulo 4", mediaTrackName("unknown", "und", "Subtítulo 4"))
    }
}
