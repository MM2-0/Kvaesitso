package de.mm20.launcher2.icons

import android.content.Context
import android.graphics.drawable.Drawable

abstract class DynamicLauncherIcon(
    foreground: Drawable,
    background: Drawable?,
    foregroundScale: Float,
    backgroundScale: Float
)
    : LauncherIcon(
        foreground,
        background,
        foregroundScale,
        backgroundScale,
) {

    abstract fun update(context: Context)
}