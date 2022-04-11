package de.mm20.launcher2.ui.settings.colorscheme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.preferences.Settings.AppearanceSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.theme.getColorScheme

@Composable
fun ColorSchemeSettingsScreen() {
    val viewModel: ColorSchemeSettingsScreenVM = viewModel()
    val context = LocalContext.current

    PreferenceScreen(title = stringResource(R.string.preference_screen_colors)) {
        item {
            PreferenceCategory {
                val theme by viewModel.theme.observeAsState()
                val darkTheme =
                    theme == AppearanceSettings.Theme.Dark || theme == AppearanceSettings.Theme.System && isSystemInDarkTheme()
                val colorScheme by viewModel.colorScheme.observeAsState()

                val items = listOf(
                    AppearanceSettings.ColorScheme.Default to R.string.preference_colors_default,
                    AppearanceSettings.ColorScheme.BlackAndWhite to R.string.preference_colors_bw
                )

                for (cs in items) {
                    Preference(
                        title = stringResource(cs.second),
                        icon = if (colorScheme == cs.first) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                        onClick = { viewModel.setColorScheme(cs.first) },
                        controls = {
                            ColorSchemePreview(
                                getColorScheme(
                                    LocalContext.current,
                                    cs.first,
                                    darkTheme,
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ColorSchemePreview(colorScheme: ColorScheme) {
    Box(
        modifier = Modifier
            .padding(vertical = 12.dp)
            .width(72.dp)
            .height(36.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                tonalElevation = 1.dp,
                color = colorScheme.surface,
                modifier = Modifier
                    .size(36.dp)
            ) {}
            Surface(
                tonalElevation = 1.dp,
                color = colorScheme.surfaceVariant,
                modifier = Modifier
                    .size(36.dp)
            ) {}
        }
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                tonalElevation = 1.dp,
                color = colorScheme.primary,
                modifier = Modifier
                    .size(16.dp)
            ) {}
            Surface(
                tonalElevation = 1.dp,
                color = colorScheme.secondary,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(16.dp)
            ) {}
            Surface(
                tonalElevation = 1.dp,
                color = colorScheme.tertiary,
                modifier = Modifier
                    .size(16.dp)
            ) {}
        }
    }
}