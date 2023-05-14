package de.mm20.launcher2.ui.settings.integrations

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.accounts.AccountType
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.locals.LocalNavController

@Composable
fun IntegrationsSettingsScreen() {
    val viewModel: IntegrationsSettingsScreenVM = viewModel()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val navController = LocalNavController.current

    LaunchedEffect(null) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.onResume()
        }
    }

    val owncloudUser by viewModel.owncloudUser
    val nextcloudUser by viewModel.nextcloudUser
    val msUser by viewModel.msUser
    val googleUser by viewModel.googleUser

    val loading by viewModel.loading

    PreferenceScreen(title = stringResource(R.string.preference_screen_integrations)) {
        if (loading) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item {
            Preference(
                title = stringResource(R.string.preference_weather_integration),
                icon = Icons.Rounded.LightMode,
                onClick = {
                    navController?.navigate("settings/integrations/weather")
                }
            )
            Preference(
                title = stringResource(R.string.preference_media_integration),
                icon = Icons.Rounded.PlayCircleOutline,
                onClick = {
                    navController?.navigate("settings/integrations/media")
                }
            )
        }
        item {
            PreferenceCategory(
                title = stringResource(id = R.string.preference_category_accounts)
            ) {

                Preference(
                    title = if (nextcloudUser != null) {
                        stringResource(R.string.preference_nextcloud)
                    } else {
                        stringResource(R.string.preference_nextcloud_signin)
                    },
                    summary = nextcloudUser?.let {
                        stringResource(R.string.preference_signin_user, it.userName)
                    } ?: stringResource(R.string.preference_nextcloud_signin_summary),
                    onClick = {
                        if (nextcloudUser != null) {
                            viewModel.signOut(AccountType.Nextcloud)
                        } else {
                            viewModel.signIn(context as AppCompatActivity, AccountType.Nextcloud)
                        }
                    },
                    enabled = !loading,
                )

                Preference(
                    title = if (owncloudUser != null) {
                        stringResource(R.string.preference_owncloud)
                    } else {
                        stringResource(R.string.preference_owncloud_signin)
                    },
                    summary = owncloudUser?.let {
                        stringResource(R.string.preference_signin_user, it.userName)
                    } ?: stringResource(R.string.preference_owncloud_signin_summary),
                    onClick = {
                        if (owncloudUser != null) {
                            viewModel.signOut(AccountType.Owncloud)
                        } else {
                            viewModel.signIn(context as AppCompatActivity, AccountType.Owncloud)
                        }
                    },
                    enabled = !loading,
                )

                if (viewModel.isGoogleAvailable) {
                    Preference(
                        title = if (googleUser != null) {
                            stringResource(R.string.preference_google)
                        } else {
                            stringResource(R.string.preference_google_signin)
                        },
                        summary = googleUser?.let {
                            stringResource(R.string.preference_signin_user, it.userName)
                        } ?: stringResource(R.string.preference_google_signin_summary),
                        onClick = {
                            if (googleUser != null) {
                                viewModel.signOut(AccountType.Google)
                            } else {
                                viewModel.signIn(
                                    context as AppCompatActivity,
                                    AccountType.Google
                                )
                            }
                        },
                        enabled = !loading,
                    )
                }
            }
        }
    }
}

@Composable
fun GoogleSigninButton(
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.height(40.dp),
        shadowElevation = 1.dp,
        color = Color.White,
        shape = RoundedCornerShape(2.dp),
        onClick = onClick
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    modifier = Modifier.size(18.dp),
                    painter = painterResource(id = R.drawable.ic_google_g),
                    contentDescription = null
                )
            }
            Text(
                modifier = Modifier.padding(start = 13.dp, end = 8.dp),
                text = stringResource(id = R.string.preference_google_signin),
                fontFamily = FontFamily.SansSerif,
                color = Color(0f, 0f, 0f, 0.54f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}