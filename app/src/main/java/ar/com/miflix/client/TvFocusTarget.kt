package ar.com.miflix.client

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

internal val LocalTvFocusMemory = staticCompositionLocalOf<TvFocusMemory?> { null }

internal fun Modifier.tvFocusTarget(key: String, entry: Boolean = false): Modifier = composed {
    val memory = LocalTvFocusMemory.current
    if (memory == null) this else {
        val requester = remember(memory, key) { FocusRequester() }
        DisposableEffect(memory, key, entry) {
            memory.register(key, entry) { runCatching { requester.requestFocus(); true }.getOrDefault(false) }
            onDispose { memory.unregister(key) }
        }
        this.focusRequester(requester).onFocusChanged { if (it.isFocused) memory.focused(key) }
    }
}
