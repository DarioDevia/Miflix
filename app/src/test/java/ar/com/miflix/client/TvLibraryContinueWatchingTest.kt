package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

class TvLibraryContinueWatchingTest {
    private class Memory : ProgressStorage {
        val entries = mutableMapOf<String,SavedProgress>()
        override fun read(key:String)=entries[key]
        override fun write(key:String, progress:SavedProgress) { entries[key]=progress }
        override fun remove(key:String) { entries.remove(key) }
    }
    private class LibraryMemory : TvLibraryStorage {
        var json:String?=null
        override fun read()=json
        override fun write(json:String) { this.json=json }
    }
    @Test fun markingMovieSeenHidesItWithoutDeletingProgressAndUnmarkOnlyRevealsPending() {
        val memory=Memory(); val progress=PlaybackProgressStore(TvProgressStorage(memory))
        val persisted=LibraryMemory(); val library=TvPersonalLibraryStore(persisted)
        val movie=Title(id="m",tipo="pelicula",telegramUrl="https://t.me/c/1/1")
        val key=ProgressKeys.title(movie)
        progress.save(key,60_000L,3_600_000L,1L)
        val original=memory.entries.toMap()
        library.setTitleWatched(movie,true)
        assertTrue(tvContinueWatching(listOf(movie),progress,library).isEmpty())
        assertEquals(original,memory.entries)
        library.setTitleWatched(movie,false)
        assertEquals(1,tvContinueWatching(listOf(movie),progress,library).size)
        assertFalse(TvPersonalLibraryStore(persisted).isTitleWatched(movie))
        assertEquals(original,memory.entries)
    }
    @Test fun markingEpisodeOnlyHidesThatEpisodeAndUnstartedNeverAppears() {
        val memory=Memory(); val progress=PlaybackProgressStore(TvProgressStorage(memory))
        val library=TvPersonalLibraryStore(LibraryMemory())
        val episodes=(1..3).map { Episode(id="e$it",numero=it,telegramUrl="https://t.me/c/1/$it") }
        val season=Season(numero=1,episodios=episodes)
        val series=Title(id="s",tipo="serie",temporadas=listOf(season))
        val keys=episodes.map { ProgressKeys.episode(series,season,it) }
        progress.save(keys[0],60_000L,1_200_000L,1L);progress.save(keys[1],90_000L,1_200_000L,2L)
        library.setWatched(keys[0],true)
        assertEquals(listOf(keys[1]),tvContinueWatching(listOf(series),progress,library).map { it.key })
        library.setWatched(keys[2],true);library.setWatched(keys[2],false)
        assertFalse(tvContinueWatching(listOf(series),progress,library).any { it.key==keys[2] })
        assertFalse(memory.entries.containsKey(keys[2]))
    }
    @Test fun completedProgressNeverReturnsWhenUnmarkedAndLegacyMobileProjectionStaysUnchanged() {
        val memory=Memory();val progress=PlaybackProgressStore(memory);val library=TvPersonalLibraryStore(LibraryMemory())
        val movie=Title(id="m",tipo="pelicula",telegramUrl="https://t.me/c/1/1")
        val key=ProgressKeys.title(movie)
        memory.entries[key]=SavedProgress(3_600_000L,3_600_000L,1L)
        library.setTitleWatched(movie,true);library.setTitleWatched(movie,false)
        assertTrue(tvContinueWatching(listOf(movie),progress,library).isEmpty())
        memory.entries[key]=SavedProgress(60_000L,3_600_000L,1L)
        library.setTitleWatched(movie,true)
        assertEquals(1,continueWatching(listOf(movie),progress).size)
        assertTrue(tvContinueWatching(listOf(movie),progress,library).isEmpty())
    }
}
