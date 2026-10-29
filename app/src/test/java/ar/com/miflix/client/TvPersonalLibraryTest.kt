package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

class TvPersonalLibraryTest {
    private class Memory(var json: String? = null, var fail: Boolean = false) : TvLibraryStorage {
        var writes = 0
        override fun read() = json
        override fun write(json: String) { if (fail) error("disk"); this.json = json; writes++ }
    }
    private val movie = Title(id = "movie", tipo = "pelicula", titulo = "Movie", telegramUrl = "https://t.me/c/1/1")
    @Test fun favoritesOfAllTypesAreDurableAndDeduplicated() {
        val memory = Memory(); val store = TvPersonalLibraryStore(memory)
        val titles = listOf(movie, movie.copy(id = "series", tipo = "serie"), movie.copy(id = "anime", tipo = "anime"))
        titles.forEach { store.setFavorite(it.id, true); store.setFavorite(it.id, true) }
        val reopened = TvPersonalLibraryStore(memory)
        assertEquals(3, reopened.favorites(titles+titles).size)
        assertEquals(3, memory.writes)
        reopened.setFavorite(movie.id, false)
        assertFalse(TvPersonalLibraryStore(memory).isFavorite(movie.id))
        assertFalse(memory.json!!.contains("https://"))
    }
    @Test fun missingCatalogTitleNeverDeletesPersonalRecord() {
        val memory = Memory(); val store = TvPersonalLibraryStore(memory)
        store.setFavorite(movie.id, true); store.setTitleWatched(movie, true)
        val json = memory.json
        assertTrue(store.favorites(emptyList()).isEmpty())
        assertTrue(store.watchedTitles(emptyList()).isEmpty())
        assertEquals(json, memory.json)
        assertEquals(listOf(movie), store.favorites(listOf(movie)))
        assertEquals(listOf(movie), store.watchedTitles(listOf(movie)))
    }
    @Test fun episodeMarksNeverMarkParentOrOtherEpisodes() {
        val first = Episode(id = "first"); val second = Episode(id = "second")
        val season = Season(numero = 1, episodios = listOf(first, second))
        val series = Title(id = "series", tipo = "serie", temporadas = listOf(season))
        val store = TvPersonalLibraryStore(Memory())
        store.setWatched(ProgressKeys.episode(series, season, first), true)
        assertFalse(store.isTitleWatched(series)); assertTrue(store.hasWatchedEpisode(series))
        assertFalse(store.isWatched(ProgressKeys.episode(series, season, second)))
        assertEquals(listOf(series), store.watchedTitles(listOf(series)))
    }
    @Test fun seriesTitleMarkDoesNotFanOutToEpisodes() {
        val episode = Episode(id = "e")
        val season = Season(numero = 1, episodios = listOf(episode))
        val series = Title(id = "series", tipo = "serie", temporadas = listOf(season))
        val store = TvPersonalLibraryStore(Memory())
        store.setTitleWatched(series, true)
        assertFalse(store.isWatched(ProgressKeys.episode(series, season, episode)))
    }
    @Test fun movieWatchAndUnwatchAreDurableWithoutReusingOldCoverage() {
        val memory = Memory(); var store = TvPersonalLibraryStore(memory)
        val key = ProgressKeys.title(movie)
        store.record(key, WatchedInterval(0, 700_000), 1_000_000)
        store.setTitleWatched(movie, true)
        store = TvPersonalLibraryStore(memory)
        assertTrue(store.isTitleWatched(movie))
        store.setTitleWatched(movie, false)
        store.record(key, WatchedInterval(950_000, 1_000_000), 1_000_000)
        store.flush()
        assertFalse(TvPersonalLibraryStore(memory).isTitleWatched(movie))
    }
    @Test fun actualConsumptionCanMarkWatchedButSeekToCreditsCannot() {
        val store = TvPersonalLibraryStore(Memory()); val key = ProgressKeys.title(movie)
        store.record(key, WatchedInterval(950_000, 1_000_000), 1_000_000)
        assertFalse(store.isTitleWatched(movie))
        store.record(key, WatchedInterval(0, 800_000), 1_000_000)
        assertTrue(store.isTitleWatched(movie))
    }
    @Test fun coverageSurvivesCloseAndMergesWithoutDoubleCounting() {
        val memory = Memory(); val key = ProgressKeys.title(movie)
        val first = TvPersonalLibraryStore(memory)
        first.record(key, WatchedInterval(0, 500_000), 1_000_000); first.flush()
        val second = TvPersonalLibraryStore(memory)
        second.record(key, WatchedInterval(100_000, 500_000), 1_000_000)
        assertFalse(second.isTitleWatched(movie))
        second.record(key, WatchedInterval(500_000, 1_000_000), 1_000_000)
        assertTrue(TvPersonalLibraryStore(memory).isTitleWatched(movie))
    }
    @Test fun invalidDataAreReadOnlyAndNeverOverwritten() {
        for (json in listOf("{invalid", "null", "[]", "", "{\"favorites\":4}", "{\"schemaVersion\":1.5}")) {
            val memory = Memory(json); val store = TvPersonalLibraryStore(memory)
            assertFalse(json, store.canWrite)
            store.setFavorite(movie.id, true); store.setTitleWatched(movie, true); store.flush()
            assertEquals(json, memory.json); assertEquals(0, memory.writes)
        }
    }
    @Test fun futureAndUnknownSchemasArePreservedByteExactly() {
        for (json in listOf("{\"schemaVersion\":2,\"favorites\":[\"movie\"],\"future\":true}",
            "{\"schemaVersion\":1,\"favorites\":[\"movie\"],\"unknown\":{}}")) {
            val memory = Memory(json); val store = TvPersonalLibraryStore(memory)
            assertFalse(store.canWrite); assertTrue(store.isFavorite(movie.id))
            store.setFavorite(movie.id, false); store.setWatched("title:movie", true); store.flush()
            assertEquals(json, store.exportJson()); assertEquals(0, memory.writes)
        }
    }
    @Test fun readFailureNeverTriggersAnEmptyOverwrite() {
        var writes = 0
        val store = TvPersonalLibraryStore(object : TvLibraryStorage {
            override fun read(): String? = error("read failed")
            override fun write(json: String) { writes++ }
        })
        store.setFavorite("a", true); store.flush()
        assertFalse(store.canWrite); assertEquals(0, writes)
    }
    @Test fun failedWriteRollsBackVisibleSelection() {
        val memory = Memory(fail = true); val store = TvPersonalLibraryStore(memory)
        store.setFavorite(movie.id, true)
        assertFalse(store.isFavorite(movie.id)); assertNotNull(store.lastError)
        memory.fail = false; store.setFavorite(movie.id, true)
        assertTrue(TvPersonalLibraryStore(memory).isFavorite(movie.id)); assertNull(store.lastError)
    }
    @Test fun unmarkChangesGenerationAndClearsMoviePublicationAliases() {
        val episode = Episode(id = "publication", telegramUrl = movie.telegramUrl)
        val season = Season(numero = 0, episodios = listOf(episode))
        val publication = movie.copy(temporadas = listOf(season))
        val key = ProgressKeys.episode(publication, season, episode)
        val store = TvPersonalLibraryStore(Memory())
        store.setWatched(key, true); assertTrue(store.isTitleWatched(publication))
        store.setTitleWatched(publication, false)
        assertFalse(store.isWatched(key)); assertFalse(store.isTitleWatched(publication))
        assertEquals(2L, store.generation(key))
    }
    @Test fun blankIdentifiersAreNeverPersisted() {
        val memory = Memory(); val store = TvPersonalLibraryStore(memory)
        store.setFavorite(" ", true); store.setWatched("", true); store.setTitleWatched(Title(), true)
        assertEquals(0, memory.writes)
    }
}
