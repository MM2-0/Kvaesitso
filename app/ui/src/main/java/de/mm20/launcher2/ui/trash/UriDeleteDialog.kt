package de.mm20.launcher2.ui.trash

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import de.mm20.launcher2.comms.files.TelosTrash
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.*
import org.koin.compose.koinInject
import java.io.File

/** The same deletion choice regardless of which Telos library opened the file. */
@Composable
fun UriDeleteDialog(uri: Uri, onDeleted: () -> Unit, onDismiss: () -> Unit, onPermanent: (() -> Unit)? = null) =
    UriDeleteDialog(listOf(uri), onDeleted, onDismiss, onPermanent)

@Composable
fun UriDeleteDialog(uris: List<Uri>, onDeleted: () -> Unit, onDismiss: () -> Unit, onPermanent: (() -> Unit)? = null) {
    require(uris.isNotEmpty())
    val context = LocalContext.current
    val trash: TelosTrash = koinInject()
    val scope = rememberCoroutineScope()
    var paths by remember(uris) { mutableStateOf<List<String?>>(emptyList()) }
    var resolved by remember(uris) { mutableStateOf(false) }
    var localTrash by remember(uris) { mutableStateOf(false) }
    var systemTrash by remember(uris) { mutableStateOf(false) }
    var confirming by remember(uris) { mutableStateOf(true) }
    var busy by remember(uris) { mutableStateOf(false) }
    var nativePending by remember(uris) { mutableStateOf(false) }
    val native = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        nativePending = false
        if (it.resultCode == Activity.RESULT_OK) onDeleted()
        onDismiss()
    }
    LaunchedEffect(uris) {
        paths = uris.map { trash.filePath(it) }
        localTrash = withContext(Dispatchers.IO) { paths.all { path -> path?.let { trash.supports(it) && File(it).exists() && File(it).parentFile?.canWrite() == true } == true } }
        systemTrash = Build.VERSION.SDK_INT >= 30 && uris.size <= 2000 && uris.all { it.authority == MediaStore.AUTHORITY } && withContext(Dispatchers.IO) {
            uris.all { uri ->
                val type = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
                type.startsWith("image/") || type.startsWith("video/") || type.startsWith("audio/")
            }
        }
        resolved = true
    }
    if (resolved && !confirming && busy && !nativePending) androidx.compose.material3.AlertDialog(
        onDismissRequest = {},
        containerColor = de.mm20.launcher2.ui.component.virtualAppPopup(),
        title = { TelosCyberHeading {  androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(I18nR.string.telos_trash_working))  } },
        text = { androidx.compose.material3.LinearProgressIndicator() }, confirmButton = {})
    fun failed() { Toast.makeText(context, I18nR.string.telos_trash_move_failed, Toast.LENGTH_LONG).show(); onDismiss() }
    if (resolved && confirming) TrashDeleteDialog(uris.size, localTrash || systemTrash,
        onTrash = {
            busy = true
            if (localTrash) scope.launch {
                busy = true
                var changed = false
                try { for (path in paths) { trash.move(path!!); changed = true }; onDeleted(); onDismiss() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { if (changed) onDeleted(); failed() }
                finally { busy = false }
            } else if (Build.VERSION.SDK_INT >= 30) {
                runCatching {
                    val request = MediaStore.createTrashRequest(context.contentResolver, uris, true)
                    nativePending = true
                    native.launch(IntentSenderRequest.Builder(request.intentSender).build())
                }.onFailure { nativePending = false; failed() }
            }
        },
        onPermanent = {
            busy = true
            if (onPermanent != null) { onPermanent(); onDismiss() }
            else if (Build.VERSION.SDK_INT >= 30 && systemTrash) {
                runCatching {
                    nativePending = true
                    native.launch(IntentSenderRequest.Builder(MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender).build())
                }.onFailure { nativePending = false; failed() }
            } else scope.launch {
                busy = true
                try {
                    val deleted = withContext(Dispatchers.IO) {
                        uris.map { uri -> when {
                            uri.scheme == "file" -> File(uri.path!!).delete()
                            DocumentsContract.isDocumentUri(context, uri) -> DocumentsContract.deleteDocument(context.contentResolver, uri)
                            else -> context.contentResolver.delete(uri, null, null) > 0
                        } }.all { it }
                    }
                    if (deleted) onDeleted() else failed()
                    onDismiss()
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { failed() }
                finally { busy = false }
            }
        },
        onDismiss = { confirming = false; if (!busy && !nativePending) onDismiss() },
        fixedRetentionDays = if (localTrash) null else if (systemTrash) 30 else null,
    )
}
