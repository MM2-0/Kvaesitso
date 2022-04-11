package de.mm20.launcher2.icons

import android.content.Context
import android.graphics.drawable.Drawable

abstract class DynamicLauncherIcon(
    foreground: Drawable,
    background: Drawable?,
    foregroundScale: Float,
    backgroundScale: Float,
    isThemeable: Boolean = false
) : LauncherIcon(
    foreground,
    background,
    foregroundScale,
    backgroundScale,
    isThemeable = isThemeable
) {

    abstract fun update(context: Context)
}