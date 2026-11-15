package de.mm20.launcher2.ui.network.firewall

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import android.widget.Toast
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.network.api.AppDirectory
import de.mm20.launcher2.network.api.DomainRule
import de.mm20.launcher2.network.api.FirewallController
import de.mm20.launcher2.network.api.IpRule
import de.mm20.launcher2.network.api.Protocol
import de.mm20.launcher2.network.api.RuleAction
import de.mm20.launcher2.network.api.RuleScope
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

private typealias IconsNetworkRulesScreens = de.mm20.launcher2.base.R.drawable

@Serializable
data object NetworkUniversalRulesRoute : NavKey

@Serializable
data object NetworkCustomRulesRoute : NavKey

@Serializable
data class NetworkAppRulesRoute(val appId: Int) : NavKey

/** Rules that apply to all apps (Wi-Fi, mobile data, local network, new apps, device state...). */
@Composable
fun NetworkUniversalRulesScreen() {
    val fw: FirewallController = koinInject()
    val directory: AppDirectory = koinInject()
    val scope = rememberCoroutineScope()
    val u by fw.universalRules.collectAsState()
    val apps by directory.apps.collectAsState()
    val allowedNew by fw.allowedNewApps.collectAsState()
    val detection by fw.backgroundDetection.collectAsState()

    fun set(change: (de.mm20.launcher2.network.api.UniversalRules) -> de.mm20.launcher2.network.api.UniversalRules) {
        scope.launch { fw.setUniversalRules(change(fw.universalRules.value)) }
    }

    PreferenceScreen(title = stringResource(I18nR.string.netfw_u_title)) {
        item {
            Text(
                stringResource(I18nR.string.netfw_u_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item {
            PreferenceCategory(stringResource(I18nR.string.netfw_u_networks)) {
                SwitchPreference(title = stringResource(I18nR.string.netfw_u_wifi), icon = IconsNetworkRulesScreens.wifi_24px, value = u.blockWifi, onValueChanged = { v -> set { it.copy(blockWifi = v) } })
                SwitchPreference(title = stringResource(I18nR.string.netfw_u_mobile), icon = IconsNetworkRulesScreens.signal_cellular_alt_24px, value = u.blockMobile, onValueChanged = { v -> set { it.copy(blockMobile = v) } })
                SwitchPreference(title = stringResource(I18nR.string.netfw_u_roaming), icon = IconsNetworkRulesScreens.public_24px, value = u.blockRoaming, onValueChanged = { v -> set { it.copy(blockRoaming = v) } })
                SwitchPreference(title = stringResource(I18nR.string.netfw_u_metered), icon = IconsNetworkRulesScreens.signal_cellular_off_24px, value = u.blockMetered, onValueChanged = { v -> set { it.copy(blockMetered = v) } })
                SwitchPreference(title = stringResource(I18nR.string.netfw_u_lan), icon = IconsNetworkRulesScreens.lan_24px, value = u.blockLan, onValueChanged = { v -> set { it.copy(blockLan = v) } })
            }
        }
        item {
            PreferenceCategory(stringResource(I18nR.string.netfw_u_device)) {
                SwitchPreference(title = stringResource(I18nR.string.netfw_u_background), icon = IconsNetworkRulesScreens.pause_24px, value = u.blockBackground, onValueChanged = { v -> set { it.copy(blockBackground = v) } })
                SwitchPreference(title = stringResource(I18nR.string.netfw_u_screen_off), icon = IconsNetworkRulesScreens.schedule_24px, value = u.blockScreenOff, onValueChanged = { v -> set { it.copy(blockScreenOff = v) } })
                SwitchPreference(title = stringResource(I18nR.string.netfw_u_locked), icon = IconsNetworkRulesScreens.lock_24px, value = u.blockWhenDeviceLocked, onValueChanged = { v -> set { it.copy(blockWhenDeviceLocked = v) } })
                if (u.blockBackground && !detection) {
                    val context = LocalContext.current
                    Preference(
                        title = stringResource(I18nR.string.netfw_usage_needed),
                        controls = {
                            TextButton(onClick = {
                                try {
                                    context.startActivity(
                                        android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (e: Exception) {
                                    // no such settings screen
                                }
                            }) { Text(stringResource(I18nR.string.netfw_usage_grant)) }
                        },
                    )
                }
            }
        }
        item {
            PreferenceCategory(stringResource(I18nR.string.netfw_u_apps)) {
                SwitchPreference(
                    title = stringResource(I18nR.string.netfw_u_new_apps),
                    summary = stringResource(I18nR.string.netfw_u_new_apps_summary),
                    icon = IconsNetworkRulesScreens.person_add_24px,
                    value = u.blockNewApps,
                    onValueChanged = { v -> set { it.copy(blockNewApps = v) } },
                )
                SwitchPreference(title = stringResource(I18nR.string.netfw_u_unknown), icon = IconsNetworkRulesScreens.person_search_24px, value = u.blockUnknownApps, onValueChanged = { v -> set { it.copy(blockUnknownApps = v) } })
                SwitchPreference(
                    title = stringResource(I18nR.string.netfw_u_default_deny),
                    summary = stringResource(I18nR.string.netfw_u_default_deny_summary),
                    icon = IconsNetworkRulesScreens.signal_cellular_off_24px,
                    value = u.defaultDeny,
                    onValueChanged = { v -> set { it.copy(defaultDeny = v) } },
                )
            }
        }
        item {
            PreferenceCategory(stringResource(I18nR.string.netfw_u_traffic)) {
                SwitchPreference(title = stringResource(I18nR.string.netfw_u_udp), value = u.blockUdp, onValueChanged = { v -> set { it.copy(blockUdp = v) } })
                SwitchPreference(title = stringResource(I18nR.string.netfw_u_http), value = u.blockHttp, onValueChanged = { v -> set { it.copy(blockHttp = v) } })
                SwitchPreference(title = stringResource(I18nR.string.netfw_u_dns_bypass), value = u.blockDnsBypass, onValueChanged = { v -> set { it.copy(blockDnsBypass = v) } })
            }
        }
        if (u.blockNewApps) {
            val waiting = apps.filter { it.hasInternet && fw.isNewAppBlocked(it.appId) && it.appId !in allowedNew }
            if (waiting.isNotEmpty()) {
                item {
                    PreferenceCategory(stringResource(I18nR.string.netfw_u_new_apps_list)) {
                        waiting.forEach { app ->
                            Preference(
                                title = { Text(app.label) },
                                icon = { NetAppIcon(app.packageName) },
                                controls = {
                                    TextButton(onClick = { scope.launch { fw.allowNewApp(app.appId) } }) {
                                        Text(stringResource(I18nR.string.netfw_u_allow))
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** IP / CIDR / port rules and domain rules, system-wide or for one app, Block or Trust. */
@Composable
fun NetworkCustomRulesScreen(appId: Int? = null) {
    val fw: FirewallController = koinInject()
    val directory: AppDirectory = koinInject()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val ipRules by fw.ipRules.collectAsState()
    val domainRules by fw.domainRules.collectAsState()
    var cq by rememberSaveable { mutableStateOf("") }
    var dialog by rememberSaveable { mutableStateOf<Boolean?>(null) } // true = IP rule, false = domain rule

    fun scopeText(scope: RuleScope) = when (scope) {
        RuleScope.System -> context.getString(I18nR.string.netfw_c_all_apps)
        is RuleScope.App -> directory.labelFor(scope.appId)
    }

    fun actionText(a: RuleAction) = context.getString(if (a == RuleAction.Block) I18nR.string.netfw_c_action_block else I18nR.string.netfw_c_action_trust)

    val shownDomains = domainRules.filter { (appId == null || it.scope == RuleScope.App(appId)) && (cq.isBlank() || de.mm20.launcher2.comms.search.TelosSearch.matches(cq, it.domain, scopeText(it.scope))) }
    val shownIps = ipRules.filter { (appId == null || it.scope == RuleScope.App(appId)) && (cq.isBlank() || de.mm20.launcher2.comms.search.TelosSearch.matches(cq, it.address, it.port.takeIf { p -> p != 0 }?.toString(), it.protocol?.name, scopeText(it.scope))) }

    PreferenceScreen(title = stringResource(I18nR.string.netfw_c_title)) {
        item {
            de.mm20.launcher2.ui.component.TelosSearchBar(cq, { cq = it }, stringResource(I18nR.string.hc_search))
        }
        if (cq.isNotBlank() && shownDomains.isEmpty() && shownIps.isEmpty()) {
            item { de.mm20.launcher2.ui.component.SearchEmptyState(cq.trim()) }
        }
        item {
            Text(
                stringResource(I18nR.string.netfw_c_trust_info),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item {
            PreferenceCategory(stringResource(I18nR.string.netfw_c_domains)) {
                Preference(title = stringResource(I18nR.string.netfw_c_add_domain), icon = IconsNetworkRulesScreens.add_24px, onClick = { dialog = false })
                shownDomains.forEach { r ->
                    Preference(
                        title = { Text(r.domain) },
                        summary = { Text("${actionText(r.action)} · ${scopeText(r.scope)}") },
                        controls = {
                            IconButton(onClick = { scope.launch { fw.removeDomainRule(r.id) } }) {
                                Icon(painterResource(IconsNetworkRulesScreens.delete_24px), contentDescription = stringResource(I18nR.string.hc_delete))
                            }
                        },
                    )
                }
            }
        }
        item {
            PreferenceCategory(stringResource(I18nR.string.netfw_c_ip)) {
                Preference(title = stringResource(I18nR.string.netfw_c_add_ip), icon = IconsNetworkRulesScreens.add_24px, onClick = { dialog = true })
                shownIps.forEach { r ->
                    val where = buildString {
                        append(r.address)
                        if (r.port != 0) append(":").append(r.port)
                        r.protocol?.let { append(" ").append(it.name.uppercase()) }
                    }
                    Preference(
                        title = { Text(where) },
                        summary = { Text("${actionText(r.action)} · ${scopeText(r.scope)}") },
                        controls = {
                            IconButton(onClick = { scope.launch { fw.removeIpRule(r.id) } }) {
                                Icon(painterResource(IconsNetworkRulesScreens.delete_24px), contentDescription = stringResource(I18nR.string.hc_delete))
                            }
                        },
                    )
                }
            }
        }
        if (ipRules.isEmpty() && domainRules.isEmpty()) {
            item {
                Text(
                    stringResource(I18nR.string.netfw_c_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    dialog?.let { isIp ->
        AddRuleDialog(
            isIp = isIp,
            initialScope = appId?.let { RuleScope.App(it) } ?: RuleScope.System,
            directory = directory,
            onDismiss = { dialog = null },
            onAdd = { value, port, protocol, ruleScope, action ->
                scope.launch {
                    val result = if (isIp) {
                        fw.addIpRule(IpRule(0, ruleScope, value, port, protocol, action)).map { }
                    } else {
                        fw.addDomainRule(DomainRule(0, ruleScope, value, action)).map { }
                    }
                    if (result.isSuccess) dialog = null
                    else Toast.makeText(context, context.getString(I18nR.string.netfw_c_invalid), Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}

@Composable
internal fun AddRuleDialog(
    isIp: Boolean,
    directory: AppDirectory,
    onDismiss: () -> Unit,
    onAdd: (value: String, port: Int, protocol: Protocol?, scope: RuleScope, action: RuleAction) -> Unit,
    initialValue: String = "",
    initialScope: RuleScope = RuleScope.System,
    initialAction: RuleAction = RuleAction.Block,
    title: String? = null,
    showAction: Boolean = true,
) {
    var value by remember { mutableStateOf(initialValue) }
    var port by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf<Protocol?>(null) }
    var ruleScope by remember { mutableStateOf(initialScope) }
    var action by remember { mutableStateOf(initialAction) }
    var pickApp by remember { mutableStateOf(false) }
    val apps by directory.apps.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { TelosCyberHeading {  Text(title ?: stringResource(if (isIp) I18nR.string.netfw_c_add_ip else I18nR.string.netfw_c_add_domain))  } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    label = { Text(stringResource(if (isIp) I18nR.string.netfw_c_address else I18nR.string.netfw_c_domain)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isIp) {
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter(Char::isDigit).take(5) },
                        singleLine = true,
                        label = { Text(stringResource(I18nR.string.netfw_c_port)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = protocol == null, onClick = { protocol = null }, label = { Text(stringResource(I18nR.string.netfw_c_protocol_any)) })
                        FilterChip(selected = protocol == Protocol.Tcp, onClick = { protocol = Protocol.Tcp }, label = { Text("TCP") })
                        FilterChip(selected = protocol == Protocol.Udp, onClick = { protocol = Protocol.Udp }, label = { Text("UDP") })
                    }
                }
                Text(stringResource(I18nR.string.netfw_c_scope), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = ruleScope == RuleScope.System, onClick = { ruleScope = RuleScope.System }, label = { Text(stringResource(I18nR.string.netfw_c_all_apps)) })
                    FilterChip(
                        selected = ruleScope is RuleScope.App,
                        onClick = { pickApp = true },
                        label = {
                            Text(
                                (ruleScope as? RuleScope.App)?.let { directory.labelFor(it.appId) }
                                    ?: stringResource(I18nR.string.netfw_c_one_app)
                            )
                        },
                    )
                }
                Box {
                    DropdownMenu(expanded = pickApp, onDismissRequest = { pickApp = false }) {
                        var appQuery by remember { mutableStateOf("") }
                        de.mm20.launcher2.ui.component.TelosSearchBar(appQuery, { appQuery = it }, stringResource(I18nR.string.hc_search))
                        apps.filter { it.hasInternet && de.mm20.launcher2.comms.search.TelosSearch.matches(appQuery, it.label, it.packageName) }.forEach { app ->
                            DropdownMenuItem(
                                text = { Text(app.label) },
                                leadingIcon = { NetAppIcon(app.packageName, 24.dp) },
                                onClick = { ruleScope = RuleScope.App(app.appId); pickApp = false },
                            )
                        }
                    }
                }
                if (showAction) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = action == RuleAction.Block, onClick = { action = RuleAction.Block }, label = { Text(stringResource(I18nR.string.netfw_c_action_block)) })
                    FilterChip(selected = action == RuleAction.Trust, onClick = { action = RuleAction.Trust }, label = { Text(stringResource(I18nR.string.netfw_c_action_trust)) })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = value.isNotBlank(),
                onClick = { onAdd(value.trim(), port.toIntOrNull() ?: 0, protocol, ruleScope, action) },
            ) { Text(stringResource(I18nR.string.hc_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.hc_cancel)) } },
    )
}
