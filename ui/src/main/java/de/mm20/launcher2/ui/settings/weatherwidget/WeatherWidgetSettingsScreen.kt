package de.mm20.launcher2.ui.settings.weatherwidget

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.preferences.Settings.WeatherSettings.WeatherProvider
import de.mm20.launcher2.ui.BuildConfig
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.common.WeatherLocationSearchDialog
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import de.mm20.launcher2.ui.component.preferences.*
import de.mm20.launcher2.weather.WeatherLocation

@Composable
fun WeatherWidgetSettingsScreen() {
    val viewModel: WeatherWidgetSettingsScreenVM = viewModel()
    val context = LocalContext.current

    PreferenceScreen(title = stringResource(R.string.preference_screen_weatherwidget)) {
        item {
            PreferenceCategory {
                val weatherProvider by viewModel.weatherProvider.observeAsState()
                ListPreference(
                    title = stringResource(R.string.preference_weather_provider),
                    items = listOf(
                        stringResource(R.string.provider_metno) to WeatherProvider.MetNo,
                        stringResource(R.string.provider_openweathermap) to WeatherProvider.OpenWeatherMap,
                        stringResource(R.string.provider_here) to WeatherProvider.Here,
                        stringResource(R.string.provider_brightsky) to WeatherProvider.BrightSky,
                    ),
                    onValueChanged = {
                        if (it != null) viewModel.setWeatherProvider(it)
                    },
                    value = weatherProvider
                )
                val imperialUnits by viewModel.imperialUnits.observeAsState(false)
                SwitchPreference(
                    title = stringResource(R.string.preference_imperial_units),
                    summary = stringResource(R.string.preference_imperial_units_summary),
                    value = imperialUnits,
                    onValueChanged = {
                        viewModel.setImperialUnits(it)
                    }
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.preference_category_location)) {
                val hasPermission by viewModel.hasLocationPermission.observeAsState()
                AnimatedVisibility(hasPermission == false) {
                    MissingPermissionBanner(
                        text = stringResource(R.string.missing_permission_auto_location),
                        onClick = {
                            viewModel.requestLocationPermission(context as AppCompatActivity)
                        },
                        modifier = Modifier.padding(16.dp)
                    )
                }
                val autoLocation by viewModel.autoLocation.observeAsState(false)
                SwitchPreference(
                    title = stringResource(R.string.preference_automatic_location),
                    summary = stringResource(R.string.preference_automatic_location_summary),
                    value = autoLocation,
                    onValueChanged = {
                        viewModel.setAutoLocation(it)
                    }
                )
                val location by viewModel.location.observeAsState()
                LocationPreference(
                    title = stringResource(R.string.preference_location),
                    value = location,
                    enabled = !autoLocation,
                )
            }
        }
        if (BuildConfig.DEBUG) {
            item {
                PreferenceCategory(stringResource(R.string.preference_category_debug)) {
                    Preference(
                        "Clear weather data",
                        summary = "Remove weather data from database",
                        onClick = {
                            viewModel.clearWeatherData()
                        })
                }
            }
        }
    }
}

@Composable
fun LocationPreference(
    title: String,
    value: WeatherLocation?,
    enabled: Boolean = true
) {
    var showDialog by remember { mutableStateOf(false) }
    Preference(
        title = title,
        summary = value?.name,
        enabled = enabled,
        onClick = {
            showDialog = true
        }
    )
    if (showDialog) {
        WeatherLocationSearchDialog(onDismissRequest = { showDialog = false })
    }
}