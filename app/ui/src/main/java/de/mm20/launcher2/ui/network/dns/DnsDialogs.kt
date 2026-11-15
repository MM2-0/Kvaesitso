package de.mm20.launcher2.ui.network.dns

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.network.api.DnsKind
import de.mm20.launcher2.network.api.DnsServer
import de.mm20.launcher2.network.api.InsecureDnsUrlException
import de.mm20.launcher2.network.impl.dns.DnsCatalog
import de.mm20.launcher2.network.impl.dns.NextDns
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.launch

private val FORM_KINDS = listOf(
    DnsKind.Doh, DnsKind.Dot, DnsKind.DnsCrypt, DnsKind.Odoh, DnsKind.DnsProxy, DnsKind.Plain,
)

/** Add or edit a custom server of any kind, with validation and a latency test. */
@Composable
internal fun DnsServerForm(
    initial: DnsServer,
    isNew: Boolean,
    validate: (DnsServer) -> Result<Unit>,
    test: suspend (DnsServer) -> Result<Long>,
    onSave: (DnsServer, (Result<Unit>) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(if (initial.kind == DnsKind.System) DnsKind.Doh else initial.kind) }
    var name by remember { mutableStateOf(initial.name) }
    var url by remember { mutableStateOf(initial.url) }
    var relay by remember { mutableStateOf(initial.relay.orEmpty()) }
    var bootstrap by remember { mutableStateOf(initial.bootstrapIps.joinToString(", ")) }
    var error by remember { mutableStateOf(false) }
    var insecure by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Result<Long>?>(null) }

    fun build() = initial.copy(
        kind = kind,
        name = name.trim(),
        url = url.trim(),
        relay = relay.trim().ifEmpty { null },
        bootstrapIps = bootstrap.split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() },
    )

    val urlLabel = stringResource(
        when (kind) {
            DnsKind.Doh -> I18nR.string.net_dns_field_doh
            DnsKind.Dot -> I18nR.string.net_dns_field_dot
            DnsKind.DnsCrypt -> I18nR.string.net_dns_field_stamp
            DnsKind.Odoh -> I18nR.string.net_dns_field_odoh_target
            DnsKind.DnsProxy -> I18nR.string.net_dns_field_proxy
            else -> I18nR.string.net_dns_field_plain
        }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { TelosCyberHeading {  Text(stringResource(if (isNew) I18nR.string.net_dns_form_add else I18nR.string.net_dns_form_edit))  } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FORM_KINDS.forEach { k ->
                        FilterChip(selected = kind == k, onClick = { kind = k; error = false; insecure = false }, label = { Text(kindLabel(k)) })
                    }
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text(stringResource(I18nR.string.net_dns_field_name)) }, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it; error = false; insecure = false },
                    label = { Text(urlLabel) }, modifier = Modifier.fillMaxWidth(),
                    isError = error, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    supportingText = if (error) ({ Text(stringResource(if (insecure) I18nR.string.au2_netsec_http_not_allowed else I18nR.string.net_dns_invalid)) }) else null,
                )
                if (kind == DnsKind.DnsCrypt) {
                    Text(stringResource(I18nR.string.net_dns_field_relay), style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = relay.isEmpty(), onClick = { relay = "" }, label = { Text(stringResource(I18nR.string.net_dns_relay_none)) })
                        DnsCatalog.dnscryptRelays.forEach { r ->
                            FilterChip(selected = relay == r.stamp, onClick = { relay = r.stamp }, label = { Text(r.name) })
                        }
                    }
                }
                if (kind == DnsKind.DnsCrypt || kind == DnsKind.Odoh) {
                    OutlinedTextField(
                        value = relay, onValueChange = { relay = it; error = false },
                        label = { Text(stringResource(if (kind == DnsKind.Odoh) I18nR.string.net_dns_field_odoh_relay else I18nR.string.net_dns_field_relay_stamp)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (kind == DnsKind.Doh || kind == DnsKind.Dot) {
                    OutlinedTextField(
                        value = bootstrap, onValueChange = { bootstrap = it },
                        label = { Text(stringResource(I18nR.string.net_dns_field_bootstrap)) }, modifier = Modifier.fillMaxWidth(),
                    )
                }
                when {
                    testing -> Text(stringResource(I18nR.string.net_dns_test_running))
                    testResult != null -> {
                        val r = testResult!!
                        Text(
                            r.fold(
                                { stringResource(I18nR.string.net_dns_test_ok, it) },
                                { stringResource(I18nR.string.net_dns_test_fail, it.message ?: it.toString()) },
                            ),
                            color = if (r.isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Text(stringResource(I18nR.string.net_dns_vpn_only), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val s = build()
                val v = validate(s)
                insecure = v.exceptionOrNull() is InsecureDnsUrlException
                if (v.isFailure || s.name.isEmpty()) error = true
                else onSave(s) { r ->
                    if (r.isFailure) { insecure = r.exceptionOrNull() is InsecureDnsUrlException; error = true }
                }
            }) { Text(stringResource(I18nR.string.net_dns_save)) }
        },
        dismissButton = {
            Row {
                TextButton(
                    enabled = !testing,
                    onClick = {
                        val s = build()
                        val v = validate(s)
                        if (v.isFailure) {
                            insecure = v.exceptionOrNull() is InsecureDnsUrlException
                            error = true
                            return@TextButton
                        }
                        testing = true
                        scope.launch { testResult = test(s); testing = false }
                    },
                ) { Text(stringResource(I18nR.string.net_dns_action_test)) }
                TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.net_dns_cancel)) }
            }
        },
    )
}

/** Guided entry for NextDNS: configuration ID and optional device name. */
@Composable
internal fun NextDnsDialog(onAdd: (DnsServer) -> Unit, onDismiss: () -> Unit) {
    var id by remember { mutableStateOf("") }
    var device by remember { mutableStateOf("") }
    var dot by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val nameTemplate = stringResource(I18nR.string.net_dns_nextdns_name, "%s")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.net_dns_nextdns))  } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(I18nR.string.net_dns_nextdns_intro), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = id, onValueChange = { id = it; error = false }, singleLine = true,
                    label = { Text(stringResource(I18nR.string.net_dns_nextdns_id)) }, modifier = Modifier.fillMaxWidth(),
                    isError = error,
                    supportingText = if (error) ({ Text(stringResource(I18nR.string.net_dns_nextdns_bad_id)) }) else null,
                )
                OutlinedTextField(
                    value = device, onValueChange = { device = it }, singleLine = true,
                    label = { Text(stringResource(I18nR.string.net_dns_nextdns_device)) }, modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !dot, onClick = { dot = false }, label = { Text("DoH") })
                    FilterChip(selected = dot, onClick = { dot = true }, label = { Text("DoT") })
                }
                if (NextDns.isValidId(id)) {
                    Text(
                        if (dot) NextDns.dotUrl(id, device) else NextDns.dohUrl(id, device),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(stringResource(I18nR.string.net_dns_vpn_only), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (!NextDns.isValidId(id)) error = true
                else {
                    val name = nameTemplate.replace("%s", id.trim())
                    onAdd(if (dot) NextDns.dot(id, device, name) else NextDns.doh(id, device, name))
                }
            }) { Text(stringResource(I18nR.string.net_dns_nextdns_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.net_dns_cancel)) } },
    )
}
