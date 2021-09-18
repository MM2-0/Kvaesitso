package de.mm20.launcher2.favorites

import android.content.Context
import android.util.Log
import de.mm20.launcher2.search.data.*

class SearchableDeserializer(val context: Context) {
    fun deserialize(serialized: String?): Searchable? {
        val type = serialized?.substringBefore("#") ?: return null
        val data = serialized.substringAfter("#")
        return when (type) {
            "app" -> LauncherApp.deserialize(context, data)
            "shortcut" -> AppShortcut.deserialize(context, data)
            "calculator" -> null
            "calendar" -> CalendarEvent.deserialize(context, data)
            "contact" -> Contact.deserialize(context, data)
            "gdrive" -> GDriveFile.deserialize(data)
            "owncloud" -> OwncloudFile.deserialize(data)
            "nextcloud" -> NextcloudFile.deserialize(data)
            "file" -> File.deserialize(context, data)
            "onedrive" -> OneDriveFile.deserialize(data)
            "websearch" -> null
            "website" -> Website.deserialize(data)
            "wikipedia" -> Wikipedia.deserialize(data)
            else -> null
        }
    }

    companion object {
        fun getTypePrefix(searchable: Searchable): String {
            return when(searchable) {
                is Application -> "app"
                is AppShortcut -> "shortcut"
                is CalendarEvent -> "calendar"
                is Contact -> "contact"
                is GDriveFile -> "gdrive"
                is OneDriveFile -> "onedrive"
                is NextcloudFile -> "nextcloud"
                is OwncloudFile -> "owncloud"
                is File -> "file"
                is Website -> "website"
                is Wikipedia -> "wikipedia"
                else -> ""
            }
        }
    }
}
