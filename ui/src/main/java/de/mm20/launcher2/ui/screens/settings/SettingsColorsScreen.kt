package de.mm20.launcher2.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.preferences.dataStore
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.theme.colors.*
import de.mm20.launcher2.ui.theme.wallpaperColorsAsState
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import de.mm20.launcher2.preferences.Settings.AppearanceSettings.ColorScheme as ColorSchemeOption

@Composable
fun SettingsColorsScreen() {
    val context = LocalContext.current
    val dataStore = context.dataStore
    val scope = rememberCoroutineScope()
    PreferenceScreen(title = stringResource(id = R.string.preference_screen_colors)) {
        item {
            val colorScheme by remember {
                dataStore.data.map {
                    it.appearance.colorScheme
                }
            }.collectAsState(initial = ColorSchemeOption.Default)
            val schemes = mutableListOf(
                ColorSchemeItem(
                    ColorSchemeOption.Default,
                    DefaultColorScheme(),
                    stringResource(id = R.string.preference_colors_default)
                ),
                ColorSchemeItem(
                    ColorSchemeOption.MM20,
                    MM20ColorScheme(),
                    stringResource(id = R.string.preference_colors_mm20)
                ),
                ColorSchemeItem(
                    ColorSchemeOption.BlackAndWhite,
                    BlackWhiteColorScheme(),
                    stringResource(id = R.string.preference_colors_bw)
                )
            )
            if (isAtLeastApiLevel(Build.VERSION_CODES.S)) {
                schemes.add(
                    ColorSchemeItem(
                        ColorSchemeOption.MaterialYou,
                        SystemColorScheme(context),
                        stringResource(id = R.string.preference_colors_mdyou)
                    )
                )
            }
            if (isAtLeastApiLevel(Build.VERSION_CODES.O_MR1)) {
                val wallpaperColors by wallpaperColorsAsState()
                schemes.add(
                    ColorSchemeItem(
                        ColorSchemeOption.Wallpaper,
                        WallpaperColorScheme(wallpaperColors),
                        stringResource(id = R.string.preference_colors_wallpaper)
                    )
                )
            }
            PreferenceCategory {
                for (scheme in schemes) {
                    Preference(
                        title = scheme.label,
                        icon = if (colorScheme == scheme.value) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                        controls = {
                            ColorSchemePreview(scheme.colorScheme)
                        },
                        onClick = {
                            scope.launch {
                                dataStore.updateData {
                                    it.toBuilder()
                                        .setAppearance(
                                            it.appearance.toBuilder().setColorScheme(scheme.value)
                                        )
                                        .build()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorSchemePreview(colorScheme: ColorScheme) {
    val isDark = !MaterialTheme.colors.isLight
    val neutral1 = if (isDark) colorScheme.neutral1.shade800 else colorScheme.neutral1.shade100
    val neutral2 = if (isDark) colorScheme.neutral2.shade800 else colorScheme.neutral2.shade100
    val accent1 = if (isDark) colorScheme.accent1.shade300 else colorScheme.accent1.shade500
    val accent2 = if (isDark) colorScheme.accent2.shade300 else colorScheme.accent2.shade500
    val accent3 = if (isDark) colorScheme.accent3.shade300 else colorScheme.accent3.shade500
    Box(
        modifier = Modifier.height(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxHeight()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(48.dp)
                    .background(neutral1)
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(48.dp)
                    .background(neutral2)
            )
        }

        Row(
            modifier = Modifier.height(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(accent1)
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(16.dp)
                    .background(accent2)
            )
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(accent3)
            )
        }

    }
}

private data class ColorSchemeItem(
    val value: ColorSchemeOption,
    val colorScheme: ColorScheme,
    val label: String,
)