package de.mm20.launcher2.websites

import android.content.Context
import android.webkit.URLUtil
import androidx.core.graphics.toColorInt
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.search.data.Website
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.UncheckedIOException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.IOException
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL
import java.util.concurrent.TimeUnit

interface WebsiteRepository {
    fun search(query: String): Flow<Website?>
}

internal class WebsiteRepositoryImpl(val context: Context) : WebsiteRepository, KoinComponent {

    private val dataStore: LauncherDataStore by inject()

    private val httpClient = OkHttpClient
        .Builder()
        .connectTimeout(200, TimeUnit.MILLISECONDS)
        .readTimeout(3000, TimeUnit.MILLISECONDS)
        .writeTimeout(1000, TimeUnit.MILLISECONDS)
        .build()

    override fun search(query: String): Flow<Website?> = channelFlow {
        send(null)
        withContext(Dispatchers.IO) {
            httpClient.dispatcher.cancelAll()
        }
        if (query.isBlank()) return@channelFlow

        dataStore.data.map { it.websiteSearch.enabled }.collectLatest {
            if(it) {
                val website = queryWebsite(query)
                send(website)
            } else {
                send(null)
            }
        }

    }

    private suspend fun queryWebsite(query: String): Website? {
        val result = withContext(Dispatchers.IO) {
            var url = query
            val protocol = "https://"
            if (!query.startsWith("https://") && !query.startsWith("http://")) url =
                "$protocol$query"
            if (!URLUtil.isValidUrl(url)) return@withContext null
            try {
                val request = Request.Builder()
                    .url(URL(url))
                    .get()
                    .tag("onlinesearch")
                    .build()
                val response = httpClient.newCall(request).execute()
                url = response.request.url.toString()
                val body = response.body?.string() ?: return@withContext null
                val doc = Jsoup.parse(body)
                var title = doc.select("meta[property=og:title]").attr("content")
                if (title.isBlank()) title = doc.title()
                if (title.isBlank()) title = url
                var description = doc.select("meta[property=og:description]").attr("content")
                if (description.isBlank()) description =
                    doc.select("meta[name=description]").attr("content")
                val color = try {
                    val colorString = doc.select("meta[name=theme-color]").attr("content")
                    if (colorString.isNotEmpty()) colorString.toColorInt()
                    else 0
                } catch (e: IllegalArgumentException) {
                    0
                }
                var image = doc.select("meta[property=og:image]").attr("content")
                var favicon = doc.select("link[rel=apple-touch-icon]").attr("href")
                if (favicon.isBlank()) favicon =
                    doc.head().select("meta[itemprop=image]").attr("content")
                if (favicon.isBlank()) favicon = doc.select("link[rel=icon]").attr("href")
                if (favicon.isBlank()) favicon =
                    doc.head().select("link[href~=.*\\.(ico|png)]").attr("href")
                if (!favicon.isBlank()) favicon = resolveUrl(response.request.url, favicon)
                if (!image.isBlank()) image = resolveUrl(response.request.url, image)
                return@withContext Website(
                    label = title,
                    url = url,
                    description = description,
                    image = image,
                    favicon = favicon,
                    color = color
                )
            } catch (e: IOException) {
                //Ignore. Not a HTML page or no connection. No result for this query
            } catch (e: UncheckedIOException) {
            } catch (e: URISyntaxException) {
            } catch (e: RuntimeException) {
            } catch (e: IllegalArgumentException) {
            }
            return@withContext null
        }
        return result
    }

    private fun resolveUrl(url: HttpUrl, link: String): String {
        return try {
            URL(url.toUrl(), link).toString()
        } catch (e: MalformedURLException) {
            ""
        }
    }
}