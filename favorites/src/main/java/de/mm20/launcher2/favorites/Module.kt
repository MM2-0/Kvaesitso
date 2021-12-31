package de.mm20.launcher2.favorites

import de.mm20.launcher2.calendar.CalendarEventDeserializer
import de.mm20.launcher2.calendar.CalendarEventSerializer
import de.mm20.launcher2.contacts.ContactDeserializer
import de.mm20.launcher2.contacts.ContactSerializer
import de.mm20.launcher2.files.*
import de.mm20.launcher2.search.NullDeserializer
import de.mm20.launcher2.search.data.*
import de.mm20.launcher2.websites.WebsiteDeserializer
import de.mm20.launcher2.websites.WebsiteSerializer
import de.mm20.launcher2.wikipedia.WikipediaDeserializer
import de.mm20.launcher2.wikipedia.WikipediaSerializer
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val favoritesModule = module {
    factory { (searchable: Searchable) ->
        if (searchable is LauncherApp) {
            return@factory LauncherAppSerializer()
        }
        if (searchable is AppShortcut) {
            return@factory AppShortcutSerializer()
        }
        if (searchable is CalendarEvent) {
            return@factory CalendarEventSerializer()
        }
        if (searchable is Contact) {
            return@factory ContactSerializer()
        }
        if (searchable is Wikipedia) {
            return@factory WikipediaSerializer()
        }
        if (searchable is GDriveFile) {
            return@factory GDriveFileSerializer()
        }
        if (searchable is OneDriveFile) {
            return@factory OneDriveFileSerializer()
        }
        if (searchable is OwncloudFile) {
            return@factory OwncloudFileSerializer()
        }
        if (searchable is NextcloudFile) {
            return@factory NextcloudFileSerializer()
        }
        if (searchable is LocalFile) {
            return@factory LocalFileSerializer()
        }
        if (searchable is Website) {
            return@factory WebsiteSerializer()
        }
        throw IllegalArgumentException("No known serializer exists for type ${searchable.javaClass.canonicalName}")
    }

    factory { (serialized: String) ->
        val type = serialized.substringBefore("#")
        if (type == "app") {
            return@factory LauncherAppDeserializer(androidContext())
        }
        if (type == "shortcut") {
            return@factory AppShortcutDeserializer(androidContext())
        }
        if (type == "calendar") {
            return@factory CalendarEventDeserializer(androidContext())
        }
        if (type == "contact") {
            return@factory ContactDeserializer(androidContext())
        }
        if (type == "wikipedia") {
            return@factory WikipediaDeserializer()
        }
        if (type == "gdrive") {
            return@factory GDriveFileDeserializer()
        }
        if (type == "onedrive") {
            return@factory OneDriveFileDeserializer()
        }
        if (type == "nextcloud") {
            return@factory NextcloudFileDeserializer()
        }
        if (type == "owncloud") {
            return@factory OwncloudFileDeserializer()
        }
        if (type == "file") {
            return@factory LocalFileDeserializer(androidContext())
        }
        if (type == "website") {
            return@factory WebsiteDeserializer()
        }
        return@factory NullDeserializer()
    }

    single<FavoritesRepository> { FavoritesRepositoryImpl(androidContext(), get()) }
}