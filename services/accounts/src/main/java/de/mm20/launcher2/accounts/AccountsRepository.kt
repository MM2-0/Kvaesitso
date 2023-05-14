package de.mm20.launcher2.accounts

import android.app.Activity
import android.content.Context
import de.mm20.launcher2.gservices.GoogleApiHelper
import de.mm20.launcher2.nextcloud.NextcloudApiHelper
import de.mm20.launcher2.owncloud.OwncloudClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

interface AccountsRepository {
    fun signin(context: Activity, accountType: AccountType)
    fun signout(accountType: AccountType)

    /**
     * Whether support for this account type is enabled in this build
     */
    fun isSupported(accountType: AccountType): Boolean

    suspend fun getCurrentlySignedInAccount(accountType: AccountType): Account?
}

internal class AccountsRepositoryImpl(
    context: Context
) : AccountsRepository {
    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    private val googleApiHelper = GoogleApiHelper.getInstance(context)
    private val nextcloudApiHelper = NextcloudApiHelper(context)
    private val owncloudApiHelper = OwncloudClient(context)

    override fun signin(context: Activity, accountType: AccountType) {
        when (accountType) {
            AccountType.Google -> {
                scope.launch {
                    googleApiHelper.login(context)
                }
            }
            AccountType.Nextcloud ->
                scope.launch {
                    nextcloudApiHelper.login(context)
                }
            AccountType.Owncloud ->
                scope.launch {
                    owncloudApiHelper.login(context, 0)
                }
        }
    }

    override fun signout(accountType: AccountType) {
        when (accountType) {
            AccountType.Google -> {
                googleApiHelper.logout()
            }
            AccountType.Nextcloud -> {
                scope.launch {
                    nextcloudApiHelper.logout()
                }
            }
            AccountType.Owncloud -> {
                owncloudApiHelper.logout()
            }
        }
    }

    override fun isSupported(accountType: AccountType): Boolean {
        return when (accountType) {
            AccountType.Google -> googleApiHelper.isAvailable()
            AccountType.Nextcloud -> true
            AccountType.Owncloud -> true
        }
    }

    override suspend fun getCurrentlySignedInAccount(accountType: AccountType): Account? {
        return when (accountType) {
            AccountType.Google -> {
                getGoogleAccount()
            }
            AccountType.Nextcloud -> {
                getNextcloudAccount()
            }
            AccountType.Owncloud -> {
                getOwncloudAccount()
            }
        }
    }

    private suspend fun getGoogleAccount(): Account? {
        return googleApiHelper.getAccount()?.let {
            Account(it.name, AccountType.Google)
        }
    }

    private suspend fun getMicrosoftAccount(): Account? {
        return null
        /*return msGraphApiHelper.getUser()?.let {
            Account(it.name, AccountType.Microsoft)
        }*/
    }

    private suspend fun getNextcloudAccount(): Account? {
        return nextcloudApiHelper.getLoggedInUser()?.let {
            Account(it.displayName, AccountType.Nextcloud)
        }
    }

    private suspend fun getOwncloudAccount(): Account? {
        return owncloudApiHelper.getLoggedInUser()?.let {
            Account(it.displayName, AccountType.Owncloud)
        }
    }

}