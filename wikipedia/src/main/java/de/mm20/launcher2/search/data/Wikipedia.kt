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
        val image: String?,
        val wikipediaUrl: String,
) : Searchable() {
    override val key = "wikipedia://$wikipediaUrl:$id"

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
        val uri = "${wikipediaUrl}/wiki?curid=$id"
        intent.intent.data = Uri.parse(uri)
        return intent.intent
    }

    companion object {

    }
}