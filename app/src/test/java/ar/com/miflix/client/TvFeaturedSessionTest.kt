package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class TvFeaturedSessionTest {
    private fun candidate(id: Int, genre: String = "genre-${id % 4}") =
        TvHeroCandidate("movie-$id", setOf(genre))
    private fun session() = TvFeaturedSession(Random(42))

    @Test fun onlyNamedMoviesWithPublishedBackgroundsAreEligible() {
        assertTrue(tvHeroEligibleMovie("pelicula", "a", "Movie", listOf("validated-background")))
        assertFalse(tvHeroEligibleMovie("serie", "a", "Series", listOf("validated-background")))
        assertFalse(tvHeroEligibleMovie("pelicula", " ", "Movie", listOf("validated-background")))
        assertFalse(tvHeroEligibleMovie("pelicula", "a", " ", listOf("validated-background")))
        assertFalse(tvHeroEligibleMovie("pelicula", "a", "Movie", emptyList()))
    }
    @Test fun poolHasAtMostTenDistinctMovies() {
        val pool=session().update((0..30).map(::candidate)+candidate(1),emptySet(),emptySet())
        assertEquals(10,pool.size)
        assertEquals(pool.size,pool.toSet().size)
    }
    @Test fun emptyAndSmallCatalogsNeverInventTitles() {
        val s=session()
        assertTrue(s.update(emptyList(),emptySet(),emptySet()).isEmpty())
        assertNull(s.advance())
        assertEquals(listOf("movie-1"),s.update(listOf(candidate(1)),emptySet(),emptySet()))
        assertEquals("movie-1",s.advance());assertNull(s.nextId)
        assertEquals(3,s.update((1..3).map(::candidate),emptySet(),emptySet()).size)
    }
    @Test fun repeatedReconciliationAndMetadataRefreshKeepPoolAndIndex() {
        val s=session();val catalog=(0..20).map(::candidate)
        val pool=s.update(catalog,emptySet(),emptySet())
        val current=s.advance()
        assertEquals(pool,s.update(catalog.reversed(),setOf("other"),setOf("changed")))
        assertEquals(current,s.currentId)
        assertEquals(pool,s.update(catalog+candidate(99),emptySet(),emptySet()))
    }
    @Test fun previousSessionTitlesAreAvoidedWhenThereAreEnoughAlternatives() {
        val previous=(0..9).map { "movie-$it" }.toSet()
        val pool=session().update((0..29).map(::candidate),previous,emptySet())
        assertTrue(pool.none { it in previous })
    }
    @Test fun smallCatalogReusesOnlyWhatIsNeeded() {
        val previous=(0..9).map { "movie-$it" }.toSet()
        val pool=session().update((0..11).map(::candidate),previous,emptySet())
        assertEquals(setOf("movie-10","movie-11"),pool.take(2).toSet())
        assertEquals(10,pool.size)
    }
    @Test fun genresPreferDifferentOpeningAndAdjacentTitles() {
        val catalog=(0..8).map { candidate(it,"drama") }+candidate(9,"comedy")
        val pool=session().update(catalog,emptySet(),setOf("drama"))
        assertEquals("movie-9",pool.first())
        val mixed=(0..7).map { candidate(it,if(it%2==0) "comedy" else "drama") }
        val ordered=session().update(mixed,emptySet(),emptySet())
        ordered.zipWithNext().forEach { (a,b) ->
            assertNotEquals(mixed.first { it.id==a }.genres,mixed.first { it.id==b }.genres)
        }
    }
    @Test fun absentGenresAreSafeAndNormalizedWithoutInventingGenres() {
        assertEquals(setOf("drama","comedia"),tvHeroGenres(listOf(" Drama ","DRAMA","Comedia"," ")))
        assertEquals(2,session().update(listOf(TvHeroCandidate("a",emptySet()),TvHeroCandidate("b",emptySet())),
            emptySet(),emptySet()).size)
    }
    @Test fun rotationsWrapWithoutRepeatingUntilThePoolEnds() {
        val s=session();val pool=s.update((0..4).map(::candidate),emptySet(),emptySet())
        assertEquals(pool[1],s.nextId)
        val seen=mutableListOf(s.currentId)
        repeat(4) { seen+=s.advance() }
        assertEquals(pool,seen)
        assertEquals(pool.first(),s.advance())
    }
    @Test fun catalogRemovalAndFailedBackdropsCannotLeaveStaleActions() {
        val s=session();val candidates=(0..20).map(::candidate)
        val first=s.update(candidates,emptySet(),emptySet()).first()
        s.rejectBackdrop(first)
        val revised=s.update(candidates,emptySet(),emptySet())
        assertFalse(first in revised);assertTrue(s.currentId in revised);assertEquals(10,revised.size)
        val current=s.currentId
        val removed=s.update(candidates.filter { it.id!=current },emptySet(),emptySet())
        assertFalse(current in removed);assertTrue(s.currentId in removed)
    }
    @Test fun rotationRequiresVisibleResumedHomeAndUnfocusedHero() {
        assertTrue(tvHeroCanRotate(2,true,true,false))
        assertFalse(tvHeroCanRotate(1,true,true,false))
        assertFalse(tvHeroCanRotate(2,false,true,false))
        assertFalse(tvHeroCanRotate(2,true,false,false))
        assertFalse(tvHeroCanRotate(2,true,true,true))
        assertEquals(8_000L,TV_HERO_INTERVAL_MS)
    }
}
