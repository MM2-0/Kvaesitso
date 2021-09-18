package de.mm20.launcher2.owncloud

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.security.crypto.MasterKeys
import de.mm20.launcher2.webdav.WebDavApi
import de.mm20.launcher2.webdav.WebDavFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.io.File
import java.io.IOException

class OwncloudClient(val context: Context) {


    private val httpClient by lazy {
        OkHttpClient.Builder()
                .authenticator(object : Authenticator {
                    override fun authenticate(route: Route?, response: Response): Request? {
                        if (response.priorResponse?.priorResponse != null) return null
                        return response.request
                                .newBuilder()
                                .addHeader("Authorization", getAuthorization() ?: return null)
                                .build()
                    }

                })
                .build()
    }

    private val preferences by lazy {
        createPreferences()
    }

    private fun createPreferences(catchErrors: Boolean = true): SharedPreferences {
        try {
            val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            return EncryptedSharedPreferences.create(
                context,
                "owncloud",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: IOException) {
            if (!catchErrors) throw e
            File(context.filesDir, "../shared_prefs/owncloud.xml").delete()
            return createPreferences(false)
        }
    }

    fun login(activity: Activity, requestCode: Int) {
        activity.startActivityForResult(Intent(context, LoginActivity::class.java), requestCode)
    }


    suspend fun checkOwncloudInstallation(url: String): Boolean {
        var url = url
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        val request = Request.Builder()
                .url("$url/remote.php/webdav")
                .build()
        val response = runCatching {
            withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute()
            }
        }.getOrNull() ?: return false
        return response.code == 200 || response.code == 401
    }

    suspend fun getLoggedInUser(): OcUser? {
        val server = getServer()
        val username = getUserName()
        val token = getToken()

        if (server == null || username == null || token == null) {
            return null
        }

        val displayName = getDisplayName() ?: return null

        return OcUser(
                displayName,
                username
        )
    }

    /**
     * Returns the user's display name or user name if the user is logged in
     * returns null if they are not logged in.
     */
    private suspend fun getDisplayName(): String? {

        if (preferences.getString("displayname", null) != null) {
            return preferences.getString("displayname", null)
        }

        val server = getServer() ?: return null

        val request = Request.Builder()
                .addHeader("OCS-APIRequest", "true")
                .url("$server/ocs/v1.php/cloud/user?format=json")
                .build()

        val response = runCatching {
            withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute()
            }
        }.getOrNull() ?: return getUserName()

        if (response.code != 200) {
            logout()
            return null
        }
        val body = response.body ?: return getUserName()

        return withContext(Dispatchers.IO) {
            val json = JSONObject(body.string())

            return@withContext json.optJSONObject("ocs")
                    ?.optJSONObject("data")
                    ?.optString("display-name")
                    ?: getUserName()
        }
    }

    private fun getAuthorization(): String? {
        return Credentials.basic(getUserName() ?: return null, getToken() ?: return null)
    }

    fun getServer(): String? {
        return preferences.getString("server", null)
    }

    fun getUserName(): String? {
        return preferences.getString("username", null)
    }

    fun getUserDisplayName(): String? {
        return preferences.getString("displayname", getUserName())
    }

    private fun getToken(): String? {
        return preferences.getString("token", null)
    }

    internal fun setServer(server: String, username: String, token: String) {
        preferences.edit {
            putString("server", server)
            putString("username", username)
            putString("token", token)
        }
    }

    fun logout() {
        preferences.edit {
            putString("server", null)
            putString("username", null)
            putString("token", null)
            putString("displayname", null)
        }
    }

    suspend fun tryLogin(url: String, username: String, pw: String): Boolean {

        setServer(url, username, pw)

        val displayName = getDisplayName()
        preferences.edit {
            putString("displayname", displayName)
        }

        return displayName != null
    }

    val files by lazy {
        FilesApi()
    }

    inner class FilesApi internal constructor() {
        suspend fun query(query: String): List<WebDavFile> {
            val server = getServer() ?: return emptyList()
            val username = getUserName() ?: return emptyList()
            return WebDavApi.searchReport("$server/remote.php/dav/", username, query, httpClient)
        }
    }
}