package de.mm20.launcher2.ui.settings.tasks

import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
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
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.preferences.GuardedPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.PreferenceWithSwitch
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.settings.calendarsearch.CalendarProviderSettingsRoute
import kotlinx.serialization.Serializable

@Serializable
data object TasksIntegrationSettingsRoute: NavKey

@Composable
fun TasksIntegrationSettingsScreen() {
    val viewModel: TasksSettingsScreenVM = viewModel()
    val activity = LocalActivity.current
    val backStack = LocalBackStack.current

    val isTasksInstalled by viewModel.isTasksAppInstalled.collectAsStateWithLifecycle(null)
    val hasTasksPermission by viewModel.hasTasksPermission.collectAsStateWithLifecycle(null)
    val isTasksSearchEnabled by viewModel.isTasksSearchEnabled.collectAsStateWithLifecycle(false)

    PreferenceScreen(
        title = stringResource(R.string.preference_tasks_integration)
    ) {
        if (isTasksInstalled == false) {
            item {
                PreferenceCategory {
                    Banner(
                        text = stringResource(
                            R.string.preference_tasks_integration_description,
                            stringResource(R.string.app_name)
                        ),
                        icon = R.drawable.info_24px,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(16.dp),
                        primaryAction = {
                            Button(onClick = {
                                viewModel.downloadTasksApp(activity as AppCompatActivity)
                            }) {
                                Text(stringResource(R.string.action_install))
                            }
                        }
                    )
                }
            }
        }
        if (isTasksInstalled == true) {
            item {
                PreferenceCategory {
                    if (hasTasksPermission == true) {
                        Banner(
                            text = stringResource(R.string.preference_tasks_integration_ready),
                            icon = R.drawable.check_circle_24px,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.shapes.extraSmall
                                )
                                .padding(16.dp),
                        )
                    }
                    GuardedPreference(
                        locked = hasTasksPermission == false,
                        onUnlock = {
                            viewModel.requestTasksPermission(activity as AppCompatActivity)
                        },
                        description = stringResource(R.string.missing_permission_tasks_integration),
                    ) {
                        PreferenceWithSwitch(
                            icon = R.drawable.task_alt_24px,
                            title = stringResource(R.string.preference_search_tasks),
                            summary = stringResource(R.string.preference_search_tasks_summary),
                            switchValue = isTasksSearchEnabled == true && hasTasksPermission == true,
                            onSwitchChanged = {
                                viewModel.setTasksSearchEnabled(it)
                            },
                            enabled = hasTasksPermission == true,
                            onClick = {
                                backStack.add(CalendarProviderSettingsRoute(providerId = "tasks.org"))
                            }
                        )
                    }
                    Preference(
                        title = stringResource(R.string.preference_launch_tasks_app),
                        icon = R.drawable.open_in_new_24px,
                        onClick = {
                            viewModel.launchTasksApp(activity as AppCompatActivity)
                        }
                    )
                }
            }
        }
    }
}