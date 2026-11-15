package de.mm20.launcher2.ui.files

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import android.content.pm.PackageManager
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.helper.InstalledApkExport
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.*
import java.io.File
import java.util.Locale

private data class ExportableApp(val packageName: String, val label: String)

/** Export only through an explicit CreateDocument grant; no silent Downloads overwrite. */
@Composable
fun FilesAppManagerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<ExportableApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var pendingBundle by rememberSaveable { mutableStateOf(false) }
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try {
            apps = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                pm.getInstalledApplications(0).map { app ->
                    ExportableApp(app.packageName, pm.getApplicationLabel(app).toString().take(1024))
                }.sortedBy { it.label.lowercase(Locale.ROOT) }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val selected = pending; val bundle = pendingBundle; pending = null
        if (uri != null) scope.launch {
            busy = true
            var complete = false
            try {
                withContext(Dispatchers.IO) {
                    require(selected != null)
                    val pm = context.packageManager
                    val initial = pm.getPackageInfo(selected, 0)
                    val app = requireNotNull(initial.applicationInfo)
                    val paths = listOf(app.sourceDir) + app.splitSourceDirs.orEmpty().sorted()
                    require((paths.size > 1) == bundle)
                    context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                        InstalledApkExport.write(paths.map(::File), output, checkActive = { ensureActive() })
                    } ?: error("Destination unavailable")
                    val after = pm.getPackageInfo(selected, 0)
                    val current = requireNotNull(after.applicationInfo)
                    require(initial.lastUpdateTime == after.lastUpdateTime && paths == listOf(current.sourceDir) + current.splitSourceDirs.orEmpty().sorted())
                    complete = true
                }
                Toast.makeText(context, context.getString(I18nR.string.hc_backup_saved), Toast.LENGTH_LONG).show()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally {
                if (!complete) withContext(NonCancellable + Dispatchers.IO) {
                    runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                }
                busy = false
            }
        }
    }
    val shown = remember(apps, query) { apps.filter { de.mm20.launcher2.comms.search.TelosSearch.matches(query, it.label, it.packageName) } }
    AlertDialog(onDismissRequest = onDismiss, title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_apps))  } }, text = {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
            item {
                Text(stringResource(I18nR.string.nf_apk_export_scope))
                OutlinedTextField(query, { query = it }, label = { Text(stringResource(I18nR.string.hc_search)) }, singleLine = true)
                if (loading || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (failed) Text(stringResource(I18nR.string.hc_export_failed), color = MaterialTheme.colorScheme.error)
            }
            items(shown, key = { it.packageName }) { app ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(app.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(app.packageName, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(enabled = !busy && pending == null, onClick = {
                        pending = app.packageName; failed = false
                        scope.launch {
                            val split = withContext(Dispatchers.IO) { runCatching {
                                context.packageManager.getApplicationInfo(app.packageName, 0).splitSourceDirs.orEmpty().isNotEmpty()
                            }.getOrNull() }
                            if (split == null) { pending = null; failed = true }
                            else try { pendingBundle = split; picker.launch(app.packageName + if (split) ".zip" else ".apk") }
                            catch (_: Exception) { pending = null; failed = true }
                        }
                    }) { Text(stringResource(I18nR.string.hc_export)) }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.hc_close)) } })
}
