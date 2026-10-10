package ar.com.miflix.client

import org.junit.Assert.*
import org.junit.Test

class AppUpdatesTest {
    private class Storage(var seen: Int = 0) : UpdateNoticeStorage {
        var writes = 0
        override fun read() = seen
        override fun write(versionCode: Int) { seen = versionCode; writes++ }
    }

    @Test fun aboutUsesInstalledBuildVersionAndCode() {
        val version = AppUpdates.installedVersion()
        assertEquals(BuildConfig.VERSION_NAME, version.name)
        assertEquals(BuildConfig.VERSION_CODE, version.code)
        assertEquals("${BuildConfig.VERSION_NAME} · versionCode ${BuildConfig.VERSION_CODE}", version.display)
    }
    @Test fun displayDoesNotHardcodeARelease() {
        assertEquals("future-build · versionCode 123", InstalledVersion("future-build", 123).display)
    }
    @Test fun bundledHistoryIncludesCurrentBuildAndNonEmptyDocumentedChanges() {
        assertEquals(BuildConfig.VERSION_CODE, AppUpdates.history.first().code)
        assertEquals(BuildConfig.VERSION_NAME, AppUpdates.history.first().name)
        assertTrue(AppUpdates.history.all { it.name.isNotBlank() && it.changes.all(String::isNotBlank) })
        assertTrue(AppUpdates.history.all { it.changes.isNotEmpty() })
        assertEquals(AppUpdates.history.size, AppUpdates.history.map { it.code }.distinct().size)
        assertEquals(AppUpdates.history.map { it.code }.sortedDescending(), AppUpdates.history.map { it.code })
        assertEquals("DarioDevia", AppUpdates.DEVELOPER)
    }
    @Test fun newInstallPresentsOnlyOnceAtHome() {
        val storage = Storage()
        val notice = UpdateNotice(storage)
        assertTrue(notice.presentIfEligible(29, true, true))
        assertFalse(notice.presentIfEligible(29, true, true))
        assertEquals(1, storage.writes)
    }
    @Test fun restartingWithStoredVersionDoesNotRepeatNotice() {
        val storage = Storage(28)
        assertTrue(UpdateNotice(storage).presentIfEligible(29, true, true))
        assertFalse(UpdateNotice(storage).presentIfEligible(29, true, true))
    }
    @Test fun nextVersionIsPresentedAndDowngradeIsNot() {
        val storage = Storage(28)
        val notice = UpdateNotice(storage)
        assertTrue(notice.presentIfEligible(29, true, true))
        assertFalse(notice.presentIfEligible(28, true, true))
        assertTrue(notice.presentIfEligible(30, true, true))
        assertEquals(2, storage.writes)
    }
    @Test fun playbackOtherScreensDoNotConsumeNotice() {
        val storage = Storage(28)
        assertFalse(UpdateNotice(storage).presentIfEligible(29, false, true))
        assertEquals(28, storage.seen)
        assertEquals(0, storage.writes)
        assertTrue(UpdateNotice(storage).presentIfEligible(29, true, true))
    }
    @Test fun backgroundWaitsForResumeWithoutRepeatingAfterReturn() {
        val storage = Storage(28)
        val notice = UpdateNotice(storage)
        assertFalse(notice.presentIfEligible(29, true, false))
        assertEquals(28, storage.seen)
        assertTrue(notice.presentIfEligible(29, true, true))
        assertFalse(notice.presentIfEligible(29, true, false))
        assertFalse(notice.presentIfEligible(29, true, true))
    }
}
