package de.mm20.launcher2.ui.locals

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Size
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.preferences.MeasurementSystem
import de.mm20.launcher2.preferences.TimeFormat
import de.mm20.launcher2.preferences.ui.CardStyle
import de.mm20.launcher2.preferences.ui.GridSettings
import de.mm20.launcher2.ui.theme.WallpaperColors

val LocalWindowSize = compositionLocalOf { Size(0f, 0f) }

val LocalBackStack = staticCompositionLocalOf< NavBackStack<NavKey>> { throw IllegalStateException("No backstack provided") }

val LocalCardStyle = compositionLocalOf { CardStyle() }

val LocalFavoritesEnabled = compositionLocalOf { true }

val LocalGridSettings = compositionLocalOf { GridSettings() }

val LocalTimeFormat = compositionLocalOf { TimeFormat.TwentyFourHour }
val LocalMeasurementSystem = compositionLocalOf { MeasurementSystem.Metric }

val LocalSnackbarHostState = compositionLocalOf { SnackbarHostState() }

val LocalDarkTheme = compositionLocalOf { false }

/**
 * Workaround a bug in Jetpack Compose which incorrectly places popups
 * that are nested inside other popups.
 */
val LocalWindowPosition = compositionLocalOf { 0f }

val LocalWallpaperColors = compositionLocalOf { WallpaperColors() }

val LocalPreferDarkContentOverWallpaper = compositionLocalOf { false }