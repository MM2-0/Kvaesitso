package de.mm20.launcher2.ui.downloads

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.downloads.DownloadManager
import de.mm20.launcher2.downloads.DownloadState
import de.mm20.launcher2.downloads.DownloadTask
import de.mm20.launcher2.downloads.TorrentFile
import de.mm20.launcher2.downloads.engine.PeerRow
import de.mm20.launcher2.downloads.engine.TorrentController
import de.mm20.launcher2.downloads.engine.TrackerRow
import de.mm20.launcher2.downloads.engine.TrackerStatus
import de.mm20.launcher2.downloads.logic.Formatting
import de.mm20.launcher2.downloads.logic.SeedRules
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun TorrentDetailSheet(
    task: DownloadTask,
    manager: DownloadManager,
    controller: TorrentController,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var addTracker by remember { mutableStateOf(false) }
    val d = task.torrent
    val running = task.state.isActive

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        // the tabs have a fixed height: on a small screen or in landscape the sheet has to scroll
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(task.displayName, style = MaterialTheme.typography.titleLarge, maxLines = 2)
            Text(
                torrentStateLabel(task) + torrentProgressSuffix(task),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
            )
            if (d != null) {
                Text(
                    "↓ ${Formatting.speed(if (task.state == DownloadState.Seeding) 0 else task.speedBps)}  ↑ ${Formatting.speed(d.uploadBps)} · " +
                        stringResource(I18nR.string.dl_t_ratio, SeedRules.formatRatio(d.ratio)) + " · " +
                        stringResource(I18nR.string.dl_t_peers, d.seeds, (d.peers - d.seeds).coerceAtLeast(0)),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PieceMapBar(task, controller)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when (task.state) {
                    DownloadState.Paused -> Button(onClick = { manager.resume(task.id) }) { Text(stringResource(I18nR.string.dl_resume)) }
                    DownloadState.Failed -> Button(onClick = { manager.resume(task.id) }) { Text(stringResource(I18nR.string.dl_retry)) }
                    DownloadState.Completed -> {}
                    else -> Button(onClick = { manager.pause(task.id) }) { Text(stringResource(I18nR.string.dl_pause)) }
                }
                OutlinedButton(enabled = running, onClick = { scope.launch { controller.forceRecheck(task.id) } }) { Text(stringResource(I18nR.string.dl_t_recheck)) }
                OutlinedButton(enabled = running, onClick = { scope.launch { controller.forceReannounce(task.id) } }) { Text(stringResource(I18nR.string.dl_t_reannounce)) }
                OutlinedButton(onClick = onDelete) { Text(stringResource(I18nR.string.dl_delete)) }
            }
            if (d != null) {
                SwitchRow(stringResource(I18nR.string.dl_t_sequential), d.sequential) { on -> scope.launch { controller.setSequential(task.id, on) } }
            }

            PrimaryTabRow(selectedTabIndex = tab) {
                for ((i, label) in listOf(I18nR.string.dl_t_tab_files, I18nR.string.dl_t_tab_peers, I18nR.string.dl_t_tab_trackers, I18nR.string.dl_t_tab_info).withIndex()) {
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(label)) })
                }
            }
            when (tab) {
                0 -> FilesTab(task, controller, openFile = { f -> openTorrentFile(context, task, f) })
                1 -> PeersTab(task, controller)
                2 -> TrackersTab(task, controller, onAdd = { addTracker = true })
                else -> InfoTab(task, controller, manager)
            }
            Spacer(Modifier.size(12.dp))
        }
    }
    if (addTracker) AddTrackerDialog(onDismiss = { addTracker = false }) { url ->
        scope.launch {
            if (controller.addTracker(task.id, url)) addTracker = false
            else Toast.makeText(context, I18nR.string.dl_t_tracker_failed, Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
private fun FilesTab(task: DownloadTask, controller: TorrentController, openFile: (TorrentFile) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val files = task.torrent?.files.orEmpty()
    if (files.isEmpty()) {
        Text(stringResource(I18nR.string.dl_t_files_wait), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
        return
    }
    TorrentFileList(
        files = files,
        onChange = { changes ->
            scope.launch { if (!controller.setFilePriorities(task.id, changes)) Toast.makeText(context, I18nR.string.dl_t_keep_one_file, Toast.LENGTH_SHORT).show() }
        },
        showProgress = true,
        onOpen = openFile,
        modifier = Modifier.fillMaxWidth().height(340.dp),
    )
}

@Composable
private fun PeersTab(task: DownloadTask, controller: TorrentController) {
    val peers = rememberPolled(task.id, task.state, emptyList<PeerRow>()) { controller.peers(task.id) }
    if (peers.isEmpty()) {
        Text(
            stringResource(if (task.state.isActive) I18nR.string.dl_t_peers_empty else I18nR.string.dl_t_not_running),
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp),
        )
        return
    }
    Column {
        Text(stringResource(I18nR.string.dl_t_flags_legend), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.fillMaxWidth().height(320.dp)) {
            items(peers) { p -> PeerItem(p) }
        }
    }
}

@Composable
private fun PeerItem(p: PeerRow) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(p.ip + "  " + p.client, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        Text(
            "↓ ${Formatting.speed(p.downBps)}  ↑ ${Formatting.speed(p.upBps)} · ${(p.progress * 100).toInt()} % · ${p.flags}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun TrackersTab(task: DownloadTask, controller: TorrentController, onAdd: () -> Unit) {
    val trackers = rememberPolled(task.id, task.state, emptyList<TrackerRow>()) { controller.trackers(task.id) }
    Column {
        if (trackers.isEmpty()) {
            Text(
                stringResource(if (task.state.isActive) I18nR.string.dl_t_trackers_empty else I18nR.string.dl_t_not_running),
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp),
            )
        } else {
            LazyColumn(Modifier.fillMaxWidth().height(280.dp)) {
                items(trackers) { t -> TrackerItem(t) }
            }
        }
        TextButton(onClick = onAdd, enabled = task.state.isActive) { Text(stringResource(I18nR.string.dl_t_add_tracker)) }
    }
}

@Composable
private fun TrackerItem(t: TrackerRow) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(t.url, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
        val status = when (t.status) {
            TrackerStatus.Working -> stringResource(I18nR.string.dl_t_tracker_working)
            TrackerStatus.Updating -> stringResource(I18nR.string.dl_t_tracker_updating)
            TrackerStatus.NotContacted -> stringResource(I18nR.string.dl_t_tracker_idle)
            TrackerStatus.Error -> stringResource(I18nR.string.dl_t_tracker_error, t.fails)
        }
        Text(
            "Tier ${t.tier} · $status" + if (t.message.isNotBlank()) " · ${t.message}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = if (t.status == TrackerStatus.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddTrackerDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.dl_t_add_tracker))  } },
        text = {
            OutlinedTextField(
                url, { url = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(I18nR.string.dl_t_tracker_url)) },
                supportingText = { Text(stringResource(I18nR.string.dl_t_tracker_hint)) },
            )
        },
        confirmButton = { TextButton(enabled = TorrentController.isTrackerUrl(url.trim()), onClick = { onAdd(url.trim()) }) { Text(stringResource(I18nR.string.dl_t_add_tracker)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.dl_cancel)) } },
    )
}

@Composable
private fun InfoTab(task: DownloadTask, controller: TorrentController, manager: de.mm20.launcher2.downloads.DownloadManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val d = task.torrent ?: return
    var editSeed by remember { mutableStateOf(false) }
    var limit by remember(task.id) { mutableStateOf((task.speedLimitBps / 1024).takeIf { it > 0 }?.toString().orEmpty()) }
    Column(Modifier.fillMaxWidth().height(340.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoLine(I18nR.string.dl_detail_size, if (task.totalBytes >= 0) Formatting.size(task.totalBytes) else "?")
        if (d.infoHash.isNotEmpty()) InfoLine(I18nR.string.dl_t_info_hash, d.infoHash, mono = true)
        if (d.numPieces > 0) InfoLine(I18nR.string.dl_t_info_pieces, "${d.numPieces} × ${Formatting.size(d.pieceLength.toLong())}")
        if (d.isPrivate) InfoLine(I18nR.string.dl_t_info_private, stringResource(I18nR.string.dl_yes))
        InfoLine(I18nR.string.dl_t_info_uploaded, Formatting.size(d.uploadedBytes))
        InfoLine(I18nR.string.dl_t_info_received, Formatting.size(d.receivedBytes))
        InfoLine(I18nR.string.dl_t_info_ratio, SeedRules.formatRatio(d.ratio))
        if (d.seedingSeconds > 0) InfoLine(I18nR.string.dl_t_info_seeding_time, Formatting.duration(d.seedingSeconds))
        InfoLine(
            I18nR.string.dl_t_info_seed_limits,
            if (d.stopAtDone) stringResource(I18nR.string.dl_t_stop_at_done)
            else stringResource(I18nR.string.dl_t_ratio_limit) + ": " + ratioLabel(d.seedRatioX100) + " · " + stringResource(I18nR.string.dl_t_time_limit) + ": " + minutesLabel(d.seedMinutes),
        )
        TextButton(onClick = { editSeed = true }) { Text(stringResource(I18nR.string.dl_t_seed_edit)) }
        OutlinedTextField(
            value = limit, onValueChange = { v -> limit = v.filter(Char::isDigit).take(7); manager.setSpeedLimit(task.id, (limit.toLongOrNull() ?: 0L) * 1024) },
            singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(I18nR.string.dl_p3_torrent_limit)) },
        )
        d.stagingPath?.let { InfoLine(I18nR.string.dl_t_info_staging, it, mono = true) }
        task.fileUri?.let { InfoLine(I18nR.string.dl_detail_saved_to, Uri.decode(it)) }
        if (d.magnet.isNotEmpty()) {
            InfoLine(I18nR.string.dl_t_info_magnet, d.magnet, mono = true)
            TextButton(onClick = { copyLink(context, d.magnet) }) { Text(stringResource(I18nR.string.dl_t_copy_magnet)) }
        }
        if (task.treeUri != null || task.fileUri != null) {
            TextButton(onClick = { if (!openFolder(context, task)) Toast.makeText(context, I18nR.string.dl_no_app, Toast.LENGTH_SHORT).show() }) {
                Text(stringResource(I18nR.string.dl_t_open_folder))
            }
        }
        if (!task.error.isNullOrBlank()) InfoLine(I18nR.string.dl_detail_error, task.error!!)
    }
    if (editSeed) SeedLimitsDialog(d.seedRatioX100, d.seedMinutes, d.stopAtDone, onDismiss = { editSeed = false }) { r, m, s ->
        controller.setSeedLimits(task.id, r, m, s)
        editSeed = false
    }
}

@Composable
private fun SeedLimitsDialog(ratio: Int, minutes: Int, stop: Boolean, onDismiss: () -> Unit, onSave: (Int, Int, Boolean) -> Unit) {
    var r by remember { mutableStateOf(ratio) }
    var m by remember { mutableStateOf(minutes) }
    var s by remember { mutableStateOf(stop) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.dl_t_seed_edit))  } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SwitchRow(stringResource(I18nR.string.dl_t_stop_at_done), s) { s = it }
                if (!s) {
                    Text(stringResource(I18nR.string.dl_t_ratio_limit), style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (x in RATIO_CHOICES) FilterChip(selected = r == x, onClick = { r = x }, label = { Text(ratioLabel(x)) })
                    }
                    Text(stringResource(I18nR.string.dl_t_time_limit), style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (x in MINUTE_CHOICES) FilterChip(selected = m == x, onClick = { m = x }, label = { Text(minutesLabel(x)) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(r, m, s) }) { Text(stringResource(I18nR.string.dl_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.dl_cancel)) } },
    )
}

@Composable
private fun InfoLine(label: Int, value: String, mono: Boolean = false) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = if (mono) FontFamily.Monospace else null)
    }
}

/** Piece map: one cell per group of pieces, filled by how many of them are there */
@Composable
private fun PieceMapBar(task: DownloadTask, controller: TorrentController) {
    val cells = rememberPolled(task.id, task.state, FloatArray(0), periodMs = 2500) { controller.pieceMap(task.id, 120) }
    val have = MaterialTheme.colorScheme.primary
    val missing = MaterialTheme.colorScheme.surfaceVariant
    if (cells.isEmpty()) return
    Column {
        Text(stringResource(I18nR.string.dl_t_piece_map), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val w = size.width / cells.size
            for ((i, f) in cells.withIndex()) {
                drawRect(missing, Offset(i * w, 0f), Size(w + 0.5f, size.height))
                if (f > 0f) drawRect(have.copy(alpha = 0.25f + 0.75f * f), Offset(i * w, 0f), Size(w + 0.5f, size.height))
            }
        }
    }
}

/** Calls [load] now and every [periodMs] while the task is active (once when it is not) */
@Composable
private fun <T> rememberPolled(id: String, state: DownloadState, initial: T, periodMs: Long = 2000, load: suspend () -> T): T {
    var value by remember(id) { mutableStateOf(initial) }
    LaunchedEffect(id, state.isActive) {
        while (true) {
            value = load()
            if (!state.isActive) break
            delay(periodMs)
        }
    }
    return value
}

private fun openTorrentFile(context: android.content.Context, task: DownloadTask, f: TorrentFile): Boolean {
    val uri = f.uri?.let(Uri::parse) ?: return false
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, de.mm20.launcher2.downloads.logic.MimeTypes.forName(f.path) ?: "*/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return try { context.startActivity(intent); true } catch (e: Exception) { false }
}

private fun openFolder(context: android.content.Context, task: DownloadTask): Boolean {
    val tree = task.treeUri?.let(Uri::parse)
    val uri = if (tree != null) DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    else Uri.parse("content://com.android.providers.downloads.documents/root/downloads")
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return try { context.startActivity(intent); true } catch (e: Exception) { false }
}
