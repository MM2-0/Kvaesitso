package de.mm20.launcher2.search

import kotlinx.serialization.Serializable

@Serializable
data class SearchFilters(
    val events: Boolean = false,
    val websites: Boolean = false,
    val articles: Boolean = false,
    val places: Boolean = false,
    val files: Boolean = false,
) {
    private val categories = listOf(websites, articles, places, files, events)
    val enabledCategories: Int
        get() = categories.count { it }

    fun toggleWebsites(): SearchFilters = SearchFilters(websites = !websites)
    fun toggleArticles(): SearchFilters = SearchFilters(articles = !articles)
    fun togglePlaces(): SearchFilters = SearchFilters(places = !places)
    fun toggleFiles(): SearchFilters = SearchFilters(files = !files)
    fun toggleEvents(): SearchFilters = SearchFilters(events = !events)
}
