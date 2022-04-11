package de.mm20.launcher2.ui.settings.colorscheme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.preferences.Settings.AppearanceSettings.ColorScheme
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen

@Composable
fun ColorSchemeSettingsScreen() {
    val viewModel: ColorSchemeSettingsScreenVM = viewModel()
    val context = LocalContext.current

    PreferenceScreen(title = stringResource(R.string.preference_screen_colors)) {
        item {
            PreferenceCategory {
                val colorScheme by viewModel.colorScheme.observeAsState()
                Preference(
                    title = stringResource(R.string.preference_colors_default),
                    icon = if (colorScheme == ColorScheme.Default) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                    onClick = { viewModel.setColorScheme(ColorScheme.Default) }
                )
                Preference(
                    title = stringResource(R.string.preference_colors_bw),
                    icon = if (colorScheme == ColorScheme.BlackAndWhite) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                    onClick = { viewModel.setColorScheme(ColorScheme.BlackAndWhite) }
                )
            }
        }
    }
}