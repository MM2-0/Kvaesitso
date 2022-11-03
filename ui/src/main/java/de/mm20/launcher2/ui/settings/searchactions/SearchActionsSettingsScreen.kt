package de.mm20.launcher2.ui.settings.searchactions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference

@Composable
fun SearchActionsSettingsScreen() {
    val viewModel: SearchActionsSettingsScreenVM = viewModel()
    val settings by viewModel.searchActionSettings.observeAsState(
        Settings.SearchActionSettings.getDefaultInstance()
    )

    PreferenceScreen(stringResource(id = R.string.preference_screen_search_actions)) {
        item {
            PreferenceCategory {
                SwitchPreference(
                    icon = Icons.Rounded.Call,
                    title = stringResource(R.string.search_action_call),
                    value = settings.call,
                    onValueChanged = {
                        viewModel.updateSettings {
                            setCall(it)
                        }
                    },
                )
                SwitchPreference(
                    icon = Icons.Rounded.Sms,
                    title = stringResource(R.string.search_action_message),
                    value = settings.message,
                    onValueChanged = {
                        viewModel.updateSettings {
                            setMessage(it)
                        }
                    },
                )
                SwitchPreference(
                    icon = Icons.Rounded.Email,
                    title = stringResource(R.string.search_action_email),
                    value = settings.email,
                    onValueChanged = {
                        viewModel.updateSettings {
                            setEmail(it)
                        }
                    },
                )
                SwitchPreference(
                    icon = Icons.Rounded.Person,
                    title = stringResource(R.string.search_action_contact),
                    value = settings.contact,
                    onValueChanged = {
                        viewModel.updateSettings {
                            setContact(it)
                        }
                    },
                )
                SwitchPreference(
                    icon = Icons.Rounded.Alarm,
                    title = stringResource(R.string.search_action_alarm),
                    value = settings.setAlarm,
                    onValueChanged = {
                        viewModel.updateSettings {
                            setSetAlarm(it)
                        }
                    },
                )
                SwitchPreference(
                    icon = Icons.Rounded.Timer,
                    title = stringResource(R.string.search_action_timer),
                    value = settings.startTimer,
                    onValueChanged = {
                        viewModel.updateSettings {
                            setStartTimer(it)
                        }
                    },
                )
                SwitchPreference(
                    icon = Icons.Rounded.Event,
                    title = stringResource(R.string.search_action_event),
                    value = settings.scheduleEvent,
                    onValueChanged = {
                        viewModel.updateSettings {
                            setScheduleEvent(it)
                        }
                    },
                )
                SwitchPreference(
                    icon = Icons.Rounded.Language,
                    title = stringResource(R.string.search_action_open_url),
                    value = settings.openUrl,
                    onValueChanged = {
                        viewModel.updateSettings {
                            setOpenUrl(it)
                        }
                    },
                )
            }
        }
    }
}