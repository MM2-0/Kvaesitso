package de.mm20.launcher2.owncloud

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.serialization.Json
import de.mm20.launcher2.webdav.WebDavApi
import de.mm20.launcher2.webdav.WebDavFile
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.basicAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import io.ktor.http.path
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerializationException
import java.io.File
import java.io.IOException

class OwncloudClient(val context: Context) {


    private val httpClient by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(Json.Lenient)
            }
            defaultRequest {
                val user = getUserName()
                val token = getToken()
                if (user != null && token != null) {
                    basicAuth(user, token)
                }
            }
        }
    }

    private val preferences by lazy {
        createPreferences()
    }

    private fun createPreferences(catchErrors: Boolean = true): SharedPreferences {
        try {
            val masterKey =
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
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

    fun getLoginIntent(): Intent {
        return Intent(context, LoginActivity::class.java)
    }

    fun login(activity: Activity, requestCode: Int) {
        activity.startActivityForResult(getLoginIntent(), requestCode)
    }


    internal suspend fun checkOwncloudInstallation(url: String): Boolean {
        var url = url
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }

        val response = try {
            httpClient.get {
                url {
                    takeFrom(url)
                    appendPathSegments("remote.php", "webdav")
                }
            }
        } catch (e: Exception) {
            return false
        }

        return response.status == HttpStatusCode.OK || response.status == HttpStatusCode.Unauthorized
    }

    internal suspend fun checkOwncloudCredentials(
        server: String,
        username: String,
        password: String
    ): Boolean {
        val response = try {
            httpClient.get {
                url {
                    takeFrom(server)
                    path("ocs", "v1.php", "cloud", "user")
                    parameter("format", "json")
                }
                basicAuth(username, password)
            }
        } catch (e: IOException) {
            Log.e("OwncloudClient", "HTTP error", e)
            return false
        }

        if (response.status != HttpStatusCode.OK) {
            Log.e("OwncloudClient", "HTTP error: ${response.status}")
            return false
        }

        return true
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
        val response = try {
            httpClient.get {
                url {
                    takeFrom(server)
                    path("ocs", "v1.php", "cloud", "user")
                    parameter("format", "json")
                }
                header("OCS-APIRequest", "true")
            }
        } catch (e: Exception) {
            CrashReporter.logException(e)
            return getUserName()
        }

        if (response.status != HttpStatusCode.OK) {
            logout()
            return null
        }
        val body = try {
            response.body<UserReponse>()
        } catch (e: SerializationException) {
            CrashReporter.logException(e)
            return getUserName()
        }
        return body.ocs.data.displayName ?: getUserName()
    }

    fun getServer(): String? {
        return preferences.getString("server", null)
    }

    fun getUserName(): String? {
        return preferences.getString("username", null)
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