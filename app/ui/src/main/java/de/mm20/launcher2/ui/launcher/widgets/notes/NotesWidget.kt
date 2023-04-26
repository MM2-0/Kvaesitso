package de.mm20.launcher2.ui.launcher.widgets.notes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.markdown.MarkdownEditor
import de.mm20.launcher2.widgets.NotesWidget
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Composable
fun NotesWidget(
    widget: NotesWidget,
    onNewNote: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: NotesWidgetVM =
        viewModel(key = "notes-widget-${widget.id}", factory = NotesWidgetVM.Factory)

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
        MarkdownEditor(
            value = text,
            onValueChange = { viewModel.setText(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = {
                Text(stringResource(R.string.notes_widget_placeholder))
            }
        )

        AnimatedVisibility(text.isNotBlank()) {
            var showMenu by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
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
                                onNewNote()
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
                            text = { Text("Dismiss") },
                            leadingIcon = {
                                Icon(Icons.Rounded.Delete, null)
                            },
                            onClick = { /*TODO*/ },
                        )
                    }
                }
            }
        }
    }
}