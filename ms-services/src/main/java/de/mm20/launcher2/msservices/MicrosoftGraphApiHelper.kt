package de.mm20.launcher2.msservices

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.microsoft.graph.core.ClientException
import com.microsoft.graph.core.DefaultClientConfig
import com.microsoft.graph.extensions.GraphServiceClient
import com.microsoft.graph.extensions.IGraphServiceClient
import com.microsoft.graph.http.GraphServiceException
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.exception.MsalException
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.preferences.LauncherPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MicrosoftGraphApiHelper(val context: Context) {

    private var accessToken: String? = null
    private val client: IGraphServiceClient
    private var clientApplication: ISingleAccountPublicClientApplication? = null

    init {
        client = GraphServiceClient
            .Builder()
            .fromConfig(DefaultClientConfig.createWithAuthenticationProvider {
                it.addHeader("Authorization", "Bearer $accessToken")
            })
            .buildClient()
    }

    private suspend fun getClientApplication(): ISingleAccountPublicClientApplication? {
        val resId = getConfigResId()
        if (resId == 0) return null
        if (clientApplication == null) {
            clientApplication = withContext(Dispatchers.IO) {
                PublicClientApplication.createSingleAccountPublicClientApplication(
                    context.applicationContext,
                    resId
                )
            }
        }
        return clientApplication!!
    }

    private suspend fun acquireAccessToken(): Boolean {
        val result = withContext(Dispatchers.IO) {
            try {
                val application = getClientApplication() ?: return@withContext null
                val authority = application.configuration.defaultAuthority.authorityURL.toString()
                application.acquireTokenSilent(SCOPES, authority)
            } catch (e: MsalException) {
                CrashReporter.logException(e)
                logout()
                null
            } catch (e: ClientException) {
                CrashReporter.logException(e)
                null
            }
        }
        accessToken = result?.accessToken
        return result != null
    }

    suspend fun login(context: Activity) {
        val clientApplication = getClientApplication() ?: return
        suspendCoroutine<IAuthenticationResult?> {
            clientApplication.signIn(context, "", SCOPES, object : AuthenticationCallback {
                override fun onSuccess(authenticationResult: IAuthenticationResult?) {
                    accessToken = authenticationResult?.accessToken
                    LauncherPreferences.instance.searchOneDrive = true
                    it.resume(authenticationResult)
                }

                override fun onCancel() {
                    it.resume(null)
                }

                override fun onError(exception: MsalException?) {
                    if (exception != null) Log.e("MM20", exception.stackTraceToString())
                    it.resume(null)
                }

            })
        }
        loadAccountName()
    }

    suspend fun logout() {
        accessToken = null
        LauncherPreferences.instance.searchOneDrive = false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(PREF_ACCOUNT_NAME, null)
        }
        withContext(Dispatchers.IO) { getClientApplication()?.signOut() }
    }

    suspend fun getUser(): MsUser? {
        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(
            PREF_ACCOUNT_NAME,
            null
        ) ?: loadAccountName()


        return name?.let {
            MsUser(name = it)
        }
    }

    private suspend fun loadAccountName(): String? {
        if (!isLoggedIn()) return null
        if (!acquireAccessToken()) return null
        return withContext(Dispatchers.IO) {
            try {
                val user = client.me.buildRequest().get() ?: return@withContext null
                val name = user.displayName ?: user.mail ?: "Microsoft User"
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                    putString(PREF_ACCOUNT_NAME, name)
                }
                return@withContext name
            } catch (e: GraphServiceException) {
                CrashReporter.logException(e)
                logout()
            } catch (e: ClientException) {
                CrashReporter.logException(e)
            }
            null
        }
    }

    suspend fun isLoggedIn(): Boolean {
        return withContext(Dispatchers.IO) {
            getClientApplication()?.currentAccount?.currentAccount != null
        }
    }


    suspend fun queryOneDriveFiles(query: String): List<DriveItem>? {
        if (!acquireAccessToken()) return null
        return try {
            withContext(Dispatchers.IO) {
                client.me.drive.getSearch(
                    URLEncoder.encode(query.replace("'", "''"), "utf8")
                )
                    .buildRequest()
                    .select("id,name,file,size,video,image,webUrl,shared,createdBy")
                    .top(10)
                    .get()
                    ?.currentPage
                    ?.mapNotNull { DriveItem.fromApiDriveItem(it) }
            }
        } catch (e: GraphServiceException) {
            CrashReporter.logException(e)
            null
        } catch (e: ClientException) {
            CrashReporter.logException(e)
            null
        }
    }

    fun isAvailable(): Boolean {
        return getConfigResId() != 0
    }

    private fun getConfigResId(): Int {
        return context.resources.getIdentifier("msal_auth_config", "raw", context.packageName)
    }

    companion object {
        private lateinit var instance: MicrosoftGraphApiHelper

        fun getInstance(context: Context): MicrosoftGraphApiHelper {
            if (!Companion::instance.isInitialized) instance =
                MicrosoftGraphApiHelper(context.applicationContext)
            return instance
        }

        private val SCOPES = arrayOf(
            "User.Read",
            "Files.Read.All"
        )

        const val PREFS = "ms-account"
        const val PREF_ACCOUNT_NAME = "name"
    }

}