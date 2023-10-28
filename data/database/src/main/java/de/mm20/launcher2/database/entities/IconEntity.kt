package de.mm20.launcher2.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Icons")
data class IconEntity(
        val type: String,
        val packageName: String? = null,
        val activityName: String? = null,
        val drawable: String?,
        val extras: String? = null,
        val iconPack: String,
        val name: String? = null,
        val themed: Boolean = false,
        @PrimaryKey(autoGenerate = true) val id : Long? = null
)