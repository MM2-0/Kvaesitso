package de.mm20.launcher2.favorites

import de.mm20.launcher2.appshortcuts.AppShortcutDeserializer
import de.mm20.launcher2.appshortcuts.AppShortcutSerializer
import de.mm20.launcher2.calendar.CalendarEventDeserializer
import de.mm20.launcher2.calendar.CalendarEventSerializer
import de.mm20.launcher2.contacts.ContactDeserializer
import de.mm20.launcher2.contacts.ContactSerializer
import de.mm20.launcher2.files.*
import de.mm20.launcher2.search.NullDeserializer
import de.mm20.launcher2.search.NullSerializer
import de.mm20.launcher2.search.data.*
import de.mm20.launcher2.websites.WebsiteDeserializer
import de.mm20.launcher2.websites.WebsiteSerializer
import de.mm20.launcher2.wikipedia.WikipediaDeserializer
import de.mm20.launcher2.wikipedia.WikipediaSerializer
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val favoritesModule = module {
    single<FavoritesRepository> { FavoritesRepositoryImpl(androidContext(), get()) }
}