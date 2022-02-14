package de.mm20.launcher2.ui.locals

import android.appwidget.AppWidgetHost
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Size
import androidx.navigation.NavController
import de.mm20.launcher2.preferences.Settings

val LocalWindowSize = compositionLocalOf { Size(0f, 0f) }

val LocalAppWidgetHost = compositionLocalOf<AppWidgetHost?>(defaultFactory = { null })

val LocalNavController = compositionLocalOf<NavController?> { null }

val LocalCardStyle = compositionLocalOf<Settings.CardSettings> { Settings.CardSettings.getDefaultInstance() }

val LocalFavoritesEnabled = compositionLocalOf { true }

/**
 * Workaround a bug in Jetpack Compose which incorrectly places popups
 * that are nested inside other popups.
 */
val LocalWindowPosition = compositionLocalOf { 0f }