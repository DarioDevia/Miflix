package ar.com.miflix.client

import android.graphics.Color
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.core.view.WindowCompat

/** Normal screens own edge-to-edge; the existing Player and separate intro window never do. */
internal fun normalEdgeToEdgeActive(screen: Screen, fullscreenActive: Boolean, introActive: Boolean): Boolean =
    screen != Screen.PLAYER && !fullscreenActive && !introActive

@Composable
internal fun NormalEdgeToEdge(activity: ComponentActivity?, enabled: Boolean) {
    DisposableEffect(activity, enabled) {
        if (activity == null || !enabled) onDispose { }
        else {
            val window = activity.window
            val bars = WindowCompat.getInsetsController(window, window.decorView)
            val statusColor = window.statusBarColor
            val navigationColor = window.navigationBarColor
            val statusDarkIcons = bars.isAppearanceLightStatusBars
            val navigationDarkIcons = bars.isAppearanceLightNavigationBars
            val flags = window.attributes.flags
            val cutoutMode = if (Build.VERSION.SDK_INT >= 28) window.attributes.layoutInDisplayCutoutMode else 0
            val dividerColor = if (Build.VERSION.SDK_INT >= 28) window.navigationBarDividerColor else 0
            val statusContrast = if (Build.VERSION.SDK_INT >= 29) window.isStatusBarContrastEnforced else false
            val navigationContrast = if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced else false

            // Activity 1.8.2 already includes this official compatibility API (since 1.8.0).
            // Keep the existing navigation-bar color; only the status bar needs transparency.
            activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.dark(navigationColor)
            )

            onDispose {
                // Restore the pre-existing fitted-window baseline before Player takes ownership.
                // All old effect disposals run before newly entering fullscreen effects.
                WindowCompat.setDecorFitsSystemWindows(window, true)
                window.statusBarColor = statusColor
                window.navigationBarColor = navigationColor
                bars.isAppearanceLightStatusBars = statusDarkIcons
                bars.isAppearanceLightNavigationBars = navigationDarkIcons
                val managedFlags = WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
                window.setFlags(flags, managedFlags) // Never restore unrelated flags such as KEEP_SCREEN_ON.
                if (Build.VERSION.SDK_INT >= 28) {
                    window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = cutoutMode }
                    window.navigationBarDividerColor = dividerColor
                }
                if (Build.VERSION.SDK_INT >= 29) {
                    window.isStatusBarContrastEnforced = statusContrast
                    window.isNavigationBarContrastEnforced = navigationContrast
                }
            }
        }
    }
}
