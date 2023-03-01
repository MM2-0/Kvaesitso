package de.mm20.launcher2.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "LaunchTimestamp")
data class SearchableLaunchTimestampEntity(
    @PrimaryKey @ColumnInfo("stamp") val timestampUnixMs: Long,
    val key: String
)
