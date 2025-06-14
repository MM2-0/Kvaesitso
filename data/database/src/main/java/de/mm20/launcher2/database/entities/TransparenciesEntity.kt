package de.mm20.launcher2.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "Transparencies")
data class TransparenciesEntity(
    @PrimaryKey val id: UUID,
    val name: String,

    val background: Float?,
    val surface: Float?,
    val elevatedSurface: Float?,
)