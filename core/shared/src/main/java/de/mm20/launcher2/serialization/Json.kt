package de.mm20.launcher2.serialization

/**
 * Default Json serializer configurations
 */
object Json {
    /**
     * A Json serializer configuration that aims to be as forgiving as possible.
     * Suitable for external data sources, and legacy data that may not be fully compliant with the current schema.
     */
    val Lenient = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
        coerceInputValues = true
    }
}