package de.mm20.launcher2.themes.shapes

import de.mm20.launcher2.database.entities.ShapesEntity
import de.mm20.launcher2.serialization.UUIDSerializer
import de.mm20.launcher2.themes.ShapeSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Shapes(
    @Serializable(with = UUIDSerializer::class) val id: UUID = UUID.randomUUID(),
    val builtIn: Boolean = false,
    val name: String,
    val baseShape: Shape = Shape(
        corners = CornerStyle.Rounded,
        radii = intArrayOf(12, 12, 12, 12),
    ),
    val extraSmall: Shape? = null,
    val small: Shape? = null,
    val medium: Shape? = null,
    val large: Shape? = null,
    val largeIncreased: Shape? = null,
    val extraLarge: Shape? = null,
    val extraLargeIncreased: Shape? = null,
    val extraExtraLarge: Shape? = null,
) {
    constructor(entity: ShapesEntity) : this(
        id = entity.id,
        builtIn = false,
        name = entity.name,
        baseShape = Shape.fromString(entity.baseShape) ?: Shape(
            corners = CornerStyle.Rounded,
            radii = intArrayOf(12, 12, 12, 12)
        ),
        extraSmall = Shape.fromString(entity.extraSmall),
        small = Shape.fromString(entity.small),
        medium = Shape.fromString(entity.medium),
        large = Shape.fromString(entity.large),
        largeIncreased = Shape.fromString(entity.largeIncreased),
        extraLarge = Shape.fromString(entity.extraLarge),
        extraLargeIncreased = Shape.fromString(entity.extraLargeIncreased),
        extraExtraLarge = Shape.fromString(entity.extraExtraLarge),
    )

    internal fun toEntity(): ShapesEntity {
        return ShapesEntity(
            id = id,
            name = name,
            baseShape = baseShape.toString(),
            extraSmall = extraSmall?.toString(),
            small = small?.toString(),
            medium = medium?.toString(),
            large = large?.toString(),
            largeIncreased = largeIncreased?.toString(),
            extraLarge = extraLarge?.toString(),
            extraLargeIncreased = extraLargeIncreased?.toString(),
            extraExtraLarge = extraExtraLarge?.toString(),
        )
    }
}

@Serializable(with = ShapeSerializer::class)
data class Shape(
    /**
     * The style of the corners.
     * null to inherit the corner style from the base shape.
     */
    val corners: CornerStyle? = null,
    /**
     * Radii in dp, in the order of top-start, top-end, bottom-end, bottom-start.
     * null to inherit the radius from the base shape.
     */
    val radii: IntArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Shape) return false
        return corners == other.corners &&
                radii.contentEquals(other.radii)
    }

    override fun hashCode(): Int {
        var result = corners.hashCode()
        result = 31 * result + radii.contentHashCode()
        return result
    }

    override fun toString(): String {
        val type = when (corners) {
            CornerStyle.Rounded -> "r"
            CornerStyle.Cut -> "c"
            null -> "$"
        }
        val radii = radii?.joinToString("|") ?: ""
        return "$type.${radii}"
    }

    companion object {
        fun fromString(string: String?): Shape? {

            if (string == null) return null
            val parts = string.split('.')
            val corners = when (parts[0]) {
                "r" -> CornerStyle.Rounded
                "c" -> CornerStyle.Cut
                else -> null
            }
            val radii = if (parts.size > 1 && parts[1].isNotEmpty()) {
                parts[1].split("|").map { it.toInt() }.toIntArray()
            } else {
                null
            }
            return Shape(corners, radii)
        }
    }
}

enum class CornerStyle {
    Rounded,
    Cut,
}