package de.mm20.launcher2.ui.launcher.widgets.notes

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.mm20.launcher2.services.widgets.WidgetsService
import de.mm20.launcher2.widgets.NotesWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class NotesWidgetVM(
    private val widgetsService: WidgetsService,
) : ViewModel() {
    private val widget = MutableStateFlow<NotesWidget?>(null)

    val noteText = mutableStateOf(widget.value?.config?.storedText ?: "")

    val isLastNoteWidget = widgetsService.countWidgets(NotesWidget.Type).map {
        it == 1
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(), 1)

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

    fun exportNote(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val text = noteText.value
            Log.d("MM20", text)
            val outputStream = context.contentResolver.openOutputStream(uri)
            outputStream?.use {
                it.write(text.toByteArray())
            }
        }
    }

    fun dismissNote() {
        widgetsService.removeWidget(widget.value ?: return)
    }


    companion object: KoinComponent {
        val Factory = viewModelFactory {
            initializer {
                NotesWidgetVM(get())
            }
        }
    }
}