package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

class ContinueWatchingTest {
    private class MemoryStorage : ProgressStorage {
        val entries = mutableMapOf<String, SavedProgress>()
        override fun read(key: String) = entries[key]
        override fun write(key: String, progress: SavedProgress) { entries[key] = progress }
        override fun remove(key: String) { entries.remove(key) }
    }
    private val storage = MemoryStorage()
    private val store = PlaybackProgressStore(storage)
    private val movie = Title(id = "movie", tipo = "pelicula", titulo = "Película",
        telegramUrl = "https://t.me/c/1/1")
    private fun seed(key: String, position: Long = 60_000L, duration: Long = 3_600_000L,
        timestamp: Long = 1L) { storage.entries[key] = SavedProgress(position, duration, timestamp) }

    @Test fun validMovieUsesExistingKeyAndProgress() {
        val key = ProgressKeys.title(movie)
        seed(key)
        val item = continueWatching(listOf(movie), store).single()
        assertEquals(movie, item.title)
        assertEquals(key, item.key)
        assertEquals(storage.entries[key], item.progress)
        assertNull(item.episodeIndex)
        assertEquals(1f / 60f, item.fraction!!, 0.0001f)
    }

    @Test fun completedProgressIsExcludedIncludingExactThreshold() {
        seed(ProgressKeys.title(movie), position = 3_420_000L)
        assertTrue(continueWatching(listOf(movie), store).isEmpty())
        seed(ProgressKeys.title(movie), position = 3_600_000L)
        assertTrue(continueWatching(listOf(movie), store).isEmpty())
    }

    @Test fun insignificantProgressIsExcludedButThresholdIsIncluded() {
        seed(ProgressKeys.title(movie), position = 29_999L)
        assertTrue(continueWatching(listOf(movie), store).isEmpty())
        seed(ProgressKeys.title(movie), position = 30_000L)
        assertEquals(1, continueWatching(listOf(movie), store).size)
    }

    @Test fun recentFirstAndOrphanIgnored() {
        val other = movie.copy(id = "other")
        seed(ProgressKeys.title(movie), timestamp = 10L)
        seed(ProgressKeys.title(other), timestamp = 20L)
        seed("title:removed", timestamp = 30L)
        assertEquals(listOf("other", "movie"),
            continueWatching(listOf(movie, other), store).map { it.title.id })
        assertTrue(continueWatching(emptyList(), store).isEmpty())
    }

    @Test fun seriesEpisodeResolvesTmdbIdentityAndCorrectSeasonAndEpisode() {
        val first = Episode(tmdbId = 11, numero = 1, telegramUrl = "https://t.me/c/1/2")
        val second = Episode(tmdbId = 12, numero = 2, telegramUrl = "https://t.me/c/1/3")
        val season = Season(numero = 2, episodios = listOf(first, second))
        val title = Title(id = "series", tipo = "serie", temporadas = listOf(Season(numero = 1), season))
        seed(ProgressKeys.episode(title, season, second))
        val item = continueWatching(listOf(title), store).single()
        assertEquals(1, item.seasonIndex)
        assertEquals(1, item.episodeIndex)
        assertEquals(second, item.episode)
        assertTrue(item.label!!.startsWith("T2 · E2"))
    }

    @Test fun animePublicationSurvivesReorderAndDirectLinkDoesNotDuplicate() {
        val old = Episode(id = "anime-publicacion-0", numero = 1, telegramUrl = "https://t.me/c/1/42")
        val title = Title(id = "anime", tipo = "anime", telegramUrl = old.telegramUrl)
        seed(ProgressKeys.episode(title, Season(numero = 0), old))
        val moved = old.copy(id = "anime-publicacion-3")
        val current = title.copy(temporadas = listOf(Season(numero = 0,
            episodios = listOf(Episode(), moved))))
        val item = continueWatching(listOf(current), store).single()
        assertEquals(1, item.episodeIndex)
        assertEquals(moved, item.episode)
        assertEquals(ProgressKeys.title(current), item.key)
    }

    @Test fun legacyEpisodeUsesExistingFallbackKey() {
        val episode = Episode(id = "legacy", telegramUrl = "https://t.me/c/1/9")
        val season = Season(numero = 1, episodios = listOf(episode))
        val title = Title(id = "series", tipo = "serie", temporadas = listOf(season))
        seed("episode:series:1:legacy")
        assertEquals(episode, continueWatching(listOf(title), store).single().episode)
    }

    @Test fun unknownDurationHasNoVisualFraction() {
        seed(ProgressKeys.title(movie), duration = 0L)
        assertNull(continueWatching(listOf(movie), store).single().fraction)
    }
}
