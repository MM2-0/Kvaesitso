package de.mm20.launcher2.ui.settings.integrations

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.accounts.Account
import de.mm20.launcher2.accounts.AccountType
import de.mm20.launcher2.accounts.AccountsRepository
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class IntegrationsSettingsScreenVM : ViewModel(), KoinComponent {
    private val accountsRepository: AccountsRepository by inject()

    val isGoogleAvailable = accountsRepository.isSupported(AccountType.Google)
    val isMicrosoftAvailable = accountsRepository.isSupported(AccountType.Microsoft)

    val googleUser = mutableStateOf<Account?>(null)
    val msUser= mutableStateOf<Account?>(null)
    val nextcloudUser = mutableStateOf<Account?>(null)
    val owncloudUser = mutableStateOf<Account?>(null)

    val loading = mutableStateOf(true)

    fun onResume() {
        viewModelScope.launch {
            loading.value = true
            googleUser.value = accountsRepository.getCurrentlySignedInAccount(AccountType.Google)
            nextcloudUser.value = accountsRepository.getCurrentlySignedInAccount(AccountType.Nextcloud)
            msUser.value = accountsRepository.getCurrentlySignedInAccount(AccountType.Microsoft)
            owncloudUser.value = accountsRepository.getCurrentlySignedInAccount(AccountType.Owncloud)
            loading.value = false
        }
    }

    fun signIn(activity: AppCompatActivity, accountType: AccountType) {
        accountsRepository.signin(activity, accountType)
    }

    fun signOut(accountType: AccountType) {
        accountsRepository.signout(accountType)
        when(accountType){
            AccountType.Google -> googleUser.value = null
            AccountType.Microsoft -> msUser.value = null
            AccountType.Nextcloud -> nextcloudUser.value = null
            AccountType.Owncloud -> owncloudUser.value = null
        }
    }
}