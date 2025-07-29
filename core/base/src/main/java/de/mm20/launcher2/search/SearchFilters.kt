package de.mm20.launcher2.search

import kotlinx.serialization.Serializable

@Serializable
data class SearchFilters(
    val apps: Boolean = true,
    val shortcuts: Boolean = true,
    val contacts: Boolean = true,
    val tools: Boolean = true,

    val events: Boolean = false,
    val websites: Boolean = false,
    val articles: Boolean = false,
    val places: Boolean = false,
    val files: Boolean = false,
) {
    private val categories = listOf(apps, websites, articles, places, files, shortcuts, contacts, events, tools)

    val allCategoriesEnabled
        get() = categories.all { it }

    val enabledCategories: Int
        get() = categories.count { it }
}
