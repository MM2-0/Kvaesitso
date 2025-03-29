package de.mm20.launcher2.ui.settings.contacts

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference

@Composable
fun ContactsSettingsScreen() {
    val viewModel: ContactsSettingsScreenVM = viewModel()
    val context = LocalContext.current

    val hasCallPermission by viewModel.hasCallPermission.collectAsStateWithLifecycle(null)
    val callOnTap by viewModel.callOnTap.collectAsStateWithLifecycle(null)

    PreferenceScreen(
        title = stringResource(R.string.preference_search_contacts)
    ) {
        item {
            PreferenceCategory {
                AnimatedVisibility(hasCallPermission == false) {
                    MissingPermissionBanner(
                        text = stringResource(R.string.missing_permission_call_contacts_settings),
                        onClick = {
                            viewModel.requestCallPermission(context as AppCompatActivity)
                        },
                        modifier = Modifier.padding(16.dp)
                    )
                }
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