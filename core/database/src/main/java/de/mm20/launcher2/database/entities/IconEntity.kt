package de.mm20.launcher2.database.entities

import android.content.ComponentName
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Icons")
data class IconEntity(
        val type: String,
        val componentName: ComponentName?,
        val drawable: String?,
        val iconPack: String,
        val name: String?,
        val themed: Boolean = false,
        @PrimaryKey(autoGenerate = true) val id : Long? = null
)