package de.mm20.launcher2.serialization

/**
 * Default JSON serializer configurations
 */
object Json {
    /**
     * A very lenient JSON serializer that allows unknown keys, null values, and coerces input values.
     */
    val Lenient = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
        coerceInputValues = true
    }
}