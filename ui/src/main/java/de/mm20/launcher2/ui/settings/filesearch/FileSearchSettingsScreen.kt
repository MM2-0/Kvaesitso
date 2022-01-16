package de.mm20.launcher2.ui.settings.filesearch

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.accounts.AccountType
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.MissingPermissionBanner
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
    val loading by viewModel.loading.observeAsState()
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
                val localFiles by viewModel.localFiles.observeAsState()
                val hasFilePermission by viewModel.hasFilePermission.observeAsState()
                AnimatedVisibility(hasFilePermission == false) {
                    MissingPermissionBanner(
                        text = stringResource(
                            if (isAtLeastApiLevel(29)) R.string.missing_permission_file_search_android10 else R.string.missing_permission_file_search
                        ), onClick = {
                            viewModel.requestFilePermission(context as AppCompatActivity)
                        },
                        modifier = Modifier.padding(16.dp)
                    )
                }
                SwitchPreference(
                    title = stringResource(R.string.preference_search_localfiles),
                    summary = stringResource(R.string.preference_search_localfiles_summary),
                    value = localFiles == true && hasFilePermission == true,
                    onValueChanged = {
                        viewModel.setLocalFiles(it)
                    },
                    enabled = hasFilePermission == true
                )

                val nextcloud by viewModel.nextcloud.observeAsState()
                val nextcloudAccount by viewModel.nextcloudAccount.observeAsState()
                AnimatedVisibility(nextcloudAccount == null) {
                    Banner(
                        text = stringResource(R.string.no_account_nextcloud),
                        icon = Icons.Rounded.AccountBox,
                        primaryAction = {
                            TextButton(onClick = {
                                viewModel.login(
                                    context as AppCompatActivity,
                                    AccountType.Nextcloud
                                )
                            }) {
                                Text(
                                    stringResource(R.string.connect_account),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        },
                        modifier = Modifier.padding(16.dp)
                    )
                }
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

                val owncloud by viewModel.owncloud.observeAsState()
                val owncloudAccount by viewModel.owncloudAccount.observeAsState()
                AnimatedVisibility(owncloudAccount == null) {
                    Banner(
                        text = stringResource(R.string.no_account_owncloud),
                        icon = Icons.Rounded.AccountBox,
                        primaryAction = {
                            TextButton(onClick = {
                                viewModel.login(
                                    context as AppCompatActivity,
                                    AccountType.Owncloud
                                )
                            }) {
                                Text(
                                    stringResource(R.string.connect_account),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        },
                        modifier = Modifier.padding(16.dp)
                    )
                }
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

                val onedrive by viewModel.onedrive.observeAsState()
                val microsoftAccount by viewModel.microsoftAccount.observeAsState()
                AnimatedVisibility(microsoftAccount == null) {
                    Banner(
                        text = stringResource(R.string.no_account_microsoft),
                        icon = Icons.Rounded.AccountBox,
                        primaryAction = {
                            TextButton(onClick = {
                                viewModel.login(
                                    context as AppCompatActivity,
                                    AccountType.Microsoft
                                )
                            }) {
                                Text(
                                    stringResource(R.string.connect_account),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        },
                        modifier = Modifier.padding(16.dp)
                    )
                }
                SwitchPreference(
                    title = stringResource(R.string.preference_search_onedrive),
                    summary = microsoftAccount?.let {
                        stringResource(R.string.preference_search_onedrive_summary, it.userName)
                    } ?: stringResource(R.string.preference_summary_not_logged_in),
                    value = onedrive == true && microsoftAccount != null,
                    onValueChanged = {
                        viewModel.setOneDrive(it)
                    },
                    enabled = microsoftAccount != null
                )

                val gdrive by viewModel.gdrive.observeAsState()
                val googleAccount by viewModel.googleAccount.observeAsState()
                AnimatedVisibility(googleAccount == null) {
                    Banner(
                        text = stringResource(R.string.no_account_google),
                        icon = Icons.Rounded.AccountBox,
                        primaryAction = {
                            TextButton(onClick = {
                                viewModel.login(
                                    context as AppCompatActivity,
                                    AccountType.Google
                                )
                            }) {
                                Text(
                                    stringResource(R.string.connect_account),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        },
                        modifier = Modifier.padding(16.dp)
                    )
                }
                SwitchPreference(
                    title = stringResource(R.string.preference_search_gdrive),
                    summary = googleAccount?.let {
                        stringResource(R.string.preference_search_gdrive_summary, it.userName)
                    } ?: stringResource(R.string.preference_summary_not_logged_in),
                    value = gdrive == true && googleAccount != null,
                    onValueChanged = {
                        viewModel.setGdrive(it)
                    },
                    enabled = googleAccount != null
                )
            }
        }
    }
}