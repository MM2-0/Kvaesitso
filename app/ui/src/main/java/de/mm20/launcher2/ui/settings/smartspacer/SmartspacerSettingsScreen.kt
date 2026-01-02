package de.mm20.launcher2.ui.settings.smartspacer

import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import kotlinx.serialization.Serializable

@Serializable
data object SmartspacerSettingsRoute : NavKey

@Composable
fun SmartspacerSettingsScreen() {
    val viewModel = viewModel<SmartspacerSettingsScreenVM>()
    val activity = LocalActivity.current

    val isSmartspacerAppInstalled by viewModel.isSmartspacerAppInstalled.collectAsStateWithLifecycle(
        null
    )
    val isSmartspacerEnabled by viewModel.isSmartspacerEnabled.collectAsStateWithLifecycle(null)

    PreferenceScreen(
        title = stringResource(R.string.preference_smartspacer_integration)
    ) {
        if (isSmartspacerAppInstalled == false) {
            item {
                PreferenceCategory {
                    Banner(
                        text = stringResource(
                            R.string.preference_smartspacer_integration_description,
                            stringResource(R.string.app_name)
                        ),
                        icon = R.drawable.info_24px,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(16.dp),
                        primaryAction = {
                            Button(onClick = {
                                viewModel.downloadSmartspacerApp(activity as AppCompatActivity)
                            }) {
                                Text(stringResource(R.string.action_install))
                            }
                        }
                    )
                }
            }
        }
        if (isSmartspacerAppInstalled == true) {
            item {
                PreferenceCategory {
                    SwitchPreference(
                        icon = R.drawable.smartspacer,
                        title = stringResource(R.string.preference_smartspacer_enable),
                        value = isSmartspacerEnabled == true,
                        onValueChanged = {
                            viewModel.setSmartspacerEnabled(it)
                        }
                    )

                    Preference(
                        title = stringResource(R.string.preference_launch_smartspacer_app),
                        icon = R.drawable.open_in_new_24px,
                        onClick = {
                            viewModel.launchSmartspacerApp(activity as AppCompatActivity)
                        }
                    )
                }
            }
        }
    }
}