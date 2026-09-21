package de.mm20.launcher2.database.entities

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "IconPack")
data class IconPackEntity(
        val name: String,
        @PrimaryKey val packageName: String,
        val version: String,
        var scale: Float = 1f,
        val themed: Boolean = false,
)