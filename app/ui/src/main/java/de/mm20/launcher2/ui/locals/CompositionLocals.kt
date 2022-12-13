package de.mm20.launcher2.ui.locals

import android.appwidget.AppWidgetHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Size
import androidx.navigation.NavController
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.preferences.Settings.GridSettings
import de.mm20.launcher2.ui.theme.WallpaperColors

val LocalWindowSize = compositionLocalOf { Size(0f, 0f) }

val LocalAppWidgetHost = compositionLocalOf<AppWidgetHost?>(defaultFactory = { null })

val LocalNavController = compositionLocalOf<NavController?> { null }

val LocalCardStyle = compositionLocalOf<Settings.CardSettings> { Settings.CardSettings.getDefaultInstance() }

val LocalFavoritesEnabled = compositionLocalOf { true }

val LocalGridSettings = compositionLocalOf { GridSettings.newBuilder().setColumnCount(5).setShowLabels(true).setIconSize(48).build() }

val LocalSnackbarHostState = compositionLocalOf { SnackbarHostState() }

val LocalDarkTheme = compositionLocalOf { false }

/**
 * Workaround a bug in Jetpack Compose which incorrectly places popups
 * that are nested inside other popups.
 */
val LocalWindowPosition = compositionLocalOf { 0f }

val LocalWallpaperColors = compositionLocalOf { WallpaperColors() }

val LocalPreferDarkContentOverWallpaper = compositionLocalOf { false }