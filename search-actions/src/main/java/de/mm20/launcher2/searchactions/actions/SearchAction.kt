package de.mm20.launcher2.searchactions.actions

import android.content.Context
import de.mm20.launcher2.search.Searchable

interface SearchAction : Searchable {
    val label: String
    val icon: SearchActionIcon
    val iconColor: Int
    fun start(context: Context)
}

enum class SearchActionIcon(value: Int) {
    Search(0),
    Custom(1),
    Website(2),
    Alarm(3),
    Timer(4),
    Contact(5),
    Phone(6),
    Email(7),
    Message(8),
    Calendar(9),
    Translate(10),
}