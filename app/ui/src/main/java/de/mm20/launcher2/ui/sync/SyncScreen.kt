package de.mm20.launcher2.ui.sync

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import de.mm20.launcher2.ui.component.TelosCyber
import de.mm20.launcher2.ui.component.TelosCyberAppBarTitle
import de.mm20.launcher2.ui.component.TelosCyberSectionLabel
import de.mm20.launcher2.ui.component.TelosTechPill
import de.mm20.launcher2.ui.component.telosTechSurface
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.VirtualAppWindow
import de.mm20.launcher2.ui.desktopsync.DesktopLanPairClient
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object SyncRoute : NavKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen() {
    val context = LocalContext.current
    val viewModel: SyncViewModel = viewModel()

    val serviceRunning by viewModel.serviceRunning.collectAsStateWithLifecycle()
    val wifiOnly by viewModel.wifiOnly.collectAsStateWithLifecycle()
    val chargingOnly by viewModel.chargingOnly.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddFolderDialog by remember { mutableStateOf(false) }
    var showAddDeviceDialog by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }

    VirtualAppWindow {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        TelosCyberAppBarTitle(
                            title = stringResource(I18nR.string.notes_sync_settings),
                            status = stringResource(
                                if (serviceRunning) I18nR.string.net_hub_state_on else I18nR.string.net_hub_state_off
                            ),
                            statusColor = if (serviceRunning) TelosCyber.NeonGreen else TelosCyber.NeonMagenta,
                        )
                    },
                    actions = {
                        IconButton(onClick = { showFontDialog = true }) {
                            Icon(painterResource(BaseR.drawable.text_fields_24px), contentDescription = stringResource(I18nR.string.per_app_font_size))
                        }
                        TextButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:8384"))
                                runCatching { context.startActivity(intent) }
                            }
                        ) {
                            Icon(painterResource(BaseR.drawable.open_in_new_24px), contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(I18nR.string.telos_sync_web_ui))
                        }
                    }
                )
            },
            floatingActionButton = {
                if (selectedTab == 0) {
                    FloatingActionButton(
                        onClick = { showAddFolderDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(painterResource(BaseR.drawable.add_24px), contentDescription = "Add Folder")
                    }
                } else if (selectedTab == 1) {
                    FloatingActionButton(
                        onClick = { showAddDeviceDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(painterResource(BaseR.drawable.add_24px), contentDescription = "Add Device")
                    }
                }
            }
        ) { paddingValues ->
            BoxWithConstraints(Modifier.fillMaxSize().padding(paddingValues)) {
                val shortHeight = maxHeight < 480.dp
                Column(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .telosTechSurface(
                                borderColor = if (serviceRunning) TelosCyber.NeonGreen else TelosCyber.NeonMagenta,
                            )
                            .padding(16.dp),
                    ) {
                        TelosCyberSectionLabel(stringResource(I18nR.string.sync_preview_title), accent = TelosCyber.NeonCyan)
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(I18nR.string.sync_preview_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(stringResource(I18nR.string.sync_preview_summary), style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = false, onCheckedChange = {}, enabled = false)
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TelosTechPill(stringResource(I18nR.string.telos_sync_folders, folders.size), selectedTab == 0, { selectedTab = 0 }, accentColor = TelosCyber.NeonGreen)
                        TelosTechPill(stringResource(I18nR.string.telos_sync_devices, devices.size), selectedTab == 1, { selectedTab = 1 }, accentColor = TelosCyber.NeonCyan)
                        TelosTechPill(stringResource(I18nR.string.hc_settings), selectedTab == 2, { selectedTab = 2 }, accentColor = TelosCyber.NeonMagenta)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .then(if (shortHeight && selectedTab == 2) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                    ) {
                        when (selectedTab) {
                            0 -> FoldersTab(folders, onTogglePause = { viewModel.toggleFolderPause(it) })
                            1 -> DevicesTab(devices, onTogglePause = { viewModel.toggleDevicePause(it) })
                            2 -> SettingsTab(
                                viewModel = viewModel,
                                wifiOnly = wifiOnly,
                                chargingOnly = chargingOnly,
                                onWifiChange = { viewModel.setWifiOnly(it) },
                                onChargingChange = { viewModel.setChargingOnly(it) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddFolderDialog) {
        var label by remember { mutableStateOf("") }
        var path by remember { mutableStateOf("") }
        var type by remember { mutableStateOf("Send & Receive") }

        AlertDialog(
            onDismissRequest = { showAddFolderDialog = false },
            containerColor = de.mm20.launcher2.ui.component.virtualAppPopup(),
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.telos_sync_add_folder))  } },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text(stringResource(I18nR.string.telos_sync_folder_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = path,
                        onValueChange = { path = it },
                        label = { Text(stringResource(I18nR.string.telos_sync_folder_path)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addFolder(label, path, type)
                        showAddFolderDialog = false
                    }
                ) { Text(stringResource(I18nR.string.hc_add)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddFolderDialog = false }) { Text(stringResource(I18nR.string.hc_cancel)) }
            }
        )
    }

    if (showAddDeviceDialog) {
        var deviceId by remember { mutableStateOf("") }
        var deviceName by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDeviceDialog = false },
            containerColor = de.mm20.launcher2.ui.component.virtualAppPopup(),
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.telos_sync_add_device))  } },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = deviceId,
                        onValueChange = { deviceId = it },
                        label = { Text(stringResource(I18nR.string.telos_sync_device_id)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = deviceName,
                        onValueChange = { deviceName = it },
                        label = { Text(stringResource(I18nR.string.telos_sync_device_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addDevice(deviceId, deviceName)
                        showAddDeviceDialog = false
                    }
                ) { Text(stringResource(I18nR.string.hc_add)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDeviceDialog = false }) { Text(stringResource(I18nR.string.hc_cancel)) }
            }
        )
    }

    if (showFontDialog) {
        de.mm20.launcher2.ui.component.PerAppFontScaleDialog(
            appKey = "sync",
            appName = stringResource(I18nR.string.notes_sync_settings),
            onDismiss = { showFontDialog = false }
        )
    }
}

@Composable
private fun FoldersTab(folders: List<SyncthingFolder>, onTogglePause: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(folders, key = { it.id }) { folder ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .telosTechSurface(borderColor = TelosCyber.NeonGreen)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                    Icon(
                        painter = painterResource(BaseR.drawable.folder_24px),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(folder.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(folder.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Badge { Text(folder.type) }
                            Badge(containerColor = MaterialTheme.colorScheme.tertiaryContainer) { Text(folder.sizeFormatted) }
                        }
                    }
                    IconButton(onClick = { onTogglePause(folder.id) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(
                            painter = painterResource(if (folder.isPaused) BaseR.drawable.play_arrow_24px else BaseR.drawable.pause_24px),
                            contentDescription = "Toggle Pause"
                        )
                    }
                }
        }
    }
}

@Composable
private fun DevicesTab(devices: List<SyncthingDevice>, onTogglePause: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(devices, key = { it.id }) { device ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .telosTechSurface(borderColor = TelosCyber.NeonCyan)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                    Icon(
                        painter = painterResource(BaseR.drawable.hard_disk_24px),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(device.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Badge(containerColor = if (device.isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer) {
                                Text(stringResource(if (device.isConnected) I18nR.string.au_music_connected else I18nR.string.au_music_not_connected))
                            }
                            Text("▲ ${device.downloadSpeed} · ▼ ${device.uploadSpeed}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    IconButton(onClick = { onTogglePause(device.id) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(
                            painter = painterResource(if (device.isPaused) BaseR.drawable.play_arrow_24px else BaseR.drawable.pause_24px),
                            contentDescription = "Toggle Pause"
                        )
                    }
                }
        }
    }
}

@Composable
private fun SettingsTab(
    viewModel: SyncViewModel,
    wifiOnly: Boolean,
    chargingOnly: Boolean,
    onWifiChange: (Boolean) -> Unit,
    onChargingChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sleepSyncEnabled by viewModel.sleepSyncEnabled.collectAsStateWithLifecycle()
    val sleepSyncMode by viewModel.sleepSyncMode.collectAsStateWithLifecycle()
    val dndDuringSync by viewModel.dndDuringSync.collectAsStateWithLifecycle()
    val syncOnScreenOff by viewModel.syncOnScreenOff.collectAsStateWithLifecycle()
    
    var showModeDialog by remember { mutableStateOf(false) }
    var pairUri by remember { mutableStateOf("") }
    var pairHost by remember { mutableStateOf("") }
    var pairPort by remember { mutableStateOf(DesktopLanPairClient.DEFAULT_PORT.toString()) }
    var pairCode by remember { mutableStateOf("") }
    var pairMsg by remember { mutableStateOf<String?>(null) }
    var pairBusy by remember { mutableStateOf(false) }
    var paired by remember { mutableStateOf(DesktopLanPairClient.storedToken(context) != null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TelosCyberSectionLabel(stringResource(I18nR.string.desktop_lan_pair_android_title), accent = TelosCyber.NeonGreen)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .telosTechSurface(borderColor = TelosCyber.NeonGreen)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(I18nR.string.desktop_lan_pair_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = pairUri,
                onValueChange = {
                    pairUri = it
                    DesktopLanPairClient.parseUri(it)?.let { payload ->
                        pairHost = payload.host
                        pairPort = payload.port.toString()
                        pairCode = payload.code
                    }
                },
                label = { Text(stringResource(I18nR.string.desktop_lan_pair_qr_uri)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = pairHost,
                onValueChange = { pairHost = it },
                label = { Text(stringResource(I18nR.string.desktop_lan_pair_host)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = pairPort,
                onValueChange = { pairPort = it.filter { ch -> ch.isDigit() }.take(5) },
                label = { Text(stringResource(I18nR.string.desktop_lan_pair_port)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = pairCode,
                onValueChange = { pairCode = DesktopLanPairClient.normalizeCode(it) },
                label = { Text(stringResource(I18nR.string.desktop_lan_pair_code)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !pairBusy,
                    onClick = {
                        scope.launch {
                            pairBusy = true
                            pairMsg = null
                            val payload = DesktopLanPairClient.parseUri(pairUri)
                                ?: DesktopLanPairClient.PairPayload(
                                    code = DesktopLanPairClient.normalizeCode(pairCode),
                                    host = pairHost.trim(),
                                    port = pairPort.toIntOrNull() ?: DesktopLanPairClient.DEFAULT_PORT,
                                )
                            val result = DesktopLanPairClient.pair(context, payload)
                            if (result.isSuccess) {
                                paired = true
                                pairMsg = context.getString(I18nR.string.desktop_lan_pair_paired)
                            } else {
                                pairMsg = context.getString(I18nR.string.desktop_lan_pair_failed)
                            }
                            pairBusy = false
                        }
                    },
                ) { Text(stringResource(I18nR.string.desktop_lan_pair_connect)) }
                OutlinedButton(
                    enabled = !pairBusy && paired,
                    onClick = {
                        scope.launch {
                            pairBusy = true
                            val result = DesktopLanPairClient.fetchDesktopBundle(context)
                            pairMsg = if (result.isSuccess) {
                                context.getString(I18nR.string.desktop_lan_pair_fetch_ok)
                            } else {
                                context.getString(I18nR.string.desktop_lan_pair_failed)
                            }
                            pairBusy = false
                        }
                    },
                ) { Text(stringResource(I18nR.string.desktop_lan_pair_fetch_bundle)) }
            }
            if (paired) {
                Text(
                    stringResource(I18nR.string.desktop_lan_pair_paired),
                    color = TelosCyber.NeonGreen,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            pairMsg?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .telosTechSurface(borderColor = TelosCyber.NeonMagenta)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(I18nR.string.telos_sync_wifi_only), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(I18nR.string.telos_sync_wifi_only_sum), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = wifiOnly, onCheckedChange = onWifiChange)
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(I18nR.string.telos_sync_charging_only), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(I18nR.string.telos_sync_charging_only_sum), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = chargingOnly, onCheckedChange = onChargingChange)
                }
        }
        
        TelosCyberSectionLabel(stringResource(I18nR.string.telos_sync_sleep_title), accent = TelosCyber.NeonCyan, modifier = Modifier.padding(top = 8.dp, start = 8.dp))
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .telosTechSurface(borderColor = TelosCyber.NeonCyan)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(I18nR.string.telos_sync_sleep_enable), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(I18nR.string.telos_sync_sleep_enable_sum), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = sleepSyncEnabled, onCheckedChange = { viewModel.setSleepSyncEnabled(it) })
                }
                
                if (sleepSyncEnabled) {
                    HorizontalDivider()
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showModeDialog = true },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(stringResource(I18nR.string.telos_sync_network_mode), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                when (sleepSyncMode) {
                                    "Wi-Fi and Cellular" -> stringResource(I18nR.string.telos_sync_mode_wifi_cell)
                                    "Any Unmetered" -> stringResource(I18nR.string.telos_sync_mode_unmetered)
                                    else -> stringResource(I18nR.string.telos_sync_mode_wifi)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    
                    HorizontalDivider()
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(I18nR.string.telos_sync_dnd), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(stringResource(I18nR.string.telos_sync_dnd_sum), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = dndDuringSync, onCheckedChange = { viewModel.setDndDuringSync(it) })
                    }
                    
                    HorizontalDivider()
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(I18nR.string.telos_sync_screen_off), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(stringResource(I18nR.string.telos_sync_screen_off_sum), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = syncOnScreenOff, onCheckedChange = { viewModel.setSyncOnScreenOff(it) })
                    }
                }
        }
    }
    
    if (showModeDialog) {
        AlertDialog(
            onDismissRequest = { showModeDialog = false },
            containerColor = de.mm20.launcher2.ui.component.virtualAppPopup(),
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.telos_sync_network_mode))  } },
            text = {
                Column {
                    val modes = listOf(
                        "Only on Wi-Fi" to stringResource(I18nR.string.telos_sync_mode_wifi),
                        "Wi-Fi and Cellular" to stringResource(I18nR.string.telos_sync_mode_wifi_cell),
                        "Any Unmetered" to stringResource(I18nR.string.telos_sync_mode_unmetered),
                    )
                    modes.forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    viewModel.setSleepSyncMode(mode)
                                    showModeDialog = false 
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = sleepSyncMode == mode,
                                onClick = { 
                                    viewModel.setSleepSyncMode(mode)
                                    showModeDialog = false 
                                }
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModeDialog = false }) { Text(stringResource(I18nR.string.hc_close)) }
            }
        )
    }
}
