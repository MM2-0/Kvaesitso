package de.mm20.launcher2.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Websearch")
data class WebsearchEntity(
        var urlTemplate: String,
        var label: String,
        var color: Int,
        var icon: String?,
        var encoding: Int?,
        @PrimaryKey(autoGenerate = true) val id: Long?
)