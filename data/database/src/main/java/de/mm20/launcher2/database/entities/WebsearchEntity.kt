package de.mm20.launcher2.database.entities

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "Websearch")
data class WebsearchEntity(
        var urlTemplate: String,
        var label: String,
        var color: Int,
        var icon: String?,
        var encoding: Int?,
        @PrimaryKey(autoGenerate = true) val id: Long?
)