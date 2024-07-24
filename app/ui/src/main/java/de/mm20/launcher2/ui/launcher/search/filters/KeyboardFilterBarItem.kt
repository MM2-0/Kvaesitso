package de.mm20.launcher2.ui.launcher.search.filters

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AppShortcut
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.VisibilityOff
import de.mm20.launcher2.preferences.KeyboardFilterBarItem
import de.mm20.launcher2.search.SearchFilters
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.icons.Wikipedia

val KeyboardFilterBarItem.icon
    get() = when (this) {
        KeyboardFilterBarItem.Apps -> Icons.Rounded.Apps
        KeyboardFilterBarItem.Events -> Icons.Rounded.Today
        KeyboardFilterBarItem.Contacts -> Icons.Rounded.Person
        KeyboardFilterBarItem.Places -> Icons.Rounded.Place
        KeyboardFilterBarItem.Files -> Icons.Rounded.Description
        KeyboardFilterBarItem.Tools -> Icons.Rounded.Handyman
        KeyboardFilterBarItem.Articles -> Icons.Rounded.Wikipedia
        KeyboardFilterBarItem.Websites -> Icons.Rounded.Public
        KeyboardFilterBarItem.Shortcuts -> Icons.Rounded.AppShortcut
        KeyboardFilterBarItem.HiddenResults -> Icons.Rounded.VisibilityOff
        KeyboardFilterBarItem.OnlineResults -> Icons.Rounded.Language
    }

fun KeyboardFilterBarItem.getLabel(context: Context): String {
    return when (this) {
        KeyboardFilterBarItem.Apps -> context.getString(R.string.search_filter_apps)
        KeyboardFilterBarItem.Events -> context.getString(R.string.preference_search_calendar)
        KeyboardFilterBarItem.Contacts -> context.getString(R.string.preference_search_contacts)
        KeyboardFilterBarItem.Places -> context.getString(R.string.preference_search_locations)
        KeyboardFilterBarItem.Files -> context.getString(R.string.preference_search_files)
        KeyboardFilterBarItem.Tools -> context.getString(R.string.search_filter_tools)
        KeyboardFilterBarItem.Articles -> context.getString(R.string.preference_search_wikipedia)
        KeyboardFilterBarItem.Websites -> context.getString(R.string.preference_search_websites)
        KeyboardFilterBarItem.Shortcuts -> context.getString(R.string.preference_search_appshortcuts)
        KeyboardFilterBarItem.HiddenResults -> context.getString(R.string.preference_hidden_items)
        KeyboardFilterBarItem.OnlineResults -> context.getString(R.string.search_filter_online)
    }
}

val KeyboardFilterBarItem.isCategory
    get() = when (this) {
        KeyboardFilterBarItem.OnlineResults, KeyboardFilterBarItem.HiddenResults -> false
        else -> true
    }

fun SearchFilters.isSelected(item: KeyboardFilterBarItem): Boolean {
    if (item.isCategory && allCategoriesEnabled) return false
    return when (item) {
        KeyboardFilterBarItem.Apps -> apps
        KeyboardFilterBarItem.Events -> events
        KeyboardFilterBarItem.Contacts -> contacts
        KeyboardFilterBarItem.Places -> places
        KeyboardFilterBarItem.Files -> files
        KeyboardFilterBarItem.Tools -> tools
        KeyboardFilterBarItem.Articles -> articles
        KeyboardFilterBarItem.Websites -> websites
        KeyboardFilterBarItem.Shortcuts -> shortcuts
        KeyboardFilterBarItem.HiddenResults -> hiddenItems
        KeyboardFilterBarItem.OnlineResults -> allowNetwork
    }
}

fun SearchFilters.toggle(item: KeyboardFilterBarItem): SearchFilters {
    return when (item) {
        KeyboardFilterBarItem.Apps -> return toggleApps()
        KeyboardFilterBarItem.Events -> return toggleEvents()
        KeyboardFilterBarItem.Contacts -> return toggleContacts()
        KeyboardFilterBarItem.Places -> return togglePlaces()
        KeyboardFilterBarItem.Files -> return toggleFiles()
        KeyboardFilterBarItem.Tools -> return toggleTools()
        KeyboardFilterBarItem.Articles -> return toggleArticles()
        KeyboardFilterBarItem.Websites -> return toggleWebsites()
        KeyboardFilterBarItem.Shortcuts -> return toggleShortcuts()
        KeyboardFilterBarItem.HiddenResults -> return copy(hiddenItems = !hiddenItems)
        KeyboardFilterBarItem.OnlineResults -> return copy(allowNetwork = !allowNetwork)
    }
}