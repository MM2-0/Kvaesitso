package de.mm20.launcher2.calendar

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import androidx.core.database.getStringOrNull
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.search.CalendarSearchSettings
import de.mm20.launcher2.search.CalendarEvent
import de.mm20.launcher2.search.SearchableRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Calendar

interface CalendarRepository : SearchableRepository<CalendarEvent> {
    fun findMany(
        from: Long = System.currentTimeMillis(),
        to: Long = from + 14 * 24 * 60 * 60 * 1000L,
        excludeCalendars: List<Long> = emptyList(),
        excludeAllDayEvents: Boolean = false,
        limit: Int = 999,
    ): Flow<ImmutableList<CalendarEvent>>

    suspend fun getCalendars(): List<UserCalendar>
}

internal class CalendarRepositoryImpl(
    private val context: Context,
    private val permissionsManager: PermissionsManager,
    private val settings: CalendarSearchSettings,
) : CalendarRepository {

    override fun search(query: String): Flow<ImmutableList<CalendarEvent>> {
        if (query.isBlank() || query.length < 2) {
            return flow {
                emit(persistentListOf())
            }
        }

        val hasPermission = permissionsManager.hasPermission(PermissionGroup.Calendar)
        val enabled = settings.enabled

        return hasPermission.combine(enabled) { a, b -> a && b }
            .map {
                if (it) {
                    val now = System.currentTimeMillis()
                    queryCalendarEvents(
                        query,
                        intervalStart = now,
                        intervalEnd = now + 14 * 24 * 60 * 60 * 1000L,
                    ).toImmutableList()
                } else {
                    persistentListOf()
                }
            }

    }

    override fun findMany(
        from: Long,
        to: Long,
        excludeCalendars: List<Long>,
        excludeAllDayEvents: Boolean,
        limit: Int,
    ) = channelFlow<ImmutableList<CalendarEvent>> {
        val hasPermission = permissionsManager.hasPermission(PermissionGroup.Calendar)
        hasPermission.collectLatest {
            if (it) {
                val events = withContext(Dispatchers.IO) {
                    queryCalendarEvents(
                        query = null,
                        intervalStart = from,
                        intervalEnd = to,
                        limit = limit,
                        excludeAllDayEvents = excludeAllDayEvents,
                        excludeCalendars = excludeCalendars
                    )
                }
                send(events.toImmutableList())
            } else {
                send(persistentListOf())
            }
        }
    }

    private suspend fun queryCalendarEvents(
        query: String?,
        intervalStart: Long,
        intervalEnd: Long,
        limit: Int = 10,
        excludeAllDayEvents: Boolean = false,
        excludeCalendars: List<Long> = emptyList(),
    ): List<AndroidCalendarEvent> {
        val results = withContext(Dispatchers.IO) {
            val results = mutableListOf<AndroidCalendarEvent>()
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
            if (query != null) selection.add("${CalendarContract.Instances.TITLE} LIKE ?")
            if (excludeCalendars.isNotEmpty()) selection.add("${CalendarContract.Instances.CALENDAR_ID} NOT IN (${excludeCalendars.joinToString()})")
            if (excludeAllDayEvents) selection.add("${CalendarContract.Instances.ALL_DAY} = 0")
            val selArgs = if (query != null) arrayOf("%$query%") else null
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
                val event = AndroidCalendarEvent(
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