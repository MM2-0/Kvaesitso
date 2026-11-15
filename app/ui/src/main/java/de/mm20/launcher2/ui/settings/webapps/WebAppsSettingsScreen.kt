package de.mm20.launcher2.ui.settings.webapps

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import coil.compose.AsyncImage
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.ListPreferenceItem
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.settings.webapps.EditWebAppShortcutSheet
import de.mm20.launcher2.ui.settings.webapps.WebAppsSettingsRoute
import de.mm20.launcher2.ui.settings.webapps.WebAppsSettingsScreenVM
import de.mm20.launcher2.ui.settings.webapps.PanelDirection
import kotlinx.serialization.Serializable

@Serializable
data object WebAppsSettingsRoute : NavKey

@Composable
fun WebAppsSettingsScreen() {
    val context = LocalContext.current
    val viewModel: WebAppsSettingsScreenVM = viewModel()
    val shortcuts by viewModel.shortcuts.collectAsState()
    val direction by viewModel.direction.collectAsState()
    val adBlockEnabled by viewModel.adBlockEnabled.collectAsState()
    val zoomControlsEnabled by viewModel.zoomControlsEnabled.collectAsState()
    val trackingParamStrippingEnabled by viewModel.trackingParamStrippingEnabled.collectAsState()
    val topBarAtBottom by viewModel.topBarAtBottom.collectAsState()
    val swipeToSwitchEnabled by viewModel.swipeToSwitchEnabled.collectAsState()
    val groupsEnabled by viewModel.groupsEnabled.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val userAgentMode by viewModel.userAgentMode.collectAsState()
    val customUserAgent by viewModel.customUserAgent.collectAsState()
    val cookiesEnabled by viewModel.cookiesEnabled.collectAsState()
    val thirdPartyCookiesEnabled by viewModel.thirdPartyCookiesEnabled.collectAsState()
    var showCustomUserAgentDialog by remember { mutableStateOf(false) }
    var showClearCookiesDialog by remember { mutableStateOf(false) }

    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var groupToRename by remember { mutableStateOf<de.mm20.launcher2.preferences.WebAppGroup?>(null) }
    var groupToDelete by remember { mutableStateOf<de.mm20.launcher2.preferences.WebAppGroup?>(null) }
    var shortcutToDelete by remember { mutableStateOf<de.mm20.launcher2.search.WebAppShortcut?>(null) }
    var presetToAdd by remember { mutableStateOf<WebAppPresetCategory?>(null) }

    PreferenceScreen(
        title = stringResource(I18nR.string.preference_screen_web_app_shortcuts),
    ) {
        item {
            PreferenceCategory(title = stringResource(I18nR.string.preference_screen_web_apps_panel)) {
                SwitchPreference(
                    title = stringResource(I18nR.string.preference_web_apps_panel),
                    summary = stringResource(I18nR.string.preference_web_apps_panel_summary),
                    value = direction != null,
                    onValueChanged = { viewModel.setEnabled(it) },
                )
                if (direction != null) {
                    ListPreference(
                        title = stringResource(I18nR.string.web_apps_panel_direction),
                        items = listOf(
                            ListPreferenceItem(stringResource(I18nR.string.web_apps_panel_direction_left), PanelDirection.Left),
                            ListPreferenceItem(stringResource(I18nR.string.web_apps_panel_direction_right), PanelDirection.Right),
                        ),
                        value = direction ?: PanelDirection.Right,
                        onValueChanged = { if (it != null) viewModel.setDirection(it) },
                    )
                }
            }
        }
        item {
            PreferenceCategory(title = stringResource(I18nR.string.web_app_groups_title)) {
                SwitchPreference(
                    title = stringResource(I18nR.string.web_app_groups_enable),
                    summary = stringResource(I18nR.string.web_app_groups_enable_summary),
                    value = groupsEnabled,
                    onValueChanged = { viewModel.setGroupsEnabled(it) },
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(I18nR.string.blocklists_web_title)) {
                de.mm20.launcher2.ui.component.BlockListsSection(
                    de.mm20.launcher2.comms.blocklist.BlockListKind.WEB,
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(I18nR.string.web_app_presets_title)) {
                Text(
                    text = stringResource(I18nR.string.web_app_presets_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                for (category in WebAppPresets.categories) {
                    Preference(
                        icon = category.iconRes,
                        title = stringResource(category.nameRes),
                        summary = category.apps.joinToString(", ") { it.name },
                        onClick = { presetToAdd = category },
                        controls = {
                            TextButton(onClick = { presetToAdd = category }) {
                                Text(stringResource(I18nR.string.web_app_preset_add))
                            }
                        }
                    )
                }
                Text(
                    text = stringResource(I18nR.string.web_app_presets_login_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        if (groupsEnabled) {
            item {
                PreferenceCategory(title = stringResource(I18nR.string.web_app_groups_manage)) {
                    for (group in groups) {
                        Preference(
                            icon = WebAppPresets.byId(group.category)?.iconRes ?: BaseR.drawable.folder_24px,
                            title = group.name,
                            summary = stringResource(I18nR.string.web_app_group_apps_count, group.appKeys.size),
                            onClick = { groupToRename = group },
                            controls = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Checkbox(
                                            checked = group.notificationsEnabled,
                                            onCheckedChange = { viewModel.toggleGroupNotifications(group.id, it) }
                                        )
                                        Text(stringResource(I18nR.string.web_app_group_notifications), style = MaterialTheme.typography.labelSmall)
                                    }
                                    IconButton(onClick = { groupToDelete = group }) {
                                        Icon(
                                            painterResource(BaseR.drawable.delete_24px),
                                            contentDescription = stringResource(I18nR.string.menu_delete),
                                        )
                                    }
                                }
                            }
                        )
                    }
                    Preference(
                        icon = BaseR.drawable.add_24px,
                        title = stringResource(I18nR.string.web_app_group_create),
                        onClick = { showCreateGroupDialog = true }
                    )
                }
            }
        }
        item {
            PreferenceCategory(title = stringResource(I18nR.string.preference_category_web_app_browsing)) {
                SwitchPreference(
                    title = stringResource(I18nR.string.preference_web_app_ad_block),
                    summary = stringResource(I18nR.string.preference_web_app_ad_block_summary),
                    value = adBlockEnabled,
                    onValueChanged = { viewModel.setAdBlockEnabled(it) },
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.preference_web_app_zoom_controls),
                    summary = stringResource(I18nR.string.preference_web_app_zoom_controls_summary),
                    value = zoomControlsEnabled,
                    onValueChanged = { viewModel.setZoomControlsEnabled(it) },
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.preference_web_app_tracking_param_stripping),
                    summary = stringResource(I18nR.string.preference_web_app_tracking_param_stripping_summary),
                    value = trackingParamStrippingEnabled,
                    onValueChanged = { viewModel.setTrackingParamStrippingEnabled(it) },
                )
                ListPreference(
                    title = stringResource(I18nR.string.preference_web_app_top_bar_position),
                    items = listOf(
                        ListPreferenceItem(stringResource(I18nR.string.search_bar_position_top), false),
                        ListPreferenceItem(stringResource(I18nR.string.search_bar_position_bottom), true),
                    ),
                    value = topBarAtBottom,
                    onValueChanged = { if (it != null) viewModel.setTopBarAtBottom(it) },
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.preference_web_app_swipe_to_switch),
                    summary = stringResource(I18nR.string.preference_web_app_swipe_to_switch_summary),
                    value = swipeToSwitchEnabled,
                    onValueChanged = { viewModel.setSwipeToSwitchEnabled(it) },
                )
                ListPreference(
                    title = stringResource(I18nR.string.au4_webapps2_user_agent),
                    items = listOf(
                        ListPreferenceItem(stringResource(I18nR.string.au4_webapps2_user_agent_default), WebAppUserAgents.MODE_DEFAULT),
                        ListPreferenceItem(stringResource(I18nR.string.au4_webapps2_user_agent_desktop), WebAppUserAgents.MODE_DESKTOP),
                        ListPreferenceItem(stringResource(I18nR.string.au4_webapps2_user_agent_custom), WebAppUserAgents.MODE_CUSTOM),
                    ),
                    value = userAgentMode,
                    onValueChanged = {
                        if (it != null) {
                            viewModel.setUserAgentMode(it)
                            if (it == WebAppUserAgents.MODE_CUSTOM) showCustomUserAgentDialog = true
                        }
                    },
                )
                if (userAgentMode == WebAppUserAgents.MODE_CUSTOM) {
                    Preference(
                        title = stringResource(I18nR.string.au4_webapps2_user_agent_custom_title),
                        summary = customUserAgent.ifEmpty { stringResource(I18nR.string.au4_webapps2_user_agent_default) },
                        onClick = { showCustomUserAgentDialog = true },
                    )
                }
                SwitchPreference(
                    title = stringResource(I18nR.string.au4_webapps2_cookies),
                    summary = stringResource(I18nR.string.au4_webapps2_cookies_summary),
                    value = cookiesEnabled,
                    onValueChanged = { viewModel.setCookiesEnabled(it) },
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.au4_webapps2_third_party_cookies),
                    summary = stringResource(I18nR.string.au4_webapps2_third_party_cookies_summary),
                    enabled = cookiesEnabled,
                    value = thirdPartyCookiesEnabled && cookiesEnabled,
                    onValueChanged = { viewModel.setThirdPartyCookiesEnabled(it) },
                )
                Preference(
                    title = stringResource(I18nR.string.au4_webapps2_clear_cookies),
                    summary = stringResource(I18nR.string.au4_webapps2_clear_cookies_summary),
                    onClick = { showClearCookiesDialog = true },
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(I18nR.string.web_apps_panel_items)) {
                if (shortcuts.isEmpty()) {
                    Text(
                        text = stringResource(I18nR.string.au3_secplug_web_apps_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                for (shortcut in shortcuts) {
                    Preference(
                        icon = {
                            val model = shortcut.iconUri ?: shortcut.faviconUrl
                            if (model != null) {
                                AsyncImage(
                                    model = model,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(MaterialTheme.shapes.extraSmall),
                                )
                            } else {
                                val categoryIcon = WebAppPresets.byId(
                                    groups.find { it.appKeys.contains(shortcut.key) }?.category
                                )?.iconRes
                                Icon(
                                    painterResource(categoryIcon ?: BaseR.drawable.language_24px),
                                    contentDescription = null,
                                )
                            }
                        },
                        title = shortcut.label,
                        summary = shortcut.url,
                        onClick = {
                            viewModel.editShortcut(shortcut)
                        },
                        controls = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Checkbox(
                                        checked = shortcut.showInGrid,
                                        onCheckedChange = { viewModel.setPlacement(shortcut, it, shortcut.showInPanel) }
                                    )
                                    Text(stringResource(I18nR.string.search_filter_apps), style = MaterialTheme.typography.labelSmall)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Checkbox(
                                        checked = shortcut.showInPanel,
                                        onCheckedChange = { viewModel.setPlacement(shortcut, shortcut.showInGrid, it) }
                                    )
                                    Text(stringResource(I18nR.string.preference_screen_web_apps_panel), style = MaterialTheme.typography.labelSmall)
                                }
                                IconButton(onClick = { viewModel.duplicate(shortcut) }) {
                                    Icon(
                                        painterResource(BaseR.drawable.content_copy_24px),
                                        contentDescription = stringResource(I18nR.string.duplicate),
                                    )
                                }
                                IconButton(onClick = { shortcutToDelete = shortcut }) {
                                    Icon(
                                        painterResource(BaseR.drawable.delete_24px),
                                        contentDescription = stringResource(I18nR.string.menu_delete),
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                FilledTonalButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    onClick = {
                        viewModel.createShortcut()
                    }) {
                    Icon(
                        painterResource(BaseR.drawable.add_20px),
                        null,
                        modifier = Modifier
                            .padding(end = ButtonDefaults.IconSpacing)
                            .size(ButtonDefaults.IconSize)
                    )
                    Text(stringResource(I18nR.string.web_app_shortcut_create))
                }
            }
        }
    }

    val createShortcut by viewModel.showCreateDialog
    val editShortcut by viewModel.showEditDialogFor

    EditWebAppShortcutSheet(
        expanded = createShortcut,
        existing = null,
        onSave = { label, url, iconUri, faviconUrl, rendererPackage, showInGrid, showInPanel, iconSource, customCss, notificationsEnabled, groupId, adBlockMode, cookieOptions ->
            viewModel.save(null, label, url, iconUri, faviconUrl, rendererPackage, showInGrid, showInPanel, iconSource, customCss, notificationsEnabled, groupId, adBlockMode, cookieOptions)
        },
        onDismiss = { viewModel.dismissDialogs() },
        onImportIcon = { uri, sizePx -> viewModel.importIcon(uri, sizePx) },
        onFindFavicon = { url -> viewModel.findFavicon(url) },
        onExportIconPackIcon = { customIcon, sizePx -> viewModel.exportIconPackIcon(customIcon, sizePx) },
    )
    EditWebAppShortcutSheet(
        expanded = editShortcut != null,
        existing = editShortcut,
        onSave = { label, url, iconUri, faviconUrl, rendererPackage, showInGrid, showInPanel, iconSource, customCss, notificationsEnabled, groupId, adBlockMode, cookieOptions ->
            viewModel.save(editShortcut, label, url, iconUri, faviconUrl, rendererPackage, showInGrid, showInPanel, iconSource, customCss, notificationsEnabled, groupId, adBlockMode, cookieOptions)
        },
        onDismiss = { viewModel.dismissDialogs() },
        onImportIcon = { uri, sizePx -> viewModel.importIcon(uri, sizePx) },
        onFindFavicon = { url -> viewModel.findFavicon(url) },
        onExportIconPackIcon = { customIcon, sizePx -> viewModel.exportIconPackIcon(customIcon, sizePx) },
    )

    if (showCustomUserAgentDialog) {
        var userAgentText by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(customUserAgent) }
        AlertDialog(
            containerColor = de.mm20.launcher2.ui.component.virtualAppPopup(),
            onDismissRequest = { showCustomUserAgentDialog = false },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.au4_webapps2_user_agent_custom_title))  } },
            text = {
                OutlinedTextField(
                    value = userAgentText,
                    onValueChange = { userAgentText = it },
                    label = { Text(stringResource(I18nR.string.au4_webapps2_user_agent_custom_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setCustomUserAgent(userAgentText)
                    showCustomUserAgentDialog = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showCustomUserAgentDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (showClearCookiesDialog) {
        AlertDialog(
            containerColor = de.mm20.launcher2.ui.component.virtualAppPopup(),
            onDismissRequest = { showClearCookiesDialog = false },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.au4_webapps2_clear_cookies))  } },
            text = { Text(stringResource(I18nR.string.au4_webapps2_clear_cookies_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearCookiesDialog = false
                    viewModel.clearCookiesAndSiteData {
                        android.widget.Toast.makeText(
                            context,
                            context.getString(I18nR.string.au4_webapps2_clear_cookies_done),
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                }) { Text(stringResource(I18nR.string.au4_webapps2_clear_cookies_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearCookiesDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (showCreateGroupDialog) {
        var groupName by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
        AlertDialog(
            containerColor = de.mm20.launcher2.ui.component.virtualAppPopup(),
            onDismissRequest = { showCreateGroupDialog = false },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.web_app_group_create_title))  } },
            text = {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text(stringResource(I18nR.string.web_app_group_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = groupName.isNotBlank(),
                    onClick = {
                        viewModel.createGroup(groupName)
                        showCreateGroupDialog = false
                    }
                ) {
                    Text(stringResource(I18nR.string.web_app_group_create_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroupDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (groupToRename != null) {
        var groupName by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(groupToRename!!.name) }
        AlertDialog(
            containerColor = de.mm20.launcher2.ui.component.virtualAppPopup(),
            onDismissRequest = { groupToRename = null },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.web_app_group_rename_title))  } },
            text = {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text(stringResource(I18nR.string.web_app_group_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = groupName.isNotBlank(),
                    onClick = {
                        viewModel.updateGroup(groupToRename!!.copy(name = groupName))
                        groupToRename = null
                    }
                ) {
                    Text(stringResource(I18nR.string.web_app_group_rename_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { groupToRename = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    groupToDelete?.let { group ->
        AlertDialog(
            containerColor = de.mm20.launcher2.ui.component.virtualAppPopup(),
            onDismissRequest = { groupToDelete = null },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.web_app_group_delete_title))  } },
            text = { Text(stringResource(I18nR.string.web_app_group_delete_message, group.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteGroup(group.id)
                    groupToDelete = null
                }) { Text(stringResource(I18nR.string.menu_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { groupToDelete = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    shortcutToDelete?.let { shortcut ->
        AlertDialog(
            containerColor = de.mm20.launcher2.ui.component.virtualAppPopup(),
            onDismissRequest = { shortcutToDelete = null },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.web_app_delete_title))  } },
            text = { Text(stringResource(I18nR.string.web_app_delete_message, shortcut.labelOverride ?: shortcut.label)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(shortcut)
                    shortcutToDelete = null
                }) { Text(stringResource(I18nR.string.menu_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { shortcutToDelete = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    presetToAdd?.let { category ->
        val selected = remember(category) { mutableStateListOf<WebAppPreset>().apply { addAll(category.apps) } }
        val existingUrls = remember(shortcuts) { shortcuts.map { WebAppPresets.comparableUrl(it.url) }.toSet() }
        val categoryName = stringResource(category.nameRes)
        AlertDialog(
            containerColor = de.mm20.launcher2.ui.component.virtualAppPopup(),
            onDismissRequest = { presetToAdd = null },
            icon = { Icon(painterResource(category.iconRes), null) },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.web_app_preset_dialog_title, categoryName))  } },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        stringResource(I18nR.string.web_app_preset_dialog_summary),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    for (app in category.apps) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (selected.contains(app)) selected.remove(app) else selected.add(app)
                                },
                        ) {
                            Checkbox(
                                checked = selected.contains(app),
                                onCheckedChange = { if (it) selected.add(app) else selected.remove(app) },
                            )
                            Column {
                                Text(app.name, style = MaterialTheme.typography.bodyMedium)
                                if (WebAppPresets.comparableUrl(app.url) in existingUrls) {
                                    Text(
                                        stringResource(I18nR.string.web_app_preset_exists),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        stringResource(I18nR.string.web_app_presets_login_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = selected.isNotEmpty(),
                    onClick = {
                        viewModel.addPreset(category, selected.toList()) {
                            android.widget.Toast.makeText(
                                context,
                                context.getString(I18nR.string.web_app_preset_added, categoryName),
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                        presetToAdd = null
                    }
                ) { Text(stringResource(I18nR.string.web_app_preset_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { presetToAdd = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}
