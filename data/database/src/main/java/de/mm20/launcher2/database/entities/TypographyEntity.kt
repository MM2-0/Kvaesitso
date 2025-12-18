package de.mm20.launcher2.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.mm20.launcher2.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@Entity(tableName = "Typography")
data class TypographyEntity(
    @Serializable(with = UUIDSerializer::class) @PrimaryKey val id: UUID,
    val name: String,

    val fonts: String? = null,
    val displayLarge: String? = null,
    val displayMedium: String? = null,
    val displaySmall: String? = null,
    val headlineLarge: String? = null,
    val headlineMedium: String? = null,
    val headlineSmall: String? = null,
    val titleLarge: String? = null,
    val titleMedium: String? = null,
    val titleSmall: String? = null,
    val bodyLarge: String? = null,
    val bodyMedium: String? = null,
    val bodySmall: String? = null,
    val labelLarge: String? = null,
    val labelMedium: String? = null,
    val labelSmall: String? = null,
    val emphasizedDisplayLarge: String? = null,
    val emphasizedDisplayMedium: String? = null,
    val emphasizedDisplaySmall: String? = null,
    val emphasizedHeadlineLarge: String? = null,
    val emphasizedHeadlineMedium: String? = null,
    val emphasizedHeadlineSmall: String? = null,
    val emphasizedTitleLarge: String? = null,
    val emphasizedTitleMedium: String? = null,
    val emphasizedTitleSmall: String? = null,
    val emphasizedBodyLarge: String? = null,
    val emphasizedBodyMedium: String? = null,
    val emphasizedBodySmall: String? = null,
    val emphasizedLabelLarge: String? = null,
    val emphasizedLabelMedium: String? = null,
    val emphasizedLabelSmall: String? = null,
)