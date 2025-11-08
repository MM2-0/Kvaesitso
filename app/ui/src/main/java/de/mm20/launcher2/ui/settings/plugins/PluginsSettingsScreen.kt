package de.mm20.launcher2.ui.settings.plugins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import coil.compose.AsyncImage
import de.mm20.launcher2.plugin.PluginPackage
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.LargeMessage
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.serialization.Serializable

@Serializable
data object PluginsSettingsRoute: NavKey

@Composable
fun PluginsSettingsScreen() {
    val viewModel: PluginsSettingsScreenVM = viewModel()
    val pluginPackages by viewModel.pluginPackages.collectAsState(null)
    val enabledPackages by viewModel.enabledPluginPackages.collectAsState(emptyList())
    val disabledPackages by viewModel.disabledPluginPackages.collectAsState(emptyList())
    PreferenceScreen(
        title = stringResource(R.string.preference_screen_plugins),
        helpUrl = "https://kvaesitso.mm20.de/docs/user-guide/concepts/plugins"
    ) {
        when {
            pluginPackages?.isEmpty() == true -> {
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
                            icon = R.drawable.extension_48px,
                            text = stringResource(R.string.no_plugins_installed),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            else -> {
                if (enabledPackages.isNotEmpty()) {
                    item {
                        PreferenceCategory("Enabled") {
                            for (plugin in enabledPackages) {
                                PluginPreference(viewModel, plugin)
                            }
                        }
                    }
                }
                if (disabledPackages.isNotEmpty()) {
                    item {
                        PreferenceCategory("Installed") {
                            for (plugin in disabledPackages) {
                                PluginPreference(viewModel, plugin)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginPreference(viewModel: PluginsSettingsScreenVM, plugin: PluginPackage) {
    val backStack = LocalBackStack.current
    val icon by remember(plugin.packageName) {
        viewModel.getIcon(plugin)
    }.collectAsState(null)
    Preference(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(plugin.label)
            }
        },
        summary = plugin.description?.let { { Text(it) } },
        icon = {
            AsyncImage(
                model = icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp)
            )
        },
        onClick = {
            backStack.add(PluginSettingsRoute(pluginId = plugin.packageName))
        }
    )
}