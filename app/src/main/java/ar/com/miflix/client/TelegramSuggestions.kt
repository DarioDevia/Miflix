package ar.com.miflix.client

/** Public username only; paths, phone numbers, query parameters and invitation links are rejected. */
internal fun telegramSuggestionsUrl(configuredUsername: String?): String? {
    val username = configuredUsername?.trim()?.removePrefix("@") ?: return null
    return username.takeIf { it.matches(Regex("[A-Za-z][A-Za-z0-9_]{4,31}")) }
        ?.let { "https://t.me/$it" }
}
