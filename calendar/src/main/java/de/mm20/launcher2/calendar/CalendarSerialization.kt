package de.mm20.launcher2.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import androidx.core.database.getStringOrNull
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.search.data.CalendarEvent
import org.json.JSONObject
import java.util.*

class CalendarEventSerializer: SearchableSerializer {
    override fun serialize(searchable: SavableSearchable): String {
        searchable as CalendarEvent
        val json = JSONObject()
        json.put("id", searchable.id)
        return json.toString()
    }

    override val typePrefix: String
        get() = "calendar"
}

class CalendarEventDeserializer(val context: Context): SearchableDeserializer {
    override fun deserialize(serialized: String): SavableSearchable? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return null
        val json = JSONObject(serialized)
        val id = json.getLong("id")
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, System.currentTimeMillis())
        ContentUris.appendId(builder, System.currentTimeMillis() + 63072000000L)
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
        val selection = CalendarContract.Instances.EVENT_ID + " = ?"
        val selArgs = arrayOf(id.toString())
        val cursor = context.contentResolver.query(uri, projection, selection, selArgs, null)
            ?: return null
        if (cursor.moveToNext()) {
            val title = cursor.getStringOrNull(1) ?: ""
            val begin = cursor.getLong(2)
            val end = cursor.getLong(3)
            val allday = cursor.getInt(4) != 0
            val color = cursor.getInt(5)
            val location = cursor.getStringOrNull(6)
            val calendar = cursor.getLong(7)
            val description = cursor.getStringOrNull(8)
                ?: ""
            cursor.close()
            val proj = arrayOf(
                CalendarContract.Attendees.EVENT_ID,
                CalendarContract.Attendees.ATTENDEE_NAME,
                CalendarContract.Attendees.ATTENDEE_EMAIL
            )
            val sel = "${CalendarContract.Attendees.EVENT_ID} = $id"
            val s = "${CalendarContract.Attendees.ATTENDEE_NAME} COLLATE NOCASE ASC"
            val cur = context.contentResolver.query(
                CalendarContract.Attendees.CONTENT_URI,
                proj, sel, null, s
            ) ?: return null
            val attendees = mutableListOf<String>()
            while (cur.moveToNext()) {
                attendees.add(
                    cur.getStringOrNull(1).takeUnless { it.isNullOrBlank() }
                    ?: cur.getStringOrNull(2)
                    ?: continue
                )
            }
            cur.close()
            val tzOffset = if (allday) {
                Calendar.getInstance().timeZone.getOffset(begin)
            } else {
                0
            }
            return CalendarEvent(
                label = title,
                id = id,
                color = color,
                startTime = begin - tzOffset,
                endTime = end - tzOffset - if (allday) 1 else 0,
                allDay = allday,
                location = location ?: "",
                attendees = attendees,
                description = description,
                calendar = calendar
            )
        }
        cursor.close()
        return null
    }

}