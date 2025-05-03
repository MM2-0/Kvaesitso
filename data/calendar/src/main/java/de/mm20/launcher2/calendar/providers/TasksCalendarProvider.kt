package de.mm20.launcher2.calendar.providers

import android.content.Context
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import androidx.core.net.toUri
import de.mm20.launcher2.search.CalendarEvent
import de.mm20.launcher2.search.calendar.CalendarListType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

internal class TasksCalendarProvider(
    private val context: Context,
) : CalendarProvider {
    override suspend fun search(
        query: String?,
        from: Long,
        to: Long,
        excludedCalendars: List<String>,
        excludeAllDayEvents: Boolean,
        allowNetwork: Boolean
    ): List<CalendarEvent> {
        return withContext(Dispatchers.IO) {
            val startOfDay = Instant.ofEpochMilli(from)
                .atZone(ZoneId.systemDefault())
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .toInstant().toEpochMilli()
            queryTasks(
                selection = buildList {
                    add("($to >= hideUntil OR hideUntil IS NULL)")
                    add("($from <= dueDate OR ($startOfDay <= dueDate AND dueDate % 60000 <= 0))")
                    if (excludedCalendars.isNotEmpty()) {
                        add("cdl_id NOT IN (${excludedCalendars.joinToString()})")
                    }
                    if (query != null) {
                        add(
                            "title LIKE '%${
                                query.replace("'", "").replace("%", "")
                            }%'"
                        )
                    }
                }.joinToString(" AND ").takeIf { it.isNotEmpty() },
            )
        }
    }

    override suspend fun getCalendarLists(): List<CalendarList> {
        return withContext(Dispatchers.IO) {
            val uri = "content://org.tasks/lists".toUri()
            val cursor = context.contentResolver.query(uri, arrayOf(), null, arrayOf(), null)
                ?: return@withContext emptyList()
            val results = mutableListOf<CalendarList>()

            val idIndex = cursor.getColumnIndex("cdl_id")
            val nameIndex = cursor.getColumnIndex("cdl_name")
            val colorIndex = cursor.getColumnIndex("cdl_color")
            val accountIndex = cursor.getColumnIndex("cdl_account")

            cursor.use {
                while (cursor.moveToNext()) {
                    val id = cursor.getLongOrNull(idIndex)?.toString() ?: continue
                    results += CalendarList(
                        id = "$namespace:$id",
                        name = cursor.getStringOrNull(nameIndex) ?: continue,
                        color = cursor.getIntOrNull(colorIndex) ?: 0,
                        types = listOf(CalendarListType.Tasks),
                        providerId = "tasks.org",
                        owner = cursor.getStringOrNull(accountIndex)?.substringAfter(":", "")
                            ?.takeIf { it.isNotBlank() },
                    )
                }
            }
            results
        }
    }

    private fun queryTasks(
        selection: String? = null,
        selectionArgs: Array<String>? = arrayOf(),
    ): List<CalendarEvent> {
        val uri = "content://org.tasks/todoagenda".toUri()
        val cursor = context.contentResolver.query(uri, arrayOf(), selection, selectionArgs, null)

        val results = mutableListOf<CalendarEvent>()

        cursor?.use {
            val idIndex = cursor.getColumnIndex("_id")
            val titleIndex = cursor.getColumnIndex("title")
            val startIndex = cursor.getColumnIndex("hideUntil")
            val dueIndex = cursor.getColumnIndex("dueDate")
            val completedIndex = cursor.getColumnIndex("completed")
            val notesIndex = cursor.getColumnIndex("notes")
            val colorIndex = cursor.getColumnIndex("cdl_color")
            val calendarNameIndex = cursor.getColumnIndex("cdl_name")

            while (cursor.moveToNext()) {

                val id = cursor.getLongOrNull(idIndex) ?: continue
                val dueDate = cursor.getLongOrNull(dueIndex)?.takeIf { it > 0L } ?: continue

                // https://github.com/tasks/tasks/blob/13d4c029e855fd32ec91e4d4ec5f740ec506136e/data/src/commonMain/kotlin/org/tasks/data/entity/Task.kt#L345
                val isAllDay = dueDate % 60000 <= 0

                val endTime = if (isAllDay) {
                    Instant.ofEpochMilli(dueDate)
                        .atZone(ZoneId.systemDefault())
                        .withHour(23)
                        .withMinute(59)
                        .withSecond(59)
                        .withNano(999_999_999)
                        .toInstant()
                        .toEpochMilli()
                } else {
                    dueDate
                }

                results += TasksCalendarEvent(
                    id = id,
                    label = cursor.getStringOrNull(titleIndex) ?: continue,
                    description = cursor.getStringOrNull(notesIndex),
                    color = cursor.getIntOrNull(colorIndex),
                    calendarName = cursor.getStringOrNull(calendarNameIndex),
                    startTime = cursor.getLongOrNull(startIndex)?.takeIf { it > 0L },
                    endTime = endTime,
                    allDay = isAllDay,
                    isCompleted = (cursor.getLongOrNull(completedIndex) ?: 0L) != 0L,
                )
            }
        }

        return results
    }

    suspend fun get(id: Long): CalendarEvent? {
        return withContext(Dispatchers.IO) {
            queryTasks(
                selection = "_id = $id",
            ).firstOrNull()
        }
    }

    override val namespace: String = "tasks.org"
}