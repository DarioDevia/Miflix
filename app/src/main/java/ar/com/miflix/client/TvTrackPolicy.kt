package ar.com.miflix.client

/** DPAD order includes explanatory unsupported rows and the final close action. */
internal fun tvPanelNextIndex(current: Int, count: Int, down: Boolean): Int =
    if (count <= 0) 0 else (current + if (down) 1 else -1).coerceIn(0, count - 1)

/** Media3 C.FORMAT_* values; no unsupported decoder capabilities are overridden. */
internal fun tvTrackUnavailableReason(support: Int): String? = when (support) {
    4 -> null // FORMAT_HANDLED
    3 -> "La pista excede las capacidades de este dispositivo."
    2 -> "El dispositivo no admite la protección de esta pista."
    1 -> "Este dispositivo no tiene un decodificador compatible para esta pista."
    else -> "Este dispositivo no admite el formato de esta pista."
}

internal fun tvTrackLabel(label: String?, language: String?, fallback: String,
    channels: Int, audio: Boolean, captions: Boolean): String {
    val parts = mutableListOf<String>()
    val knownLanguage = language?.takeIf { it.isNotBlank() && it != "und" }
    if (knownLanguage != null) parts += mediaTrackName(null, knownLanguage, fallback)
    val name = mediaTrackName(label, null, if (parts.isEmpty()) fallback else "")
    if (name.isNotBlank() && parts.none { it.equals(name, true) }) parts += name
    if (audio && channels > 0) {
        val description = when (channels) { 1 -> "Mono"; 2 -> "Stereo"; else -> "Surround · $channels canales" }
        if (parts.none { it.contains(description, true) }) parts += description
    }
    if (captions && parts.none { it.contains("CC", true) }) parts += "CC · Para personas sordas"
    return parts.joinToString(" · ")
}
