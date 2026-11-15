package de.mm20.launcher2.ui.network.wireguard

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import de.mm20.launcher2.ui.network.firewall.NetAppIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.network.api.AppDirectory
import de.mm20.launcher2.network.api.AppEntry
import de.mm20.launcher2.network.api.WgAssignment
import de.mm20.launcher2.network.api.WireguardConfig
import de.mm20.launcher2.network.api.WireguardController
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private typealias IconsNetworkWireguardAppsScreen = de.mm20.launcher2.base.R.drawable

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NetworkWireguardAppsScreen() {
    val backStack = LocalBackStack.current
    val wg: WireguardController = koinInject()
    val dir: AppDirectory = koinInject()
    val scope = rememberCoroutineScope()
    val apps by dir.apps.collectAsState()
    val configs by wg.configs.collectAsState()
    val assignments by wg.assignments.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    var showSystem by rememberSaveable { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<Int>() }
    var bulkMenu by remember { mutableStateOf(false) }

    val shown = remember(apps, query, showSystem) {
        apps.filter { (showSystem || !it.isSystem) && it.hasInternet }
            .filter { de.mm20.launcher2.comms.search.TelosSearch.matches(query, it.label, it.packageName) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                title = {
                    TelosCyberHeading {
                        Text(if (selected.isEmpty()) stringResource(I18nR.string.nwg_apps_title) else stringResource(I18nR.string.nwg_selected, selected.size))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (selected.isEmpty()) backStack.removeLastOrNull() else selected.clear() }) {
                        Icon(
                            painterResource(if (selected.isEmpty()) IconsNetworkWireguardAppsScreen.arrow_back_24px else IconsNetworkWireguardAppsScreen.close_24px),
                            contentDescription = stringResource(if (selected.isEmpty()) I18nR.string.nwg_back else I18nR.string.nwg_clear_selection),
                        )
                    }
                },
                actions = {
                    if (selected.isNotEmpty()) {
                        TextButton(onClick = { selected.clear(); selected.addAll(shown.map { it.appId }) }) {
                            Text(stringResource(I18nR.string.nwg_select_all))
                        }
                        Box {
                            TextButton(onClick = { bulkMenu = true }) { Text(stringResource(I18nR.string.nwg_assign_selected)) }
                            DropdownMenu(expanded = bulkMenu, onDismissRequest = { bulkMenu = false }) {
                                assignmentChoices(configs).forEach { (label, a) ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = {
                                        bulkMenu = false
                                        val ids = selected.toList()
                                        selected.clear()
                                        scope.launch { wg.assignAll(ids, a) }
                                    })
                                }
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            de.mm20.launcher2.ui.component.TelosSearchBar(query, { query = it }, stringResource(I18nR.string.nwg_search))
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(I18nR.string.nwg_show_system), Modifier.weight(1f))
                Switch(checked = showSystem, onCheckedChange = { showSystem = it })
            }
            if (shown.isEmpty() && query.isNotBlank()) de.mm20.launcher2.ui.component.SearchEmptyState(query.trim())
            else if (shown.isEmpty() && apps.isEmpty()) Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Text(stringResource(I18nR.string.au_netui_loading_apps), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else if (shown.isEmpty()) Text(
                stringResource(I18nR.string.netfw_no_apps),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            else LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.appId }) { app ->
                    AppRow(
                        app = app,
                        assignment = assignments[app.appId] ?: WgAssignment.SystemDefault,
                        configs = configs,
                        selecting = selected.isNotEmpty(),
                        isSelected = app.appId in selected,
                        onToggleSelect = { if (app.appId in selected) selected.remove(app.appId) else selected.add(app.appId) },
                        onAssign = { a -> scope.launch { wg.assign(app.appId, a) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun assignmentChoices(configs: List<WireguardConfig>): List<Pair<String, WgAssignment>> =
    listOf(
        stringResource(I18nR.string.nwg_system_default) to WgAssignment.SystemDefault,
        stringResource(I18nR.string.nwg_direct) to WgAssignment.Direct,
    ) + configs.map { it.name to WgAssignment.Config(it.id) }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    app: AppEntry,
    assignment: WgAssignment,
    configs: List<WireguardConfig>,
    selecting: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onAssign: (WgAssignment) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val current = when (assignment) {
        WgAssignment.SystemDefault -> stringResource(I18nR.string.nwg_system_default)
        WgAssignment.Direct -> stringResource(I18nR.string.nwg_direct)
        is WgAssignment.Config -> configs.firstOrNull { it.id == assignment.configId }?.name ?: stringResource(I18nR.string.nwg_system_default)
    }
    Box {
        ListItem(
            headlineContent = { Text(app.label, maxLines = 1) },
            supportingContent = {
                Text(current, color = if (assignment == WgAssignment.SystemDefault) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
            },
            leadingContent = {
                if (selecting) Checkbox(checked = isSelected, onCheckedChange = { onToggleSelect() })
                else NetAppIcon(app.packageName, 32.dp)
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.combinedClickable(
                onClick = { if (selecting) onToggleSelect() else menu = true },
                onLongClick = onToggleSelect,
            ),
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            assignmentChoices(configs).forEach { (label, a) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; onAssign(a) })
            }
        }
    }
}
