package de.mm20.launcher2.ui.network.dns

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.network.NetState
import de.mm20.launcher2.network.api.DnsKind
import de.mm20.launcher2.network.api.DnsServer
import de.mm20.launcher2.network.api.DnsTag
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object NetworkDnsRoute : NavKey

private typealias IconsNetworkDnsScreen = de.mm20.launcher2.base.R.drawable

/** Encrypted DNS of Telos Network: choose a built-in or custom server. Only applies while the VPN runs. */
@Composable
fun NetworkDnsScreen() {
    val vm: NetworkDnsVM = viewModel()
    val backStack = de.mm20.launcher2.ui.locals.LocalBackStack.current
    val context = LocalContext.current
    val servers by vm.servers.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val engineState by vm.engineState.collectAsStateWithLifecycle()

    var dnsQuery by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf<String?>(null) }
    val filter = filterName?.let { n -> DnsTag.values().firstOrNull { it.name == n } }
    var details by remember { mutableStateOf<DnsServer?>(null) }
    var form by remember { mutableStateOf<DnsServer?>(null) }
    var formIsNew by remember { mutableStateOf(true) }
    var nextDns by remember { mutableStateOf(false) }

    val select: (DnsServer) -> Unit = { s ->
        vm.select(s.id) { msg ->
            Toast.makeText(context, context.getString(I18nR.string.net_dns_select_failed, msg), Toast.LENGTH_LONG).show()
        }
    }

    val activeFilter = filter
    val visible = servers.filter { it.kind == DnsKind.System || !it.builtIn || activeFilter == null || it.tags.contains(activeFilter) }
        .filter { dnsQuery.isBlank() || de.mm20.launcher2.comms.search.TelosSearch.matches(dnsQuery, it.name, it.provider, it.url, it.description, it.relay, *it.tags.map { t -> t.name }.toTypedArray(), *it.tags.map { t -> tagText(t) }.toTypedArray()) }
    val systemServer = visible.filter { it.kind == DnsKind.System }
    val custom = visible.filter { !it.builtIn }
    val groups = DnsTag.values().map { tag ->
        tag to visible.filter { it.builtIn && it.kind != DnsKind.System && it.tags.firstOrNull() == tag }
    }.filter { it.second.isNotEmpty() }

    PreferenceScreen(title = stringResource(I18nR.string.net_dns_title)) {
        item {
            PreferenceCategory {
                Preference(
                    title = { Text(engineStateText(engineState)) },
                    summary = { Text(stringResource(I18nR.string.net_dns_vpn_only)) },
                    icon = { Icon(painterResource(IconsNetworkDnsScreen.info_24px), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                )
            }
        }
        item {
            de.mm20.launcher2.ui.component.TelosSearchBar(dnsQuery, { dnsQuery = it }, stringResource(I18nR.string.hc_search), Modifier.padding(horizontal = 0.dp))
        }
        if (dnsQuery.isNotBlank() && visible.isEmpty()) {
            item { de.mm20.launcher2.ui.component.SearchEmptyState(dnsQuery.trim()) }
        }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = filter == null, onClick = { filterName = null }, label = { Text(stringResource(I18nR.string.net_dns_filter_all)) })
                DnsTag.values().forEach { tag ->
                    FilterChip(selected = filter == tag, onClick = { filterName = tag.name }, label = { Text(tagFilterText(tag)) })
                }
            }
        }
        item {
            PreferenceCategory {
                Preference(
                    title = stringResource(I18nR.string.net_dns_add_custom),
                    icon = IconsNetworkDnsScreen.add_24px,
                    onClick = { formIsNew = true; form = DnsServer("", DnsKind.Doh, "") },
                )
                Preference(
                    title = stringResource(I18nR.string.nd_title),
                    summary = stringResource(I18nR.string.nd_intro),
                    icon = IconsNetworkDnsScreen.edit_24px,
                    onClick = { backStack.add(de.mm20.launcher2.ui.network.nextdns.NextDnsRoute) },
                )
                Preference(
                    title = stringResource(I18nR.string.net_dns_nextdns),
                    summary = stringResource(I18nR.string.net_dns_nextdns_summary),
                    icon = IconsNetworkDnsScreen.add_24px,
                    onClick = { nextDns = true },
                )
            }
        }
        if (systemServer.isNotEmpty()) {
            item {
                PreferenceCategory(title = stringResource(I18nR.string.net_dns_group_system)) {
                    systemServer.forEach { s -> DnsRow(s, selected.id == s.id, { details = s }, { select(s) }) }
                }
            }
        }
        if (custom.isNotEmpty()) {
            item {
                PreferenceCategory(title = stringResource(I18nR.string.net_dns_group_custom)) {
                    custom.forEach { s -> DnsRow(s, selected.id == s.id, { details = s }, { select(s) }) }
                }
            }
        }
        groups.forEach { (tag, list) ->
            item(key = "group-$tag") {
                PreferenceCategory(title = tagGroupText(tag)) {
                    list.forEach { s -> DnsRow(s, selected.id == s.id, { details = s }, { select(s) }) }
                }
            }
        }
    }

    details?.let { s ->
        val current = servers.firstOrNull { it.id == s.id } ?: s
        DnsDetailsSheet(
            server = current,
            isSelected = selected.id == current.id,
            test = { vm.test(it) },
            onUse = { select(current) },
            onCopy = {
                details = null
                formIsNew = true
                form = current.copy(id = "", name = current.name, builtIn = false, tags = emptyList(), provider = "", description = "")
            },
            onEdit = {
                details = null
                formIsNew = false
                form = current
            },
            onRemove = { vm.remove(current.id); details = null },
            onDismiss = { details = null },
        )
    }

    form?.let { draft ->
        DnsServerForm(
            initial = draft,
            isNew = formIsNew,
            validate = { vm.validate(it) },
            test = { vm.test(it) },
            onSave = { s, done ->
                if (formIsNew) vm.add(s) { r -> done(r.map { }); if (r.isSuccess) form = null }
                else vm.update(s) { r -> done(r); if (r.isSuccess) form = null }
            },
            onDismiss = { form = null },
        )
    }

    if (nextDns) {
        NextDnsDialog(
            onAdd = { s ->
                vm.add(s) { r ->
                    if (r.isSuccess) nextDns = false
                    else Toast.makeText(context, context.getString(I18nR.string.net_dns_invalid), Toast.LENGTH_LONG).show()
                }
            },
            onDismiss = { nextDns = false },
        )
    }
}

@Composable
private fun DnsRow(server: DnsServer, isSelected: Boolean, onClick: () -> Unit, onSelect: () -> Unit) {
    val summary = buildString {
        append(kindLabel(server.kind))
        server.tags.firstOrNull()?.let { append(" · "); append(tagText(it)) }
        if (server.provider.isNotEmpty() && server.name != server.provider) { append(" · "); append(server.provider) }
    }
    Preference(
        title = { Text(if (server.kind == DnsKind.System) stringResource(I18nR.string.net_dns_system) else server.name) },
        summary = { Text(summary) },
        icon = { RadioButton(selected = isSelected, onClick = onSelect) },
        onClick = onClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DnsDetailsSheet(
    server: DnsServer,
    isSelected: Boolean,
    test: suspend (DnsServer) -> Result<Long>,
    onUse: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Result<Long>?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (server.kind == DnsKind.System) stringResource(I18nR.string.net_dns_system) else server.name,
                style = MaterialTheme.typography.titleLarge,
            )
            server.tags.forEach { Text(tagText(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Detail(stringResource(I18nR.string.net_dns_detail_protocol), kindLabel(server.kind))
            if (server.provider.isNotEmpty()) Detail(stringResource(I18nR.string.net_dns_detail_provider), server.provider)
            if (server.url.isNotEmpty()) Detail(stringResource(I18nR.string.net_dns_detail_address), server.url)
            if (!server.relay.isNullOrBlank()) Detail(stringResource(I18nR.string.net_dns_detail_relay), server.relay.orEmpty())
            if (server.bootstrapIps.isNotEmpty()) Detail(stringResource(I18nR.string.net_dns_detail_bootstrap), server.bootstrapIps.joinToString(", "))
            if (server.kind == DnsKind.Plain || server.kind == DnsKind.DnsProxy) {
                Text(stringResource(I18nR.string.net_dns_not_encrypted_note), style = MaterialTheme.typography.bodySmall)
            }
            if (server.kind == DnsKind.Odoh && server.relay.isNullOrBlank()) {
                Text(stringResource(I18nR.string.net_dns_odoh_note), style = MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(I18nR.string.net_dns_vpn_only), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            when {
                testing -> Text(stringResource(I18nR.string.net_dns_test_running))
                result != null -> {
                    val r = result!!
                    Text(
                        r.fold(
                            { stringResource(I18nR.string.net_dns_test_ok, it) },
                            { stringResource(I18nR.string.net_dns_test_fail, it.message ?: it.toString()) },
                        ),
                        color = if (r.isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onUse, enabled = !isSelected) { Text(stringResource(I18nR.string.net_dns_action_use)) }
                OutlinedButton(
                    onClick = {
                        testing = true
                        scope.launch { result = test(server); testing = false }
                    },
                    enabled = !testing,
                ) { Text(stringResource(I18nR.string.net_dns_action_test)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (server.kind != DnsKind.System) {
                    OutlinedButton(onClick = onCopy) { Text(stringResource(I18nR.string.net_dns_action_copy)) }
                }
                if (!server.builtIn) {
                    OutlinedButton(onClick = onEdit) { Text(stringResource(I18nR.string.net_dns_action_edit)) }
                    TextButton(onClick = { confirmRemove = true }) { Text(stringResource(I18nR.string.net_dns_action_remove)) }
                }
            }
            Text("", Modifier.padding(bottom = 16.dp))
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.net_dns_remove_title))  } },
            text = { Text(stringResource(I18nR.string.net_dns_remove_message, server.name)) },
            confirmButton = { TextButton(onClick = { confirmRemove = false; onRemove() }) { Text(stringResource(I18nR.string.net_dns_action_remove)) } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(I18nR.string.net_dns_cancel)) } },
        )
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun kindLabel(kind: DnsKind): String = when (kind) {
    DnsKind.System -> stringResource(I18nR.string.net_dns_group_system)
    DnsKind.Plain -> stringResource(I18nR.string.net_dns_kind_plain)
    DnsKind.Doh -> "DoH"
    DnsKind.Dot -> "DoT"
    DnsKind.DnsCrypt -> "DNSCrypt"
    DnsKind.Odoh -> "ODoH"
    DnsKind.DnsProxy -> stringResource(I18nR.string.net_dns_kind_proxy)
}

@Composable
private fun tagText(tag: DnsTag): String = stringResource(
    when (tag) {
        DnsTag.Adblock -> I18nR.string.net_dns_tag_adblock
        DnsTag.Malware -> I18nR.string.net_dns_tag_malware
        DnsTag.Family -> I18nR.string.net_dns_tag_family
        DnsTag.Privacy -> I18nR.string.net_dns_tag_privacy
        DnsTag.Unfiltered -> I18nR.string.net_dns_tag_unfiltered
    }
)

@Composable
private fun tagFilterText(tag: DnsTag): String = stringResource(
    when (tag) {
        DnsTag.Adblock -> I18nR.string.net_dns_filter_adblock
        DnsTag.Malware -> I18nR.string.net_dns_filter_malware
        DnsTag.Family -> I18nR.string.net_dns_filter_family
        DnsTag.Privacy -> I18nR.string.net_dns_filter_privacy
        DnsTag.Unfiltered -> I18nR.string.net_dns_filter_unfiltered
    }
)

@Composable
private fun tagGroupText(tag: DnsTag): String = stringResource(
    when (tag) {
        DnsTag.Adblock -> I18nR.string.net_dns_group_adblock
        DnsTag.Malware -> I18nR.string.net_dns_group_malware
        DnsTag.Family -> I18nR.string.net_dns_group_family
        DnsTag.Privacy -> I18nR.string.net_dns_group_privacy
        DnsTag.Unfiltered -> I18nR.string.net_dns_group_unfiltered
    }
)

@Composable
private fun engineStateText(state: NetState): String = when (state) {
    NetState.Off -> stringResource(I18nR.string.net_dns_engine_off)
    NetState.Starting -> stringResource(I18nR.string.net_dns_engine_starting)
    NetState.On -> stringResource(I18nR.string.net_dns_engine_on)
    is NetState.Error -> stringResource(I18nR.string.net_dns_engine_error, state.message)
    is NetState.NeedsPermission -> stringResource(I18nR.string.net_dns_engine_permission)
}
