package ar.com.miflix.client

/** TV persistence adapter: an unavailable duration must not erase a known timeline. */
internal class TvProgressStorage(private val delegate: ProgressStorage) : ProgressStorage {
    private val knownDurations = mutableMapOf<String, Long>()

    override fun read(key: String): SavedProgress? = delegate.read(key)?.also {
        if (it.durationMs > 0L) knownDurations[key] = it.durationMs
    }

    override fun write(key: String, progress: SavedProgress) {
        val duration = progress.durationMs.takeIf { it > 0L }
            ?: read(key)?.durationMs?.takeIf { it > 0L }
            ?: knownDurations[key] ?: 0L
        if (duration > 0L) knownDurations[key] = duration
        delegate.write(key, progress.copy(durationMs = duration))
    }

    // Completion/restart still uses the existing clear policy. Keep duration in this
    // session so a late checkpoint with unknown duration cannot resurrect a finished item.
    override fun remove(key: String) { delegate.remove(key) }
}
