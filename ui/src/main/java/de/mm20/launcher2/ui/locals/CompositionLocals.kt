package de.mm20.launcher2.ui.locals

import android.appwidget.AppWidgetHost
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Size
import de.mm20.launcher2.ui.theme.WallpaperColors
import de.mm20.launcher2.ui.theme.colors.ColorScheme
import de.mm20.launcher2.ui.theme.colors.DefaultColorScheme

val LocalWindowSize = compositionLocalOf { Size(0f, 0f) }

val LocalAppWidgetHost = compositionLocalOf<AppWidgetHost?>(defaultFactory = { null })

val LocalWallpaperColors = compositionLocalOf<WallpaperColors?> { null }

val LocalColorScheme = compositionLocalOf<ColorScheme> { DefaultColorScheme() }