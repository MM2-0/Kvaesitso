package de.mm20.launcher2.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "SearchAction")
data class SearchActionEntity(
    @PrimaryKey val position: Int,
    val type: String,
    val data: String? = null,
    val label: String? = null,
    val icon: Int? = null,
    val color: Int? = null,
    val customIcon: String? = null,
    val options: String? = null,
)