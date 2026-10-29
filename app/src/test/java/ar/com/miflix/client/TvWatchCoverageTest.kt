package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

class TvWatchCoverageTest {
    private class Disk(var value:String?=null):TvLibraryStorage {
        override fun read()=value
        override fun write(json:String){value=json}
    }
    private fun range(a:Long,b:Long)=WatchedInterval(a,b)
    @Test fun thresholdNeedsEightyPercentAndFinishZone() {
        assertFalse(sufficientlyWatched(listOf(range(0,80_000)),100_000))
        assertFalse(sufficientlyWatched(listOf(range(90_000,100_000)),100_000))
        assertTrue(sufficientlyWatched(listOf(range(15_000,95_000)),100_000))
        assertFalse(sufficientlyWatched(listOf(range(15_001,95_000)),100_000))
        assertFalse(sufficientlyWatched(emptyList(),0))
    }
    @Test fun repeatedIntervalsNeverAddNewCoverage() {
        val repeated=List(10){range(0,20_000)}
        assertEquals(listOf(range(0,20_000)),mergeWatchedIntervals(repeated,100_000))
        assertFalse(sufficientlyWatched(repeated+range(95_000,100_000),100_000))
    }
    @Test fun rangesMergeAcrossSessionsWithoutBridgingGaps() {
        val disk=Disk(); val first=TvPersonalLibraryStore(disk)
        first.record("title:a",range(0,40_000),100_000);first.flush()
        val second=TvPersonalLibraryStore(disk)
        second.record("title:a",range(40_000,95_000),100_000)
        assertTrue(TvPersonalLibraryStore(disk).isWatched("title:a"))
    }
    @Test fun seekToEndDoesNotCountMissingMiddle() {
        val store=TvPersonalLibraryStore(Disk())
        store.record("title:a",range(0,1_000),100_000)
        store.record("title:a",range(95_000,100_000),100_000)
        assertFalse(store.isWatched("title:a"))
    }
    @Test fun normalPlaybackProducesIntervalsButPauseAndBufferingDoNot() {
        val sampler=CoverageSampler();sampler.reset(0,0,true)
        assertEquals(range(0,500),sampler.sample(500,500,true))
        assertEquals(range(500,600),sampler.sample(600,600,false))
        assertNull(sampler.sample(600,1_600,false))
        assertNull(sampler.sample(600,2_000,true))
        assertEquals(range(600,1_100),sampler.sample(1_100,2_500,true))
    }
    @Test fun discontinuityResetDoesNotBridgeForwardOrBackwardSeek() {
        val sampler=CoverageSampler();sampler.reset(1_000,0,true)
        sampler.reset(80_000,100,true)
        assertEquals(range(80_000,80_500),sampler.sample(80_500,600,true))
        sampler.reset(5_000,700,true)
        assertEquals(range(5_000,5_500),sampler.sample(5_500,1_200,true))
    }
    @Test fun unsignaledJumpAndLongSuspensionAreRejected() {
        val sampler=CoverageSampler();sampler.reset(0,0,true)
        assertNull(sampler.sample(90_000,500,true))
        assertNull(sampler.sample(95_000,10_000,true))
    }
    @Test fun clampingAndUnionAreSafeForMalformedIntervals() {
        assertEquals(listOf(range(0,100)),mergeWatchedIntervals(listOf(range(-10,30),range(30,500),range(80,40)),100))
    }
    @Test fun fiveMinuteMarginAppliesToLongContent() {
        val duration=12_000_000L
        assertFalse(sufficientlyWatched(listOf(range(0,11_600_000)),duration))
        assertTrue(sufficientlyWatched(listOf(range(0,11_700_000)),duration))
    }
    @Test fun episodeCompletionNeverMarksWholeSeriesOrSibling() {
        val episode=Episode(id="e1",numero=1,telegramUrl="https://t.me/c/1/2")
        val sibling=episode.copy(id="e2",numero=2)
        val season=Season(numero=1,episodios=listOf(episode,sibling))
        val title=Title(id="series",tipo="serie",temporadas=listOf(season))
        val key=ProgressKeys.episode(title,season,episode)
        val store=TvPersonalLibraryStore(Disk())
        store.record(key,range(0,100_000),100_000)
        assertTrue(store.isWatched(key));assertFalse(store.isTitleWatched(title))
        assertTrue(store.hasWatchedEpisode(title))
        assertFalse(store.isWatched(ProgressKeys.episode(title,season,sibling)))
        store.setTitleWatched(title,true)
        assertTrue(store.isTitleWatched(title))
        assertFalse(store.isWatched(ProgressKeys.episode(title,season,sibling)))
    }
    @Test fun manualUnmarkClearsOldCoverageAndPersists() {
        val disk=Disk();val store=TvPersonalLibraryStore(disk)
        store.record("title:a",range(0,80_000),100_000)
        store.setWatched("title:a",true)
        assertTrue(TvPersonalLibraryStore(disk).isWatched("title:a"))
        store.setWatched("title:a",false)
        store.record("title:a",range(95_000,100_000),100_000);store.flush()
        assertFalse(TvPersonalLibraryStore(disk).isWatched("title:a"))
    }
    @Test fun legacyFavoriteEnvelopeAndMissingNewFieldsRemainReadable() {
        val disk=Disk("""{"schemaVersion":1,"favorites":["a"]}""")
        val store=TvPersonalLibraryStore(disk)
        assertTrue(store.isFavorite("a")); assertFalse(store.isWatched("title:a"))
        store.setWatched("title:a",true)
        assertTrue(TvPersonalLibraryStore(disk).isFavorite("a"))
    }
    @Test fun invalidCoverageDoesNotLoseFavoritesOrWatchedIds() {
        val store=TvPersonalLibraryStore(Disk("""{"schemaVersion":1,"favorites":["a"],"watched":["episode:b"],"coverage":{"bad":null}}"""))
        assertTrue(store.isFavorite("a"));assertTrue(store.isWatched("episode:b"))
        assertFalse(store.canWrite) // Preserve original corrupt coverage instead of rewriting it.
    }
    @Test fun futureStorageIsNotDestructivelyDowngraded() {
        val raw="""{"schemaVersion":2,"favorites":["a"],"future":true}"""
        val disk=Disk(raw);val store=TvPersonalLibraryStore(disk)
        store.setFavorite("b",true);store.setWatched("title:a",true)
        assertEquals(raw,disk.value)
    }
    @Test fun animePublicationIdentityUsesExistingStableKey() {
        val episode=Episode(id="anime-publicacion-123",telegramUrl="https://t.me/c/1/2")
        val season=Season(numero=1,episodios=listOf(episode))
        val title=Title(id="anime",tipo="anime",temporadas=listOf(season))
        val store=TvPersonalLibraryStore(Disk());val key=ProgressKeys.episode(title,season,episode)
        assertTrue(key.startsWith("publication:anime:"))
        store.setWatched(key,true); assertTrue(store.hasWatchedEpisode(title)); assertFalse(store.isTitleWatched(title))
    }
    @Test fun moviePublicationCountsForMovieButUnmarkClearsAliases() {
        val e=Episode(id="publication",telegramUrl="https://t.me/c/1/2")
        val season=Season(numero=1,episodios=listOf(e))
        val movie=Title(id="movie",tipo="pelicula",temporadas=listOf(season))
        val store=TvPersonalLibraryStore(Disk())
        store.record(ProgressKeys.episode(movie,season,e),range(0,100_000),100_000)
        assertTrue(store.isTitleWatched(movie))
        store.setTitleWatched(movie,false)
        assertFalse(store.isTitleWatched(movie)); assertFalse(store.hasWatchedEpisode(movie))
    }
    @Test fun watchingHistoryDoesNotEraseSavedPlaybackPosition() {
        val values=mutableMapOf<String,SavedProgress>()
        val progress=PlaybackProgressStore(object:ProgressStorage {
            override fun read(key:String)=values[key]
            override fun write(key:String,progress:SavedProgress){values[key]=progress}
            override fun remove(key:String){values.remove(key)}
        })
        progress.save("title:a",50_000,200_000,123)
        val library=TvPersonalLibraryStore(Disk())
        library.setWatched("title:a",true)
        assertEquals(50_000L,progress.resumablePosition("title:a"))
        library.setWatched("title:a",false)
        assertEquals(50_000L,progress.resumablePosition("title:a"))
    }

}
