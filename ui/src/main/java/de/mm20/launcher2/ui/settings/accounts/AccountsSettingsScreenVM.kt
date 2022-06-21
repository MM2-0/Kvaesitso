package de.mm20.launcher2.ui.settings.accounts

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.accounts.Account
import de.mm20.launcher2.accounts.AccountType
import de.mm20.launcher2.accounts.AccountsRepository
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AccountsSettingsScreenVM : ViewModel(), KoinComponent {
    private val accountsRepository: AccountsRepository by inject()

    val isGoogleAvailable = accountsRepository.isSupported(AccountType.Google)
    val isMicrosoftAvailable = accountsRepository.isSupported(AccountType.Microsoft)

    val googleUser = MutableLiveData<Account?>(null)
    val msUser= MutableLiveData<Account?>(null)
    val nextcloudUser = MutableLiveData<Account?>(null)
    val owncloudUser = MutableLiveData<Account?>(null)

    val loading = MutableLiveData(true)

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