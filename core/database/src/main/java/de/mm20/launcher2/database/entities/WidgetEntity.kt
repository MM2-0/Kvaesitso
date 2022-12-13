package de.mm20.launcher2.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "Widget")
data class WidgetEntity(
        val type: String,
        var data: String,
        var height: Int,
        var position: Int,
        val label: String = "",
        @PrimaryKey(autoGenerate = true) val id: Int? = null
)