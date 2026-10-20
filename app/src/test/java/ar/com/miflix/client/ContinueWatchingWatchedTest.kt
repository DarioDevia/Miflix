package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

/** Tests the local projection against real stores, without a Player or Telegram mock. */
class ContinueWatchingWatchedTest {
    private class ProgressDisk : ProgressStorage {
        val entries = mutableMapOf<String, SavedProgress>()
        override fun read(key: String) = entries[key]
        override fun write(key: String, progress: SavedProgress) { entries[key] = progress }
        override fun remove(key: String) { entries.remove(key) }
    }
    private class LibraryDisk(var json: String? = null) : LibraryStorage {
        override fun read() = json
        override fun write(json: String) { this.json = json }
    }
    private val disk = ProgressDisk()
    private val libraryDisk = LibraryDisk()
    private val progress = PlaybackProgressStore(disk)
    private val library = PersonalLibraryStore(libraryDisk)
    private val movie = Title(id = "movie", tipo = "pelicula", titulo = "Movie",
        telegramUrl = "https://t.me/c/1/1")
    private fun seed(key: String, position: Long = 60_000, duration: Long = 3_600_000,
        updated: Long = 1) { disk.entries[key] = SavedProgress(position, duration, updated) }
    private fun row(titles: List<Title>) = continueWatching(titles, progress, library)
    private fun series(type: String = "serie"): Title = Title(id = type, tipo = type,
        temporadas = listOf(Season(numero = 1, episodios = (1..4).map {
            Episode(tmdbId = it.toLong(), numero = it, telegramUrl = "https://t.me/c/1/${it + 1}")
        })))
    private fun key(title: Title, index: Int) =
        ProgressKeys.episode(title, title.temporadas[0], title.temporadas[0].episodios[index])

    @Test fun pendingMovieRemainsUntilMarkedAndUnmarkRestoresOnlyValidProgress() {
        val key = ProgressKeys.title(movie)
        seed(key)
        assertEquals(key, row(listOf(movie)).single().key)
        val saved = disk.entries.toMap()
        library.setTitleWatched(movie, true)
        assertTrue(row(listOf(movie)).isEmpty())
        assertEquals(saved, disk.entries)
        library.setTitleWatched(movie, false)
        assertEquals(key, row(listOf(movie)).single().key)
        assertEquals(saved, disk.entries)
    }

    @Test fun unmarkWithoutPendingProgressNeverCreatesAnEntry() {
        library.setTitleWatched(movie, true)
        library.setTitleWatched(movie, false)
        assertTrue(row(listOf(movie)).isEmpty())
        seed(ProgressKeys.title(movie), position = 3_420_000)
        assertTrue(row(listOf(movie)).isEmpty())
        seed(ProgressKeys.title(movie), position = 29_999)
        assertTrue(row(listOf(movie)).isEmpty())
    }

    @Test fun completedMovieRecordIsHiddenWithoutRemovingItOrMarkingWatched() {
        val key = ProgressKeys.title(movie)
        seed(key, position = 3_420_000)
        val before = disk.entries.toMap()
        assertTrue(row(listOf(movie)).isEmpty())
        assertEquals(before, disk.entries)
        assertFalse(library.isTitleWatched(movie))
    }

    @Test fun watchedAndOldProgressSurviveReopenWithoutReappearingOrChangingFavorites() {
        val key = ProgressKeys.title(movie)
        seed(key)
        library.setFavorite(movie.id, true)
        library.setTitleWatched(movie, true)
        val originalProgress = disk.entries.toMap()
        val originalLibrary = libraryDisk.json
        val reopened = PersonalLibraryStore(libraryDisk)
        assertTrue(continueWatching(listOf(movie), PlaybackProgressStore(disk), reopened).isEmpty())
        assertEquals(originalProgress, disk.entries)
        assertEquals(originalLibrary, libraryDisk.json)
        assertTrue(reopened.isTitleWatched(movie))
        assertTrue(reopened.isFavorite(movie.id))
    }

    @Test fun automaticWatchedStateAlsoExcludesStaleMovieProgress() {
        val key = ProgressKeys.title(movie)
        seed(key)
        library.record(key, WatchedInterval(0, 3_500_000), 3_600_000)
        assertTrue(library.isWatched(key))
        assertTrue(row(listOf(movie)).isEmpty())
        assertNotNull(disk.entries[key])
    }

    @Test fun seriesAndAnimeExcludeOnlyCompletedOrWatchedEpisodesKeepPendingAndNeverInventUnstarted() {
        listOf("serie", "anime").forEach { type ->
            val title = series(type)
            seed(key(title, 0), updated = 5)
            seed(key(title, 1), position = 3_420_000, updated = 6)
            seed(key(title, 2), updated = 7)
            library.setWatched(key(title, 2), true)
            val before = disk.entries.toMap()
            val item = row(listOf(title)).single()
            assertEquals(key(title, 0), item.key)
            assertEquals(0, item.seasonIndex)
            assertEquals(0, item.episodeIndex)
            assertFalse(library.isTitleWatched(title))
            assertEquals(before, disk.entries)
            library.setWatched(key(title, 2), false)
            assertEquals(listOf(key(title, 2), key(title, 0)), row(listOf(title)).map { it.key })
        }
    }

    @Test fun manualParentSeriesMarkDoesNotMarkOrHideItsIndependentEpisodes() {
        val title = series()
        seed(key(title, 0))
        library.setTitleWatched(title, true)
        assertEquals(key(title, 0), row(listOf(title)).single().key)
        assertFalse(library.isWatched(key(title, 0)))
    }

    @Test fun watchedAnimePublicationCannotReenterThroughDirectTitleAlias() {
        val episode = Episode(id = "anime-publicacion-0", numero = 1,
            telegramUrl = "https://t.me/c/1/42")
        val title = Title(id = "anime", tipo = "anime", telegramUrl = episode.telegramUrl,
            temporadas = listOf(Season(numero = 0, episodios = listOf(episode))))
        val key = key(title, 0)
        seed(key)
        assertEquals(1, row(listOf(title, title)).size)
        library.setWatched(key, true)
        assertTrue(row(listOf(title, title)).isEmpty())
        library.setWatched(key, false)
        assertEquals(key, row(listOf(title, title)).single().key)
    }

    @Test fun moviePublicationsRespectExistingTitleWatchedSemanticsAndUnmark() {
        val title = movie.copy(temporadas = series().temporadas)
        seed(key(title, 0))
        seed(key(title, 1))
        library.setTitleWatched(title, true)
        assertTrue(row(listOf(title)).isEmpty())
        library.setTitleWatched(title, false)
        assertEquals(2, row(listOf(title)).size)
        library.setWatched(key(title, 0), true)
        assertTrue(library.isTitleWatched(title))
        assertTrue(row(listOf(title)).isEmpty())
    }

    @Test fun projectionRereadsProgressAfterPlaybackWithoutAnyCatalogRefresh() {
        val titles = listOf(movie)
        assertTrue(row(titles).isEmpty())
        progress.save(ProgressKeys.title(movie), 60_000, 3_600_000, 1)
        assertEquals(1, row(titles).size)
        progress.finish(ProgressKeys.title(movie), 3_600_000, 3_600_000, 2)
        assertTrue(row(titles).isEmpty())
    }

    @Test fun filteringRetainsRecentOrderAndDeduplicationAndIgnoresOrphans() {
        val other = movie.copy(id = "other")
        val hidden = movie.copy(id = "hidden")
        seed(ProgressKeys.title(movie), updated = 2)
        seed(ProgressKeys.title(other), updated = 3)
        seed(ProgressKeys.title(hidden), updated = 4)
        seed("title:orphan", updated = 5)
        library.setTitleWatched(hidden, true)
        assertEquals(listOf("other", "movie"), row(listOf(movie, hidden, other, movie)).map { it.title.id })
        assertTrue(row(emptyList()).isEmpty())
    }

    @Test fun pendingEpisodeMarkAndUnmarkPublishesLocalRevisionWithoutProgressWrites() {
        val title = series()
        seed(key(title, 0))
        val initialRevision = library.revision.value
        val before = disk.entries.toMap()
        library.setWatched(key(title, 0), true)
        assertTrue(library.revision.value > initialRevision)
        assertTrue(row(listOf(title)).isEmpty())
        val markedRevision = library.revision.value
        library.setWatched(key(title, 0), false)
        assertTrue(library.revision.value > markedRevision)
        assertEquals(key(title, 0), row(listOf(title)).single().key)
        assertEquals(before, disk.entries)
    }
}
