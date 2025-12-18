package de.mm20.launcher2.nextcloud

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import de.mm20.launcher2.serialization.Json
import de.mm20.launcher2.webdav.WebDavApi
import de.mm20.launcher2.webdav.WebDavFile
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.basicAuth
import io.ktor.client.request.delete
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.http.path
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.File
import java.io.IOException

class NextcloudApiHelper(val context: Context) {


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
                "nextcloud",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: IOException) {
            if (!catchErrors) throw e
            File(context.filesDir, "../shared_prefs/nextcloud.xml").delete()
            return createPreferences(false)
        }
    }

    fun getLoginIntent(): Intent {
        return Intent(context, LoginActivity::class.java)
    }

    fun login(activity: Activity) {
        activity.startActivity(getLoginIntent())
    }


    /**
     * Perform a POST request to the /index.php/login/v2 endpoint of the given Nextcloud server
     * and return the response. Returns null if an error occurs.
     */
    internal suspend fun startLoginFlow(serverUrl: String): LoginFlowResponse? {
        val response = try {
            httpClient.post {
                url {
                    takeFrom(serverUrl)
                    path("index.php", "login", "v2")
                }
                header(HttpHeaders.UserAgent, context.getString(R.string.app_name))
            }
        } catch (e: IOException) {
            Log.e("NextcloudApiHelper", "HTTP error", e)
            null
        }
        if (response?.status != HttpStatusCode.OK) {
            Log.e(
                "NextcloudApiHelper",
                "Invalid response: ${response?.status} ${response?.bodyAsText()}"
            )
            return null
        }

        return try {
            response.body<LoginFlowResponse>()
        } catch (e: SerializationException) {
            Log.e("NextcloudApiHelper", "Invalid response body", e)
            null
        }
    }

    internal suspend fun pollLoginFlow(loginFlow: LoginFlowResponse): LoginPollResponse? {
        val response = try {
            httpClient.submitForm(
                url = loginFlow.poll.endpoint,
                formParameters = parameters {
                    append("token", loginFlow.poll.token)
                }
            )
        } catch (e: IOException) {
            Log.e("NextcloudApiHelper", "HTTP error", e)
            null
        }
        if (response?.status != HttpStatusCode.OK) {
            Log.e(
                "NextcloudApiHelper",
                "Invalid response: ${response?.status} ${response?.bodyAsText()}"
            )
            return null
        }

        return try {
            response.body<LoginPollResponse>()
        } catch (e: SerializationException) {
            Log.e("NextcloudApiHelper", "Invalid response body", e)
            null
        }
    }

    suspend fun getLoggedInUser(): NcUser? {
        val server = getServer()
        val username = getUserName()
        val token = getToken()

        if (server == null || username == null || token == null) {
            return null
        }

        val displayName = getDisplayName() ?: return null

        return NcUser(
            displayName,
            username
        )
    }

    /**
     * Returns the user's display name or user name if the user is logged in and their token has
     * not been revoked,
     * returns null if they are not logged in.
     */
    private suspend fun getDisplayName(): String? {

        val displayname = preferences.getString("displayname", null)
        if (displayname != null) {
            return displayname
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
            Log.e("NextcloudApiHelper", "HTTP error", e)
            return getUserName()
        }

        if (response.status != HttpStatusCode.OK) {
            logout()
            return null
        }
        val body = try {
            response.body<UserReponse>()
        } catch (e: SerializationException) {
            Log.e("NextcloudApiHelper", "Invalid response body", e)
            return getUserName()
        }

        return withContext(Dispatchers.IO) {
            val name = body.ocs.data.displayName

            preferences.edit {
                putString("displayname", name)
            }

            return@withContext name
                ?: getUserName()
        }
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

    suspend fun logout() {
        val server = getServer()
        val username = getUserName()
        val token = getToken()
        if (server == null || username == null || token == null) return
        withContext(Dispatchers.IO) {
            try {
                httpClient.delete {
                    url {
                        takeFrom(server)
                        path("ocs", "v2.php", "core", "apppassword")
                    }
                    header("OCS-APIREQUEST", "true")
                }
            } catch (e: IOException) {
                Log.e("NextcloudApiHelper", "Error during Nextcloud logout", e)
            }
        }
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
        suspend fun search(query: String): List<WebDavFile> {
            val server = getServer() ?: return emptyList()
            val username = getUserName() ?: return emptyList()
            return WebDavApi.search("$server/remote.php/dav/", username, query, httpClient)
        }
    }
}