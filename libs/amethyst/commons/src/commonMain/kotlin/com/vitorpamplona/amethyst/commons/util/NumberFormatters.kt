/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.util

import kotlin.math.roundToInt

/**
 * Formats a count to human-readable format with suffix (K, M, G).
 * Example: 1500 -> "1K items", 2500000 -> "2M items"
 */
fun countToHumanReadable(
    counter: Int,
    suffix: String,
): String =
    when {
        counter >= 1_000_000_000 -> "${(counter / 1_000_000_000f).roundToInt()}G $suffix"
        counter >= 1_000_000 -> "${(counter / 1_000_000f).roundToInt()}M $suffix"
        counter >= 1_000 -> "${(counter / 1_000f).roundToInt()}K $suffix"
        else -> "$counter $suffix"
    }

/**
 * Formats a count to human-readable format with suffix (K, M, G).
 */
fun countToHumanReadable(
    counter: Long,
    suffix: String,
): String =
    when {
        counter >= 1_000_000_000 -> "${(counter / 1_000_000_000f).roundToInt()}G $suffix"
        counter >= 1_000_000 -> "${(counter / 1_000_000f).roundToInt()}M $suffix"
        counter >= 1_000 -> "${(counter / 1_000f).roundToInt()}K $suffix"
        else -> "$counter $suffix"
    }

/**
 * Formats a count to human-readable format without suffix.
 * Example: 1500 -> "1K", 2500000 -> "2M"
 */
fun countToHumanReadable(counter: Int): String =
    when {
        counter >= 1_000_000_000 -> "${(counter / 1_000_000_000f).roundToInt()}G"
        counter >= 1_000_000 -> "${(counter / 1_000_000f).roundToInt()}M"
        counter >= 1_000 -> "${(counter / 1_000f).roundToInt()}K"
        else -> "$counter"
    }

/**
 * Formats a count to human-readable format without suffix.
 */
fun countToHumanReadable(counter: Long): String =
    when {
        counter >= 1_000_000_000 -> "${(counter / 1_000_000_000f).roundToInt()}G"
        counter >= 1_000_000 -> "${(counter / 1_000_000f).roundToInt()}M"
        counter >= 1_000 -> "${(counter / 1_000f).roundToInt()}K"
        else -> "$counter"
    }

/**
 * Formats byte count to human-readable format (KB, MB, GB).
 * Example: 1500 -> "1 KB", 2500000 -> "2 MB"
 */
fun countToHumanReadableBytes(counter: Int): String =
    when {
        counter >= 1_000_000_000 -> "${(counter / 1_000_000_000f).roundToInt()} GB"
        counter >= 1_000_000 -> "${(counter / 1_000_000f).roundToInt()} MB"
        counter >= 1_000 -> "${(counter / 1_000f).roundToInt()} KB"
        else -> "$counter B"
    }

/**
 * Formats byte count to human-readable format (KB, MB, GB).
 */
fun countToHumanReadableBytes(counter: Long): String =
    when {
        counter >= 1_000_000_000 -> "${(counter / 1_000_000_000f).roundToInt()} GB"
        counter >= 1_000_000 -> "${(counter / 1_000_000f).roundToInt()} MB"
        counter >= 1_000 -> "${(counter / 1_000f).roundToInt()} KB"
        else -> "$counter B"
    }

private const val NO_BREAK_SPACE = "\u00A0"

private fun roundedDiv(
    value: Long,
    divisor: Long,
): Long = (value + divisor / 2) / divisor

/**
 * A file size for people, in decimal units (1 MB = 1,000,000 bytes, as Android's own formatter does):
 * "850 KB", "12.4 MB", "140 MB", "1.2 GB". KB is always whole and anything under 1 KB reads "1 KB";
 * MB and GB keep one decimal below 100. A no-break space keeps the number and its unit on one line.
 */
fun formatFileSize(bytes: Long): String {
    val kb = roundedDiv(bytes, 1_000)
    if (kb < 1_000) return "${kb.coerceAtLeast(1)}${NO_BREAK_SPACE}KB"
    // Rounding can carry into the next unit (999,950 KB is "1.0 MB"), so pick the unit after rounding.
    val (unitBytes, unit) = if (roundedDiv(bytes, 1_000_000) < 1_000) 1_000_000L to "MB" else 1_000_000_000L to "GB"
    val tenths = roundedDiv(bytes, unitBytes / 10)
    return if (tenths < 1_000) "${tenths / 10}.${tenths % 10}$NO_BREAK_SPACE$unit" else "${roundedDiv(bytes, unitBytes)}$NO_BREAK_SPACE$unit"
}
