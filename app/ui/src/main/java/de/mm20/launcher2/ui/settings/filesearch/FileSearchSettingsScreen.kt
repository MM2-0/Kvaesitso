package de.mm20.launcher2.ui.settings.filesearch

import android.app.PendingIntent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.accounts.AccountType
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.ktx.sendWithBackgroundPermission
import de.mm20.launcher2.plugin.PluginState
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.GuardedPreference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference

@Composable
fun FileSearchSettingsScreen() {
    val viewModel: FileSearchSettingsScreenVM = viewModel()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(null) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.onResume()
        }
    }

    val plugins by viewModel.availablePlugins.collectAsStateWithLifecycle(
        emptyList(),
        minActiveState = Lifecycle.State.RESUMED,
    )
    val enabledPlugins by viewModel.enabledPlugins.collectAsStateWithLifecycle(null)

    val loading by viewModel.loading
    PreferenceScreen(title = stringResource(R.string.preference_search_files)) {
        if (loading == true) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }
            return@PreferenceScreen
        }
        item {
            PreferenceCategory {
                val localFiles by viewModel.localFiles.collectAsState()
                val hasFilePermission by viewModel.hasFilePermission.collectAsState()
                GuardedPreference(
                    locked = hasFilePermission == false,
                    onUnlock = {
                        viewModel.requestFilePermission(context as AppCompatActivity)
                    },
                    description = stringResource(
                        if (isAtLeastApiLevel(29)) R.string.missing_permission_file_search_settings_android10 else R.string.missing_permission_file_search_settings
                    ),
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.preference_search_localfiles),
                        summary = stringResource(R.string.preference_search_localfiles_summary),
                        value = localFiles == true && hasFilePermission == true,
                        onValueChanged = {
                            viewModel.setLocalFiles(it)
                        },
                        enabled = hasFilePermission == true
                    )
                }

                val nextcloud by viewModel.nextcloud.collectAsState()
                val nextcloudAccount by viewModel.nextcloudAccount
                GuardedPreference(
                    locked = nextcloudAccount == null,
                    onUnlock = {
                        viewModel.login(context as AppCompatActivity, AccountType.Nextcloud)
                    },
                    icon = R.drawable.account_box_24px,
                    description = stringResource(R.string.no_account_nextcloud),
                    unlockLabel = stringResource(R.string.connect_account),
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.preference_search_nextcloud),
                        summary = nextcloudAccount?.let {
                            stringResource(R.string.preference_search_cloud_summary, it.userName)
                        } ?: stringResource(R.string.preference_summary_not_logged_in),
                        value = nextcloud == true && nextcloudAccount != null,
                        onValueChanged = {
                            viewModel.setNextcloud(it)
                        },
                        enabled = nextcloudAccount != null
                    )
                }

                val owncloud by viewModel.owncloud.collectAsState()
                val owncloudAccount by viewModel.owncloudAccount
                GuardedPreference(
                    locked = owncloudAccount == null,
                    onUnlock = {
                        viewModel.login(context as AppCompatActivity, AccountType.Owncloud)
                    },
                    icon = R.drawable.account_box_24px,
                    description = stringResource(R.string.no_account_owncloud),
                    unlockLabel = stringResource(R.string.connect_account),
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.preference_search_owncloud),
                        summary = owncloudAccount?.let {
                            stringResource(R.string.preference_search_cloud_summary, it.userName)
                        } ?: stringResource(R.string.preference_summary_not_logged_in),
                        value = owncloud == true && owncloudAccount != null,
                        onValueChanged = {
                            viewModel.setOwncloud(it)
                        },
                        enabled = owncloudAccount != null
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
                        icon = R.drawable.error_24px,
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
    }
}