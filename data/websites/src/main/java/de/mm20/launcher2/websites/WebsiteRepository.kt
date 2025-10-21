package de.mm20.launcher2.websites

import android.content.Context
import android.webkit.URLUtil
import androidx.core.graphics.toColorInt
import de.mm20.launcher2.preferences.search.WebsiteSearchSettings
import de.mm20.launcher2.search.SearchableRepository
import de.mm20.launcher2.search.Website
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.Url
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.IOException
import java.io.UncheckedIOException
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL


internal class WebsiteRepository(
    val context: Context,
    val settings: WebsiteSearchSettings,
) : SearchableRepository<Website> {

    private val httpClient by lazy {
        HttpClient {
            install(HttpTimeout) {
                connectTimeoutMillis = 200
                requestTimeoutMillis = 3000
                socketTimeoutMillis = 1000
            }
        }
    }

    override fun search(query: String): Flow<ImmutableList<Website>> {
        return settings.enabled.transformLatest { enabled ->
            emit(persistentListOf())
            if (!enabled || query.isBlank()) return@transformLatest

            val website = queryWebsite(query)
            website?.let {
                emit(persistentListOf(it))
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
                val response = httpClient.get {
                    url(url)
                }
                url = response.request.url.toString()
                val body = response.bodyAsText()
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
                    description = description.takeIf { it.isNotBlank() },
                    imageUrl = image.takeIf { it.isNotBlank() },
                    faviconUrl = favicon.takeIf { it.isNotBlank() },
                    color = color.takeIf { it != 0 }
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

    private fun resolveUrl(url: Url, link: String): String {
        return try {
            URL(URL(url.toString()), link).toString()
        } catch (e: MalformedURLException) {
            ""
        }
    }
}