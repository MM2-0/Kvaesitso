package de.mm20.launcher2.ui.screens.settings

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import de.mm20.launcher2.preferences.dataStore
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun SettingsBadgesScreen() {
    val dataStore = LocalContext.current.dataStore
    val scope = rememberCoroutineScope()
    PreferenceScreen(
        title = stringResource(id = R.string.preference_screen_badges)
    ) {
        item {
            PreferenceCategory {
                val notificationBadges by remember {
                    dataStore.data.map { it.badges.notificationBadges }
                }.collectAsState(initial = false)
                SwitchPreference(
                    title = stringResource(id = R.string.preference_notification_badges),
                    summary = stringResource(id = R.string.preference_notification_badges_summary),
                    value = notificationBadges,
                    onValueChanged = { newValue ->
                        scope.launch {
                            dataStore.updateData {
                                it.toBuilder()
                                    .setBadges(
                                        it.badges.toBuilder().setNotificationBadges(newValue)
                                    )
                                    .build()
                            }
                        }
                    }
                )
                val suspendedBadges by remember {
                    dataStore.data.map { it.badges.suspendBadges }
                }.collectAsState(initial = false)
                SwitchPreference(
                    title = stringResource(id = R.string.preference_suspended_badges),
                    summary = stringResource(id = R.string.preference_suspended_badges_summary),
                    value = suspendedBadges,
                    onValueChanged = { newValue ->
                        scope.launch {
                            dataStore.updateData {
                                it.toBuilder()
                                    .setBadges(
                                        it.badges.toBuilder().setSuspendBadges(newValue)
                                    )
                                    .build()
                            }
                        }
                    }
                )
                val cloudBadges by remember {
                    dataStore.data.map { it.badges.cloudBadges }
                }.collectAsState(initial = false)
                SwitchPreference(
                    title = stringResource(id = R.string.preference_cloud_badges),
                    summary = stringResource(id = R.string.preference_cloud_badges_summary),
                    value = cloudBadges,
                    onValueChanged = { newValue ->
                        scope.launch {
                            dataStore.updateData {
                                it.toBuilder()
                                    .setBadges(
                                        it.badges.toBuilder().setCloudBadges(newValue)
                                    )
                                    .build()
                            }
                        }
                    }
                )
                val shortcutBadges by remember {
                    dataStore.data.map { it.badges.shortcutBadges }
                }.collectAsState(initial = false)
                SwitchPreference(
                    title = stringResource(id = R.string.preference_shortcut_badges),
                    summary = stringResource(id = R.string.preference_shortcut_badges_summary),
                    value = shortcutBadges,
                    onValueChanged = { newValue ->
                        scope.launch {
                            dataStore.updateData {
                                it.toBuilder()
                                    .setBadges(
                                        it.badges.toBuilder().setShortcutBadges(newValue)
                                    )
                                    .build()
                            }
                        }
                    }
                )
            }
        }
    }
}