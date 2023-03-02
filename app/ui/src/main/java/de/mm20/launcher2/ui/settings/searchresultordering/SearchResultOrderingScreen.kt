package de.mm20.launcher2.ui.settings.searchresultordering

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChangeCircle
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.preferences.Settings.SearchResultOrderingSettings.*
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SliderPreference

@Composable
fun SearchResultOrderingScreen() {
    val viewModel: SearchResultOrderingScreenVM = viewModel()

    PreferenceScreen(
        title = stringResource(R.string.preference_search_result_ordering_title)
    ) {
        item {
            PreferenceCategory {
                val searchResultOrdering by viewModel.searchResultOrdering.observeAsState()
                ListPreference(
                    title = stringResource(R.string.preference_search_result_ordering),
                    items = listOf(
                        stringResource(R.string.preference_search_result_ordering_alphabetic) to Ordering.Alphabetic,
                        stringResource(R.string.preference_search_result_ordering_launch_count) to Ordering.LaunchCount,
                        stringResource(R.string.preference_search_result_ordering_weighted) to Ordering.Weighted
                    ),
                    value = searchResultOrdering,
                    onValueChanged = {
                        if (it != null) viewModel.setSearchResultOrdering(it)
                    },
                    icon = Icons.Rounded.Sort
                )
                val searchResultWeightFactor by viewModel.searchResultWeightFactor.observeAsState(
                    WeightFactor.Default
                )
                AnimatedVisibility(visible = searchResultOrdering == Ordering.Weighted) {
                    SliderPreference(
                        title = stringResource(R.string.preference_search_result_ordering_weight_factor),
                        icon = Icons.Rounded.ChangeCircle,
                        value = searchResultWeightFactor,
                        labels = listOf(
                            stringResource(R.string.preference_search_result_ordering_weight_factor_low) to WeightFactor.Low,
                            stringResource(R.string.preference_search_result_ordering_weight_factor_default) to WeightFactor.Default,
                            stringResource(R.string.preference_search_result_ordering_weight_factor_high) to WeightFactor.High
                        )
                    ) { viewModel.setSearchResultWeightFactor(it) }
                }
            }
        }
    }
}