package de.mm20.launcher2.ui.launcher.helper

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ktx.isAtLeastApiLevel

@Composable
fun WallpaperBlur(blurRadius: () -> Int) {
    if (!isAtLeastApiLevel(31)) return
    val context = LocalContext.current
    val density = LocalDensity.current

    val radius = blurRadius()
    val animatable = remember { Animatable(radius, Int.VectorConverter) }
    LaunchedEffect(radius) {
        animatable.animateTo(with(density) { radius.dp.toPx().toInt() }) {
            if (value > 0) {
                val windowAttributes = (context as Activity).window.attributes
                windowAttributes.flags =
                    windowAttributes.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                context.window.attributes = windowAttributes
                context.window.setBackgroundBlurRadius(value)
            } else {
                val windowAttributes = (context as Activity).window.attributes
                windowAttributes.flags =
                    windowAttributes.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
                context.window.attributes = windowAttributes
                context.window.setBackgroundBlurRadius(0)
            }
        }

    }
}