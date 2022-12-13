package de.mm20.launcher2.search.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import coil.imageLoader
import coil.request.ImageRequest
import de.mm20.launcher2.icons.*
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.websites.R
import java.util.concurrent.ExecutionException

data class Website(
    override val label: String,
    val url: String,
    val description: String,
    val image: String,
    val favicon: String,
    val color: Int,
    override val labelOverride: String? = null,
) : SavableSearchable {

    override val domain: String = Domain

    override val key = "$domain://$url"

    override val preferDetailsOverLaunch: Boolean = false

    override fun overrideLabel(label: String): Website {
        return this.copy(labelOverride = label)
    }

    override suspend fun loadIcon(
        context: Context,
        size: Int,
        themed: Boolean,
    ): LauncherIcon? {
        if (favicon.isEmpty()) return null
        try {
            val request = ImageRequest.Builder(context)
                .data(favicon)
                .size(size)
                .allowHardware(false)
                .build()
            val icon = context.imageLoader.execute(request).drawable ?: return null

            return StaticLauncherIcon(
                foregroundLayer = StaticIconLayer(
                    icon = icon,
                    scale = 1f,
                ),
                backgroundLayer = TransparentLayer
            )
        } catch (e: ExecutionException) {
            return null
        }

    }

    override fun getPlaceholderIcon(context: Context): StaticLauncherIcon {
        val color = if (color != 0) color else 0xFFF76F8E.toInt()
        if (label.isNotBlank()) {
            return StaticLauncherIcon(
                foregroundLayer = TextLayer(text = label[0].toString(), color = color),
                backgroundLayer = ColorLayer(color)
            )
        }

        return StaticLauncherIcon(
            foregroundLayer = TintedIconLayer(
                icon = ContextCompat.getDrawable(context, R.drawable.ic_website)!!,
                scale = 0.5f,
                color = color,
            ),
            backgroundLayer = ColorLayer(color)
        )
    }

    private fun getLaunchIntent(): Intent {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(url)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        return intent
    }

    override fun launch(context: Context, options: Bundle?): Boolean {
        return context.tryStartActivity(getLaunchIntent(), options)
    }

    companion object {
        const val Domain = "web"
    }
}