package de.mm20.launcher2.ui.settings.google

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

class GoogleSettingsScreenVM: ViewModel(), KoinComponent {
    private val accountsRepository: AccountsRepository by inject()
    private val fileSearchSettings: FileSearchSettings by inject()

    val googleUser = mutableStateOf<Account?>(null)
    val loading = mutableStateOf(true)

    fun onResume() {
        viewModelScope.launch {
            loading.value = true
            googleUser.value = accountsRepository.getCurrentlySignedInAccount(AccountType.Google)
            loading.value = false
        }
    }

    fun signIn(activity: AppCompatActivity) {
        accountsRepository.signin(activity, AccountType.Google)
    }

    fun signOut() {
        accountsRepository.signout(AccountType.Google)
        googleUser.value = null
    }

    val searchFiles = fileSearchSettings.gdriveFiles

    fun setSearchFiles(value: Boolean) {
        fileSearchSettings.setGdriveFiles(value)
    }
}