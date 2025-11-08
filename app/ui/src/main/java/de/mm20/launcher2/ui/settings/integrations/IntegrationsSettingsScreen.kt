package de.mm20.launcher2.ui.settings.integrations

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.locals.LocalNavController

@Composable
fun IntegrationsSettingsScreen() {
    val viewModel: IntegrationsSettingsScreenVM = viewModel()
    val navController = LocalNavController.current

    PreferenceScreen(title = stringResource(R.string.preference_screen_integrations)) {
        item {
            PreferenceCategory {
                Preference(
                    title = stringResource(R.string.preference_weather_integration),
                    icon = R.drawable.light_mode_24px,
                    onClick = {
                        navController?.navigate("settings/integrations/weather")
                    }
                )
                Preference(
                    title = stringResource(R.string.preference_media_integration),
                    icon = R.drawable.play_circle_24px,
                    onClick = {
                        navController?.navigate("settings/integrations/media")
                    }
                )
            }
        }
        item {
            PreferenceCategory {
                Preference(
                    title = stringResource(R.string.preference_nextcloud),
                    icon = R.drawable.nextcloud,
                    onClick = {
                        navController?.navigate("settings/integrations/nextcloud")
                    }
                )
                Preference(
                    title = stringResource(R.string.preference_owncloud),
                    icon = R.drawable.owncloud,
                    onClick = {
                        navController?.navigate("settings/integrations/owncloud")
                    }
                )
                Preference(
                    title = stringResource(R.string.preference_search_wikipedia),
                    icon = R.drawable.wikipedia,
                    onClick = {
                        navController?.navigate("settings/search/wikipedia")
                    }
                )
                Preference(
                    title = stringResource(R.string.preference_tasks_integration),
                    icon = R.drawable.check_24px_sharp,
                    onClick = {
                        navController?.navigate("settings/integrations/tasks")
                    }
                )
                Preference(
                    title = stringResource(R.string.preference_breezyweather_integration),
                    icon = R.drawable.breezy_weather,
                    onClick = {
                        navController?.navigate("settings/integrations/breezyweather")
                    }
                )
            }
        }
    }
}