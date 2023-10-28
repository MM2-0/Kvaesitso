package de.mm20.launcher2.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID


@Entity(tableName = "Widget")
data class WidgetEntity(
        val type: String,
        var config: String?,
        var position: Int,
        @PrimaryKey val id: UUID,
        val parentId: UUID? = null,
)

/**
 * Partial entity for updating and deleting
 */
data class PartialWidgetEntity(
        val type: String,
        var config: String?,
        @PrimaryKey val id: UUID,
)