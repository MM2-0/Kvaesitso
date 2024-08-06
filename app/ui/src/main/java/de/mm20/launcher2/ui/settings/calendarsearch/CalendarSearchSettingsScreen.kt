package de.mm20.launcher2.ui.settings.calendarsearch

import android.app.PendingIntent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.ktx.sendWithBackgroundPermission
import de.mm20.launcher2.plugin.PluginState
import de.mm20.launcher2.themes.atTone
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import de.mm20.launcher2.ui.component.preferences.CheckboxPreference
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.PreferenceWithSwitch
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.locals.LocalDarkTheme

@Composable
fun CalendarSearchSettingsScreen() {
    val viewModel: CalendarSearchSettingsScreenVM = viewModel()
    val context = LocalContext.current

    val hasCalendarPermission by viewModel.hasCalendarPermission.collectAsState(null)
    val plugins by viewModel.availablePlugins.collectAsState(emptyList())
    val enabledProviders by viewModel.enabledProviders.collectAsState(emptySet())

    val calendarLists by viewModel.calendarLists.collectAsStateWithLifecycle(
        null,
        minActiveState = Lifecycle.State.RESUMED
    )
    val excludedCalendars by viewModel.excludedCalendars.collectAsState(emptyList())

    var showDialogForProvider by remember { mutableStateOf<String?>(null) }

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
            val selectedCalendars = remember(excludedCalendars, calendarLists) {
                calendarLists?.count { it.providerId == "local" }
                    ?.minus(excludedCalendars.count {
                        it.startsWith("local")
                    })
            }
            PreferenceWithSwitch(
                title = stringResource(R.string.preference_search_calendar),
                summary = if (selectedCalendars != null && calendarLists != null) "$selectedCalendars lists selected"
                else stringResource(R.string.preference_search_calendar_summary),
                switchValue = enabledProviders.contains("local") && hasCalendarPermission == true,
                onSwitchChanged = {
                    viewModel.setProviderEnabled("local", it)
                },
                enabled = hasCalendarPermission == true,
                onClick = {
                    showDialogForProvider = "local"
                }
            )
            for (plugin in plugins) {
                val state = plugin.state
                if (state is PluginState.SetupRequired) {
                    Banner(
                        modifier = Modifier.padding(16.dp),
                        text = state.message
                            ?: stringResource(id = R.string.plugin_state_setup_required),
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
                val selectedCalendars = remember(excludedCalendars, calendarLists) {
                    calendarLists?.count { it.providerId == plugin.plugin.authority }
                        ?.minus(excludedCalendars.count {
                            it.startsWith(
                                plugin.plugin.authority
                            )
                        })
                }
                PreferenceWithSwitch(
                    title = plugin.plugin.label,
                    enabled = state is PluginState.Ready,
                    summary = (state as? PluginState.SetupRequired)?.message
                        ?: if (selectedCalendars != null && calendarLists != null) "$selectedCalendars lists selected"
                                else (state as? PluginState.Ready)?.text ?: plugin.plugin.description,
                    switchValue = enabledProviders.contains(plugin.plugin.authority) && state is PluginState.Ready,
                    onSwitchChanged = {
                        viewModel.setProviderEnabled(plugin.plugin.authority, it)
                    },
                    onClick = {
                        showDialogForProvider = plugin.plugin.authority
                    }
                )
            }
        }
    }

    val dialogCalendarLists by remember {
        derivedStateOf {
            if (showDialogForProvider == null) null
            else calendarLists?.filter { it.providerId == showDialogForProvider }
        }
    }

    if (showDialogForProvider != null && dialogCalendarLists != null) {
        BasicAlertDialog(
            onDismissRequest = {
                showDialogForProvider = null
            },
        ) {
            Surface(
                modifier = Modifier
                    .wrapContentWidth()
                    .wrapContentHeight(),
                shape = MaterialTheme.shapes.large,
                tonalElevation = AlertDialogDefaults.TonalElevation
            ) {
                LazyColumn {
                    items(dialogCalendarLists ?: emptyList()) {
                        CheckboxPreference(
                            title = it.name,
                            summary = it.owner,
                            iconPadding = false,
                            value = it.id !in excludedCalendars,
                            onValueChanged = { value ->
                                viewModel.setCalendarExcluded(it.id, !value)
                            },
                            checkboxColors = CheckboxDefaults.colors(
                                checkedColor = if (it.color == 0) MaterialTheme.colorScheme.primary
                                else Color(
                                    it.color.atTone(if (LocalDarkTheme.current) 80 else 40)
                                ),
                                checkmarkColor = if (it.color == 0) MaterialTheme.colorScheme.onPrimary
                                else Color(
                                    it.color.atTone(if (LocalDarkTheme.current) 20 else 100)
                                )
                            )
                        )
                    }
                }
            }
        }
    }
}
