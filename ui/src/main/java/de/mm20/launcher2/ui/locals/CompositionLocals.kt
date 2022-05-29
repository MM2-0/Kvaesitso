package de.mm20.launcher2.ui.locals

import android.appwidget.AppWidgetHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import de.mm20.launcher2.preferences.Settings

val LocalWindowSize = compositionLocalOf { Size(0f, 0f) }

val LocalAppWidgetHost = compositionLocalOf<AppWidgetHost?>(defaultFactory = { null })

val LocalNavController = compositionLocalOf<NavController?> { null }

val LocalCardStyle = compositionLocalOf<Settings.CardSettings> { Settings.CardSettings.getDefaultInstance() }

val LocalFavoritesEnabled = compositionLocalOf { true }

val LocalGridColumns = compositionLocalOf { 5 }

val LocalGridIconSize = compositionLocalOf { 48.dp }

val LocalSnackbarHostState = compositionLocalOf { SnackbarHostState() }

val LocalDarkTheme = compositionLocalOf { false }

/**
 * Workaround a bug in Jetpack Compose which incorrectly places popups
 * that are nested inside other popups.
 */
val LocalWindowPosition = compositionLocalOf { 0f }