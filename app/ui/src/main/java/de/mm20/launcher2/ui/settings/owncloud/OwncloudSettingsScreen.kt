package de.mm20.launcher2.ui.settings.owncloud

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.serialization.Serializable

@Serializable
data object OwncloudSettingsRoute: NavKey

@Composable
fun OwncloudSettingsScreen() {

    val viewModel: OwncloudSettingsScreenVM = viewModel()
    val lifecycleOwner = LocalLifecycleOwner.current
    val backStack = LocalBackStack.current

    val owncloudUser by viewModel.owncloudUser
    val loading by viewModel.loading
    val searchFiles by viewModel.searchFiles.collectAsState(null)

    LaunchedEffect(null) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.onResume()
        }
    }
    PreferenceScreen(title = stringResource(I18nR.string.preference_owncloud)) {
        if (loading) return@PreferenceScreen

        if (owncloudUser != null) {
            item {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.medium)
                        .fillParentMaxWidth()
                        .padding(vertical = 64.dp)
                    ,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(72.dp)
                            .background(MaterialTheme.colorScheme.secondary, CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.onSecondaryContainer, CircleShape),
                    ) {
                        Text(
                            text = owncloudUser!!.userName.split(" ")
                                .mapNotNull { it.firstOrNull() }
                                .joinToString("").let {
                                    if (it.length >= 2) it.first().toString() + it.last().toString()
                                    else it
                                }
                            ,
                            color = MaterialTheme.colorScheme.onSecondary,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    }
                    Text(
                        modifier = Modifier.padding(top = 24.dp),
                        text = stringResource(I18nR.string.preference_signin_user, owncloudUser!!.userName),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    var confirmSignOut by remember { mutableStateOf(false) }
                    if (confirmSignOut) {
                        AlertDialog(
                            onDismissRequest = { confirmSignOut = false },
                            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.au4_confirms_oc_title))  } },
                            text = { Text(stringResource(I18nR.string.au4_confirms_oc_message)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    confirmSignOut = false
                                    viewModel.signOut()
                                }) { Text(stringResource(I18nR.string.au4_confirms_confirm)) }
                            },
                            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(I18nR.string.au4_confirms_cancel)) } },
                        )
                    }
                    Button(
                        modifier = Modifier.padding(top = 32.dp),
                        onClick = {
                            confirmSignOut = true
                        },
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    ) {
                        Icon(
                            painterResource(BaseR.drawable.logout_20px),
                            modifier = Modifier
                                .padding(end = ButtonDefaults.IconSpacing)
                                .size(ButtonDefaults.IconSize),
                            contentDescription = null)
                        Text(text = stringResource(I18nR.string.preference_signout))
                    }
                }
            }

            item {
                PreferenceCategory {
                    SwitchPreference(
                        title = stringResource(I18nR.string.plugin_type_filesearch),
                        summary = stringResource(
                            I18nR.string.preference_search_cloud_summary,
                            owncloudUser!!.userName
                        ),
                        value = searchFiles == true,
                        onValueChanged = {
                            viewModel.setSearchFiles(it)
                        },
                        iconPadding = false,
                    )
                }
            }
        } else {
            item {
                val activity = LocalActivity.current as AppCompatActivity
                PreferenceCategory {
                    Preference(
                        title = stringResource(I18nR.string.preference_owncloud_signin),
                        summary = stringResource(I18nR.string.preference_owncloud_signin_summary),
                        icon = BaseR.drawable.login_24px,
                        onClick = {
                            viewModel.signIn(activity)
                        }
                    )
                }
            }
        }
    }
}