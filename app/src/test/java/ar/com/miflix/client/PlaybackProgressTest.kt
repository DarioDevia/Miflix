package ar.com.miflix.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackProgressTest {
    private class MemoryStorage : ProgressStorage {
        val entries = mutableMapOf<String, SavedProgress>()
        override fun read(key: String) = entries[key]
        override fun write(key: String, progress: SavedProgress) { entries[key] = progress }
        override fun remove(key: String) { entries.remove(key) }
    }

    @Test fun noProgressOrOnlyFirstSecondsStartsAtZero() {
        val store = PlaybackProgressStore(MemoryStorage())
        assertNull(store.resumablePosition("movie:a"))
        store.save("movie:a", 29_999L, 7_200_000L, 123L)
        assertNull(store.resumablePosition("movie:a"))
    }

    @Test fun progressCanResumeAndStoresDurationAndTimestamp() {
        val storage = MemoryStorage()
        val store = PlaybackProgressStore(storage)
        store.save("movie:a", 2_607_000L, 6_600_000L, 456L)
        assertEquals(2_607_000L, store.resumablePosition("movie:a"))
        assertEquals(SavedProgress(2_607_000L, 6_600_000L, 456L), storage.entries["movie:a"])
        assertEquals("43:27", formatProgressTime(2_607_000L))
    }

    @Test fun nearEndClearsProgressUsingFiveMinutesOrFivePercent() {
        val store = PlaybackProgressStore(MemoryStorage())
        store.save("movie", 6_299_999L, 6_600_000L, 1L)
        assertEquals(6_299_999L, store.resumablePosition("movie"))
        store.save("movie", 6_300_000L, 6_600_000L, 2L)
        assertNull(store.resumablePosition("movie"))
        store.save("episode", 1_139_999L, 1_200_000L, 1L)
        assertEquals(1_139_999L, store.resumablePosition("episode"))
        store.save("episode", 1_140_000L, 1_200_000L, 2L)
        assertNull(store.resumablePosition("episode"))
    }

    @Test fun startingFromBeginningRemovesOldPositionAndSavesAgain() {
        val store = PlaybackProgressStore(MemoryStorage())
        store.save("movie", 2_000_000L, 6_000_000L, 1L)
        store.clear("movie")
        assertNull(store.resumablePosition("movie"))
        store.save("movie", 90_000L, 6_000_000L, 2L)
        assertEquals(90_000L, store.resumablePosition("movie"))
    }

    @Test fun moviesAndEpisodesKeepIndependentPositions() {
        val store = PlaybackProgressStore(MemoryStorage())
        val movieA = ProgressKeys.title(Title(id = "movie-a"))
        val movieB = ProgressKeys.title(Title(id = "movie-b"))
        val series = Title(id = "series")
        val episodeA = ProgressKeys.episode(series, Season(numero = 1), Episode(id = "e1"))
        val episodeB = ProgressKeys.episode(series, Season(numero = 1), Episode(id = "e2"))
        for ((key, position) in listOf(movieA to 40_000L, movieB to 50_000L,
            episodeA to 60_000L, episodeB to 70_000L)) {
            store.save(key, position, 3_600_000L, 1L)
        }
        assertEquals(40_000L, store.resumablePosition(movieA))
        assertEquals(50_000L, store.resumablePosition(movieB))
        assertEquals(60_000L, store.resumablePosition(episodeA))
        assertEquals(70_000L, store.resumablePosition(episodeB))
        assertNotEquals(episodeA, episodeB)
    }

    @Test fun indexedPublicationsKeepKeyWhenListOrderChanges() {
        val title = Title(id = "anime")
        val first = ProgressKeys.episode(title, Season(numero = 0),
            Episode(id = "anime-publicacion-0", telegramUrl = "https://t.me/c/1/42"))
        val moved = ProgressKeys.episode(title, Season(numero = 0),
            Episode(id = "anime-publicacion-3", telegramUrl = "https://t.me/c/1/42"))
        assertEquals(first, moved)
    }

    @Test fun adminEpisodeTmdbIdsSaveIndependentProgressAndTakePrecedenceOverLegacyId() {
        val storage = MemoryStorage()
        val store = PlaybackProgressStore(storage)
        val title = Title(id = "serie-imdb-tt6656238")
        val season = Season(numero = 1)
        val first = ProgressKeys.episode(title, season, Episode(tmdbId = 1458520))
        val second = ProgressKeys.episode(title, season, Episode(tmdbId = 1482178))
        assertNotEquals(first, second)
        assertEquals(first, ProgressKeys.episode(title, season,
            Episode(id = "legacy", tmdbId = 1458520)))
        store.save(first, 60_000L, 3_000_000L, 1L)
        store.save(second, 90_000L, 3_000_000L, 2L)
        assertEquals(60_000L, store.resumablePosition(first))
        assertEquals(90_000L, store.resumablePosition(second))
    }

    @Test fun legacyIdsAndMissingIdsKeepSeparateFallbacks() {
        val title = Title(id = "series")
        val season = Season(numero = 1)
        assertEquals("episode:series:1:e1",
            ProgressKeys.episode(title, season, Episode(id = "e1")))
        val first = ProgressKeys.episode(title, season,
            Episode(numero = 1, telegramUrl = "https://t.me/c/1/1"))
        val second = ProgressKeys.episode(title, season,
            Episode(numero = 2, telegramUrl = "https://t.me/c/1/2"))
        assertNotEquals(first, second)
        assertEquals(first, ProgressKeys.episode(title, season,
            Episode(numero = 1, telegramUrl = "https://t.me/c/1/1")))
    }

    @Test fun directTitlePlaybackAndItsEpisodeShareTheSameProgress() {
        val episode = Episode(id = "anime-publicacion-0", telegramUrl = "https://t.me/c/1/42")
        val season = Season(numero = 0, episodios = listOf(episode))
        val title = Title(id = "anime", telegramUrl = episode.telegramUrl,
            temporadas = listOf(season))
        assertEquals(ProgressKeys.episode(title, season, episode), ProgressKeys.title(title))
    }

    @Test fun leavingBeforeThresholdRemovesOldProgress() {
        val store = PlaybackProgressStore(MemoryStorage())
        store.save("movie", 900_000L, 3_600_000L, 1L)
        store.finish("movie", 4_000L, 3_600_000L, 2L)
        assertNull(store.resumablePosition("movie"))
    }
}
