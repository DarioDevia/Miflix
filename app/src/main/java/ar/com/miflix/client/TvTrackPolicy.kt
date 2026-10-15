package ar.com.miflix.client

/** DPAD order includes explanatory unsupported rows and the final close action. */
internal fun tvPanelNextIndex(current: Int, count: Int, down: Boolean): Int =
    if (count <= 0) 0 else (current + if (down) 1 else -1).coerceIn(0, count - 1)

/** Media3 C.FORMAT_* values; no unsupported decoder capabilities are overridden. */
internal fun tvTrackUnavailableReason(support: Int, sampleMimeType: String? = null): String? = when (support) {
    4 -> null // FORMAT_HANDLED
    3 -> "La pista excede las capacidades de este dispositivo."
    2 -> "El dispositivo no admite la protección de esta pista."
    1 -> if (sampleMimeType == "audio/ac3") "Este dispositivo no tiene un decodificador AC3 compatible."
        else "Este dispositivo no tiene un decodificador compatible para esta pista."
    else -> "Este dispositivo no admite el formato de esta pista."
}

/** Display only trusted language metadata; labels and channel names are never parsed. */
private val tvLanguageCodes: Map<String, String> by lazy {
    buildMap {
        java.util.Locale.getISOLanguages().forEach { code ->
            put(code, code)
            runCatching { java.util.Locale(code).isO3Language }.getOrNull()
                ?.let { put(it, code) }
        }
        // ISO 639-2 bibliographic aliases, alongside terminological ISO3 codes.
        listOf("alb:sq", "arm:hy", "baq:eu", "bur:my", "chi:zh", "cze:cs",
            "dut:nl", "fre:fr", "geo:ka", "ger:de", "gre:el", "ice:is",
            "mac:mk", "mao:mi", "may:ms", "per:fa", "rum:ro", "slo:sk",
            "tib:bo", "wel:cy").forEach { alias ->
            put(alias.substringBefore(':'), alias.substringAfter(':'))
        }
    }
}

internal fun tvLanguageName(language: String?): String {
    val tag = language?.trim()?.lowercase(java.util.Locale.ROOT) ?: return "Idioma desconocido"
    if (!Regex("[a-z]{2,3}(?:[-_][a-z0-9]{2,8})*").matches(tag)) return "Idioma desconocido"
    val primary = tag.substringBefore('-').substringBefore('_')
    val code = tvLanguageCodes[primary] ?: return "Idioma desconocido"
    val name = java.util.Locale(code).getDisplayLanguage(java.util.Locale("es"))
    return name.replaceFirstChar { it.titlecase(java.util.Locale("es")) }
}

/** Same input order and count: numbering never changes Media3 indices or identity. */
internal fun tvNumberTrackLabels(labels: List<String>): List<String> {
    val seen = mutableMapOf<String, Int>()
    return labels.map { label ->
        val occurrence = (seen[label] ?: 0) + 1
        seen[label] = occurrence
        if (occurrence == 1) label else "$label ($occurrence)"
    }
}
