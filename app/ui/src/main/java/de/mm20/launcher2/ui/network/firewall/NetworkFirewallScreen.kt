package de.mm20.launcher2.ui.network.firewall

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.network.api.AppDirectory
import de.mm20.launcher2.network.api.AppEntry
import de.mm20.launcher2.network.api.isFirewallCandidate
import de.mm20.launcher2.network.api.AppRule
import de.mm20.launcher2.network.api.FirewallController
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

private typealias IconsNetworkFirewallScreen = de.mm20.launcher2.base.R.drawable

@Serializable
data object NetworkFirewallRoute : NavKey

@Serializable
data class NetworkPackageFirewallRoute(val packageName: String) : NavKey

private const val FILTER_ALL = 0
private const val FILTER_USER = 1
private const val FILTER_SYSTEM = 2
private const val FILTER_RULES = 3
private const val FILTER_ALLOWED = 4
private const val FILTER_BLOCKED = 5

/** Per app firewall: switches for every connection type, bulk actions, and the way to the rule pages. */
@Composable
fun NetworkFirewallScreen(packageName: String? = null) {
    val fw: FirewallController = koinInject()
    val directory: AppDirectory = koinInject()
    val engine: de.mm20.launcher2.network.NetworkEngine = koinInject()
    val networkSettings: de.mm20.launcher2.network.api.NetworkSettings = koinInject()
    val netConfig by networkSettings.values.collectAsState()
    val engineState by engine.state.collectAsState()
    val ipRules by fw.ipRules.collectAsState()
    val domainRules by fw.domainRules.collectAsState()
    val wg: de.mm20.launcher2.network.api.WireguardController = koinInject()
    val assignments by wg.assignments.collectAsState()
    val logs: de.mm20.launcher2.network.api.LogController = koinInject()
    val totals by logs.appSummaries.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val backStack = LocalBackStack.current

    val apps by directory.apps.collectAsState()
    val rules by fw.appRules.collectAsState()
    val temp by fw.tempAllowed.collectAsState()
    val allowedNew by fw.allowedNewApps.collectAsState()
    val universal by fw.universalRules.collectAsState()
    val detection by fw.backgroundDetection.collectAsState()

    var refreshing by remember { mutableStateOf(true) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(FILTER_ALL) }
    var category by rememberSaveable { mutableStateOf<Int?>(null) }
    val categories = remember(apps, context) { apps.map { it.category }.distinct().filter { it >= 0 }.sorted().mapNotNull { value -> android.content.pm.ApplicationInfo.getCategoryTitle(context, value)?.let { value to it.toString() } } }
    var expanded by rememberSaveable { mutableStateOf<Int?>(null) }
    var menu by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var pendingBulk by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(packageName, apps) {
        if (packageName != null) {
            apps.firstOrNull { packageName in it.packages }?.let { expanded = it.appId }
        }
    }

    // the usage access permission is granted in the Android settings: look again when coming back
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                fw.refreshBackgroundDetection()
                scope.launch {
                    refreshing = true
                    try { directory.refresh() } finally { refreshing = false }
                }
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val shown = remember(apps, rules, query, filter, category, packageName) {
        apps.filter { app ->
            if (packageName != null) {
                app.isFirewallCandidate(packageName)
            } else {
                app.isFirewallCandidate(null) &&
                (category == null || category == app.category) &&
                de.mm20.launcher2.comms.search.TelosSearch.matches(query, app.label, app.packages.joinToString(" "), app.appId.toString()) &&
                when (filter) {
                    FILTER_USER -> !app.isSystem
                    FILTER_SYSTEM -> app.isSystem
                    FILTER_RULES -> rules.containsKey(app.appId)
                    FILTER_ALLOWED -> rules[app.appId]?.blockAll != true
                    FILTER_BLOCKED -> rules[app.appId]?.blockAll == true
                    else -> true
                }
            }
        }
    }

    fun bulk(change: (AppRule) -> AppRule) {
        scope.launch {
            fw.setAppRules(shown.map { change(rules[it.appId] ?: AppRule(it.appId)) })
            Toast.makeText(context, context.getString(I18nR.string.netfw_bulk_done, shown.size), Toast.LENGTH_SHORT).show()
        }
    }

    pendingBulk?.let { kind ->
        AlertDialog(
            onDismissRequest = { pendingBulk = null },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.au4_confirms_bulk_title))  } },
            text = { Text(stringResource(when (kind) {
                0 -> I18nR.string.au4_confirms_bulk_block_all
                1 -> I18nR.string.au4_confirms_bulk_allow_all
                2 -> I18nR.string.au4_confirms_bulk_block_wifi
                else -> I18nR.string.au4_confirms_bulk_block_mobile
            }, shown.size)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingBulk = null
                    when (kind) {
                        0 -> bulk { it.copy(blockAll = true) }
                        1 -> bulk { it.copy(blockAll = false) }
                        2 -> bulk { it.copy(blockWifi = true) }
                        else -> bulk { it.copy(blockMobile = true) }
                    }
                }) { Text(stringResource(I18nR.string.au4_confirms_confirm)) }
            },
            dismissButton = { TextButton(onClick = { pendingBulk = null }) { Text(stringResource(I18nR.string.au4_confirms_cancel)) } },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.netfw_bulk_reset))  } },
            text = { Text(stringResource(I18nR.string.au2_netsec_reset_message, shown.size)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    bulk { AppRule(it.appId) }
                }) { Text(stringResource(I18nR.string.au2_netsec_confirm_reset)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(I18nR.string.au2_netsec_cancel)) } },
        )
    }

    PreferenceScreen(
        bottomBar = { de.mm20.launcher2.ui.network.NetworkNavigationBar(de.mm20.launcher2.ui.network.NetworkSection.Apps) },
        title = stringResource(I18nR.string.netfw_title),
        topBarActions = {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(painterResource(IconsNetworkFirewallScreen.more_vert_24px), contentDescription = stringResource(I18nR.string.hc_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (packageName == null) {
                        DropdownMenuItem(text = { Text(stringResource(I18nR.string.netfw_filter_all)) }, onClick = { category = null; menu = false })
                        categories.forEach { (value, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { category = value; menu = false },
                                trailingIcon = { if (category == value) Icon(painterResource(IconsNetworkFirewallScreen.check_circle_24px), null) })
                        }
                    }
                    Text(
                        stringResource(I18nR.string.netfw_bulk),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    DropdownMenuItem(text = { Text(stringResource(I18nR.string.netfw_bulk_block_all)) }, onClick = { menu = false; pendingBulk = 0 })
                    DropdownMenuItem(text = { Text(stringResource(I18nR.string.netfw_bulk_allow_all)) }, onClick = { menu = false; pendingBulk = 1 })
                    DropdownMenuItem(text = { Text(stringResource(I18nR.string.netfw_bulk_block_wifi)) }, onClick = { menu = false; pendingBulk = 2 })
                    DropdownMenuItem(text = { Text(stringResource(I18nR.string.netfw_bulk_block_mobile)) }, onClick = { menu = false; pendingBulk = 3 })
                    DropdownMenuItem(
                        text = { Text(stringResource(I18nR.string.netfw_bulk_reset)) },
                        onClick = { menu = false; confirmReset = true },
                    )
                }
            }
        },
    ) {
        if (packageName == null) {
        item {
            Preference(title = stringResource(I18nR.string.net_hub_title),
                summary = stringResource(if (engineState is de.mm20.launcher2.network.NetState.On) I18nR.string.net_hub_state_on else I18nR.string.net_hub_summary_off),
                onClick = { backStack.add(de.mm20.launcher2.ui.network.NetworkRoute) })
        }
        item {
            PreferenceCategory {
                Preference(
                    title = stringResource(I18nR.string.netfw_universal_rules),
                    summary = stringResource(I18nR.string.netfw_universal_rules_summary),
                    icon = IconsNetworkFirewallScreen.rule_settings_24px,
                    onClick = { backStack.add(NetworkUniversalRulesRoute) },
                )
                Preference(
                    title = stringResource(I18nR.string.netfw_custom_rules),
                    summary = stringResource(I18nR.string.netfw_custom_rules_summary),
                    icon = IconsNetworkFirewallScreen.tune_24px,
                    onClick = { backStack.add(NetworkCustomRulesRoute) },
                )
            }
        }
        item {
            de.mm20.launcher2.ui.component.TelosSearchBar(query, { query = it }, stringResource(I18nR.string.netfw_search_apps))
        }
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    FILTER_ALL to I18nR.string.netfw_filter_all,
                    FILTER_ALLOWED to I18nR.string.netfw_r_default,
                    FILTER_BLOCKED to I18nR.string.netfw_blocked,
                    FILTER_USER to I18nR.string.netfw_filter_user,
                    FILTER_SYSTEM to I18nR.string.netfw_filter_system,
                    FILTER_RULES to I18nR.string.netfw_filter_rules,
                ).forEach { (id, label) ->
                    FilterChip(selected = filter == id, onClick = { filter = id }, label = { Text(stringResource(label)) })
                }
            }
        }
        }
        if (shown.isEmpty() && query.isNotBlank()) {
            item { de.mm20.launcher2.ui.component.SearchEmptyState(query.trim()) }
        } else if (shown.isEmpty() && refreshing) {
            // the app list is read in the background: show progress instead of a false "no apps"
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text(stringResource(I18nR.string.au_netui_loading_apps), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (shown.isEmpty()) {
            item {
                Text(
                    stringResource(I18nR.string.netfw_no_apps),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        items(shown.size, key = { shown[it].appId }) { index ->
            val app = shown[index]
            val rule = rules[app.appId] ?: AppRule(app.appId)
            val newBlocked = universal.blockNewApps && app.appId !in allowedNew && fw.isNewAppBlocked(app.appId)
            AppRuleRow(
                app = app,
                rule = rule,
                tempUntilMs = temp[app.appId]?.takeIf { it > System.currentTimeMillis() },
                newAppBlocked = newBlocked,
                backgroundDetection = detection,
                detail = packageName != null,
                expanded = packageName != null || expanded == app.appId,
                onToggleExpanded = { if (packageName == null) backStack.add(NetworkPackageFirewallRoute(app.packageName)) else expanded = if (expanded == app.appId) null else app.appId },
                onRule = { next -> scope.launch {
                    fw.setAppRule(next)
                    if (next.blockLan && !rule.blockLan && !netConfig.routeLan) {
                        networkSettings.setRouteLan(true); networkSettings.flush()
                        if (engine.state.value is de.mm20.launcher2.network.NetState.On) engine.restart()
                    } else if (next.excludeFromVpn != rule.excludeFromVpn && engine.state.value is de.mm20.launcher2.network.NetState.On) engine.restart()
                    else engine.closeAllConnections()
                } },
                allowVpnExclusion = !netConfig.torEnabled && netConfig.torAppIds.isEmpty(),
                direct = assignments[app.appId] == de.mm20.launcher2.network.api.WgAssignment.Direct,
                onDirect = { enabled -> scope.launch {
                    wg.assign(app.appId, if (enabled) de.mm20.launcher2.network.api.WgAssignment.Direct else de.mm20.launcher2.network.api.WgAssignment.SystemDefault)
                    engine.closeAllConnections()
                } },
                traffic = totals.firstOrNull { it.appId == app.appId }?.bytes ?: 0,
                ipCount = ipRules.count { it.scope == de.mm20.launcher2.network.api.RuleScope.App(app.appId) },
                domainCount = domainRules.count { it.scope == de.mm20.launcher2.network.api.RuleScope.App(app.appId) },
                onCustomRules = { backStack.add(NetworkAppRulesRoute(app.appId)) },
                onTempAllow = { ms -> scope.launch { fw.allowTemporarily(app.appId, ms) } },
                onCancelTemp = { scope.launch { fw.cancelTemporaryAllow(app.appId) } },
                onAllowNew = { scope.launch { fw.allowNewApp(app.appId) } },
                onGrantUsageAccess = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                        // no such settings screen on this device
                    }
                },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AppRuleRow(
    app: AppEntry,
    rule: AppRule,
    tempUntilMs: Long?,
    newAppBlocked: Boolean,
    backgroundDetection: Boolean,
    detail: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onRule: (AppRule) -> Unit,
    onTempAllow: (Long) -> Unit,
    onCancelTemp: () -> Unit,
    onAllowNew: () -> Unit,
    onGrantUsageAccess: () -> Unit,
    allowVpnExclusion: Boolean,
    direct: Boolean,
    onDirect: (Boolean) -> Unit,
    traffic: Long,
    ipCount: Int,
    domainCount: Int,
    onCustomRules: () -> Unit,
) {
    val context = LocalContext.current
    val blockedTypes = buildList {
        if (rule.blockWifi) add(stringResource(I18nR.string.netfw_t_wifi))
        if (rule.blockMobile) add(stringResource(I18nR.string.netfw_t_mobile))
        if (rule.blockRoaming) add(stringResource(I18nR.string.netfw_t_roaming))
        if (rule.blockLan) add(stringResource(I18nR.string.netfw_t_lan))
        if (rule.blockVpn) add(stringResource(I18nR.string.netfw_t_vpn))
        if (rule.blockBackground) add(stringResource(I18nR.string.netfw_t_background))
        if (rule.blockScreenOff) add(stringResource(I18nR.string.netfw_t_screen_off))
    }
    val summary = when {
        tempUntilMs != null -> stringResource(I18nR.string.netfw_temp_allowed_until, formatClock(context, tempUntilMs))
        rule.blockAll -> stringResource(I18nR.string.netfw_blocked)
        newAppBlocked -> stringResource(I18nR.string.netfw_new_app_blocked)
        blockedTypes.isNotEmpty() -> stringResource(I18nR.string.netfw_summary_blocked_on, blockedTypes.joinToString(", "))
        else -> stringResource(I18nR.string.netfw_no_rules)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceBright),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (detail) {
            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                NetAppIcon(app.packageName, size = 80.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(app.label, style = MaterialTheme.typography.headlineSmall)
                    Text(app.packages.joinToString("\n") + " (" + app.appId + ")", style = MaterialTheme.typography.bodySmall)
                    Text(summary, style = MaterialTheme.typography.bodyMedium)
                    Text(android.text.format.Formatter.formatShortFileSize(context, traffic), style = MaterialTheme.typography.labelLarge)
                }
            }
            NetworkAppRuleGrid(rule, allowVpnExclusion, onRule)
        } else Preference(
            title = { Text(app.label) },
            summary = { Text(summary + "\n" + app.packages.joinToString(" · ") + " (" + app.appId + ")\n" + android.text.format.Formatter.formatShortFileSize(context, traffic)) },
            icon = { NetAppIcon(app.packageName) },
            onClick = onToggleExpanded,
            controls = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val editable = !rule.blockAll && !rule.bypassFirewall && !rule.excludeFromVpn
                    NetworkRuleToggle(I18nR.string.netfw_t_wifi, !rule.blockWifi, editable,
                        IconsNetworkFirewallScreen.wifi_24px, IconsNetworkFirewallScreen.wifi_off_24px) { onRule(rule.copy(blockWifi = !it)) }
                    NetworkRuleToggle(I18nR.string.netfw_t_mobile, !rule.blockMobile, editable,
                        IconsNetworkFirewallScreen.signal_cellular_alt_24px, IconsNetworkFirewallScreen.signal_cellular_off_24px) { onRule(rule.copy(blockMobile = !it)) }
                }
            },
        )
        if (expanded && !detail) FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            @Composable fun AllowChip(label: Int, blocked: Boolean, change: () -> Unit, enabled: Boolean = !rule.blockAll && !rule.bypassFirewall && !rule.excludeFromVpn) {
                FilterChip(selected = !blocked, onClick = change, enabled = enabled, label = { Text(stringResource(label)) })
            }
            FilterChip(selected = rule.excludeFromVpn, enabled = allowVpnExclusion,
                onClick = { onRule(rule.copy(excludeFromVpn = !rule.excludeFromVpn)) }, label = { Text(stringResource(I18nR.string.netfw_vpn_bypass)) })
            AllowChip(I18nR.string.netfw_t_wifi, rule.blockWifi, { onRule(rule.copy(blockWifi = !rule.blockWifi)) })
            AllowChip(I18nR.string.netfw_t_mobile, rule.blockMobile, { onRule(rule.copy(blockMobile = !rule.blockMobile)) })
            AllowChip(I18nR.string.netfw_t_lan, rule.blockLan, { onRule(rule.copy(blockLan = !rule.blockLan)) })
            AllowChip(I18nR.string.netfw_t_vpn, rule.blockVpn, { onRule(rule.copy(blockVpn = !rule.blockVpn)) })
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (!detail) SwitchPreference(title = stringResource(I18nR.string.netfw_blocked), value = rule.blockAll,
                    onValueChanged = { onRule(rule.copy(blockAll = it)) })
                if (newAppBlocked) {
                    Preference(
                        title = stringResource(I18nR.string.netfw_new_app_allow),
                        onClick = onAllowNew,
                    )
                }
                if (detail) {
                    SwitchPreference(title = stringResource(I18nR.string.netfw_t_wifi), value = !rule.blockWifi, onValueChanged = { onRule(rule.copy(blockWifi = !it)) })
                    SwitchPreference(title = stringResource(I18nR.string.netfw_t_mobile), value = !rule.blockMobile, onValueChanged = { onRule(rule.copy(blockMobile = !it)) })
                    SwitchPreference(title = stringResource(I18nR.string.netfw_t_lan), value = !rule.blockLan, onValueChanged = { onRule(rule.copy(blockLan = !it)) })
                    SwitchPreference(title = stringResource(I18nR.string.netfw_t_vpn), value = !rule.blockVpn, onValueChanged = { onRule(rule.copy(blockVpn = !it)) })
                }
                SwitchPreference(title = stringResource(I18nR.string.netfw_block_roaming), icon = IconsNetworkFirewallScreen.public_24px, value = rule.blockRoaming, onValueChanged = { onRule(rule.copy(blockRoaming = it)) })
                SwitchPreference(title = stringResource(I18nR.string.netfw_block_background), icon = IconsNetworkFirewallScreen.pause_24px, value = rule.blockBackground, onValueChanged = { onRule(rule.copy(blockBackground = it)) })
                SwitchPreference(title = stringResource(I18nR.string.netfw_block_screen_off), icon = IconsNetworkFirewallScreen.schedule_24px, value = rule.blockScreenOff, onValueChanged = { onRule(rule.copy(blockScreenOff = it)) })
                if (!backgroundDetection && rule.blockBackground) {
                    Preference(
                        title = stringResource(I18nR.string.netfw_usage_needed),
                        controls = { TextButton(onClick = onGrantUsageAccess) { Text(stringResource(I18nR.string.netfw_usage_grant)) } },
                    )
                }
                if (!detail) SwitchPreference(title = stringResource(I18nR.string.netfw_u_metered), value = rule.blockMetered, onValueChanged = { onRule(rule.copy(blockMetered = it)) })
                if (!detail) SwitchPreference(title = stringResource(I18nR.string.netfw_u_unmetered), value = rule.blockUnmetered, onValueChanged = { onRule(rule.copy(blockUnmetered = it)) })
                de.mm20.launcher2.ui.network.NetworkProxyAssignmentPreference(app.appId)
                SwitchPreference(title = stringResource(I18nR.string.nwg_direct), value = direct, onValueChanged = onDirect)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onCustomRules) { Text("$ipCount · " + stringResource(I18nR.string.netfw_r_ip)) }
                    TextButton(onClick = onCustomRules) { Text("$domainCount · " + stringResource(I18nR.string.netfw_r_domain)) }
                }
                var editNote by remember { mutableStateOf(false) }
                Preference(title = stringResource(I18nR.string.hc_notes), summary = rule.note, onClick = { editNote = true })
                if (editNote) {
                    var note by remember { mutableStateOf(rule.note) }
                    AlertDialog(onDismissRequest = { editNote = false },
                        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_notes))  } },
                        text = { OutlinedTextField(note, { note = it }, modifier = Modifier.fillMaxWidth()) },
                        confirmButton = { TextButton(onClick = { onRule(rule.copy(note = note.trim())); editNote = false }) { Text(stringResource(I18nR.string.hc_save)) } },
                        dismissButton = { TextButton(onClick = { editNote = false }) { Text(stringResource(I18nR.string.hc_cancel)) } })
                }
                if (!detail) SwitchPreference(title = stringResource(I18nR.string.netfw_ignore_universal), value = rule.ignoreUniversalRules, onValueChanged = { onRule(rule.copy(ignoreUniversalRules = it)) })
                if (!detail) SwitchPreference(title = stringResource(I18nR.string.netfw_bypass_firewall), value = rule.bypassFirewall, onValueChanged = { onRule(rule.copy(bypassFirewall = it)) })
                if (!detail) SwitchPreference(title = stringResource(I18nR.string.netfw_exclude_vpn), enabled = allowVpnExclusion, value = rule.excludeFromVpn, onValueChanged = { onRule(rule.copy(excludeFromVpn = it)) })
                if (tempUntilMs != null) {
                    Preference(
                        title = stringResource(I18nR.string.netfw_temp_allowed_until, formatClock(context, tempUntilMs)),
                        controls = { TextButton(onClick = onCancelTemp) { Text(stringResource(I18nR.string.netfw_temp_cancel)) } },
                    )
                } else {
                    var open by remember { mutableStateOf(false) }
                    Box {
                        Preference(
                            title = stringResource(I18nR.string.netfw_temp_allow),
                            icon = IconsNetworkFirewallScreen.timer_24px,
                            onClick = { open = true },
                        )
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            listOf(
                                I18nR.string.netfw_temp_15m to 15L * 60_000,
                                I18nR.string.netfw_temp_1h to 60L * 60_000,
                                I18nR.string.netfw_temp_8h to 8L * 60 * 60_000,
                            ).forEach { (label, ms) ->
                                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { open = false; onTempAllow(ms) })
                            }
                        }
                    }
                }
            }
        }
    }
}
