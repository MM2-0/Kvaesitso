package de.mm20.launcher2.calendar

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import androidx.core.database.getStringOrNull
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.search.data.CalendarEvent
import de.mm20.launcher2.search.data.UserCalendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*

interface CalendarRepository {
    fun search(query: String): Flow<List<CalendarEvent>>

    fun getUpcomingEvents(): Flow<List<CalendarEvent>>

    suspend fun getCalendars(): List<UserCalendar>
}

internal class CalendarRepositoryImpl(
    private val context: Context,
) : CalendarRepository, KoinComponent {

    private val dataStore: LauncherDataStore by inject()
    private val permissionsManager: PermissionsManager by inject()

    override fun search(query: String): Flow<List<CalendarEvent>> {
        if (query.isBlank() || query.length < 3) {
            return flow {
                emit(emptyList())
            }
        }

        val hasPermission = permissionsManager.hasPermission(PermissionGroup.Calendar)
        val searchCalendar = dataStore.data.map { it.calendarSearch.enabled }
        return combine(hasPermission, searchCalendar) { permission, search ->
            permission && search
        }.map {
            if (it) {
                val now = System.currentTimeMillis()
                queryCalendarEvents(
                    query,
                    intervalStart = now,
                    intervalEnd = now + 14 * 24 * 60 * 60 * 1000L,
                )
            } else {
                emptyList()
            }
        }

    }

    private suspend fun queryCalendarEvents(
        query: String,
        intervalStart: Long,
        intervalEnd: Long,
        limit: Int = 10,
        excludeAllDayEvents: Boolean = false,
        excludeCalendars: List<Long> = emptyList(),
    ): List<CalendarEvent> {
        val results = withContext(Dispatchers.IO) {
            val results = mutableListOf<CalendarEvent>()
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
            if (excludeCalendars.isNotEmpty()) selection.add("${CalendarContract.Instances.CALENDAR_ID} NOT IN (${excludeCalendars.joinToString()})")
            if (excludeAllDayEvents) selection.add("${CalendarContract.Instances.ALL_DAY} = 0")
            val selArgs = if (query.isBlank()) null else arrayOf("%$query%")
            val sort =
                "${CalendarContract.Instances.BEGIN} ASC" + if (limit > -1) " LIMIT $limit" else ""
            val cursor = context.contentResolver.query(
                uri,
                projection,
                selection.joinToString(separator = " AND "),
                selArgs,
                sort
            ) ?: return@withContext mutableListOf()
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
                ) ?: return@withContext mutableListOf()
                val attendees = mutableListOf<String>()
                while (cur.moveToNext()) {
                    attendees.add(
                        cur.getStringOrNull(1).takeUnless { it.isNullOrBlank() }
                            ?: cur.getStringOrNull(2)
                            ?: continue
                    )
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
                    label = cursor.getStringOrNull(1) ?: "",
                    id = cursor.getLong(0),
                    color = cursor.getInt(5),
                    startTime = begin - tzOffset,
                    endTime = cursor.getLong(3) - tzOffset - if (allday) 1 else 0,
                    allDay = allday,
                    location = cursor.getStringOrNull(6) ?: "",
                    attendees = attendees,
                    description = cursor.getStringOrNull(8)
                        ?: "",
                    calendar = cursor.getLong(7)
                )
                results.add(event)
            }
            cursor.close()
            return@withContext results
        }

        return results
    }

    override fun getUpcomingEvents(): Flow<List<CalendarEvent>> = channelFlow {
        val hasPermission = permissionsManager.hasPermission(PermissionGroup.Calendar)
        hasPermission.collectLatest {
            if (it) {
                dataStore.data.map { it.calendarWidget }.collectLatest { settings ->
                    val now = System.currentTimeMillis()
                    val end = now + 14 * 24 * 60 * 60 * 1000L
                    val events = withContext(Dispatchers.IO) {
                        queryCalendarEvents(
                            query = "",
                            intervalStart = now,
                            intervalEnd = end,
                            limit = 700,
                            excludeAllDayEvents = settings.hideAlldayEvents,
                            excludeCalendars = settings.excludeCalendarsList
                        )
                    }
                    send(events)
                }
            } else {
                send(emptyList())
            }
        }
    }

    override suspend fun getCalendars(): List<UserCalendar> {
        if (!permissionsManager.checkPermissionOnce(PermissionGroup.Calendar)) return emptyList()
        return withContext(Dispatchers.IO) {
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
            val cursor = context.contentResolver.query(uri, proj, null, null, null)
                ?: return@withContext emptyList()
            while (cursor.moveToNext()) {
                try {
                    calendars.add(
                        UserCalendar(
                            id = cursor.getLong(0),
                            name = cursor.getStringOrNull(5) ?: cursor.getStringOrNull(1) ?: "",
                            owner = cursor.getStringOrNull(2) ?: "",
                            color = cursor.getInt(3)
                        )
                    )
                } catch (e: NullPointerException) {
                    continue
                }
            }
            cursor.close()
            calendars.sortBy { it.owner }
            return@withContext calendars
        }
    }
}