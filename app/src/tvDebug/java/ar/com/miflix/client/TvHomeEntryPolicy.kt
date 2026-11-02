package ar.com.miflix.client

internal const val HOME_DETAIL_ACTION = "ar.com.miflix.client.tv.HOME_DETAIL"
internal const val HOME_DETAIL_EXTRA = "miflix_home_title_id"
internal const val HOME_DETAIL_WAIT_MS = 15_000L

/** Opaque catalog ID only: no URL, path, command, playback options or display-title matching. */
internal fun homeEntryId(action: String?, value: Any?, hasData: Boolean = false): String? =
    (value as? String)?.takeIf {
        action == HOME_DETAIL_ACTION && !hasData &&
            Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,255}").matches(it)
    }

internal enum class HomeEntryStatus { NONE, WAIT_CATALOG, WAIT_AUTH, MISSING, DETAIL }
internal data class HomeEntryResult(val status: HomeEntryStatus, val title: Title? = null)
internal fun homeEntryResolve(id: String?, titles: List<Title>, catalogReady: Boolean,
    authorized: Boolean): HomeEntryResult = when {
    id == null -> HomeEntryResult(HomeEntryStatus.NONE)
    !catalogReady -> HomeEntryResult(HomeEntryStatus.WAIT_CATALOG)
    !authorized -> HomeEntryResult(HomeEntryStatus.WAIT_AUTH)
    else -> {
        val matches = titles.filter { it.id == id && it.tipo in setOf("pelicula", "serie", "anime") }
        if(matches.size == 1) HomeEntryResult(HomeEntryStatus.DETAIL, matches.single())
        else HomeEntryResult(HomeEntryStatus.MISSING)
    }
}

/** Reducer shared by the Android adapter and JVM tests; a stale completion cannot consume a newer tap. */
internal data class HomeEntryInbox(val id: String? = null, val serial: Long = 0) {
    fun submit(action: String?, value: Any?, hasData: Boolean = false): HomeEntryInbox {
        val next = homeEntryId(action, value, hasData) ?: return this
        return HomeEntryInbox(next, serial + 1)
    }
    fun consume(completedSerial: Long): HomeEntryInbox =
        if(completedSerial == serial) copy(id = null) else this
}
