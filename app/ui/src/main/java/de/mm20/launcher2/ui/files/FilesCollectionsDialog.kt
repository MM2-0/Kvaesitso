package de.mm20.launcher2.ui.files

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import android.os.Environment
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.helper.FileSearchIndex
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.files.remote.RemotePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

private data class FilesCollectionResult(val entries: List<FsEntry>, val partial: Boolean)

/** Read-only virtual views of the bounded local index; never delete or alter files. */
@Composable
internal fun FilesCollectionsDialog(vm: FilesViewModel, onDismiss: () -> Unit, onOpen: (FsEntry) -> Unit) {
    var selected by remember { mutableStateOf("recent") }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf(FilesCollectionResult(emptyList(), false)) }
    val current = vm.path
    val root = remember(current) {
        if (current != null && !RemotePath.isRemote(current) && !ArchivePath.isArchive(current) &&
            !de.mm20.launcher2.ui.files.vault.VaultPath.isVault(current)) File(current) else Environment.getExternalStorageDirectory()
    }
    LaunchedEffect(selected, root, vm.showHidden, vm.bookmarks) {
        loading = true; failed = false
        try {
            result = withContext(Dispatchers.IO) {
                val coroutine = currentCoroutineContext()
                if (selected == "bookmarks") {
                    val bookmarks = vm.bookmarks.take(300).map { path ->
                        coroutine.ensureActive()
                        val file = File(path)
                        FsEntry(path, nameOf(path), if (path.startsWith("/")) file.isDirectory else true,
                            if (path.startsWith("/") && file.isFile) file.length() else -1,
                            if (path.startsWith("/")) file.lastModified() else 0)
                    }
                    FilesCollectionResult(bookmarks, vm.bookmarks.size > 300)
                } else {
                    val snapshot = FileSearchIndex.read(root) { coroutine.ensureActive() }
                    val candidates = snapshot.files.asSequence().filter { !it.directory && !it.symbolicLink && (vm.showHidden || !it.hidden) }
                        .filter { selected == "recent" || FileKind.of(it.name, false).name == selected }
                        .sortedWith(compareByDescending<de.mm20.launcher2.helper.IndexedFile> { it.modified }.thenBy { it.path })
                        .take(301).toList()
                    FilesCollectionResult(candidates.take(300).map { FsEntry(it.path, it.name, false, it.size, it.modified) },
                        snapshot.limited || snapshot.unreadable > 0 || candidates.size > 300)
                }
            }
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (_: Exception) { failed = true; result = FilesCollectionResult(emptyList(), false) }
        finally { loading = false }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { TelosCyberHeading {  Text(stringResource(I18nR.string.telos_files_collections))  } },
        text = { Column(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
            Text(stringResource(I18nR.string.telos_files_collections_scope, root.path), style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected == "recent", { selected = "recent" }, label = { Text(stringResource(I18nR.string.telos_files_collection_recent)) })
                FilterChip(selected == "bookmarks", { selected = "bookmarks" }, label = { Text(stringResource(I18nR.string.telos_files_collection_bookmarks)) })
                FileKind.entries.filter { it != FileKind.Folder && it != FileKind.Other }.forEach { kind ->
                    FilterChip(selected == kind.name, { selected = kind.name }, label = { Text(stringResource(kind.labelRes)) })
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (failed) Text(stringResource(I18nR.string.telos_files_collections_failed), color = MaterialTheme.colorScheme.error)
            if (result.partial) Text(stringResource(I18nR.string.telos_files_collections_partial), style = MaterialTheme.typography.bodySmall)
            if (!loading && !failed && result.entries.isEmpty()) Text(stringResource(I18nR.string.telos_files_collections_empty))
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                items(result.entries, key = { it.path }) { entry ->
                    TextButton(onClick = { onDismiss(); onOpen(entry) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(entry.path, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } })
}
