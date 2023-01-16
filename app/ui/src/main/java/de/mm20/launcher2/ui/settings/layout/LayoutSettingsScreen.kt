package de.mm20.launcher2.ui.settings.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.preferences.Settings.LayoutSettings.Layout
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen

@Composable
fun LayoutSettingsScreen() {
    val viewModel: LayoutSettingsScreenVM = viewModel()
    PreferenceScreen(
        title = stringResource(id = R.string.preference_layout)
    ) {
        item {
            PreferenceCategory {
                val baseLayout by viewModel.baseLayout.observeAsState()
                ListPreference(title = stringResource(R.string.preference_layout_open_search),
                    items = listOf(
                        stringResource(R.string.open_search_pull_down) to Layout.PullDown,
                        stringResource(R.string.open_search_swipe_left) to Layout.Pager,
                        stringResource(R.string.open_search_swipe_right) to Layout.PagerReversed,
                    ),
                    value = baseLayout,
                    onValueChanged = {
                        if (it != null) viewModel.setBaseLayout(it)
                    },
                )
                val bottomSearchBar by viewModel.bottomSearchBar.observeAsState()
                ListPreference(
                    title = stringResource(R.string.preference_layout_search_bar_position),
                    items = listOf(
                        stringResource(R.string.search_bar_position_top) to false,
                        stringResource(R.string.search_bar_position_bottom) to true,
                    ),
                    value = bottomSearchBar,
                    onValueChanged = {
                        if (it != null) viewModel.setBottomSearchBar(it)
                    },
                )
                val reverseSearchResults by viewModel.reverseSearchResults.observeAsState()
                ListPreference(title = stringResource(R.string.preference_layout_search_results),
                    items = listOf(
                        stringResource(R.string.search_results_order_top_down) to false,
                        stringResource(R.string.search_results_order_bottom_up) to true,
                    ),
                    value = reverseSearchResults,
                    onValueChanged = {
                        if (it != null) viewModel.setReverseSearchResults(it)
                    },
                )
            }
        }
    }
}