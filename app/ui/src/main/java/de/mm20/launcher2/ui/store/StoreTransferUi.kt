package de.mm20.launcher2.ui.store

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.store.installer.Downloader
import de.mm20.launcher2.store.installer.StoreDownloadStatus
import de.mm20.launcher2.ui.R

@Composable
internal fun StoreTransferProgress(id: String) {
    val downloader: Downloader = org.koin.compose.koinInject()
    val transfers by downloader.progress.collectAsStateWithLifecycle()
    val transfer = transfers[id] ?: return
    if (transfer.status == StoreDownloadStatus.Complete || transfer.status == StoreDownloadStatus.Failed) return
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val total = transfer.total
        val cyan = Color(0xFF00F2FE)
        val pink = Color(0xFFFF007F)
        
        if (total != null && total > 0) {
            val prog = (transfer.bytes.toFloat() / total).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { prog },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = cyan,
                trackColor = pink.copy(alpha = 0.2f)
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = cyan,
                trackColor = pink.copy(alpha = 0.2f)
            )
        }
        Text(
            when (transfer.status) {
                StoreDownloadStatus.Paused -> "[PAUSED]"
                StoreDownloadStatus.WaitingForNetwork -> "[CONNECTING...]"
                else -> "[STREAMING: ${transfer.bytesPerSecond / 1024} KB/s  ·  ${transfer.bytes / (1024 * 1024)} MB]"
            },
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = cyan,
        )
    }
}

@Composable
internal fun StoreUpdateConsent(viewModel: StoreViewModel) {
    val pending by viewModel.pendingUpdates.collectAsStateWithLifecycle()
    if (pending.isEmpty()) return
    AlertDialog(
        onDismissRequest = viewModel::dismissUpdates,
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.telos_store_auto_title))  } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(I18nR.string.telos_store_auto_explanation))
                Text(pending.joinToString { it.displayName }, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { viewModel.confirmUpdates(true) }) { Text(stringResource(I18nR.string.telos_store_auto_enable)) } },
        dismissButton = { TextButton(onClick = { viewModel.confirmUpdates(false) }) { Text(stringResource(I18nR.string.telos_store_update_once)) } },
    )
}
