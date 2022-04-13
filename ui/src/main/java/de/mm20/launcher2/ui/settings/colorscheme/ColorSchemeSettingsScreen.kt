package de.mm20.launcher2.ui.settings.colorscheme

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.preferences.Settings.AppearanceSettings
import de.mm20.launcher2.ui.BuildConfig
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.theme.colorSchemeAsState

@Composable
fun ColorSchemeSettingsScreen() {
    val viewModel: ColorSchemeSettingsScreenVM = viewModel()

    PreferenceScreen(title = stringResource(R.string.preference_screen_colors)) {
        item {
            PreferenceCategory {
                val colorScheme by viewModel.colorScheme.observeAsState()

                val items = mutableListOf(
                    AppearanceSettings.ColorScheme.Default to stringResource(R.string.preference_colors_default),
                    AppearanceSettings.ColorScheme.BlackAndWhite to stringResource(R.string.preference_colors_bw),
                )

                if (BuildConfig.DEBUG && isAtLeastApiLevel(27)) {
                    items.add(AppearanceSettings.ColorScheme.DebugMaterialYouCompat to "Material You Compat")
                }

                for (cs in items) {
                    val scheme by colorSchemeAsState(cs.first)
                    Preference(
                        title = cs.second,
                        icon = if (colorScheme == cs.first) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                        onClick = { viewModel.setColorScheme(cs.first) },
                        controls = {
                            ColorSchemePreview(scheme)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ColorSchemePreview(colorScheme: ColorScheme) {
    MaterialTheme(colorScheme = colorScheme) {
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
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .size(36.dp)
                ) {}
                Surface(
                    tonalElevation = 1.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .size(36.dp)
                ) {}
            }
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    tonalElevation = 1.dp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(16.dp)
                ) {}
                Surface(
                    tonalElevation = 1.dp,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .size(16.dp)
                ) {}
                Surface(
                    tonalElevation = 1.dp,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier
                        .size(16.dp)
                ) {}
            }
        }
    }

}