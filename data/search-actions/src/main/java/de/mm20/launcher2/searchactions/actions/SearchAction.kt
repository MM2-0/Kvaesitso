package de.mm20.launcher2.searchactions.actions

import android.content.Context
import de.mm20.launcher2.search.Searchable

interface SearchAction : Searchable {
    val label: String
    val icon: SearchActionIcon
    val iconColor: Int
    val customIcon: String?
    fun start(context: Context)
}

enum class SearchActionIcon(private val value: Int) {
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
    WebSearch(11),
    PersonSearch(12),
    StatsSearch(13),
    SearchPage(14),
    SearchList(15),
    ImageSearch(16),
    Location(17),
    Movie(18),
    Music(19),
    Game(20),
    Note(21),
    Share(22);
    fun toInt(): Int {
        return value
    }
    companion object {
        fun fromInt(value: Int?): SearchActionIcon {
            return entries.firstOrNull { it.value == value } ?: Search
        }
    }
}