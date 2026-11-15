package de.mm20.launcher2.ui.media.docs

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.base.ProvideCompositionLocals
import de.mm20.launcher2.ui.media.docs.office.BigTextFile
import de.mm20.launcher2.ui.media.docs.office.DocFiles
import de.mm20.launcher2.ui.media.docs.office.OfficeController
import de.mm20.launcher2.ui.media.docs.office.OfficeDocs
import de.mm20.launcher2.ui.media.docs.office.OfficeView
import de.mm20.launcher2.ui.media.docs.office.PdfHit
import de.mm20.launcher2.ui.media.docs.office.PdfPageText
import de.mm20.launcher2.ui.media.docs.office.PdfTextSearch
import de.mm20.launcher2.ui.files.ArchiveRarUnavailableException
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import de.mm20.launcher2.ui.component.rememberVirtualAppBlur
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import de.mm20.launcher2.ui.media.docs.office.SaveOutcome
import de.mm20.launcher2.ui.media.docs.office.WriteResult
import de.mm20.launcher2.ui.media.docs.pdf.PdfToolsActivity
import de.mm20.launcher2.ui.theme.LauncherTheme
import de.mm20.launcher2.ui.component.virtualAppElevated
import de.mm20.launcher2.ui.component.virtualAppGlass
import de.mm20.launcher2.ui.component.virtualAppSurface
import de.mm20.launcher2.ui.media.photos.ViewerLibrary
import de.mm20.launcher2.ui.theme.transparency.transparency
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.material3.TopAppBarDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TEXT_EDIT_LIMIT = 2 * 1024 * 1024

/**
 * Telos Viewer as a viewer for documents: PDF pages (with search and page jump), text files (that can be edited), and
 * Word, Excel, PowerPoint and OpenDocument files (preview and editing, old binary formats read-only or converted).
 * The page layout and fonts of Office files are not reproduced.
 */
class DocumentViewerActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        val uri = intent.data ?: intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (uri == null) { finish(); return }
        var name = displayName(uri)
        // Google Drive shortcuts open in the Google app or the browser, there is nothing to show here
        if (!GoogleShortcuts.isShortcut(name)) GoogleShortcuts.extFor(intent.type)?.let { name = "$name.$it" }
        if (GoogleShortcuts.isShortcut(name)) {
            GoogleShortcuts.open(this, uri, DocumentTypes.ext(name))
            finish()
            return
        }
        setContent {
            ProvideCompositionLocals {
                LauncherTheme {
                    DocumentScreen(uri, name, onClose = { finish() })
                }
            }
        }
    }

    private fun displayName(uri: Uri): String {
        if (uri.scheme == "content") {
            runCatching {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) return it.getString(0) }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: getString(I18nR.string.od_default_name)
    }
}

private sealed interface DocState {
    data object Loading : DocState
    data class Failed(val message: String) : DocState
    class Pdf(val doc: PdfDoc) : DocState
    class Blocks(val items: List<ViewItem>) : DocState
    /** [copyOf] is set when this is a copy of the beginning of a big file: it can only be saved as a new file */
    class Text(val text: String, val source: File, val copyOf: BigTextFile?) : DocState
    class BigText(val big: BigTextFile) : DocState
    class Office(val controller: OfficeController) : DocState
    class Comic(val pages: List<File>) : DocState
    class Graphic(val bitmap: Bitmap?, val file: File, val svg: Boolean, val caption: String) : DocState
}

private sealed interface ViewItem {
    class Block(val block: DocBlock) : ViewItem
    class Row(val cells: List<String>, val scroll: androidx.compose.foundation.ScrollState, val header: Boolean) : ViewItem
}

/** A PDF opened with the system's renderer. Only one page can be open at a time. */
private class PdfDoc(val file: File) {
    private val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(fd)
    val count: Int = renderer.pageCount

    @Synchronized
    fun aspect(page: Int): Float = renderer.openPage(page).use { it.width.toFloat() / it.height }

    @Synchronized
    fun render(page: Int, width: Int): Bitmap = renderer.openPage(page).use { p ->
        // a zoomed page must not need more than ~16 megapixels (64 MB), or several visible pages run out of memory
        val ratio = p.height / p.width.toFloat()
        val w = minOf(width.toFloat(), kotlin.math.sqrt(16_000_000f / ratio)).toInt().coerceAtLeast(1)
        val height = (w * ratio).toInt().coerceAtLeast(1)
        Bitmap.createBitmap(w, height, Bitmap.Config.ARGB_8888).also {
            it.eraseColor(AndroidColor.WHITE)
            p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        }
    }

    // waits for a page that is being drawn: closing the renderer under it crashes
    @Synchronized
    fun close() { runCatching { renderer.close() }; runCatching { fd.close() } }
}

private fun convertedName(name: String): String = name.substringBeforeLast('.') + "." + when (name.substringAfterLast('.', "").lowercase()) {
    "xls" -> "xlsx"; "ppt" -> "pptx"; else -> "docx"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentScreen(uri: Uri, name: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var curUri by remember { mutableStateOf(uri) }
    var curName by remember { mutableStateOf(name) }
    var state by remember { mutableStateOf<DocState>(DocState.Loading) }
    val stateRef by rememberUpdatedState(state)
    var editing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showJump by remember { mutableStateOf(false) }
    var pdfNight by remember { mutableStateOf(false) }
    var highlightMode by remember { mutableStateOf(false) }
    var epubStyle by remember { mutableStateOf(ViewerLibrary.epubStyle(context)) }
    var textUndo by remember { mutableStateOf(false) }
    val pdfList = rememberLazyListState()
    var searcher by remember { mutableStateOf<PdfTextSearch?>(null) }

    // Save-as: one launcher, the mime type and what happens with the chosen location are set before it starts
    var saveMime by remember { mutableStateOf("*/*") }
    var onTarget by remember { mutableStateOf<((Uri) -> Unit)?>(null) }
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(saveMime)) { target ->
        if (target != null) onTarget?.invoke(target)
        onTarget = null
    }
    var launchName by remember { mutableStateOf<String?>(null) }
    // the launcher is started after the next composition, so that it already uses the new mime type
    LaunchedEffect(launchName) {
        launchName?.let { launchName = null; saveAs.launch(it) }
    }
    fun askTarget(suggested: String, mime: String, then: (Uri) -> Unit) {
        saveMime = mime
        onTarget = then
        launchName = suggested
    }

    LaunchedEffect(uri) {
        val s = withContext(Dispatchers.IO) { load(context, uri, name) }
        state = s
        textUndo = DocFiles.undoFile(context, uri).exists()
    }
    DisposableEffect(Unit) {
        onDispose {
            when (val s = stateRef) {
                is DocState.Pdf -> s.doc.close()
                is DocState.BigText -> s.big.close()
                is DocState.Text -> s.copyOf?.close()
                is DocState.Office -> s.controller.close()
                else -> {}
            }
        }
    }
    DisposableEffect(state) {
        val s = state
        if (s is DocState.Pdf) searcher = PdfTextSearch(context, s.doc.file)
        onDispose { searcher?.close(); searcher = null }
    }

    val office = (state as? DocState.Office)?.controller
    BackHandler(enabled = editing || office?.editing == true) {
        if (office != null) { if (office.isDirty) confirmDiscard = true else office.discard() }
        else {
            val t = state as? DocState.Text
            // unsaved text edits are not thrown away silently
            if (t != null && editText != t.text) confirmDiscard = true else editing = false
        }
    }

    fun savedMessage() { message = context.getString(I18nR.string.od_saved) }
    fun failedMessage() { message = context.getString(I18nR.string.od_save_failed) }

    fun openWith() {
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(curUri, context.contentResolver.getType(curUri) ?: "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { context.startActivity(Intent.createChooser(view, curName)) }
    }

    fun saveText(s: DocState.Text) {
        scope.launch(Dispatchers.IO) {
            val tmp = DocFiles.tempFile(context, curName)
            val ok = runCatching { tmp.writeText(editText) }.isSuccess
            if (!ok) { withContext(Dispatchers.Main) { failedMessage() }; return@launch }
            // the original is written when the sender allows it, otherwise it is saved as a new file; a copy of a cut file never replaces the original
            var result = WriteResult.NotWritable
            if (s.copyOf == null) {
                runCatching { s.source.copyTo(DocFiles.undoFile(context, curUri), overwrite = true) }
                result = DocFiles.copyToUri(context, tmp, curUri)
                if (result == WriteResult.Partial) DocFiles.copyToUri(context, s.source, curUri)
            }
            withContext(Dispatchers.Main) {
                if (result == WriteResult.Ok) {
                    savedMessage(); state = DocState.Text(editText, tmp, null); editing = false
                    textUndo = DocFiles.undoFile(context, curUri).exists()
                } else {
                    askTarget(curName, "text/plain") { target ->
                        scope.launch(Dispatchers.IO) {
                            val done = DocFiles.copyToUri(context, tmp, target) == WriteResult.Ok
                            withContext(Dispatchers.Main) { if (done) { savedMessage(); editing = false } else failedMessage() }
                        }
                    }
                }
            }
        }
    }

    fun saveOffice(c: OfficeController) {
        scope.launch {
            c.commit()
            when (val r = c.save()) {
                SaveOutcome.Saved -> savedMessage()
                is SaveOutcome.NeedSaveAs -> askTarget(c.name, DocFiles.mimeFor(c.name)) { target ->
                    scope.launch { if (c.finishSaveAs(r.file, target)) message = context.getString(I18nR.string.od_saved_copy) else failedMessage() }
                }
                SaveOutcome.Failed -> failedMessage()
            }
        }
    }

    fun saveOfficeCopy(c: OfficeController) {
        scope.launch {
            c.commit()
            val tmp = c.build()
            if (tmp == null) { failedMessage(); return@launch }
            askTarget(c.name, DocFiles.mimeFor(c.name)) { target ->
                scope.launch { if (c.finishSaveAs(tmp, target)) message = context.getString(I18nR.string.od_saved_copy) else failedMessage() }
            }
        }
    }

    fun convertLegacy(c: OfficeController) {
        scope.launch {
            val converted = c.convertLegacy()
            if (converted == null) { message = context.getString(I18nR.string.od_convert_failed); return@launch }
            val (tmp, newName) = converted
            askTarget(newName, DocFiles.mimeFor(newName)) { target ->
                scope.launch {
                    val done = withContext(Dispatchers.IO) { DocFiles.copyToUri(context, tmp, target) == WriteResult.Ok }
                    if (!done) { failedMessage(); return@launch }
                    val newDoc = withContext(Dispatchers.IO) { runCatching { OfficeDocs.open(tmp, newName) }.getOrNull() }
                    if (newDoc == null) { failedMessage(); return@launch }
                    c.close()
                    val nc = OfficeController(context, target, newName, newDoc)
                    nc.editing = true
                    curUri = target; curName = newName
                    state = DocState.Office(nc)
                    message = context.getString(I18nR.string.od_converted)
                }
            }
        }
    }

    val blur = rememberVirtualAppBlur()
    val bar = virtualAppElevated()
    Scaffold(
        containerColor = virtualAppSurface(),
        topBar = {
            TopAppBar(
                modifier = Modifier.virtualAppGlass(blur, bar),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                title = { TelosCyberHeading {  Text(curName, maxLines = 1)  } },
                navigationIcon = { IconButton(onClick = onClose) { Icon(painterResource(de.mm20.launcher2.base.R.drawable.arrow_back_24px), contentDescription = stringResource(I18nR.string.hc_back)) } },
                actions = {
                    val s = state
                    when {
                        s is DocState.Text && !editing -> TextButton(onClick = { editText = s.text; editing = true }) { Text(stringResource(I18nR.string.hc_edit)) }
                        s is DocState.Text && editing -> {
                            TextButton(onClick = { saveText(s) }) { Text(stringResource(I18nR.string.hc_save)) }
                            TextButton(onClick = { editing = false; if (s.copyOf != null) state = DocState.BigText(s.copyOf) }) { Text(stringResource(I18nR.string.hc_cancel)) }
                        }
                        s is DocState.Pdf -> TextButton(onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(context, PdfToolsActivity::class.java).setData(curUri).putExtra("name", curName)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                )
                            }
                        }) { Text(stringResource(I18nR.string.od_pdf_tools)) }
                        s is DocState.Office && s.controller.editing -> {
                            TextButton(onClick = { saveOffice(s.controller) }) { Text(stringResource(I18nR.string.hc_save)) }
                            TextButton(onClick = { if (s.controller.isDirty) confirmDiscard = true else s.controller.discard() }) { Text(stringResource(I18nR.string.hc_cancel)) }
                        }
                        s is DocState.Office && s.controller.canEdit -> TextButton(onClick = { s.controller.editing = true }) { Text(stringResource(I18nR.string.hc_edit)) }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (s is DocState.Pdf) {
                                DropdownMenuItem(text = { Text(stringResource(I18nR.string.od_search_pdf)) }, onClick = { menu = false; showSearch = true })
                                DropdownMenuItem(text = { Text(stringResource(I18nR.string.od_go_to_page)) }, onClick = { menu = false; showJump = true })
                                DropdownMenuItem(text = { Text(stringResource(I18nR.string.viewer_pdf_night)) }, onClick = { menu = false; pdfNight = !pdfNight })
                                DropdownMenuItem(text = { Text(stringResource(I18nR.string.viewer_pdf_highlight)) }, onClick = { menu = false; highlightMode = !highlightMode })
                            }
                            if (DocumentTypes.ext(curName) == "epub") {
                                DropdownMenuItem(text = { Text(stringResource(I18nR.string.viewer_epub_larger)) }, onClick = {
                                    menu = false
                                    epubStyle = epubStyle.copy(fontSp = (epubStyle.fontSp + 2).coerceAtMost(32))
                                    ViewerLibrary.saveEpubStyle(context, epubStyle)
                                })
                                DropdownMenuItem(text = { Text(stringResource(I18nR.string.viewer_epub_smaller)) }, onClick = {
                                    menu = false
                                    epubStyle = epubStyle.copy(fontSp = (epubStyle.fontSp - 2).coerceAtLeast(12))
                                    ViewerLibrary.saveEpubStyle(context, epubStyle)
                                })
                                DropdownMenuItem(text = { Text(stringResource(I18nR.string.viewer_epub_serif)) }, onClick = {
                                    menu = false
                                    epubStyle = epubStyle.copy(serif = !epubStyle.serif)
                                    ViewerLibrary.saveEpubStyle(context, epubStyle)
                                })
                                DropdownMenuItem(text = { Text(stringResource(I18nR.string.viewer_epub_night)) }, onClick = {
                                    menu = false
                                    epubStyle = epubStyle.copy(night = !epubStyle.night)
                                    ViewerLibrary.saveEpubStyle(context, epubStyle)
                                })
                            }
                            if (s is DocState.BigText) {
                                DropdownMenuItem(text = { Text(stringResource(I18nR.string.od_edit_copy)) }, onClick = {
                                    menu = false
                                    scope.launch {
                                        val text = withContext(Dispatchers.IO) { s.big.head(TEXT_EDIT_LIMIT) }
                                        editText = text; editing = true
                                        state = DocState.Text(text, s.big.file, s.big)
                                    }
                                })
                            }
                            if (s is DocState.Text && !editing && textUndo) {
                                DropdownMenuItem(text = { Text(stringResource(I18nR.string.od_undo_save)) }, onClick = {
                                    menu = false
                                    scope.launch {
                                        val ok = withContext(Dispatchers.IO) {
                                            val bak = DocFiles.undoFile(context, curUri)
                                            val done = bak.exists() && DocFiles.copyToUri(context, bak, curUri) == WriteResult.Ok
                                            if (done) bak.delete()
                                            done
                                        }
                                        if (ok) {
                                            textUndo = false
                                            state = withContext(Dispatchers.IO) { load(context, curUri, curName) }
                                            message = context.getString(I18nR.string.od_undo_done)
                                        } else message = context.getString(I18nR.string.od_undo_failed)
                                    }
                                })
                            }
                            if (s is DocState.Office) {
                                if (s.controller.editing) {
                                    DropdownMenuItem(text = { Text(stringResource(I18nR.string.od_save_copy)) }, onClick = { menu = false; saveOfficeCopy(s.controller) })
                                } else if (s.controller.hasUndo) {
                                    DropdownMenuItem(text = { Text(stringResource(I18nR.string.od_undo_save)) }, onClick = {
                                        menu = false
                                        scope.launch {
                                            message = context.getString(if (s.controller.undoLastSave()) I18nR.string.od_undo_done else I18nR.string.od_undo_failed)
                                        }
                                    })
                                }
                            }
                            if (!editing && office?.editing != true) {
                                DropdownMenuItem(text = { Text(stringResource(I18nR.string.hc_open_with)) }, onClick = { menu = false; openWith() })
                                de.mm20.launcher2.ui.common.share.ShareMenuItem(onClick = {
                                    menu = false
                                    val shareUri = curUri
                                    val shareName = curName
                                    scope.launch {
                                        val ok = withContext(Dispatchers.IO) {
                                            de.mm20.launcher2.ui.common.share.ShareActions.shareFile(context, shareUri, context.contentResolver.getType(shareUri), shareName)
                                        }
                                        if (!ok) message = context.getString(I18nR.string.au9_share_failed)
                                    }
                                })
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                DocState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is DocState.Failed -> Text(s.message, Modifier.align(Alignment.Center).padding(32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                is DocState.Pdf -> PdfView(s.doc, pdfList, night = pdfNight, highlight = highlightMode, fileKey = curUri.toString())
                is DocState.Comic -> ComicView(s.pages)
                is DocState.Graphic -> GraphicView(s)
                is DocState.Blocks -> BlocksView(s.items, if (DocumentTypes.ext(curName) == "epub") epubStyle else null)
                is DocState.BigText -> BigTextView(s.big)
                is DocState.Office -> OfficeView(s.controller, onConvert = { convertLegacy(s.controller) })
                is DocState.Text -> if (editing) {
                    Column(Modifier.fillMaxSize()) {
                        if (s.copyOf != null) Text(stringResource(I18nR.string.od_copy_hint), Modifier.padding(12.dp, 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        OutlinedTextField(
                            value = editText, onValueChange = { editText = it }, modifier = Modifier.fillMaxSize().padding(8.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        )
                    }
                } else TextView(s.text)
            }
            message?.let { m ->
                Surface(Modifier.align(Alignment.BottomCenter).padding(16.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.inverseSurface) {
                    Text(m, Modifier.padding(16.dp, 10.dp), color = MaterialTheme.colorScheme.inverseOnSurface)
                }
                LaunchedEffect(m) { kotlinx.coroutines.delay(2500); message = null }
            }
        }
    }

    if (confirmDiscard && (office != null || editing)) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.od_discard_title))  } },
            text = { Text(stringResource(I18nR.string.od_discard_text)) },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; if (office != null) office.discard() else editing = false }) { Text(stringResource(I18nR.string.od_discard)) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(I18nR.string.od_keep_editing)) } },
        )
    }
    val pdf = (state as? DocState.Pdf)?.doc
    if (showSearch && pdf != null && searcher != null) {
        PdfSearchDialog(
            searcher!!,
            onJump = { page -> showSearch = false; scope.launch { pdfList.animateScrollToItem(page) } },
            onHighlightAll = { q ->
                showSearch = false
                val pdfFile = pdf.file
                val key = curUri.toString()
                scope.launch {
                    val added = withContext(Dispatchers.Default) {
                        val n = pdf.count
                        val extra = ArrayList<ViewerLibrary.Mark>()
                        for (i in 0 until n) {
                            val glyphs = PdfPageText.glyphs(pdfFile, i).glyphs
                            for (group in PdfPageText.matches(glyphs, q)) {
                                val u = PdfPageText.union(group) ?: continue
                                extra += ViewerLibrary.Mark(i, u[0], u[1], u[2], u[3], q)
                            }
                        }
                        extra.forEach { ViewerLibrary.addMark(context, key, it) }
                        extra.size
                    }
                    message = context.getString(I18nR.string.viewer_highlight_added, added)
                }
            },
            onDismiss = { showSearch = false },
        )
    }
    if (showJump && pdf != null) {
        var input by remember { mutableStateOf("") }
        val go = {
            input.trim().toIntOrNull()?.let { p -> showJump = false; scope.launch { pdfList.animateScrollToItem((p - 1).coerceIn(0, pdf.count - 1)) } }
            Unit
        }
        AlertDialog(
            onDismissRequest = { showJump = false },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.od_go_to_page))  } },
            text = {
                OutlinedTextField(
                    input, { input = it.filter { c -> c.isDigit() }.take(6) }, singleLine = true,
                    label = { Text(stringResource(I18nR.string.od_page_range, pdf.count)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { go() }),
                )
            },
            confirmButton = { TextButton(onClick = { go() }) { Text(stringResource(I18nR.string.od_go)) } },
            dismissButton = { TextButton(onClick = { showJump = false }) { Text(stringResource(I18nR.string.hc_cancel)) } },
        )
    }
}

@Composable
private fun PdfSearchDialog(
    searcher: PdfTextSearch,
    onJump: (Int) -> Unit,
    onHighlightAll: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PdfHit>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var noText by remember { mutableStateOf(false) }
    fun run() {
        if (busy || query.isBlank()) return
        busy = true; progress = 0
        scope.launch {
            val found = withContext(Dispatchers.Default) { runCatching { searcher.search(query) { progress = it } }.getOrDefault(emptyList()) }
            results = found; noText = !searcher.hasText; busy = false
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.od_search_pdf))  } },
        text = {
            Column {
                de.mm20.launcher2.ui.component.TelosSearchBar(
                    query, { query = it }, stringResource(I18nR.string.od_search_hint),
                    modifier = Modifier.padding(horizontal = 0.dp),
                    autoFocus = true,
                    onSearch = { run() },
                )
                val r = results
                when {
                    busy -> Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.width(20.dp).aspectRatio(1f), strokeWidth = 2.dp)
                        Text(stringResource(I18nR.string.od_searching, progress), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodySmall)
                    }
                    r != null && noText -> Text(stringResource(I18nR.string.od_no_text_layer), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall)
                    r != null && r.isEmpty() -> Text(stringResource(I18nR.string.od_no_results), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall)
                    r != null -> {
                        Text(stringResource(I18nR.string.od_results_count, r.size), Modifier.padding(top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.labelMedium)
                        LazyColumn(Modifier.heightIn(max = 320.dp)) {
                            items(r.size) { i ->
                                Column(Modifier.fillMaxWidth().clickable { onJump(r[i].page) }.padding(vertical = 6.dp)) {
                                    Text(stringResource(I18nR.string.hc_page_number, r[i].page + 1), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    Text(r[i].snippet, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { run() }) { Text(stringResource(I18nR.string.hc_search)) }
                if (!query.isBlank()) {
                    TextButton(onClick = { onHighlightAll(query) }) { Text(stringResource(I18nR.string.viewer_highlight_all)) }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.hc_close)) } },
    )
}

/** Copies the document into the cache (a PDF needs a real file) and reads it. */
private fun load(context: Context, uri: Uri, name: String): DocState = runCatching {
    val dir = File(context.cacheDir, "doc_view").apply { mkdirs() }
    dir.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 2 * 60 * 60_000 }?.forEach { it.delete() }
    DocFiles.sweep(context)
    val file = File(dir, "${System.nanoTime()}_" + name.replace('/', '_'))
    context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
    when {
        DocumentTypes.isPdf(name) || (DocumentTypes.ext(name) == "ai" && GraphicPreviews.isAiPdf(file)) -> DocState.Pdf(PdfDoc(file))
        DocumentTypes.isComic(name) -> {
            val pagesDir = File(dir, "comic_${file.name}")
            val pages = ComicArchive.extract(file, name, pagesDir)
            if (pages.isEmpty()) DocState.Failed(context.getString(I18nR.string.viewer_comic_empty))
            else DocState.Comic(pages)
        }
        DocumentTypes.isGraphic(name) -> {
            val ext = DocumentTypes.ext(name)
            when (ext) {
                "svg" -> DocState.Graphic(null, file, true, context.getString(I18nR.string.viewer_preview_svg))
                "psd" -> DocState.Graphic(
                    GraphicPreviews.psdThumbnail(file),
                    file,
                    false,
                    context.getString(I18nR.string.viewer_preview_psd),
                )
                "cdr" -> DocState.Graphic(
                    GraphicPreviews.embeddedRaster(file),
                    file,
                    false,
                    context.getString(I18nR.string.viewer_preview_cdr),
                )
                else -> DocState.Graphic(
                    GraphicPreviews.embeddedRaster(file),
                    file,
                    false,
                    context.getString(I18nR.string.viewer_preview_ai),
                )
            }.let { g ->
                if (!g.svg && g.bitmap == null) DocState.Failed(context.getString(I18nR.string.viewer_preview_none))
                else g
            }
        }
        DocumentTypes.isOfficeModel(name) -> {
            val doc = runCatching { OfficeDocs.open(file, name) }.getOrNull()
            if (doc != null) DocState.Office(OfficeController(context, uri, name, doc))
            else if (DocumentTypes.isLegacyOffice(name)) DocState.Failed(context.getString(I18nR.string.od_legacy_failed))
            else {
                // the structure could not be read: fall back to the plain text and tables
                val blocks = DocumentReaders.read(file, name, DocLabels({ context.getString(I18nR.string.od_sheet_n, it) }, { context.getString(I18nR.string.au_office_slide_n, it) }))
                if (blocks.isEmpty()) DocState.Failed(context.getString(I18nR.string.od_nothing_to_show)) else DocState.Blocks(flatten(blocks))
            }
        }
        DocumentTypes.isOffice(name) -> {
            val blocks = DocumentReaders.read(file, name, DocLabels({ context.getString(I18nR.string.od_sheet_n, it) }, { context.getString(I18nR.string.au_office_slide_n, it) }))
            if (blocks.isEmpty()) DocState.Failed(context.getString(I18nR.string.od_nothing_to_show))
            else DocState.Blocks(flatten(blocks))
        }
        else -> {
            if (file.length() <= TEXT_EDIT_LIMIT) DocState.Text(DocumentReaders.decodeText(file.readBytes()), file, null)
            else DocState.BigText(BigTextFile(file).also { it.index() })
        }
    }
}.getOrElse {
    DocState.Failed(
        if (it is EncryptedDocumentException) context.getString(I18nR.string.au_office_encrypted)
        else if (it is ArchiveRarUnavailableException) context.getString(I18nR.string.viewer_cbr_fdroid)
        else if (it is de.mm20.launcher2.ui.files.ArchiveRarEncryptedException) context.getString(I18nR.string.viewer_cbr_encrypted)
        else if (it is SecurityException) context.getString(I18nR.string.od_pdf_protected)
        else context.getString(I18nR.string.od_cannot_open, it.message ?: it.javaClass.simpleName),
    )
}

private fun flatten(blocks: List<DocBlock>): List<ViewItem> = buildList {
    for (b in blocks) when (b) {
        is DocBlock.Table -> {
            val scroll = androidx.compose.foundation.ScrollState(0)
            b.rows.forEachIndexed { i, row -> add(ViewItem.Row(row, scroll, i == 0)) }
        }
        else -> add(ViewItem.Block(b))
    }
}

// ---------------------------------------------------------------- views

@Composable
private fun PdfView(
    doc: PdfDoc,
    listState: androidx.compose.foundation.lazy.LazyListState,
    night: Boolean,
    highlight: Boolean,
    fileKey: String,
) {
    val density = LocalDensity.current
    val screenWidth = with(density) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    var zoom by remember { mutableStateOf(1f) }
    val widthPx = (screenWidth * zoom).toInt()
    val hScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
        Box(
            Modifier.fillMaxSize().horizontalScroll(hScroll).pointerInput(Unit) {
                // only a pinch is taken here, one finger still scrolls
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size >= 2) {
                            val old = zoom
                            val new = (old * event.calculateZoom()).coerceIn(1f, 4f)
                            val f = new / old
                            if (f != 1f) {
                                // keep the point between the fingers where it is: x is in content coordinates here
                                val c = event.calculateCentroid(useCurrent = true)
                                val viewportX = c.x - hScroll.value
                                val targetX = ((c.x * f) - viewportX).toInt().coerceAtLeast(0)
                                val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { c.y >= it.offset && c.y < it.offset + it.size }
                                if (item != null) {
                                    val frac = (c.y - item.offset) / item.size.toFloat()
                                    listState.requestScrollToItem(item.index, (item.size * f * frac - c.y).toInt())
                                }
                                zoom = new
                                scope.launch {
                                    androidx.compose.runtime.withFrameNanos { }
                                    hScroll.scrollTo(targetX)
                                }
                            }
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        ) {
            LazyColumn(Modifier.width(with(density) { widthPx.toDp() }), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(doc.count) { index -> PdfPage(doc, index, widthPx, night, highlight, fileKey) }
            }
        }
        Surface(Modifier.align(Alignment.BottomEnd).padding(16.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f)) {
            Text("${listState.firstVisibleItemIndex + 1} / ${doc.count}", Modifier.padding(12.dp, 6.dp), color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun PdfPage(
    doc: PdfDoc,
    index: Int,
    widthPx: Int,
    night: Boolean,
    highlight: Boolean,
    fileKey: String,
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, index, widthPx) {
        value = withContext(Dispatchers.Default) { runCatching { doc.render(index, widthPx) }.getOrNull() }
    }
    val aspect = remember(index) { runCatching { doc.aspect(index) }.getOrDefault(0.7f) }
    var marks by remember(fileKey, index) { mutableStateOf(ViewerLibrary.marks(context, fileKey).filter { it.page == index }) }
    val density = LocalDensity.current
    val invert = remember {
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    }
    val shown = bitmap
    val glyphs by produceState(emptyList<de.mm20.launcher2.ui.media.docs.office.PdfGlyph>(), doc.file, index) {
        value = withContext(Dispatchers.Default) { PdfPageText.glyphs(doc.file, index).glyphs }
    }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(aspect)) {
        val boxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val boxH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        if (shown != null) {
            Image(
                shown.asImageBitmap(),
                contentDescription = stringResource(I18nR.string.hc_page_number, index + 1),
                modifier = Modifier.fillMaxSize().then(
                    if (highlight) Modifier.pointerInput(glyphs, fileKey, index) {
                        detectDragGestures(
                            onDragStart = { p -> dragStart = p; dragEnd = p },
                            onDrag = { change, _ ->
                                change.consume()
                                dragEnd = change.position
                            },
                            onDragEnd = {
                                val a = dragStart
                                val b = dragEnd
                                dragStart = null
                                dragEnd = null
                                if (a == null || b == null) return@detectDragGestures
                                val l = (minOf(a.x, b.x) / boxW).coerceIn(0f, 1f)
                                val r = (maxOf(a.x, b.x) / boxW).coerceIn(0f, 1f)
                                val t = (minOf(a.y, b.y) / boxH).coerceIn(0f, 1f)
                                val bot = (maxOf(a.y, b.y) / boxH).coerceIn(0f, 1f)
                                if (r - l < 0.01f && bot - t < 0.01f) return@detectDragGestures
                                val hit = PdfPageText.inRect(glyphs, l, t, r, bot)
                                val u = PdfPageText.union(hit)
                                val mark = if (u != null) ViewerLibrary.Mark(
                                    index, u[0], u[1], u[2], u[3], hit.joinToString("") { it.text }.trim(),
                                ) else ViewerLibrary.Mark(index, l, t, r, bot, "")
                                ViewerLibrary.addMark(context, fileKey, mark)
                                marks = marks + mark
                            },
                            onDragCancel = { dragStart = null; dragEnd = null },
                        )
                    } else Modifier,
                ),
                contentScale = ContentScale.FillWidth,
                colorFilter = if (night) invert else null,
            )
        } else {
            Box(Modifier.fillMaxSize().background(if (night) Color.Black else Color.White), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        marks.forEach { m ->
            Box(
                Modifier
                    .offset {
                        IntOffset((m.left * boxW).toInt(), (m.top * boxH).toInt())
                    }
                    .size(
                        width = ((m.right - m.left) * boxW / density.density).dp,
                        height = ((m.bottom - m.top) * boxH / density.density).dp,
                    )
                    .background(Color(0x66FFEB3B))
                    .clickable {
                        ViewerLibrary.removeMark(context, fileKey, m)
                        marks = marks.filter { it != m }
                    },
            )
        }
        val ds = dragStart
        val de = dragEnd
        if (ds != null && de != null) {
            val l = minOf(ds.x, de.x)
            val t = minOf(ds.y, de.y)
            Box(
                Modifier
                    .offset { IntOffset(l.toInt(), t.toInt()) }
                    .size(
                        width = (kotlin.math.abs(de.x - ds.x) / density.density).dp,
                        height = (kotlin.math.abs(de.y - ds.y) / density.density).dp,
                    )
                    .background(Color(0x44FFEB3B)),
            )
        }
        marks.lastOrNull { it.text.isNotBlank() }?.let { last ->
            Surface(
                Modifier.align(Alignment.BottomStart).padding(8.dp),
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.9f),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    last.text.take(180),
                    Modifier.padding(8.dp, 4.dp),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 3,
                )
            }
        }
    }
}

@Composable
private fun ComicView(pages: List<File>) {
    val pager = rememberPagerState { pages.size }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { i ->
            AsyncImage(
                model = pages[i],
                contentDescription = stringResource(I18nR.string.hc_page_number, i + 1),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        Text(
            "${pager.currentPage + 1} / ${pages.size}",
            Modifier.align(Alignment.CenterHorizontally).padding(12.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun GraphicView(state: DocState.Graphic) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            state.caption,
            Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.svg) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(state.file).decoderFactory(SvgDecoder.Factory()).build(),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            val bmp = state.bitmap
            if (bmp != null) {
                Image(
                    bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

@Composable
private fun TextView(text: String) {
    val lines = remember(text) { text.lines() }
    SelectionContainer {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
            items(lines.size) { i ->
                Text(lines[i].ifEmpty { " " }, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}

/** A text file that is too big to edit: lines are read from the file when they scroll into view. */
@Composable
private fun BigTextView(big: BigTextFile) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
        item {
            Text(stringResource(I18nR.string.od_text_too_big), Modifier.padding(bottom = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        items(big.lines) { i ->
            Text(big.line(i).ifEmpty { " " }, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 6)
        }
    }
}

@Composable
private fun BlocksView(items: List<ViewItem>, epub: ViewerLibrary.EpubStyle? = null) {
    val paraStyle = if (epub != null) {
        MaterialTheme.typography.bodyLarge.copy(
            fontSize = epub.fontSp.sp,
            lineHeight = (epub.fontSp * 1.5f).sp,
            fontFamily = if (epub.serif) FontFamily.Serif else FontFamily.SansSerif,
            color = if (epub.night) Color(0xFFE8E4D8) else MaterialTheme.colorScheme.onSurface,
        )
    } else MaterialTheme.typography.bodyLarge
    val pad = (epub?.marginDp ?: 16).dp
    val bg = if (epub?.night == true) Color(0xFF1C1914) else Color.Transparent
    SelectionContainer {
        LazyColumn(
            Modifier.fillMaxSize().background(bg),
            contentPadding = PaddingValues(pad),
        ) {
            items(items) { item ->
                when (item) {
                    is ViewItem.Block -> when (val b = item.block) {
                        is DocBlock.Title -> Text(b.text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp), color = paraStyle.color)
                        is DocBlock.Heading -> Text(
                            b.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                            style = when (b.level) { 1 -> MaterialTheme.typography.headlineSmall; 2 -> MaterialTheme.typography.titleLarge; else -> MaterialTheme.typography.titleMedium },
                            color = paraStyle.color,
                        )
                        is DocBlock.Paragraph -> Text(b.text, style = paraStyle, modifier = Modifier.padding(vertical = 4.dp))
                        is DocBlock.Table -> {}
                    }
                    is ViewItem.Row -> Row(Modifier.horizontalScroll(item.scroll)) {
                        item.cells.forEach { cell ->
                            Box(
                                Modifier.width(140.dp).border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                    .background(if (item.header) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent).padding(8.dp),
                            ) { Text(cell, style = MaterialTheme.typography.bodySmall, fontWeight = if (item.header) FontWeight.SemiBold else FontWeight.Normal, maxLines = 6) }
                        }
                    }
                }
            }
        }
    }
}
