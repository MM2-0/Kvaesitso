package de.mm20.launcher2.search.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import androidx.core.database.getStringOrNull
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import de.mm20.launcher2.calendar.R
import de.mm20.launcher2.graphics.TextDrawable
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.ktx.checkPermission
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.LauncherPreferences
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
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
        val df = SimpleDateFormat("d")
        val day = df.format(startTime)
        df.applyPattern("MMM")
        val month = df.format(startTime)
        val fgLayers = arrayOf(
            TextDrawable(
                day,
                color = Color.WHITE,
                fontSize = 40 * context.dp,
                typeface = Typeface.DEFAULT_BOLD
            ),
            TextDrawable(
                month,
                color = Color.WHITE,
                fontSize = 26 * context.dp,
                typeface = Typeface.DEFAULT_BOLD
            )
        )
        val foreground = LayerDrawable(fgLayers)
        foreground.setLayerInset(0, 0, 0, 0, (26 * context.dp).toInt())
        foreground.setLayerInset(1, 0, (40 * context.dp).toInt(), 0, 0)
        val background = ColorDrawable(getDisplayColor(context, color))
        return LauncherIcon(
            foreground = foreground,
            background = background,
            foregroundScale = 0.74f
        )
    }

    override fun getLaunchIntent(context: Context): Intent? {
        return null
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