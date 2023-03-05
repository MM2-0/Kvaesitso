package de.mm20.launcher2.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Searchable")
data class SavedSearchableEntity(
        @PrimaryKey val key: String,
        val type: String,
        @ColumnInfo(name = "searchable") val serializedSearchable: String,
        var launchCount: Int,
        @ColumnInfo(name = "pinned") var pinPosition: Int,
        var hidden: Boolean,
        @ColumnInfo(defaultValue = "0.0") var weight: Double
)
