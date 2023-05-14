package de.mm20.launcher2.ui.settings.filesearch

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.accounts.Account
import de.mm20.launcher2.accounts.AccountType
import de.mm20.launcher2.accounts.AccountsRepository
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class FileSearchSettingsScreenVM : ViewModel(), KoinComponent {
    private val dataStore: LauncherDataStore by inject()
    private val accountsRepository: AccountsRepository by inject()
    private val permissionsManager: PermissionsManager by inject()

    val hasFilePermission = permissionsManager.hasPermission(PermissionGroup.ExternalStorage)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val loading = mutableStateOf(true)
    val nextcloudAccount = mutableStateOf<Account?>(null)
    val owncloudAccount = mutableStateOf<Account?>(null)
    val googleAccount = mutableStateOf<Account?>(null)

    val googleAvailable = accountsRepository.isSupported(AccountType.Google)

    fun onResume() {
        viewModelScope.launch {
            nextcloudAccount.value =
                accountsRepository.getCurrentlySignedInAccount(AccountType.Nextcloud)
            owncloudAccount.value =
                accountsRepository.getCurrentlySignedInAccount(AccountType.Owncloud)
            googleAccount.value = accountsRepository.getCurrentlySignedInAccount(AccountType.Google)
            loading.value = false
        }
    }

    val localFiles = dataStore.data.map { it.fileSearch.localFiles }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setLocalFiles(localFiles: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setFileSearch(
                        it.fileSearch
                            .toBuilder()
                            .setLocalFiles(localFiles)
                    )
                    .build()
            }
        }
    }

    val nextcloud = dataStore.data.map { it.fileSearch.nextcloud }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setNextcloud(nextcloud: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setFileSearch(
                        it.fileSearch
                            .toBuilder()
                            .setNextcloud(nextcloud)
                    )
                    .build()
            }
        }
    }

    val gdrive = dataStore.data.map { it.fileSearch.gdrive }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setGdrive(gdrive: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setFileSearch(
                        it.fileSearch
                            .toBuilder()
                            .setGdrive(gdrive)
                    )
                    .build()
            }
        }
    }

    val onedrive = dataStore.data.map { it.fileSearch.onedrive }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setOneDrive(onedrive: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setFileSearch(
                        it.fileSearch
                            .toBuilder()
                            .setOnedrive(onedrive)
                    )
                    .build()
            }
        }
    }

    val owncloud = dataStore.data.map { it.fileSearch.owncloud }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setOwncloud(owncloud: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setFileSearch(
                        it.fileSearch
                            .toBuilder()
                            .setOwncloud(owncloud)
                    )
                    .build()
            }
        }
    }

    fun requestFilePermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.ExternalStorage)
    }

    fun login(context: AppCompatActivity, accountType: AccountType) {
        accountsRepository.signin(context, accountType)
    }
}