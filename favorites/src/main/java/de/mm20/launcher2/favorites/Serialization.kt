package de.mm20.launcher2.favorites

import android.content.Context
import de.mm20.launcher2.appshortcuts.LauncherShortcutDeserializer
import de.mm20.launcher2.appshortcuts.LauncherShortcutSerializer
import de.mm20.launcher2.appshortcuts.LegacyShortcutDeserializer
import de.mm20.launcher2.appshortcuts.LegacyShortcutSerializer
import de.mm20.launcher2.calendar.CalendarEventDeserializer
import de.mm20.launcher2.calendar.CalendarEventSerializer
import de.mm20.launcher2.contacts.ContactDeserializer
import de.mm20.launcher2.contacts.ContactSerializer
import de.mm20.launcher2.files.*
import de.mm20.launcher2.search.NullDeserializer
import de.mm20.launcher2.search.NullSerializer
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.search.data.*
import de.mm20.launcher2.websites.WebsiteDeserializer
import de.mm20.launcher2.websites.WebsiteSerializer
import de.mm20.launcher2.wikipedia.WikipediaDeserializer
import de.mm20.launcher2.wikipedia.WikipediaSerializer


internal fun getSerializer(searchable: Searchable?): SearchableSerializer {
    if (searchable is LauncherApp) {
        return LauncherAppSerializer()
    }
    if (searchable is LauncherShortcut) {
        return LauncherShortcutSerializer()
    }
    if (searchable is LegacyShortcut) {
        return LegacyShortcutSerializer()
    }
    if (searchable is CalendarEvent) {
        return CalendarEventSerializer()
    }
    if (searchable is Contact) {
        return ContactSerializer()
    }
    if (searchable is Wikipedia) {
        return WikipediaSerializer()
    }
    if (searchable is GDriveFile) {
        return GDriveFileSerializer()
    }
    if (searchable is OneDriveFile) {
        return OneDriveFileSerializer()
    }
    if (searchable is OwncloudFile) {
        return OwncloudFileSerializer()
    }
    if (searchable is NextcloudFile) {
        return NextcloudFileSerializer()
    }
    if (searchable is LocalFile) {
        return LocalFileSerializer()
    }
    if (searchable is Website) {
        return WebsiteSerializer()
    }
    return NullSerializer()
}

internal fun getDeserializer(context: Context, serialized: String): SearchableDeserializer {
    val type = serialized.substringBefore("#")
    if (type == "app") {
        return LauncherAppDeserializer(context)
    }
    if (type == "shortcut") {
        return LauncherShortcutDeserializer(context)
    }
    if (type == "legacyshortcut") {
        return LegacyShortcutDeserializer(context)
    }
    if (type == "calendar") {
        return CalendarEventDeserializer(context)
    }
    if (type == "contact") {
        return ContactDeserializer(context)
    }
    if (type == "wikipedia") {
        return WikipediaDeserializer(context)
    }
    if (type == "gdrive") {
        return GDriveFileDeserializer()
    }
    if (type == "onedrive") {
        return OneDriveFileDeserializer()
    }
    if (type == "nextcloud") {
        return NextcloudFileDeserializer()
    }
    if (type == "owncloud") {
        return OwncloudFileDeserializer()
    }
    if (type == "file") {
        return LocalFileDeserializer(context)
    }
    if (type == "website") {
        return WebsiteDeserializer()
    }
    return NullDeserializer()
}