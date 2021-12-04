package de.mm20.launcher2.search.data

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.util.Log
import android.webkit.URLUtil
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toColorInt
import androidx.palette.graphics.Palette
import com.bumptech.glide.Glide
import de.mm20.launcher2.graphics.TextDrawable
import de.mm20.launcher2.helper.NetworkUtils
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.ktx.jsonObjectOf
import de.mm20.launcher2.ktx.sp
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.preferences.WebsiteProtocols
import de.mm20.launcher2.websites.R
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.UncheckedIOException
import java.io.IOException
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL
import java.util.concurrent.ExecutionException

class Website(
        override val label: String,
        val url: String,
        val description: String,
        val image: String,
        val favicon: String,
        val color: Int
) : Searchable() {

    override val key = "web://$url"
    override suspend fun loadIconAsync(context: Context, size: Int): LauncherIcon? {
        if (favicon.isEmpty()) return null
        try {
            val icon = Glide.with(context)
                    .asDrawable()
                    .load(favicon)
                    .submit()
                    .get()
            val color = if (color != 0) color else {
                Palette
                        .from(icon.toBitmap())
                        .generate()
                        .getLightMutedColor(Color.WHITE)
            }
            return LauncherIcon(
                    foreground = icon,
                    background = ColorDrawable(color),
                    foregroundScale = 0.7f
            )
        } catch (e: ExecutionException) {
            return null
        }

    }

    override fun getPlaceholderIcon(context: Context): LauncherIcon {
        val drawable = if (label.isNotEmpty()) {
            TextDrawable(label[0].toString(), typeface = Typeface.DEFAULT_BOLD, fontSize = 40 * context.sp)
        } else context.getDrawable(R.drawable.ic_website)!!
        return LauncherIcon(
                foreground = drawable,
                background = ColorDrawable(if (color != 0) color else Color.LTGRAY),
                foregroundScale = 1f
        )
    }

    override fun getLaunchIntent(context: Context): Intent? {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(url)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        return intent
    }

    companion object {
        fun search(context: Context, query: String, client: OkHttpClient): Website? {
            var url = query
            val prefs = LauncherPreferences.instance
            if (!prefs.searchWebsite ||
                    NetworkUtils.isOffline(context, prefs.searchWebsitesMobileData)) return null
            val protocol = if (LauncherPreferences.instance.searchWebsitesProtocol == WebsiteProtocols.HTTPS) "https://" else "http://"
            if (!query.startsWith("https://") && !query.startsWith("http://")) url = "$protocol$query"
            if (!URLUtil.isValidUrl(url)) return null
            try {
                val request = Request.Builder()
                        .url(URL(url))
                        .get()
                        .tag("onlinesearch")
                        .build()
                val response = client.newCall(request).execute()
                url = response.request.url.toString()
                val body = response.body?.string() ?: return null
                val doc = Jsoup.parse(body)
                var title = doc.select("meta[property=og:title]").attr("content")
                if (title.isBlank()) title = doc.title()
                if (title.isBlank()) title = url
                var description = doc.select("meta[property=og:description]").attr("content")
                if (description.isBlank()) description = doc.select("meta[name=description]").attr("content")
                val color = try {
                    val colorString = doc.select("meta[name=theme-color]").attr("content")
                    if (colorString.isNotEmpty()) colorString.toColorInt()
                    else 0
                } catch (e: IllegalArgumentException) {
                    0
                }
                var image = doc.select("meta[property=og:image]").attr("content")
                var favicon = doc.select("link[rel=apple-touch-icon]").attr("href")
                if (favicon.isBlank()) favicon = doc.head().select("meta[itemprop=image]").attr("content")
                if (favicon.isBlank()) favicon = doc.select("link[rel=icon]").attr("href")
                if (favicon.isBlank()) favicon = doc.head().select("link[href~=.*\\.(ico|png)]").attr("href")
                if (!favicon.isBlank()) favicon = resolve(response.request.url, favicon)
                if (!image.isBlank()) image = resolve(response.request.url, image)
                return Website(
                        label = title,
                        url = url,
                        description = description,
                        image = image,
                        favicon = favicon,
                        color = color)
            } catch (e: IOException) {
                //Ignore. Not a HTML page or no connection. No result for this query
            } catch (e: UncheckedIOException) {
            } catch (e: URISyntaxException) {
            } catch (e: RuntimeException) {
            } catch (e: IllegalArgumentException) {
            }
            return null
        }

        private fun resolve(url: HttpUrl, link: String): String {
            /*if(link.startsWith("http://") || link.startsWith("https://")) return link
            if(link.startsWith("//")) return "${urlTemplate.scheme()}:$link"
            if(link.startsWith("/")) return "${urlTemplate.scheme()}:$link"*/
            return try {
                URL(url.toUrl(), link).toString()
            } catch (e: MalformedURLException) {
                ""
            }
        }
    }
}