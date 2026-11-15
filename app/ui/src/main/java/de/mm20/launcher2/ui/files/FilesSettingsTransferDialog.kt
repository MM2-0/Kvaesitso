package de.mm20.launcher2.ui.files

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun FilesSettingsTransferButton(workspace: FilesWorkspaceViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val queued by workspace.transferQueue.items.collectAsState()
    val recovering by workspace.transferQueue.isRecovering.collectAsState()
    val importAllowed = !recovering && queued.none { it.status in setOf(FilesTransferQueue.Status.QUEUED, FilesTransferQueue.Status.RUNNING, FilesTransferQueue.Status.PAUSED, FilesTransferQueue.Status.FAILED) } && workspace.canImportSettings()
    var menu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<FilesSettingsTransfer.Settings?>(null) }
    var result by remember { mutableStateOf<Int?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            busy = true
            val text = runCatching { workspace.exportSettings() }
            scope.launch {
                result = withContext(Dispatchers.IO) { runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(text.getOrThrow()) } ?: error("Cannot write")
                }.fold({ I18nR.string.telos_files_settings_exported }, { I18nR.string.telos_files_settings_failed }) }
                busy = false
            }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val parsed = withContext(Dispatchers.IO) { runCatching {
                    context.contentResolver.openInputStream(uri)?.use {
                        FilesSettingsTransfer.parse(FilesSettingsTransfer.read(it))
                    } ?: error("Cannot read")
                } }
                pending = parsed.getOrNull()
                if (parsed.isFailure) result = I18nR.string.telos_files_settings_failed
                busy = false
            }
        }
    }
    Box {
    ChromeIcon(
        icon = de.mm20.launcher2.base.R.drawable.settings_24px,
        label = stringResource(I18nR.string.telos_files_settings_transfer),
        enabled = !busy,
        selected = menu,
        onClick = { menu = true },
    )
    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        if (!importAllowed) DropdownMenuItem(text = { Text(stringResource(I18nR.string.telos_files_settings_busy)) }, enabled = false, onClick = {})
        DropdownMenuItem(text = { Text(stringResource(I18nR.string.telos_files_settings_export)) }, onClick = { menu = false; export.launch("telos-files-settings.json") })
        DropdownMenuItem(text = { Text(stringResource(I18nR.string.telos_files_settings_import)) }, enabled = importAllowed, onClick = { menu = false; import.launch(arrayOf("application/json", "text/plain")) })
    }
    }
    pending?.let { settings ->
        AlertDialog(onDismissRequest = { pending = null }, title = { TelosCyberHeading {  Text(stringResource(I18nR.string.telos_files_settings_import))  } },
            text = { Text(stringResource(I18nR.string.telos_files_settings_confirm) + if (!importAllowed) "\n\n" + stringResource(I18nR.string.telos_files_settings_busy) else "") },
            confirmButton = { TextButton(enabled = importAllowed, onClick = {
                result = runCatching { workspace.importSettings(settings) }.fold({ I18nR.string.telos_files_settings_imported }, { I18nR.string.telos_files_settings_failed })
                pending = null
            }) { Text(stringResource(I18nR.string.telos_files_settings_import)) } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(I18nR.string.hc_cancel)) } })
    }
    result?.let { message -> AlertDialog(onDismissRequest = { result = null }, text = { Text(stringResource(message)) },
        confirmButton = { TextButton(onClick = { result = null }) { Text(stringResource(android.R.string.ok)) } }) }
}
