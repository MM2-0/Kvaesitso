package de.mm20.launcher2.ui.settings.plugins

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.ktx.sendWithBackgroundPermission
import de.mm20.launcher2.plugin.PluginState
import de.mm20.launcher2.themes.atTone
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.preferences.CheckboxPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceWithSwitch
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.locals.LocalDarkTheme
import de.mm20.launcher2.ui.locals.LocalNavController

@Composable
fun PluginSettingsScreen(pluginId: String) {
    val navController = LocalNavController.current
    val activity = LocalActivity.current
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

    val locationPlugins by viewModel.locationPlugins.collectAsStateWithLifecycle(
        emptyList(),
        minActiveState = Lifecycle.State.RESUMED
    )

    val calendarPlugins by viewModel.calendarPlugins.collectAsStateWithLifecycle(
        emptyList(),
        minActiveState = Lifecycle.State.RESUMED
    )

    val contactPlugins by viewModel.contactPlugins.collectAsStateWithLifecycle(
        emptyList(),
        minActiveState = Lifecycle.State.RESUMED
    )

    val weatherPlugins by viewModel.weatherPlugins.collectAsStateWithLifecycle(
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

    val enabledContactPlugins by viewModel.enabledContactPlugins.collectAsStateWithLifecycle(
        null
    )

    val enabledLocationSearchPlugins by viewModel.enabledLocationSearchPlugins.collectAsStateWithLifecycle(
        null
    )

    val enabledCalendarSearchPlugins by viewModel.enabledCalendarSearchPlugins.collectAsStateWithLifecycle(
        null
    )

    val weatherProviderId by viewModel.weatherProvider.collectAsStateWithLifecycle(
        null
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = {
                        if (navController?.navigateUp() != true) {
                            activity?.onBackPressed()
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
                                activity?.startActivity(it)
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
                    .padding(bottom = 16.dp)
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
                            if (pluginPackage?.author != null) {
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
                                    if (pluginPackage?.isVerified == true) {
                                        Icon(
                                            Icons.Rounded.Verified, null,
                                            modifier = Modifier
                                                .padding(start = 4.dp)
                                                .size(16.dp),
                                            tint = MaterialTheme.colorScheme.secondary,
                                        )
                                    }
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
                    title = stringResource(R.string.preference_plugin_enable),
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
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    if (filePlugins.isNotEmpty()) {
                        PreferenceCategory(
                            stringResource(R.string.plugin_type_filesearch),
                            iconPadding = false,
                        ) {
                            for (plugin in filePlugins) {
                                val state = plugin.state
                                if (state is PluginState.SetupRequired) {
                                    Banner(
                                        modifier = Modifier.padding(16.dp),
                                        text = state.message
                                            ?: stringResource(R.string.plugin_state_setup_required),
                                        icon = Icons.Rounded.Info,
                                        primaryAction = {
                                            TextButton(onClick = {
                                                try {
                                                    state.setupActivity.sendWithBackgroundPermission(
                                                        context
                                                    )
                                                } catch (e: PendingIntent.CanceledException) {
                                                    CrashReporter.logException(e)
                                                }
                                            }) {
                                                Text(stringResource(R.string.plugin_action_setup))
                                            }
                                        }
                                    )
                                } else if (state is PluginState.Error) {
                                    Banner(
                                        modifier = Modifier.padding(16.dp),
                                        text = stringResource(R.string.plugin_state_error),
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
                    if (contactPlugins.isNotEmpty()) {
                        PreferenceCategory(
                            stringResource(R.string.plugin_type_contacts),
                            iconPadding = false,
                        ) {
                            for (plugin in contactPlugins) {
                                val state = plugin.state
                                if (state is PluginState.SetupRequired) {
                                    Banner(
                                        modifier = Modifier.padding(16.dp),
                                        text = state.message
                                            ?: stringResource(R.string.plugin_state_setup_required),
                                        icon = Icons.Rounded.Info,
                                        primaryAction = {
                                            TextButton(onClick = {
                                                try {
                                                    state.setupActivity.sendWithBackgroundPermission(
                                                        context
                                                    )
                                                } catch (e: PendingIntent.CanceledException) {
                                                    CrashReporter.logException(e)
                                                }
                                            }) {
                                                Text(stringResource(R.string.plugin_action_setup))
                                            }
                                        }
                                    )
                                } else if (state is PluginState.Error) {
                                    Banner(
                                        modifier = Modifier.padding(16.dp),
                                        text = stringResource(R.string.plugin_state_error),
                                        icon = Icons.Rounded.Error,
                                        color = MaterialTheme.colorScheme.errorContainer,
                                    )
                                }
                                SwitchPreference(
                                    title = plugin.plugin.label,
                                    enabled = enabledContactPlugins != null && state is PluginState.Ready,
                                    summary = (state as? PluginState.Ready)?.text
                                        ?: (state as? PluginState.SetupRequired)?.message
                                        ?: plugin.plugin.description,
                                    value = enabledContactPlugins?.contains(plugin.plugin.authority) == true && state is PluginState.Ready,
                                    onValueChanged = {
                                        viewModel.setContactPluginEnabled(
                                            plugin.plugin.authority,
                                            it
                                        )
                                    },
                                    iconPadding = false,
                                )
                            }
                        }
                    }
                    if (locationPlugins.isNotEmpty()) {
                        PreferenceCategory(
                            stringResource(R.string.plugin_type_locationsearch),
                            iconPadding = false,
                        ) {
                            for (plugin in locationPlugins) {
                                val state = plugin.state
                                if (state is PluginState.SetupRequired) {
                                    Banner(
                                        modifier = Modifier.padding(16.dp),
                                        text = state.message
                                            ?: stringResource(R.string.plugin_state_setup_required),
                                        icon = Icons.Rounded.Info,
                                        primaryAction = {
                                            TextButton(onClick = {
                                                try {
                                                    state.setupActivity.sendWithBackgroundPermission(
                                                        context
                                                    )
                                                } catch (e: PendingIntent.CanceledException) {
                                                    CrashReporter.logException(e)
                                                }
                                            }) {
                                                Text(stringResource(R.string.plugin_action_setup))
                                            }
                                        }
                                    )
                                } else if (state is PluginState.Error) {
                                    Banner(
                                        modifier = Modifier.padding(16.dp),
                                        text = stringResource(R.string.plugin_state_error),
                                        icon = Icons.Rounded.Error,
                                        color = MaterialTheme.colorScheme.errorContainer,
                                    )
                                }
                                SwitchPreference(
                                    title = plugin.plugin.label,
                                    enabled = enabledLocationSearchPlugins != null && state is PluginState.Ready,
                                    summary = (state as? PluginState.Ready)?.text
                                        ?: (state as? PluginState.SetupRequired)?.message
                                        ?: plugin.plugin.description,
                                    value = enabledLocationSearchPlugins?.contains(plugin.plugin.authority) == true && state is PluginState.Ready,
                                    onValueChanged = {
                                        viewModel.setLocationSearchPluginEnabled(
                                            plugin.plugin.authority,
                                            it
                                        )
                                    },
                                    iconPadding = false,
                                )
                            }
                        }
                    }
                    if (calendarPlugins.isNotEmpty()) {
                        PreferenceCategory(
                            stringResource(R.string.plugin_type_calendar),
                            iconPadding = false,
                        ) {
                            val excludedCalendars by viewModel.excludedCalendars.collectAsState(
                                emptySet()
                            )
                            for (plugin in calendarPlugins) {
                                val state = plugin.state
                                if (state is PluginState.SetupRequired) {
                                    Banner(
                                        modifier = Modifier.padding(16.dp),
                                        text = state.message
                                            ?: stringResource(R.string.plugin_state_setup_required),
                                        icon = Icons.Rounded.Info,
                                        primaryAction = {
                                            TextButton(onClick = {
                                                try {
                                                    state.setupActivity.sendWithBackgroundPermission(
                                                        context
                                                    )
                                                } catch (e: PendingIntent.CanceledException) {
                                                    CrashReporter.logException(e)
                                                }
                                            }) {
                                                Text(stringResource(R.string.plugin_action_setup))
                                            }
                                        }
                                    )
                                } else if (state is PluginState.Error) {
                                    Banner(
                                        modifier = Modifier.padding(16.dp),
                                        text = stringResource(R.string.plugin_state_error),
                                        icon = Icons.Rounded.Error,
                                        color = MaterialTheme.colorScheme.errorContainer,
                                    )
                                }
                                val calendarLists by remember(plugin.state, plugin.plugin) {
                                    viewModel.getCalendarLists(plugin.plugin)
                                }.collectAsStateWithLifecycle(
                                    null,
                                    minActiveState = Lifecycle.State.RESUMED
                                )
                                var showDialog by remember { mutableStateOf(false) }
                                val selectedCalendars = remember(excludedCalendars, calendarLists) {
                                    calendarLists?.size?.minus(excludedCalendars.count {
                                        it.startsWith(
                                            plugin.plugin.authority
                                        )
                                    })
                                }
                                PreferenceWithSwitch(
                                    title = plugin.plugin.label,
                                    enabled = enabledCalendarSearchPlugins != null && state is PluginState.Ready,
                                    summary = (state as? PluginState.SetupRequired)?.message
                                        ?: if (selectedCalendars != null && calendarLists != null) {
                                            pluralStringResource(
                                                R.plurals.calendar_search_enabled_lists,
                                                selectedCalendars,
                                                selectedCalendars
                                            )
                                        }
                                        else (state as? PluginState.Ready)?.text
                                            ?: plugin.plugin.description,
                                    switchValue = enabledCalendarSearchPlugins?.contains(plugin.plugin.authority) == true && state is PluginState.Ready,
                                    onSwitchChanged = {
                                        viewModel.setCalendarSearchPluginEnabled(
                                            plugin.plugin.authority,
                                            it
                                        )
                                    },
                                    iconPadding = false,
                                    onClick = {
                                        showDialog = true
                                    }
                                )
                                if (showDialog && calendarLists?.isNotEmpty() == true) {
                                    ModalBottomSheet(
                                        onDismissRequest = {
                                            showDialog = false
                                        },
                                    ) {
                                        LazyColumn {
                                            items(calendarLists!!) {
                                                CheckboxPreference(
                                                    title = it.name,
                                                    summary = it.owner,
                                                    iconPadding = false,
                                                    value = it.id !in excludedCalendars,
                                                    onValueChanged = { value ->
                                                        viewModel.setCalendarExcluded(it.id, !value)
                                                    },

                                                    checkboxColors = CheckboxDefaults.colors(
                                                        checkedColor = if (it.color == 0) MaterialTheme.colorScheme.primary
                                                        else Color(
                                                            it.color.atTone(if (LocalDarkTheme.current) 80 else 40)
                                                        ),
                                                        checkmarkColor = if (it.color == 0) MaterialTheme.colorScheme.onPrimary
                                                        else Color(
                                                            it.color.atTone(if (LocalDarkTheme.current) 20 else 100)
                                                        )
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (weatherPlugins.isNotEmpty()) {
                        PreferenceCategory(
                            stringResource(R.string.plugin_type_weather),
                            iconPadding = false,
                        ) {
                            for (plugin in weatherPlugins) {
                                val state = plugin.state
                                if (state is PluginState.SetupRequired) {
                                    Banner(
                                        modifier = Modifier.padding(16.dp),
                                        text = state.message
                                            ?: stringResource(R.string.plugin_state_setup_required),
                                        icon = Icons.Rounded.Info,
                                        primaryAction = {
                                            TextButton(onClick = {
                                                try {
                                                    state.setupActivity.sendWithBackgroundPermission(
                                                        context
                                                    )
                                                } catch (e: PendingIntent.CanceledException) {
                                                    CrashReporter.logException(e)
                                                }
                                            }) {
                                                Text(stringResource(R.string.plugin_action_setup))
                                            }
                                        }
                                    )
                                } else if (state is PluginState.Error) {
                                    Banner(
                                        modifier = Modifier.padding(16.dp),
                                        text = stringResource(R.string.plugin_state_error),
                                        icon = Icons.Rounded.Error,
                                        color = MaterialTheme.colorScheme.errorContainer,
                                    )
                                }
                                Preference(
                                    title = plugin.plugin.label,
                                    enabled = state is PluginState.Ready && weatherProviderId != plugin.plugin.authority,
                                    iconPadding = false,
                                    summary = if (weatherProviderId != plugin.plugin.authority) {
                                        stringResource(R.string.plugin_weather_provider_enable)
                                    } else {
                                        stringResource(R.string.plugin_weather_provider_enabled)
                                    },
                                    onClick = {
                                        viewModel.setWeatherProvider(plugin.plugin.authority)
                                    }
                                )
                            }
                            Preference(
                                title = stringResource(R.string.widget_config_weather_integration_settings),
                                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                                onClick = {
                                    navController?.navigate("settings/integrations/weather")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}