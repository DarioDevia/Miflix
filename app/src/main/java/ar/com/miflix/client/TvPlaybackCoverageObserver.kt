package ar.com.miflix.client

import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.Player
import kotlinx.coroutines.delay

/** Read-only observer adapted from Mobile; never controls or releases the player. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class TvPlaybackCoverageObserver(private val player: Player, private val key: String,
    private val store: TvPersonalLibraryStore, private val clock: () -> Long = SystemClock::elapsedRealtime) : Player.Listener {
    private val sampler = CoverageSampler()
    private var closed = false
    private var lastFlush = clock()
    private var generation = store.generation(key)
    init { sampler.reset(player.currentPosition, lastFlush, player.isPlaying, player.playbackParameters.speed) }
    private fun record(position: Long, playing: Boolean) {
        if (closed) return
        val now = clock()
        if (generation != store.generation(key)) {
            generation = store.generation(key)
            sampler.reset(position, now, playing, player.playbackParameters.speed)
        } else sampler.sample(position, now, playing, player.playbackParameters.speed)?.let {
            store.record(key, it, player.duration)
        }
        if (now - lastFlush >= 15_000L) { store.flush(); lastFlush = now }
    }
    fun sample() { if (!closed) record(player.currentPosition, player.isPlaying) }
    override fun onIsPlayingChanged(isPlaying: Boolean) { if (!closed) record(player.currentPosition, isPlaying) }
    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        if (closed) return
        record(oldPosition.positionMs, player.isPlaying)
        sampler.reset(newPosition.positionMs, clock(), player.isPlaying, player.playbackParameters.speed)
    }
    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) { sample(); store.flush() }
    }
    fun finish() {
        if (closed) return
        sample(); closed = true; store.flush()
    }
}

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun TvObserveLibraryCoverage(player: Player, key: String) {
    val store = LocalTvLibrary.current ?: return
    val owner = LocalLifecycleOwner.current
    val observer = remember(player, key, store) { TvPlaybackCoverageObserver(player, key, store) }
    DisposableEffect(player, observer, owner) {
        player.addListener(observer)
        val lifecycle = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { observer.finish(); player.removeListener(observer) }
        }
        owner.lifecycle.addObserver(lifecycle)
        onDispose { observer.finish(); player.removeListener(observer); owner.lifecycle.removeObserver(lifecycle) }
    }
    LaunchedEffect(observer) { while (true) { observer.sample(); delay(500L) } }
}
