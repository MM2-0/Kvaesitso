package de.mm20.launcher2.ui.notes

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import de.mm20.launcher2.ui.component.virtualAppPopup
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.SearchEmptyState
import de.mm20.launcher2.ui.component.TelosCyber
import de.mm20.launcher2.ui.component.TelosCyberAppBarTitle
import de.mm20.launcher2.ui.component.TelosTechPill
import de.mm20.launcher2.ui.component.telosTechSurface
import de.mm20.launcher2.ui.component.TelosSearchTopBar
import de.mm20.launcher2.ui.theme.transparency.transparency
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.text.DateFormat
import java.util.Date

@Serializable
data object NotesRoute : NavKey

private val noteSaver = Saver<Note?, String>(
    save = { it?.toJson()?.toString() ?: "" },
    restore = { s -> if (s.isEmpty()) null else runCatching { Note.fromJson(org.json.JSONObject(s)) }.getOrNull() },
)

private fun noteColor(i: Int, fallback: Color): Color =
    NotesStore.Colors.getOrNull(i)?.takeIf { it != 0 }?.let { Color(it).copy(alpha = 0.85f) } ?: fallback

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen() {
    val vm: NotesViewModel = viewModel()
    val context = LocalContext.current
    val notes by vm.visible.collectAsState()
    val labels by vm.labels.collectAsState()
    val query by vm.query.collectAsState()
    val filter by vm.filter.collectAsState()
    val label by vm.label.collectAsState()
    val syncing by vm.syncing.collectAsState()
    val message by vm.message.collectAsState()
    // saved with the instance state, so that a rotation does not throw away the note that is being written
    var editing by rememberSaveable(stateSaver = noteSaver) { mutableStateOf<Note?>(null) }
    var showSync by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if (it.isNotEmpty()) vm.import(it) }

    val text = message?.let {
        val p = it.split(':')
        when (p[0]) {
            "import" -> if (p.getOrNull(2)?.toIntOrNull().let { it != null && it > 0 }) stringResource(I18nR.string.notes_imported_skipped, p[1].toInt(), p[2].toInt())
                else stringResource(I18nR.string.notes_imported, p[1].toInt())
            "sync" -> if (p.getOrNull(3)?.toIntOrNull().let { it != null && it > 0 }) stringResource(I18nR.string.notes_synced_skipped, p[1].toInt(), p[2].toInt(), p[3].toInt())
                else stringResource(I18nR.string.notes_synced, p[1].toInt(), p[2].toInt())
            else -> stringResource(I18nR.string.notes_sync_failed, it.substringAfter(':'))
        }
    }
    // a label filter whose notes are all gone has no chip left to switch it off
    LaunchedEffect(labels, label) { if (label != null && label !in labels) vm.label.value = null }
    LaunchedEffect(text) { if (text != null) { snack.showSnackbar(text); vm.message.value = null } }

    BackHandler(enabled = editing != null || searching || filter != NotesFilter.Notes) {
        when {
            editing != null -> { editing?.let { vm.save(it) }; editing = null }
            searching -> { searching = false; vm.query.value = "" }
            else -> vm.filter.value = NotesFilter.Notes
        }
    }

    de.mm20.launcher2.ui.component.VirtualAppWindow {
    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        val e = editing
        if (e != null) {
            NoteEditor(e, labels, onChange = { editing = it }, onClose = { vm.save(e); editing = null },
                onArchive = { vm.save(e.copy(archived = !e.archived)); editing = null },
                onTrash = { vm.trash(e); editing = null })
        } else Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snack) },
            floatingActionButton = {
                if (filter == NotesFilter.Notes) FloatingActionButton(onClick = { editing = Note(labels = listOfNotNull(label)) }) {
                    Icon(painterResource(BaseR.drawable.add_24px), stringResource(I18nR.string.notes_new))
                }
            },
        ) { pad ->
            Column(Modifier.padding(pad)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (searching) {
                        TelosSearchTopBar(query, { vm.query.value = it }, stringResource(I18nR.string.tsp_search_notes), Modifier.weight(1f), autoFocus = true)
                    } else {
                        TelosCyberAppBarTitle(
                            title = stringResource(when (filter) {
                                NotesFilter.Notes -> I18nR.string.notes_title; NotesFilter.Archive -> I18nR.string.notes_archive; NotesFilter.Trash -> I18nR.string.notes_trash
                            }),
                            status = stringResource(I18nR.string.notes_title),
                            statusColor = when (filter) {
                                NotesFilter.Notes -> TelosCyber.NeonCyan
                                NotesFilter.Archive -> TelosCyber.NeonOrange
                                NotesFilter.Trash -> TelosCyber.NeonMagenta
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    var showFontDialog by remember { mutableStateOf(false) }
                    if (showFontDialog) {
                        de.mm20.launcher2.ui.component.PerAppFontScaleDialog(
                            appKey = "notes",
                            appName = stringResource(I18nR.string.notes_title),
                            onDismiss = { showFontDialog = false }
                        )
                    }
                    IconButton(onClick = { searching = !searching; if (!searching) vm.query.value = "" }) {
                        Icon(painterResource(if (searching) BaseR.drawable.close_24px else BaseR.drawable.search_24px), stringResource(I18nR.string.notes_search))
                    }
                    IconButton(onClick = { showFontDialog = true }) {
                        Icon(painterResource(BaseR.drawable.settings_24px), contentDescription = stringResource(I18nR.string.per_app_font_size))
                    }
                    if (syncing) CircularProgressIndicator(Modifier.size(24.dp)) else Box {
                        IconButton(onClick = { menu = true }) { Icon(painterResource(BaseR.drawable.more_vert_24px), stringResource(I18nR.string.notes_title)) }
                        DropdownMenu(menu, { menu = false }, containerColor = de.mm20.launcher2.ui.component.virtualAppPopup()) {
                            DropdownMenuItem(text = { Text(stringResource(I18nR.string.notes_sync_now)) }, onClick = { menu = false; vm.syncNow() })
                            DropdownMenuItem(text = { Text(stringResource(I18nR.string.notes_sync_settings)) }, onClick = { menu = false; showSync = true })
                            DropdownMenuItem(text = { Text(stringResource(I18nR.string.notes_import)) }, onClick = { menu = false; importer.launch(arrayOf("*/*")) })
                            DropdownMenuItem(text = { Text(stringResource(I18nR.string.notes_archive)) }, onClick = { menu = false; vm.filter.value = NotesFilter.Archive })
                            DropdownMenuItem(text = { Text(stringResource(I18nR.string.notes_trash)) }, onClick = { menu = false; vm.filter.value = NotesFilter.Trash })
                            if (filter == NotesFilter.Trash) DropdownMenuItem(text = { Text(stringResource(I18nR.string.notes_empty_trash)) }, onClick = { menu = false; vm.emptyTrash() })
                        }
                    }
                }
                if (labels.isNotEmpty()) LazyRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(labels) { l ->
                        TelosTechPill(
                            text = l,
                            isSelected = label == l,
                            onClick = { vm.label.value = if (label == l) null else l },
                            accentColor = TelosCyber.accentFor(l.hashCode()),
                        )
                    }
                }
                if (notes.isEmpty() && query.isNotBlank()) {
                    SearchEmptyState(query)
                } else if (notes.isEmpty()) {
                    de.mm20.launcher2.ui.kmp.SharedNotesHost(
                        text = "",
                        onText = { editing = Note(body = it, labels = listOfNotNull(label)) },
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                    )
                } else LazyVerticalStaggeredGrid(
                    StaggeredGridCells.Adaptive(160.dp * androidx.compose.ui.platform.LocalDensity.current.fontScale), Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalItemSpacing = 8.dp,
                ) {
                    items(notes, key = { it.id }) { n ->
                      Box {
                        var cardMenu by remember { mutableStateOf(false) }
                        NoteCard(n, onClick = { if (filter == NotesFilter.Trash) vm.restore(n) else editing = n },
                            onLong = {
                                // in the trash a long press still deletes for good; otherwise a menu offers archive and share
                                if (filter == NotesFilter.Trash) vm.delete(n) else cardMenu = true
                            },
                            trash = filter == NotesFilter.Trash)
                        DropdownMenu(cardMenu, { cardMenu = false }, containerColor = de.mm20.launcher2.ui.component.virtualAppPopup()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(if (n.archived) I18nR.string.au9_share_unarchive else I18nR.string.notes_archive)) },
                                onClick = {
                                    cardMenu = false
                                    vm.save(n.copy(archived = !n.archived))
                                    scope.launch {
                                        snack.currentSnackbarData?.dismiss()
                                        val r = snack.showSnackbar(
                                            context.getString(if (n.archived) I18nR.string.au_planner_unarchived else I18nR.string.au_planner_archived),
                                            context.getString(I18nR.string.au_planner_undo), duration = SnackbarDuration.Short)
                                        if (r == SnackbarResult.ActionPerformed) vm.save(n)
                                    }
                                })
                            NoteImageShareItem(n) { cardMenu = false }
                            de.mm20.launcher2.ui.common.share.ShareMenuItem(onClick = {
                                cardMenu = false
                                de.mm20.launcher2.ui.common.share.ShareActions.shareNote(context, n.title, n.body, false)
                            })
                            DropdownMenuItem(
                                text = { Text(stringResource(I18nR.string.au9_share_note_md)) },
                                onClick = {
                                    cardMenu = false
                                    de.mm20.launcher2.ui.common.share.ShareActions.shareNote(context, n.title, n.body, true)
                                })
                        }
                      }
                    }
                }
            }
        }
        if (showSync) SyncDialog(vm) { showSync = false }
    }
    }
}


private val mdBold = Regex("""\*\*(.*?)\*\*""")
private val mdItalic = Regex("""\*(.*?)\*""")
private val mdStrike = Regex("~~(.*?)~~")

@Composable
private fun renderMarkdown(text: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        append(text)
        mdBold.findAll(text).forEach { match ->
            addStyle(SpanStyle(fontWeight = FontWeight.Bold), match.range.first, match.range.last + 1)
        }
        mdItalic.findAll(text).forEach { match ->
            addStyle(SpanStyle(fontStyle = FontStyle.Italic), match.range.first, match.range.last + 1)
        }
        mdStrike.findAll(text).forEach { match ->
            addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), match.range.first, match.range.last + 1)
        }
    }
}

@Composable
private fun NoteCard(n: Note, onClick: () -> Unit, onLong: () -> Unit, trash: Boolean) {
    val bg = noteColor(n.color, MaterialTheme.colorScheme.surfaceContainerHigh)
    val fg = if (n.color == 0) MaterialTheme.colorScheme.onSurface else Color.Black
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(bg)
            .combinedClickableCompat(onClick, onLong).padding(14.dp),
    ) {
        if (n.title.isNotBlank()) Text(n.title, style = MaterialTheme.typography.titleMedium, color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (n.body.isNotBlank()) Text(n.body, style = MaterialTheme.typography.bodyMedium, color = fg, maxLines = 8, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = if (n.title.isNotBlank()) 6.dp else 0.dp))
        if (n.labels.isNotEmpty()) Text(n.labels.joinToString("  ") { "#$it" }, style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.7f), modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(n.modifiedAt)),
                style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.6f))
            if (n.pinned) Icon(painterResource(BaseR.drawable.star_24px_filled), stringResource(I18nR.string.notes_pin),
                Modifier.padding(start = 6.dp).size(14.dp), tint = fg.copy(alpha = 0.6f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteEditor(note: Note, labels: List<String>, onChange: (Note) -> Unit, onClose: () -> Unit, onArchive: () -> Unit, onTrash: () -> Unit) {
    val bg = noteColor(note.color, MaterialTheme.colorScheme.surface)
    val fg = if (note.color == 0) MaterialTheme.colorScheme.onSurface else Color.Black
    var colors by remember { mutableStateOf(false) }
    var labelDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(bg).imePadding()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(painterResource(BaseR.drawable.arrow_back_24px), stringResource(I18nR.string.notes_back), tint = fg) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { onChange(note.copy(pinned = !note.pinned)) }) {
                Icon(painterResource(if (note.pinned) BaseR.drawable.star_24px_filled else BaseR.drawable.star_24px_outlined), stringResource(I18nR.string.notes_pin), tint = fg)
            }
            IconButton(onClick = { colors = !colors }) { Icon(painterResource(BaseR.drawable.palette_24px), stringResource(I18nR.string.notes_color), tint = fg) }
            IconButton(onClick = { labelDialog = true }) { Icon(painterResource(BaseR.drawable.label_24px), stringResource(I18nR.string.notes_labels), tint = fg) }
            IconButton(onClick = onArchive) { Icon(painterResource(BaseR.drawable.archive_24px), stringResource(I18nR.string.notes_archive), tint = fg) }
            Box {
                var shareMenu by remember { mutableStateOf(false) }
                IconButton(onClick = { shareMenu = true }) { Icon(painterResource(BaseR.drawable.share_24px), stringResource(I18nR.string.notes_share), tint = fg) }
                DropdownMenu(shareMenu, { shareMenu = false }, containerColor = de.mm20.launcher2.ui.component.virtualAppPopup()) {
                    NoteImageShareItem(note) { shareMenu = false }
                    DropdownMenuItem(text = { Text(stringResource(I18nR.string.au9_share_note_text)) }, onClick = {
                        shareMenu = false
                        de.mm20.launcher2.ui.common.share.ShareActions.shareNote(context, note.title, note.body, false)
                    })
                    DropdownMenuItem(text = { Text(stringResource(I18nR.string.au9_share_note_md)) }, onClick = {
                        shareMenu = false
                        de.mm20.launcher2.ui.common.share.ShareActions.shareNote(context, note.title, note.body, true)
                    })
                }
            }
            IconButton(onClick = onTrash) { Icon(painterResource(BaseR.drawable.delete_24px), stringResource(I18nR.string.notes_delete), tint = fg) }
        }
        if (colors) LazyRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(NotesStore.Colors.indices.toList()) { i ->
                Box(Modifier.size(32.dp).clip(CircleShape).background(noteColor(i, MaterialTheme.colorScheme.surfaceContainerHighest))
                    .clickable { onChange(note.copy(color = i)) })
            }
        }
        BasicTextField(
            note.title, { onChange(note.copy(title = it)) },
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            textStyle = MaterialTheme.typography.headlineSmall.copy(color = fg, fontWeight = FontWeight.SemiBold),
            cursorBrush = SolidColor(fg),
            decorationBox = { inner -> Box { if (note.title.isEmpty()) Text(stringResource(I18nR.string.notes_title_hint), style = MaterialTheme.typography.headlineSmall, color = fg.copy(alpha = 0.4f)); inner() } },
        )
        if (note.labels.isNotEmpty()) Text(note.labels.joinToString("  ") { "#$it" }, Modifier.padding(horizontal = 20.dp), color = fg.copy(alpha = 0.7f), style = MaterialTheme.typography.labelLarge)
        BasicTextField(
            note.body, { onChange(note.copy(body = it)) },
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp, vertical = 8.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = fg), cursorBrush = SolidColor(fg),
            decorationBox = { inner -> Box { if (note.body.isEmpty()) Text(stringResource(I18nR.string.notes_body_hint), style = MaterialTheme.typography.bodyLarge, color = fg.copy(alpha = 0.4f)); inner() } },
        )
    }
    if (labelDialog) {
        var text by remember { mutableStateOf(note.labels.joinToString(", ")) }
        AlertDialog(
            onDismissRequest = { labelDialog = false },
        containerColor = virtualAppPopup(),
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.notes_labels))  } },
            text = {
                Column {
                    OutlinedTextField(text, { text = it }, singleLine = true, placeholder = { Text(stringResource(I18nR.string.notes_labels_hint)) })
                    if (labels.isNotEmpty()) Text(labels.joinToString("  ") { "#$it" }, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium)
                }
            },
            confirmButton = { TextButton(onClick = {
                onChange(note.copy(labels = text.split(',').map { it.trim().trimStart('#') }.filter { it.isNotEmpty() }.distinct())); labelDialog = false
            }) { Text(stringResource(I18nR.string.notes_ok)) } },
            dismissButton = { TextButton(onClick = { labelDialog = false }) { Text(stringResource(I18nR.string.notes_cancel)) } },
        )
    }
}

@Composable
private fun SyncDialog(vm: NotesViewModel, onDismiss: () -> Unit) {
    val s = vm.sync.settings
    var folder by remember { mutableStateOf(s.folderUri) }
    var url by remember { mutableStateOf(s.ncUrl) }
    var user by remember { mutableStateOf(s.ncUser) }
    var pass by remember { mutableStateOf(s.ncPassword) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            folder = uri.toString()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = virtualAppPopup(),
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.notes_sync_settings))  } },
        text = {
            Column(Modifier.verticalScrollCompat(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(I18nR.string.notes_sync_folder), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(I18nR.string.notes_sync_folder_desc), style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { picker.launch(null) }) { Text(stringResource(I18nR.string.notes_sync_choose_folder)) }
                    if (folder != null) TextButton(onClick = { folder = null }) { Text(stringResource(I18nR.string.notes_sync_off)) }
                }
                Text(folder?.let { Uri_decode(it) } ?: stringResource(I18nR.string.notes_sync_not_set), style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text(stringResource(I18nR.string.notes_sync_nextcloud), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(I18nR.string.notes_sync_nextcloud_desc), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(url, { url = it }, label = { Text(stringResource(I18nR.string.notes_sync_server)) }, singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Uri))
                OutlinedTextField(user, { user = it }, label = { Text(stringResource(I18nR.string.notes_sync_user)) }, singleLine = true)
                OutlinedTextField(pass, { pass = it }, label = { Text(stringResource(I18nR.string.notes_sync_password)) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            }
        },
        confirmButton = { TextButton(onClick = {
            s.folderUri = folder; s.ncUrl = url.trim(); s.ncUser = user.trim(); s.ncPassword = pass
            onDismiss(); vm.syncNow()
        }) { Text(stringResource(I18nR.string.notes_sync_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.notes_cancel)) } },
    )
}

private fun Uri_decode(s: String) = android.net.Uri.decode(s).substringAfter("tree/")

@Composable
private fun NoteImageShareItem(note: Note, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    DropdownMenuItem(enabled = !busy, text = { Text(stringResource(I18nR.string.notes_share_image)) }, onClick = {
        busy = true
        scope.launch {
            val success = shareNoteImages(context, note)
            if (!success) android.widget.Toast.makeText(context, I18nR.string.au9_share_failed, android.widget.Toast.LENGTH_LONG).show()
            busy = false
            onDismiss()
        }
    })
}
