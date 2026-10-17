package ar.com.miflix.client

/** A fresh launch plays once; restoring an Activity must never restart an intro. */
internal class IntroSession(restoringActivity: Boolean) {
    var active: Boolean = !restoringActivity
        private set

    fun finish() { active = false }
}
