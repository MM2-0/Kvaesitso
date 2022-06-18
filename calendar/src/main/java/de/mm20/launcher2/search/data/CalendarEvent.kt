package de.mm20.launcher2.search.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.provider.CalendarContract
import de.mm20.launcher2.graphics.TextDrawable
import de.mm20.launcher2.icons.ColorLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TextLayer
import de.mm20.launcher2.ktx.dp
import palettes.TonalPalette
import java.text.SimpleDateFormat

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


    override fun getPlaceholderIcon(context: Context): StaticLauncherIcon {
        val df = SimpleDateFormat("dd")
        return StaticLauncherIcon(
            foregroundLayer = TextLayer(
                text = df.format(startTime),
                color = Color.WHITE
            ),
            backgroundLayer = ColorLayer(getDisplayColor())
        )
    }

    override fun getLaunchIntent(context: Context): Intent {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
        return Intent(Intent.ACTION_VIEW).setData(uri).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun getDisplayColor(): Int {
        val palette = TonalPalette.fromInt(color)
        return palette.tone(70)
    }
}

data class UserCalendar(
    val id: Long,
    val name: String,
    val owner: String,
    val color: Int
)