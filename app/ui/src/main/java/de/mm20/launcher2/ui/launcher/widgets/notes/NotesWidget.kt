package de.mm20.launcher2.ui.launcher.widgets.notes

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.component.markdown.MarkdownEditor
import de.mm20.launcher2.widgets.NotesWidget

@Composable
fun NotesWidget(widget: NotesWidget) {
    val viewModel: NotesWidgetVM = viewModel(key = "notes-widget-${widget.id}", factory = NotesWidgetVM.Factory)

    LaunchedEffect(widget) {
        viewModel.updateWidget(widget)
    }

    val text by viewModel.noteText

    MarkdownEditor(
        value = text,
        onValueChange = { viewModel.setText(it) },
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    )
}