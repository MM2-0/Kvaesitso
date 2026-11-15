package de.mm20.launcher2.ui.files

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.helper.BatchNames
import de.mm20.launcher2.ui.R

@Composable
internal fun BatchRenameDialog(vm: FilesViewModel, items: List<FsEntry>, onDismiss: () -> Unit) {
    var pattern by remember { mutableStateOf("{name}_{n:3}{ext}") }
    var start by remember { mutableStateOf("1") }
    var find by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var regex by remember { mutableStateOf(false) }
    val names = remember(items, pattern, start, find, replacement, regex) {
        runCatching {
            val first = start.toInt().also { require(it >= 0) }
            BatchNames.plan(items.map { BatchNames.Input(it.name, it.isDir) }, pattern, first, find, replacement, regex)
        }.getOrNull()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.telos_files_batch_rename))  } },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(I18nR.string.telos_files_rename_tokens), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(pattern, { pattern = it }, label = { Text(stringResource(I18nR.string.telos_files_rename_pattern)) }, singleLine = true)
                OutlinedTextField(start, { start = it }, label = { Text(stringResource(I18nR.string.telos_files_rename_start)) }, singleLine = true)
                OutlinedTextField(find, { find = it }, label = { Text(stringResource(I18nR.string.telos_files_rename_find)) }, singleLine = true)
                OutlinedTextField(replacement, { replacement = it }, label = { Text(stringResource(I18nR.string.telos_files_rename_replace)) }, singleLine = true)
                Row { Checkbox(regex, { regex = it }); Text(stringResource(I18nR.string.telos_files_rename_regex), modifier = Modifier.padding(top = 12.dp)) }
                if (names == null) Text(stringResource(I18nR.string.telos_files_rename_invalid), color = MaterialTheme.colorScheme.error)
                else items.zip(names).take(8).forEach { (item, name) -> Text("${item.name} → $name", style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(enabled = names != null, onClick = { names?.let { vm.batchRename(items, it) }; onDismiss() }) { Text(stringResource(I18nR.string.hc_rename)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}
