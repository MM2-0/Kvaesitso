package de.mm20.launcher2.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.mm20.launcher2.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@Entity(tableName = "Transparencies")
data class TransparenciesEntity(
    @Serializable(with = UUIDSerializer::class) @PrimaryKey val id: UUID,
    val name: String,

    val background: Float?,
    val surface: Float?,
    val elevatedSurface: Float?,
)