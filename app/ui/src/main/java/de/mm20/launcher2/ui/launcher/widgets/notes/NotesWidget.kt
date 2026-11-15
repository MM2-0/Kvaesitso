package de.mm20.launcher2.ui.launcher.widgets.notes

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.DismissableBottomSheet
import de.mm20.launcher2.ui.component.Tooltip
import de.mm20.launcher2.ui.component.markdown.MarkdownEditor
import de.mm20.launcher2.ui.component.markdown.MarkdownText
import de.mm20.launcher2.ui.locals.LocalSnackbarHostState
import de.mm20.launcher2.widgets.NotesWidget
import de.mm20.launcher2.widgets.NotesWidgetConfig
import de.mm20.launcher2.widgets.Widget
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
fun NotesWidget(
    widget: NotesWidget,
    onWidgetAdd: (widget: Widget, offset: Int) -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = LocalSnackbarHostState.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var showConflictResolveSheet by remember { mutableStateOf(false) }
    var readWriteErrorSheetText by remember { mutableStateOf<String?>(null) }

    var focused by remember { mutableStateOf(false) }

    val viewModel: NotesWidgetVM =
        viewModel(key = "notes-widget-${widget.id}", factory = NotesWidgetVM.Factory)

    val isLastWidget by viewModel.isLastNoteWidget.collectAsState(null)

    LaunchedEffect(widget) {
        viewModel.updateWidget(context, widget)
    }
    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.onResume(context)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown"),
        onResult = {
            if (it != null) viewModel.exportNote(context, it)
        }
    )

    val linkFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) {
        it ?: return@rememberLauncherForActivityResult
        viewModel.linkFile(context, it)
    }
    val linkExistingFileLauncher = rememberLauncherForActivityResult(
        LinkExistingFileContract()
    ) {
        it ?: return@rememberLauncherForActivityResult
        viewModel.linkFile(context, it)
    }
    var showLinkChooser by remember { mutableStateOf(false) }
    if (showLinkChooser) {
        AlertDialog(
            onDismissRequest = { showLinkChooser = false },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.note_widget_link_file))  } },
            text = {
                Column {
                    TextButton(onClick = {
                        showLinkChooser = false
                        linkExistingFileLauncher.launch(arrayOf("text/*", "application/octet-stream"))
                    }) { Text(stringResource(I18nR.string.au4_noteslink_link_existing)) }
                    TextButton(onClick = {
                        showLinkChooser = false
                        linkFileLauncher.launch(getDefaultNoteFileName(context))
                    }) { Text(stringResource(I18nR.string.au4_noteslink_create_new)) }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLinkChooser = false }) {
                    Text(stringResource(I18nR.string.au4_noteslink_cancel))
                }
            },
        )
    }

    val text by viewModel.noteText
    if (viewModel.linkedFileConflict.value) {
        Banner(
            modifier = Modifier.padding(16.dp),
            text = stringResource(I18nR.string.note_widget_conflict),
            icon = BaseR.drawable.error_24px,
            primaryAction = {
                Button(onClick = { showConflictResolveSheet = true }) {
                    Text(stringResource(I18nR.string.note_widget_conflict_action_resolve))
                }
            },
        )
        NoteWidgetConflictResolveSheet(
            expanded = showConflictResolveSheet,
            localContent = widget.config.storedText,
            fileContent = text.text,
            onResolve = {
                viewModel.resolveFileContentConflict(context, it)
                showConflictResolveSheet = false
            },
            onDismissRequest = {
                showConflictResolveSheet = false
            }
        )
        return
    }
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = 64.dp)
                .animateContentSize(),
        ) {
            MarkdownEditor(
                value = text,
                onValueChange = { viewModel.setText(context, it) },
                focus = focused,
                onFocusChange = { focused = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                placeholder = {
                    Text(
                        stringResource(I18nR.string.notes_widget_placeholder),
                    )
                }
            )
            AnimatedVisibility(text.text.isBlank()) {
                Row(
                    modifier = Modifier.padding(8.dp),
                ) {
                    Tooltip(
                        tooltipText = stringResource(
                            if (widget.config.linkedFile == null) I18nR.string.note_widget_link_file
                            else I18nR.string.note_widget_action_unlink_file
                        ),
                    ) {
                        IconButton(
                            onClick = {
                                if (widget.config.linkedFile == null) {
                                    showLinkChooser = true
                                } else {
                                    viewModel.unlinkFile(context)
                                }
                            },
                        ) {
                            Icon(
                                painterResource(
                                    if (widget.config.linkedFile == null) BaseR.drawable.link_24px
                                    else BaseR.drawable.link_off_24px,
                                ),
                                stringResource(
                                    if (widget.config.linkedFile == null) I18nR.string.note_widget_link_file
                                    else I18nR.string.note_widget_action_unlink_file
                                )
                            )
                        }
                    }
                    if (isLastWidget == false) {
                        Tooltip(
                            tooltipText = stringResource(I18nR.string.notes_widget_action_dismiss)
                        ) {
                            IconButton(
                                onClick = {
                                    viewModel.dismissNote()
                                },
                            ) {
                                Icon(
                                    painterResource(BaseR.drawable.delete_24px),
                                    stringResource(I18nR.string.notes_widget_action_dismiss)
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            text.text.isBlank() && (viewModel.linkedFileReadError.value ||
                viewModel.linkedFileSavingState.value == LinkedFileSavingState.Error)
        ) {
            Row(modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp)) {
                NoteFileErrorChip(viewModel, onShow = { readWriteErrorSheetText = it })
            }
        }

        AnimatedVisibility(text.text.isNotBlank()) {
            var showMenu by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NoteFileErrorChip(viewModel, onShow = { readWriteErrorSheetText = it })
                Spacer(modifier = Modifier.weight(1f))
                Box {
                    Tooltip(
                        tooltipText = stringResource(I18nR.string.action_more_actions)
                    ) {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                painterResource(BaseR.drawable.more_vert_24px),
                                stringResource(I18nR.string.action_more_actions)
                            )
                        }
                    }
                    DropdownMenuPopup(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }) {
                        DropdownMenuGroup(
                            shapes = MenuDefaults.groupShapes(),
                        ) {
                            DropdownMenuItem(
                                shape = MenuDefaults.leadingItemShape,
                                text = { Text(stringResource(I18nR.string.notes_widget_action_new)) },
                                leadingIcon = {
                                    Icon(painterResource(BaseR.drawable.add_24px), null)
                                },
                                onClick = {
                                    val newWidget = NotesWidget(
                                        id = UUID.randomUUID(),
                                    )
                                    onWidgetAdd(newWidget, 1)
                                    showMenu = false
                                },
                            )
                            DropdownMenuItem(
                                shape = MenuDefaults.middleItemShape,
                                text = { Text(stringResource(I18nR.string.menu_share)) },
                                leadingIcon = {
                                    Icon(painterResource(BaseR.drawable.share_24px), null)
                                },
                                onClick = {
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, text.text)
                                        context.startActivity(Intent.createChooser(this, null))
                                    }
                                    showMenu = false
                                },
                            )
                            DropdownMenuItem(
                                shape = MenuDefaults.middleItemShape,
                                text = { Text(stringResource(I18nR.string.notes_widget_action_save)) },
                                leadingIcon = {
                                    Icon(painterResource(BaseR.drawable.file_save_24px), null)
                                },
                                onClick = {
                                    val fileName = getDefaultNoteFileName(context)
                                    exportLauncher.launch(fileName)
                                    showMenu = false
                                },
                            )
                            if (widget.config.linkedFile == null) {
                                DropdownMenuItem(
                                    shape = MenuDefaults.middleItemShape,
                                    text = { Text(stringResource(I18nR.string.note_widget_link_file)) },
                                    leadingIcon = {
                                        Icon(painterResource(BaseR.drawable.link_24px), null)
                                    },
                                    onClick = {
                                        showLinkChooser = true
                                        showMenu = false
                                    },
                                )
                            } else {
                                DropdownMenuItem(
                                    shape = MenuDefaults.middleItemShape,
                                    text = { Text(stringResource(I18nR.string.note_widget_action_unlink_file)) },
                                    leadingIcon = {
                                        Icon(painterResource(BaseR.drawable.link_off_24px), null)
                                    },
                                    onClick = {
                                        viewModel.unlinkFile(context)
                                        showMenu = false
                                    },
                                )
                            }
                            DropdownMenuItem(
                                shape = MenuDefaults.trailingItemShape,
                                text = { Text(stringResource(I18nR.string.notes_widget_action_dismiss)) },
                                leadingIcon = {
                                    Icon(painterResource(BaseR.drawable.delete_24px), null)
                                },
                                onClick = {
                                    val wasLast = isLastWidget != false

                                    if (wasLast) {
                                        viewModel.updateWidgetContent(NotesWidgetConfig())
                                    } else {
                                        viewModel.dismissWidget(widget)
                                    }

                                    lifecycleOwner.lifecycleScope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = context.getString(I18nR.string.notes_widget_dismissed),
                                            actionLabel = context.getString(I18nR.string.action_undo),
                                            duration = SnackbarDuration.Short,
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            if (wasLast) {
                                                viewModel.updateWidgetContent(widget.config)
                                            } else {
                                                onWidgetAdd(widget, 0)
                                            }
                                        }
                                    }

                                    showMenu = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    NoteReadWriteErrorSheet(
        message = readWriteErrorSheetText,
        onDismiss = { readWriteErrorSheetText = null },
        onRelink = {
            showLinkChooser = true
        },
        onUnlink = {
            viewModel.unlinkFile(context)
        }
    )
}

private class LinkExistingFileContract : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        return super.createIntent(context, input).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
    }
}

@Composable
private fun NoteFileErrorChip(viewModel: NotesWidgetVM, onShow: (String) -> Unit) {
    val context = LocalContext.current
    val isWrite = viewModel.linkedFileSavingState.value == LinkedFileSavingState.Error
    if (!isWrite && !viewModel.linkedFileReadError.value) return
    TextButton(
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        onClick = {
            onShow(
                context.getString(
                    if (isWrite) I18nR.string.note_widget_file_write_error_description
                    else I18nR.string.note_widget_file_read_error_description
                )
            )
        }) {
        Icon(
            modifier = Modifier
                .padding(end = ButtonDefaults.IconSpacing)
                .size(ButtonDefaults.IconSize),
            painter = painterResource(BaseR.drawable.error_20px),
            contentDescription = null,
        )
        Text(
            stringResource(
                if (isWrite) I18nR.string.note_widget_file_write_error
                else I18nR.string.note_widget_file_read_error
            )
        )
    }
}

@Composable
fun NoteWidgetConflictResolveSheet(
    expanded: Boolean,
    localContent: String,
    fileContent: String,
    onResolve: (LinkedFileConflictStrategy) -> Unit,
    onDismissRequest: () -> Unit,
) {
    DismissableBottomSheet(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        var selectedStrategy by remember { mutableStateOf<LinkedFileConflictStrategy?>(null) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .navigationBarsPadding(),
        ) {

            Text(
                stringResource(I18nR.string.note_widget_conflict_description),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(I18nR.string.note_widget_conflict_local_version),
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                style = MaterialTheme.typography.titleSmall
            )
            SelectableNoteContent(
                content = localContent,
                selected = selectedStrategy == LinkedFileConflictStrategy.KeepLocal,
                onSelect = { selectedStrategy = LinkedFileConflictStrategy.KeepLocal },
            )
            Text(
                stringResource(I18nR.string.note_widget_conflict_file_version),
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                style = MaterialTheme.typography.titleSmall
            )
            SelectableNoteContent(
                content = fileContent,
                selected = selectedStrategy == LinkedFileConflictStrategy.KeepFile,
                onSelect = { selectedStrategy = LinkedFileConflictStrategy.KeepFile },
            )
            Column(
                modifier = Modifier.padding(top = 8.dp),
            ) {
                OutlinedButton(
                    onClick = { onResolve(LinkedFileConflictStrategy.Unlink) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                ) {
                    Icon(
                        painterResource(BaseR.drawable.link_off_24px),
                        null,
                        modifier = Modifier
                            .padding(end = ButtonDefaults.IconSpacing)
                            .size(ButtonDefaults.IconSize)
                    )
                    Text(stringResource(I18nR.string.note_widget_action_unlink_file))
                }
                Button(
                    onClick = { onResolve(selectedStrategy ?: return@Button) },
                    enabled = selectedStrategy != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painterResource(BaseR.drawable.check_circle_20px),
                        null,
                        modifier = Modifier
                            .padding(end = ButtonDefaults.IconSpacing)
                            .size(ButtonDefaults.IconSize)
                    )
                    Text(stringResource(I18nR.string.note_widget_conflict_keep_selected))
                }
            }
        }
    }
}

@Composable
fun SelectableNoteContent(
    content: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val color =
        if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    Surface(
        modifier = modifier
            .fillMaxWidth(),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        ),
        color = color,
        shape = MaterialTheme.shapes.small,
    ) {
        Box(
            modifier = Modifier
                .combinedClickable(
                    onClick = onSelect,
                    onLongClick = { expanded = !expanded },
                )
                .animateContentSize() then if (expanded) Modifier.heightIn(min = 100.dp) else Modifier.height(
                100.dp
            ),
        ) {
            MarkdownText(
                modifier = Modifier
                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
                    .padding(16.dp),
                text = content, onTextChange = {}
            )
            IconButton(
                modifier = Modifier.align(Alignment.TopEnd),
                onClick = onSelect
            ) {
                Icon(
                    painterResource(if (selected) BaseR.drawable.check_circle_24px_filled else BaseR.drawable.circle_24px),
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    contentDescription = null
                )
            }
            IconButton(
                modifier = Modifier.align(Alignment.BottomEnd),
                onClick = { expanded = !expanded }
            ) {
                Icon(
                    painterResource(if (expanded) BaseR.drawable.keyboard_arrow_up_24px else BaseR.drawable.keyboard_arrow_down_24px),
                    contentDescription = null
                )
            }
        }
    }
}

@Composable
fun NoteReadWriteErrorSheet(
    message: String?,
    onDismiss: () -> Unit,
    onRelink: () -> Unit,
    onUnlink: () -> Unit,
) {
    DismissableBottomSheet(
        state = message,
        expanded = { it != null },
        onDismissRequest = onDismiss,
    ) { message ->
        message ?: return@DismissableBottomSheet

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .padding(horizontal = 24.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onRelink()
                        onDismiss()
                    }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(BaseR.drawable.link_24px), null)
                Text(
                    stringResource(I18nR.string.note_widget_action_relink_file),
                    modifier = Modifier.padding(start = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onUnlink()
                        onDismiss()
                    }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(BaseR.drawable.link_off_24px), null)
                Text(
                    stringResource(I18nR.string.note_widget_action_unlink_file),
                    modifier = Modifier.padding(start = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

fun getDefaultNoteFileName(context: Context): String {
    return context.getString(
        I18nR.string.notes_widget_export_filename,
        // No colons: many document providers reject or rewrite them in file names
        ZonedDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss", java.util.Locale.ROOT)
        )
    ) + ".md"
}