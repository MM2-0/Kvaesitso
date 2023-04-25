package de.mm20.launcher2.ui.launcher.widgets.notes

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.mm20.launcher2.services.widgets.WidgetsService
import de.mm20.launcher2.widgets.NotesWidget
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class NotesWidgetVM(
    private val widgetsService: WidgetsService,
) : ViewModel() {
    private val widget = MutableStateFlow<NotesWidget?>(null)

    val noteText = mutableStateOf(widget.value?.config?.storedText ?: "")

    fun updateWidget(widget: NotesWidget) {
        val oldId = this.widget.value?.id
        this.widget.value = widget
        if (widget.id != oldId) noteText.value = widget.config.storedText
    }

    private var updateJob: Job? = null
    fun setText(text: String) {
        noteText.value = text
        updateJob?.cancel()
        val widget = widget.value ?: return
        updateJob = viewModelScope.launch {
            delay(1000)
            widgetsService.updateWidget(widget.copy(config = widget.config.copy(storedText = text)))
        }
    }

    companion object: KoinComponent {
        val Factory = viewModelFactory {
            initializer {
                NotesWidgetVM(get())
            }
        }
    }
}