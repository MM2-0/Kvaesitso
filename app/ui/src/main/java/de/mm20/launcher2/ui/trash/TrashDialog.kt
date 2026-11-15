package de.mm20.launcher2.ui.trash

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import de.mm20.launcher2.ui.component.virtualAppPopup
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.comms.files.TelosTrash
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.text.DateFormat
import java.util.Date

@Composable
fun TrashDialog(onDismiss: () -> Unit, onChanged: () -> Unit = {}) {
    val trash: TelosTrash = koinInject()
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<TelosTrash.Item>>(emptyList()) }
    var systemItems by remember { mutableStateOf<List<TelosTrash.SystemItem>>(emptyList()) }
    var confirmSystemDelete by remember { mutableStateOf<TelosTrash.SystemItem?>(null) }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<TelosTrash.Item?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val native = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()) { result ->
        scope.launch { items = trash.entries(); systemItems = trash.systemEntries(); busy = false; if (result.resultCode == android.app.Activity.RESULT_OK) onChanged() }
    }
    fun systemAction(item: TelosTrash.SystemItem, permanent: Boolean) {
        if (android.os.Build.VERSION.SDK_INT >= 30) runCatching {
            busy = true
            val request = if (permanent) android.provider.MediaStore.createDeleteRequest(context.contentResolver, listOf(item.uri))
                else android.provider.MediaStore.createTrashRequest(context.contentResolver, listOf(item.uri), false)
            native.launch(androidx.activity.result.IntentSenderRequest.Builder(request.intentSender).build())
        }.onFailure { busy = false; error = true }
    }
    LaunchedEffect(Unit) { try { items = trash.entries(); systemItems = trash.systemEntries() } finally { busy = false } }
    confirmSystemDelete?.let { item -> TrashDeleteDialog(1, false, {}, { systemAction(item, true) }, { confirmSystemDelete = null }) }
    confirmDelete?.let { item ->
        TrashDeleteDialog(1, false, {}, {
            scope.launch { busy = true; error = !trash.deletePermanently(item); items = trash.entries(); busy = false; onChanged() }
        }, { confirmDelete = null })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = de.mm20.launcher2.ui.component.virtualAppPopup(),
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.telos_trash_title))  } },
        text = { Column(Modifier.heightIn(max = 500.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error) Text(stringResource(I18nR.string.telos_trash_restore_failed), color = MaterialTheme.colorScheme.error)
            if (items.isEmpty() && systemItems.isEmpty() && !busy) Text(stringResource(I18nR.string.telos_trash_empty))
            LazyColumn {
                items(items, key = { it.entry.file.path }) { item ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(item.entry.name, style = MaterialTheme.typography.titleSmall)
                        Text(item.entry.original, style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(I18nR.string.telos_trash_expires, DateFormat.getDateInstance().format(Date(item.entry.expiresAt))), style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton(enabled = !busy, onClick = { scope.launch {
                                busy = true; error = !trash.restore(item); items = trash.entries(); busy = false; onChanged()
                            } }) { Text(stringResource(I18nR.string.telos_trash_restore)) }
                            TextButton(enabled = !busy, onClick = { confirmDelete = item }) { Text(stringResource(I18nR.string.telos_trash_permanent), color = MaterialTheme.colorScheme.error) }
                        }
                        HorizontalDivider()
                    }
                }
                items(systemItems, key = { it.uri.toString() }) { item ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(item.name, style = MaterialTheme.typography.titleSmall)
                        Text(item.original, style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(I18nR.string.telos_trash_expires, DateFormat.getDateInstance().format(Date(item.expiresAt))), style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton(enabled = !busy, onClick = { systemAction(item, false) }) { Text(stringResource(I18nR.string.telos_trash_restore)) }
                            TextButton(enabled = !busy, onClick = { confirmSystemDelete = item }) { Text(stringResource(I18nR.string.telos_trash_permanent), color = MaterialTheme.colorScheme.error) }
                        }
                        HorizontalDivider()
                    }
                }

            }
        } },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    scope.launch {
                        busy = true
                        val cleaner = de.mm20.launcher2.comms.files.DiskCleaner(context)
                        val junk = cleaner.scan()
                        cleaner.fillTrash(trash, junk)
                        items = trash.entries()
                        systemItems = trash.systemEntries()
                        busy = false
                        onChanged()
                    }
                }, enabled = !busy) { Text(stringResource(I18nR.string.trash_fill_bin)) }
                TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.hc_close)) }
            }
        })
}
