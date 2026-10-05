package ar.com.miflix.client

import java.text.Normalizer
import java.util.Locale

/** Metadata only: no persisted group indices or assumptions about container track order. */
internal data class AudioTrackMetadata(
    val label: String?,
    val language: String?,
    val supported: Boolean = true,
    val selected: Boolean = false
)

private fun normalizedTrackText(value: String?): String = Normalizer.normalize(
    value.orEmpty(), Normalizer.Form.NFD
).replace(Regex("\\p{M}"), "").lowercase(Locale.ROOT)

private fun spanishPriority(track: AudioTrackMetadata): Int? {
    if (!track.supported) return null
    val label = normalizedTrackText(track.label)
    val language = track.language.orEmpty().lowercase(Locale.ROOT).replace('_', '-')
    val spanish = language.substringBefore('-') in setOf("es", "spa") ||
        Regex("\\b(spanish|espanol|castellano|latino)\\b").containsMatchIn(label)
    if (!spanish) return null
    // Explicit European metadata takes precedence over an ambiguous generic Spanish label.
    if (language == "es-es" || Regex("\\b(european|europeo|espana|spain)\\b").containsMatchIn(label)) return 2
    if (language == "es-419" || language in setOf("es-ar", "es-mx", "es-cl", "es-co",
            "es-pe", "es-uy", "es-ve", "es-bo", "es-ec", "es-py", "es-cr", "es-cu",
            "es-do", "es-gt", "es-hn", "es-ni", "es-pa", "es-pr", "es-sv") ||
        Regex("\\b(latino|latina|latin american|latin america|latinoamericano|latinoamericana)\\b")
            .containsMatchIn(label)) return 0
    return 1
}

/** Null means leave Media3's existing default/original choice untouched. */
internal fun preferredSpanishAudio(tracks: List<AudioTrackMetadata>): Int? = tracks.indices
    .filter { spanishPriority(tracks[it]) != null }
    .minWithOrNull(compareBy<Int> { spanishPriority(tracks[it])!! }
        .thenBy { !tracks[it].selected })

internal fun mediaTrackName(label: String?, language: String?, fallback: String): String {
    val usefulLabel = label?.trim()?.takeIf { it.isNotEmpty() &&
        it.lowercase(Locale.ROOT) !in setOf("und", "unknown", "undefined") }
    if (usefulLabel != null) {
        // Translate only known whole language names; retain specific or unfamiliar labels.
        val base = usefulLabel.substringBefore('[').trim()
        val friendly = when (normalizedTrackText(base)) {
            "spanish", "espanol" -> "Español"
            "european spanish" -> "Español de España"
            "latin american spanish", "spanish latin america", "castellano latino" -> "Español latino"
            "english" -> "Inglés"
            "korean" -> "Coreano"
            else -> base
        }
        val qualifiers = Regex("\\[([^]]+)]").findAll(usefulLabel).map { match ->
            when (normalizedTrackText(match.groupValues[1])) {
                "forcednarrative", "forced", "forzados" -> "Forzados"
                "original" -> "Original"
                "cc" -> "CC"
                else -> match.groupValues[1]
            }
        }.toList()
        // Avoid discarding non-bracket suffixes of unusual labels.
        if (qualifiers.isNotEmpty() && !usefulLabel.endsWith(']')) return usefulLabel
        return (listOf(friendly) + qualifiers).joinToString(" · ")
    }
    val tag = language?.trim()?.replace('_', '-')?.takeIf {
        it.isNotEmpty() && it.lowercase(Locale.ROOT) !in setOf("und", "unknown")
    } ?: return fallback
    val locale = Locale.forLanguageTag(tag)
    if (locale.language.isBlank()) return fallback
    val display = locale.getDisplayName(Locale.forLanguageTag("es"))
    return display.replaceFirstChar { it.titlecase(Locale.forLanguageTag("es")) }
        .takeIf { it.isNotBlank() } ?: fallback
}
