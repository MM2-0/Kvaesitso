package de.mm20.launcher2.ui.settings.integrations

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.settings.breezyweather.BreezyWeatherSettingsRoute
import de.mm20.launcher2.ui.settings.feed.FeedIntegrationSettingsRoute
import de.mm20.launcher2.ui.settings.media.MediaIntegrationSettingsRoute
import de.mm20.launcher2.ui.settings.nextcloud.NextcloudSettingsRoute
import de.mm20.launcher2.ui.settings.owncloud.OwncloudSettingsRoute
import de.mm20.launcher2.ui.settings.smartspacer.SmartspacerSettingsRoute
import de.mm20.launcher2.ui.settings.tasks.TasksIntegrationSettingsRoute
import de.mm20.launcher2.ui.settings.weather.WeatherIntegrationSettingsRoute
import de.mm20.launcher2.ui.settings.wikipedia.WikipediaSettingsRoute
import kotlinx.serialization.Serializable

@Serializable
data object IntegrationsSettingsRoute : NavKey

@Composable
fun IntegrationsSettingsScreen() {
    val viewModel: IntegrationsSettingsScreenVM = viewModel()
    val backStack = LocalBackStack.current

    PreferenceScreen(title = stringResource(R.string.preference_screen_integrations)) {
        item {
            PreferenceCategory {
                Preference(
                    title = stringResource(R.string.preference_weather_integration),
                    icon = R.drawable.light_mode_24px,
                    onClick = {
                        backStack.add(WeatherIntegrationSettingsRoute)
                    }
                )
                Preference(
                    title = stringResource(R.string.preference_media_integration),
                    icon = R.drawable.play_circle_24px,
                    onClick = {
                        backStack.add(MediaIntegrationSettingsRoute)
                    }
                )
                /*
                Preference(
                    title = stringResource(R.string.preference_feed_integration),
                    icon = R.drawable.news_24px,
                    onClick = {
                        backStack.add(FeedIntegrationSettingsRoute)
                    }
                )*/
            }
        }
        item {
            PreferenceCategory {
                Preference(
                    title = stringResource(R.string.preference_nextcloud),
                    icon = R.drawable.nextcloud,
                    onClick = {
                        backStack.add(NextcloudSettingsRoute)
                    }
                )
                Preference(
                    title = stringResource(R.string.preference_owncloud),
                    icon = R.drawable.owncloud,
                    onClick = {
                        backStack.add(OwncloudSettingsRoute)
                    }
                )
                Preference(
                    title = stringResource(R.string.preference_search_wikipedia),
                    icon = R.drawable.wikipedia,
                    onClick = {
                        backStack.add(WikipediaSettingsRoute)
                    }
                )
            }
        }
        item {
            PreferenceCategory {
                Preference(
                    title = stringResource(R.string.preference_tasks_integration),
                    icon = R.drawable.check_24px_sharp,
                    onClick = {
                        backStack.add(TasksIntegrationSettingsRoute)
                    }
                )
                Preference(
                    title = stringResource(R.string.preference_breezyweather_integration),
                    icon = R.drawable.breezy_weather,
                    onClick = {
                        backStack.add(BreezyWeatherSettingsRoute)
                    }
                )
                /*
                if (isAtLeastApiLevel(29)) {
                    Preference(
                        title = stringResource(R.string.preference_smartspacer_integration),
                        icon = R.drawable.smartspacer,
                        onClick = {
                            backStack.add(SmartspacerSettingsRoute)
                        }
                    )
                }*/
            }
        }
    }
}