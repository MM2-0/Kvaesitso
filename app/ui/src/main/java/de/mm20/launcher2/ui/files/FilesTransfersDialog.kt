package de.mm20.launcher2.ui.files

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.R

@Composable
fun FilesTransfersDialog(queue: FilesTransferQueue, onDismiss: () -> Unit) {
    val transfers by queue.items.collectAsState()
    AlertDialog(onDismissRequest = onDismiss,
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.telos_files_transfers_title))  } },
        text = {
            Column {
                Text(stringResource(I18nR.string.telos_files_transfers_note), style = MaterialTheme.typography.bodySmall)
                if (transfers.isEmpty()) Text(stringResource(I18nR.string.telos_files_transfers_empty), Modifier.padding(top = 16.dp))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(transfers, key = { it.id }) { item ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                            Text(stringResource(if (item.move) I18nR.string.au_files_moving else I18nR.string.au_files_copying), style = MaterialTheme.typography.titleSmall)
                            Text(item.sources.joinToString { java.io.File(it).name }, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                            Text(item.destination, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                            Text(stringResource(when (item.status) {
                                FilesTransferQueue.Status.QUEUED -> I18nR.string.telos_files_transfer_queued
                                FilesTransferQueue.Status.RUNNING -> I18nR.string.telos_files_transfer_running
                                FilesTransferQueue.Status.PAUSED -> I18nR.string.telos_files_transfer_paused
                                FilesTransferQueue.Status.COMPLETE -> I18nR.string.telos_files_transfer_complete
                                FilesTransferQueue.Status.FAILED -> I18nR.string.telos_files_transfer_failed
                                FilesTransferQueue.Status.CANCELLED -> I18nR.string.au_files_cancelled
                                FilesTransferQueue.Status.ORIGINAL_RETAINED -> I18nR.string.telos_files_transfer_retained
                                FilesTransferQueue.Status.LIMIT_EXCEEDED -> I18nR.string.telos_files_transfer_limit
                            }), style = MaterialTheme.typography.bodySmall)
                            if (item.status == FilesTransferQueue.Status.RUNNING) {
                                if (item.total > 0) LinearProgressIndicator(progress = { (item.completed.toFloat() / item.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                                else LinearProgressIndicator(Modifier.fillMaxWidth())
                            }
                            Row {
                                when (item.status) {
                                    FilesTransferQueue.Status.RUNNING, FilesTransferQueue.Status.QUEUED -> TextButton(onClick = { queue.pause(item.id) }) { Text(stringResource(I18nR.string.telos_files_transfer_pause)) }
                                    FilesTransferQueue.Status.PAUSED, FilesTransferQueue.Status.FAILED -> TextButton(onClick = { queue.resume(item.id) }) { Text(stringResource(I18nR.string.telos_files_transfer_resume)) }
                                    else -> Unit
                                }
                                if (item.status in setOf(FilesTransferQueue.Status.RUNNING, FilesTransferQueue.Status.QUEUED, FilesTransferQueue.Status.PAUSED, FilesTransferQueue.Status.FAILED))
                                    TextButton(onClick = { queue.cancel(item.id) }) { Text(stringResource(I18nR.string.hc_cancel)) }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.hc_close)) } },
        dismissButton = { TextButton(onClick = { queue.clearHistory() }) { Text(stringResource(I18nR.string.telos_files_transfers_clear)) } })
}
