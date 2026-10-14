package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

class TvTrailerPolicyTest {
    @Test fun officialEmbedIdentifiesAppAndPreservesControls() {
        val html = tvTrailerHtml(YouTubeTrailer("abcdefghijk", 12), "ar.com.miflix.client.tv")
        assertTrue(html.contains("https://www.youtube.com/iframe_api"))
        assertTrue(html.contains("origin:'https://ar.com.miflix.client.tv'"))
        assertTrue(html.contains("autoplay:0,controls:1"))
        assertTrue(html.contains("start:12"))
        assertTrue(html.contains("strict-origin-when-cross-origin"))
        assertFalse(html.contains("visibility:hidden"))
    }
    @Test fun catalogYouTubeLinksUseExistingParserAndExternalAlternativesAreExplicitHttps() {
        assertEquals(YouTubeTrailer("abcdefghijk", 12), TrailerLink.parse("https://youtu.be/abcdefghijk?t=12s"))
        assertNull(TrailerLink.parse("https://example.test/trailer"))
        assertEquals("https://example.test/trailer", tvTrailerExternalUrl("https://example.test/trailer"))
        for (url in listOf("javascript:alert(1)", "file:///private", "http://example.test", "https://user:pass@example.test"))
            assertNull(tvTrailerExternalUrl(url))
    }
    @Test fun embeddingRestrictionsAndDeviceErrorsHaveDifferentExplanations() {
        assertEquals(tvTrailerError(101), tvTrailerError(150))
        assertNotEquals(tvTrailerError(101), tvTrailerError(5))
        assertTrue(tvTrailerError(153).contains("identificar"))
        assertTrue(tvTrailerError(100).contains("privado"))
    }
    @Test(expected = IllegalArgumentException::class) fun invalidIdCannotInjectHtml() {
        tvTrailerHtml(YouTubeTrailer("';alert(1)//"), "ar.com.miflix.client.tv")
    }
    @Test fun apiReadinessDoesNotMaskPlaybackTimeout() {
        assertTrue(tvTrailerPlaybackTimedOut(true, -1, false))
        assertTrue(tvTrailerPlaybackTimedOut(true, 3, false))
        assertTrue(tvTrailerPlaybackTimedOut(true, 5, false))
        for (state in listOf(0, 1, 2)) assertFalse(tvTrailerPlaybackTimedOut(true, state, false))
        assertFalse(tvTrailerPlaybackTimedOut(false, -1, false))
        assertFalse(tvTrailerPlaybackTimedOut(true, -1, true))
    }
    @Test fun scriptLoadAndInvalidParametersHaveVisibleErrors() {
        assertTrue(tvTrailerHtml(YouTubeTrailer("abcdefghijk"), "ar.com.miflix.client.tv")
            .contains("MIFLIX_TV_SCRIPT_ERROR"))
        assertTrue(tvTrailerError(2).contains("identificador"))
    }
    @Test fun everySupportedLinkFormProducesTheSameExternalVideoAndStart() {
        val links = listOf("https://youtu.be/abcdefghijk?t=12s",
            "https://www.youtube.com/watch?v=abcdefghijk&start=12",
            "https://www.youtube.com/embed/abcdefghijk?start=12",
            "https://www.youtube.com/shorts/abcdefghijk?t=12")
        for (link in links) assertEquals("https://www.youtube.com/watch?v=abcdefghijk&t=12s",
            tvTrailerWatchUrl(TrailerLink.parse(link)!!))
    }
    @Test fun officialTvThenMobileThenGenericHandlerAreTriedInOrder() {
        assertEquals(listOf("com.google.android.youtube.tv", "com.google.android.youtube", null),
            tvTrailerLaunchPackages(true))
        assertEquals(listOf(null), tvTrailerLaunchPackages(false))
    }
    @Test(expected = IllegalArgumentException::class) fun externalUrlRejectsInvalidVideoId() {
        tvTrailerWatchUrl(YouTubeTrailer("not-an-id"))
    }
    @Test(expected = IllegalArgumentException::class) fun externalUrlRejectsInvalidStart() {
        tvTrailerWatchUrl(YouTubeTrailer("abcdefghijk", -1))
    }
}
