package ar.com.miflix.admin

import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One writer for drafts and repository commands. Never cancel an in-flight disk write. */
internal class DraftPersistence(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val save: (JsonObject?, Set<String>) -> Unit,
    private val onError: (Exception) -> Unit,
    private val quietMillis: Long = 500
) {
    private data class Edit(val value: JsonObject?, val paths: Set<String>)
    private val monitor = Any()
    private val writer = Mutex()
    private val pending = java.util.ArrayDeque<Edit>()
    private var timer: Job? = null

    // Callers supply an owned immutable snapshot; no catalog is copied here.
    fun offer(value: JsonObject?, paths: Set<String> = emptySet()) {
        synchronized(monitor) {
            val last=pending.peekLast()
            if (last != null && last.value == value && last.paths == paths) return
            // Provider receipts belong to the exact verified value, never a later manual edit.
            if (last != null && last.paths.isEmpty() && paths.isEmpty()) pending.removeLast()
            pending.addLast(Edit(value,paths.toSet()))
            timer?.cancel()
            timer = scope.launch {
                delay(quietMillis)
                flushSoon()
            }
        }
    }
    private fun takePending(): Edit? = synchronized(monitor) { pending.peekFirst() }
    private fun flushLocked() {
        while (true) {
            val edit = takePending() ?: return
            save(edit.value, edit.paths)
            synchronized(monitor) { if (pending.peekFirst() === edit) pending.removeFirst() }
        }
    }
    suspend fun <T> command(task: () -> T): T = withContext(dispatcher) {
        writer.withLock { flushLocked(); task() }
    }
    private suspend fun flushSafely() {
        try { command {} }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { onError(e) }
    }
    fun finish() {
        synchronized(monitor) {timer?.cancel()}
        scope.launch(dispatcher) {
            try {flushSafely()} finally {scope.cancel()}
        }
    }
    fun flushSoon() {
        // A timer can be cancelled; the independent flush job cannot be cancelled by typing.
        scope.launch { flushSafely() }
    }
}
