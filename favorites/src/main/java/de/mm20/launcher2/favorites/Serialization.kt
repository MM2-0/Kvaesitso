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
import de.mm20.launcher2.search.Searchable
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
    if (searchable is Tag) {
        return TagSerializer()
    }
    return NullSerializer()
}

internal fun getDeserializer(context: Context, type: String): SearchableDeserializer {
    if (type == LauncherApp.Domain) {
        return LauncherAppDeserializer(context)
    }
    if (type == LauncherShortcut.Domain) {
        return LauncherShortcutDeserializer(context)
    }
    if (type == LegacyShortcut.Domain) {
        return LegacyShortcutDeserializer(context)
    }
    if (type == CalendarEvent.Domain) {
        return CalendarEventDeserializer(context)
    }
    if (type == Contact.Domain) {
        return ContactDeserializer(context)
    }
    if (type == Wikipedia.Domain) {
        return WikipediaDeserializer(context)
    }
    if (type == GDriveFile.Domain) {
        return GDriveFileDeserializer()
    }
    if (type == OneDriveFile.Domain) {
        return OneDriveFileDeserializer()
    }
    if (type == NextcloudFile.Domain) {
        return NextcloudFileDeserializer()
    }
    if (type == OwncloudFile.Domain) {
        return OwncloudFileDeserializer()
    }
    if (type == LocalFile.Domain) {
        return LocalFileDeserializer(context)
    }
    if (type == Website.Domain) {
        return WebsiteDeserializer()
    }
    if (type == Tag.Domain) {
        return TagDeserializer()
    }
    return NullDeserializer()
}