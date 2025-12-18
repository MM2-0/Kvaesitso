package de.mm20.launcher2.ui.settings.osm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.preferences.search.LocationSearchSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.component.preferences.TextPreference
import kotlinx.serialization.Serializable

@Serializable
data object OsmSettingsRoute: NavKey

@Composable
fun OsmSettingsScreen() {
    val viewModel: OsmSettingsScreenVM = viewModel()
    val osmLocations by viewModel.osmLocations.collectAsState()
    val hideUncategorized by viewModel.hideUncategorized.collectAsState()
    val customOverpassUrl by viewModel.customOverpassUrl.collectAsState()

    PreferenceScreen(title = stringResource(R.string.preference_search_osm_locations)) {
        item {
            PreferenceCategory {
                SwitchPreference(
                    title = stringResource(R.string.preference_search_osm_locations),
                    summary = stringResource(R.string.preference_search_osm_locations_summary),
                    value = osmLocations == true,
                    onValueChanged = {
                        viewModel.setOsmLocations(it)
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_search_locations_hide_uncategorized),
                    summary = stringResource(R.string.preference_search_locations_hide_uncategorized_summary),
                    value = hideUncategorized == true,
                    enabled = osmLocations == true,
                    onValueChanged = {
                        viewModel.setHideUncategorized(it)
                    }
                )
            }
        }

        item {
            PreferenceCategory(stringResource(R.string.preference_category_advanced)) {
                TextPreference(
                    title = stringResource(R.string.preference_search_location_custom_overpass_url),
                    value = customOverpassUrl ?: "",
                    placeholder = LocationSearchSettings.DefaultOverpassUrl,
                    summary = customOverpassUrl?.takeIf { it.isNotBlank() }
                        ?: LocationSearchSettings.DefaultOverpassUrl,
                    onValueChanged = {
                        viewModel.setCustomOverpassUrl(it)
                    },
                    enabled = osmLocations == true,
                )
            }
        }
    }
}