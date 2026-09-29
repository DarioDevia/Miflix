package ar.com.miflix.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrailerLinkTest {
    @Test fun realCatalogYoutubeUrlsAndStartTimes() {
        assertEquals(YouTubeTrailer("kgv8jf_8dm0"),
            TrailerLink.parse("https://www.youtube.com/watch?v=kgv8jf_8dm0"))
        assertEquals(YouTubeTrailer("ndStFxq8zfU", 23),
            TrailerLink.parse("https://www.youtube.com/watch?v=ndStFxq8zfU&t=23s"))
        assertEquals(YouTubeTrailer("fNdzqUppiOQ", 20),
            TrailerLink.parse("https://youtu.be/fNdzqUppiOQ?t=20s"))
        assertEquals(YouTubeTrailer("kgv8jf_8dm0"),
            TrailerLink.parse("https://www.youtube.com/embed/kgv8jf_8dm0"))
    }

    @Test fun absentInvalidAndUnsupportedUrlsDoNotAutoplay() {
        listOf(null, "", "  ", "oops", "http://youtu.be/kgv8jf_8dm0",
            "https://youtube.com.evil.test/watch?v=kgv8jf_8dm0",
            "https://www.youtube.com/watch?v=wrong",
            "https://example.test/video.mp4").forEach {
            assertNull(TrailerLink.parse(it))
        }
    }

    @Test fun autoplayPreferenceDefaultsToCallerDecision() {
        val url = "https://www.youtube.com/watch?v=kgv8jf_8dm0"
        assertEquals(YouTubeTrailer("kgv8jf_8dm0"), TrailerLink.autoplayTarget(url, true))
        assertNull(TrailerLink.autoplayTarget(url, false))
        assertNull(TrailerLink.autoplayTarget(null, true))
    }
}
