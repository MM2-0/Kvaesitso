package de.mm20.launcher2.ui.settings.accounts

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.LinearProgressIndicator
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
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen

@Composable
fun AccountsSettingsScreen() {
    val viewModel: AccountsSettingsScreenVM = viewModel()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(null) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.onResume()
        }
    }

    val loading by viewModel.loading.observeAsState(true)

    PreferenceScreen(title = stringResource(R.string.preference_screen_services)) {
        if (loading) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }
            return@PreferenceScreen
        }
        item {
            PreferenceCategory(title = stringResource(R.string.preference_category_services_nextcloud)) {
                val account by viewModel.nextcloudUser.observeAsState()
                Preference(
                    title = if (account != null) {
                        stringResource(R.string.preference_signin_logout)
                    } else {
                        stringResource(R.string.preference_nextcloud_signin)
                    },
                    summary = account?.let {
                        stringResource(R.string.preference_signin_user, it.userName)
                    } ?: stringResource(R.string.preference_nextcloud_signin_summary),
                    onClick = {
                        if (account != null) {
                            viewModel.signOut(AccountType.Nextcloud)
                        } else {
                            viewModel.signIn(context as AppCompatActivity, AccountType.Nextcloud)
                        }
                    }
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(R.string.preference_category_services_owncloud)) {
                val account by viewModel.owncloudUser.observeAsState()
                Preference(
                    title = if (account != null) {
                        stringResource(R.string.preference_signin_logout)
                    } else {
                        stringResource(R.string.preference_owncloud_signin)
                    },
                    summary = account?.let {
                        stringResource(R.string.preference_signin_user, it.userName)
                    } ?: stringResource(R.string.preference_owncloud_signin_summary),
                    onClick = {
                        if (account != null) {
                            viewModel.signOut(AccountType.Owncloud)
                        } else {
                            viewModel.signIn(context as AppCompatActivity, AccountType.Owncloud)
                        }
                    }
                )
            }
        }
        if (viewModel.isMicrosoftAvailable) {
            item {
                PreferenceCategory(title = stringResource(R.string.preference_category_services_microsoft)) {
                    val account by viewModel.msUser.observeAsState()
                    Preference(
                        title = if (account != null) {
                            stringResource(R.string.preference_signin_logout)
                        } else {
                            stringResource(R.string.preference_ms_signin)
                        },
                        summary = account?.let {
                            stringResource(R.string.preference_signin_user, it.userName)
                        } ?: stringResource(R.string.preference_ms_signin_summary),
                        onClick = {
                            if (account != null) {
                                viewModel.signOut(AccountType.Microsoft)
                            } else {
                                viewModel.signIn(context as AppCompatActivity, AccountType.Microsoft)
                            }
                        }
                    )
                }
            }
        }
        if (viewModel.isGoogleAvailable) {
            item {
                PreferenceCategory(title = stringResource(R.string.preference_category_services_google)) {
                    val account by viewModel.googleUser.observeAsState()
                    Preference(
                        title = if (account != null) {
                            stringResource(R.string.preference_signin_logout)
                        } else {
                            stringResource(R.string.preference_google_signin)
                        },
                        summary = account?.let {
                            stringResource(R.string.preference_signin_user, it.userName)
                        } ?: stringResource(R.string.preference_google_signin_summary),
                        onClick = {
                            if (account != null) {
                                viewModel.signOut(AccountType.Google)
                            } else {
                                viewModel.signIn(context as AppCompatActivity, AccountType.Google)
                            }
                        }
                    )
                }
            }
        }
    }
}