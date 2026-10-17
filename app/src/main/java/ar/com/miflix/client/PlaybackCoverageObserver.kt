package ar.com.miflix.client

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.media3.common.Player

/** Read-only listener; cannot seek, prepare, release, change tracks or change playWhenReady. */
internal class PlaybackCoverageObserver(private val player: Player, private val key: String,
    private val store: PersonalLibraryStore) : Player.Listener {
    private val sampler = CoverageSampler()
    private var closed = false
    private var lastFlush = SystemClock.elapsedRealtime()
    init { sampler.reset(player.currentPosition, lastFlush, player.isPlaying, player.playbackParameters.speed) }
    private fun record(position: Long, playing: Boolean) {
        if (closed) return
        val now = SystemClock.elapsedRealtime()
        sampler.sample(position, now, playing, player.playbackParameters.speed)?.let {
            store.record(key, it, player.duration)
        }
        if (now - lastFlush >= 15_000) { store.flush(); lastFlush = now }
    }
    fun sample() { if (!closed) record(player.currentPosition, player.isPlaying) }
    override fun onIsPlayingChanged(isPlaying: Boolean) { if (!closed) record(player.currentPosition, isPlaying) }
    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        if (closed) return
        record(oldPosition.positionMs, player.isPlaying)
        sampler.reset(newPosition.positionMs, SystemClock.elapsedRealtime(), player.isPlaying, player.playbackParameters.speed)
    }
    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) { sample(); store.flush() }
    }
    fun finish() {
        if (closed) return
        sample()
        store.flush()
        closed = true
    }
}

@Composable
internal fun rememberPlaybackCoverage(player: Player, key: String, store: PersonalLibraryStore?): PlaybackCoverageObserver? {
    val observer = remember(player, key, store) { store?.let { PlaybackCoverageObserver(player, key, it) } }
    DisposableEffect(player, observer) {
        observer?.let(player::addListener)
        onDispose {
            observer?.finish()
            observer?.let(player::removeListener)
        }
    }
    return observer
}
