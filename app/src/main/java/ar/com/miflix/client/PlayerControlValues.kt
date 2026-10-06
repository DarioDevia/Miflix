package ar.com.miflix.client

import kotlin.math.roundToInt

internal fun controlFraction(value: Float): Float =
    if (value.isFinite()) value.coerceIn(0f, 1f) else 0f

internal fun verticalControlFraction(y: Float, height: Float, inset: Float): Float {
    val length = height - 2 * inset
    return if (length > 0f) controlFraction(1f - (y - inset) / length) else 0f
}

internal fun scrubPosition(fraction: Float, duration: Long): Long? =
    if (duration > 0) (controlFraction(fraction).toDouble() * duration)
        .toLong().coerceIn(0L, duration) else null

internal fun volumeIndex(fraction: Float, minimum: Int, maximum: Int): Int =
    minimum + (controlFraction(fraction) * (maximum - minimum).coerceAtLeast(0)).roundToInt()

internal fun volumeFraction(index: Int, minimum: Int, maximum: Int): Float =
    if (maximum > minimum) controlFraction((index - minimum).toFloat() / (maximum - minimum))
    else 0f

internal fun canAutoHideControls(visible: Boolean, playing: Boolean, panelOpen: Boolean,
    interacting: Boolean): Boolean = visible && playing && !panelOpen && !interacting

/** Writes only the supplied window override. Construction never changes brightness. */
internal class TemporaryPlayerBrightness(
    private val previous: Float,
    private val write: (Float) -> Unit
) {
    private var changed = false

    fun set(fraction: Float) {
        write(controlFraction(fraction))
        changed = true
    }

    fun restore() {
        if (!changed) return
        write(previous) // Preserve -1 (inherit system), rather than replacing it with a guessed value.
        changed = false
    }
}
