package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

class TvHomeProbePolicyTest {
    private fun movie(id:String,type:String="pelicula") = Title(id=id,tipo=type,titulo="Título $id",
        telegramUrl="https://t.me/c/1/1",backdropUrl="https://artwork.test/$id.jpg")
    private fun desired(key:String="a",title:String="A") = HomeProbeDesired(HOME_PROBE_PREFIX+key,mapOf("title" to title))
    private val today=java.time.LocalDate.of(2026,11,1)
    private fun select(titles:List<Title>)=homeProbeCards(HomeProbeCatalog(titles,
        titles.associate { it.id to HomeProbeDate(java.time.LocalDate.of(2026,1,1)) }),today).cards
    @Test fun noCatalogCreatesNoSyntheticFixtures() {
        assertTrue(homeProbeCards(HomeProbeCatalog(emptyList(),emptyMap()),today).cards.isEmpty())
    }
    @Test fun availableDatedItemsAreRealAndStableAtDateTies() {
        val cards=select(listOf(movie("m"),movie("s","serie"),movie("a","anime")))
        assertEquals(listOf("Título a","Título m","Título s"),cards.map { it.title })
        assertFalse(cards.any { it.synthetic })
    }
    @Test fun insufficientCatalogPublishesOnlyRealAvailableEntries() {
        val cards=select(listOf(movie("m"),movie("bad").copy(telegramUrl=null)))
        assertEquals(1,cards.size);assertFalse(cards.any { it.synthetic })
    }
    @Test fun sameIdsAreStableAcrossMetadataUpdatesAndNoTelegramUrlLeaksInKeys() {
        val first=select(listOf(movie("m"))).first()
        val next=select(listOf(movie("m").copy(titulo="Nuevo nombre"))).first()
        assertEquals(first.key,next.key);assertEquals(64,first.key.removePrefix(HOME_PROBE_PREFIX).length)
        assertFalse(first.key.contains("t.me"));assertEquals("Nuevo nombre",next.title)
    }
    @Test fun duplicatesNeverCreateRepeatedCardsAndImagesOnlyUsePublishedBackdrops() {
        val cards=select(listOf(movie("m"),movie("m"),movie("n").copy(backdropUrl=null)))
        assertEquals(2,cards.size);assertEquals(2,cards.map { it.key }.distinct().size)
        assertEquals("https://artwork.test/m.jpg",cards.first().image);assertNull(cards[1].image)
    }
    @Test fun firstRunInsertsEachDesiredProgram() {
        val plan=homeProbePlan(emptyList(),listOf(desired("a"),desired("b")))
        assertEquals(2,plan.upserts.size);assertTrue(plan.upserts.all { it.existingId==null });assertTrue(plan.deleteIds.isEmpty())
    }
    @Test fun secondRunDoesNotRewriteIdenticalPrograms() {
        val target=desired();val plan=homeProbePlan(listOf(HomeProbeStored(1,target.key,target.payload)),listOf(target))
        assertTrue(plan.upserts.isEmpty());assertTrue(plan.deleteIds.isEmpty())
    }
    @Test fun metadataChangesUpdateSameProviderId() {
        val target=desired(title="New");val plan=homeProbePlan(listOf(HomeProbeStored(7,target.key,mapOf("title" to "Old"))),listOf(target))
        assertEquals(7L,plan.upserts.single().existingId);assertTrue(plan.deleteIds.isEmpty())
    }
    @Test fun duplicatesAndStaleProgramsAreRemovedOnlyWithinProbeNamespace() {
        val target=desired();val rows=listOf(HomeProbeStored(8,target.key,target.payload),HomeProbeStored(2,target.key,target.payload),
            HomeProbeStored(10,HOME_PROBE_PREFIX+"stale",emptyMap()),HomeProbeStored(30,"legacy-local-probe",emptyMap()),HomeProbeStored(40,null,emptyMap()))
        val plan=homeProbePlan(rows,listOf(target))
        assertTrue(plan.upserts.isEmpty());assertEquals(setOf(8L,10L),plan.deleteIds.toSet())
    }
    @Test fun deletedProviderRowsAreRecreatedAndExtraProviderFieldsDoNotCauseRewrites() {
        val target=desired();assertEquals(1,homeProbePlan(emptyList(),listOf(target)).upserts.size)
        assertTrue(homeProbePlan(listOf(HomeProbeStored(1,target.key,target.payload+mapOf("system" to "extra"))),listOf(target)).upserts.isEmpty())
    }
    @Test fun invalidNamespaceAndDuplicateDesiredKeysFailBeforeWrites() {
        for (targets in listOf(listOf(HomeProbeDesired("foreign",emptyMap())),listOf(desired(),desired()))) {
            try { homeProbePlan(emptyList(),targets);fail("Must reject") }catch(_:IllegalArgumentException) { }
        }
    }
    @Test fun emptyDesiredSetPreservesAllPrograms() {
        val plan=homeProbePlan(listOf(HomeProbeStored(1,"foreign",emptyMap()),HomeProbeStored(2,HOME_PROBE_PREFIX+"stale",emptyMap())),emptyList())
        assertTrue(plan.deleteIds.isEmpty());assertTrue(plan.upserts.isEmpty())
    }
}
