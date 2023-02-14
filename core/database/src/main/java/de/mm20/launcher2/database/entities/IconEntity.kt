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
        val themed: Boolean,
        val name: String?,
        @PrimaryKey(autoGenerate = true) val id : Long? = null
)