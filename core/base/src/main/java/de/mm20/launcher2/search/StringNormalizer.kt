package de.mm20.launcher2.search

interface StringNormalizer {
    /**
     * A unique identifier for the normalization algorithm. Two normalizers that share the same ID must
     * return the same normalized string for the same input.
     */
    val id: String

    fun normalize(input: String): String
}