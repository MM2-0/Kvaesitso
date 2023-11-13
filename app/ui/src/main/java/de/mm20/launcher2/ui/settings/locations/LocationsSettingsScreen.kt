package de.mm20.launcher2.ui.settings.locations

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ZoomOutMap
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
    PreferenceScreen(title = stringResource(R.string.preference_search_locations)) {
        item {
            val locations by viewModel.locations.collectAsState()
            SwitchPreference(
                title = stringResource(R.string.preference_search_locations),
                summary = stringResource(R.string.preference_search_locations_summary),
                value = locations == true,
                onValueChanged = {
                    viewModel.setLocations(it)
                }
            )
            PreferenceCategory {
                val insaneUnits by viewModel.insaneUnits.collectAsState()
                ListPreference(
                    title = stringResource(R.string.length_unit),
                    items = listOf(
                        stringResource(R.string.imperial) to true,
                        stringResource(R.string.metric) to false
                    ),
                    enabled = locations == true,
                    value = insaneUnits,
                    onValueChanged = {
                        viewModel.setInsaneUnits(it)
                    }
                )
                val radius by viewModel.radius.collectAsState()
                SliderPreference(
                    title = stringResource(R.string.preference_search_locations_radius),
                    icon = Icons.Rounded.ZoomOutMap,
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
                            text = it.toFloat().metersToLocalizedString(LocalContext.current, insaneUnits),
                            style = MaterialTheme.typography.titleSmall
                        )
                        it.toFloat().metersToLocalizedString(LocalContext.current, insaneUnits)
                    }
                )
                val hideUncategorized by viewModel.hideUncategorized.collectAsState()
                SwitchPreference(
                    title = stringResource(R.string.preference_search_locations_hide_uncategorized),
                    summary = stringResource(R.string.preference_search_locations_hide_uncategorized_summary),
                    value = hideUncategorized,
                    enabled = locations == true,
                    onValueChanged = {
                        viewModel.setHideUncategorized(it)
                    }
                )
                val customOverpassUrl by viewModel.customOverpassUrl.collectAsState()
                TextPreference(
                    title = stringResource(R.string.preference_search_location_custom_overpass_url),
                    value = customOverpassUrl,
                    placeholder = stringResource(id = R.string.overpass_url),
                    summary = customOverpassUrl.takeIf { !it.isNullOrBlank() }
                        ?: stringResource(id = R.string.overpass_url),
                    onValueChanged = {
                        viewModel.setCustomOverpassUrl(it)
                    }
                )
            }
            PreferenceCategory {
                val showMap by viewModel.showMap.collectAsState()
                SwitchPreference(
                    title = stringResource(R.string.preference_search_locations_show_map),
                    summary = stringResource(R.string.preference_search_locations_show_map_summary),
                    enabled = locations == true,
                    value = showMap,
                    onValueChanged = {
                        viewModel.setShowMap(it)
                    }
                )
                val themeMap by viewModel.themeMap.collectAsState()
                SwitchPreference(
                    title = stringResource(R.string.preference_search_locations_theme_map),
                    summary = stringResource(R.string.preference_search_locations_theme_map_summary),
                    value = themeMap,
                    enabled = locations == true && showMap,
                    onValueChanged = {
                        viewModel.setThemeMap(it)
                    }
                )
                val showPositionOnMap by viewModel.showPositionOnMap.collectAsState()
                SwitchPreference(
                    title = stringResource(R.string.preference_search_locations_show_position_on_map),
                    summary = stringResource(R.string.preference_search_locations_show_position_on_map_summary),
                    value = showPositionOnMap,
                    enabled = locations == true && showMap,
                    onValueChanged = {
                        viewModel.setShowPositionOnMap(it)
                    }
                )
                val customTileServerUrl by viewModel.customTileServerUrl.collectAsState()
                TextPreference(
                    title = stringResource(R.string.preference_search_location_custom_tile_server_url),
                    value = customTileServerUrl,
                    placeholder = stringResource(id = R.string.tile_server_url),
                    summary = customTileServerUrl.takeIf { !it.isNullOrBlank() }
                        ?: stringResource(id = R.string.tile_server_url),
                    onValueChanged = {
                        viewModel.setCustomTileServerUrl(it)
                    }
                )
            }
        }
    }
}