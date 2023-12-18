package de.mm20.launcher2.ui.settings.plugins

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.plugin.PluginState
import de.mm20.launcher2.plugin.PluginType
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.locals.LocalNavController

@Composable
fun PluginSettingsScreen(pluginId: String) {
    val navController = LocalNavController.current
    val activity = LocalContext.current as AppCompatActivity
    val context = LocalContext.current
    val viewModel: PluginSettingsScreenVM = viewModel()
    LaunchedEffect(pluginId) {
        viewModel.init(pluginId)
    }

    val pluginPackage by viewModel.pluginPackage.collectAsStateWithLifecycle(null)
    val icon by viewModel.icon.collectAsStateWithLifecycle(null)
    val types by viewModel.types.collectAsStateWithLifecycle(emptyList())

    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle(
        null,
        minActiveState = Lifecycle.State.RESUMED
    )
    val filePlugins by viewModel.filePlugins.collectAsStateWithLifecycle(
        emptyList(),
        minActiveState = Lifecycle.State.RESUMED
    )

    val requestPermissionStarter =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                viewModel.setPluginEnabled(true)
            }
        }

    val enabledFileSearchPlugins by viewModel.enabledFileSearchPlugins.collectAsStateWithLifecycle(
        null
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = {
                        if (navController?.navigateUp() != true) {
                            activity.onBackPressed()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (pluginPackage?.settings != null) {
                        IconButton(onClick = {
                            pluginPackage?.settings?.let {
                                activity.startActivity(it)
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = null
                            )
                        }
                    }
                    IconButton(onClick = {
                        viewModel.openAppInfo(context)
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null
                        )
                    }
                    IconButton(onClick = {
                        viewModel.uninstall(context)
                        navController?.navigateUp()
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
                    ) {
                        AsyncImage(
                            model = icon,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(48.dp)
                        )
                        Column {
                            Text(
                                pluginPackage?.label ?: "",
                                style = MaterialTheme.typography.titleLarge
                            )
                            if (pluginPackage?.isOfficial == true) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .background(
                                            MaterialTheme.colorScheme.secondary,
                                            shape = MaterialTheme.shapes.medium,
                                        )
                                        .padding(4.dp)
                                ) {
                                    Text(
                                        "Official",
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondary,
                                    )
                                    Icon(
                                        Icons.Rounded.Verified, null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSecondary,
                                    )
                                }
                            } else if (pluginPackage?.author != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                ) {
                                    Text(
                                        pluginPackage!!.author!!,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                }
                            }
                        }
                    }
                    pluginPackage?.description?.let {
                        Text(
                            text = it,
                            modifier = Modifier
                                .padding(
                                    start = 12.dp,
                                    end = 12.dp,
                                    top = 16.dp,
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 24.dp, start = 12.dp, end = 12.dp, top = 24.dp)
                    ) {
                        for (type in types) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .background(
                                        MaterialTheme.colorScheme.tertiaryContainer,
                                        shape = MaterialTheme.shapes.medium,
                                    )
                                    .padding(4.dp)
                            ) {
                                Icon(
                                    when (type) {
                                        PluginType.FileSearch -> Icons.AutoMirrored.Rounded.InsertDriveFile
                                        PluginType.Weather -> Icons.Rounded.LightMode
                                    },
                                    null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                                Text(
                                    when (type) {
                                        PluginType.FileSearch -> "File search"
                                        PluginType.Weather -> "Weather provider"
                                    },
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                        }
                    }
                }

            }
            val surfaceColor by animateColorAsState(
                if (pluginPackage?.enabled == true) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                }
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth(),
                color = surfaceColor,
            ) {
                SwitchPreference(
                    enabled = pluginPackage != null && hasPermission != null,
                    iconPadding = false,
                    title = "Enable plugin",
                    value = pluginPackage?.enabled == true && hasPermission == true,
                    onValueChanged = {
                        if (hasPermission == true) {
                            viewModel.setPluginEnabled(it)
                        } else {
                            requestPermissionStarter.launch(
                                Intent().apply {
                                    `package` = pluginPackage?.packageName
                                    action = "de.mm20.launcher2.plugin.REQUEST_PERMISSION"
                                }
                            )
                        }
                    }
                )
            }
            AnimatedVisibility(pluginPackage?.enabled == true && hasPermission == true) {
                if (filePlugins.isNotEmpty()) {
                    PreferenceCategory(
                        "File search",
                        iconPadding = false,
                    ) {
                        for (plugin in filePlugins) {
                            val state = plugin.state
                            if (state is PluginState.SetupRequired) {
                                Banner(
                                    modifier = Modifier.padding(16.dp),
                                    text = state.message ?: "You need to setup this plugin first",
                                    icon = Icons.Rounded.Info,
                                    primaryAction = {
                                        TextButton(onClick = {
                                            try {
                                                state.setupActivity.send()
                                            } catch (e: PendingIntent.CanceledException) {
                                                CrashReporter.logException(e)
                                            }
                                        }) {
                                            Text("Set up")
                                        }
                                    }
                                )
                            } else if (state is PluginState.Error) {
                                Banner(
                                    modifier = Modifier.padding(16.dp),
                                    text = "This plugin isn't working correctly",
                                    icon = Icons.Rounded.Error,
                                    color = MaterialTheme.colorScheme.errorContainer,
                                )
                            }
                            SwitchPreference(
                                title = plugin.plugin.label,
                                enabled = enabledFileSearchPlugins != null && state is PluginState.Ready,
                                summary = (state as? PluginState.Ready)?.text
                                    ?: (state as? PluginState.SetupRequired)?.message
                                    ?: plugin.plugin.description,
                                value = enabledFileSearchPlugins?.contains(plugin.plugin.authority) == true && state is PluginState.Ready,
                                onValueChanged = {
                                    viewModel.setFileSearchPluginEnabled(
                                        plugin.plugin.authority,
                                        it
                                    )
                                },
                                iconPadding = false,
                            )
                        }
                    }
                }
            }
        }
    }
}