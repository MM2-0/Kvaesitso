package de.mm20.launcher2.ui.settings.locations

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.preferences.search.LocationSearchSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SliderPreference
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.component.preferences.TextPreference
import de.mm20.launcher2.ui.ktx.metersToLocalizedString

@Composable
fun LocationsSettingsScreen() {
    val viewModel: LocationsSettingsScreenVM = viewModel()

    val locations by viewModel.locations.collectAsState()
    val imperialUnits by viewModel.imperialUnits.collectAsState()
    val hideUncategorized by viewModel.hideUncategorized.collectAsState()
    val radius by viewModel.radius.collectAsState()
    val customOverpassUrl by viewModel.customOverpassUrl.collectAsState()
    val showMap by viewModel.showMap.collectAsState()
    val themeMap by viewModel.themeMap.collectAsState()
    val showPositionOnMap by viewModel.showPositionOnMap.collectAsState()
    val customTileServerUrl by viewModel.customTileServerUrl.collectAsState()


    PreferenceScreen(title = stringResource(R.string.preference_search_locations)) {
        item {
            PreferenceCategory {
                SwitchPreference(
                    title = stringResource(R.string.preference_search_locations),
                    summary = stringResource(R.string.preference_search_locations_summary),
                    value = locations == true,
                    onValueChanged = {
                        viewModel.setLocations(it)
                    }
                )
            }
        }
        item {
            PreferenceCategory {
                ListPreference(
                    title = stringResource(R.string.length_unit),
                    items = listOf(
                        stringResource(R.string.imperial) to true,
                        stringResource(R.string.metric) to false
                    ),
                    enabled = locations == true,
                    value = imperialUnits,
                    onValueChanged = {
                        viewModel.setImperialUnits(it)
                    }
                )
                SliderPreference(
                    title = stringResource(R.string.preference_search_locations_radius),
                    value = radius,
                    min = 500,
                    max = 10000,
                    step = 500,
                    enabled = locations == true,
                    onValueChanged = {
                        viewModel.setRadius(it)
                    },
                    label = {
                        Text(
                            modifier = Modifier
                                .width(64.dp)
                                .padding(start = 16.dp),
                            text = it.toFloat()
                                .metersToLocalizedString(LocalContext.current, imperialUnits),
                            style = MaterialTheme.typography.titleSmall
                        )
                        it.toFloat()
                            .metersToLocalizedString(LocalContext.current, imperialUnits)
                    }
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_search_locations_hide_uncategorized),
                    summary = stringResource(R.string.preference_search_locations_hide_uncategorized_summary),
                    value = hideUncategorized == true,
                    enabled = locations == true,
                    onValueChanged = {
                        viewModel.setHideUncategorized(it)
                    }
                )
            }
        }
        item {
            PreferenceCategory {
                SwitchPreference(
                    title = stringResource(R.string.preference_search_locations_show_map),
                    summary = stringResource(R.string.preference_search_locations_show_map_summary),
                    enabled = locations == true,
                    value = showMap == true,
                    onValueChanged = {
                        viewModel.setShowMap(it)
                    }
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_search_locations_theme_map),
                    summary = stringResource(R.string.preference_search_locations_theme_map_summary),
                    value = themeMap == true,
                    enabled = locations == true && showMap == true,
                    onValueChanged = {
                        viewModel.setThemeMap(it)
                    }
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_search_locations_show_position_on_map),
                    summary = stringResource(R.string.preference_search_locations_show_position_on_map_summary),
                    value = showPositionOnMap == true,
                    enabled = locations == true && showMap == true,
                    onValueChanged = {
                        viewModel.setShowPositionOnMap(it)
                    }
                )
            }

        }
        item {
            PreferenceCategory(stringResource(R.string.preference_category_advanced)) {
                TextPreference(
                    title = stringResource(R.string.preference_search_location_custom_overpass_url),
                    value = customOverpassUrl,
                    placeholder = LocationSearchSettings.DefaultOverpassUrl,
                    summary = customOverpassUrl.takeIf { !it.isNullOrBlank() }
                        ?: LocationSearchSettings.DefaultOverpassUrl,
                    onValueChanged = {
                        viewModel.setCustomOverpassUrl(it)
                    }
                )
                TextPreference(
                    title = stringResource(R.string.preference_search_location_custom_tile_server_url),
                    value = customTileServerUrl ?: "",
                    placeholder = LocationSearchSettings.DefaultTileServerUrl,
                    summary = customTileServerUrl.takeIf { !it.isNullOrBlank() }
                        ?: LocationSearchSettings.DefaultTileServerUrl,
                    onValueChanged = {
                        viewModel.setCustomTileServerUrl(it)
                    }
                )
            }
        }
    }
}