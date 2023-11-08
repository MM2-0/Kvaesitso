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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SliderPreference
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import java.text.DecimalFormat
import kotlin.math.floor
import kotlin.math.log

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
                    val isKm = it >= 1000
                    val format = remember {
                        DecimalFormat().apply {
                            maximumFractionDigits = 1
                            minimumFractionDigits = 0
                        }
                    }
                    Text(
                        modifier = Modifier
                            .width(64.dp)
                            .padding(start = 16.dp),
                        text =
                        if (isKm) "${format.format(it / 1000.0)} ${stringResource(R.string.unit_kilometer_symbol)}"
                        else "${format.format(it)} ${stringResource(R.string.unit_meter_symbol)}",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            )
        }
    }
}