package de.mm20.launcher2.search.data

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import androidx.core.content.ContextCompat
import coil.imageLoader
import coil.request.ImageRequest
import de.mm20.launcher2.icons.*
import de.mm20.launcher2.websites.R
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
    override suspend fun loadIcon(
        context: Context,
        size: Int,
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

    override fun getLaunchIntent(context: Context): Intent? {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(url)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        return intent
    }
}