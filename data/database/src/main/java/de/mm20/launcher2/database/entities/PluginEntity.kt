package de.mm20.launcher2.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Plugins")
data class PluginEntity(
    @PrimaryKey val authority: String,
    val label: String,
    val description: String?,
    val packageName: String,
    val className: String,
    val type: String,
    val settingsActivity: String?,
    val enabled: Boolean,
)