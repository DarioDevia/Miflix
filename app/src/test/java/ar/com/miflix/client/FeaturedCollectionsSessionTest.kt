package ar.com.miflix.client

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class FeaturedCollectionsSessionTest {
    private class Disk(var ids: List<String> = emptyList()) : SagaSelectionStorage {
        var writes = 0
        override fun read() = ids
        override fun write(ids: List<String>) { this.ids = ids.toList(); writes++ }
    }
    private fun candidates(count: Int): List<ResolvedCollection> = (0 until count).map {
        ResolvedCollection(CatalogCollection("c$it", "Saga $it", null, "manual", null, null, null,
            it, true, listOf("movie")), listOf(Title(id = "movie", tipo = "pelicula", titulo = "Movie")))
    }
    private fun ids(values: List<ResolvedCollection>) = values.map { it.id }

    @Test fun emptyCatalogHidesRowAndDoesNotConsumePreviousSession() {
        val disk = Disk(listOf("previous"))
        assertTrue(FeaturedCollectionsSession(disk).select(emptyList()).isEmpty())
        assertEquals(listOf("previous"), disk.ids); assertEquals(0, disk.writes)
    }
    @Test fun oneCollectionIsShownOnceAndCannotPromiseVariation() {
        val disk = Disk(listOf("c0"))
        val session = FeaturedCollectionsSession(disk)
        assertEquals(listOf("c0"), ids(session.select(candidates(1))))
        assertEquals(listOf("c0"), ids(session.select(candidates(1))))
    }
    @Test fun fiveCollectionsAllAppearWithoutDuplicates() {
        val selected = FeaturedCollectionsSession(Disk(), Random(1)).select(candidates(5))
        assertEquals(5, selected.size); assertEquals(5, ids(selected).distinct().size)
    }
    @Test fun moreThanTenCapsOnlyFeaturedRowAndNeverMutatesSection() {
        val all = candidates(24)
        val before = all.toList()
        val selected = FeaturedCollectionsSession(Disk(), Random(2)).select(all)
        assertEquals(10, selected.size); assertEquals(10, ids(selected).distinct().size)
        assertEquals(before, all); assertEquals(24, all.size)
    }
    @Test fun newSessionAvoidsExactPreviousSelectionAndOrderEvenWhenRandomRepeats() {
        val disk = Disk()
        val first = ids(FeaturedCollectionsSession(disk, Random(8)).select(candidates(15)))
        val second = ids(FeaturedCollectionsSession(disk, Random(8)).select(candidates(15)))
        assertNotEquals(first, second)
        assertEquals(first.toSet(), second.toSet()) // A different order is valid variation.
    }
    @Test fun repeatedCallsOnSameSessionStayStableIncludingCatalogReorder() {
        // Tests the process-owned selection object, not Android rotation or actual navigation.
        val all = candidates(20); val disk = Disk()
        val session = FeaturedCollectionsSession(disk, Random(4))
        val selected = ids(session.select(all))
        repeat(8) { assertEquals(selected, ids(session.select(all.reversed()))) }
        assertEquals(1, disk.writes)
    }
    @Test fun removalAndVisibilityChangeOnlyReplaceInvalidSlots() {
        val all = candidates(15)
        val session = FeaturedCollectionsSession(Disk(), Random(5))
        val first = session.select(all)
        val remove = first.first().id
        val hide = first[1].id
        val refreshed = all.filter { it.id != remove }.map {
            if (it.id == hide) it.copy(collection = it.collection.copy(visible = false)) else it
        }
        val next = session.select(refreshed)
        assertEquals(10, next.size)
        assertFalse(ids(next).contains(remove)); assertFalse(ids(next).contains(hide))
        assertEquals(ids(first).drop(2), ids(next).take(8))
    }
    @Test fun refreshUpdatesObjectsWithoutReshufflingValidIds() {
        val session = FeaturedCollectionsSession(Disk(), Random(5))
        val first = session.select(candidates(5))
        val new = candidates(5).map { it.copy(collection = it.collection.copy(nombre = "Updated")) }
        val result = session.select(new)
        assertEquals(ids(first), ids(result))
        assertTrue(result.all { it.collection.nombre == "Updated" })
        assertSame(new.first { it.id == result.first().id }, result.first())
    }
    @Test fun activityLoadingPlaceholderNeverDiscardsExistingSessionSelection() {
        val disk = Disk()
        val session = FeaturedCollectionsSession(disk, Random(9))
        val initial = ids(session.select(candidates(20)))
        assertTrue(session.select(emptyList(), catalogReady = false).isEmpty())
        assertEquals(initial, ids(session.select(candidates(20))))
        assertEquals(1, disk.writes)
    }
    @Test fun emptyOrHiddenCollectionsAreNeverFeaturedEvenIfPassedDirectly() {
        val all = candidates(3).mapIndexed { i, c -> when (i) {
            0 -> c.copy(collection = c.collection.copy(visible = false))
            1 -> c.copy(movies = emptyList())
            else -> c
        } }
        assertEquals(listOf("c2"), ids(FeaturedCollectionsSession(Disk()).select(all + all)))
    }
    @Test fun cacheInitiallyEmptyCanLoadCollectionsLaterAndFillAvailableSlots() {
        val session = FeaturedCollectionsSession(Disk(), Random(3))
        assertTrue(session.select(emptyList()).isEmpty())
        val one = ids(session.select(candidates(1)))
        val three = ids(session.select(candidates(3)))
        assertEquals(one, three.take(1)); assertEquals(3, three.size)
    }
    @Test fun newCandidatesDoNotEvictTenValidSelectionsAndAllCanDisappearSafely() {
        val session = FeaturedCollectionsSession(Disk(), Random(6))
        val first = ids(session.select(candidates(15)))
        assertEquals(first, ids(session.select(candidates(30))))
        assertTrue(session.select(emptyList()).isEmpty())
    }
}
