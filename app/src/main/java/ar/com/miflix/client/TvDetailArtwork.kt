package ar.com.miflix.client

import java.net.URI

/** Request/decode caps for one detail, not a catalog-wide preload. */
internal enum class TvArtworkKind(val rendition: String, val width: Int, val height: Int) {
    BACKDROP("w1280", 1280, 720), LOGO("w500", 800, 320), POSTER("w342", 342, 512)
}
internal data class TvDetailArtwork(val backgrounds: List<String>, val logo: String?, val poster: String?)

internal fun tvArtworkUrl(raw: String?, kind: TvArtworkKind): String? {
    val text = raw?.trim()?.takeIf(String::isNotBlank) ?: return null
    val uri = runCatching { URI(text) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", true) || uri.host.isNullOrBlank() ||
        uri.userInfo != null || uri.path.isNullOrBlank() || uri.path == "/") return null
    // Select a smaller rendition of exactly the published image; no TMDB API/lookup.
    return if (uri.host.equals("image.tmdb.org", true) &&
        Regex("^/t/p/(original|w[0-9]+|h[0-9]+)/.+").matches(uri.path)) {
        text.replaceFirst(Regex("/t/p/(original|w[0-9]+|h[0-9]+)/"), "/t/p/${kind.rendition}/")
    } else text
}

internal fun tvDetailArtwork(title: Title): TvDetailArtwork = TvDetailArtwork(
    listOfNotNull(tvArtworkUrl(title.backdropUrl, TvArtworkKind.BACKDROP),
        tvArtworkUrl(title.fondoUrl, TvArtworkKind.BACKDROP)).distinct(),
    tvArtworkUrl(title.logoUrl, TvArtworkKind.LOGO),
    tvArtworkUrl(title.posterUrl, TvArtworkKind.POSTER)
)

internal fun tvDetailFacts(title: Title): String = listOfNotNull(
    title.year?.takeIf { it > 0 }?.toString(),
    title.puntuacion?.takeIf { it.isFinite() && it in 0.0..10.0 }?.let { "★ $it / 10" }
).joinToString(" · ")
internal fun tvDetailGenres(title: Title): String = title.generos.orEmpty()
    .map(String::trim).filter(String::isNotBlank).joinToString(" · ")
