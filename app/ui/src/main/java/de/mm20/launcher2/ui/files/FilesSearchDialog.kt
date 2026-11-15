package de.mm20.launcher2.ui.files

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.helper.FileSearchFilter
import de.mm20.launcher2.ui.R
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

@Composable
internal fun FilesSearchDialog(vm: FilesViewModel, onDismiss: () -> Unit) {
    var extensions by remember { mutableStateOf(vm.searchFilter.extensions.joinToString(", ")) }
    var minimum by remember { mutableStateOf(vm.searchFilter.minimumBytes?.div(1024)?.toString().orEmpty()) }
    var maximum by remember { mutableStateOf(vm.searchFilter.maximumBytes?.div(1024)?.toString().orEmpty()) }
    fun dateText(value: Long?) = value?.let { java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString() }.orEmpty()
    var after by remember { mutableStateOf(dateText(vm.searchFilter.modifiedAfter)) }
    var before by remember { mutableStateOf(dateText(vm.searchFilter.modifiedBefore)) }
    var kind by remember { mutableStateOf(vm.searchKind) }; var recursive by remember { mutableStateOf(vm.recursiveSearch) }; var everywhere by remember { mutableStateOf(vm.searchEverywhere) }
    fun bytes(text: String): Long? = text.takeIf { it.isNotBlank() }?.toLongOrNull()?.takeIf { it >= 0 && it <= Long.MAX_VALUE / 1024 }?.times(1024)
    fun date(text: String, end: Boolean): Long? = runCatching {
        if (text.isBlank()) null else LocalDate.parse(text).let { if (end) it.plusDays(1) else it }
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - if (end) 1 else 0
    }.getOrNull()
    val min = bytes(minimum); val max = bytes(maximum); val start = date(after, false); val end = date(before, true)
    val extensionSet = extensions.split(',').map { it.trim().trimStart('.').lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }.toSet()
    val valid = (minimum.isBlank() || min != null) && (maximum.isBlank() || max != null) &&
        (after.isBlank() || start != null) && (before.isBlank() || end != null) &&
        (min == null || max == null || min <= max) && (start == null || end == null || start <= end) &&
        extensionSet.size <= 32 && extensionSet.all { it.length <= 32 && '/' !in it && '\\' !in it }
    AlertDialog(onDismissRequest = onDismiss, title = { TelosCyberHeading {  Text(stringResource(I18nR.string.telos_files_search_filters))  } }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(kind == null, { kind = null }, label = { Text(stringResource(I18nR.string.hf_store_filter_all)) })
                FileKind.entries.forEach { item -> FilterChip(kind == item, { kind = item }, label = { Text(stringResource(item.labelRes)) }) }
            }
            OutlinedTextField(extensions, { extensions = it.take(1024) }, label = { Text(stringResource(I18nR.string.telos_files_search_extensions)) }, singleLine = true)
            OutlinedTextField(minimum, { minimum = it.take(20) }, label = { Text(stringResource(I18nR.string.telos_files_search_minimum)) }, singleLine = true)
            OutlinedTextField(maximum, { maximum = it.take(20) }, label = { Text(stringResource(I18nR.string.telos_files_search_maximum)) }, singleLine = true)
            OutlinedTextField(after, { after = it.take(10) }, label = { Text(stringResource(I18nR.string.telos_files_search_after)) }, singleLine = true)
            OutlinedTextField(before, { before = it.take(10) }, label = { Text(stringResource(I18nR.string.telos_files_search_before)) }, singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(recursive, { recursive = it }); Text(stringResource(if (vm.path?.let { de.mm20.launcher2.ui.files.remote.RemotePath.isRemote(it) } == true) I18nR.string.telos_files_remote_search_recursive else I18nR.string.telos_files_search_recursive)) }
            Text(stringResource(if (vm.path?.let { de.mm20.launcher2.ui.files.remote.RemotePath.isRemote(it) } == true) I18nR.string.telos_files_remote_search_scope else I18nR.string.telos_files_search_scope), style = MaterialTheme.typography.bodySmall)
            if (!valid) Text(stringResource(I18nR.string.telos_files_search_invalid), color = MaterialTheme.colorScheme.error)
            if (vm.volumes.isNotEmpty() && !vm.rootMode) Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(everywhere, { everywhere = it })
                Column { Text(stringResource(I18nR.string.telos_files_search_everywhere)); Text(stringResource(I18nR.string.telos_files_search_everywhere_summary), style = MaterialTheme.typography.bodySmall) }
            }
            TextButton(onClick = { extensions = ""; minimum = ""; maximum = ""; after = ""; before = ""; kind = null; recursive = true; everywhere = false }) { Text(stringResource(I18nR.string.telos_files_search_reset)) }
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = {
        vm.updateSearchEverywhere(everywhere); vm.setSearchOptions(FileSearchFilter(extensionSet, min, max, start, end), kind, recursive); onDismiss()
    }) { Text(stringResource(I18nR.string.hc_apply)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.hc_cancel)) } })
}
