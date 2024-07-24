package de.mm20.launcher2.ui.settings.owncloud

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.accounts.Account
import de.mm20.launcher2.accounts.AccountType
import de.mm20.launcher2.accounts.AccountsRepository
import de.mm20.launcher2.preferences.search.FileSearchSettings
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class OwncloudSettingsScreenVM: ViewModel(), KoinComponent {
    private val accountsRepository: AccountsRepository by inject()
    private val fileSearchSettings: FileSearchSettings by inject()

    val owncloudUser = mutableStateOf<Account?>(null)
    val loading = mutableStateOf(true)

    fun onResume() {
        viewModelScope.launch {
            loading.value = true
            owncloudUser.value = accountsRepository.getCurrentlySignedInAccount(AccountType.Owncloud)
            loading.value = false
        }
    }

    fun signIn(activity: AppCompatActivity) {
        accountsRepository.signin(activity, AccountType.Owncloud)
    }

    fun signOut() {
        accountsRepository.signout(AccountType.Owncloud)
        owncloudUser.value = null
    }

    val searchFiles = fileSearchSettings.owncloudFiles

    fun setSearchFiles(value: Boolean) {
        fileSearchSettings.setOwncloudFiles(value)
    }
}