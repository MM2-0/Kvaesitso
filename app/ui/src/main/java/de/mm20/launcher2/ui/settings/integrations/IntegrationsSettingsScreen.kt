package de.mm20.launcher2.ui.settings.integrations

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.icons.Google
import de.mm20.launcher2.icons.Nextcloud
import de.mm20.launcher2.icons.Owncloud
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.locals.LocalNavController

@Composable
fun IntegrationsSettingsScreen() {
    val viewModel: IntegrationsSettingsScreenVM = viewModel()
    val navController = LocalNavController.current

    PreferenceScreen(title = stringResource(R.string.preference_screen_integrations)) {
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
            Preference(
                title = stringResource(R.string.preference_nextcloud),
                icon = Icons.Rounded.Nextcloud,
                onClick = {
                    navController?.navigate("settings/integrations/nextcloud")
                }
            )
            Preference(
                title = stringResource(R.string.preference_owncloud),
                icon = Icons.Rounded.Owncloud,
                onClick = {
                    navController?.navigate("settings/integrations/owncloud")
                }
            )
        }
    }
}