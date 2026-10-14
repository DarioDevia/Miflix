package ar.com.miflix.client

internal const val TV_CONTROLS_TIMEOUT_MS = 4_000L
internal fun tvControlsAutoHide(visible: Boolean, playing: Boolean, blocked: Boolean) =
    visible && playing && !blocked
internal fun tvPlaybackRemoteAction(code: Int, visible: Boolean): TvRemoteAction? =
    if (visible && code == 21) TvRemoteAction.REWIND
    else if (visible && code == 22) TvRemoteAction.FORWARD
    else tvRemoteAction(code, visible)

internal const val TV_PLAYER_ICON_SIZE_DP = 48
internal const val TV_PLAYER_CONTROL_GAP_DP = 2

internal enum class TvPlayerFocusTarget { PLAY, TRACKS, PROGRESS }

/** Preserve Play ↓ Tracks / Tracks ↑ Play; extend the route to the progress bar. */
internal fun tvPlayerFocusDestination(current: TvPlayerFocusTarget, down: Boolean,
    hasTracks: Boolean, canSeek: Boolean = true): TvPlayerFocusTarget {
    val order = buildList {
        add(TvPlayerFocusTarget.PLAY)
        if (hasTracks) add(TvPlayerFocusTarget.TRACKS)
        if (canSeek) add(TvPlayerFocusTarget.PROGRESS)
    }
    val index = order.indexOf(current).coerceAtLeast(0)
    return order[(index + if (down) 1 else -1).coerceIn(order.indices)]
}
