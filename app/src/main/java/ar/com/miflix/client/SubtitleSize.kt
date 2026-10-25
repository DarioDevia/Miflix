package ar.com.miflix.client

/** Relative to SubtitleView height after padding, so orientation/fullscreen scale naturally. */
internal enum class SubtitleSize(val label: String, val fractionOfHeight: Float) {
    SMALL("Pequeño", 0.042f),
    MEDIUM("Mediano", 0.0533f),
    LARGE("Grande", 0.067f);

    companion object {
        const val PREFERENCE_KEY = "subtitleSize"

        fun fromStored(value: String?): SubtitleSize = values().firstOrNull { it.name == value } ?: MEDIUM
    }
}
