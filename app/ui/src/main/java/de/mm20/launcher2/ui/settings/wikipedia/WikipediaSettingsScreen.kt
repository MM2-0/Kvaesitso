package de.mm20.launcher2.ui.settings.wikipedia

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.component.preferences.TextPreference

@Composable
fun WikipediaSettingsScreen() {
    val viewModel: WikipediaSettingsScreenVM = viewModel()
    PreferenceScreen(title = stringResource(R.string.preference_search_wikipedia)) {
        item {
            PreferenceCategory {
                val wikipedia by viewModel.wikipedia.collectAsState()
                SwitchPreference(
                    title = stringResource(R.string.preference_search_wikipedia),
                    summary = stringResource(R.string.preference_search_wikipedia_summary),
                    value = wikipedia == true,
                    onValueChanged = {
                        viewModel.setWikipedia(it)
                    }
                )
                val customUrl by viewModel.customUrl.collectAsState()
                TextPreference(
                    title = stringResource(R.string.preference_wikipedia_customurl),
                    value = customUrl ?: "",
                    placeholder = stringResource(id = R.string.wikipedia_url),
                    summary = customUrl.takeIf { !it.isNullOrBlank() }
                        ?: stringResource(id = R.string.wikipedia_url),
                    onValueChanged = {
                        viewModel.setCustomUrl(it)
                    })
            }
        }
    }
}