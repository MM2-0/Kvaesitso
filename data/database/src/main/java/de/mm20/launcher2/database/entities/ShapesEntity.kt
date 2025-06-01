package de.mm20.launcher2.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "Shapes")
data class ShapesEntity(
    @PrimaryKey val id: UUID,
    val name: String,

    val baseShape: String,

    val extraSmall: String? = null,
    val small: String? = null,
    val medium: String? = null,
    val large: String? = null,
    val largeIncreased: String? = null,
    val extraLarge: String? = null,
    val extraLargeIncreased: String? = null,
    val extraExtraLarge: String? = null,
)