package de.mm20.launcher2.ui.launcher.widgets.notes

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.services.widgets.WidgetsService
import de.mm20.launcher2.widgets.NotesWidget
import de.mm20.launcher2.widgets.NotesWidgetConfig
import de.mm20.launcher2.widgets.Widget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class NotesWidgetVM(
    private val widgetsService: WidgetsService,
) : ViewModel() {
    private val widget = MutableStateFlow<NotesWidget?>(null)

    val noteText = mutableStateOf(TextFieldValue(widget.value?.config?.storedText ?: ""))

    val linkedFileConflict = mutableStateOf(false)
    val linkedFileSavingState = mutableStateOf(LinkedFileSavingState.Saved)
    val linkedFileReadError = mutableStateOf(false)

    val isLastNoteWidget = widgetsService.countWidgets(NotesWidget.Type).map {
        it == 1
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(), 1)

    fun updateWidget(context: Context, widget: NotesWidget) {
        val oldId = this.widget.value?.id
        val oldFileUri = this.widget.value?.config?.linkedFile
        this.widget.value = widget
        if (widget.id != oldId || oldFileUri != widget.config.linkedFile) {
            val file = widget.config.linkedFile
            linkedFileConflict.value = false
            linkedFileReadError.value = false
            linkedFileSavingState.value = LinkedFileSavingState.Saved
            if (file != null) {
                reloadLinkedFile(context)
            } else {
                noteText.value = TextFieldValue(widget.config.storedText)
            }
        }
    }

    fun onResume(context: Context) {
        val widget = widget.value ?: return
        if (widget.config.linkedFile != null) {
            reloadLinkedFile(context)
        }
    }

    private fun reloadLinkedFile(context: Context) {
        val widget = widget.value ?: return
        val file = widget.config.linkedFile ?: return
        val uri = Uri.parse(file)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri).use {
                    val text = it?.bufferedReader()?.readText()
                    if (text != widget.config.storedText) {
                        when {
                            widget.config.lastSyncSuccessful -> {
                                setText(context, TextFieldValue(text ?: ""))
                            }
                            text?.isNotBlank() == true && widget.config.storedText.isNotBlank() -> {
                                if (!widget.config.lastSyncSuccessful) {
                                    linkedFileConflict.value = true
                                    noteText.value = TextFieldValue(text)
                                } else {
                                    setText(context, TextFieldValue(text))
                                }
                            }
                            text.isNullOrBlank() -> {
                                setText(context, TextFieldValue(widget.config.storedText))
                            }
                            else -> {
                                setText(context, TextFieldValue(text))
                            }
                        }
                    } else {
                        noteText.value = TextFieldValue(widget.config.storedText)
                    }
                }
            } catch (e: Exception) {
                // Catch-all because for some reason the content resolver can throw all sorts of exceptions
                CrashReporter.logException(e)
                noteText.value = TextFieldValue(widget.config.storedText)
                linkedFileReadError.value = true
                widgetsService.updateWidget(
                    widget.copy(
                        config = widget.config.copy(
                            lastSyncSuccessful = false
                        )
                    )
                )
            }
        }
    }

    private var updateJob: Job? = null
    fun setText(context: Context, text: TextFieldValue) {
        noteText.value = text
        updateJob?.cancel()
        val widget = widget.value ?: return
        updateJob = viewModelScope.launch {
            delay(1000)
            val success = if (widget.config.linkedFile != null) {
                writeContentToFile(context, Uri.parse(widget.config.linkedFile), text.text)
            } else false
            widgetsService.updateWidget(widget.copy(config = widget.config.copy(storedText = text.text, lastSyncSuccessful = success)))
        }
    }

    fun exportNote(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val text = noteText.value.text
            val outputStream = context.contentResolver.openOutputStream(uri)
            outputStream?.use {
                it.write(text.toByteArray())
            }
        }
    }

    fun dismissNote() {
        widgetsService.removeWidget(widget.value ?: return)
    }

    private var writeSemaphore = Semaphore(1)
    private suspend fun writeContentToFile(context: Context, uri: Uri, text: String): Boolean {
        return withContext(Dispatchers.IO) {
            writeSemaphore.acquire()
            try {
                val outputStream = context.contentResolver.openOutputStream(uri, "wt")
                outputStream?.use {
                    it.bufferedWriter().use {
                        it.write(text)
                    }
                }
            } catch (e: Exception) {
                linkedFileSavingState.value = LinkedFileSavingState.Error
                CrashReporter.logException(e)
                return@withContext false
            }
            writeSemaphore.release()
            return@withContext true
        }
    }

    fun resolveFileContentConflict(context: Context, strategy: LinkedFileConflictStrategy) {
        val widget = widget.value ?: return
        val linkedFile = widget.config.linkedFile ?: return
        when (strategy) {
            LinkedFileConflictStrategy.KeepLocal -> {
                val text = widget.config.storedText
                viewModelScope.launch {
                    val success = writeContentToFile(context, Uri.parse(linkedFile), text)
                    noteText.value = TextFieldValue(text)
                    if (success) {
                        widgetsService.updateWidget(
                            widget.copy(
                                config = widget.config.copy(
                                    lastSyncSuccessful = true
                                )
                            )
                        )
                    }
                }
            }

            LinkedFileConflictStrategy.KeepFile -> {
                val text = noteText.value.text
                widgetsService.updateWidget(
                    widget.copy(
                        config = widget.config.copy(
                            lastSyncSuccessful = true,
                            storedText = text,
                        )
                    )
                )
            }

            LinkedFileConflictStrategy.Unlink -> {
                noteText.value = TextFieldValue(widget.config.storedText)
                unlinkFile(context)
            }
        }
        linkedFileConflict.value = false
    }

    fun unlinkFile(context: Context) {
        val widget = widget.value ?: return
        widgetsService.updateWidget(
            widget.copy(
                config = widget.config.copy(
                    linkedFile = null,
                    lastSyncSuccessful = false
                )
            )
        )
        linkedFileSavingState.value = LinkedFileSavingState.Saved
        linkedFileReadError.value = false
        linkedFileConflict.value = false
        try {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(widget.config.linkedFile),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            CrashReporter.logException(e)
        }
    }

    fun dismissWidget(widget: Widget) {
        widgetsService.removeWidget(widget)
    }

    fun linkFile(context: Context, uri: Uri) {
        val widget = widget.value ?: return
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            if (widget.config.linkedFile != null) {
                try {
                    context.contentResolver.releasePersistableUriPermission(
                        Uri.parse(widget.config.linkedFile),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (e: SecurityException) {
                    CrashReporter.logException(e)
                }
            }
            widgetsService.updateWidget(
                widget.copy(
                    config = widget.config.copy(
                        linkedFile = uri.toString(),
                        lastSyncSuccessful = false
                    )
                )
            )
        } catch (e: SecurityException) {
            CrashReporter.logException(e)
        }
    }

    fun updateWidgetContent(config: NotesWidgetConfig) {
        val updatedWidget = widget.value?.copy(config = config) ?: return
        noteText.value = TextFieldValue(config.storedText)
        widget.value = updatedWidget
        widgetsService.updateWidget(updatedWidget)
    }


    companion object : KoinComponent {
        val Factory = viewModelFactory {
            initializer {
                NotesWidgetVM(get())
            }
        }
    }
}

enum class LinkedFileConflictStrategy {
    KeepLocal,
    KeepFile,
    Unlink,
}

enum class LinkedFileSavingState {
    Saved,
    Saving,
    Error,
}