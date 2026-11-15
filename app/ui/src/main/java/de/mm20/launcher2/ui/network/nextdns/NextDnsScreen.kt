package de.mm20.launcher2.ui.network.nextdns

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.network.nextdns.NextDnsErrorKind
import de.mm20.launcher2.network.nextdns.NextDnsBooleanSetting
import de.mm20.launcher2.network.nextdns.NextDnsSettingsPage
import de.mm20.launcher2.network.nextdns.NextDnsSettingsValues
import de.mm20.launcher2.network.nextdns.NextDnsAnalyticsMetric
import de.mm20.launcher2.network.nextdns.NextDnsAnalyticsPeriod
import de.mm20.launcher2.network.nextdns.NextDnsRewrite
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import kotlinx.serialization.Serializable

@Serializable
data object NextDnsRoute : NavKey

@Composable
fun NextDnsScreen() {
    val vm: NextDnsVM = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var profile by rememberSaveable { mutableStateOf("") }
    // Account-wide API keys never enter saved instance state or the ViewModel.
    var key by remember { mutableStateOf("") }
    var settings by rememberSaveable { mutableStateOf(false) }
    var tab by de.mm20.launcher2.ui.component.rememberPersistentUiState("nextdns.tab", 0)
    var query by rememberSaveable { mutableStateOf("") }
    var blockedOnly by rememberSaveable { mutableStateOf(false) }
    var editDomain by remember { mutableStateOf<String?>(null) }
    var editAllow by remember { mutableStateOf(true) }
    var remove by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var editRewrite by remember { mutableStateOf(false) }
    var removeRewrite by remember { mutableStateOf<NextDnsRewrite?>(null) }
    var rewriteName by rememberSaveable { mutableStateOf("") }
    var rewriteContent by rememberSaveable { mutableStateOf("") }
    var forget by remember { mutableStateOf(false) }
    var selectedSettingsPage by rememberSaveable { mutableStateOf(NextDnsSettingsPage.SECURITY.name) }
    var requestedSettingsPage by remember { mutableStateOf<NextDnsSettingsPage?>(null) }
    LaunchedEffect(state.profileId) { state.profileId?.let { profile = it } }
    val settingsPage = remember(selectedSettingsPage) {
        NextDnsSettingsPage.valueOf(selectedSettingsPage)
    }
    LaunchedEffect(tab, settingsPage, state.connected, state.loading) {
        if (!state.connected) {
            requestedSettingsPage = null
        } else if (tab == 3 && !state.loading && requestedSettingsPage != settingsPage) {
            requestedSettingsPage = settingsPage
            vm.loadSettings(settingsPage)
        }
    }
    var analyticsProfileLoaded by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tab, state.connected, state.loading, state.profileId) {
        if (!state.connected || tab != 4) {
            analyticsProfileLoaded = null
        } else if (!state.loading && analyticsProfileLoaded != state.profileId) {
            analyticsProfileLoaded = state.profileId
            vm.loadAnalytics()
        }
    }
    var rewriteProfileLoaded by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tab, state.connected, state.loading, state.profileId) {
        if (!state.connected || tab != 5) {
            rewriteProfileLoaded = null
        } else if (!state.loading && rewriteProfileLoaded != state.profileId) {
            rewriteProfileLoaded = state.profileId
            vm.loadRewrites()
        }
    }
    val settingsData = state.profileSettings[settingsPage]
    val booleanSettings = remember(settingsData) {
        settingsData?.let { NextDnsSettingsValues.booleans(it.values) }.orEmpty()
    }
    val rules = if (tab == 1) state.allowed else state.denied
    val visibleRules = remember(rules, query) { rules.filter { query.isBlank() || it.id.contains(query.trim(), true) } }
    PreferenceScreen(title = { Text(stringResource(I18nR.string.nd_title)) }, topBarActions = {
        IconButton(enabled = state.connected && !state.loading, onClick = {
            if (tab == 3) vm.loadSettings(settingsPage)
            else if (tab == 4) vm.loadAnalytics()
            else if (tab == 5) vm.loadRewrites()
            else vm.refresh(query, if (blockedOnly) "blocked" else null)
        }) {
            Icon(painterResource(BaseR.drawable.restart_alt_24px), stringResource(I18nR.string.hc_refresh))
        }
    }) {
        item {
            ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(I18nR.string.nd_intro), style = MaterialTheme.typography.bodyMedium)
                    if (state.connected) {
                        Text("${stringResource(I18nR.string.nd_profile)}: ${state.profileId.orEmpty()}", style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(enabled = !state.loading, onClick = { settings = !settings }) { Text(stringResource(I18nR.string.nd_key)) }
                            TextButton(enabled = !state.loading, onClick = { forget = true }) { Text(stringResource(I18nR.string.nd_disconnect)) }
                            OutlinedButton(enabled = !state.loading, onClick = vm::applyDns) { Text(stringResource(I18nR.string.nd_apply)) }
                        }
                        Text(stringResource(I18nR.string.net_dns_vpn_only), style = MaterialTheme.typography.bodySmall)
                    }
                    if (!state.connected || settings) {
                        var loginDialog by remember { mutableStateOf(false) }
                        if (loginDialog) {
                            NextDnsLoginDialog(
                                onDismiss = { loginDialog = false },
                                onSuccess = { pid, pkey ->
                                    profile = pid
                                    key = pkey
                                    loginDialog = false
                                    vm.connect(profile, key)
                                    key = ""
                                }
                            )
                        }
                        
                        Text(stringResource(I18nR.string.nd_help), style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(profile, { profile = it.trim().take(6) }, enabled = !state.loading,
                            label = { Text(stringResource(I18nR.string.nd_profile)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(key, { key = it }, enabled = !state.loading,
                            label = { Text(stringResource(I18nR.string.nd_key)) }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = !state.loading && profile.length == 6 && key.isNotBlank(), onClick = { vm.connect(profile, key); key = "" }) {
                                Text(stringResource(I18nR.string.nd_connect))
                            }
                            if (!state.connected) {
                                TextButton(enabled = !state.loading, onClick = { loginDialog = true }) { Text("Web Sign In") }
                            }
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("📖 Οδηγίες Εύρεσης Στοιχείων NextDNS:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                Text("1. Συνδεθείτε στο my.nextdns.io στον φυλλομετρητή σας.", style = MaterialTheme.typography.bodySmall)
                                Text("2. Profile ID: Στην καρτέλα Setup, δείτε τον 6ψήφιο κωδικό δίπλα στο \"ID\" (π.χ. abc123).", style = MaterialTheme.typography.bodySmall)
                                Text("3. API Key: Πηγαίνετε στο my.nextdns.io/account (Settings), κατεβείτε στα API Keys και πατήστε \"Show\".", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (state.dnsApplied) Text(stringResource(I18nR.string.nd_applied), color = MaterialTheme.colorScheme.primary)
                    state.error?.let { Text(stringResource(errorString(it)), color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        if (state.connected) {
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            I18nR.string.nd_logs,
                            I18nR.string.nd_allowlist,
                            I18nR.string.nd_denylist,
                            I18nR.string.net_settings_title,
                            I18nR.string.net_hub_status,
                            I18nR.string.nd_rule_title,
                        ).forEachIndexed { index, label ->
                            FilterChip(tab == index, onClick = { tab = index }, label = { Text(stringResource(label)) })
                        }
                    }
                    if (tab == 3) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NextDnsSettingsPage.entries.forEach { page ->
                                FilterChip(
                                    selected = settingsPage == page,
                                    onClick = { selectedSettingsPage = page.name },
                                    label = { Text(stringResource(settingsPageTitle(page))) },
                                )
                            }
                        }
                        Text(stringResource(I18nR.string.nd_intro), style = MaterialTheme.typography.bodySmall)
                    } else if (tab == 5) {
                        TextButton(
                            enabled = !state.loading,
                            onClick = {
                                rewriteName = ""
                                rewriteContent = ""
                                editRewrite = true
                            },
                        ) {
                            Text(stringResource(I18nR.string.hc_add))
                        }
                    } else if (tab != 4) {
                        OutlinedTextField(query, { query = it.take(250) }, label = { Text(stringResource(I18nR.string.hc_search)) }, singleLine = true,
                            modifier = Modifier.fillMaxWidth())
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (tab == 0) FilterChip(blockedOnly, onClick = { blockedOnly = !blockedOnly; vm.refresh(query, if (blockedOnly) "blocked" else null) }, label = { Text(stringResource(I18nR.string.nd_denylist)) })
                            TextButton(enabled = !state.loading, onClick = { vm.refresh(query, if (blockedOnly) "blocked" else null) }) { Text(stringResource(I18nR.string.hc_search)) }
                            if (tab != 0) TextButton(enabled = !state.loading, onClick = { editAllow = tab == 1; editDomain = "" }) { Text(stringResource(I18nR.string.hc_add)) }
                        }
                    }
                }
            }
            when (tab) {
                0 -> {
                    if (state.logs.isEmpty() && !state.loading) {
                        item { Text(stringResource(I18nR.string.nd_empty), Modifier.padding(20.dp)) }
                    }
                    itemsIndexed(state.logs, key = { index, log -> "${log.timestamp}:${log.domain}:$index" }) { _, log ->
                        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(log.domain, style = MaterialTheme.typography.titleMedium)
                                val label = when (log.status) {
                                    "blocked" -> I18nR.string.nd_deny
                                    "allowed" -> I18nR.string.nd_allow
                                    else -> I18nR.string.nd_logs
                                }
                                Text(
                                    stringResource(label),
                                    color = if (log.status == "blocked") MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    listOfNotNull(log.timestamp, log.deviceName, log.protocol).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (log.reasons.isNotEmpty()) {
                                    Text(log.reasons.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(
                                        enabled = !state.loading,
                                        onClick = { editAllow = true; editDomain = log.domain },
                                    ) { Text(stringResource(I18nR.string.nd_allow)) }
                                    TextButton(
                                        enabled = !state.loading,
                                        onClick = { editAllow = false; editDomain = log.domain },
                                    ) { Text(stringResource(I18nR.string.nd_deny)) }
                                }
                            }
                        }
                    }
                    if (state.cursor != null) {
                        item {
                            TextButton(
                                enabled = !state.loading,
                                onClick = vm::more,
                                modifier = Modifier.padding(16.dp),
                            ) { Text(stringResource(I18nR.string.nd_more)) }
                        }
                    }
                }
                1, 2 -> {
                    if (visibleRules.isEmpty() && !state.loading) {
                        item { Text(stringResource(I18nR.string.nd_empty), Modifier.padding(20.dp)) }
                    }
                    items(visibleRules, key = { "$tab:${it.id}" }) { rule ->
                        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(rule.id, style = MaterialTheme.typography.titleMedium)
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Switch(
                                        rule.active,
                                        onCheckedChange = { vm.toggle(tab == 1, rule.id, it) },
                                        enabled = !state.loading,
                                    )
                                    TextButton(
                                        enabled = !state.loading,
                                        onClick = { remove = (tab == 1) to rule.id },
                                    ) { Text(stringResource(I18nR.string.hc_remove)) }
                                }
                            }
                        }
                    }
                }
                3 -> {
                    if ((settingsData == null || booleanSettings.isEmpty()) && !state.loading) {
                        item { Text(stringResource(I18nR.string.nd_empty), Modifier.padding(20.dp)) }
                    } else {
                        items(booleanSettings, key = { it.path.joinToString(".") }) { setting ->
                            SwitchPreference(
                                title = setting.path.joinToString(" / ", transform = ::humanize),
                                value = setting.value,
                                enabled = !state.loading,
                                onValueChanged = { vm.toggleSetting(settingsPage, setting, it) },
                            )
                        }
                    }
                }
                4 -> {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(stringResource(I18nR.string.net_hub_status), style = MaterialTheme.typography.titleMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NextDnsAnalyticsPeriod.entries.forEach { period ->
                                    FilterChip(
                                        selected = state.analyticsPeriod == period,
                                        onClick = { vm.loadAnalytics(period = period) },
                                        enabled = !state.loading,
                                        label = { Text(period.queryValue.removePrefix("-")) },
                                    )
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = state.analyticsDeviceId == null,
                                    onClick = { vm.loadAnalytics(deviceId = null) },
                                    enabled = !state.loading,
                                    label = { Text(stringResource(I18nR.string.net_dns_filter_all)) },
                                )
                                state.analyticsDevices.forEach { device ->
                                    FilterChip(
                                        selected = state.analyticsDeviceId == device.id,
                                        onClick = { vm.loadAnalytics(deviceId = device.id) },
                                        enabled = !state.loading,
                                        label = { Text(device.name) },
                                    )
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NextDnsAnalyticsMetric.entries.forEach { metric ->
                                    FilterChip(
                                        selected = state.analyticsMetric == metric,
                                        onClick = { vm.loadAnalytics(metric = metric) },
                                        enabled = !state.loading,
                                        label = { Text(analyticsMetricLabel(metric)) },
                                    )
                                }
                            }
                        }
                    }
                    val snapshot = state.analytics
                    if (snapshot != null) {
                        item {
                            val seriesSummary = remember(snapshot.series) {
                                snapshot.series.take(48).joinToString(" · ")
                            }
                            ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(analyticsMetricLabel(snapshot.metric), style = MaterialTheme.typography.titleMedium)
                                    Text(snapshot.totalQueries.toString(), style = MaterialTheme.typography.headlineMedium)
                                    Text(
                                        "${snapshot.metric.feature} · ${state.analyticsPeriod.queryValue}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    if (seriesSummary.isNotEmpty()) {
                                        Text(seriesSummary, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        itemsIndexed(snapshot.rows, key = { index, row -> "${snapshot.metric.id}:${row.label}:$index" }) { _, row ->
                            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(row.label, style = MaterialTheme.typography.titleSmall)
                                    if (row.value.isNotEmpty()) Text(row.value)
                                    row.details?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                        }
                        if (snapshot.nextCursor != null && snapshot.rows.size < 500) {
                            item {
                                TextButton(
                                    enabled = !state.loading,
                                    onClick = vm::moreAnalytics,
                                    modifier = Modifier.padding(16.dp),
                                ) { Text(stringResource(I18nR.string.nd_more)) }
                            }
                        }
                        if (snapshot.rows.isEmpty() && snapshot.series.isEmpty()) {
                            item { Text(stringResource(I18nR.string.nd_empty), Modifier.padding(20.dp)) }
                        }
                    } else if (!state.loading) {
                        item { Text(stringResource(I18nR.string.nd_empty), Modifier.padding(20.dp)) }
                    }
                }
                5 -> {
                    if (state.rewrites.isEmpty() && !state.loading) {
                        item { Text(stringResource(I18nR.string.nd_empty), Modifier.padding(20.dp)) }
                    }
                    items(state.rewrites, key = { it.id }) { rewrite ->
                        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(rewrite.name, style = MaterialTheme.typography.titleMedium)
                                Text("${rewrite.content} · ${rewrite.type}", style = MaterialTheme.typography.bodySmall)
                                TextButton(
                                    enabled = !state.loading,
                                    onClick = { removeRewrite = rewrite },
                                ) { Text(stringResource(I18nR.string.hc_remove)) }
                            }
                        }
                    }
                }
            }
        }
    }
        editDomain?.let { initial ->
            EditDomainDialog(
                initial = initial,
                allow = editAllow,
                onDismiss = { editDomain = null },
                onSave = { allow: Boolean, domain: String -> vm.put(allow, domain) },
                loading = state.loading,
                profileId = state.profileId.orEmpty(),
            )
        }
    remove?.let { target -> AlertDialog(onDismissRequest = { remove = null }, title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_remove))  } }, text = {
        Text("${state.profileId.orEmpty()} · ${target.second}\n${stringResource(if (target.first) I18nR.string.nd_allowlist else I18nR.string.nd_denylist)}\n${stringResource(I18nR.string.nd_intro)}")
    }, confirmButton = { TextButton(enabled = !state.loading, onClick = { vm.delete(target.first, target.second); remove = null }) { Text(stringResource(I18nR.string.hc_remove)) } }, dismissButton = { TextButton(onClick = { remove = null }) { Text(stringResource(I18nR.string.hc_cancel)) } }) }
    if (editRewrite) AlertDialog(
        onDismissRequest = { editRewrite = false },
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.nd_rule_title))  } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    rewriteName,
                    { rewriteName = it.take(253) },
                    label = { Text(stringResource(I18nR.string.net_dns_field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    rewriteContent,
                    { rewriteContent = it.take(253) },
                    label = { Text(stringResource(I18nR.string.net_dns_detail_address)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !state.loading && rewriteName.isNotBlank() && rewriteContent.isNotBlank(),
                onClick = {
                    vm.addRewrite(rewriteName, rewriteContent)
                    editRewrite = false
                },
            ) { Text(stringResource(I18nR.string.hc_save)) }
        },
        dismissButton = {
            TextButton(onClick = { editRewrite = false }) { Text(stringResource(I18nR.string.hc_cancel)) }
        },
    )
    removeRewrite?.let { rewrite ->
        AlertDialog(
            onDismissRequest = { removeRewrite = null },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_remove))  } },
            text = { Text("${rewrite.name} → ${rewrite.content}") },
            confirmButton = {
                TextButton(
                    enabled = !state.loading,
                    onClick = {
                        vm.deleteRewrite(rewrite.id)
                        removeRewrite = null
                    },
                ) { Text(stringResource(I18nR.string.hc_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { removeRewrite = null }) {
                    Text(stringResource(I18nR.string.hc_cancel))
                }
            },
        )
    }
    if (forget) AlertDialog(onDismissRequest = { forget = false }, title = { TelosCyberHeading {  Text(stringResource(I18nR.string.nd_disconnect))  } }, text = { Text(state.profileId.orEmpty()) },
        confirmButton = { TextButton(enabled = !state.loading, onClick = { vm.disconnect(); key = ""; settings = false; forget = false }) { Text(stringResource(I18nR.string.hc_remove)) } }, dismissButton = { TextButton(onClick = { forget = false }) { Text(stringResource(I18nR.string.hc_cancel)) } })
}

private fun errorString(kind: NextDnsErrorKind): Int = when (kind) {
    NextDnsErrorKind.AUTH -> I18nR.string.nd_error_auth
    NextDnsErrorKind.INVALID -> I18nR.string.nd_error_invalid
    NextDnsErrorKind.RATE_LIMIT -> I18nR.string.nd_error_rate
    NextDnsErrorKind.NETWORK -> I18nR.string.nd_error_network
    NextDnsErrorKind.API -> I18nR.string.nd_error_api
    NextDnsErrorKind.UNCONFIGURED -> I18nR.string.nd_error_unconfigured
}

private val settingWordBoundaryRegex = Regex("([a-z0-9])([A-Z])")

private fun humanize(value: String): String =
    settingWordBoundaryRegex.replace(value.replace('_', ' '), "$1 $2")
        .replaceFirstChar { it.uppercase() }

private fun analyticsMetricLabel(metric: NextDnsAnalyticsMetric): String = metric.id

private fun settingsPageTitle(page: NextDnsSettingsPage): Int = when (page) {
    NextDnsSettingsPage.SECURITY -> I18nR.string.net_dns_group_malware
    NextDnsSettingsPage.PRIVACY -> I18nR.string.net_dns_tag_privacy
    NextDnsSettingsPage.PARENTAL_CONTROL -> I18nR.string.net_dns_group_family
    NextDnsSettingsPage.GENERAL -> I18nR.string.net_settings_title
}

@Composable
private fun EditDomainDialog(
    initial: String,
    allow: Boolean,
    onDismiss: () -> Unit,
    onSave: (Boolean, String) -> Unit,
    loading: Boolean,
    profileId: String,
) {
    var domain by remember(initial) { mutableStateOf(initial) }
    var editAllow by remember(allow) { mutableStateOf(allow) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.nd_rule_title))  } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${stringResource(I18nR.string.nd_profile)}: $profileId")
                Text(stringResource(I18nR.string.nd_intro))
                OutlinedTextField(domain, { domain = it }, label = { Text(stringResource(I18nR.string.nd_domain)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(editAllow, onClick = { editAllow = true }, label = { Text(stringResource(I18nR.string.nd_allow)) })
                    FilterChip(!editAllow, onClick = { editAllow = false }, label = { Text(stringResource(I18nR.string.nd_deny)) })
                }
            }
        },
        confirmButton = { TextButton(enabled = !loading && domain.isNotBlank(), onClick = { onSave(editAllow, domain); onDismiss() }) { Text(stringResource(I18nR.string.hc_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.hc_cancel)) } },
    )
}
