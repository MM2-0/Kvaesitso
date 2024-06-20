package de.mm20.launcher2.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Searchable")
data class SavedSearchableEntity(
        @PrimaryKey val key: String,
        val type: String,
        @ColumnInfo(name = "searchable") val serializedSearchable: String,
        @ColumnInfo(defaultValue = "0") val launchCount: Int,
        @ColumnInfo(defaultValue = "0") val pinPosition: Int,
        @ColumnInfo(name="hidden", defaultValue = "0") val visibility: Int,
        @ColumnInfo(defaultValue = "0.0") val weight: Double
)

data class SavedSearchableUpdatePinEntity(
        val key: String,
        val type: String,
        @ColumnInfo(name = "searchable") val serializedSearchable: String,
        val pinPosition: Int? = null,
)

data class SavedSearchableUpdateContentEntity(
        val key: String,
        val type: String,
        @ColumnInfo(name = "searchable") val serializedSearchable: String,
)