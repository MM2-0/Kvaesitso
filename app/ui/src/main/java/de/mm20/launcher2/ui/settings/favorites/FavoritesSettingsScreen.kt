package de.mm20.launcher2.ui.settings.favorites

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TableRows
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.preferences.Settings.SearchResultOrderingSettings.WeightFactor
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SliderPreference
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.launcher.sheets.EditFavoritesSheet

@Composable
fun FavoritesSettingsScreen() {
    val viewModel: FavoritesSettingsScreenVM = viewModel()
    var showEditSheet by remember { mutableStateOf(false) }
    PreferenceScreen(
        title = stringResource(R.string.preference_search_favorites)
    ) {
        item {
            PreferenceCategory {
                Preference(
                    title = stringResource(R.string.menu_item_edit_favs),
                    summary = stringResource(R.string.preference_edit_favorites_summary),
                    icon = Icons.Rounded.Sort,
                    onClick = {
                        showEditSheet = true
                    }
                )
            }
        }
        item {
            PreferenceCategory(stringResource(R.string.preference_category_favorites_frequently_used)) {
                val frequentlyUsed by viewModel.frequentlyUsed.observeAsState()
                SwitchPreference(
                    title = stringResource(R.string.frequently_used_show_in_favorites),
                    summary = stringResource(R.string.preference_favorites_frequently_used_summary),
                    value = frequentlyUsed == true,
                    onValueChanged = {
                        viewModel.setFrequentlyUsed(it)
                    },
                    icon = Icons.Rounded.Insights
                )
                val searchResultWeightFactor by viewModel.searchResultWeightFactor.observeAsState(WeightFactor.Default)
                SliderPreference(
                    title = stringResource(R.string.preference_search_result_ordering_weight_factor),
                    icon = Icons.Rounded.SwapVert,
                    value = searchResultWeightFactor,
                    labels = listOf(
                        stringResource(R.string.preference_search_result_ordering_weight_factor_low) to WeightFactor.Low,
                        stringResource(R.string.preference_search_result_ordering_weight_factor_default) to WeightFactor.Default,
                        stringResource(R.string.preference_search_result_ordering_weight_factor_high) to WeightFactor.High
                    )
                ) { viewModel.setSearchResultWeightFactor(it) }
                val frequentlyUsedRows by viewModel.frequentlyUsedRows.observeAsState(1)
                SliderPreference(
                    title = stringResource(R.string.frequently_used_rows),
                    value = frequentlyUsedRows,
                    enabled = frequentlyUsed == true,
                    min = 1,
                    max = 4,
                    onValueChanged = {
                        viewModel.setFrequentlyUsedRows(it)
                    },
                    icon = Icons.Rounded.TableRows
                )
            }
        }
        item {
            val editButton by viewModel.editButton.observeAsState()
            PreferenceCategory {
                SwitchPreference(
                    title = stringResource(R.string.preference_edit_button),
                    summary = stringResource(R.string.preference_favorites_edit_button_summary),
                    value = editButton == true,
                    onValueChanged = {
                        viewModel.setEditButton(it)
                    },
                    icon = Icons.Rounded.Edit
                )
            }
        }
    }

    if (showEditSheet) {
        EditFavoritesSheet(
            onDismiss = { showEditSheet = false }
        )
    }
}