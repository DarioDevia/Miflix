package ar.com.miflix.client

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay

internal class PlayerLevels(
    val brightness: Float?,
    val volume: Float,
    val brightnessEnabled: Boolean,
    val volumeEnabled: Boolean,
    val setBrightness: (Float) -> Unit,
    val setVolume: (Float) -> Unit
)

private fun Context.playerActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.takeIf { it !== this }?.playerActivity()
    else -> null
}

@Composable
internal fun rememberPlayerLevels(controlsVisible: Boolean): PlayerLevels {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val window = context.playerActivity()?.window
    val audio = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val minimum = if (Build.VERSION.SDK_INT >= 28) audio.getStreamMinVolume(AudioManager.STREAM_MUSIC) else 0
    val maximum = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val brightnessOverride = remember(window) {
        window?.let { target ->
            TemporaryPlayerBrightness(target.attributes.screenBrightness) { value ->
                target.attributes = target.attributes.apply { screenBrightness = value }
            }
        }
    }
    var brightness by remember(window) {
        mutableStateOf(window?.attributes?.screenBrightness?.takeIf { it in 0f..1f }
            ?: runCatching {
                // A coherent initial estimate, not the actual adaptive panel luminance.
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                    .coerceIn(0, 255) / 255f
            }.getOrNull())
    }
    var volume by remember(audio) {
        mutableFloatStateOf(volumeFraction(audio.getStreamVolume(AudioManager.STREAM_MUSIC), minimum, maximum))
    }
    var foreground by remember(owner) {
        mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(window, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                foreground = false
                brightnessOverride?.restore()
            } else if (event == Lifecycle.Event.ON_START) foreground = true
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            brightnessOverride?.restore()
        }
    }
    // Observe physical keys only while visible; no receiver, background polling or Player changes.
    LaunchedEffect(audio, controlsVisible, foreground, minimum, maximum) {
        while (controlsVisible && foreground) {
            volume = volumeFraction(audio.getStreamVolume(AudioManager.STREAM_MUSIC), minimum, maximum)
            delay(500)
        }
    }
    return PlayerLevels(brightness, volume, window != null && foreground,
        foreground && !audio.isVolumeFixed && maximum > minimum,
        setBrightness = { value ->
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                brightnessOverride?.set(value)
                brightness = controlFraction(value)
            }
        },
        setVolume = { value ->
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                try {
                    val requested = volumeIndex(value, minimum, maximum)
                    if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) != requested)
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, requested, 0)
                } catch (_: SecurityException) {
                    // Respect system policies (e.g. DND); never request special permission.
                    Log.d("MiFlixPlayback", "MEDIA_VOLUME_RESTRICTED_BY_SYSTEM")
                }
                volume = volumeFraction(audio.getStreamVolume(AudioManager.STREAM_MUSIC), minimum, maximum)
            }
        })
}

/** Observe down/up/cancel without consuming: the existing Material Slider owns its drag/tap. */
internal fun Modifier.observeProgressTouch(onInteraction: (Boolean) -> Unit,
    onCancel: () -> Unit): Modifier = pointerInput(onInteraction, onCancel) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            onInteraction(true)
            var finished = false
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                } while (event.changes.any { it.pressed })
                finished = true
            } finally {
                onInteraction(false)
                if (!finished) onCancel()
            }
        }
    }

@Composable
internal fun VerticalPlayerControl(
    value: Float?,
    enabled: Boolean,
    icon: ImageVector,
    description: String,
    trackHeight: Dp,
    modifier: Modifier = Modifier,
    onInteraction: (Boolean) -> Unit,
    onValueChange: (Float) -> Unit
) {
    val change by rememberUpdatedState(onValueChange)
    val interaction by rememberUpdatedState(onInteraction)
    val compact = trackHeight <= 48.dp
    val iconSpace = if (compact) 0.dp else 28.dp
    // Compact portrait uses the icon beside the rail, leaving room for top/bottom controls.
    Box(modifier.width(48.dp).height(trackHeight + iconSpace)
        .background(Color.Black.copy(alpha = .45f), RoundedCornerShape(24.dp))
            .semantics {
                contentDescription = description // Accessibility only; no visible labels/numbers.
                if (value != null) progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                setProgress { requested ->
                    if (enabled) change(controlFraction(requested))
                    enabled
                }
            }
            .pointerInput(enabled, compact, trackHeight) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    interaction(true)
                    try {
                        val top = iconSpace.toPx()
                        change(verticalControlFraction(down.position.y - top, size.height - top, 8.dp.toPx()))
                        while (true) {
                            val event = awaitPointerEvent()
                            val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                            event.changes.forEach { it.consume() }
                            change(verticalControlFraction(pointer.position.y - top, size.height - top, 8.dp.toPx()))
                            if (!pointer.pressed) break
                        }
                    } finally {
                        interaction(false)
                    }
                }
            }) {
        Icon(icon, contentDescription = null, tint = Color.White,
            modifier = Modifier.align(if (compact) Alignment.CenterStart else Alignment.TopCenter)
                .padding(start = if (compact) 2.dp else 0.dp, top = if (compact) 0.dp else 4.dp)
                .size(if (compact) 20.dp else 24.dp))
        Canvas(Modifier.align(if (compact) Alignment.CenterEnd else Alignment.BottomCenter)
            .width(if (compact) 24.dp else 48.dp).height(trackHeight)) {
            val inset = 8.dp.toPx()
            val x = size.width / 2f
            val bottom = size.height - inset
            drawLine(Color.White.copy(alpha = if (enabled) .4f else .2f),
                Offset(x, inset), Offset(x, bottom), 4.dp.toPx(), StrokeCap.Round)
            // If Android cannot provide a brightness estimate, don't fabricate a thumb position.
            value?.let {
                val y = bottom - controlFraction(it) * (bottom - inset)
                drawLine(Color.White, Offset(x, y), Offset(x, bottom), 4.dp.toPx(), StrokeCap.Round)
                drawCircle(Color.White, 7.dp.toPx(), Offset(x, y))
            }
        }
    }
}
