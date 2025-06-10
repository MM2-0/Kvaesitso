package de.mm20.launcher2.ui.settings.contacts

import android.app.PendingIntent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import de.mm20.launcher2.ui.component.preferences.GuardedPreference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference

@Composable
fun ContactsSettingsScreen() {
    val viewModel: ContactsSettingsScreenVM = viewModel()
    val context = LocalContext.current

    val hasContactsPermission by viewModel.hasContactsPermission.collectAsStateWithLifecycle(null)
    val hasCallPermission by viewModel.hasCallPermission.collectAsStateWithLifecycle(null)
    val plugins by viewModel.availablePlugins.collectAsStateWithLifecycle(
        emptyList(),
        minActiveState = Lifecycle.State.RESUMED
    )
    val enabledProviders by viewModel.enabledProviders.collectAsState(emptySet())
    val callOnTap by viewModel.callOnTap.collectAsStateWithLifecycle(null)

    PreferenceScreen(
        title = stringResource(R.string.preference_search_contacts)
    ) {
        item {
            PreferenceCategory {
                AnimatedVisibility(hasContactsPermission == false) {
                    MissingPermissionBanner(
                        text = stringResource(R.string.missing_permission_contact_search_settings),
                        onClick = {
                            viewModel.requestContactsPermission(context as AppCompatActivity)
                        },
                        modifier = Modifier.padding(16.dp)
                    )
                }
                GuardedPreference(
                    locked = hasContactsPermission == false,
                    onUnlock = {
                        viewModel.requestContactsPermission(context as AppCompatActivity)
                    },
                    description = stringResource(R.string.missing_permission_contact_search_settings),
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.preference_search_contacts),
                        summary = stringResource(R.string.preference_search_contacts_summary),
                        icon = Icons.Rounded.Person,
                        value = enabledProviders.contains("local"),
                        onValueChanged = {
                            viewModel.setProviderEnabled("local", it)
                        }
                    )
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
                        icon = Icons.Rounded.ErrorOutline,
                        unlockLabel = stringResource(id = R.string.plugin_action_setup),
                    ) {
                        SwitchPreference(
                            title = plugin.plugin.label,
                            enabled = state is PluginState.Ready,
                            summary = (state as? PluginState.SetupRequired)?.message
                                ?: (state as? PluginState.Ready)?.text
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
        item {
            PreferenceCategory {
                GuardedPreference(
                    locked = hasCallPermission == false,
                    onUnlock = {
                        viewModel.requestCallPermission(context as AppCompatActivity)
                    },
                    description = stringResource(R.string.missing_permission_call_contacts_settings),
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.preference_contacts_call_on_tap),
                        summary = stringResource(R.string.preference_contacts_call_on_tap_summary),
                        icon = Icons.Rounded.Call,
                        value = callOnTap == true && hasCallPermission == true,
                        onValueChanged = {
                            viewModel.setCallOnTap(it)
                        },
                        enabled = hasCallPermission == true
                    )
                }
            }
        }
    }

}