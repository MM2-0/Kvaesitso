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
            val customUrl by viewModel.customUrl.collectAsState()
            TextPreference(
                title = stringResource(R.string.preference_search_location_custom_overpass_url),
                value = customUrl,
                placeholder = stringResource(id = R.string.overpass_url),
                summary = customUrl.takeIf { !it.isNullOrBlank() }
                    ?: stringResource(id = R.string.overpass_url),
                onValueChanged = {
                    viewModel.setCustomUrl(it)
                })
        }
    }
}