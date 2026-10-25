package ar.com.miflix.client

import java.net.URI
import java.net.URLDecoder

internal data class YouTubeTrailer(val videoId: String, val startSeconds: Int = 0)

internal object TrailerLink {
    private val videoIdPattern = Regex("[A-Za-z0-9_-]{11}")

    fun autoplayTarget(url: String?, enabled: Boolean): YouTubeTrailer? =
        if (enabled) parse(url) else null

    fun parse(url: String?): YouTubeTrailer? {
        val raw = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) ||
            uri.userInfo != null || uri.port != -1) return null
        val host = uri.host?.lowercase() ?: return null
        val segments = uri.path.orEmpty().split('/').filter(String::isNotEmpty)
        val query = uri.rawQuery.orEmpty().split('&').mapNotNull { part ->
            val delimiter = part.indexOf('=')
            if (delimiter < 0) null else part.substring(0, delimiter) to
                runCatching { URLDecoder.decode(part.substring(delimiter + 1), "UTF-8") }.getOrNull()
        }.toMap()
        val id = when (host) {
            "youtube.com", "www.youtube.com", "m.youtube.com", "youtube-nocookie.com",
            "www.youtube-nocookie.com" -> when (segments.firstOrNull()) {
                "watch" -> query["v"]
                "embed", "shorts" -> segments.getOrNull(1)
                else -> null
            }
            "youtu.be", "www.youtu.be" -> segments.singleOrNull()
            else -> null
        }?.takeIf { videoIdPattern.matches(it) } ?: return null
        val start = (query["t"] ?: query["start"]).orEmpty().removeSuffix("s")
            .toIntOrNull()?.takeIf { it in 0..86_400 } ?: 0
        return YouTubeTrailer(id, start)
    }
}
