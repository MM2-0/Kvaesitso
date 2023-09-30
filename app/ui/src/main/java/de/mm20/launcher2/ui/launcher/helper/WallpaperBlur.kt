package de.mm20.launcher2.ui.launcher.helper

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ktx.isAtLeastApiLevel

@Composable
fun WallpaperBlur(blurRadius: () -> Int) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val radius = blurRadius()
    LaunchedEffect(radius) {
        if (!isAtLeastApiLevel(31)) return@LaunchedEffect
        (context as Activity).window.attributes = context.window.attributes.also {
            if (radius > 0) {
                it.blurBehindRadius = with(density) { radius.dp.toPx().toInt() }
                it.flags = it.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            } else {
                it.blurBehindRadius = 0
                it.flags = it.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
            }
        }
    }
}