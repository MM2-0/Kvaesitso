package de.mm20.launcher2.calendar

import de.mm20.launcher2.search.CalendarEvent
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val calendarModule = module {
    factory<SearchableRepository<CalendarEvent>>(named<CalendarEvent>()) { CalendarRepositoryImpl(androidContext(), get()) }
    factory<CalendarRepository> { CalendarRepositoryImpl(androidContext(), get()) }
    factory<SearchableDeserializer>(named(AndroidCalendarEvent.Domain)) { CalendarEventDeserializer(androidContext()) }
}