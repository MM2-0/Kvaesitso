package de.mm20.launcher2.search.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import de.mm20.launcher2.calendar.R
import de.mm20.launcher2.graphics.TextDrawable
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.ktx.dp
import java.text.SimpleDateFormat
import java.util.*

class CalendarEvent(
    override val label: String,
    val id: Long,
    val color: Int,
    val startTime: Long,
    val endTime: Long,
    val allDay: Boolean,
    val location: String,
    val attendees: List<String>,
    val description: String,
    val calendar: Long
) : Searchable() {

    override val key: String
        get() = "calendar://$id"


    override fun getPlaceholderIcon(context: Context): LauncherIcon {
        val df = SimpleDateFormat("dd")
        val s = (48 * context.dp).toInt()
        val foreground = TextDrawable(
            df.format(startTime),
            color = Color.WHITE,
            fontSize = 24 * context.dp,
            typeface = Typeface.DEFAULT_BOLD,
            height = s
        )
        val background = ColorDrawable(getDisplayColor(context, color))
        return LauncherIcon(
            foreground = foreground,
            background = background,
            foregroundScale = 0.74f
        )
    }

    override fun getLaunchIntent(context: Context): Intent {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
        return Intent(Intent.ACTION_VIEW).setData(uri).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    companion object {
        fun getDisplayColor(context: Context, color: Int): Int {
            val hsl = FloatArray(3).let {
                ColorUtils.RGBToHSL(color.red, color.green, color.blue, it)
                it
            }
            return if (context.resources.getBoolean(R.bool.is_dark_theme)) {
                if (ColorUtils.calculateContrast(
                        ContextCompat.getColor(
                            context,
                            R.color.calendar_foreground_color
                        ), color
                    ) < 2.5 || true
                ) {
                    if (color.red == color.green && color.red == color.blue) {
                        val level = 0xFF - ((0xFF - color.red) * 0.7f).toInt()
                        Color.rgb(level, level, level)
                    } else {
                        hsl[2] = hsl[2] + (1 - hsl[2]) * 0.2f
                        hsl[1] = 1 - (1 - hsl[1]) * 0.9f
                        ColorUtils.HSLToColor(hsl)
                    }
                } else return color
            } else {
                if (ColorUtils.calculateContrast(
                        ContextCompat.getColor(
                            context,
                            R.color.calendar_foreground_color
                        ), color
                    ) < 1.8
                ) {
                    if (color.red == color.green && color.red == color.blue) {
                        val level = (color.red * 0.7f).toInt()
                        Color.rgb(level, level, level)
                    } else {
                        hsl[2] = (0.5f - hsl[2]) * 0.8f + hsl[2]
                        hsl[1] = 1 - (1 - hsl[1]) * 0.8f
                        ColorUtils.HSLToColor(hsl)
                    }
                } else return color
            }
        }


    }
}

data class UserCalendar(
    val id: Long,
    val name: String,
    val owner: String,
    val color: Int
)