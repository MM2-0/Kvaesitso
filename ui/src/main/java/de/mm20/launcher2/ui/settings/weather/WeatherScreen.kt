package de.mm20.launcher2.ui.settings.weather

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.preferences.Settings.WeatherSettings.WeatherProvider
import de.mm20.launcher2.ui.BuildConfig
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import de.mm20.launcher2.ui.component.preferences.*
import de.mm20.launcher2.weather.WeatherLocation
import kotlinx.coroutines.launch

@Composable
fun WeatherScreen() {
    val viewModel: WeatherScreenVM = viewModel()
    val context = LocalContext.current

    PreferenceScreen(title = stringResource(R.string.preference_screen_weather)) {
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
                val scope = rememberCoroutineScope()

                val location by viewModel.location.observeAsState()
                val isSearching by viewModel.isSearchingLocation.observeAsState(initial = false)
                val locations by viewModel.locationResults.observeAsState(emptyList())
                LocationPreference(
                    title = stringResource(R.string.preference_location),
                    value = location,
                    locations = locations,
                    onValueChanged = {
                        viewModel.setLocation(it)
                    },
                    onLocationSearch = {
                        scope.launch {
                            viewModel.searchLocation(it)
                        }
                    },
                    enabled = !autoLocation,
                    isSearching = isSearching,
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
    onValueChanged: (WeatherLocation) -> Unit,
    onLocationSearch: (String) -> Unit,
    isSearching: Boolean,
    locations: List<WeatherLocation>,
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
        Dialog(onDismissRequest = { showDialog = false }) {
            Surface(
                tonalElevation = 16.dp,
                shadowElevation = 16.dp,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 16.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp
                            )
                    )
                    var query by remember { mutableStateOf("") }
                    OutlinedTextField(
                        singleLine = true,
                        value = query,
                        onValueChange = {
                            query = it
                            onLocationSearch(it)
                        }, modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    )
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .weight(1f)
                                .padding(vertical = 16.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(bottom = 16.dp)
                        ) {
                            items(locations) {
                                Text(
                                    text = it.name,
                                    modifier = Modifier
                                        .clickable {
                                            onValueChanged(it)
                                            showDialog = false
                                        }
                                        .padding(
                                            horizontal = 24.dp,
                                            vertical = 16.dp
                                        )
                                )
                            }
                        }

                    }
                    TextButton(
                        onClick = { showDialog = false },
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(24.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.close),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

            }
        }
    }
}