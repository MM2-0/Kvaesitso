package de.mm20.launcher2.ui.launcher.widgets.notes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.markdown.MarkdownEditor
import de.mm20.launcher2.ui.locals.LocalSnackbarHostState
import de.mm20.launcher2.widgets.NotesWidget
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

    val viewModel: NotesWidgetVM =
        viewModel(key = "notes-widget-${widget.id}", factory = NotesWidgetVM.Factory)

    val isLastWidget by viewModel.isLastNoteWidget.collectAsState(null)

    LaunchedEffect(widget) {
        viewModel.updateWidget(widget)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown"),
        onResult = {
            if (it != null) viewModel.exportNote(context, it)
        }
    )

    val text by viewModel.noteText
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = 64.dp)
                .animateContentSize(),
        ) {
            MarkdownEditor(
                value = text,
                onValueChange = { viewModel.setText(it) },
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                placeholder = {
                    Text(
                        stringResource(R.string.notes_widget_placeholder),
                    )

                }
            )
            AnimatedVisibility(isLastWidget == false && text.text.isBlank()) {
                IconButton(
                    onClick = {
                        viewModel.dismissNote()
                    },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Icon(Icons.Rounded.Delete, null)
                }
            }
        }

        AnimatedVisibility(text.text.isNotBlank()) {
            var showMenu by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Rounded.MoreVert, null)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.notes_widget_action_new)) },
                            leadingIcon = {
                                Icon(Icons.Rounded.Add, null)
                            },
                            onClick = {
                                val newWidget = NotesWidget(
                                    id = UUID.randomUUID(),
                                    widget.config.copy(storedText = "")
                                )
                                onWidgetAdd(newWidget, 1)
                                showMenu = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.notes_widget_action_save)) },
                            leadingIcon = {
                                Icon(Icons.Rounded.SaveAlt, null)
                            },
                            onClick = {
                                val fileName = context.getString(
                                    R.string.notes_widget_export_filename,
                                    ZonedDateTime.now().format(
                                        DateTimeFormatter.ISO_INSTANT
                                    )
                                )
                                exportLauncher.launch("$fileName.md")
                                showMenu = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.notes_widget_action_dismiss)) },
                            leadingIcon = {
                                Icon(Icons.Rounded.Delete, null)
                            },
                            onClick = {
                                if (isLastWidget == false) {
                                    viewModel.dismissNote()
                                    lifecycleOwner.lifecycleScope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = context.getString(R.string.notes_widget_dismissed),
                                            actionLabel = context.getString(R.string.action_undo),
                                            duration = SnackbarDuration.Short,
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            onWidgetAdd(widget, 0)
                                        }
                                    }
                                } else {
                                    val content = text
                                    viewModel.setText(TextFieldValue(""))
                                    lifecycleOwner.lifecycleScope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = context.getString(R.string.notes_widget_dismissed),
                                            actionLabel = context.getString(R.string.action_undo),
                                            duration = SnackbarDuration.Short,
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.setText(content)
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