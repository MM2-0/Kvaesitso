package de.mm20.launcher2.search

import kotlinx.serialization.Serializable

@Serializable
data class SearchFilters(
    val events: Boolean = false,
    val websites: Boolean = false,
    val articles: Boolean = false,
    val places: Boolean = false,
    val files: Boolean = false,
)
