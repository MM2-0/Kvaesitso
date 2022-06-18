package de.mm20.launcher2.search.data

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import de.mm20.launcher2.icons.ColorLayer
import de.mm20.launcher2.icons.StaticIconLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.wikipedia.R

class Wikipedia(
    override val label: String,
    val id: Long,
    val text: String,
    val image: String?,
    val wikipediaUrl: String,
) : Searchable() {
    override val key = "wikipedia://$wikipediaUrl:$id"

    override fun getPlaceholderIcon(context: Context): StaticLauncherIcon {
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(
                icon = ContextCompat.getDrawable(context, R.drawable.ic_wikipedia)!!,
                scale = 1f
            ),
            backgroundLayer = ColorLayer(0xFFF0F0F0.toInt())
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