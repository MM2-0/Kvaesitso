package de.mm20.launcher2.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings.AppearanceSettings
import de.mm20.launcher2.preferences.Settings.AppearanceSettings.Theme
import de.mm20.launcher2.ui.theme.colorscheme.DarkBlackAndWhiteColorScheme
import de.mm20.launcher2.ui.theme.colorscheme.DarkPre31DefaultColorScheme
import de.mm20.launcher2.ui.theme.colorscheme.LightBlackAndWhiteColorScheme
import de.mm20.launcher2.ui.theme.colorscheme.LightPre31DefaultColorScheme
import de.mm20.launcher2.ui.theme.typography.DefaultTypography
import kotlinx.coroutines.flow.map
import org.koin.androidx.compose.inject


@Composable
fun LauncherTheme(
    content: @Composable () -> Unit
) {

    val dataStore: LauncherDataStore by inject()

    val colorSchemePreference by remember { dataStore.data.map { it.appearance.colorScheme } }.collectAsState(
        AppearanceSettings.ColorScheme.Default
    )
    val themePreference by remember { dataStore.data.map { it.appearance.theme } }.collectAsState(
        Theme.System
    )

    val darkTheme =
        themePreference == Theme.Dark || themePreference == Theme.System && isSystemInDarkTheme()

    MaterialTheme(
        colorScheme = getColorScheme(LocalContext.current, colorSchemePreference, darkTheme),
        typography = DefaultTypography,
        content = content
    )
}

fun getColorScheme(context: Context, colorScheme: AppearanceSettings.ColorScheme, darkTheme: Boolean): ColorScheme {
    return when (colorScheme) {
        AppearanceSettings.ColorScheme.BlackAndWhite -> {
            if (darkTheme) DarkBlackAndWhiteColorScheme
            else LightBlackAndWhiteColorScheme
        }
        else -> {
            if (darkTheme) {
                if (isAtLeastApiLevel(31)) dynamicDarkColorScheme(context)
                else DarkPre31DefaultColorScheme
            } else {
                if (isAtLeastApiLevel(31)) dynamicLightColorScheme(context)
                else LightPre31DefaultColorScheme
            }
        }
    }
}