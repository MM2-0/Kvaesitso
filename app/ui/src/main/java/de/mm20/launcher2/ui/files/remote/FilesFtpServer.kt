package de.mm20.launcher2.ui.files.remote

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.mm20.launcher2.comms.files.FilesFtpServer
import de.mm20.launcher2.comms.files.FilesSharingServer
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Composable
fun FilesFtpServerDialog(path: String, onDismiss: () -> Unit) {
    var server by remember { mutableStateOf<FilesFtpServer?>(null) }
    var uploads by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val disposed = remember { AtomicBoolean(false) }
    val pending = remember { AtomicReference<FilesFtpServer?>(null) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) {
            server?.close(); server = null; pending.getAndSet(null)?.close()
        } }
        lifecycle.addObserver(observer)
        onDispose { disposed.set(true); lifecycle.removeObserver(observer); pending.getAndSet(null)?.close(); server?.close() }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { TelosCyberHeading {  Text("FTP")  } }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(stringResource(I18nR.string.nf_ftp_scope))
            Text(path, Modifier.padding(vertical = 8.dp))
            Row { Switch(checked = uploads, enabled = server == null && !busy, onCheckedChange = { uploads = it })
                Text(stringResource(I18nR.string.nf_ftp_uploads), Modifier.padding(8.dp)) }
            server?.let { session -> SelectionContainer { Column {
                Text(session.url)
                Text(stringResource(I18nR.string.ermis_username) + ": " + session.username)
                Text(stringResource(I18nR.string.ermis_password) + ": " + session.password)
            } } }
            if (busy) LinearProgressIndicator()
            if (failed) Text(stringResource(I18nR.string.telos_files_sharing_failed), color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = {
        if (server != null) TextButton(onClick = { server?.close(); server = null }) { Text(stringResource(I18nR.string.telos_files_sharing_stop)) }
        else TextButton(enabled = !busy, onClick = {
            busy = true; failed = false
            val allowUploads = uploads
            scope.launch {
                val opened = withContext(Dispatchers.IO) { runCatching {
                    val address = FilesSharingServer.lanAddresses().firstOrNull() ?: error("No LAN")
                    FilesFtpServer(File(path), address, allowUploads).also { session ->
                        pending.set(session); if (disposed.get()) pending.getAndSet(null)?.close()
                    }
                }.getOrNull() }
                pending.compareAndSet(opened, null)
                if (disposed.get() || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) opened?.close()
                else { server = opened?.takeIf { it.isOpen }; failed = server == null }
                busy = false
            }
        }) { Text(stringResource(I18nR.string.telos_files_sharing_start)) }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.telos_files_sharing_close)) } })
}
