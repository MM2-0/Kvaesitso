package de.mm20.launcher2.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import de.mm20.launcher2.ktx.isAtLeastApiLevel
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

    val state = remember(colorScheme, darkTheme) {
        mutableStateOf(
            when (colorScheme) {
                AppearanceSettings.ColorScheme.BlackAndWhite -> {
                    if (darkTheme) DarkBlackAndWhiteColorScheme else LightBlackAndWhiteColorScheme
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
        )
    }

    if (colorScheme == AppearanceSettings.ColorScheme.Wallpaper && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        val wallpaperColors by wallpaperColorsAsState()
        LaunchedEffect(wallpaperColors, darkTheme) {
            state.value = WallpaperColorScheme(wallpaperColors, darkTheme)
        }
    }

    return state
}
