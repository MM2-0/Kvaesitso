package de.mm20.launcher2.ui.settings.calendarsearch

import android.app.PendingIntent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.ktx.sendWithBackgroundPermission
import de.mm20.launcher2.plugin.PluginState
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.GuardedPreference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.PreferenceWithSwitch
import de.mm20.launcher2.ui.locals.LocalNavController

@Composable
fun CalendarSearchSettingsScreen() {
    val viewModel: CalendarSearchSettingsScreenVM = viewModel()
    val context = LocalContext.current
    val navController = LocalNavController.current

    val hasCalendarPermission by viewModel.hasCalendarPermission.collectAsState(null)
    val hasTasksPermission by viewModel.hasTasksPermission.collectAsState(null)
    val isTasksAppInstalled by viewModel.isTasksAppInstalled.collectAsStateWithLifecycle(false)
    val plugins by viewModel.availablePlugins.collectAsStateWithLifecycle(
        emptyList(),
        minActiveState = Lifecycle.State.RESUMED
    )
    val enabledProviders by viewModel.enabledProviders.collectAsState(emptySet())

    PreferenceScreen(title = stringResource(R.string.preference_search_calendar)) {
        item {
            PreferenceCategory {
                GuardedPreference(
                    locked = hasCalendarPermission == false,
                    onUnlock = {
                        viewModel.requestCalendarPermission(context as AppCompatActivity)
                    },
                    description = stringResource(R.string.missing_permission_calendar_search_settings),
                ) {
                    PreferenceWithSwitch(
                        title = stringResource(R.string.preference_search_calendar),
                        summary = stringResource(R.string.preference_search_local_calendar_summary),
                        switchValue = enabledProviders.contains("local") && hasCalendarPermission == true,
                        onSwitchChanged = {
                            viewModel.setProviderEnabled("local", it)
                        },
                        enabled = hasCalendarPermission == true,
                        onClick = {
                            navController?.navigate("settings/search/calendar/local")
                        }
                    )
                }
                if (isTasksAppInstalled) {
                    GuardedPreference(
                        locked = hasTasksPermission == false,
                        onUnlock = {
                            viewModel.requestTasksPermission(context as AppCompatActivity)
                        },
                        description = stringResource(R.string.missing_permission_tasks_search_settings),
                    ) {
                        PreferenceWithSwitch(
                            title = stringResource(R.string.preference_search_tasks),
                            summary = stringResource(R.string.preference_search_tasks_summary),
                            switchValue = enabledProviders.contains("tasks.org") && hasTasksPermission == true,
                            onSwitchChanged = {
                                viewModel.setProviderEnabled("tasks.org", it)
                            },
                            enabled = hasTasksPermission == true,
                            onClick = {
                                navController?.navigate("settings/search/calendar/tasks.org")
                            }
                        )
                    }
                }
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
                        icon = R.drawable.error_24px,
                        unlockLabel = stringResource(id = R.string.plugin_action_setup),
                    ) {
                        PreferenceWithSwitch(
                            title = plugin.plugin.label,
                            enabled = state is PluginState.Ready,
                            summary = (state as? PluginState.SetupRequired)?.message
                                ?: (state as? PluginState.Ready)?.text
                                ?: plugin.plugin.description,
                            switchValue = enabledProviders.contains(plugin.plugin.authority) && state is PluginState.Ready,
                            onSwitchChanged = {
                                viewModel.setProviderEnabled(plugin.plugin.authority, it)
                            },
                            onClick = {
                                navController?.navigate("settings/search/calendar/${plugin.plugin.authority}")
                            }
                        )
                    }
                }
            }
        }
    }
}
