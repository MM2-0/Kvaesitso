package de.mm20.launcher2.ui.settings.plugins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.ExtensionOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import coil.compose.AsyncImage
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.LargeMessage
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen

@Composable
fun PluginsSettingsScreen() {
    val viewModel: PluginsSettingsScreenVM = viewModel()
    val hostInstalled by viewModel.hostInstalled.collectAsState(null)
    val hasPermission by viewModel.hasPermission.collectAsState(null)
    val context = LocalContext.current
    val plugins by viewModel.plugins.collectAsState(null)
    PreferenceScreen(title = stringResource(R.string.preference_screen_plugins)) {
        when {
            hostInstalled == false -> {
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
                            icon = Icons.Rounded.ExtensionOff,
                            text = stringResource(R.string.plugin_host_not_installed),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            hasPermission == false -> {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        MissingPermissionBanner(
                            text = stringResource(R.string.missing_permission_plugins),
                            onClick = { viewModel.requestPermission(context) }
                        )
                    }
                }
            }

            plugins?.isEmpty() == true -> {
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
                            icon = Icons.Rounded.Extension,
                            text = stringResource(R.string.no_plugins_installed),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            plugins != null -> {
                items(plugins!!) { item ->
                    val icon by remember(item.plugin.authority) {
                        viewModel.getIcon(item.plugin)
                    }.collectAsState(null)
                    Preference(
                        title = { Text(item.plugin.label) },
                        summary = item.plugin.description?.let { { Text(it) } },
                        controls = {
                            Switch(checked = item.plugin.enabled, onCheckedChange = {
                                viewModel.setPluginEnabled(item.plugin, it)
                            })
                        },
                        icon = {
                            AsyncImage(model = icon, contentDescription = null, modifier = Modifier.size(36.dp))
                        },
                        onClick = {
                            viewModel.setPluginEnabled(item.plugin, !item.plugin.enabled)
                        }
                    )
                }
            }
        }
    }
}