package ar.com.miflix.client

/** Screen-local mounted targets. Removed lazy items must never retain a requester. */
internal class TvFocusMemory {
    private val targets = linkedMapOf<String, () -> Boolean>()
    private val entries = linkedSetOf<String>()
    var lastKey: String? = null
        private set

    fun register(key: String, entry: Boolean, request: () -> Boolean) {
        targets[key] = request
        if (entry) entries.add(key)
    }
    fun unregister(key: String) { targets.remove(key); entries.remove(key) }
    fun focused(key: String) { if (key in targets) lastKey = key }
    fun restore(): Boolean {
        val candidates = (listOfNotNull(lastKey) + entries + targets.keys).distinct()
        return candidates.any { targets[it]?.invoke() == true }
    }
}
