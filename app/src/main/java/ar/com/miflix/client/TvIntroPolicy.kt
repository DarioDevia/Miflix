package ar.com.miflix.client

internal const val TV_INTRO_ASSET = "miflix_tv_intro.mp4"
internal const val TV_INTRO_TIMEOUT_MS = 10_000L

/** One fresh launch per live process. Restoring a task never interrupts its navigation. */
internal class TvIntroSession {
    private var consumed = false
    @Synchronized fun claim(restoringActivity: Boolean): Boolean {
        if (consumed) return false
        consumed = true
        return !restoringActivity
    }
}
internal object TvIntroProcess { val session = TvIntroSession() }

/** Complete on key UP: neither half of a skip press reaches Home. */
internal fun tvIntroSkipKey(code: Int): Boolean = code in setOf(4, 23, 66, 160)

/** Idempotent lifetime: decoder/audio are released before the app becomes visible. */
internal class TvIntroLifetime(private val release: () -> Unit, private val onFinished: () -> Unit) {
    var closed = false
        private set
    fun finish() {
        if (closed) return
        closed = true
        try { release() } finally { onFinished() }
    }
    fun dispose() {
        if (closed) return
        closed = true
        release()
    }
}
