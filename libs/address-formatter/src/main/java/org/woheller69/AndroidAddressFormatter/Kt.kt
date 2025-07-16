package org.woheller69.AndroidAddressFormatter

/**
 * Make Kotlin functions accessible from Java.
 */
internal object Kt {
    @JvmStatic
    inline fun toIntOrNull(string: String): Int? {
        return string.toIntOrNull()
    }
}