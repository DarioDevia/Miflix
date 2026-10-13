package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

class TvTrailerPolicyTest {
    @Test fun officialEmbedIdentifiesAppAndDoesNotAutoplayOrHideControls() {
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
}
