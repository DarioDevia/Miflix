package ar.com.miflix.client

internal data class WatchedInterval(val startMs: Long, val endMs: Long)

internal fun mergeWatchedIntervals(ranges: List<WatchedInterval>, durationMs: Long): List<WatchedInterval> {
    if (durationMs <= 0) return emptyList()
    val sorted = ranges.map { WatchedInterval(it.startMs.coerceIn(0, durationMs), it.endMs.coerceIn(0, durationMs)) }
        .filter { it.endMs > it.startMs }.sortedBy { it.startMs }
    val merged = mutableListOf<WatchedInterval>()
    sorted.forEach { next ->
        val last = merged.lastOrNull()
        if (last != null && next.startMs <= last.endMs)
            merged[merged.lastIndex] = last.copy(endMs = maxOf(last.endMs, next.endMs))
        else merged += next
    }
    return merged
}
internal fun sufficientlyWatched(ranges: List<WatchedInterval>, durationMs: Long): Boolean {
    if (durationMs <= 0) return false
    val merged = mergeWatchedIntervals(ranges, durationMs)
    val covered = merged.sumOf { it.endMs - it.startMs }
    return covered.toDouble() / durationMs >= 0.8 &&
        merged.any { ProgressRules.isComplete(it.endMs, durationMs) }
}

/** Conservative consumption measurement. Discontinuities reset the anchor, never bridge a seek. */
internal class CoverageSampler {
    private data class Sample(val position: Long, val elapsed: Long, val playing: Boolean, val speed: Float)
    private var previous: Sample? = null
    fun reset(position: Long, elapsedMs: Long, playing: Boolean, speed: Float = 1f) {
        previous = Sample(position, elapsedMs, playing, speed)
    }
    fun sample(position: Long, elapsedMs: Long, playing: Boolean, speed: Float = 1f): WatchedInterval? {
        val before = previous
        reset(position, elapsedMs, playing, speed)
        if (before == null || !before.playing || before.position < 0) return null
        val elapsed = elapsedMs - before.elapsed
        val delta = position - before.position
        // Long scheduling gaps/background are not evidence of continuous playback.
        if (elapsed !in 0..2_500 || delta <= 0 || !before.speed.isFinite() || before.speed <= 0) return null
        if (delta.toDouble() > elapsed * before.speed.toDouble() + 100.0) return null
        return WatchedInterval(before.position, position)
    }
}
