package de.mm20.launcher2.ui.component
import de.mm20.launcher2.i18n.R as I18nR

import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.comms.blocklist.BlockList
import de.mm20.launcher2.comms.blocklist.BlockListKind
import de.mm20.launcher2.comms.blocklist.BlockListPresets
import de.mm20.launcher2.comms.blocklist.BlockLists
import de.mm20.launcher2.comms.blocklist.UpdateSchedule
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.launch

/**
 * The block list controls shared by the web apps settings (domain lists) and the Telos Video
 * services dialog (peer lists). Plain layout, no preference rows, so it works inside dialogs.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun BlockListsSection(kind: BlockListKind, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { BlockLists.init(context) }
    val state by BlockLists.state.collectAsState()
    val updating by BlockLists.updating.collectAsState()
    val lists = state.lists.filter { it.kind == kind }
    var showAdd by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val name = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.').orEmpty()
            val error = BlockLists.importFile(context, kind, name, uri)
            if (error != null) Toast.makeText(context, I18nR.string.blocklists_import_failed, Toast.LENGTH_LONG).show()
        }
    }

    Column(modifier) {
        Text(
            stringResource(if (kind == BlockListKind.WEB) I18nR.string.blocklists_web_info else I18nR.string.blocklists_torrent_info),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (kind == BlockListKind.TORRENT_IP) {
            // what the torrent session filters right now
            val enabled = lists.filter { it.enabled && it.entryCount > 0 }
            Text(
                if (enabled.isEmpty()) stringResource(I18nR.string.blocklists_torrent_none_active)
                else stringResource(I18nR.string.blocklists_torrent_active, enabled.sumOf { it.entryCount }, enabled.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        for (list in lists) {
            BlockListRow(list, list.id in updating)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { BlockLists.updateNow(context) }) { Text(stringResource(I18nR.string.blocklists_update_now)) }
            TextButton(onClick = { showAdd = true }) { Text(stringResource(I18nR.string.blocklists_add_custom)) }
            TextButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) { Text(stringResource(I18nR.string.blocklists_import_file)) }
        }
        Text(stringResource(I18nR.string.blocklists_schedule), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((s, label) in listOf(
                UpdateSchedule.OFF to I18nR.string.blocklists_schedule_off,
                UpdateSchedule.DAILY to I18nR.string.blocklists_schedule_daily,
                UpdateSchedule.WEEKLY to I18nR.string.blocklists_schedule_weekly,
            )) {
                FilterChip(
                    selected = state.schedule == s,
                    onClick = { BlockLists.setSchedule(context, s) },
                    label = { Text(stringResource(label)) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text(stringResource(I18nR.string.blocklists_wifi_only), modifier = Modifier.weight(1f))
            Switch(checked = state.wifiOnly, onCheckedChange = { BlockLists.setWifiOnly(context, it) })
        }
        Text(
            stringResource(I18nR.string.blocklists_schedule_info),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showAdd) AddCustomDialog(kind) { showAdd = false }
}

@Composable
private fun BlockListRow(list: BlockList, updating: Boolean) {
    val context = LocalContext.current
    val preset = BlockListPresets.byId(list.id)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(list.name, style = MaterialTheme.typography.bodyLarge)
                val subtle = MaterialTheme.colorScheme.onSurfaceVariant
                if (list.sourceUrl.isNotEmpty()) {
                    Text(
                        stringResource(I18nR.string.blocklists_downloaded_from, list.sourceUrl),
                        style = MaterialTheme.typography.bodySmall, color = subtle,
                    )
                } else {
                    Text(stringResource(I18nR.string.blocklists_imported), style = MaterialTheme.typography.bodySmall, color = subtle)
                }
                if (preset != null) {
                    if (preset.description.isNotEmpty()) {
                        Text(preset.description, style = MaterialTheme.typography.bodySmall, color = subtle)
                    }
                    Text(
                        stringResource(I18nR.string.blocklists_license, preset.license),
                        style = MaterialTheme.typography.bodySmall, color = subtle,
                    )
                }
                if (list.enabled) {
                    val status = when {
                        updating -> stringResource(I18nR.string.blocklists_updating)
                        list.lastUpdate > 0 -> stringResource(
                            I18nR.string.blocklists_status, list.entryCount,
                            DateUtils.getRelativeTimeSpanString(list.lastUpdate, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                        )
                        else -> stringResource(I18nR.string.blocklists_never)
                    }
                    Text(status, style = MaterialTheme.typography.bodySmall, color = subtle)
                    if (list.dataDate > 0) {
                        // the date the list itself carries: tells whether the list is still being maintained
                        Text(
                            stringResource(
                                I18nR.string.blocklists_data_date,
                                DateUtils.formatDateTime(context, list.dataDate, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH),
                            ),
                            style = MaterialTheme.typography.bodySmall, color = subtle,
                        )
                    }
                    list.lastError?.let {
                        Text(
                            stringResource(I18nR.string.blocklists_error, it),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            Switch(checked = list.enabled, onCheckedChange = { BlockLists.setEnabled(context, list.id, it) })
        }
        if (!list.builtin) {
            TextButton(onClick = { BlockLists.remove(context, list.id) }) { Text(stringResource(I18nR.string.blocklists_remove)) }
        }
    }
}

@Composable
private fun AddCustomDialog(kind: BlockListKind, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.blocklists_add_custom))  } },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(I18nR.string.blocklists_custom_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    url, { url = it; invalid = false }, label = { Text(stringResource(I18nR.string.blocklists_custom_url)) },
                    singleLine = true, isError = invalid, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    supportingText = if (invalid) ({ Text(stringResource(I18nR.string.blocklists_invalid_url)) }) else null,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (BlockLists.addCustom(context, kind, name, url) == null) onDismiss() else invalid = true
            }) { Text(stringResource(I18nR.string.blocklists_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.blocklists_cancel)) } },
    )
}
