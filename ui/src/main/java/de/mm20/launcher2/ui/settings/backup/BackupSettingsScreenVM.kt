package de.mm20.launcher2.ui.settings.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.backup.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

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