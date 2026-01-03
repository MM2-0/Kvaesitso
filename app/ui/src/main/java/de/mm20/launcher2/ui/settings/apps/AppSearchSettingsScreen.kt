package de.mm20.launcher2.ui.settings.apps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import kotlinx.serialization.Serializable

@Serializable
data object AppSearchSettingsRoute : NavKey

@Composable
fun AppSearchSettingsScreen() {

    val viewModel = viewModel<AppSearchSettingsScreenVM>()

    val allApps by viewModel.allApps.collectAsStateWithLifecycle()
    val showList by viewModel.showList.collectAsStateWithLifecycle()
    val showListIcons by viewModel.showListIcons.collectAsStateWithLifecycle()

    PreferenceScreen(
        title = stringResource(R.string.preference_search_apps)
    ) {
        item {
            PreferenceCategory {
                SwitchPreference(
                    icon = R.drawable.apps_24px,
                    title = stringResource(R.string.preference_search_apps),
                    summary = stringResource(R.string.preference_search_apps_summary),
                    value = allApps == true,
                    onValueChanged = {
                        viewModel.setAllApps(it)
                    }
                )
            }
        }
        item {
            PreferenceCategory {
                SwitchPreference(
                    title = stringResource(R.string.preference_grid_list_style),
                    summary = stringResource(R.string.preference_grid_list_style_summary),
                    value = showList == true,
                    onValueChanged = {
                        viewModel.setShowList(it)
                    }
                )
                AnimatedVisibility(showList == true) {
                    SwitchPreference(
                        title = stringResource(R.string.preference_grid_list_icons),
                        summary = stringResource(R.string.preference_grid_list_icons_summary),
                        value = showListIcons == true,
                        onValueChanged = {
                            viewModel.setShowListIcons(it)
                        }
                    )
                }
            }
        }
    }
}