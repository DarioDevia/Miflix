package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TvHomeProbeReleasesTest {
    private val today=LocalDate.of(2026,11,1)
    private fun title(id:String)=Title(id=id,tipo="pelicula",titulo=id,telegramUrl="https://t.me/c/1/1")
    private fun snapshot(current:Int,previous:Int=12):HomeProbeCatalog {
        val pairs=(0 until current).map { title("new$it") to LocalDate.of(2026,1,1).plusDays(it.toLong()) }+
            (0 until previous).map { title("old$it") to LocalDate.of(2025,1,1).plusDays(it.toLong()) }
        return HomeProbeCatalog(pairs.map { it.first },pairs.associate { it.first.id to HomeProbeDate(it.second) })
    }
    private fun currentCount(s:HomeProbeSelection)=s.cards.count { it.title.startsWith("new") }
    @Test fun tenCurrentYearReleasesExcludeOlderWhenLimitReached() {
        val selected=homeProbeCards(snapshot(10),today)
        assertEquals(10,selected.cards.size);assertEquals(10,currentCount(selected));assertEquals(0,selected.missingSlots)
    }
    @Test fun sixCurrentYearReleasesFillFourFromPreviousYear() {
        val selected=homeProbeCards(snapshot(6),today);assertEquals(10,selected.cards.size);assertEquals(6,currentCount(selected))
    }
    @Test fun threeCurrentYearReleasesFillSevenFromPreviousYear() {
        val selected=homeProbeCards(snapshot(3),today);assertEquals(10,selected.cards.size);assertEquals(3,currentCount(selected))
    }
    @Test fun zeroCurrentYearReleasesStillSelectTenPreviousYearReleases() {
        val selected=homeProbeCards(snapshot(0),today);assertEquals(10,selected.cards.size);assertEquals(0,currentCount(selected))
    }
    @Test fun sortingUsesFullDateDescendingAcrossSeveralYears() {
        val s=snapshot(3,3);val older=title("older")
        val selected=homeProbeCards(s.copy(titles=s.titles+older,releases=s.releases+(older.id to HomeProbeDate(LocalDate.of(2024,12,31)))),today)
        assertEquals(listOf("new2","new1","new0","old2","old1","old0","older"),selected.cards.map { it.title })
    }
    @Test fun yearChangesAutomaticallyAndFutureDaysOfCurrentYearAreExcluded() {
        val a=title("today");val b=title("tomorrow");val c=title("next-year")
        val s=HomeProbeCatalog(listOf(c,b,a),mapOf(a.id to HomeProbeDate(today),b.id to HomeProbeDate(today.plusDays(1)),c.id to HomeProbeDate(LocalDate.of(2027,1,1))))
        val now=homeProbeCards(s,today);assertEquals(listOf("today"),now.cards.map { it.title });assertEquals(2,now.futureDates)
        val next=homeProbeCards(s,LocalDate.of(2027,1,1));assertEquals(listOf("next-year","tomorrow","today"),next.cards.map { it.title });assertEquals(2027,next.asOf.year)
    }
    @Test fun fewerThanTenValidReleasesReportsMissingWithoutInventingCards() {
        val selected=homeProbeCards(snapshot(3,0),today);assertEquals(3,selected.cards.size);assertEquals(7,selected.missingSlots)
        val empty=homeProbeCards(snapshot(0,0),today);assertTrue(empty.cards.isEmpty());assertEquals(10,empty.missingSlots)
    }
    @Test fun duplicatesAndTieOrderAreStableAcrossCatalogReorder() {
        val a=title("a");val b=title("b");val s=HomeProbeCatalog(listOf(b,a,a),mapOf("a" to HomeProbeDate(today),"b" to HomeProbeDate(today)))
        assertEquals(listOf("a","b"),homeProbeCards(s,today).cards.map { it.title })
        assertEquals(homeProbeCards(s,today).cards,homeProbeCards(s.copy(titles=s.titles.reversed()),today).cards)
    }
    @Test fun explicitFullDateWinsOverCoarseYearAndDiscrepancyIsReported() {
        val a=title("a").copy(year=2026);val s=HomeProbeCatalog(listOf(a),mapOf("a" to HomeProbeDate(LocalDate.of(2025,1,1))))
        assertEquals(1,homeProbeCards(s,today).yearMismatches);assertEquals(1,homeProbeCards(s,today).cards.size)
    }
    @Test fun invalidMissingPartialAndConflictingDatesAreExcluded() {
        for (raw in listOf("2026","2026-02","2026-02-30","2026-01-01T10:00:00","", "not-a-date")) {
            assertNull(homeProbeReleaseDate("pelicula",mapOf("release_date" to raw)).date)
        }
        assertEquals("conflict",homeProbeReleaseDate("pelicula",mapOf("release_date" to "2026-01-01","fecha_estreno" to "2026-01-02")).issue)
        assertEquals(LocalDate.of(2024,2,29),homeProbeReleaseDate("pelicula",mapOf("release_date" to "2024-02-29")).date)
    }
    @Test fun seriesAndAnimeUseWholeTitleFieldsNeverSeasonsEpisodesOrAddedDates() {
        val raw="""{"schema_version":1,"items":[
            {"id":"movie","tipo":"pelicula","titulo":"Movie","telegram_url":"https://t.me/c/1/1","anio":"2026","fecha_agregado":"2026-10-01T00:00:00Z"},
            {"id":"series","tipo":"serie","titulo":"Series","telegram_url":"https://t.me/c/1/2","temporadas":[{"numero":1,"fecha_emision":"2026-01-01","episodios":[{"numero":1,"fecha_emision":"2026-01-01"}]}]},
            {"id":"anime","tipo":"anime","titulo":"Anime","telegram_url":"https://t.me/c/1/3","first_air_date":"2026-01-01"}
        ]}"""
        val snapshot=homeProbeParseCatalog(raw)
        assertNull(snapshot.releases.getValue("movie").date);assertNull(snapshot.releases.getValue("series").date)
        assertEquals(LocalDate.of(2026,1,1),snapshot.releases.getValue("anime").date)
    }
    @Test fun yearOnlyCatalogPublishesWithoutInventingDates() {
        val s=homeProbeParseCatalog("""{"schema_version":1,"items":[{"id":"a","tipo":"pelicula","titulo":"A","telegram_url":"https://t.me/c/1/1","year":2026}]}""")
        val selection=homeProbeCards(s,today);assertEquals(1,selection.cards.size);assertEquals(1,selection.missingDates)
    }
    @Test fun malformedOptionalDatesDoNotInvalidateTheCatalog() {
        for (date in listOf("{}","[]","true","42","null","\"bad\"")) {
            val snapshot=homeProbeParseCatalog("""{"schema_version":1,"items":[{"id":"a","tipo":"pelicula","titulo":"A","release_date":$date}]}""")
            assertEquals(1,snapshot.titles.size);assertNull(snapshot.releases.getValue("a").date)
        }
    }
    @Test fun invalidTransportIsNotAnAuthoritativeEmptySelection() {
        try {homeProbeParseCatalog("<html>failure</html>");fail("must abort") }catch(_:Exception){}
    }
    @Test fun repeatedSelectionPlansZeroChangesAndRefreshRemovesOnlyOwnedStalePrograms() {
        val before=homeProbeCards(snapshot(6),today).cards
        fun desired(cards:List<HomeProbeCard>)=cards.mapIndexed { index,card -> HomeProbeDesired(card.key,mapOf("title" to card.title,"weight" to (10-index).toString())) }
        val values=desired(before);val rows=values.mapIndexed { i,v -> HomeProbeStored(i.toLong(),v.key,v.payload) }
        assertTrue(homeProbePlan(rows,desired(before)).upserts.isEmpty())
        val after=homeProbeCards(snapshot(10,0),today).cards
        val plan=homeProbePlan(rows+HomeProbeStored(99,"unmanaged",emptyMap()),desired(after))
        assertFalse(plan.deleteIds.contains(99));assertTrue(plan.deleteIds.isNotEmpty())
    }
    @Test fun legacyChannelIsReusedOnlyWhenUniqueOwnedPreviewAndRecognized() {
        val legacy=HomeProbeChannel(2,"MiFlix - Prueba Home",null,"ours",true)
        val other=HomeProbeChannel(5,"MiFlix",HOME_PROBE_CHANNEL,"ours",true)
        assertEquals(2L,homeProbeChannel(listOf(legacy),"ours",5)?.id)
        assertNull(homeProbeChannel(listOf(other,legacy),"ours",5))
        assertEquals(5L,homeProbeChannel(listOf(other,legacy.copy(owner="foreign")),"ours",2)?.id)
        assertEquals(5L,homeProbeChannel(listOf(other,legacy.copy(preview=false)),"ours",2)?.id)
        assertEquals(5L,homeProbeChannel(listOf(other,legacy.copy(name="Unrelated")),"ours",2)?.id)
    }
    @Test fun deletedAndInvalidSavedIdsFallbackWithoutBlindAdoptionOrDuplication() {
        for(name in HOME_PROBE_CHANNEL_NAMES)assertEquals(3L,homeProbeChannel(listOf(HomeProbeChannel(3,name,null,"ours",true)),"ours",999)?.id)
        assertNull(homeProbeChannel(listOf(HomeProbeChannel(2,"Unrelated",null,"ours",true)),"ours",2))
        assertNull(homeProbeChannel(emptyList(),"ours",-1))
    }
    @Test fun mixedDatesAndYearsSortByYearThenKnownDateWithoutFakeDays() {
        val titles=listOf(title("unknown").copy(year=2026),title("dated").copy(year=2026),
            title("old").copy(year=2025),title("future").copy(year=2027),title("future-day").copy(year=2026))
        val s=HomeProbeCatalog(titles,mapOf("dated" to HomeProbeDate(today),"future-day" to HomeProbeDate(today.plusDays(1))))
        assertEquals(listOf("dated","unknown","old"),homeProbeCards(s,today).cards.map { it.title })
        assertEquals(2,homeProbeCards(s,today).futureDates)
    }
    @Test fun bothYearAliasesAndInvalidFullDatesRetainUsableYears() {
        for(field in listOf("year","anio")) {
            val s=homeProbeParseCatalog("""{"schema_version":1,"items":[{"id":"a","tipo":"pelicula","titulo":"A","telegram_url":"https://t.me/c/1/1","$field":2026,"release_date":"bad"}]}""")
            assertEquals(1,homeProbeCards(s,today).cards.size)
            assertNull(s.releases.getValue("a").date)
        }
    }
    @Test fun emptySelectionPreservesFivePublishedCardsWithoutWrites() {
        val previous=(1..5).map { HomeProbeStored(it.toLong(),HOME_PROBE_PREFIX+it,mapOf("title" to "old$it")) }
        val empty=homeProbeCards(HomeProbeCatalog(emptyList(),emptyMap()),today)
        assertTrue(empty.cards.isEmpty())
        val plan=homeProbePlan(previous,emptyList())
        assertTrue(plan.deleteIds.isEmpty());assertTrue(plan.upserts.isEmpty())
    }
    @Test fun yearOnlySelectionFillsTenAcrossPreviousYearsAndRollsOver() {
        val titles=(0..11).map { title("y$it").copy(year=if(it<6)2026 else 2025) }
        val s=HomeProbeCatalog(titles,emptyMap())
        assertEquals(10,homeProbeCards(s,today).cards.size)
        val future=HomeProbeCatalog(listOf(title("next").copy(year=2027)),emptyMap())
        assertTrue(homeProbeCards(future,today).cards.isEmpty())
        assertEquals(1,homeProbeCards(future,LocalDate.of(2027,1,1)).cards.size)
    }

}
