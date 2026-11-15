package de.mm20.launcher2.ui.files.remote

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import de.mm20.launcher2.ui.R
import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data object ConnectionsRoute : NavKey

private typealias Icons = de.mm20.launcher2.base.R.drawable

/** Where the saved network and cloud storages are managed. Used in the settings and from Telos Files. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsScreen() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val store = remember { ConnectionStore(context) }
    var list by remember { mutableStateOf(store.all().sortedBy { it.name.lowercase() }) }
    var editing by remember { mutableStateOf<RemoteConnection?>(null) }
    var picking by remember { mutableStateOf(false) }
    var cq by remember { mutableStateOf("") }
    val shownList = list.filter { de.mm20.launcher2.comms.search.TelosSearch.matches(cq, it.name, it.type.label, it.host, it.user) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { de.mm20.launcher2.ui.component.TelosCyberAppBarTitle(stringResource(I18nR.string.hc_cloud_and_network_storage)) },
                navigationIcon = { IconButton(onClick = { backStack.removeLastOrNull() }) { Icon(painterResource(Icons.arrow_back_24px), contentDescription = stringResource(I18nR.string.hc_back)) } },
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { picking = true }) { Icon(painterResource(Icons.add_24px), contentDescription = stringResource(I18nR.string.hc_add)) }
                DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
                    RemoteType.values().forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.label) },
                            onClick = { picking = false; editing = RemoteConnection(UUID.randomUUID().toString(), type, type.label, port = type.defaultPort) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (list.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(painterResource(Icons.cloud_20px), contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(48.dp))
                Text(stringResource(I18nR.string.hc_add_a_nextcloud_owncloud_webdav_sftp_smb), Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else Column(Modifier.fillMaxSize().padding(padding)) {
          de.mm20.launcher2.ui.component.TelosSearchBar(cq, { cq = it }, stringResource(I18nR.string.hc_search))
          if (shownList.isEmpty()) de.mm20.launcher2.ui.component.SearchEmptyState(cq.trim())
          else LazyColumn(Modifier.fillMaxSize()) {
            item(key = "remote_previews") { RemotePreviewSettings() }
            items(shownList, key = { it.id }) { c ->
                ListItem(
                    headlineContent = { Text(c.name) },
                    supportingContent = { Text(c.type.label + if (c.host.isNotEmpty()) " · ${c.host}" else if (c.user.isNotEmpty()) " · ${c.user}" else "") },
                    leadingContent = { Icon(painterResource(if (c.type.cloud) Icons.cloud_20px else Icons.storage_24px), contentDescription = null) },
                    modifier = Modifier.clickable { editing = c },
                )
            }
          }
        }
    }

    editing?.let { draft ->
        ConnectionEditor(
            initial = draft,
            isNew = list.none { it.id == draft.id },
            onSave = { store.save(it); list = store.all().sortedBy { c -> c.name.lowercase() }; editing = null },
            onDelete = { store.delete(draft.id); list = store.all().sortedBy { c -> c.name.lowercase() }; editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun ConnectionEditor(initial: RemoteConnection, isNew: Boolean, onSave: (RemoteConnection) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var c by remember { mutableStateOf(initial) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusOk by remember { mutableStateOf(false) }
    val connectingText = stringResource(I18nR.string.au_files_connecting)
    val cloudName = stringResource(I18nR.string.au_files_cloud_default_name)
    var busy by remember { mutableStateOf(false) }
    val type = c.type

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { TelosCyberHeading {  Text(if (isNew) stringResource(I18nR.string.hf_remote_new_connection, type.label) else c.name)  } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(type.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Field(stringResource(I18nR.string.hf_remote_name), c.name) { c = c.copy(name = it) }
                if (type == RemoteType.System) {
                    val picker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) { uri ->
                        if (uri != null) {
                            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                            c = c.copy(host = uri.toString(), name = c.name.ifBlank { Uri.decode(uri.toString()).substringAfterLast(':').substringAfterLast('/').ifBlank { cloudName } })
                        }
                    }
                    Text(if (c.host.isBlank()) stringResource(I18nR.string.hf_remote_no_folder) else Uri.decode(c.host).substringAfter("tree/"), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    OutlinedButton(onClick = { picker.launch(null) }) { Text(if (c.host.isBlank()) stringResource(I18nR.string.hf_remote_choose_folder) else stringResource(I18nR.string.hf_remote_choose_other_folder)) }
                } else if (!type.cloud) {
                    Field(stringResource(I18nR.string.hc_server), c.host, if (type == RemoteType.Smb) "nas.local or 192.168.1.10 or smb://nas/share" else "cloud.example.com") { c = c.copy(host = it, fingerprint = if (it == c.host) c.fingerprint else "") }
                    if (type == RemoteType.Smb) {
                        val hosts by remember { SmbLan.discover(context) }.collectAsState(emptyList())
                        if (hosts.isNotEmpty()) {
                            Text(stringResource(I18nR.string.telos_files_smb_nearby), style = MaterialTheme.typography.labelMedium)
                            hosts.take(12).forEach { h ->
                                TextButton(onClick = { c = c.copy(host = h.host, port = h.port, name = c.name.ifBlank { h.name }) }) {
                                    Text("${h.name}  (${h.host})")
                                }
                            }
                        }
                    }
                    Field(stringResource(I18nR.string.hf_remote_port), if (c.port > 0) c.port.toString() else "", type.defaultPort.toString()) { c = c.copy(port = it.filter(Char::isDigit).toIntOrNull() ?: 0) }
                    Field(stringResource(I18nR.string.hc_user_name), c.user) { c = c.copy(user = it) }
                    Field(if (type == RemoteType.Sftp && c.privateKey.isNotBlank()) stringResource(I18nR.string.hf_remote_key_password) else stringResource(I18nR.string.hc_password), c.password, password = true) { c = c.copy(password = it) }
                    Field(
                        when (type) { RemoteType.Smb -> stringResource(I18nR.string.hf_remote_share_name); RemoteType.Sftp, RemoteType.Ftp -> stringResource(I18nR.string.hf_remote_start_folder); else -> stringResource(I18nR.string.hf_remote_server_folder) },
                        c.path,
                    ) { c = c.copy(path = it) }
                    if (type != RemoteType.Sftp && type != RemoteType.Smb) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (type == RemoteType.Ftp) stringResource(I18nR.string.hf_remote_ftps) else stringResource(I18nR.string.hf_remote_use_https), Modifier.weight(1f))
                            Switch(checked = c.tls, onCheckedChange = { c = c.copy(tls = it) })
                        }
                    }
                    if (type == RemoteType.Ftp && c.tls) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(I18nR.string.au2_dlsec_ftps_anyhost))
                                Text(stringResource(I18nR.string.au2_dlsec_ftps_anyhost_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = c.acceptAnyHost, onCheckedChange = { c = c.copy(acceptAnyHost = it) })
                        }
                    }
                    if (type == RemoteType.Sftp) {
                        Field(stringResource(I18nR.string.hf_remote_private_key), c.privateKey, minLines = 3) { c = c.copy(privateKey = it) }
                        val publicKey = remember(c.privateKey, c.user, c.host) { SshKeys.publicLine(c.privateKey, "${c.user.ifBlank { "telos" }}@telos") }
                        val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                        val keyScope = rememberCoroutineScope()
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                busy = true
                                keyScope.launch {
                                    try {
                                        val pem = withContext(Dispatchers.Default) { SshKeys.generatePrivatePem() }
                                        c = c.copy(privateKey = pem, password = "")
                                    } finally { busy = false }
                                }
                            }, enabled = !busy) { Text(stringResource(I18nR.string.telos_files_ssh_generate_key)) }
                            if (publicKey != null) TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(publicKey)) }) {
                                Text(stringResource(I18nR.string.telos_files_ssh_copy_public_key))
                            }
                        }
                        if (publicKey != null) Text(stringResource(I18nR.string.telos_files_ssh_public_key_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (c.fingerprint.isNotEmpty()) Text(stringResource(I18nR.string.hc_server_key, c.fingerprint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    CloudFields(c, onChange = { c = it }, onStatus = { text, ok -> status = text; statusOk = ok })
                }
                status?.let { Text(it, color = if (statusOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(enabled = c.name.isNotBlank() && !busy && (type != RemoteType.System || c.host.isNotBlank()), onClick = { onSave(c) }) { Text(stringResource(I18nR.string.hc_save)) } },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text(stringResource(I18nR.string.hc_delete), color = MaterialTheme.colorScheme.error) }
                TextButton(enabled = !busy, onClick = {
                    busy = true; statusOk = true; status = connectingText
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                // a saved fingerprint is only remembered with a saved connection: test with the draft as it is
                                ClientFactory.create(context, c, persist = false).use { it.list("/").size }
                            }
                        }
                        busy = false
                        statusOk = result.isSuccess
                        status = result.fold({ context.getString(I18nR.string.au_files_connected_items, it) }, { context.getString(I18nR.string.au_files_connect_failed, it.message.orEmpty()) })
                    }
                }) { Text(stringResource(I18nR.string.hc_test)) }
                TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.hc_cancel)) }
            }
        },
    )
}

@Composable
private fun Field(label: String, value: String, placeholder: String = "", password: Boolean = false, minLines: Int = 1, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = minLines == 1, minLines = minLines,
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
    )
}
