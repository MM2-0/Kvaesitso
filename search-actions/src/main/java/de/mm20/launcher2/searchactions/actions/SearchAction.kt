package de.mm20.launcher2.searchactions.actions

import android.content.Context
import de.mm20.launcher2.search.Searchable
import de.mm20.launcher2.searchactions.builders.WebsearchActionBuilder

interface SearchAction : Searchable {
    val label: String
    val icon: SearchActionIcon
    val iconColor: Int
    val customIcon: String?
    fun start(context: Context)
}

enum class SearchActionIcon {
    Search,
    Custom,
    Website,
    Alarm,
    Timer,
    Contact,
    Phone,
    Email,
    Message,
    Calendar,
    Translate;
    fun toInt(): Int {
        return when (this) {
            Search -> 0
            Custom -> 1
            Website -> 2
            Alarm -> 3
            Timer -> 4
            Contact -> 5
            Phone -> 6
            Email -> 7
            Message -> 8
            Calendar -> 9
            Translate -> 10
        }
    }
    companion object {
        fun fromInt(value: Int?): SearchActionIcon {
            return when (value) {
                1 -> Custom
                2 -> Website
                3 -> Alarm
                4 -> Timer
                5 -> Contact
                6 -> Phone
                7 -> Email
                8 -> Message
                9 -> Calendar
                10 -> Translate
                else -> Search
            }
        }
    }
}