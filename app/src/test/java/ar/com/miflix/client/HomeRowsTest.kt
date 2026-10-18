package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

class HomeRowsTest {
    private fun t(id:String,type:String="pelicula",vararg genres:String)=
        Title(id=id,titulo="Título $id",tipo=type,generos=genres.toList())
    private val catalog=listOf(t("a","pelicula","Acción","Ciencia ficción"),t("b","pelicula"," ACCION "),
        t("c","pelicula","Comedia"),t("s1","serie","Acción","Drama"),t("s2","serie","Drama"),
        t("n1","anime","Acción","Animación"),t("n2","anime","Animación"),t("n3","anime"))
    private fun row(key:String)=buildHomeRows(catalog).genres.first { it.key=="genre:$key" }

    @Test fun groupsRealGenresAcrossTypes() {
        assertEquals(setOf("a","b","s1","n1"),row("accion").titles.map { it.id }.toSet())
        assertEquals(setOf("a"),row("ciencia ficcion").titles.map { it.id }.toSet())
    }
    @Test fun sameTitleCanAppearInSeveralGenres() {
        assertTrue(row("accion").titles.any { it.id=="a" })
        assertTrue(row("ciencia ficcion").titles.any { it.id=="a" })
    }
    @Test fun emptyAndIncompleteMetadataAreSafe() {
        val nulls=com.google.gson.Gson().fromJson(
            """{"id":"null","titulo":"Nulo","tipo":"pelicula","generos":[null,"","  "]}""", Title::class.java)
        val rows=buildHomeRows(listOf(nulls,t("valid"),Title(id="",titulo="Sin ID"),Title(id="bad")))
        assertTrue(rows.genres.isEmpty());assertEquals(setOf("null","valid"),rows.general.single().titles.map { it.id }.toSet())
    }
    @Test fun duplicateIdsAndRepeatedGenreSpellingsNeverDuplicateCards() {
        val source=listOf(catalog.first().copy(generos=listOf("Acción","Accion"," ACCIÓN ","Ciencia ficción")))+catalog.drop(1)+catalog.first()
        buildHomeRows(source).let { rows ->
            (rows.general+rows.genres).forEach { assertEquals(it.titles.size,it.titles.map { t->t.id }.toSet().size) }
        }
    }
    @Test fun orderKeysLabelsAndItemsAreStableWhenCatalogOrderChanges() {
        assertEquals(buildHomeRows(catalog),buildHomeRows(catalog.reversed()))
        val genres=buildHomeRows(catalog).genres
        assertEquals(genres.map { (it.more as HomeMoreTarget.Genre).key }.sorted(),genres.map { (it.more as HomeMoreTarget.Genre).key })
    }
    @Test fun preservesGeneralCategoriesInExplicitOrder() {
        val rows=buildHomeRows(catalog).general
        assertEquals(listOf("Películas","Series","Anime"),rows.map { it.label })
        assertEquals(listOf("type:pelicula","type:serie","type:anime"),rows.map { it.key })
        assertEquals(HomeMoreTarget.Category("Series"),rows[1].more)
    }
    @Test fun genresDoNotInventTypesOrMergeSemanticLabels() {
        assertTrue(buildHomeRows(listOf(t("a"),t("b","serie"),t("c","anime"))).genres.isEmpty())
        val titles=listOf(t("a","pelicula","Acción y aventura"),t("b","pelicula","Acción"),t("c","pelicula","Aventura"),t("d"))
        assertEquals(setOf("accion y aventura","accion","aventura"),buildHomeRows(titles).genres.map { (it.more as HomeMoreTarget.Genre).key }.toSet())
    }
    @Test fun noEmptyRowsAndExactEquivalentRowsAreSuppressed() {
        val rows=buildHomeRows(listOf(t("a","pelicula","Uno","Dos"),t("b","pelicula")))
        assertEquals(1,rows.genres.size)
        assertTrue((rows.general+rows.genres).all { it.titles.isNotEmpty() })
        assertTrue(buildHomeRows(listOf(t("a","pelicula","Uno"))).genres.isEmpty())
        assertTrue(buildHomeRows(catalog).genres.none { it.key=="genre:drama" })
    }
    @Test fun genreLimitIsBoundedAndChoosesMostPopulatedBeforeAlphabeticPresentation() {
        val titles=(0..19).map { t("$it","pelicula","G${it.toString().padStart(2,'0')}") }+
            listOf(t("extra1","serie","G19"),t("extra2","anime","G19"))
        assertEquals(8,buildHomeRows(titles).genres.size)
        assertTrue(buildHomeRows(titles).genres.any { it.key=="genre:g19" })
        assertEquals(2,buildHomeRows(titles,2).genres.size)
        assertTrue(buildHomeRows(titles,-1).genres.isEmpty())
    }
    @Test fun emptyAndSingleTitleCatalogsRemainUsable() {
        assertEquals(HomeCatalogRows(emptyList(),emptyList()),buildHomeRows(emptyList()))
        val rows=buildHomeRows(listOf(t("only")))
        assertEquals("only",rows.general.single().titles.single().id)
    }
    private class Disk:LibraryStorage {
        var json:String?=null;var writes=0
        override fun read()=json
        override fun write(json:String){this.json=json;writes++}
    }
    @Test fun favoritesReuseExistingStoreAndDoNotWriteOrDeleteOrphans() {
        val disk=Disk();val store=PersonalLibraryStore(disk)
        store.setFavorite("a",true);store.setFavorite("temporarily-missing",true)
        val writes=disk.writes
        val row=homeFavoriteRow(catalog,store)!!
        assertEquals(listOf("a"),row.titles.map { it.id });assertEquals(HomeMoreTarget.Library,row.more)
        assertEquals(writes,disk.writes)
        assertTrue(PersonalLibraryStore(disk).isFavorite("temporarily-missing"))
        assertNotNull(homeFavoriteRow(listOf(t("temporarily-missing")),PersonalLibraryStore(disk)))
    }
    @Test fun favoriteRowIsHiddenWithoutAvailableFavorites() {
        assertNull(homeFavoriteRow(catalog,null))
        val store=PersonalLibraryStore(Disk())
        assertNull(homeFavoriteRow(catalog,store));store.setFavorite("missing",true)
        assertNull(homeFavoriteRow(catalog,store));assertTrue(store.isFavorite("missing"))
    }
    @Test fun cardsDeliverOriginalCatalogTitleToExistingDetailCallback() {
        val row=row("accion");var selected:Title?=null
        row.select("s1"){selected=it}
        assertSame(catalog.first { it.id=="s1" },selected)
        selected=null;row.select("removed"){selected=it};assertNull(selected)
    }
    @Test fun genreSeeAllMatchesExistingSearchFilterForEveryVisibleRow() {
        val index=CatalogSearchIndex(catalog)
        buildHomeRows(catalog).genres.forEach { row ->
            val genre=(row.more as HomeMoreTarget.Genre).key
            assertEquals(row.titles.map { it.id }.toSet(),index.search("",SearchFilters(genre=genre)).map { it.id }.toSet())
        }
    }
    @Test fun sharedGenreNormalizationHandlesWhitespaceCaseAndAccents() {
        val titles=listOf(t("a","pelicula","  Ciencia   FICCIÓN "),t("b","serie","ciencia ficción"),t("c"))
        val row=buildHomeRows(titles).genres.single()
        assertEquals("genre:ciencia ficcion",row.key)
        assertEquals(setOf("a","b"),CatalogSearchIndex(titles).search("",SearchFilters(genre="CIENCIA  FICCION")).map { it.id }.toSet())
        assertEquals(1,CatalogSearchIndex(titles).genres.size)
    }
    @Test fun genreLimitDoesNotCapAvailableTitlesInARow() {
        val titles=(0..1199).map { t("$it",if(it%2==0) "pelicula" else "serie","Acción") }
        assertEquals(1200,buildHomeRows(titles).genres.single().titles.size)
    }
}
