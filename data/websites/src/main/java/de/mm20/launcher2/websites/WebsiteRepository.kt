package de.mm20.launcher2.websites

import android.content.Context
import android.webkit.URLUtil
import androidx.core.graphics.toColorInt
import de.mm20.launcher2.ktx.splitAtFirst
import de.mm20.launcher2.preferences.search.WebsiteSearchSettings
import de.mm20.launcher2.search.SearchableRepository
import de.mm20.launcher2.search.Website
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.UncheckedIOException
import java.io.IOException
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL
import java.util.concurrent.TimeUnit

private val DomainRegex by lazy {
    Regex("""^(?:(?!-)[-\w]+(?<!-)\.)+[^\d\W_]+$""", RegexOption.IGNORE_CASE)
}

internal class WebsiteRepository(
    val context: Context,
    val settings: WebsiteSearchSettings,
) : SearchableRepository<Website> {

    private val httpClient = OkHttpClient
        .Builder()
        .connectTimeout(200, TimeUnit.MILLISECONDS)
        .readTimeout(3000, TimeUnit.MILLISECONDS)
        .writeTimeout(1000, TimeUnit.MILLISECONDS)
        .build()

    override fun search(query: String, allowNetwork: Boolean): Flow<ImmutableList<Website>> {
        if (!allowNetwork) return flowOf(persistentListOf())
        return settings.enabled.transformLatest { enabled ->
            emit(persistentListOf())
            withContext(Dispatchers.IO) {
                httpClient.dispatcher.cancelAll()
            }
            if (!enabled || query.isBlank()) return@transformLatest

            val website = queryWebsite(query)
            website?.let {
                emit(persistentListOf(it))
            }
        }
    }

    private val protocols = listOf("http://", "https://")
    private suspend fun queryWebsite(query: String): Website? {
        val result = withContext(Dispatchers.IO) {

            val query = query.trim()

            var url = when {
                protocols.any { query.startsWith(it) } -> {
                    if (query.substringAfter("://").isEmpty()) return@withContext null
                    query // expect user knows what they are doing
                }

                // deny any other protocol & malformed queries
                query.contains("://") -> return@withContext null

                // "hello. world"
                query.any { it.isWhitespace() } -> return@withContext null

                else -> {
                    val (domain, path) = query.splitAtFirst('/')

                    if (!DomainRegex.matches(domain)) return@withContext null

                    "https://$domain${path.takeIf { it.isNotBlank() }?.let { "/$it" }.orEmpty()}"
                }
            }

            if (!URLUtil.isNetworkUrl(url)) return@withContext null

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
                if (favicon.isNotBlank()) favicon = resolveUrl(response.request.url, favicon)
                if (image.isNotBlank()) image = resolveUrl(response.request.url, image)
                return@withContext WebsiteImpl(
                    label = title,
                    url = url,
                    description = description,
                    imageUrl = image,
                    faviconUrl = favicon,
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