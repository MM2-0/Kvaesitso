package de.mm20.launcher2.ui.common

import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.backup.BackupCompatibility
import de.mm20.launcher2.backup.BackupComponent
import de.mm20.launcher2.backup.BackupManager
import de.mm20.launcher2.backup.BackupMetadata
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class RestoreBackupSheetVM : ViewModel(), KoinComponent {

    private val backupManager: BackupManager by inject()

    private var restoreUri: Uri? = null

    val state = MutableLiveData(RestoreBackupState.Parsing)
    val metadata = MutableLiveData<BackupMetadata?>(null)
    val compatibility = MutableLiveData<BackupCompatibility?>(null)
    val selectedComponents = MutableLiveData(setOf<BackupComponent>())

    val availableComponents = MutableLiveData(emptyList<BackupComponent>())

    fun setInputUri(uri: Uri) {
        restoreUri = uri
        state.value = RestoreBackupState.Parsing
        viewModelScope.launch {
            val metadata = backupManager.readBackupMeta(uri)
            if (metadata == null) {
                state.value = RestoreBackupState.InvalidFile
                availableComponents.value = emptyList()
            } else {
                state.value = RestoreBackupState.Ready
                compatibility.value = backupManager.checkCompatibility(metadata)
                availableComponents.value = metadata.components.toList().sortedBy { it.ordinal }
            }
            selectedComponents.value = metadata?.components ?: emptySet()
            this@RestoreBackupSheetVM.metadata.value = metadata
        }
    }

    fun  toggleComponent(component: BackupComponent) {
        val components = selectedComponents.value ?: emptySet()
        if (components.contains(component)) {
            selectedComponents.value = components - component
        } else {
            selectedComponents.value = components + component
        }
    }

    fun restore() {
        val components = selectedComponents.value ?: return
        val uri = restoreUri ?: return

        viewModelScope.launch {
            state.value = RestoreBackupState.Restoring
            backupManager.restore(uri, components)
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