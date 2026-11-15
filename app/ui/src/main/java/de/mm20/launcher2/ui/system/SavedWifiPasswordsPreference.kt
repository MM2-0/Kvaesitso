package de.mm20.launcher2.ui.system

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import de.mm20.launcher2.comms.AuthManager
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.comms.ContactQrImage
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.layout.UiSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class WifiFilter { All, Device, App }
private enum class WifiSort { Az, Za }
private enum class WifiExportKind { Json, Gzip, Encrypted }

@Composable
fun SavedWifiPasswordsPreference() {
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity
    val scope = rememberCoroutineScope()
    var privilege by remember { mutableStateOf<WifiPrivilegeState?>(null) }
    var open by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf<List<WifiEntry>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(WifiFilter.All) }
    var sort by remember { mutableStateOf(WifiSort.Az) }
    var selectedQrPayload by remember { mutableStateOf<String?>(null) }
    var noteTarget by remember { mutableStateOf<WifiEntry?>(null) }
    var forgetTarget by remember { mutableStateOf<WifiEntry?>(null) }
    var forgetAll by remember { mutableStateOf(false) }
    var exportKind by remember { mutableStateOf<WifiExportKind?>(null) }
    var exportPassword by remember { mutableStateOf("") }
    var importPassword by remember { mutableStateOf("") }
    var pendingImport by remember { mutableStateOf<ByteArray?>(null) }
    var autoPersist by remember { mutableStateOf(PrivilegedWifiStore.autoPersist(context)) }
    var connectedSsid by remember { mutableStateOf<String?>(null) }
    var revealJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun refreshPrivilege() {
        scope.launch { privilege = PrivilegedWifiStore.detect(context) }
    }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val kind = exportKind ?: return@rememberLauncherForActivityResult
        exportKind = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val allowed = activity != null && runCatching {
                AuthManager().authenticateNative(activity, context.getString(I18nR.string.telos_system_unlock_wifi))
            }.getOrDefault(false)
            val written = allowed && withContext(Dispatchers.IO) {
                runCatching {
                    val rows = (entries ?: PrivilegedWifiStore.load(context)).map { it.copy(note = PrivilegedWifiStore.noteFor(context, it.ssid).ifBlank { it.note }) }
                    val json = PrivilegedWifiStore.exportJson(rows).toByteArray(Charsets.UTF_8)
                    val bytes = when (kind) {
                        WifiExportKind.Json -> json
                        WifiExportKind.Gzip -> PrivilegedWifiStore.gzip(json)
                        WifiExportKind.Encrypted -> PrivilegedWifiStore.encrypt(json, exportPassword)
                    }
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("No output stream")
                }.isSuccess
            }
            exportPassword = ""
            if (!written) {
                withContext(Dispatchers.IO) { runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri) } }
                Toast.makeText(context, I18nR.string.au9_share_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } } ?: return@launch
            val looksEncrypted = bytes.isNotEmpty() && bytes[0] == 1.toByte() && bytes.size > 30
            if (looksEncrypted) {
                pendingImport = bytes
            } else {
                val text = runCatching { PrivilegedWifiStore.gunzip(bytes).toString(Charsets.UTF_8) }
                    .getOrElse { bytes.toString(Charsets.UTF_8) }
                mergeImported(context, text)
                entries = withContext(Dispatchers.IO) { PrivilegedWifiStore.load(context).withNotes(context) }
            }
        }
    }

    de.mm20.launcher2.ui.component.PrivilegedConnectionEffect { refreshPrivilege() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshPrivilege() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        revealJob?.cancel()
        entries = null
        selectedQrPayload = null
        query = ""
        open = false
        noteTarget = null
        forgetTarget = null
        exportPassword = ""
        importPassword = ""
        pendingImport = null
    }

    Preference(
        title = stringResource(I18nR.string.telos_system_saved_wifi),
        summary = stringResource(
            when {
                privilege?.privilege == WifiPrivilege.Root -> I18nR.string.telos_system_wifi_privilege_root
                privilege?.privilege == WifiPrivilege.Shizuku -> I18nR.string.telos_system_wifi_privilege_shizuku
                else -> I18nR.string.telos_system_wifi_privilege_missing
            },
        ),
        enabled = !loading,
        onClick = {
            open = true
            connectedSsid = currentSsid(context)
            if (privilege?.privilege != WifiPrivilege.None) {
                revealJob = scope.launch {
                    if (loading) return@launch
                    loading = true
                    try {
                        val authenticated = activity != null && runCatching {
                            AuthManager().authenticateNative(activity, context.getString(I18nR.string.telos_system_unlock_wifi))
                        }.getOrDefault(false)
                        if (authenticated) {
                            entries = withContext(Dispatchers.IO) { PrivilegedWifiStore.load(context).withNotes(context) }
                        }
                    } catch (_: Exception) {
                        Toast.makeText(context, I18nR.string.virtual_telemetry_unavailable, Toast.LENGTH_SHORT).show()
                    } finally {
                        loading = false
                    }
                }
            }
        },
    )

    if (open) {
        WifiManagerDialog(
            privilege = privilege,
            entries = entries,
            loading = loading,
            query = query,
            onQuery = { query = it },
            filter = filter,
            onFilter = { filter = it },
            sort = sort,
            onSort = { sort = it },
            autoPersist = autoPersist,
            connectedSsid = connectedSsid,
            onDismiss = { open = false; entries = null },
            onEnableShizuku = {
                scope.launch {
                    when {
                        privilege?.shizukuAuthorized == true -> refreshPrivilege()
                        privilege?.shizukuRunning == true -> {
                            PrivilegedWifiStore.requestShizuku()
                            refreshPrivilege()
                        }
                        else -> PrivilegedWifiStore.openShizuku(context)
                    }
                }
            },
            onRefresh = {
                scope.launch {
                    refreshPrivilege()
                    if (privilege?.privilege != WifiPrivilege.None) {
                        loading = true
                        entries = withContext(Dispatchers.IO) { PrivilegedWifiStore.load(context).withNotes(context) }
                        loading = false
                    }
                }
            },
            onShowQr = { selectedQrPayload = wifiQrPayload(it.ssid, it.psk.orEmpty()) },
            onNote = { noteTarget = it },
            onForget = { forgetTarget = it },
            onForgetAll = { forgetAll = true },
            onConnect = { entry ->
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { PrivilegedWifiStore.connect(entry.networkId) }
                    if (ok) connectedSsid = entry.ssid
                }
            },
            onDisconnect = {
                scope.launch {
                    if (withContext(Dispatchers.IO) { PrivilegedWifiStore.disconnect() }) connectedSsid = null
                }
            },
            onPersist = {
                scope.launch {
                    val n = withContext(Dispatchers.IO) { PrivilegedWifiStore.persistEphemeral() }
                    Toast.makeText(context, context.getString(I18nR.string.telos_system_wifi_persisted, n), Toast.LENGTH_SHORT).show()
                }
            },
            onAutoPersist = { enabled ->
                autoPersist = enabled
                PrivilegedWifiStore.setAutoPersist(context, enabled)
                if (enabled) scope.launch { withContext(Dispatchers.IO) { PrivilegedWifiStore.persistEphemeral() } }
            },
            onExport = { kind ->
                exportKind = kind
                val name = when (kind) {
                    WifiExportKind.Json -> "telos_wifi_backup.json"
                    WifiExportKind.Gzip -> "telos_wifi_backup.json.gz"
                    WifiExportKind.Encrypted -> "telos_wifi_backup.bin"
                }
                if (kind == WifiExportKind.Encrypted && exportPassword.isBlank()) {
                    exportKind = WifiExportKind.Encrypted
                } else {
                    export.launch(name)
                }
            },
            exportPassword = exportPassword,
            onExportPassword = { exportPassword = it },
            onImport = { importer.launch(arrayOf("*/*")) },
        )
    }

    selectedQrPayload?.let { payload ->
        AlertDialog(
            onDismissRequest = { selectedQrPayload = null },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_qr_code))  } },
            text = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    ContactQrImage(payload, Modifier.size(220.dp).padding(12.dp))
                }
            },
            confirmButton = { TextButton(onClick = { selectedQrPayload = null }) { Text(stringResource(I18nR.string.hc_close)) } },
        )
    }

    noteTarget?.let { target ->
        var draft by remember(target.ssid) { mutableStateOf(PrivilegedWifiStore.noteFor(context, target.ssid)) }
        AlertDialog(
            onDismissRequest = { noteTarget = null },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.telos_system_wifi_note))  } },
            text = {
                OutlinedTextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            },
            confirmButton = {
                TextButton(onClick = {
                    PrivilegedWifiStore.saveNote(context, target.ssid, draft)
                    entries = entries?.map { if (it.ssid == target.ssid) it.copy(note = draft.ifBlank { null }) else it }
                    noteTarget = null
                }) { Text(stringResource(I18nR.string.telos_system_wifi_note_save)) }
            },
            dismissButton = { TextButton(onClick = { noteTarget = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }

    forgetTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { forgetTarget = null },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.telos_system_wifi_forget))  } },
            text = { Text(stringResource(I18nR.string.telos_system_wifi_forget_confirm, target.ssid)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { PrivilegedWifiStore.forget(target.networkId) }
                        if (ok) {
                            PrivilegedWifiStore.rememberAppOnly(context, target)
                            entries = entries?.map { if (it.ssid == target.ssid) it.copy(existInSystem = false, networkId = -1) else it }
                        }
                        forgetTarget = null
                    }
                }) { Text(stringResource(I18nR.string.telos_system_wifi_forget)) }
            },
            dismissButton = { TextButton(onClick = { forgetTarget = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }

    if (forgetAll) {
        AlertDialog(
            onDismissRequest = { forgetAll = false },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.telos_system_wifi_forget_all))  } },
            text = { Text(stringResource(I18nR.string.telos_system_wifi_forget_all_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val list = entries.orEmpty()
                        withContext(Dispatchers.IO) {
                            list.forEach { PrivilegedWifiStore.rememberAppOnly(context, it) }
                            PrivilegedWifiStore.forgetAll(list)
                        }
                        entries = list.map { it.copy(existInSystem = false, networkId = -1) }
                        forgetAll = false
                    }
                }) { Text(stringResource(I18nR.string.telos_system_wifi_forget_all)) }
            },
            dismissButton = { TextButton(onClick = { forgetAll = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }

    pendingImport?.let { bytes ->
        AlertDialog(
            onDismissRequest = { pendingImport = null; importPassword = "" },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.telos_system_wifi_import))  } },
            text = {
                OutlinedTextField(
                    value = importPassword,
                    onValueChange = { importPassword = it },
                    label = { Text(stringResource(I18nR.string.telos_system_wifi_export_password)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching {
                            val json = PrivilegedWifiStore.decrypt(bytes, importPassword).toString(Charsets.UTF_8)
                            mergeImported(context, json)
                            entries = withContext(Dispatchers.IO) { PrivilegedWifiStore.load(context).withNotes(context) }
                        }
                        pendingImport = null
                        importPassword = ""
                    }
                }) { Text(stringResource(I18nR.string.telos_system_wifi_import)) }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null; importPassword = "" }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

@Composable
private fun WifiManagerDialog(
    privilege: WifiPrivilegeState?,
    entries: List<WifiEntry>?,
    loading: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    filter: WifiFilter,
    onFilter: (WifiFilter) -> Unit,
    sort: WifiSort,
    onSort: (WifiSort) -> Unit,
    autoPersist: Boolean,
    connectedSsid: String?,
    onDismiss: () -> Unit,
    onEnableShizuku: () -> Unit,
    onRefresh: () -> Unit,
    onShowQr: (WifiEntry) -> Unit,
    onNote: (WifiEntry) -> Unit,
    onForget: (WifiEntry) -> Unit,
    onForgetAll: () -> Unit,
    onConnect: (WifiEntry) -> Unit,
    onDisconnect: () -> Unit,
    onPersist: () -> Unit,
    onAutoPersist: (Boolean) -> Unit,
    onExport: (WifiExportKind) -> Unit,
    exportPassword: String,
    onExportPassword: (String) -> Unit,
    onImport: () -> Unit,
) {
    val networks = entries.orEmpty()
    val filtered = remember(networks, query, filter, sort, connectedSsid) {
        networks.filter { entry ->
            val q = query.trim()
            val matches = q.isBlank() || entry.ssid.contains(q, true) || entry.note.orEmpty().contains(q, true)
            val scope = when (filter) {
                WifiFilter.All -> true
                WifiFilter.Device -> entry.existInSystem
                WifiFilter.App -> !entry.existInSystem
            }
            matches && scope
        }.sortedWith(
            compareByDescending<WifiEntry> { it.ssid == connectedSsid }
                .thenBy { if (sort == WifiSort.Az) it.ssid.lowercase() else "" }
                .thenByDescending { if (sort == WifiSort.Za) it.ssid.lowercase() else "" },
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(I18nR.string.telos_system_saved_wifi), style = MaterialTheme.typography.titleLarge)
                    Text(networks.size.toString(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                PrivilegeBanner(privilege, onEnableShizuku)
                OutlinedTextField(
                    value = query,
                    onValueChange = onQuery,
                    label = { Text(stringResource(I18nR.string.hc_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilterChip(selected = filter == WifiFilter.All, onClick = { onFilter(WifiFilter.All) }, label = { Text(stringResource(I18nR.string.telos_system_wifi_filter_all)) })
                    FilterChip(selected = filter == WifiFilter.Device, onClick = { onFilter(WifiFilter.Device) }, label = { Text(stringResource(I18nR.string.telos_system_wifi_filter_device)) })
                    FilterChip(selected = filter == WifiFilter.App, onClick = { onFilter(WifiFilter.App) }, label = { Text(stringResource(I18nR.string.telos_system_wifi_filter_app)) })
                    Spacer(Modifier.weight(1f))
                    FilterChip(selected = sort == WifiSort.Az, onClick = { onSort(if (sort == WifiSort.Az) WifiSort.Za else WifiSort.Az) }, label = { Text(stringResource(if (sort == WifiSort.Az) I18nR.string.telos_system_wifi_sort_az else I18nR.string.telos_system_wifi_sort_za)) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { onExport(WifiExportKind.Json) }, modifier = Modifier.weight(1f)) { Text(stringResource(I18nR.string.telos_system_wifi_export)) }
                    OutlinedButton(onClick = { onExport(WifiExportKind.Gzip) }, modifier = Modifier.weight(1f)) { Text(stringResource(I18nR.string.telos_system_wifi_export_gzip)) }
                    OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text(stringResource(I18nR.string.telos_system_wifi_import)) }
                }
                OutlinedTextField(
                    value = exportPassword,
                    onValueChange = onExportPassword,
                    label = { Text(stringResource(I18nR.string.telos_system_wifi_export_password)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { onExport(WifiExportKind.Encrypted) }, enabled = exportPassword.isNotBlank()) {
                    Text(stringResource(I18nR.string.telos_system_wifi_export_encrypt))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = autoPersist, onCheckedChange = onAutoPersist)
                    Text(stringResource(I18nR.string.telos_system_wifi_auto_persist), modifier = Modifier.padding(start = 8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onPersist) { Text(stringResource(I18nR.string.telos_system_wifi_persist_ephemeral)) }
                    TextButton(onClick = onForgetAll, enabled = networks.any { it.existInSystem }) { Text(stringResource(I18nR.string.telos_system_wifi_forget_all)) }
                    TextButton(onClick = onRefresh) { Text(stringResource(I18nR.string.hc_refresh)) }
                }
                if (loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (filtered.isEmpty() && !loading) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(stringResource(if (privilege?.privilege == WifiPrivilege.None) I18nR.string.telos_system_wifi_privilege_missing else I18nR.string.telos_system_wifi_empty))
                    }
                } else {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(filtered, key = { index, item -> "${item.ssid}_$index" }) { _, entry ->
                            WifiNetworkCard(
                                entry = entry,
                                connected = entry.ssid == connectedSsid,
                                onShowQr = { onShowQr(entry) },
                                onNote = { onNote(entry) },
                                onForget = { onForget(entry) },
                                onConnect = { onConnect(entry) },
                                onDisconnect = onDisconnect,
                            )
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        }
    }
}

@Composable
private fun PrivilegeBanner(privilege: WifiPrivilegeState?, onEnableShizuku: () -> Unit) {
    val missing = privilege == null || privilege.privilege == WifiPrivilege.None
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (missing) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(
                    when {
                        privilege?.privilege == WifiPrivilege.Root -> I18nR.string.telos_system_wifi_privilege_root
                        privilege?.privilege == WifiPrivilege.Shizuku -> I18nR.string.telos_system_wifi_privilege_shizuku
                        privilege?.shizukuInstalled == true && privilege.shizukuRunning && !privilege.shizukuAuthorized -> I18nR.string.telos_system_wifi_grant_shizuku
                        privilege?.shizukuInstalled == true && !privilege.shizukuRunning -> I18nR.string.telos_system_wifi_open_shizuku
                        else -> I18nR.string.telos_system_wifi_enable_shizuku
                    },
                ),
            )
            if (missing) {
                Button(onClick = onEnableShizuku) {
                    Text(
                        stringResource(
                            when {
                                privilege?.shizukuRunning == true -> I18nR.string.telos_system_wifi_grant_shizuku
                                privilege?.shizukuInstalled == true -> I18nR.string.telos_system_wifi_open_shizuku
                                else -> I18nR.string.telos_system_wifi_enable_shizuku
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun WifiNetworkCard(
    entry: WifiEntry,
    connected: Boolean,
    onShowQr: () -> Unit,
    onNote: () -> Unit,
    onForget: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val context = LocalContext.current
    var passwordVisible by remember(entry.ssid) { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (connected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(entry.ssid, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (connected) Text(stringResource(I18nR.string.telos_system_wifi_connected), color = MaterialTheme.colorScheme.primary)
                    Text(
                        entry.security.ifBlank { stringResource(I18nR.string.telos_system_wifi_security_open) },
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = if (passwordVisible) entry.psk ?: stringResource(I18nR.string.virtual_telemetry_unavailable) else "••••••••",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (entry.psk.isNullOrBlank()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    )
                    entry.note?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!entry.existInSystem) {
                        Text(stringResource(I18nR.string.telos_system_wifi_filter_app), style = MaterialTheme.typography.labelSmall)
                    }
                }
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        painter = painterResource(if (passwordVisible) BaseR.drawable.visibility_24px else BaseR.drawable.visibility_off_24px),
                        contentDescription = stringResource(I18nR.string.hc_password),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(enabled = !entry.psk.isNullOrBlank(), onClick = {
                    val psk = entry.psk.orEmpty()
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText(context.getString(I18nR.string.hc_password), psk)
                    if (android.os.Build.VERSION.SDK_INT >= 33) clip.description.extras = android.os.PersistableBundle().apply {
                        putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
                    }
                    cm.setPrimaryClip(clip)
                }) { Text(stringResource(I18nR.string.hc_copy)) }
                TextButton(enabled = !entry.psk.isNullOrBlank(), onClick = {
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "${entry.ssid}\n${entry.psk}")
                            },
                            null,
                        ),
                    )
                }) { Text(stringResource(I18nR.string.menu_share)) }
                TextButton(enabled = !entry.psk.isNullOrBlank(), onClick = onShowQr) { Text(stringResource(I18nR.string.hc_qr_code)) }
                TextButton(onClick = onNote) { Text(stringResource(I18nR.string.telos_system_wifi_note)) }
                if (entry.existInSystem) {
                    TextButton(onClick = if (connected) onDisconnect else onConnect) {
                        Text(stringResource(if (connected) I18nR.string.telos_system_wifi_disconnect else I18nR.string.telos_system_wifi_connect))
                    }
                    TextButton(onClick = onForget) { Text(stringResource(I18nR.string.telos_system_wifi_forget)) }
                }
            }
        }
    }
}

private fun List<WifiEntry>.withNotes(context: Context): List<WifiEntry> =
    map { it.copy(note = PrivilegedWifiStore.noteFor(context, it.ssid).ifBlank { it.note }) }

private fun mergeImported(context: Context, json: String) {
    PrivilegedWifiStore.importJson(json).forEach { row ->
        PrivilegedWifiStore.rememberAppOnly(context, row)
        row.note?.let { PrivilegedWifiStore.saveNote(context, row.ssid, it) }
    }
}

internal fun currentSsid(context: Context): String? {
    val wm = context.getSystemService(WifiManager::class.java) ?: return null
    @Suppress("DEPRECATION")
    val ssid = wm.connectionInfo?.ssid?.trim('"')
    return ssid?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
}
