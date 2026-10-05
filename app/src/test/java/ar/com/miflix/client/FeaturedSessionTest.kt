package ar.com.miflix.client

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class FeaturedSessionTest {
    private fun title(id: String) = Title(id = id, titulo = "Título $id",
        backdropUrl = "https://images.example/$id.jpg")

    @Test fun onlyUsableCandidatesEnterTheDraw() {
        val valid = title("valid")
        val invalid = listOf(title("blank").copy(titulo = " "), title("missing").copy(backdropUrl = null),
            title("malformed").copy(backdropUrl = "not an image URL"), title("hostless").copy(backdropUrl = "https:///image"),
            title("id").copy(id = ""))
        invalid.forEach { assertFalse(isFeaturedCandidate(it)) }
        assertTrue(isFeaturedCandidate(valid))
        assertEquals(valid, FeaturedSession(Random(1)).select(invalid + valid))
    }

    @Test fun emptyCatalogIsSafeAndOneCandidateIsUsed() {
        val session = FeaturedSession(Random(2))
        assertNull(session.select(emptyList()))
        val only = title("only")
        assertEquals(only, session.select(listOf(only)))
    }

    @Test fun posterFallbackMatchesHeroImageRules() {
        assertTrue(isFeaturedCandidate(title("poster").copy(backdropUrl = " ", posterUrl = "https://images.example/poster.jpg")))
        assertFalse(isFeaturedCandidate(title("bad").copy(backdropUrl = "bad", posterUrl = "https://images.example/poster.jpg")))
    }

    @Test fun validChoiceSurvivesReorderRefreshAndNewMetadata() {
        val session = FeaturedSession(Random(3))
        val catalog = listOf(title("a"), title("b"), title("c"))
        val selected = session.select(catalog)!!
        val refreshed = catalog.reversed().map { it.copy(sinopsis = "Actualizada") } + title("new")
        val current = session.select(refreshed)!!
        assertEquals(selected.id, current.id)
        assertEquals("Actualizada", current.sinopsis)
        repeat(10) { assertEquals(current, session.select(refreshed)) }
    }

    @Test fun removedOrInvalidSelectionCanBeReplaced() {
        for (remove in listOf(true, false)) {
            val session = FeaturedSession(Random(4))
            val original = title("original")
            assertEquals(original, session.select(listOf(original)))
            val replacement = title("replacement")
            val refreshed = if (remove) listOf(replacement)
                else listOf(original.copy(backdropUrl = null), replacement)
            assertEquals(replacement, session.select(refreshed))
        }
    }

    @Test fun noCandidatesKeepsThePreviousSafeFallback() {
        val noImage = title("none").copy(backdropUrl = null)
        assertEquals(noImage, FeaturedSession(Random(5)).select(listOf(noImage)))
    }
}
