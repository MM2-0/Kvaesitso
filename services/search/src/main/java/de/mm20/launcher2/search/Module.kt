package de.mm20.launcher2.search

import org.koin.core.qualifier.named
import org.koin.dsl.module

val searchModule = module {
    single<SearchService> {
        SearchServiceImpl(
            get(named<Application>()),
            get(named<AppShortcut>()),
            get(named<CalendarEvent>()),
            get(named<Contact>()),
            get(named<File>()),
            get(named<Article>()),
            get(named<Location>()),
            get(),
            get(),
            get(named<Website>()),
            get(),
            get(),
            get(),
        )
    }
}