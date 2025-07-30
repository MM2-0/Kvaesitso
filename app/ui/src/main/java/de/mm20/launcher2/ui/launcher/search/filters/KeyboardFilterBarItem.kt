package de.mm20.launcher2.ui.launcher.search.filters

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Today
import de.mm20.launcher2.icons.Wikipedia
import de.mm20.launcher2.preferences.KeyboardFilterBarItem
import de.mm20.launcher2.search.SearchFilters
import de.mm20.launcher2.ui.R

val KeyboardFilterBarItem.icon
    get() = when (this) {
        KeyboardFilterBarItem.Events -> Icons.Rounded.Today
        KeyboardFilterBarItem.Places -> Icons.Rounded.Place
        KeyboardFilterBarItem.Files -> Icons.Rounded.Description
        KeyboardFilterBarItem.Articles -> Icons.Rounded.Wikipedia
        KeyboardFilterBarItem.Websites -> Icons.Rounded.Public
    }

fun KeyboardFilterBarItem.getLabel(context: Context): String {
    return when (this) {
        KeyboardFilterBarItem.Events -> context.getString(R.string.preference_search_calendar)
        KeyboardFilterBarItem.Places -> context.getString(R.string.preference_search_locations)
        KeyboardFilterBarItem.Files -> context.getString(R.string.preference_search_files)
        KeyboardFilterBarItem.Articles -> context.getString(R.string.preference_search_wikipedia)
        KeyboardFilterBarItem.Websites -> context.getString(R.string.preference_search_websites)
    }
}

fun SearchFilters.isSelected(item: KeyboardFilterBarItem): Boolean {
    return when (item) {
        KeyboardFilterBarItem.Events -> events
        KeyboardFilterBarItem.Places -> places
        KeyboardFilterBarItem.Files -> files
        KeyboardFilterBarItem.Articles -> articles
        KeyboardFilterBarItem.Websites -> websites
    }
}

fun SearchFilters.toggle(item: KeyboardFilterBarItem): SearchFilters {
    return when (item) {
        KeyboardFilterBarItem.Events -> toggleEvents()
        KeyboardFilterBarItem.Places -> togglePlaces()
        KeyboardFilterBarItem.Files -> toggleFiles()
        KeyboardFilterBarItem.Articles -> toggleArticles()
        KeyboardFilterBarItem.Websites -> toggleWebsites()
    }
}