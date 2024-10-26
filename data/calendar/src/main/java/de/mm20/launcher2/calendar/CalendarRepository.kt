package de.mm20.launcher2.calendar

import android.content.Context
import android.util.Log
import de.mm20.launcher2.calendar.providers.AndroidCalendarProvider
import de.mm20.launcher2.calendar.providers.CalendarList
import de.mm20.launcher2.calendar.providers.CalendarProvider
import de.mm20.launcher2.calendar.providers.PluginCalendarProvider
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.plugin.PluginRepository
import de.mm20.launcher2.plugin.PluginType
import de.mm20.launcher2.preferences.search.CalendarSearchSettings
import de.mm20.launcher2.search.CalendarEvent
import de.mm20.launcher2.search.SearchableRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlin.time.Duration.Companion.days

interface CalendarRepository : SearchableRepository<CalendarEvent> {
    fun findMany(
        from: Long = System.currentTimeMillis(),
        to: Long = from + 14 * 24 * 60 * 60 * 1000L,
        excludeCalendars: List<String> = emptyList(),
        excludeAllDayEvents: Boolean = false,
    ): Flow<ImmutableList<CalendarEvent>>

    fun getCalendars(providerId: String? = null): Flow<List<CalendarList>>
}

internal class CalendarRepositoryImpl(
    private val context: Context,
    private val permissionsManager: PermissionsManager,
    private val pluginRepository: PluginRepository,
    private val settings: CalendarSearchSettings,
) : CalendarRepository {

    override fun search(query: String, allowNetwork: Boolean): Flow<ImmutableList<CalendarEvent>> {
        if (query.isBlank() || query.length < 2) {
            return flow {
                emit(persistentListOf())
            }
        }

        val hasPermission = permissionsManager.hasPermission(PermissionGroup.Calendar)
        val providerIds = settings.providers
        val excludedCalendars = settings.excludedCalendars

        return combineTransform(hasPermission, providerIds, excludedCalendars) { perm, providerIds, excludedCalendars ->
            val providers = providerIds.mapNotNull {
                when (it) {
                    "local" -> if (perm) AndroidCalendarProvider(context) else null
                    else -> PluginCalendarProvider(context, it)
                }
            }

            val now = System.currentTimeMillis()
            emitAll(
                queryCalendarEvents(
                    query = query,
                    excludeAllDayEvents = false,
                    excludeCalendars = excludedCalendars.toList(),
                    providers = providers,
                    intervalStart = now,
                    intervalEnd = now + 730.days.inWholeMilliseconds,
                    allowNetwork = allowNetwork,
                )
            )
        }
    }

    override fun findMany(
        from: Long,
        to: Long,
        excludeCalendars: List<String>,
        excludeAllDayEvents: Boolean,
    ): Flow<ImmutableList<CalendarEvent>> {
        val hasPermission = permissionsManager.hasPermission(PermissionGroup.Calendar)
        val plugins = pluginRepository.findMany(
            type = PluginType.Calendar,
            enabled = true,
        )
        return combineTransform(hasPermission, plugins) { perm, plugins ->
            val providers = buildList {
                if (perm) add(AndroidCalendarProvider(context)) else null
                addAll(
                    plugins.map {
                        PluginCalendarProvider(context, it.authority)
                    }
                )
            }

            emitAll(
                queryCalendarEvents(
                    query = null,
                    intervalStart = from,
                    intervalEnd = to,
                    excludeAllDayEvents = excludeAllDayEvents,
                    excludeCalendars = excludeCalendars,
                    providers = providers,
                    allowNetwork = false,
                )
            )
        }
    }

    private suspend fun queryCalendarEvents(
        query: String?,
        intervalStart: Long,
        intervalEnd: Long,
        excludeAllDayEvents: Boolean = false,
        excludeCalendars: List<String> = emptyList(),
        allowNetwork: Boolean = false,
        providers: List<CalendarProvider>,
    ): Flow<ImmutableList<CalendarEvent>> = flow {
        supervisorScope {
            val result = MutableStateFlow(persistentListOf<CalendarEvent>())

            for (provider in providers) {
                launch {
                    val r = provider.search(
                        query,
                        from = intervalStart,
                        to = intervalEnd,
                        excludedCalendars = excludeCalendars.mapNotNull {
                            val (namespace, id) = it.split(":")
                            if (namespace == provider.namespace) id else null
                        },
                        excludeAllDayEvents = excludeAllDayEvents,
                        allowNetwork = allowNetwork,
                    )
                    result.update {
                        (it + r).toPersistentList()
                    }
                }
            }
            emitAll(result)
        }
    }

    override fun getCalendars(providerId: String?): Flow<List<CalendarList>> {
        val hasPermission = permissionsManager.hasPermission(PermissionGroup.Calendar)

        val providers: Flow<List<CalendarProvider>> = if (providerId != null) {
            when(providerId) {
                "local" -> hasPermission.map { if (it) listOf(AndroidCalendarProvider(context)) else emptyList() }
                else -> pluginRepository.get(providerId).map { if (it?.enabled == true) listOf(PluginCalendarProvider(context, providerId)) else emptyList() }
            }
        } else {
            val plugins = pluginRepository.findMany(
                type = PluginType.Calendar,
                enabled = true,
            )
            combine(hasPermission, plugins) { perm, plugins ->
                buildList {
                    if (perm) add(AndroidCalendarProvider(context))
                    addAll(plugins.map { PluginCalendarProvider(context, it.authority) })
                }
            }
        }

        return providers.transform { providers ->
            supervisorScope {
                val result = MutableStateFlow(emptyList<CalendarList>())
                for (provider in providers) {
                    launch {
                        val r = provider.getCalendarLists()
                        result.update { it + r }
                    }
                }
                emitAll(result)
            }
        }


    }
}