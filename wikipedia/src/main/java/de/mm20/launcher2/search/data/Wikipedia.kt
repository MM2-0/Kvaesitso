package de.mm20.launcher2.search.data

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Spanned
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.text.HtmlCompat
import androidx.core.text.toHtml
import de.mm20.launcher2.wikipedia.R
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.helper.NetworkUtils
import de.mm20.launcher2.preferences.LauncherPreferences
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import kotlin.math.min

class Wikipedia(
        override val label: String,
        val id: Long,
        val text: String,
        val image: String?
) : Searchable() {
    override val key = "wikipedia://$id"

    override suspend fun loadIconAsync(context: Context, size: Int): LauncherIcon? {
        return null
    }

    override fun getPlaceholderIcon(context: Context): LauncherIcon {
        return LauncherIcon(
                foreground = context.getDrawable(R.drawable.ic_wikipedia)!!,
                background = ColorDrawable(0xFFF0F0F0.toInt())
        )
    }

    override fun getLaunchIntent(context: Context): Intent? {
        val intent = CustomTabsIntent
                .Builder()
                .setToolbarColor(Color.BLACK)
                .enableUrlBarHiding()
                .setShowTitle(true)
                .build()
        val uri = "${context.getString(R.string.wikipedia_url)}/wiki?curid=$id"
        intent.intent.data = Uri.parse(uri)
        return intent.intent
    }

    companion object {

        fun search(context: Context, query: String, client: OkHttpClient): Wikipedia? {
            mutableListOf<Searchable>()
            if (query.length < 4) return null
            val prefs = LauncherPreferences.instance
            if (!prefs.searchWikipedia ||
                    NetworkUtils.isOffline(context, prefs.searchWikipediaMobileData)) return null
            val url = (context.getString(R.string.wikipedia_url) + "/w/api.php?action=query&"
                    + "generator=search&redirects=true&gsrlimit=1&prop=extracts&format=json&gsrsearch="
                    + query)
            val request = Request.Builder()
                    .url(url)
                    .tag("onlinesearch")
                    .build()
            try {
                val response = client.newCall(request).execute()
                val json = JSONObject(response.body?.string() ?: return null)
                val pages = json.getJSONObject("query")
                        .getJSONObject("pages")
                val it = pages.keys()
                if (it.hasNext()) {
                    val key = it.next()
                    val id = pages.getJSONObject(key).getLong("pageid")
                    val title = pages.getJSONObject(key).getString("title")
                    val text = pages.getJSONObject(key).getString("extract").also{
                        it.substring(0, min(500, it.length)) + "…"
                    }
                    val image = getArticleImage(context, id, client)
                    return Wikipedia(
                            label = title,
                            text = text,
                            id = id,
                            image = image
                    )
                }
            } catch (e: IOException) {
            } catch (e: JSONException) {
            }

            return null
        }

        private fun getArticleImage(context: Context, id: Long, client: OkHttpClient): String? {
            if (!LauncherPreferences.instance.searchWikipediaPictures) return null
            val width = context.resources.displayMetrics.widthPixels / 2
            val url = (context.getString(R.string.wikipedia_url) + "/w/api.php?action=query&"
                    + "prop=pageimages&format=json&pageids=$id&pithumbsize=$width")
            val request = Request.Builder()
                    .url(url)
                    .tag("onlinesearch")
                    .build()
            val response = client.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: return null)
            return json.getJSONObject("query")
                    .getJSONObject("pages")
                    .getJSONObject(id.toString())
                    .optJSONObject("thumbnail")
                    ?.getString("source")
        }
    }
}