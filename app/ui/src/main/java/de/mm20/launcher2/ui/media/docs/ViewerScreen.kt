package de.mm20.launcher2.ui.media.docs

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.common.share.ShareActions
import de.mm20.launcher2.ui.component.TelosCyber
import de.mm20.launcher2.ui.component.TelosCyberAppBarTitle
import de.mm20.launcher2.ui.component.TelosCyberSectionLabel
import de.mm20.launcher2.ui.component.TelosTechPill
import de.mm20.launcher2.ui.component.telosTechSurface
import de.mm20.launcher2.ui.component.virtualAppGlass
import de.mm20.launcher2.ui.component.virtualAppPopup
import de.mm20.launcher2.ui.theme.transparency.transparency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Home screen of Telos Viewer: a library of the documents on the phone, by category. */
@Serializable
data object ViewerRoute : NavKey

/** Categories of the library (the chips). [exts] are the file extensions that belong to it. */
internal enum class DocCategory(val labelRes: Int, val exts: Set<String>) {
    All(I18nR.string.au20_viewer_cat_all, emptySet()),
    Images(I18nR.string.file_search_type_images, setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "avif", "bmp")),
    Videos(I18nR.string.file_search_type_videos, setOf("mp4", "mkv", "webm", "mov", "3gp", "avi", "m4v")),
    Documents(I18nR.string.au20_viewer_cat_documents, setOf("doc", "docx", "odt", "rtf", "txt", "md", "markdown", "gdoc")),
    Pdf(I18nR.string.au20_viewer_cat_pdf, setOf("pdf")),
    Sheets(I18nR.string.au20_viewer_cat_sheets, setOf("xls", "xlsx", "ods", "csv", "tsv", "gsheet")),
    Slides(I18nR.string.au20_viewer_cat_slides, setOf("ppt", "pptx", "odp", "gslides")),
    Ebooks(I18nR.string.au20_viewer_cat_ebooks, setOf("epub")),
    Comics(I18nR.string.au20_viewer_cat_comics, setOf("cbz", "cbr", "cbt", "cb7")),
    Graphics(I18nR.string.au20_viewer_cat_graphics, setOf("svg", "psd", "ai", "cdr")),
    Other(
        I18nR.string.au20_viewer_cat_other,
        setOf("json", "xml", "html", "htm", "log", "yml", "yaml", "ini", "conf", "srt", "vtt", "gpx", "kml", "tex", "gdraw", "gform", "gsite"),
    );

    companion object {
        fun of(ext: String): DocCategory? = values().firstOrNull { it != All && ext in it.exts }
        val scanned: List<String> = values().flatMap { it.exts.toList() }
    }
}

private enum class DocSort(val labelRes: Int) {
    Date(I18nR.string.au20_viewer_sort_date),
    Name(I18nR.string.au20_viewer_sort_name),
    Size(I18nR.string.au20_viewer_sort_size),
}

/** A document found by the scan */
internal data class DocFile(
    val uri: Uri,
    val name: String,
    val folder: String,
    val size: Long,
    val modified: Long,
) {
    val ext: String get() = DocumentTypes.ext(name)
}

/** What a row or a recent card acts on */
private data class DocTarget(
    val uri: Uri,
    val name: String,
    val folder: String,
    val size: Long,
    val date: Long,
    val recent: Boolean,
)

private fun DocFile.target() = DocTarget(uri, name, folder, size, modified, false)

/** The files opened from the library, newest first, capped at [MAX]. Kept in the app's private preferences. */
internal object ViewerRecents {
    data class Entry(val uri: String, val name: String, val size: Long, val opened: Long)

    private const val PREFS = "telos_viewer"
    private const val KEY = "recent"
    const val MAX = 50

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun load(context: Context): List<Entry> = runCatching {
        val arr = JSONArray(prefs(context).getString(KEY, "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Entry(o.getString("u"), o.optString("n"), o.optLong("s"), o.optLong("t"))
        }
    }.getOrDefault(emptyList())

    private fun save(context: Context, list: List<Entry>) {
        val arr = JSONArray()
        list.take(MAX).forEach {
            arr.put(JSONObject().put("u", it.uri).put("n", it.name).put("s", it.size).put("t", it.opened))
        }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }

    @Synchronized
    fun add(context: Context, uri: Uri, name: String, size: Long) {
        val s = uri.toString()
        save(context, listOf(Entry(s, name, size, System.currentTimeMillis())) + load(context).filter { it.uri != s })
    }

    @Synchronized
    fun remove(context: Context, uri: String) = save(context, load(context).filter { it.uri != uri })

    @Synchronized
    fun clear(context: Context) = save(context, emptyList())
}

internal class ViewerViewModel : ViewModel() {
    private val _files = MutableStateFlow(cache)
    /** null while the first scan runs */
    val files: StateFlow<List<DocFile>?> = _files

    private val _recents = MutableStateFlow<List<ViewerRecents.Entry>>(emptyList())
    val recents: StateFlow<List<ViewerRecents.Entry>> = _recents

    private var scanning = false

    fun loadRecents(context: Context) {
        val app = context.applicationContext
        viewModelScope.launch { _recents.value = withContext(Dispatchers.IO) { ViewerRecents.load(app) } }
    }

    fun addRecent(context: Context, uri: Uri, name: String, size: Long) {
        val app = context.applicationContext
        viewModelScope.launch {
            _recents.value = withContext(Dispatchers.IO) { ViewerRecents.add(app, uri, name, size); ViewerRecents.load(app) }
        }
    }

    fun removeRecent(context: Context, uri: String) {
        val app = context.applicationContext
        viewModelScope.launch {
            _recents.value = withContext(Dispatchers.IO) { ViewerRecents.remove(app, uri); ViewerRecents.load(app) }
        }
    }

    fun clearRecents(context: Context) {
        val app = context.applicationContext
        viewModelScope.launch {
            _recents.value = withContext(Dispatchers.IO) { ViewerRecents.clear(app); emptyList() }
        }
    }

    fun scan(context: Context) {
        if (scanning) return
        scanning = true
        val app = context.applicationContext
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { runCatching { query(app) }.getOrDefault(emptyList()) }
            cache = list
            _files.value = list
            scanning = false
        }
    }

    @Suppress("DEPRECATION")
    private fun query(context: Context): List<DocFile> {
        val exts = DocCategory.scanned
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.DATA,
        )
        val selection = exts.joinToString(" OR ") { "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?" }
        val args = exts.map { "%.$it" }.toTypedArray()
        val out = ArrayList<DocFile>()
        val seen = HashSet<String>()
        context.contentResolver.query(
            uri, projection, selection, args, "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        )?.use { c ->
            while (c.moveToNext() && out.size < MAX_FILES) {
                val name = c.getString(1) ?: continue
                val path = c.getString(4).orEmpty()
                val size = c.getLong(2)
                if (size <= 0 || skip(name, path)) continue
                if (path.isNotEmpty() && !seen.add(path)) continue
                if (DocumentTypes.ext(name) !in exts) continue
                out += DocFile(
                    uri = android.content.ContentUris.withAppendedId(uri, c.getLong(0)),
                    name = name,
                    folder = if (path.isNotEmpty()) File(path).parentFile?.name.orEmpty() else "",
                    size = size,
                    modified = c.getLong(3) * 1000,
                )
            }
        }
        return out
    }

    /** Hidden files and folders, trash, other apps' private storage */
    private fun skip(name: String, path: String): Boolean {
        if (name.startsWith(".") || name.contains(".trashed-") || name.contains(".pending-")) return true
        if (path.contains("/Android/data/") || path.contains("/Android/obb/")) return true
        return path.split('/').any { it.startsWith(".") }
    }

    private companion object {
        const val MAX_FILES = 20000

        /** the last result, so that opening the screen again shows the list at once while it refreshes */
        @Volatile
        var cache: List<DocFile>? = null
    }
}

private fun hasFilesAccess(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
    else ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

private fun openDocument(context: Context, uri: Uri, name: String) {
    val ext = DocumentTypes.ext(name)
    val mediaActivity = when (DocCategory.of(ext)) {
        DocCategory.Images -> de.mm20.launcher2.ui.media.photos.PhotoViewerActivity::class.java
        DocCategory.Videos -> de.mm20.launcher2.ui.media.video.VideoPlayerActivity::class.java
        else -> DocumentViewerActivity::class.java
    }
    runCatching {
        context.startActivity(
            Intent(context, mediaActivity).setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }.onFailure { Toast.makeText(context, I18nR.string.au20_viewer_open_failed, Toast.LENGTH_SHORT).show() }
}

private fun openWith(context: Context, uri: Uri, name: String) {
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, ShareActions.mimeOf(name))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(intent, null)) }
        .onFailure { Toast.makeText(context, I18nR.string.au20_viewer_open_failed, Toast.LENGTH_SHORT).show() }
}

/** Name and size of a picked document (blocking, call off the main thread) */
private fun describe(context: Context, uri: Uri): Pair<String, Long> {
    var name: String? = null
    var size = 0L
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst()) {
                name = it.getString(0)
                if (!it.isNull(1)) size = it.getLong(1)
            }
        }
    }
    return (name ?: uri.lastPathSegment?.substringAfterLast('/') ?: context.getString(I18nR.string.od_default_name)) to size
}

private fun tileColor(ext: String): Color = when (ext) {
    "pdf" -> Color(0xFFD32F2F)
    "doc", "docx", "odt", "rtf", "gdoc" -> Color(0xFF1565C0)
    "xls", "xlsx", "ods", "csv", "tsv", "gsheet" -> Color(0xFF2E7D32)
    "ppt", "pptx", "odp", "gslides" -> Color(0xFFC2410C)
    "epub" -> Color(0xFF6A1B9A)
    else -> Color(0xFF546E7A)
}

@Composable
private fun TypeTile(ext: String, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size / 3.4f)).background(tileColor(ext)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (ext == "markdown") "MD" else ext.uppercase().take(4),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
        )
    }
}

private fun dateText(context: Context, millis: Long): String =
    if (millis <= 0) "" else DateUtils.formatDateTime(
        context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_YEAR
    )

/**
 * Telos Viewer: the pictures on the phone (gallery) and the documents (library), as two tabs.
 * The last tab used is remembered.
 */
@Composable
fun ViewerScreen() {
    de.mm20.launcher2.ui.media.VirtualAppGuardEffect("telos_viewer_app://viewer")
    val context = LocalContext.current
    var tab by rememberSaveable {
        mutableIntStateOf(
            runCatching { context.getSharedPreferences("telos_viewer", Context.MODE_PRIVATE).getInt("tab", 0) }.getOrDefault(0)
        )
    }
    fun select(t: Int) {
        tab = t
        runCatching { context.getSharedPreferences("telos_viewer", Context.MODE_PRIVATE).edit().putInt("tab", t).apply() }
    }
    val blur = de.mm20.launcher2.ui.component.rememberVirtualAppBlur()
    val bar = de.mm20.launcher2.ui.component.virtualAppElevated()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = MaterialTheme.transparency.background)).statusBarsPadding()) {
        Column(Modifier.fillMaxWidth().virtualAppGlass(blur, bar).padding(horizontal = 12.dp, vertical = 8.dp)) {
            TelosCyberAppBarTitle(
                title = stringResource(I18nR.string.au20_viewer_title),
                status = stringResource(if (tab == 0) I18nR.string.hc_photos else I18nR.string.au20_viewer_cat_documents),
                statusColor = if (tab == 0) TelosCyber.NeonCyan else TelosCyber.NeonMagenta,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TelosTechPill(
                    text = stringResource(I18nR.string.hc_photos),
                    isSelected = tab == 0,
                    onClick = { select(0) },
                    accentColor = TelosCyber.NeonCyan,
                )
                TelosTechPill(
                    text = stringResource(I18nR.string.au20_viewer_cat_documents),
                    isSelected = tab == 1,
                    onClick = { select(1) },
                    accentColor = TelosCyber.NeonMagenta,
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (tab == 0) de.mm20.launcher2.ui.media.photos.PhotosScreen(embedded = true) else DocumentsScreen(embedded = true)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun DocumentsScreen(embedded: Boolean = false) {
    val vm: ViewerViewModel = viewModel()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val files by vm.files.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()

    var hasAccess by remember { mutableStateOf(hasFilesAccess(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasAccess = hasFilesAccess(context)
        vm.loadRecents(context)
        if (hasAccess) vm.scan(context)
    }
    val legacyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasAccess = hasFilesAccess(context)
        if (hasAccess) vm.scan(context)
    }

    fun open(uri: Uri, name: String, size: Long) {
        vm.addRecent(context, uri, name, size)
        openDocument(context, uri, name)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            scope.launch {
                val (name, size) = withContext(Dispatchers.IO) { describe(context, uri) }
                open(uri, name, size)
            }
        }
    }

    var category by rememberSaveable { mutableStateOf(DocCategory.All) }
    var sort by rememberSaveable { mutableStateOf(DocSort.Date) }
    var query by rememberSaveable { mutableStateOf("") }
    var info by remember { mutableStateOf<DocTarget?>(null) }
    var pendingDelete by remember { mutableStateOf<DocTarget?>(null) }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var deleteSelected by remember { mutableStateOf(false) }
    if (deleteSelected && selected.isNotEmpty()) de.mm20.launcher2.ui.trash.UriDeleteDialog(
        selected.map(Uri::parse),
        { selected.forEach { vm.removeRecent(context, it) }; selected = emptyList(); vm.scan(context) },
        { deleteSelected = false },
    )
    var showTrash by remember { mutableStateOf(false) }
    if (showTrash) de.mm20.launcher2.ui.trash.TrashDialog({ showTrash = false }, { vm.scan(context) })
    pendingDelete?.let { target -> de.mm20.launcher2.ui.trash.UriDeleteDialog(target.uri,
        { vm.removeRecent(context, target.uri.toString()); vm.scan(context) }, { pendingDelete = null }) }


    val shown = remember(files, category, sort, query) {
        val base = files.orEmpty().filter { category == DocCategory.All || DocCategory.of(it.ext) == category }
        val filtered = de.mm20.launcher2.comms.search.TelosSearch.filter(base, query) { listOf(it.name, it.folder) }
        if (query.isNotBlank()) filtered else when (sort) {
            DocSort.Date -> filtered.sortedByDescending { it.modified }
            DocSort.Name -> filtered.sortedBy { it.name.lowercase() }
            DocSort.Size -> filtered.sortedByDescending { it.size }
        }
    }
    val showRecents = category == DocCategory.All && query.isBlank() && recents.isNotEmpty()
    val loading = hasAccess && files == null

    fun share(t: DocTarget) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { ShareActions.shareFile(context, t.uri, ShareActions.mimeOf(t.name), t.name) }
            if (!ok) Toast.makeText(context, I18nR.string.au20_viewer_share_failed, Toast.LENGTH_SHORT).show()
        }
    }

    fun shareSelected() {
        val uris = selected.map(Uri::parse)
        scope.launch {
            val ok = withContext(Dispatchers.IO) { ShareActions.shareUris(context, uris) }
            if (!ok) Toast.makeText(context, I18nR.string.au20_viewer_share_failed, Toast.LENGTH_SHORT).show()
        }
    }

    val surface = MaterialTheme.colorScheme.surface
    val primary = MaterialTheme.colorScheme.primary
    Box(
        Modifier.fillMaxSize().background(surface)
            .background(Brush.verticalGradient(listOf(primary.copy(alpha = 0.14f), surface)))
    ) {
        Column(Modifier.fillMaxSize().then(if (embedded) Modifier else Modifier.statusBarsPadding())) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = if (embedded) 4.dp else 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    if (!embedded) {
                        TelosCyberAppBarTitle(
                            title = stringResource(I18nR.string.au20_viewer_title),
                            status = files?.let { stringResource(I18nR.string.au20_viewer_count, shown.size) },
                            statusColor = TelosCyber.NeonMagenta,
                        )
                    } else if (files != null) {
                        TelosCyberSectionLabel(
                            text = stringResource(I18nR.string.au20_viewer_count, shown.size),
                            accent = TelosCyber.NeonMagenta,
                        )
                    }
                }
                if (selected.isNotEmpty()) {
                    Text(selected.size.toString())
                    IconButton(onClick = { shareSelected() }) {
                        Icon(painterResource(BaseR.drawable.share_24px), stringResource(I18nR.string.hc_share))
                    }
                    IconButton(onClick = { deleteSelected = true }) { Icon(painterResource(BaseR.drawable.delete_24px), stringResource(I18nR.string.hc_delete)) }
                    IconButton(onClick = { selected = emptyList() }) { Icon(painterResource(BaseR.drawable.close_24px), stringResource(I18nR.string.hc_cancel)) }
                }
                if (selected.isNotEmpty()) {
                    IconButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                        Icon(
                            painterResource(BaseR.drawable.folder_24px),
                            contentDescription = stringResource(I18nR.string.au20_viewer_open_file),
                        )
                    }
                } else {
                    FilledTonalButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                        Icon(painterResource(BaseR.drawable.folder_24px), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(I18nR.string.au20_viewer_open_file))
                    }
                }
            }

            de.mm20.launcher2.ui.component.TelosSearchBar(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(I18nR.string.au20_viewer_search_hint),
                trailing = {
                    TextButton(onClick = { showTrash = true }) { Text(stringResource(I18nR.string.telos_trash_title)) }
                    var sortMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(painterResource(BaseR.drawable.sort_24px), contentDescription = stringResource(I18nR.string.au20_viewer_sort))
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            DocSort.values().forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(s.labelRes)) },
                                    leadingIcon = {
                                        RadioButton(selected = sort == s, onClick = null)
                                    },
                                    onClick = { sort = s; sortMenu = false },
                                )
                            }
                        }
                    }
                },
                filters = {
                    DocCategory.values().forEachIndexed { index, c ->
                        TelosTechPill(
                            text = stringResource(c.labelRes),
                            isSelected = category == c,
                            onClick = {
                                if (category != c) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                category = c
                            },
                            accentColor = TelosCyber.accentFor(index),
                        )
                    }
                },
            )

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!hasAccess) {
                    item(key = "access") {
                        AccessCard(onAllow = {
                            if (Build.VERSION.SDK_INT >= 30) {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
                                    )
                                }.onFailure {
                                    runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                                }
                            } else {
                                legacyPermission.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                            }
                        })
                    }
                }
                if (showRecents) {
                    item(key = "recent_title") {
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            TelosCyberSectionLabel(
                                text = stringResource(I18nR.string.au20_viewer_recent),
                                accent = TelosCyber.NeonCyan,
                                modifier = Modifier.weight(1f).padding(start = 4.dp),
                            )
                            TextButton(onClick = { vm.clearRecents(context) }) { Text(stringResource(I18nR.string.au20_viewer_clear_recent)) }
                        }
                    }
                    item(key = "recent_row") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 0.dp),
                        ) {
                            items(recents.take(12), key = { it.uri }) { r ->
                                val target = DocTarget(Uri.parse(r.uri), r.name, "", r.size, r.opened, true)
                                RecentCard(
                                    target,
                                    onOpen = { open(target.uri, target.name, target.size) },
                                    onShare = { share(target) },
                                    onOpenWith = { openWith(context, target.uri, target.name) },
                                    onInfo = { info = target },
                                    onRemove = { vm.removeRecent(context, r.uri) },
                                )
                            }
                        }
                    }
                    item(key = "all_title") {
                        TelosCyberSectionLabel(
                            text = stringResource(I18nR.string.au20_viewer_cat_all),
                            accent = TelosCyber.NeonGreen,
                            modifier = Modifier.padding(start = 4.dp, top = 16.dp),
                        )
                    }
                }
                if (loading) {
                    items(6) {
                        Box(
                            Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        )
                    }
                } else if (hasAccess && shown.isEmpty()) {
                    item(key = "empty") {
                        if (query.isNotBlank()) {
                            de.mm20.launcher2.ui.component.SearchEmptyState(query)
                        } else {
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    painterResource(BaseR.drawable.description_24px),
                                    contentDescription = null,
                                    modifier = Modifier.size(72.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(stringResource(I18nR.string.au20_viewer_empty_title), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(I18nR.string.au20_viewer_empty_text),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
                items(shown, key = { it.uri.toString() }) { f ->
                    val target = f.target()
                    DocRow(
                        f,
                        onOpen = {
                            if (selected.isEmpty()) open(f.uri, f.name, f.size)
                            else selected = if (f.uri.toString() in selected) selected - f.uri.toString() else selected + f.uri.toString()
                        },
                        selected = f.uri.toString() in selected,
                        onSelect = { selected = if (f.uri.toString() in selected) selected - f.uri.toString() else selected + f.uri.toString() },
                        onShare = { share(target) },
                        onOpenWith = { openWith(context, f.uri, f.name) },
                        onInfo = { info = target },
                        onDelete = { pendingDelete = target },
                    )
                }
                item(key = "bottom") { Spacer(Modifier.navigationBarsPadding()) }
            }
        }
    }

    info?.let { t ->
        AlertDialog(
            onDismissRequest = { info = null },
            containerColor = virtualAppPopup(),
            confirmButton = { TextButton(onClick = { info = null }) { Text(stringResource(android.R.string.ok)) } },
            title = { TelosCyberHeading {  Text(t.name, maxLines = 2, overflow = TextOverflow.Ellipsis)  } },
            text = {
                val ext = DocumentTypes.ext(t.name)
                val cat = DocCategory.of(ext)?.let { stringResource(it.labelRes) }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoLine(stringResource(I18nR.string.au20_viewer_info_type), listOfNotNull(ext.uppercase().ifEmpty { null }, cat).joinToString(" · "))
                    if (t.folder.isNotEmpty()) InfoLine(stringResource(I18nR.string.au20_viewer_info_folder), t.folder)
                    if (t.size > 0) InfoLine(stringResource(I18nR.string.au20_viewer_info_size), Formatter.formatFileSize(context, t.size))
                    if (t.date > 0) InfoLine(stringResource(I18nR.string.au20_viewer_info_date), dateText(context, t.date))
                }
            },
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun AccessCard(onAllow: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .telosTechSurface(borderColor = TelosCyber.NeonOrange, shape = RoundedCornerShape(24.dp)),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                painterResource(BaseR.drawable.folder_24px),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(stringResource(I18nR.string.au20_viewer_access_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(I18nR.string.au20_viewer_access_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onAllow, modifier = Modifier.padding(top = 4.dp)) { Text(stringResource(I18nR.string.au20_viewer_access_button)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocRow(
    file: DocFile,
    selected: Boolean,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    Box {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .telosTechSurface(
                    borderColor = if (selected) TelosCyber.NeonCyan else TelosCyber.NeonMagenta.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(20.dp),
                )
                .clip(RoundedCornerShape(20.dp))
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menu = true },
                ),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(selected, { onSelect() })
                ViewerThumbnail(file.uri, file.name, file.modified, Modifier.size(64.dp).clip(RoundedCornerShape(14.dp))) { TypeTile(file.ext, 64.dp) }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(file.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (file.folder.isNotEmpty()) {
                        Text(
                            file.folder,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        listOf(Formatter.formatShortFileSize(context, file.size), dateText(context, file.modified))
                            .filter { it.isNotEmpty() }.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
        DocMenu(menu, { menu = false }, onShare, onOpenWith, onInfo, null, onDelete)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentCard(
    t: DocTarget,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit,
    onInfo: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    Box {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Transparent,
            modifier = Modifier
                .width(164.dp)
                .telosTechSurface(borderColor = TelosCyber.NeonCyan, shape = RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menu = true },
                ),
        ) {
            Column(Modifier.padding(12.dp)) {
                ViewerThumbnail(t.uri, t.name, t.date, Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(12.dp))) { TypeTile(DocumentTypes.ext(t.name), 44.dp) }
                Text(
                    t.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    dateText(context, t.date),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        DocMenu(menu, { menu = false }, onShare, onOpenWith, onInfo, onRemove)
    }
}

@Composable
private fun DocMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit,
    onInfo: () -> Unit,
    onRemove: (() -> Unit)?,
    onDelete: (() -> Unit)? = null,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(I18nR.string.au20_viewer_share)) },
            leadingIcon = { Icon(painterResource(BaseR.drawable.share_24px), contentDescription = null, modifier = Modifier.size(24.dp)) },
            onClick = { onDismiss(); onShare() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(I18nR.string.au20_viewer_open_with)) },
            leadingIcon = { Icon(painterResource(BaseR.drawable.open_in_new_24px), contentDescription = null, modifier = Modifier.size(24.dp)) },
            onClick = { onDismiss(); onOpenWith() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(I18nR.string.au20_viewer_info)) },
            leadingIcon = { Icon(painterResource(BaseR.drawable.info_24px), contentDescription = null, modifier = Modifier.size(24.dp)) },
            onClick = { onDismiss(); onInfo() },
        )
        if (onDelete != null) DropdownMenuItem(text = { Text(stringResource(I18nR.string.hc_delete)) },
            leadingIcon = { Icon(painterResource(BaseR.drawable.delete_24px), null) }, onClick = { onDismiss(); onDelete() })
        if (onRemove != null) {
            DropdownMenuItem(
                text = { Text(stringResource(I18nR.string.au20_viewer_remove_recent)) },
                leadingIcon = { Icon(painterResource(BaseR.drawable.close_24px), contentDescription = null, modifier = Modifier.size(24.dp)) },
                onClick = { onDismiss(); onRemove() },
            )
        }
    }
}
