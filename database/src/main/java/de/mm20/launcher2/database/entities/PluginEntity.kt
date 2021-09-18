package de.mm20.launcher2.database.entities

import androidx.room.Entity

@Entity(tableName = "Plugin", primaryKeys = ["packageName", "data"])
data class PluginEntity(
        val packageName: String,
        val data: String,
        val type: String
)