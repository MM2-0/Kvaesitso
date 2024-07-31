package de.mm20.launcher2.ui.settings.calendarsearch

import android.app.PendingIntent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.ktx.sendWithBackgroundPermission
import de.mm20.launcher2.plugin.PluginState
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference

@Composable
fun CalendarSearchSettingsScreen() {
    val viewModel: CalendarSearchSettingsScreenVM = viewModel()
    val context = LocalContext.current

    val hasCalendarPermission by viewModel.hasCalendarPermission.collectAsState(null)
    val plugins by viewModel.availablePlugins.collectAsState(emptyList())
    val enabledProviders by viewModel.enabledProviders.collectAsState(emptySet())

    PreferenceScreen(title = stringResource(R.string.preference_search_calendar)) {
        item {
            AnimatedVisibility(hasCalendarPermission == false) {
                MissingPermissionBanner(
                    text = stringResource(R.string.missing_permission_calendar_search_settings),
                    onClick = {
                        viewModel.requestCalendarPermission(context as AppCompatActivity)
                    },
                    modifier = Modifier.padding(16.dp)
                )
            }
            SwitchPreference(
                title = stringResource(R.string.preference_search_calendar),
                summary = stringResource(R.string.preference_search_calendar_summary),
                value = enabledProviders.contains("local") && hasCalendarPermission == true,
                onValueChanged = {
                    viewModel.setProviderEnabled("local", it)
                },
                enabled = hasCalendarPermission == true
            )
            for (plugin in plugins) {
                val state = plugin.state
                if (state is PluginState.SetupRequired) {
                    Banner(
                        modifier = Modifier.padding(16.dp),
                        text = state.message ?: stringResource(id = R.string.plugin_state_setup_required),
                        icon = Icons.Rounded.ErrorOutline,
                        primaryAction = {
                            TextButton(onClick = {
                                try {
                                    state.setupActivity.sendWithBackgroundPermission(context)
                                } catch (e: PendingIntent.CanceledException) {
                                    CrashReporter.logException(e)
                                }
                            }) {
                                Text(stringResource(id = R.string.plugin_action_setup))
                            }
                        }
                    )
                }
                SwitchPreference(
                    title = plugin.plugin.label,
                    enabled = state is PluginState.Ready,
                    summary = (state as? PluginState.Ready)?.text
                        ?: (state as? PluginState.SetupRequired)?.message
                        ?: plugin.plugin.description,
                    value = enabledProviders.contains(plugin.plugin.authority) && state is PluginState.Ready,
                    onValueChanged = {
                        viewModel.setProviderEnabled(plugin.plugin.authority, it)
                    },
                )
            }
        }
    }
}