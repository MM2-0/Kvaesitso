/*
 * Telos PDF tools.
 *
 * Adapted from PaperKnife+ (https://github.com/potatameister/PaperKnifePlus)
 * Copyright (C) potatameister and PaperKnife+ contributors
 * Copyright (C) 2026 Telos contributors
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version. It is distributed WITHOUT ANY WARRANTY; see the
 * GNU General Public License for more details (https://www.gnu.org/licenses/).
 *
 * PDF processing uses PdfBox-Android (https://github.com/TomRoush/PdfBox-Android, Apache-2.0).
 */

package de.mm20.launcher2.ui.media.docs.pdf

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.base.ProvideCompositionLocals
import de.mm20.launcher2.ui.theme.LauncherTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PDF toolkit. Start it with [PdfTools.createIntent]: the intent data is the (optional) Uri of a PDF,
 * the extra [PdfTools.EXTRA_NAME] is its display name.
 */
class PdfToolsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PdfTools.ensureInit(this)
        val vm = ViewModelProvider(this)[PdfToolsViewModel::class.java]
        if (savedInstanceState == null) {
            @Suppress("DEPRECATION")
            val uri = intent.data ?: intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            vm.start(uri, intent.getStringExtra(PdfTools.EXTRA_NAME), PdfTool.fromKey(intent.getStringExtra(PdfTools.EXTRA_TOOL)))
        }
        setContent {
            ProvideCompositionLocals {
                LauncherTheme {
                    PdfToolsApp(vm, onFinish = { finish() })
                }
            }
        }
    }
}

sealed interface PdfScreen {
    data object Home : PdfScreen
    data class Tool(val tool: PdfTool) : PdfScreen
    data object Result : PdfScreen
    data object History : PdfScreen
}

class PasswordRequest(val name: String, val wrong: Boolean, val answer: CompletableDeferred<String?>)

class PdfToolsViewModel(app: Application) : AndroidViewModel(app) {
    private val appContext: Context = app.applicationContext
    val context: Context get() = appContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var screen by mutableStateOf<PdfScreen>(PdfScreen.Home)
    val pdfs = mutableStateListOf<PdfSource>()
    val images = mutableStateListOf<ImageItem>()
    var selectedId by mutableLongStateOf(-1L)

    var loading by mutableStateOf(false)
    var passwordRequest by mutableStateOf<PasswordRequest?>(null)

    var busy by mutableStateOf(false)
    var progress by mutableStateOf<Pair<Int, Int>?>(null)
    var error by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)

    var results by mutableStateOf<List<ResultFile>>(emptyList())
    var resultTool by mutableStateOf<PdfTool?>(null)
    var inputBytes by mutableStateOf(0L)
    var saved by mutableStateOf<List<SavedFile>>(emptyList())

    private var job: Job? = null

    init {
        // leftovers of an earlier run that did not end cleanly
        Thread {
            val limit = System.currentTimeMillis() - 24L * 3600 * 1000
            runCatching {
                PdfEngine.workDir(appContext).walkTopDown().filter { it.isFile && it.lastModified() < limit }.forEach { it.delete() }
            }
        }.start()
    }

    val selected: PdfSource? get() = pdfs.firstOrNull { it.id == selectedId } ?: pdfs.lastOrNull()

    fun start(uri: Uri?, name: String?, tool: PdfTool?) {
        if (tool != null) screen = PdfScreen.Tool(tool)
        if (uri != null) addPdfs(listOf(uri), name)
    }

    fun displayName(uri: Uri): String {
        if (uri.scheme == "content") {
            runCatching {
                appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0)?.let { n -> return n }
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"
    }

    fun addPdfs(uris: List<Uri>, nameHint: String? = null, onAdded: (PdfSource) -> Unit = {}) {
        scope.launch {
            loading = true
            try {
                for (uri in uris) {
                    val src = loadOne(uri, if (uris.size == 1) nameHint else null) ?: continue
                    pdfs.add(src)
                    selectedId = src.id
                    onAdded(src)
                }
            } finally {
                loading = false
            }
        }
    }

    private suspend fun loadOne(uri: Uri, nameHint: String?): PdfSource? {
        val name = nameHint ?: withContext(Dispatchers.IO) { displayName(uri) }
        val allowBroken = (screen as? PdfScreen.Tool)?.tool == PdfTool.REPAIR
        var res = PdfEngine.loadSource(appContext, uri, name, allowBroken = allowBroken)
        var wrong = false
        while (res is LoadResult.NeedsPassword) {
            val needed: LoadResult.NeedsPassword = res
            val answer = CompletableDeferred<String?>()
            passwordRequest = PasswordRequest(needed.name, wrong, answer)
            val password = answer.await()
            passwordRequest = null
            if (password == null) {
                withContext(Dispatchers.IO) { needed.raw.delete() }
                return null
            }
            res = PdfEngine.loadSource(appContext, uri, name, password, needed.raw, allowBroken)
            wrong = res is LoadResult.NeedsPassword
        }
        return when (res) {
            is LoadResult.Ok -> res.source
            is LoadResult.Failed -> {
                error = appContext.getString(I18nR.string.pdft_error_open, name, res.reason)
                null
            }
            is LoadResult.NeedsPassword -> null
        }
    }

    fun removePdf(src: PdfSource) {
        pdfs.remove(src)
        scope.launch(Dispatchers.IO) { src.file.delete() }
    }

    fun addImages(uris: List<Uri>) {
        scope.launch {
            val items = withContext(Dispatchers.IO) { uris.map { ImageItem(PdfEngine.nextId(), it, displayName(it)) } }
            images.addAll(items)
        }
    }

    fun run(tool: PdfTool, inputSize: Long, block: suspend (Progress) -> List<ResultFile>) {
        if (busy) return
        busy = true
        progress = null
        job = scope.launch {
            try {
                val r = block { done, total -> progress = done to total }
                results = r
                resultTool = tool
                inputBytes = inputSize
                saved = emptyList()
                screen = PdfScreen.Result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = appContext.getString(I18nR.string.pdft_error_failed, e.message ?: e.javaClass.simpleName)
            } finally {
                busy = false
                progress = null
            }
        }
    }

    fun cancel() {
        job?.cancel()
        busy = false
        progress = null
    }

    fun back(finish: () -> Unit) {
        when (screen) {
            PdfScreen.Home -> finish()
            is PdfScreen.Tool -> screen = PdfScreen.Home
            PdfScreen.Result -> screen = resultTool?.let { PdfScreen.Tool(it) } ?: PdfScreen.Home
            PdfScreen.History -> screen = PdfScreen.Home
        }
    }

    // ---------------------------------------------------------------- saving

    fun saveToDocuments() = save(results) { PdfStorage.saveToMediaStore(appContext, it) }
    fun saveToFolder(tree: Uri) {
        persistGrant(tree)
        save(results) { PdfStorage.saveToTree(appContext, tree, it) }
    }

    fun saveAs(target: Uri) {
        persistGrant(target)
        save(results.take(1)) { PdfStorage.saveToDocument(appContext, target, it) }
    }

    fun share(uris: List<Uri>) {
        scope.launch {
            val shared = withContext(Dispatchers.IO) {
                PdfStorage.share(appContext, uris)
            }
            if (!shared) message = appContext.getString(I18nR.string.pdft_error_open_other)
        }
    }

    /** Keeps access to the chosen folder or file so that history entries can still be opened later. */
    private fun persistGrant(uri: Uri) {
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    private fun save(files: List<ResultFile>, op: (ResultFile) -> SavedFile) {
        if (busy) return
        busy = true
        progress = null
        job = scope.launch {
            try {
                val done = withContext(Dispatchers.IO) { files.map(op) }
                saved = saved + done
                PdfStorage.addHistory(appContext, resultTool, done)
                message = appContext.getString(I18nR.string.pdft_saved_n, done.size)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = appContext.getString(I18nR.string.pdft_error_save, e.message ?: e.javaClass.simpleName)
            } finally {
                busy = false
            }
        }
    }

    fun useResultAsInput(r: ResultFile) {
        scope.launch {
            loading = true
            val src = withContext(Dispatchers.IO) { PdfEngine.sourceFromResult(appContext, r) }
            loading = false
            if (src == null) {
                error = appContext.getString(I18nR.string.pdft_error_failed, r.name)
            } else {
                pdfs.add(src)
                selectedId = src.id
                screen = PdfScreen.Home
            }
        }
    }

    override fun onCleared() {
        scope.cancel()
        Thread { PdfEngine.clearCache(appContext) }.start()
    }
}

// ---------------------------------------------------------------- app host

@Composable
fun PdfToolsApp(vm: PdfToolsViewModel, onFinish: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }
    BackHandler {
        if (vm.busy) vm.cancel() else vm.back(onFinish)
    }
    when (val s = vm.screen) {
        PdfScreen.Home -> HomeScreen(vm, snackbar, onFinish)
        is PdfScreen.Tool -> ToolRouter(vm, s.tool, snackbar)
        PdfScreen.Result -> ResultScreen(vm, snackbar)
        PdfScreen.History -> HistoryScreen(vm, snackbar)
    }

    vm.passwordRequest?.let { req ->
        PasswordDialog(
            name = req.name,
            wrong = req.wrong,
            onSubmit = { req.answer.complete(it) },
            onCancel = { req.answer.complete(null) },
        )
    }
    if (vm.busy) {
        val p = vm.progress
        AlertDialog(
            onDismissRequest = {},
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.pdft_working))  } },
            text = {
                Column {
                    if (p != null && p.second > 0) {
                        LinearProgressIndicator(progress = { (p.first.toFloat() / p.second).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Text(
                            stringResource(I18nR.string.pdft_progress, p.first.coerceAtMost(p.second), p.second),
                            Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.cancel() }) { Text(stringResource(I18nR.string.pdft_cancel)) } },
        )
    }
    if (vm.loading && vm.passwordRequest == null) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Text(stringResource(I18nR.string.pdft_loading), Modifier.padding(start = 16.dp))
                }
            },
        )
    }
    vm.error?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.error = null },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.pdft_error_title))  } },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { vm.error = null }) { Text(stringResource(I18nR.string.pdft_ok)) } },
        )
    }
}

// ---------------------------------------------------------------- home

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(vm: PdfToolsViewModel, snackbar: SnackbarHostState, onFinish: () -> Unit) {
    val pickPdf = rememberPdfPicker(multiple = true) { vm.addPdfs(it) }
    var previewing by remember { mutableStateOf<PdfSource?>(null) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { TelosCyberHeading {  Text(stringResource(I18nR.string.pdft_title))  } },
                navigationIcon = {
                    IconButton(onClick = onFinish) {
                        Ico(BaseR.drawable.arrow_back_24px, description = stringResource(I18nR.string.pdft_back))
                    }
                },
                actions = {
                    IconButton(onClick = { vm.screen = PdfScreen.History }) {
                        Ico(BaseR.drawable.schedule_24px, description = stringResource(I18nR.string.pdft_history))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    stringResource(I18nR.string.pdft_home_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { SectionLabel(stringResource(I18nR.string.pdft_files)) }
            if (vm.pdfs.isEmpty()) {
                item {
                    Text(
                        stringResource(I18nR.string.pdft_no_files),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(vm.pdfs.toList(), key = { it.id }) { src ->
                    FileHeader(src, onChange = null, onPreview = { previewing = src }, onRemove = { vm.removePdf(src) })
                }
            }
            item {
                FilledTonalButton(onClick = pickPdf) { Text(stringResource(I18nR.string.pdft_add_pdf)) }
            }
            for (group in ToolGroup.entries) {
                item(key = "group_${group.name}") { SectionLabel(stringResource(group.title), Modifier.padding(top = 8.dp)) }
                val tools = PdfTool.entries.filter { it.group == group }
                items(tools.chunked(2), key = { row -> row.first().key }) { row ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { tool ->
                            ToolCard(tool, Modifier.weight(1f).fillMaxHeight()) { vm.screen = PdfScreen.Tool(tool) }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
    PreviewHost(previewing, if (previewing != null) 0 else null) { previewing = null }
}

@Composable
private fun ToolCard(tool: PdfTool, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(12.dp)) {
            Box(
                Modifier.size(36.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Ico(tool.icon, Modifier.size(20.dp), MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Text(
                stringResource(tool.title),
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(tool.description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------------------------------------------------------- result

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultScreen(vm: PdfToolsViewModel, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val results = vm.results
    val first = results.firstOrNull()
    val mime = first?.mime ?: "application/pdf"
    val tree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.saveToFolder(uri)
    }
    val createDoc = rememberLauncherForActivityResult(remember(mime) { ActivityResultContracts.CreateDocument(mime) }) { uri ->
        if (uri != null) vm.saveAs(uri)
    }
    var previewing by remember { mutableStateOf<ResultFile?>(null) }

    ToolFrame(
        title = stringResource(I18nR.string.pdft_result_title),
        onBack = { vm.back {} },
        snackbar = snackbar,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                        Ico(BaseR.drawable.check_24px, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(stringResource(I18nR.string.pdft_done), style = MaterialTheme.typography.titleMedium)
                        Text(
                            vm.resultTool?.let { stringResource(it.title) } ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (vm.resultTool == PdfTool.COMPRESS && results.size == 1 && vm.inputBytes > 0) {
                item {
                    val after = results[0].size
                    val saving = ((vm.inputBytes - after) * 100f / vm.inputBytes)
                    Text(
                        if (after < vm.inputBytes) {
                            stringResource(
                                I18nR.string.pdft_compress_result,
                                formatBytes(context, vm.inputBytes),
                                formatBytes(context, after),
                                saving.toInt(),
                            )
                        } else {
                            stringResource(I18nR.string.pdft_compress_no_gain, formatBytes(context, vm.inputBytes), formatBytes(context, after))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(results, key = { it.file.absolutePath }) { r ->
                ResultCard(r, onPreview = { previewing = r })
            }
            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
            item { SectionLabel(stringResource(I18nR.string.pdft_save)) }
            if (PdfStorage.mediaStoreSupported) {
                item {
                    Button(onClick = { vm.saveToDocuments() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(I18nR.string.pdft_save_to, PdfStorage.mediaStoreFolder(mime)))
                    }
                }
            }
            item {
                OutlinedButton(onClick = { tree.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(I18nR.string.pdft_save_choose_folder))
                }
            }
            if (results.size == 1) {
                item {
                    OutlinedButton(onClick = { createDoc.launch(results[0].name) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(I18nR.string.pdft_save_as))
                    }
                }
            }
            if (vm.saved.isNotEmpty()) {
                item {
                    Column {
                        Text(stringResource(I18nR.string.pdft_saved_files), style = MaterialTheme.typography.titleSmall)
                        vm.saved.forEach {
                            Text("${it.name} (${formatBytes(context, it.size)})", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item { SectionLabel(stringResource(I18nR.string.pdft_then)) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        val items = if (vm.saved.isNotEmpty()) vm.saved.map { it.uri to it.mime }
                        else results.map { PdfStorage.cacheUri(context, it.file) to it.mime }
                        vm.share(items.map { it.first })
                    }) { Text(stringResource(I18nR.string.pdft_share)) }
                    if (results.size == 1) {
                        FilledTonalButton(onClick = {
                            val uri = vm.saved.firstOrNull()?.uri ?: PdfStorage.cacheUri(context, results[0].file)
                            try {
                                PdfStorage.open(context, uri, results[0].mime)
                            } catch (e: ActivityNotFoundException) {
                                vm.message = context.getString(I18nR.string.pdft_error_open_other)
                            }
                        }) { Text(stringResource(I18nR.string.pdft_open)) }
                    }
                }
            }
            if (results.size == 1 && results[0].mime == "text/plain") {
                item {
                    OutlinedButton(
                        onClick = {
                            val text = runCatching { results[0].file.readText() }.getOrDefault("")
                            val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                            cm?.setPrimaryClip(android.content.ClipData.newPlainText(null, text))
                            vm.message = context.getString(I18nR.string.pdft_copied)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(I18nR.string.pdft_copy_text)) }
                }
            }
            if (results.size == 1 && results[0].mime == "application/pdf") {
                item {
                    OutlinedButton(onClick = { vm.useResultAsInput(results[0]) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(I18nR.string.pdft_use_as_input))
                    }
                }
            }
            item {
                TextButton(onClick = { vm.screen = PdfScreen.Home }) { Text(stringResource(I18nR.string.pdft_back_to_tools)) }
            }
        }
    }
    previewing?.let { r ->
        val pages = rememberPdfPages(r.file)
        if (pages != null) PdfPreviewDialog(pages, 0) { previewing = null }
    }
}

@Composable
private fun ResultCard(r: ResultFile, onPreview: () -> Unit) {
    val context = LocalContext.current
    val isPdf = r.mime == "application/pdf"
    val pages = if (isPdf) rememberPdfPages(r.file) else null
    val textPreview by produceState<String?>(null, r) {
        if (r.mime == "text/plain") {
            value = withContext(Dispatchers.IO) {
                runCatching { r.file.bufferedReader().use { rd -> CharArray(1500).let { buf -> String(buf, 0, rd.read(buf).coerceAtLeast(0)) } } }.getOrNull()
            }
        }
    }
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (pages != null && pages.count > 0) {
                PdfThumb(pages, 0, Modifier.width(56.dp).height(78.dp))
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(r.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    formatBytes(context, r.size) + if (pages != null) " - " + stringResource(I18nR.string.pdft_pages_count, pages.count) else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                textPreview?.let {
                    Text(it, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, maxLines = 6, overflow = TextOverflow.Ellipsis)
                }
            }
            if (pages != null) {
                IconButton(onClick = onPreview) {
                    Ico(BaseR.drawable.visibility_24px, description = stringResource(I18nR.string.pdft_preview))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- history

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryScreen(vm: PdfToolsViewModel, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(PdfStorage.loadHistory(context)) }
    ToolFrame(
        title = stringResource(I18nR.string.pdft_history),
        onBack = { vm.back {} },
        snackbar = snackbar,
        actions = {
            if (entries.isNotEmpty()) {
                TextButton(onClick = {
                    PdfStorage.clearHistory(context)
                    entries = emptyList()
                }) { Text(stringResource(I18nR.string.pdft_history_clear)) }
            }
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(I18nR.string.pdft_history_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries, key = { it.id }) { entry ->
                    val tool = PdfTool.fromKey(entry.tool)
                    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Column(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        tool?.let { stringResource(it.title) } ?: stringResource(I18nR.string.pdft_title),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        DateUtils.getRelativeTimeSpanString(entry.time).toString(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = {
                                    PdfStorage.removeHistory(context, entry.id)
                                    entries = entries.filter { it.id != entry.id }
                                }) { Ico(BaseR.drawable.delete_24px, description = stringResource(I18nR.string.pdft_remove)) }
                            }
                            entry.files.forEach { f ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${f.name} (${formatBytes(context, f.size)})",
                                        Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    TextButton(onClick = {
                                        try {
                                            PdfStorage.open(context, f.uri, f.mime)
                                        } catch (e: Exception) {
                                            vm.message = context.getString(I18nR.string.pdft_error_open_other)
                                        }
                                    }) { Text(stringResource(I18nR.string.pdft_open)) }
                                    TextButton(onClick = {
                                        vm.share(listOf(f.uri))
                                    }) { Text(stringResource(I18nR.string.pdft_share)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
