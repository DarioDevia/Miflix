package ar.com.miflix.client

internal enum class TvRemoteAction { PLAY_PAUSE, PLAY, PAUSE, REWIND, FORWARD, SHOW_CONTROLS }

/** Android KeyEvent codes; pure mapping, including both common select buttons. */
internal fun tvRemoteAction(keyCode: Int, controlsVisible: Boolean): TvRemoteAction? = when (keyCode) {
    85 -> TvRemoteAction.PLAY_PAUSE
    126 -> TvRemoteAction.PLAY
    127 -> TvRemoteAction.PAUSE
    89 -> TvRemoteAction.REWIND
    90 -> TvRemoteAction.FORWARD
    19, 20, 21, 22, 23, 66 -> if (!controlsVisible) TvRemoteAction.SHOW_CONTROLS else null
    else -> null
}
