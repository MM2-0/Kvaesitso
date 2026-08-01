package de.mm20.launcher2.ui.launcher.helper

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import de.mm20.launcher2.ktx.isAtLeastApiLevel

@Composable
fun WallpaperBlur(blurRadius: () -> Int) {
    if (!isAtLeastApiLevel(31)) return
    val context = LocalContext.current
    val density = LocalDensity.current

    val radius = blurRadius()
    LaunchedEffect(radius) {
        if (radius > 0) {
            val windowAttributes = (context as Activity).window.attributes
            windowAttributes.flags =
                windowAttributes.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            context.window.attributes = windowAttributes
            context.window.setBackgroundBlurRadius(radius)
        } else {
            val windowAttributes = (context as Activity).window.attributes
            windowAttributes.flags =
                windowAttributes.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
            context.window.attributes = windowAttributes
            context.window.setBackgroundBlurRadius(0)
        }

    }
}