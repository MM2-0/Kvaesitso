package de.mm20.launcher2.ui.settings.backup

import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.backup.BackupComponent
import de.mm20.launcher2.backup.BackupManager
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class CreateBackupSheetVM : ViewModel(), KoinComponent {

    private val backupManager: BackupManager by inject()

    val state = MutableLiveData(CreateBackupState.Ready)

    val selectedComponents = MutableLiveData(BackupComponent.values().toSet())

    fun reset() {
        state.value = CreateBackupState.Ready
    }

    fun createBackup(uri: Uri) {
        val components = selectedComponents.value ?: return
        viewModelScope.launch {
            state.value = CreateBackupState.BackingUp
            backupManager.backup(uri, components)
            state.value = CreateBackupState.BackedUp
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

}

enum class CreateBackupState {
    Ready,
    BackingUp,
    BackedUp,
}