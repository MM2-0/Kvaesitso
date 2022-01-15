package de.mm20.launcher2.search.data

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import com.bumptech.glide.Glide
import de.mm20.launcher2.graphics.TextDrawable
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.ktx.sp
import de.mm20.launcher2.websites.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    override suspend fun loadIcon(context: Context, size: Int): LauncherIcon? {
        if (favicon.isEmpty()) return null
        try {
            return withContext(Dispatchers.IO) {
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
                LauncherIcon(
                    foreground = icon,
                    background = ColorDrawable(color),
                    foregroundScale = 0.7f
                )
            }
        } catch (e: ExecutionException) {
            return null
        }

    }

    override fun getPlaceholderIcon(context: Context): LauncherIcon {
        val drawable = if (label.isNotEmpty()) {
            TextDrawable(
                label[0].toString(),
                typeface = Typeface.DEFAULT_BOLD,
                fontSize = 40 * context.sp
            )
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
}