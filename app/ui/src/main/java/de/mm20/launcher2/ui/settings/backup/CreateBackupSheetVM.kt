package de.mm20.launcher2.ui.settings.backup

import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.backup.BackupManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class CreateBackupSheetVM : ViewModel(), KoinComponent {

    private val backupManager: BackupManager by inject()

    val state = mutableStateOf(CreateBackupState.Ready)

    fun reset() {
        state.value = CreateBackupState.Ready
    }

    fun createBackup(uri: Uri) {
        viewModelScope.launch {
            state.value = CreateBackupState.BackingUp
            try {
                backupManager.backup(uri)
                state.value = CreateBackupState.BackedUp
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                state.value = CreateBackupState.Failed
            }
        }
    }
}

enum class CreateBackupState {
    Ready,
    BackingUp,
    BackedUp,
    Failed,
}
