package de.mm20.launcher2.ui.settings.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.LargeMessage
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import kotlinx.serialization.Serializable

@Serializable
data object FeedIntegrationSettingsRoute : NavKey

@Composable
fun FeedIntegrationSettingsScreen() {
    val context = LocalContext.current

    val viewModel: FeedIntegrationSettingsScreenVM = viewModel()
    val selectedProvider by viewModel.providerPackage.collectAsState(null)
    val feedEnabled by viewModel.feedEnabled.collectAsState(null)

    val providers = remember { viewModel.getFeedProviders(context) }
    PreferenceScreen(
        title = stringResource(R.string.preference_feed_integration),
        helpUrl = "https://kvaesitso.mm20.de/docs/user-guide/integrations/feed"
    ) {
        if (providers.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillParentMaxHeight()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    LargeMessage(
                        icon = R.drawable.news_48px,
                        text = stringResource(R.string.no_feed_providers),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        } else {
            item {
                PreferenceCategory {
                    SwitchPreference(
                        icon = R.drawable.news_24px,
                        title = stringResource(R.string.preference_feed_integration),
                        summary = stringResource(R.string.preference_feed_enable_summary),
                        value = feedEnabled == true,
                        onValueChanged = {
                            viewModel.setFeedEnabled(it)
                        }
                    )
                }
            }
            item {
                PreferenceCategory(stringResource(R.string.preference_category_feed_provider)) {
                    for (prov in providers) {
                        Preference(
                            title = prov.label,
                            icon = if (prov.packageName == selectedProvider) R.drawable.radio_button_checked_24px else R.drawable.radio_button_unchecked_24px,
                            onClick = {
                                viewModel.setProviderPackage(prov.packageName)
                            }
                        )
                    }
                }
            }
        }
    }
}