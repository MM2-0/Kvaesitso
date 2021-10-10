package de.mm20.launcher2.icons

import android.content.Context
import android.graphics.drawable.Drawable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

abstract class DynamicLauncherIcon(
        foreground: Drawable,
        background: Drawable?,
        foregroundScale: Float,
        backgroundScale: Float,
        autoGenerateBackgroundMode: Int,
        badgeNumber: Float,
        badgeDrawable: Drawable?)
    : LauncherIcon(
        foreground,
        background,
        foregroundScale,
        backgroundScale,
        autoGenerateBackgroundMode
) {

    abstract fun update(context: Context)
}