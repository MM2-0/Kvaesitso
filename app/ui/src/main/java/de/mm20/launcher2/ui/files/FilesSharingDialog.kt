package de.mm20.launcher2.ui.files

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.mm20.launcher2.comms.files.FilesSharingServer
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.comms.encodeQr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Telos Drop: P2P Wi-Fi File Transfer with instant QR code scanning and direct web download. */
@Composable
fun FilesSharingDialog(path: String, onDismiss: () -> Unit) {
    var server by remember { mutableStateOf<FilesSharingServer?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val disposed = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val pending = remember { java.util.concurrent.atomic.AtomicReference<FilesSharingServer?>(null) }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { server?.close(); server = null }
        }
        lifecycle.addObserver(observer)
        onDispose { disposed.set(true); pending.getAndSet(null)?.close(); lifecycle.removeObserver(observer); server?.close(); server = null }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            TelosCyberHeading {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(BaseR.drawable.share_24px),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(I18nR.string.telos_drop_title), fontWeight = FontWeight.Bold)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(I18nR.string.telos_drop_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = File(path).name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                server?.let { sharing ->
                    val qrBitmap = remember(sharing.url) { encodeQr(sharing.url, 512) }
                    if (qrBitmap != null) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "Telos Drop QR Code",
                                modifier = Modifier.size(180.dp).padding(12.dp)
                            )
                        }
                    }
                    SelectionContainer {
                        Text(
                            text = sharing.url,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (failed) {
                    Text(
                        stringResource(I18nR.string.telos_files_sharing_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            if (server != null) {
                Button(
                    onClick = { server?.close(); server = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                ) {
                    Text(stringResource(I18nR.string.telos_drop_stop))
                }
            } else {
                Button(
                    enabled = !busy,
                    onClick = {
                        busy = true; failed = false
                        scope.launch {
                            val opened = withContext(Dispatchers.IO) { runCatching {
                                val address = FilesSharingServer.lanAddresses().firstOrNull() ?: error("No LAN")
                                FilesSharingServer(File(path), address).also { created ->
                                    pending.set(created)
                                    if (disposed.get()) pending.getAndSet(null)?.close()
                                }
                            }.getOrNull() }
                            pending.compareAndSet(opened, null)
                            if (disposed.get() || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) opened?.close()
                            else { server = opened; failed = opened == null }
                            busy = false
                        }
                    }
                ) {
                    Text(stringResource(I18nR.string.telos_drop_start))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(I18nR.string.telos_files_sharing_close))
            }
        }
    )
}
