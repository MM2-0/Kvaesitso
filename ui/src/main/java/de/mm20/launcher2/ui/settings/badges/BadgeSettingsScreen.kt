package de.mm20.launcher2.ui.settings.badges

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference

@Composable
fun BadgeSettingsScreen() {
    val viewModel: BadgeSettingsScreenVM = viewModel()
    val context = LocalContext.current
    PreferenceScreen(title = stringResource(R.string.preference_screen_badges)) {
        item {
            PreferenceCategory {
                val notifications by viewModel.notifications.observeAsState()
                val hasNotificationsPermission by viewModel.hasNotificationsPermission.observeAsState()
                AnimatedVisibility(hasNotificationsPermission == false) {
                    MissingPermissionBanner(
                        text = stringResource(R.string.missing_permission_notification_badges),
                        onClick = {
                            viewModel.requestNotificationsPermission(context as AppCompatActivity)
                        },
                        modifier = Modifier.padding(16.dp)
                    )
                }
                SwitchPreference(
                    title = stringResource(R.string.preference_notification_badges),
                    summary = stringResource(R.string.preference_notification_badges_summary),
                    enabled = hasNotificationsPermission != false,
                    value = notifications == true && hasNotificationsPermission == true,
                    onValueChanged = {
                        viewModel.setNotifications(it)
                    }
                )
                val cloudFiles by viewModel.cloudFiles.observeAsState()
                SwitchPreference(
                    title = stringResource(R.string.preference_cloud_badges),
                    summary = stringResource(R.string.preference_cloud_badges_summary),
                    value = cloudFiles == true,
                    onValueChanged = {
                        viewModel.setCloudFiles(it)
                    }
                )
                val suspendedApps by viewModel.suspendedApps.observeAsState()
                SwitchPreference(
                    title = stringResource(R.string.preference_suspended_badges),
                    summary = stringResource(R.string.preference_suspended_badges_summary),
                    value = suspendedApps == true,
                    onValueChanged = {
                        viewModel.setSuspendedApps(it)
                    }
                )
                val shortcuts by viewModel.shortcuts.observeAsState()
                SwitchPreference(
                    title = stringResource(R.string.preference_shortcut_badges),
                    summary = stringResource(R.string.preference_shortcut_badges_summary),
                    value = shortcuts == true,
                    onValueChanged = {
                        viewModel.setShortcuts(it)
                    }
                )
            }
        }
    }
}