package de.mm20.launcher2.ui.settings.breezyweather

import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen

@Composable
fun BreezyWeatherSettingsScreen() {
    val activity = LocalActivity.current as AppCompatActivity
    val viewModel: BreezyWeatherSettingsScreenVM = viewModel()

    val isBreezyInstalled by viewModel.isBreezyInstalled.collectAsStateWithLifecycle(null)
    val isWeatherProvider by viewModel.isBreezySetAsWeatherProvider.collectAsStateWithLifecycle(null)

    PreferenceScreen(
        title = stringResource(R.string.preference_breezyweather_integration)
    ) {
        if (isBreezyInstalled == false) {
            item {
                PreferenceCategory {
                    Banner(
                        text = stringResource(
                            R.string.preference_breezyweather_integration_description,
                            stringResource(R.string.app_name),
                        ),
                        icon = Icons.Rounded.Info,
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp),
                        primaryAction = {
                            Button(onClick = {
                                viewModel.downloadBreezyApp(activity)
                            }) {
                                Text(stringResource(R.string.action_install))
                            }
                        }
                    )
                }
            }
        }
        if (isBreezyInstalled == true) {
            item {
                PreferenceCategory {
                    Banner(
                        text = stringResource(
                            R.string.preference_breezyweather_integration_instructions,
                            stringResource(R.string.app_name),
                        ),
                        icon = Icons.Rounded.Info,
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp),
                    )
                    Preference(
                        title = stringResource(R.string.preference_breezyweather_integration),
                        summary = stringResource(
                            if (isWeatherProvider == true) R.string.plugin_weather_provider_enabled
                            else R.string.plugin_weather_provider_enable
                        ),
                        enabled = isWeatherProvider == false,
                        onClick = {
                            viewModel.setBreezyAsWeatherProvider()
                        }
                    )
                    Preference(
                        title = stringResource(R.string.preference_launch_breezyweather_app),
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        onClick = {
                            viewModel.launchBreezyApp(activity)
                        }
                    )
                }
            }
        }
    }
}