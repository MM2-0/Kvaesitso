package de.mm20.launcher2.ui.settings.backup

import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import org.koin.core.component.KoinComponent

class BackupSettingsScreenVM : ViewModel(), KoinComponent {

    val showBackupSheet = mutableStateOf(false)

    val restoreUri = mutableStateOf<Uri?>(null)

    fun setShowBackupSheet(show: Boolean) {
        showBackupSheet.value = show
    }

    fun setRestoreUri(uri: Uri?) {
        restoreUri.value = uri
    }
}