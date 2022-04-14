package de.mm20.launcher2.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings.AppearanceSettings
import de.mm20.launcher2.preferences.Settings.AppearanceSettings.Theme
import de.mm20.launcher2.ui.theme.colorscheme.*
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

    val colorScheme by colorSchemeAsState(colorSchemePreference)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DefaultTypography,
        content = content
    )
}

@Composable
fun colorSchemeAsState(colorScheme: AppearanceSettings.ColorScheme): MutableState<ColorScheme> {
    val context = LocalContext.current
    val dataStore: LauncherDataStore by inject()

    val themePreference by remember { dataStore.data.map { it.appearance.theme } }.collectAsState(
        Theme.System
    )
    val darkTheme =
        themePreference == Theme.Dark || themePreference == Theme.System && isSystemInDarkTheme()


    when (colorScheme) {
        AppearanceSettings.ColorScheme.BlackAndWhite -> {
            return remember(darkTheme) {
                mutableStateOf(
                    if (darkTheme) DarkBlackAndWhiteColorScheme else LightBlackAndWhiteColorScheme
                )
            }
        }
        AppearanceSettings.ColorScheme.Custom -> {
            val colors by remember(darkTheme) {
                dataStore.data.map { if (darkTheme) it.appearance.customColors.darkScheme else it.appearance.customColors.lightScheme }
            }.collectAsState(null)
            val state = remember(colors, darkTheme) {
                mutableStateOf(
                    colors?.let { CustomColorScheme(it) }
                        ?: if (darkTheme) DarkDefaultColorScheme else LightDefaultColorScheme
                )
            }
            return state
        }
        else -> {
            if (Build.VERSION.SDK_INT >= 27 && (Build.VERSION.SDK_INT < 31 || colorScheme == AppearanceSettings.ColorScheme.DebugMaterialYouCompat)) {
                val wallpaperColors by wallpaperColorsAsState()
                val state = remember(wallpaperColors, darkTheme) {
                    mutableStateOf(
                        wallpaperColors?.let { MaterialYouCompatScheme(it, darkTheme) }
                            ?: if (darkTheme) DarkDefaultColorScheme else LightDefaultColorScheme
                    )
                }
                return state
            }
            if (Build.VERSION.SDK_INT >= 31) {
                return remember(darkTheme) {
                    mutableStateOf(
                        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(
                            context
                        )
                    )
                }
            }

            return remember { mutableStateOf(if (darkTheme) DarkDefaultColorScheme else LightDefaultColorScheme) }

        }
    }

}
