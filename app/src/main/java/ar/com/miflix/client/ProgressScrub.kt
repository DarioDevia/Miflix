package ar.com.miflix.client

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Provisional Slider value; only finish returns a target for the existing seek. */
internal class ProgressScrub {
    var fraction: Float? by mutableStateOf(null)
        private set

    fun preview(value: Float) {
        fraction = controlFraction(value)
    }

    fun finish(duration: Long): Long? {
        val pending = fraction ?: return null
        fraction = null
        return scrubPosition(pending, duration)
    }
}
