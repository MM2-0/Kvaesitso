package de.mm20.launcher2.ui.common

import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.backup.BackupCompatibility
import de.mm20.launcher2.backup.BackupManager
import de.mm20.launcher2.backup.BackupMetadata
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class RestoreBackupSheetVM : ViewModel(), KoinComponent {

    private val backupManager: BackupManager by inject()

    private var restoreUri: Uri? = null

    val state = mutableStateOf(RestoreBackupState.Parsing)
    val metadata = mutableStateOf<BackupMetadata?>(null)
    val compatibility = mutableStateOf<BackupCompatibility?>(null)

    fun setInputUri(uri: Uri) {
        restoreUri = uri
        state.value = RestoreBackupState.Parsing
        viewModelScope.launch {
            val metadata = backupManager.readBackupMeta(uri)
            if (metadata == null) {
                state.value = RestoreBackupState.InvalidFile
            } else {
                state.value = RestoreBackupState.Ready
                compatibility.value = backupManager.checkCompatibility(metadata)
            }
            this@RestoreBackupSheetVM.metadata.value = metadata
        }
    }

    fun restore() {
        val uri = restoreUri ?: return

        viewModelScope.launch {
            state.value = RestoreBackupState.Restoring
            backupManager.restore(uri)
            state.value = RestoreBackupState.Restored
        }
    }
}

enum class RestoreBackupState {
    Parsing,
    InvalidFile,
    Ready,
    Restoring,
    Restored,
}