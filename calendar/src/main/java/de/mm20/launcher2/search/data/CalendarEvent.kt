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

    companion object: KoinComponent {
        fun search(
            context: Context,
            query: String,
            intervalStart: Long,
            intervalEnd: Long,
            limit: Int = 10,
            hideAllDayEvents: Boolean = false,
            unselectedCalendars: List<Long> = emptyList(),
            hiddenEvents: List<Long> = emptyList()
        ): List<CalendarEvent> {
            val permissionsManager: PermissionsManager = get()
            val results = mutableListOf<CalendarEvent>()
            if (!query.isEmpty() && query.length < 3) return results
            if (!LauncherPreferences.instance.searchCalendars) return listOf()
            if (!permissionsManager.checkPermission(PermissionGroup.Calendar)) {
                return emptyList()
            }
            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, intervalStart)
            ContentUris.appendId(builder, intervalEnd)
            val uri = builder.build()
            val projection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.DISPLAY_COLOR,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.CALENDAR_ID,
                CalendarContract.Instances.DESCRIPTION
            )
            val selection = mutableListOf<String>()
            if (query.isNotEmpty()) selection.add("${CalendarContract.Instances.TITLE} LIKE ?")
            if (hiddenEvents.isNotEmpty()) selection.add("${CalendarContract.Instances.EVENT_ID} NOT IN (${hiddenEvents.joinToString()})")
            if (unselectedCalendars.isNotEmpty()) selection.add("${CalendarContract.Instances.CALENDAR_ID} NOT IN (${unselectedCalendars.joinToString()})")
            if (hideAllDayEvents) selection.add("${CalendarContract.Instances.ALL_DAY} = 0")
            val selArgs = if (query.isBlank()) null else arrayOf("%$query%")
            val sort =
                "${CalendarContract.Instances.BEGIN} ASC" + if (limit > -1) " LIMIT $limit" else ""
            val cursor = context.contentResolver.query(
                uri,
                projection,
                selection.joinToString(separator = " AND "),
                selArgs,
                sort
            )
                ?: return mutableListOf()
            val proj = arrayOf(
                CalendarContract.Attendees.EVENT_ID,
                CalendarContract.Attendees.ATTENDEE_NAME,
                CalendarContract.Attendees.ATTENDEE_EMAIL
            )
            val s = "${CalendarContract.Attendees.ATTENDEE_NAME} COLLATE NOCASE ASC"
            while (cursor.moveToNext()) {
                val sel = "${CalendarContract.Attendees.EVENT_ID} = ${cursor.getLong(0)}"
                val cur = context.contentResolver.query(
                    CalendarContract.Attendees.CONTENT_URI,
                    proj, sel, null, s
                ) ?: return mutableListOf()
                val attendees = mutableListOf<String>()
                while (cur.moveToNext()) {
                    attendees.add(cur.getString(1).takeUnless { it.isNullOrBlank() }
                        ?: cur.getString(2))
                }
                cur.close()
                val allday = cursor.getInt(4) > 0
                val begin = cursor.getLong(2)

                val tzOffset = if (allday) {
                    Calendar.getInstance().timeZone.getOffset(begin)
                } else {
                    0
                }
                val event = CalendarEvent(
                    label = cursor.getString(1) ?: "",
                    id = cursor.getLong(0),
                    color = cursor.getInt(5),
                    startTime = begin - tzOffset,
                    endTime = cursor.getLong(3) - tzOffset - if (allday) 1 else 0,
                    allDay = allday,
                    location = cursor.getString(6) ?: "",
                    attendees = attendees,
                    description = cursor.getStringOrNull(8)
                        ?: "",
                    calendar = cursor.getLong(7)
                )
                results.add(event)
            }
            cursor.close()

            return results
        }

        fun getCalendars(context: Context): List<UserCalendar> {
            val calendars = mutableListOf<UserCalendar>()
            val uri = CalendarContract.Calendars.CONTENT_URI
            val proj = arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.CALENDAR_COLOR,
                CalendarContract.Calendars.VISIBLE,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            )
            if (!context.checkPermission(Manifest.permission.READ_CALENDAR)) return calendars
            val cursor = context.contentResolver.query(uri, proj, null, null, null)
                ?: return emptyList()
            while (cursor.moveToNext()) {
                try {
                    calendars.add(
                        UserCalendar(
                            id = cursor.getLong(0),
                            name = cursor.getString(5) ?: cursor.getString(1) ?: "",
                            owner = cursor.getString(2),
                            color = cursor.getInt(3)
                        )
                    )
                } catch (e: NullPointerException) {
                    continue
                }
            }
            cursor.close()
            calendars.sortBy { it.owner }
            return calendars
        }

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