package de.mm20.launcher2.search.data

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import de.mm20.launcher2.icons.ColorLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TintedIconLayer
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
            foregroundLayer = TintedIconLayer(
                icon = ContextCompat.getDrawable(context, R.drawable.ic_wikipedia)!!,
                scale = 1f,
                color = 0xFFC1C2C4.toInt(),
            ),
            backgroundLayer = ColorLayer(0xFFC1C2C4.toInt())
        )
    }

    override fun getLaunchIntent(context: Context): Intent? {
        val intent = CustomTabsIntent
            .Builder()
            .setToolbarColor(Color.BLACK)
            .enableUrlBarHiding()
            .setShowTitle(true)
            .build()
        val uri = "${wikipediaUrl.padEnd(1, '/')}wiki?curid=$id"
        intent.intent.data = Uri.parse(uri)
        return intent.intent
    }
}