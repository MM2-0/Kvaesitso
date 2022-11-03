package de.mm20.launcher2.database.entities

data class SearchActionEntity(
    val type: String,
    val data: String? = null,
    val options: String? = null,
    val position: Int,
)