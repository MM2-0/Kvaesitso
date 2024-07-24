package de.mm20.launcher2.websites

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import coil.imageLoader
import coil.request.ImageRequest
import de.mm20.launcher2.icons.*
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.search.Website
import java.util.concurrent.ExecutionException

internal data class WebsiteImpl(
    override val label: String,
    override val url: String,
    override val description: String?,
    override val imageUrl: String?,
    override val faviconUrl: String?,
    override val color: Int?,
    override val labelOverride: String? = null,
) : Website {

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
        if (faviconUrl == null) return null
        try {
            val request = ImageRequest.Builder(context)
                .data(faviconUrl)
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
        val color = color ?: 0xFFF76F8E.toInt()
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

    override val canShare: Boolean = true

    override fun share(context: Context) {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.putExtra(
            Intent.EXTRA_TEXT,
            "${label}\n\n${description}\n\n${url}"
        )
        shareIntent.type = "text/plain"
        context.startActivity(Intent.createChooser(shareIntent, null))
    }

    override fun getSerializer(): SearchableSerializer {
        return WebsiteSerializer()
    }

    companion object {
        const val Domain = "web"
    }
}