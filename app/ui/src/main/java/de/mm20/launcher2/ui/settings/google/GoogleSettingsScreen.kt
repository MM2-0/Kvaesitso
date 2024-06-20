package de.mm20.launcher2.ui.settings.google

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.locals.LocalNavController
import de.mm20.launcher2.ui.settings.integrations.GoogleSigninButton

@Composable
fun GoogleSettingsScreen() {

    val viewModel: GoogleSettingsScreenVM = viewModel()
    val lifecycleOwner = LocalLifecycleOwner.current
    val navController = LocalNavController.current

    val googleUser by viewModel.googleUser
    val loading by viewModel.loading
    val searchFiles by viewModel.searchFiles.collectAsState(null)

    LaunchedEffect(null) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.onResume()
        }
    }
    PreferenceScreen(title = stringResource(R.string.preference_google)) {
        if (loading) return@PreferenceScreen

        if (googleUser != null) {
            item {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .fillParentMaxWidth()
                        .padding(vertical = 64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(72.dp)
                            .background(MaterialTheme.colorScheme.secondary, CircleShape)
                            .border(
                                2.dp,
                                MaterialTheme.colorScheme.onSecondaryContainer,
                                CircleShape
                            ),
                    ) {
                        Text(
                            text = googleUser!!.userName.split(" ")
                                .map { it.first() }
                                .joinToString("").let {
                                    if (it.length >= 2) it.first().toString() + it.last().toString()
                                    else it.first().toString()
                                },
                            color = MaterialTheme.colorScheme.onSecondary,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    }
                    Text(
                        modifier = Modifier.padding(top = 24.dp),
                        text = stringResource(
                            R.string.preference_signin_user,
                            googleUser!!.userName
                        ),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Button(
                        modifier = Modifier.padding(top = 32.dp),
                        onClick = {
                            viewModel.signOut()
                        },
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Logout,
                            modifier = Modifier
                                .padding(end = ButtonDefaults.IconSpacing)
                                .size(ButtonDefaults.IconSize),
                            contentDescription = null
                        )
                        Text(text = stringResource(R.string.preference_signout))
                    }
                }
            }
            item {
                HorizontalDivider()
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.plugin_type_filesearch),
                    summary = stringResource(
                        R.string.preference_search_cloud_summary,
                        googleUser!!.userName
                    ),
                    value = searchFiles == true,
                    onValueChanged = {
                        viewModel.setSearchFiles(it)
                    },
                    iconPadding = false,
                )
            }
        } else {
            item {
                val activity = LocalContext.current as AppCompatActivity
                Column(
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .padding(vertical = 64.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    GoogleSigninButton(
                        onClick = {
                            viewModel.signIn(activity)
                        }
                    )
                }
            }
            item {
                HorizontalDivider()
            }
        }
    }
}