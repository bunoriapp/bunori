package com.halovoid.bunori.ui.core.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Shared composable helper for toggling system bar visibility (e.g. for full-screen Reader).
 * Restores system bars automatically when leaving the screen.
 */
@Composable
fun SystemBarHandler(
    isSystemBarsVisible: Boolean
) {
    val context = LocalContext.current

    LaunchedEffect(isSystemBarsVisible) {
        val activity = context.findActivity()
        val window = activity?.window
        if (window != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            android.util.Log.d("BunoriReader", "SystemBarHandler: isSystemBarsVisible=$isSystemBarsVisible -> applying insets controller")
            if (isSystemBarsVisible) {
                controller.show(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
        } else {
            android.util.Log.w("BunoriReader", "SystemBarHandler: window is null, could not apply system bar visibility")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val activity = context.findActivity()
            val window = activity?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
