package de.mm20.launcher2.ui.network.logs

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import android.text.format.Formatter
import de.mm20.launcher2.network.api.AppDirectory
import de.mm20.launcher2.network.api.BlocklistController
import de.mm20.launcher2.network.api.ConnectionLogEntry
import de.mm20.launcher2.network.api.ConnectionType
import de.mm20.launcher2.network.api.DecisionReason
import de.mm20.launcher2.network.api.DnsLogEntry
import de.mm20.launcher2.network.api.DomainRule
import de.mm20.launcher2.network.api.FirewallController
import de.mm20.launcher2.network.api.FlowInfo
import de.mm20.launcher2.network.api.IpRule
import de.mm20.launcher2.network.api.LogController
import de.mm20.launcher2.network.api.LogFilter
import de.mm20.launcher2.network.api.NetworkSettings
import de.mm20.launcher2.network.api.RuleAction
import de.mm20.launcher2.network.api.RuleScope
import de.mm20.launcher2.network.api.Verdict
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.network.firewall.NetworkPackageFirewallRoute
import de.mm20.launcher2.ui.network.firewall.NetAppIcon
import de.mm20.launcher2.ui.network.firewall.formatClock
import de.mm20.launcher2.ui.network.firewall.formatDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

private typealias IconsNetworkLogsScreen = de.mm20.launcher2.base.R.drawable

@Serializable
data object NetworkLogsRoute : NavKey

private const val TAB_CONNECTIONS = 0
private const val TAB_DNS = 1
private const val TAB_APPS = 2

private class LogAction(val label: String, val run: suspend () -> Unit)

/** Connection log, DNS log and per app totals, with filters, CSV export and rule actions per entry. */
@Composable
fun NetworkLogsScreen() {
    val logs: LogController = koinInject()
    val directory: AppDirectory = koinInject()
    val settings: NetworkSettings = koinInject()
    val fw: FirewallController = koinInject()
    val blocklists: BlocklistController = koinInject()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val backStack = LocalBackStack.current

    var tab by de.mm20.launcher2.ui.component.rememberPersistentUiState("network.logs.tab", TAB_CONNECTIONS)
    var query by rememberSaveable { mutableStateOf("") }
    var blockedFilter by rememberSaveable { mutableIntStateOf(0) } // 0 all, 1 blocked, 2 allowed
    var appFilter by rememberSaveable { mutableStateOf<Int?>(null) }
    var menu by remember { mutableStateOf(false) }
    var connDetail by remember { mutableStateOf<ConnectionLogEntry?>(null) }
    var dnsDetail by remember { mutableStateOf<DnsLogEntry?>(null) }
    var exportDns by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmClearApp by remember { mutableStateOf<Int?>(null) }

    val values by settings.values.collectAsState()
    val stats by logs.stats.collectAsState()
    val summaries by logs.appSummaries.collectAsState()

    val filter = LogFilter(
        query = null,
        appId = appFilter,
        blocked = when (blockedFilter) { 1 -> true; 2 -> false; else -> null },
        limit = if (query.isBlank()) 500 else 5000,
    )
    val connectionsRaw by remember(query.isBlank(), appFilter, blockedFilter) { logs.connections(filter) }.collectAsState(emptyList())
    val dnsRaw by remember(query.isBlank(), appFilter, blockedFilter) { logs.dns(filter) }.collectAsState(emptyList())
    val connections = if (query.isBlank()) connectionsRaw else connectionsRaw.filter {
        de.mm20.launcher2.comms.search.TelosSearch.matches(query, it.domain, it.destIp, directory.labelFor(it.uid))
    }.take(500)
    val dnsEntries = if (query.isBlank()) dnsRaw else dnsRaw.filter {
        de.mm20.launcher2.comms.search.TelosSearch.matches(query, it.domain, directory.labelFor(it.uid), it.server, *it.answers.toTypedArray())
    }.take(500)

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            scope.launch {
                val text = logs.exportCsv(exportDns, filter.copy(query = query.ifBlank { null }, limit = Int.MAX_VALUE))
                val written = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()); true } ?: false
                    } catch (e: Exception) {
                        false
                    }
                }
                Toast.makeText(
                    context,
                    context.getString(if (written) I18nR.string.hc_done else I18nR.string.au_netui_export_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.netfw_l_clear))  } },
            text = { Text(stringResource(I18nR.string.au2_netsec_clear_logs_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch {
                        when (tab) {
                            TAB_CONNECTIONS -> logs.clearConnections()
                            TAB_DNS -> logs.clearDns()
                            else -> logs.clearAll()
                        }
                    }
                }) { Text(stringResource(I18nR.string.au2_netsec_confirm_clear)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(I18nR.string.au2_netsec_cancel)) } },
        )
    }
    confirmClearApp?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmClearApp = null },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.netfw_l_clear_app, directory.labelFor(id)))  } },
            text = { Text(stringResource(I18nR.string.au2_netsec_clear_app_logs_message, directory.labelFor(id))) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearApp = null
                    scope.launch { logs.clearApp(id) }
                }) { Text(stringResource(I18nR.string.au2_netsec_confirm_clear)) }
            },
            dismissButton = { TextButton(onClick = { confirmClearApp = null }) { Text(stringResource(I18nR.string.au2_netsec_cancel)) } },
        )
    }

    PreferenceScreen(
        bottomBar = { de.mm20.launcher2.ui.network.NetworkNavigationBar(de.mm20.launcher2.ui.network.NetworkSection.Logs) },
        title = stringResource(I18nR.string.netfw_l_title),
        topBarActions = {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(painterResource(IconsNetworkLogsScreen.more_vert_24px), contentDescription = stringResource(I18nR.string.hc_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (tab != TAB_APPS) {
                        DropdownMenuItem(
                            text = { Text(stringResource(I18nR.string.netfw_l_export)) },
                            onClick = {
                                menu = false
                                exportDns = tab == TAB_DNS
                                exporter.launch(if (tab == TAB_DNS) "telos-network-dns.csv" else "telos-network-connections.csv")
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(I18nR.string.netfw_l_clear)) },
                        onClick = {
                            menu = false
                            confirmClear = true
                        },
                    )
                    appFilter?.let { id ->
                        DropdownMenuItem(
                            text = { Text(stringResource(I18nR.string.netfw_l_clear_app, directory.labelFor(id))) },
                            onClick = { menu = false; confirmClearApp = id },
                        )
                    }
                }
            }
        },
    ) {
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = tab == TAB_CONNECTIONS, onClick = { tab = TAB_CONNECTIONS }, label = { Text(stringResource(I18nR.string.netfw_l_connections)) })
                FilterChip(selected = tab == TAB_DNS, onClick = { tab = TAB_DNS }, label = { Text(stringResource(I18nR.string.netfw_l_dns)) })
                FilterChip(selected = tab == TAB_APPS, onClick = { tab = TAB_APPS }, label = { Text(stringResource(I18nR.string.netfw_l_apps)) })
            }
        }
        item {
            Text(
                stringResource(
                    I18nR.string.netfw_l_stats,
                    if (tab == TAB_DNS) stats.dnsAllowed else stats.connectionsAllowed,
                    if (tab == TAB_DNS) stats.dnsBlocked else stats.connectionsBlocked,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if ((tab == TAB_CONNECTIONS && !values.logConnections) || (tab == TAB_DNS && !values.logDns)) {
            item {
                Text(
                    stringResource(I18nR.string.netfw_l_logging_off),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        run {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    de.mm20.launcher2.ui.component.TelosSearchBar(query, { query = it }, stringResource(I18nR.string.netfw_l_search))
                    if (tab != TAB_APPS) Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(selected = blockedFilter == 0, onClick = { blockedFilter = 0 }, label = { Text(stringResource(I18nR.string.netfw_l_all)) })
                        FilterChip(selected = blockedFilter == 1, onClick = { blockedFilter = 1 }, label = { Text(stringResource(I18nR.string.netfw_l_blocked)) })
                        FilterChip(selected = blockedFilter == 2, onClick = { blockedFilter = 2 }, label = { Text(stringResource(I18nR.string.netfw_l_allowed)) })
                        appFilter?.let { id ->
                            FilterChip(
                                selected = true,
                                onClick = { appFilter = null },
                                label = { Text(directory.labelFor(id)) },
                                trailingIcon = { Icon(painterResource(IconsNetworkLogsScreen.close_20px), contentDescription = null) },
                            )
                        }
                    }
                }
            }
        }
        when (tab) {
            TAB_CONNECTIONS -> {
                if (connections.isEmpty()) item { EmptyLog(query) }
                items(connections.size, key = { connections[it].id }) { i ->
                    val e = connections[i]
                    val blocked = e.verdict == Verdict.Block
                    Preference(
                        title = { Text(e.domain ?: "${e.destIp}:${e.destPort}", maxLines = 1) },
                        summary = {
                            Text(
                                "${directory.labelFor(e.uid)} · ${formatClock(context, e.timeMs)} · ${e.protocol.name.uppercase()} ${e.destPort} · ${reasonText(e.reason)}",
                                maxLines = 2,
                            )
                        },
                        icon = { LogAppIcon(directory.byUid(e.uid)?.packageName) { backStack.add(NetworkPackageFirewallRoute(it)) } },
                        controls = { VerdictIcon(blocked) },
                        onClick = { connDetail = e },
                    )
                }
            }
            TAB_DNS -> {
                if (dnsEntries.isEmpty()) item { EmptyLog(query) }
                items(dnsEntries.size, key = { dnsEntries[it].id }) { i ->
                    val e = dnsEntries[i]
                    Preference(
                        title = { Text(e.domain, maxLines = 1) },
                        summary = {
                            Text(
                                "${directory.labelFor(e.uid)} · ${formatClock(context, e.timeMs)} · ${dnsTypeName(e.type)}" +
                                    if (e.blocklists.isNotEmpty()) " · " + e.blocklists.joinToString(", ") else "",
                                maxLines = 2,
                            )
                        },
                        icon = { LogAppIcon(directory.byUid(e.uid)?.packageName) { backStack.add(NetworkPackageFirewallRoute(it)) } },
                        controls = { VerdictIcon(e.blocked) },
                        onClick = { dnsDetail = e },
                    )
                }
            }
            else -> {
                val appSummaries = if (query.isBlank()) summaries else summaries.filter { de.mm20.launcher2.comms.search.TelosSearch.matches(query, directory.labelFor(it.appId)) }
                if (appSummaries.isEmpty()) item { EmptyLog(query) }
                items(appSummaries.size, key = { appSummaries[it].appId }) { i ->
                    val s = appSummaries[i]
                    Preference(
                        title = { Text(directory.labelFor(s.appId)) },
                        summary = {
                            Text(
                                stringResource(I18nR.string.netfw_l_app_summary, s.connections, s.connectionsBlocked, s.dnsQueries, s.dnsBlocked) +
                                    if (s.bytes > 0) " · " + Formatter.formatShortFileSize(context, s.bytes) else ""
                            )
                        },
                        icon = { LogAppIcon(directory.byUid(s.appId)?.packageName) { backStack.add(NetworkPackageFirewallRoute(it)) } },
                        onClick = {
                            appFilter = if (s.appId >= 0) s.appId else null
                            tab = TAB_CONNECTIONS
                        },
                    )
                }
            }
        }
    }

    fun toast() = Toast.makeText(context, context.getString(I18nR.string.hc_done), Toast.LENGTH_SHORT).show()

    connDetail?.let { e ->
        val actions = connectionActions(context, e, fw, blocklists, ::toast)
        DetailDialog(
            rows = listOf(
                stringResource(I18nR.string.netfw_l_d_app) to directory.labelFor(e.uid),
                stringResource(I18nR.string.netfw_l_d_destination) to "${e.domain?.let { "$it, " } ?: ""}${e.destIp}:${e.destPort} (${e.protocol.name.uppercase()})",
                stringResource(I18nR.string.netfw_l_d_reason) to (if (e.verdict == Verdict.Block) stringResource(I18nR.string.netfw_l_blocked) else stringResource(I18nR.string.netfw_l_allowed)) + ": " + reasonText(e.reason),
                stringResource(I18nR.string.netfw_l_d_network) to networkText(e.network),
                stringResource(I18nR.string.netfw_l_d_lists) to e.blocklists.joinToString(", "),
                stringResource(I18nR.string.netfw_l_d_traffic) to if (e.bytesReceived + e.bytesSent > 0) "↓ ${Formatter.formatShortFileSize(context, e.bytesReceived)}  ↑ ${Formatter.formatShortFileSize(context, e.bytesSent)}" else "",
                stringResource(I18nR.string.netfw_l_d_time) to formatDateTime(context, e.timeMs),
            ),
            actions = actions,
            onDismiss = { connDetail = null },
        )
    }
    dnsDetail?.let { e ->
        val actions = dnsActions(context, e, fw, blocklists, ::toast)
        DetailDialog(
            rows = listOf(
                stringResource(I18nR.string.netfw_l_d_app) to directory.labelFor(e.uid),
                stringResource(I18nR.string.netfw_l_d_destination) to "${e.domain} (${dnsTypeName(e.type)})",
                stringResource(I18nR.string.netfw_l_d_reason) to if (e.blocked) stringResource(I18nR.string.netfw_l_blocked) else stringResource(I18nR.string.netfw_l_allowed),
                stringResource(I18nR.string.netfw_l_d_lists) to e.blocklists.joinToString(", "),
                stringResource(I18nR.string.netfw_l_d_server) to e.server,
                stringResource(I18nR.string.netfw_l_d_answers) to e.answers.joinToString(", "),
                stringResource(I18nR.string.netfw_l_d_time) to formatDateTime(context, e.timeMs),
                stringResource(I18nR.string.netfw_l_d_error) to (e.error ?: ""),
            ),
            actions = actions,
            onDismiss = { dnsDetail = null },
        )
    }
}

@Composable
private fun networkText(type: ConnectionType): String = stringResource(
    when (type) {
        ConnectionType.Wifi -> I18nR.string.netfw_t_wifi
        ConnectionType.Mobile -> I18nR.string.netfw_t_mobile
        ConnectionType.Vpn -> I18nR.string.netfw_t_vpn
        ConnectionType.Ethernet -> I18nR.string.au_netui_net_ethernet
        ConnectionType.Other -> I18nR.string.au_netui_net_other
        ConnectionType.None -> I18nR.string.au_netui_net_none
    }
)

@Composable
private fun EmptyLog(query: String = "") {
    if (query.isNotBlank()) { de.mm20.launcher2.ui.component.SearchEmptyState(query.trim()); return }
    Text(
        stringResource(I18nR.string.netfw_l_empty),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
}

@Composable
private fun VerdictIcon(blocked: Boolean) {
    Icon(
        painterResource(if (blocked) IconsNetworkLogsScreen.close_24px else IconsNetworkLogsScreen.check_24px),
        contentDescription = null,
        tint = if (blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun DetailDialog(rows: List<Pair<String, String>>, actions: List<LogAction>, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.hc_close)) } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rows.filter { it.second.isNotBlank() }.forEach { (label, value) ->
                    Column {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        Text(value, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                actions.forEach { a ->
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { scope.launch { a.run(); onDismiss() } },
                    ) { Text(a.label) }
                }
            }
        },
    )
}

@Composable
private fun reasonText(reason: DecisionReason): String = stringResource(
    when (reason) {
        DecisionReason.Default -> I18nR.string.netfw_r_default
        DecisionReason.AppRule -> I18nR.string.netfw_r_app_rule
        DecisionReason.ConnectionTypeRule -> I18nR.string.netfw_r_conn_type
        DecisionReason.BackgroundRule -> I18nR.string.netfw_r_background
        DecisionReason.ScreenOffRule -> I18nR.string.netfw_r_screen_off
        DecisionReason.UniversalRule -> I18nR.string.netfw_r_universal
        DecisionReason.DomainRule -> I18nR.string.netfw_r_domain
        DecisionReason.IpRule -> I18nR.string.netfw_r_ip
        DecisionReason.Blocklist -> I18nR.string.netfw_r_blocklist
        DecisionReason.Trusted -> I18nR.string.netfw_r_trusted
        DecisionReason.Internal -> I18nR.string.netfw_r_internal
        DecisionReason.EngineError -> I18nR.string.netfw_r_error
    }
)

private fun dnsTypeName(type: Int) = when (type) {
    1 -> "A"
    28 -> "AAAA"
    5 -> "CNAME"
    15 -> "MX"
    16 -> "TXT"
    65 -> "HTTPS"
    12 -> "PTR"
    else -> type.toString()
}

private fun scopes(uid: Int): List<Pair<Boolean, RuleScope>> =
    buildList {
        if (uid >= 0) add(true to RuleScope.App(uid % FlowInfo.PER_USER_RANGE))
        add(false to RuleScope.System)
    }

/** The things the user can do about a logged connection: allow it for this app / all apps, block it, or lift the app's rules for a while. */
private fun connectionActions(
    context: android.content.Context,
    e: ConnectionLogEntry,
    fw: FirewallController,
    blocklists: BlocklistController,
    done: () -> Unit,
): List<LogAction> {
    val actions = mutableListOf<LogAction>()
    val domain = e.domain?.takeIf { it.isNotBlank() }
    val name = domain ?: e.destIp
    if (e.verdict == Verdict.Block) {
        when {
            e.reason == DecisionReason.Blocklist && domain != null -> scopes(e.uid).forEach { (app, sc) ->
                actions += LogAction(context.getString(if (app) I18nR.string.netfw_l_a_allow_app else I18nR.string.netfw_l_a_allow_all, name)) {
                    blocklists.addBypass(sc, domain); done()
                }
            }
            e.reason == DecisionReason.DomainRule && domain != null -> scopes(e.uid).forEach { (app, sc) ->
                actions += LogAction(context.getString(if (app) I18nR.string.netfw_l_a_allow_app else I18nR.string.netfw_l_a_allow_all, name)) {
                    fw.addDomainRule(DomainRule(0, sc, domain, RuleAction.Trust)); done()
                }
            }
            e.reason == DecisionReason.IpRule -> scopes(e.uid).forEach { (app, sc) ->
                actions += LogAction(context.getString(if (app) I18nR.string.netfw_l_a_allow_app else I18nR.string.netfw_l_a_allow_all, e.destIp)) {
                    fw.addIpRule(IpRule(0, sc, e.destIp, e.destPort, e.protocol, RuleAction.Trust)); done()
                }
            }
            e.uid >= 0 -> actions += LogAction(context.getString(I18nR.string.netfw_l_a_temp)) {
                fw.allowTemporarily(e.uid % FlowInfo.PER_USER_RANGE, 15L * 60_000); done()
            }
        }
    } else {
        scopes(e.uid).forEach { (app, sc) ->
            actions += LogAction(context.getString(if (app) I18nR.string.netfw_l_a_block_app else I18nR.string.netfw_l_a_block_all, name)) {
                if (domain != null) fw.addDomainRule(DomainRule(0, sc, domain, RuleAction.Block))
                else fw.addIpRule(IpRule(0, sc, e.destIp, 0, null, RuleAction.Block))
                done()
            }
        }
    }
    return actions
}

private fun dnsActions(
    context: android.content.Context,
    e: DnsLogEntry,
    fw: FirewallController,
    blocklists: BlocklistController,
    done: () -> Unit,
): List<LogAction> {
    val actions = mutableListOf<LogAction>()
    scopes(e.uid).forEach { (app, sc) ->
        if (e.blocked) {
            actions += LogAction(context.getString(if (app) I18nR.string.netfw_l_a_allow_app else I18nR.string.netfw_l_a_allow_all, e.domain)) {
                if (e.blocklists.isNotEmpty()) blocklists.addBypass(sc, e.domain)
                else fw.addDomainRule(DomainRule(0, sc, e.domain, RuleAction.Trust))
                done()
            }
        } else {
            actions += LogAction(context.getString(if (app) I18nR.string.netfw_l_a_block_app else I18nR.string.netfw_l_a_block_all, e.domain)) {
                fw.addDomainRule(DomainRule(0, sc, e.domain, RuleAction.Block)); done()
            }
        }
    }
    return actions
}

/** The icon edits app rules; the surrounding row retains the connection details. */
@Composable
internal fun LogAppIcon(packageName: String?, openRules: (String) -> Unit) {
    val rulesLabel = stringResource(I18nR.string.netfw_title)
    IconButton(enabled = packageName != null, onClick = { packageName?.let(openRules) },
        modifier = Modifier.semantics { contentDescription = rulesLabel }) {
        NetAppIcon(packageName)
    }
}
