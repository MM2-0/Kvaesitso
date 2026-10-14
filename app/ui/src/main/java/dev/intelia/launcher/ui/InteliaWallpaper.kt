package dev.intelia.launcher.ui

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import java.util.function.Consumer

/** Uses the system wallpaper surface, including live wallpapers; never reads its image. */
@Composable
internal fun InteliaWallpaper(mode: Int, expanded: Boolean): Boolean {
    val activity = LocalContext.current as Activity
    var supported by remember { mutableStateOf(false) }
    val radius = with(LocalDensity.current) { (if (expanded) 28.dp else 18.dp).roundToPx() }
    DisposableEffect(activity) {
        if (Build.VERSION.SDK_INT >= 31) {
            val manager = activity.windowManager
            val listener = Consumer<Boolean> { supported = it }
            manager.addCrossWindowBlurEnabledListener(listener)
            onDispose { manager.removeCrossWindowBlurEnabledListener(listener) }
        } else onDispose { }
    }
    val enabled = mode == 0 && supported
    val activeRadius = if (enabled) radius else 0
    DisposableEffect(activity, activeRadius) {
        if (Build.VERSION.SDK_INT >= 31) {
            val attributes = activity.window.attributes
            val flags = if (activeRadius > 0) attributes.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                else attributes.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
            if (attributes.blurBehindRadius != activeRadius || attributes.flags != flags) {
                attributes.blurBehindRadius = activeRadius
                attributes.flags = flags
                activity.window.attributes = attributes
            }
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= 31 && activeRadius > 0) {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                activity.window.attributes = activity.window.attributes.apply { blurBehindRadius = 0 }
            }
        }
    }
    return enabled
}
