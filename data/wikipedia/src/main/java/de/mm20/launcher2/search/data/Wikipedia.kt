package de.mm20.launcher2.search.data

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import de.mm20.launcher2.icons.ColorLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TintedIconLayer
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.wikipedia.R

data class Wikipedia(
    override val label: String,
    val id: Long,
    val text: String,
    val image: String?,
    val wikipediaUrl: String,
    override val labelOverride: String? = null,
) : SavableSearchable {

    override val domain: String = Domain

    override val preferDetailsOverLaunch: Boolean = false

    override fun overrideLabel(label: String): Wikipedia {
        return this.copy(labelOverride = label)
    }

    override val key = "$domain://$wikipediaUrl:$id"

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

    private fun getLaunchIntent(): Intent {
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

    override fun launch(context: Context, options: Bundle?): Boolean {
        return context.tryStartActivity(getLaunchIntent(), options)
    }

    companion object {
        const val Domain = "wikipedia"
    }
}