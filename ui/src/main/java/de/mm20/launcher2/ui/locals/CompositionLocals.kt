package de.mm20.launcher2.ui.locals

import android.appwidget.AppWidgetHost
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Size
import androidx.navigation.NavController
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.ui.theme.WallpaperColors
import de.mm20.launcher2.ui.theme.colors.ColorPalette
import de.mm20.launcher2.ui.theme.colors.DefaultColorPalette

val LocalWindowSize = compositionLocalOf { Size(0f, 0f) }

val LocalAppWidgetHost = compositionLocalOf<AppWidgetHost?>(defaultFactory = { null })

val LocalWallpaperColors = compositionLocalOf<WallpaperColors?> { null }

val LocalColorScheme = compositionLocalOf<ColorPalette> { DefaultColorPalette() }

val LocalNavController = compositionLocalOf<NavController?> { null }

val LocalCardStyle = compositionLocalOf<Settings.CardSettings> { Settings.CardSettings.getDefaultInstance() }