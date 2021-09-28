package de.mm20.launcher2.ui.screens.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import de.mm20.launcher2.gservices.GoogleAccount
import de.mm20.launcher2.gservices.GoogleApiHelper
import de.mm20.launcher2.msservices.MicrosoftGraphApiHelper
import de.mm20.launcher2.msservices.MsUser
import de.mm20.launcher2.nextcloud.NcUser
import de.mm20.launcher2.nextcloud.NextcloudApiHelper
import de.mm20.launcher2.owncloud.OcUser
import de.mm20.launcher2.owncloud.OwncloudClient
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import kotlinx.coroutines.launch

@Composable
fun SettingsAccountScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    PreferenceScreen(title = stringResource(id = R.string.preference_screen_services)) {
        item {
            PreferenceCategory(title = stringResource(id = R.string.preference_category_services_nextcloud)) {
                val client = remember { NextcloudApiHelper(context) }
                var account by remember { mutableStateOf<NcUser?>(null) }
                LaunchedEffect(null) {
                    account = client.getLoggedInUser()
                }

                val launcher =
                    rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) {
                        scope.launch {
                            account = client.getLoggedInUser()
                        }
                    }

                Preference(
                    title = account?.let {
                        stringResource(id = R.string.preference_signin_logout)
                    } ?: stringResource(id = R.string.preference_nextcloud_signin),
                    summary = account?.let {
                        stringResource(
                            id = R.string.preference_signin_user,
                            it.displayName
                        )
                    } ?: stringResource(id = R.string.preference_nextcloud_signin_summary),
                    onClick = {
                        if (account == null) {
                            launcher.launch(client.getLoginIntent())
                        } else {
                            scope.launch {
                                client.logout()
                                account = null
                            }
                        }
                    }
                )
            }
            PreferenceCategory(title = stringResource(id = R.string.preference_category_services_owncloud)) {
                val client = remember { OwncloudClient(context) }
                var account by remember { mutableStateOf<OcUser?>(null) }
                LaunchedEffect(null) {
                    account = client.getLoggedInUser()
                }

                val launcher =
                    rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) {
                        scope.launch {
                            account = client.getLoggedInUser()
                        }
                    }

                Preference(
                    title = account?.let {
                        stringResource(id = R.string.preference_signin_logout)
                    } ?: stringResource(id = R.string.preference_owncloud_signin),
                    summary = account?.let {
                        stringResource(
                            id = R.string.preference_signin_user,
                            it.displayName
                        )
                    } ?: stringResource(id = R.string.preference_owncloud_signin_summary),
                    onClick = {
                        if (account == null) {
                            launcher.launch(client.getLoginIntent())
                        } else {
                            scope.launch {
                                client.logout()
                                account = null
                            }
                        }
                    }
                )
            }
            PreferenceCategory(title = stringResource(id = R.string.preference_category_services_google)) {
                val client = remember { GoogleApiHelper.getInstance(context) }
                var account by remember { mutableStateOf<GoogleAccount?>(null) }
                LaunchedEffect(null) {
                    account = client.getAccount()
                }
                Preference(
                    title = account?.let {
                        stringResource(id = R.string.preference_signin_logout)
                    } ?: stringResource(id = R.string.preference_google_signin),
                    summary = if (client.isAvailable()) {
                        account?.let {
                            stringResource(
                                id = R.string.preference_signin_user,
                                it.name
                            )
                        } ?: stringResource(id = R.string.preference_google_signin_summary)
                    } else {
                        stringResource(id = R.string.feature_not_available, stringResource(R.string.app_name))
                    },
                    onClick = {
                        if (account == null) {
                            scope.launch {
                                client.login(context as Activity)
                                account = client.getAccount()
                            }
                        } else {
                            client.logout()
                            account = null
                        }
                    },
                    enabled = client.isAvailable()
                )
            }
            PreferenceCategory(title = stringResource(id = R.string.preference_category_services_microsoft)) {
                val client = remember { MicrosoftGraphApiHelper.getInstance(context) }
                var account by remember { mutableStateOf<MsUser?>(null) }
                LaunchedEffect(null) {
                    account = client.getUser()
                }
                Preference(
                    title = account?.let {
                        stringResource(id = R.string.preference_signin_logout)
                    } ?: stringResource(id = R.string.preference_ms_signin),
                    summary = if (client.isAvailable()) {
                        account?.let {
                            stringResource(
                                id = R.string.preference_signin_user,
                                it.name
                            )
                        } ?: stringResource(id = R.string.preference_ms_signin_summary)
                    } else {
                        stringResource(id = R.string.feature_not_available, stringResource(R.string.app_name))
                    },
                    onClick = {
                        if (account == null) {
                            scope.launch {
                                client.login(context as Activity)
                                account = client.getUser()
                            }
                        } else {
                            scope.launch {
                                client.logout()
                                account = null
                            }
                        }
                    },
                    enabled = client.isAvailable()
                )
            }
        }
    }
}