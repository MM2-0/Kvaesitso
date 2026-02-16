package de.mm20.launcher2.preferences

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Represents different widget screen targets.
 * Each target corresponds to a separate widget area with its own scope.
 */
@Serializable
enum class WidgetScreenTarget {
    Widgets1,
    Widgets2,
    Widgets3,
    Widgets4;

    /**
     * Returns the UUID used as parent identifier for widget repository operations.
     * These are deterministic UUIDs generated using UUID v5 (name-based).
     */
    val scopeId: UUID
        get() = when (this) {
            Widgets1 -> UUID.fromString("00000000-0000-0000-0000-000000000001")
            Widgets2 -> UUID.fromString("00000000-0000-0000-0000-000000000002")
            Widgets3 -> UUID.fromString("00000000-0000-0000-0000-000000000003")
            Widgets4 -> UUID.fromString("00000000-0000-0000-0000-000000000004")
        }

    companion object {
        /**
         * The default widget screen target
         */
        val Default = Widgets1

        /**
         * Get list of available widget screen targets based on configured count.
         * @param count Number of widget screens to make available (1-4)
         * @return List of WidgetScreenTarget enum values
         */
        fun getAvailableTargets(count: Int): List<WidgetScreenTarget> {
            val all = listOf(Widgets1, Widgets2, Widgets3, Widgets4)
            return all.take(count.coerceIn(1, 4))
        }
    }
}
