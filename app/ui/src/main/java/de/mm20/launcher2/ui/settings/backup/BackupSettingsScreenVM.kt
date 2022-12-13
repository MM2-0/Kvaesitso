package de.mm20.launcher2.ui.settings.backup

import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.koin.core.component.KoinComponent

class BackupSettingsScreenVM : ViewModel(), KoinComponent {

    val showBackupSheet = MutableLiveData(false)

    val restoreUri = MutableLiveData<Uri?>(null)

    fun setShowBackupSheet(show: Boolean) {
        showBackupSheet.value = show
    }

    fun setRestoreUri(uri: Uri?) {
        restoreUri.value = uri
    }
}