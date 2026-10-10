package ar.com.miflix.client

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Diagnostic bookkeeping only: no timeouts, retries or TDLib state changes. */
internal class PendingPlaybackRequests {
    private val sequence = AtomicInteger()
    private val pending = ConcurrentHashMap<Int, Long>()
    fun begin(nowMs: Long): Int = sequence.incrementAndGet().also { pending[it] = nowMs }
    fun end(id: Int) { pending.remove(id) }
    fun count(): Int = pending.size
    fun oldestAgeMs(nowMs: Long): Long = pending.values.minOrNull()
        ?.let { (nowMs - it).coerceAtLeast(0L) } ?: 0L
}
