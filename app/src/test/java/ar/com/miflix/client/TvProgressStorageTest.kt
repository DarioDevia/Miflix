package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

class TvProgressStorageTest {
    private class Memory : ProgressStorage {
        val entries = mutableMapOf<String, SavedProgress>()
        override fun read(key: String) = entries[key]
        override fun write(key: String, progress: SavedProgress) { entries[key] = progress }
        override fun remove(key: String) { entries.remove(key) }
    }
    private val memory = Memory()
    private val store = PlaybackProgressStore(TvProgressStorage(memory))
    private val movie = Title(id = "movie", tipo = "pelicula", telegramUrl = "https://t.me/c/1/1")
    private val key = ProgressKeys.title(movie)
    private fun row() = continueWatching(listOf(movie), store)

    @Test fun pendingMovieStaysAndUnknownDurationPreservesPositionAndTimestamp() {
        store.save(key, 60_000L, 3_600_000L, 1L)
        store.save(key, 90_000L, 0L, 2L)
        assertEquals(SavedProgress(90_000L, 3_600_000L, 2L), row().single().progress)
    }
    @Test fun unknownDurationAtFinishUsesSameCompletionMarginWithoutDeletingRecord() {
        store.save(key, 60_000L, 3_600_000L, 1L)
        store.finish(key, 3_420_000L, -9223372036854775807L, 2L)
        assertTrue(row().isEmpty())
        assertEquals(3_420_000L, memory.entries[key]?.positionMs)
    }
    @Test fun lateUnknownCheckpointAfterCompletionCannotResurrectMovie() {
        store.save(key, 60_000L, 3_600_000L, 1L)
        store.save(key, 3_600_000L, 3_600_000L, 2L)
        assertTrue(row().isEmpty())
        store.finish(key, 3_600_000L, 0L, 3L)
        assertTrue(row().isEmpty())
    }
    @Test fun newSessionReadsPersistedDurationAndStillExcludesCompletedRecord() {
        memory.entries[key] = SavedProgress(3_500_000L, 3_600_000L, 1L)
        val reopened = PlaybackProgressStore(TvProgressStorage(memory))
        assertTrue(continueWatching(listOf(movie), reopened).isEmpty())
        assertNotNull(memory.entries[key])
    }
    @Test fun durationNeverKnownCannotBeInvented() {
        store.save(key, 90_000L, 0L, 1L)
        assertEquals(0L, row().single().progress.durationMs)
    }
    @Test fun explicitRestartCanCreateRealPendingProgressAgain() {
        store.save(key, 60_000L, 3_600_000L, 1L)
        store.clear(key)
        assertTrue(row().isEmpty())
        store.save(key, 90_000L, 0L, 2L)
        assertEquals(90_000L, row().single().progress.positionMs)
    }
    @Test fun unstartedAndFirstSecondsDoNotAppear() {
        assertTrue(row().isEmpty())
        store.save(key, 10_000L, 3_600_000L, 1L)
        assertTrue(row().isEmpty())
    }
    @Test fun existingMobileStorageStillHasItsOriginalBehavior() {
        val mobile = PlaybackProgressStore(memory)
        mobile.save(key, 60_000L, 3_600_000L, 1L)
        mobile.save(key, 3_600_000L, 0L, 2L)
        // Reproduces the original defect; only the TV adapter changes this behavior.
        assertEquals(1, continueWatching(listOf(movie), mobile).size)
    }
    @Test fun mixedEpisodesStayIndependentAndNoNextEpisodeIsCreated() {
        val episodes = (1..4).map { Episode(id = "e$it", numero = it, telegramUrl = "https://t.me/c/1/$it") }
        val season = Season(numero = 2, episodios = episodes)
        val title = Title(id = "series", tipo = "serie", temporadas = listOf(season))
        val keys = episodes.map { ProgressKeys.episode(title, season, it) }
        store.save(keys[0], 60_000L, 1_200_000L, 1L)
        store.save(keys[1], 60_000L, 1_200_000L, 2L)
        store.finish(keys[1], 1_140_000L, 0L, 3L)
        store.save(keys[2], 70_000L, 1_200_000L, 4L)
        val snapshot = memory.entries.toMap()
        val row = continueWatching(listOf(title, title), store)
        assertEquals(listOf(keys[2], keys[0]), row.map { it.key })
        assertEquals(snapshot, memory.entries)
        assertFalse(memory.entries.containsKey(keys[3]))
    }
    @Test fun rowRecalculationRemovesFinishedMovieWithoutChangingOtherHistory() {
        store.save(key, 60_000L, 3_600_000L, 1L)
        assertEquals(1, row().size)
        val unrelated = SavedProgress(90_000L, 1_200_000L, 4L)
        memory.entries["unrelated"] = unrelated
        store.finish(key, 3_600_000L, 0L, 5L)
        assertTrue(row().isEmpty())
        assertEquals(unrelated, memory.entries["unrelated"])
    }
}
