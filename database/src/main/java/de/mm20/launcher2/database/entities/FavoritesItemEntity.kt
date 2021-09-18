package de.mm20.launcher2.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Searchable")
data class FavoritesItemEntity(
        @PrimaryKey val key: String,
        @ColumnInfo(name = "searchable") val serializedSearchable: String,
        var launchCount: Int,
        @ColumnInfo(name = "pinned") var pinPosition: Int,
        var hidden: Boolean
)
