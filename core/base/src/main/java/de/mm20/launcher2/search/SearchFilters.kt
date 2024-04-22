package de.mm20.launcher2.search

import de.mm20.launcher2.ktx.toInt
import kotlinx.serialization.Serializable

@Serializable
data class SearchFilters(
    val allowNetwork: Boolean = false,
    val hiddenItems: Boolean = false,
    val apps: Boolean = true,
    val websites: Boolean = true,
    val articles: Boolean = true,
    val places: Boolean = true,
    val files: Boolean = true,
    val shortcuts: Boolean = true,
    val contacts: Boolean = true,
    val events: Boolean = true,
    val tools: Boolean = true,
    val settings: Boolean = true,
) {
    val allCategoriesEnabled
        get() = apps && websites && articles && places && files && shortcuts && contacts && events && tools && settings

    val enabledCategories: Int
        get() = apps.toInt() + websites.toInt() + articles.toInt() + places.toInt() + files.toInt() + shortcuts.toInt() + contacts.toInt() + events.toInt() + tools.toInt() + settings.toInt()
}
