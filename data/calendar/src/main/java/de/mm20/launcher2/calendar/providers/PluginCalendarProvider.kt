package de.mm20.launcher2.calendar.providers

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.util.Log
import de.mm20.launcher2.plugin.QueryPluginApi
import de.mm20.launcher2.plugin.contracts.CalendarPluginContract
import de.mm20.launcher2.plugin.contracts.CalendarPluginContract.CalendarListColumns
import de.mm20.launcher2.plugin.contracts.CalendarPluginContract.EventColumns
import de.mm20.launcher2.plugin.data.set
import de.mm20.launcher2.plugin.data.withColumns
import de.mm20.launcher2.search.CalendarEvent
import de.mm20.launcher2.search.UpdateResult
import de.mm20.launcher2.search.asUpdateResult
import de.mm20.launcher2.search.calendar.CalendarQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class PluginCalendarProvider(
    private val context: Context,
    private val pluginAuthority: String,
) : QueryPluginApi<CalendarQuery, PluginCalendarEvent>(
    context, pluginAuthority
), CalendarProvider {
    override suspend fun search(
        query: String?,
        from: Long,
        to: Long,
        excludedCalendars: List<String>,
        excludeAllDayEvents: Boolean,
        allowNetwork: Boolean
    ): List<CalendarEvent> {
        return search(
            CalendarQuery(
                query = query,
                start = from,
                end = to,
                excludedCalendars = excludedCalendars,
            ),
            allowNetwork,
        )
    }

    override fun Uri.Builder.appendQueryParameters(query: CalendarQuery): Uri.Builder {
        if (query.query != null) {
            appendQueryParameter(
                CalendarPluginContract.Params.Query,
                query.query
            )
        }
        val start = query.start
        if (start != null) {
            appendQueryParameter(
                CalendarPluginContract.Params.StartTime,
                start.toString()
            )
        }
        val end = query.end
        if (end != null) {
            appendQueryParameter(
                CalendarPluginContract.Params.EndTime,
                end.toString()
            )
        }
        if (query.excludedCalendars.isNotEmpty()) {
            appendQueryParameter(
                CalendarPluginContract.Params.Exclude,
                query.excludedCalendars.joinToString(",")
            )
        }
        return this
    }

    override fun Cursor.getData(): List<PluginCalendarEvent>? {
        val config = getConfig()
        val cursor = this

        if (config == null) {
            Log.e("MM20", "Plugin ${pluginAuthority} returned null config")
            cursor.close()
            return null
        }

        val results = mutableListOf<PluginCalendarEvent>()
        val timestamp = System.currentTimeMillis()
        cursor.withColumns(EventColumns) {
            while (cursor.moveToNext()) {
                results += PluginCalendarEvent(
                    id = cursor[EventColumns.Id] ?: continue,
                    authority = pluginAuthority,
                    uri = Uri.parse(cursor[EventColumns.Uri] ?: continue),
                    color = cursor[EventColumns.Color],
                    label = cursor[EventColumns.Title] ?: continue,
                    description = cursor[EventColumns.Description],
                    location = cursor[EventColumns.Location],
                    calendarName = cursor[EventColumns.CalendarName],
                    allDay = cursor[EventColumns.IncludeTime] == false,
                    startTime = cursor[EventColumns.StartTime],
                    endTime = cursor[EventColumns.EndTime] ?: continue,
                    attendees = cursor[EventColumns.Attendees] ?: emptyList(),
                    isCompleted = cursor[EventColumns.IsCompleted],
                    updatedSelf = {
                        if (it !is PluginCalendarEvent) UpdateResult.TemporarilyUnavailable()
                        else refresh(it, timestamp).asUpdateResult()
                    },
                    storageStrategy = config.storageStrategy,
                    timestamp = timestamp,
                )
            }
        }
        cursor.close()
        return results
    }

    override fun PluginCalendarEvent.toBundle(): Bundle {
        return Bundle().apply {
            set(EventColumns.Id, id)
            set(EventColumns.Title, label)
            set(EventColumns.Description, description)
            set(EventColumns.CalendarName, calendarName)
            set(EventColumns.Location, location)
            set(EventColumns.Color, color)
            set(EventColumns.StartTime, startTime)
            set(EventColumns.EndTime, endTime)
            set(EventColumns.IncludeTime, !allDay)
            set(EventColumns.Attendees, attendees)
            set(EventColumns.Uri, uri.toString())
            set(EventColumns.IsCompleted, isCompleted)
        }
    }

    override suspend fun getCalendarLists(): List<CalendarList> = withContext(Dispatchers.IO) {
        val uri = Uri.Builder()
            .scheme("content")
            .authority(pluginAuthority)
            .path(CalendarPluginContract.Paths.CalendarLists)
            .build()
        val cancellationSignal = CancellationSignal()

        return@withContext suspendCancellableCoroutine {
            it.invokeOnCancellation {
                cancellationSignal.cancel()
            }
            val cursor = try {
                context.contentResolver.query(
                    uri,
                    null,
                    null,
                    cancellationSignal
                )
            } catch (e: Exception) {
                Log.e("MM20", "Plugin $pluginAuthority threw exception", e)
                it.resume(emptyList())
                return@suspendCancellableCoroutine
            }

            if (cursor == null) {
                Log.e("MM20", "Plugin $pluginAuthority returned null cursor")
                it.resume(emptyList())
                return@suspendCancellableCoroutine
            }

            val results = mutableListOf<CalendarList>()
            cursor.use {
                cursor.withColumns(CalendarListColumns) {
                    while (cursor.moveToNext()) {
                        results += CalendarList(
                            id = "${pluginAuthority}:" + (cursor[CalendarListColumns.Id] ?: continue),
                            color = cursor[CalendarListColumns.Color] ?: 0,
                            name = cursor[CalendarListColumns.Name] ?: continue,
                            owner = cursor[CalendarListColumns.AccountName],
                            types = cursor[CalendarListColumns.ContentTypes] ?: continue,
                            providerId = pluginAuthority,
                        )
                    }
                }
            }

            it.resume(results)
        }
    }

    override val namespace: String = pluginAuthority
}