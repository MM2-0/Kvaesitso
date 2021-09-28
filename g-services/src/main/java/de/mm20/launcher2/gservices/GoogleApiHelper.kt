package de.mm20.launcher2.gservices

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.*
import androidx.core.content.edit
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.drive.Drive
import com.google.api.services.oauth2.Oauth2
import de.mm20.launcher2.crashreporter.CrashReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException

class GoogleApiHelper private constructor(private val context: Context) {

    val transport by lazy {
        NetHttpTransport()
    }

    suspend fun queryGDriveFiles(query: String): List<DriveFile> {
        val requestInitializer = getRequestInitializer() ?: return emptyList()
        val jsonFactory = GsonFactory.getDefaultInstance()
        return withContext(Dispatchers.IO) {
            try {
                val drive =
                    Drive.Builder(transport, jsonFactory, requestInitializer).build()
                val request = drive.files().list()
                request.q = "name contains '${query.replace("'", "")}'"
                request.pageSize = 10
                request.fields =
                    "files(id, webViewLink, size, name, mimeType, owners, imageMediaMetadata, videoMediaMetadata, folderColorRgb)"
                request.corpora = "user"
                val response = request.execute()
                val files = response.files ?: return@withContext emptyList()
                files.map { DriveFile.fromApiDriveFile(it) }

            } catch (e: IOException) {
                emptyList()
            } catch (e: Error) {
                emptyList()
            }
        }
    }

    private suspend fun getCredential(): Credential? {
        val authFlow = getAuthFlow() ?: return null
        return withContext(Dispatchers.IO) {
            val credential: Credential? = authFlow.loadCredential(USER_ID)
            if ((credential?.expiresInSeconds ?: 0) < 5 * 60) {
                try {
                    if (credential?.refreshToken() == false) return@withContext null
                } catch (e: IOException) {
                    CrashReporter.logException(e)
                }
            }
            return@withContext credential
        }
    }

    private suspend fun getRequestInitializer(): HttpRequestInitializer? {
        val credential = getCredential() ?: return null

        return HttpRequestInitializer { request ->
            credential.initialize(request)
            request?.connectTimeout = 5000
            request?.readTimeout = 10000
        }
    }

    suspend fun getAccount(): GoogleAccount? {

        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(
            PREF_ACCOUNT_NAME,
            null
        ) ?: loadAccountName()


        return name?.let {
            GoogleAccount(name = it)
        }
    }

    fun isAvailable(): Boolean {
        return getConfigResId() != 0
    }

    private fun getConfigResId(): Int {
        return context.resources.getIdentifier("g_services", "raw", context.packageName)
    }


    private fun getAuthFlow(): GoogleAuthorizationCodeFlow? {
        val configResId = getConfigResId()
        if (configResId == 0) return null
        val jsonFactory = GsonFactory.getDefaultInstance()
        return GoogleAuthorizationCodeFlow.Builder(
            NetHttpTransport(),
            jsonFactory,
            GoogleClientSecrets.load(
                jsonFactory,
                context.resources.openRawResource(configResId).reader()
            ),
            SCOPES
        )
            .setCredentialDataStore(
                FileDataStoreFactory(context.filesDir).getDataStore(
                    "google_signin"
                )
            )
            .build()
    }

    private var callback: (() -> Unit)? = null

    suspend fun login(activity: Activity) {
        val authFlow = getAuthFlow() ?: return

        suspendCancellableCoroutine<Unit> {
            val url = authFlow
                .newAuthorizationUrl()
                .setRedirectUri(getRedirectUri())
                .toString()
            val themeColor = 0xFF4285f4.toInt()

            val customTabsIntent = CustomTabsIntent
                .Builder()
                .setDefaultColorSchemeParams(
                    CustomTabColorSchemeParams.Builder()
                        .setToolbarColor(themeColor)
                        .setNavigationBarColor(themeColor)
                        .build()
                )
                .build()

            callingActivity = activity.javaClass
            callback = {
                it.resumeWith(Result.success(Unit))
            }
            it.invokeOnCancellation {
                callback = null
                Log.d("MM20", "Google Signin has been canceled")
            }

            customTabsIntent.intent.flags = Intent.FLAG_ACTIVITY_NO_HISTORY
            customTabsIntent.launchUrl(activity, Uri.parse(url))

        }
    }

    suspend fun finishAuthFlow(activity: Activity, code: String) {
        val authFlow = getAuthFlow() ?: return
        withContext(Dispatchers.IO) {
            val tokenResponse = try {
                authFlow.newTokenRequest(code).setRedirectUri(getRedirectUri()).execute()
            } catch (e: IOException) {
                CrashReporter.logException(e)
                return@withContext
            }
            authFlow.createAndStoreCredential(tokenResponse, USER_ID)
        }
        loadAccountName()
        returnToPreviousActivity(activity)
    }

    fun cancelAuthFlow(activity: Activity) {
        returnToPreviousActivity(activity)
    }

    private fun returnToPreviousActivity(activity: Activity) {
        val intent = Intent(activity, callingActivity)
        callingActivity = null
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        activity.startActivity(intent)
        callback?.invoke()
    }

    private suspend fun loadAccountName(): String? {
        val requestInitializer = getRequestInitializer() ?: return null
        val jsonFactory = GsonFactory.getDefaultInstance()
        val oauth2 = Oauth2.Builder(transport, jsonFactory, requestInitializer).build()
        try {
            val meResponse = withContext(Dispatchers.IO) {
                oauth2.userinfo().v2().me().get().execute()
            }
            if (meResponse != null) {
                val name = meResponse.name
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                    putString(PREF_ACCOUNT_NAME, name)
                }
                return name
            }
        } catch (e: IOException) {
            CrashReporter.logException(e)
        }
        return null
    }


    fun logout() {
        val authFlow = getAuthFlow() ?: return
        authFlow.credentialDataStore.clear()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(PREF_ACCOUNT_NAME, null)
        }
    }

    private fun getRedirectUri(): String {
        return "${context.packageName}:/google-auth-redirect"
    }

    companion object {
        private lateinit var instance: GoogleApiHelper

        fun getInstance(context: Context): GoogleApiHelper {
            if (!::instance.isInitialized) instance = GoogleApiHelper(context.applicationContext)
            return instance
        }

        val SCOPES = setOf("https://www.googleapis.com/auth/drive", "profile")
        const val USER_ID = "google-user"
        const val PREFS = "google-account"
        const val PREF_ACCOUNT_NAME = "name"

        private var callingActivity: Class<Activity>? = null
    }
}