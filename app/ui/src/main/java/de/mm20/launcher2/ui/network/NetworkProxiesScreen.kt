package de.mm20.launcher2.ui.network

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.network.NetworkEngine
import de.mm20.launcher2.network.api.*
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable data object NetworkProxiesRoute : NavKey

@Composable
fun NetworkProxiesScreen() {
    val controller: ProxyController = koinInject()
    val engine: NetworkEngine = koinInject()
    val endpoints by controller.endpoints.collectAsState()
    val selected by controller.systemDefault.collectAsState()
    val ready by controller.ready.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ProxyEndpoint?>(null) }
    var remove by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun perform(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { action(); engine.closeAllConnections() }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { Toast.makeText(context, I18nR.string.pdft_error_title, Toast.LENGTH_LONG).show() }
            finally { busy = false }
        }
    }
    PreferenceScreen(title = stringResource(I18nR.string.netproxy_title), bottomBar = { NetworkNavigationBar(NetworkSection.Settings) }, topBarActions = {
        IconButton(onClick = { /* TODO: FoxyProxy Import */ }, enabled = !busy) { Icon(painterResource(BaseR.drawable.download_24px), "Import") }
        IconButton(onClick = { editing = null; adding = true }, enabled = !busy) { Icon(painterResource(BaseR.drawable.add_24px), stringResource(I18nR.string.hc_add)) }
    }) {
        item { Text(stringResource(I18nR.string.netproxy_hint), Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }
        item {
            val choices = listOf(ListPreferenceItem<Int?>(stringResource(I18nR.string.dl_t_select_none), null)) + endpoints.map { ListPreferenceItem<Int?>(it.name, it.id) } +
                if (selected != null && endpoints.none { it.id == selected }) listOf(ListPreferenceItem<Int?>(stringResource(I18nR.string.netproxy_missing, selected!!), selected)) else emptyList()
            ListPreference(title = stringResource(I18nR.string.netproxy_default), items = choices, value = selected,
                enabled = !busy, onValueChanged = { id -> perform { controller.setDefault(id) } })
        }
        items(endpoints.size, key = { endpoints[it].id }) { index ->
            val endpoint = endpoints[index]
            PreferenceCategory {
                SwitchPreference(title = endpoint.name, summary = (if (endpoint.kind == ProxyKind.Socks5) "SOCKS5" else "HTTP CONNECT") + " · " + endpoint.host + ":" + endpoint.port,
                    value = endpoint.enabled, enabled = !busy, onValueChanged = { enabled -> perform { controller.setEnabled(endpoint.id, enabled) } })
                Preference(title = stringResource(if (endpoint.id in ready) I18nR.string.netproxy_registered else I18nR.string.netproxy_not_registered),
                    controls = { FlowRow {
                        TextButton(onClick = { editing = endpoint; adding = true }, enabled = !busy) { Text(stringResource(I18nR.string.hc_edit)) }
                        TextButton(onClick = { remove = endpoint.id }, enabled = !busy) { Text(stringResource(I18nR.string.pdft_remove)) }
                    } })
            }
        }
    }
    remove?.let { id ->
        AlertDialog(onDismissRequest = { remove = null }, title = { TelosCyberHeading {  Text(stringResource(I18nR.string.pdft_remove))  } },
            text = { Text(stringResource(I18nR.string.netproxy_remove_hint)) },
            confirmButton = { TextButton(onClick = { remove = null; perform { controller.remove(id) } }) { Text(stringResource(I18nR.string.pdft_remove)) } },
            dismissButton = { TextButton(onClick = { remove = null }) { Text(stringResource(I18nR.string.hc_cancel)) } })
    }
    if (adding) {
        var name by remember { mutableStateOf(editing?.name.orEmpty()) }; var host by remember { mutableStateOf(editing?.host.orEmpty()) }; var port by remember { mutableStateOf(editing?.port?.toString() ?: "1080") }
        var username by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var kind by remember { mutableStateOf(editing?.kind ?: ProxyKind.Socks5) }
        var ja3Fingerprint by remember { mutableStateOf(editing?.ja3Fingerprint.orEmpty()) }
        AlertDialog(onDismissRequest = { if (!busy) adding = false }, title = { TelosCyberHeading {  Text(stringResource(I18nR.string.netproxy_title))  } },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (editing != null) Text(stringResource(I18nR.string.netproxy_reenter_credentials), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = kind == ProxyKind.Socks5, onClick = { kind = ProxyKind.Socks5; port = "1080" }, label = { Text("SOCKS5") })
                    FilterChip(selected = kind == ProxyKind.HttpConnect, onClick = { kind = ProxyKind.HttpConnect; port = "8080" }, label = { Text("HTTP CONNECT") })
                    FilterChip(selected = kind == ProxyKind.Ssh, onClick = { kind = ProxyKind.Ssh; port = "22" }, label = { Text("SSH") })
                    FilterChip(selected = kind == ProxyKind.HttpsConnect, onClick = { kind = ProxyKind.HttpsConnect; port = "443" }, label = { Text("HTTPS CONNECT") })
                    FilterChip(selected = kind == ProxyKind.Masque, onClick = { kind = ProxyKind.Masque; port = "443" }, label = { Text("MASQUE (HTTP/3)") })
                }
                OutlinedTextField(name, { name = it.take(80) }, label = { Text(stringResource(I18nR.string.netproxy_name)) }, enabled = !busy)
                OutlinedTextField(host, { host = it.take(253) }, label = { Text(stringResource(I18nR.string.netproxy_host)) }, enabled = !busy, singleLine = true)
                OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, label = { Text(stringResource(I18nR.string.netproxy_port)) }, enabled = !busy, singleLine = true)
                OutlinedTextField(username, { username = it.take(512) }, label = { Text(stringResource(I18nR.string.netproxy_username)) }, enabled = !busy, singleLine = true)
                OutlinedTextField(password, { password = it.take(512) }, label = { Text(stringResource(I18nR.string.netproxy_password)) }, enabled = !busy, singleLine = true, visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(ja3Fingerprint, { ja3Fingerprint = it.take(200) }, label = { Text("Custom JA3 TLS Fingerprint (Optional)") }, enabled = !busy, singleLine = true)
            } },
            confirmButton = { TextButton(onClick = {
                val numericPort = port.toIntOrNull() ?: return@TextButton
                perform {
                    val id = editing?.id
                    if (id == null) controller.add(name, kind, host, numericPort, username, password, null, ja3Fingerprint.takeIf { it.isNotBlank() })
                    else controller.update(id, name, kind, host, numericPort, username, password, null, ja3Fingerprint.takeIf { it.isNotBlank() })
                    password = ""; adding = false; editing = null
                }
            }, enabled = !busy && name.isNotBlank() && host.isNotBlank() && (port.toIntOrNull() ?: 0) in 1..65535) { Text(stringResource(I18nR.string.hc_save)) } },
            dismissButton = { TextButton(onClick = { adding = false }, enabled = !busy) { Text(stringResource(I18nR.string.hc_cancel)) } })
    }
}

@Composable
internal fun NetworkProxyAssignmentPreference(appId: Int) {
    val controller: ProxyController = koinInject()
    val engine: NetworkEngine = koinInject()
    val endpoints by controller.endpoints.collectAsState()
    val assignments by controller.assignments.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val assignment = assignments[appId] ?: ProxyAssignment.SystemDefault
    val choices = listOf(ListPreferenceItem<ProxyAssignment>(stringResource(I18nR.string.netproxy_follow_default), ProxyAssignment.SystemDefault),
        ListPreferenceItem<ProxyAssignment>(stringResource(I18nR.string.netproxy_direct), ProxyAssignment.Direct)) +
        endpoints.map { ListPreferenceItem<ProxyAssignment>(it.name, ProxyAssignment.Endpoint(it.id)) } +
        if (assignment is ProxyAssignment.Endpoint && endpoints.none { it.id == assignment.id }) listOf(ListPreferenceItem<ProxyAssignment>(stringResource(I18nR.string.netproxy_missing, assignment.id), assignment)) else emptyList()
    ListPreference(title = stringResource(I18nR.string.netproxy_title), items = choices, value = assignment, onValueChanged = { next ->
        scope.launch {
            try { controller.assign(appId, next); engine.closeAllConnections() }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { Toast.makeText(context, I18nR.string.pdft_error_title, Toast.LENGTH_LONG).show() }
        }
    })
}
