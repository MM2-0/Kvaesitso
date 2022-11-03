package de.mm20.launcher2.searchactions.actions

import android.content.Context
import de.mm20.launcher2.search.Searchable

interface SearchAction : Searchable {
    val label: String
    val icon: SearchActionIcon
    val iconColor: Int
    fun start(context: Context)
}

enum class SearchActionIcon {
    Search,
    Website,
    Alarm,
    Timer,
    Contact,
    Phone,
    Email,
    Message,
    Calendar,
    Translate,
    Custom,
}