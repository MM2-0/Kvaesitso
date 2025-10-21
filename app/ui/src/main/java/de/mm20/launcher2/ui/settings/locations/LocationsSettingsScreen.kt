package de.mm20.launcher2.ui.settings.locations

import android.app.PendingIntent
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.ktx.sendWithBackgroundPermission
import de.mm20.launcher2.plugin.PluginState
import de.mm20.launcher2.preferences.search.LocationSearchSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.GuardedPreference
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.PreferenceWithSwitch
import de.mm20.launcher2.ui.component.preferences.SliderPreference
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.component.preferences.TextPreference
import de.mm20.launcher2.ui.ktx.metersToLocalizedString
import de.mm20.launcher2.ui.locals.LocalNavController

@Composable
fun LocationsSettingsScreen() {
    val viewModel: LocationsSettingsScreenVM = viewModel()

    val navController = LocalNavController.current
    val context = LocalContext.current

    val osmLocations by viewModel.osmLocations.collectAsState()
    val imperialUnits by viewModel.imperialUnits.collectAsState()
    val radius by viewModel.radius.collectAsState()
    val showMap by viewModel.showMap.collectAsState()
    val themeMap by viewModel.themeMap.collectAsState()
    val customTileServerUrl by viewModel.customTileServerUrl.collectAsState()

    val plugins by viewModel.availablePlugins.collectAsStateWithLifecycle(
        initialValue = emptyList(),
        minActiveState = Lifecycle.State.RESUMED
    )
    val enabledPlugins by viewModel.enabledPlugins.collectAsStateWithLifecycle(initialValue = null)

    val anyLocationProviderEnabled = osmLocations ?: false || enabledPlugins.isNullOrEmpty().not()


    PreferenceScreen(title = stringResource(R.string.preference_search_locations)) {
        item {
            PreferenceCategory {
                PreferenceWithSwitch(
                    title = stringResource(R.string.preference_search_osm_locations),
                    summary = stringResource(R.string.preference_search_osm_locations_summary),
                    switchValue = osmLocations == true,
                    onSwitchChanged = {
                        viewModel.setOsmLocations(it)
                    },
                    onClick = {
                        navController?.navigate("settings/search/locations/osm")
                    }
                )
                for (plugin in plugins) {
                    val state = plugin.state
                    GuardedPreference(
                        locked = state is PluginState.SetupRequired,
                        onUnlock = {
                            try {
                                (state as PluginState.SetupRequired).setupActivity.sendWithBackgroundPermission(
                                    context
                                )
                            } catch (e: PendingIntent.CanceledException) {
                                CrashReporter.logException(e)
                            }
                        },
                        description = (state as? PluginState.SetupRequired)?.message
                            ?: stringResource(id = R.string.plugin_state_setup_required),
                        icon = Icons.Rounded.ErrorOutline,
                        unlockLabel = stringResource(id = R.string.plugin_action_setup),
                    ) {
                        SwitchPreference(
                            title = plugin.plugin.label,
                            enabled = enabledPlugins != null && state is PluginState.Ready,
                            summary = (state as? PluginState.Ready)?.text
                                ?: (state as? PluginState.SetupRequired)?.message
                                ?: plugin.plugin.description,
                            value = enabledPlugins?.contains(plugin.plugin.authority) == true && state is PluginState.Ready,
                            onValueChanged = {
                                viewModel.setPluginEnabled(plugin.plugin.authority, it)
                            },
                        )
                    }
                }
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
                    enabled = anyLocationProviderEnabled,
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
                    enabled = anyLocationProviderEnabled,
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
            }
        }
        item {
            PreferenceCategory {
                SwitchPreference(
                    title = stringResource(R.string.preference_search_locations_show_map),
                    summary = stringResource(R.string.preference_search_locations_show_map_summary),
                    enabled = anyLocationProviderEnabled,
                    value = showMap == true,
                    onValueChanged = {
                        viewModel.setShowMap(it)
                    }
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_search_locations_theme_map),
                    summary = stringResource(R.string.preference_search_locations_theme_map_summary),
                    value = themeMap == true,
                    enabled = anyLocationProviderEnabled && showMap == true,
                    onValueChanged = {
                        viewModel.setThemeMap(it)
                    }
                )
            }

        }
        item {
            PreferenceCategory(stringResource(R.string.preference_category_advanced)) {
                TextPreference(
                    title = stringResource(R.string.preference_search_location_custom_tile_server_url),
                    value = customTileServerUrl ?: "",
                    placeholder = LocationSearchSettings.DefaultTileServerUrl,
                    summary = customTileServerUrl?.takeIf { it.isNotBlank() }
                        ?: LocationSearchSettings.DefaultTileServerUrl,
                    onValueChanged = {
                        viewModel.setCustomTileServerUrl(it)
                    },
                    enabled = anyLocationProviderEnabled
                )
            }
        }
    }
}