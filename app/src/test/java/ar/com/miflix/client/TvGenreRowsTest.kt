package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

class TvGenreRowsTest {
    private fun movie(id:String, vararg genres:String)=Title(id=id,tipo="pelicula",titulo=id,
        telegramUrl="https://t.me/c/1/1",generos=genres.toList())
    @Test fun missingGenresAndUnavailableTitlesDoNotCreateRows() {
        assertTrue(tvGenreRows(listOf(movie("a"),movie("b","Drama").copy(telegramUrl=null))).isEmpty())
    }
    @Test fun accentsCaseAndWhitespaceMergeWithoutSemanticAliases() {
        val rows=tvGenreRows(listOf(movie("a"," ACCIÓN ","Accion"),movie("b","acción"),
            movie("c","Ciencia   ficción","Ciencia ficción"),movie("d","Suspenso","Thriller")))
        assertEquals(4,rows.size);assertEquals(listOf("a","b"),rows.first { it.key.endsWith(":accion") }.titles.map { it.id })
        assertEquals(1,rows.first { it.key.endsWith(":ciencia ficcion") }.titles.size)
    }
    @Test fun multiGenreIsAllowedButSameTitleNeverRepeatsInRow() {
        val a=movie("a","Drama","Comedia","Drama")
        assertEquals(listOf(1,1),tvGenreRows(listOf(a,a)).map { it.titles.size })
    }
    @Test fun typesStaySeparatedAndAnimeIsNotInventedAsGenre() {
        val a=movie("a","Drama");val s=a.copy(id="s",tipo="serie");val n=a.copy(id="n",tipo="anime")
        val rows=tvGenreRows(listOf(n,s,a))
        assertEquals(listOf("Películas · Drama","Series · Drama","Anime · Drama"),rows.map { it.label })
        assertEquals(listOf("pelicula","serie","anime"),rows.map { it.titles.single().tipo })
    }
    @Test fun numericIdsAreNotGuessedAsNamesOrMappedWithoutContract() {
        assertTrue(tvGenreRows(listOf(movie("a","28"," "))).isEmpty())
    }
    @Test fun malformedOptionalGenreMetadataDoesNotBlockTvCatalog() {
        if(!BuildConfig.IS_TV)return
        for (genres in listOf("null","{}","[null,{},true,28,\"Drama\"]")) {
            val c=CatalogRepository.parseCatalog("""{"schema_version":1,"items":[{"id":"a","tipo":"pelicula","titulo":"A","generos":$genres}]}""")
            assertEquals("a",c.items.single().id)
            assertTrue(c.items.single().generos.all { it == "Drama" })
        }
    }
    @Test fun largeCatalogBuildsLocalReferencesWithoutLimitsOrCopies() {
        val titles=(0 until 12000).map { movie("m$it","Drama","Comedia") }
        val rows=tvGenreRows(titles);assertEquals(2,rows.size);assertEquals(12000,rows[0].titles.size)
        assertSame(titles[0],rows[0].titles.first { it.id=="m0" })
    }
}
