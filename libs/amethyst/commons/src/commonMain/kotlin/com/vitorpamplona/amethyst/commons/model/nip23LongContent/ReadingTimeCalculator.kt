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
package com.vitorpamplona.amethyst.commons.model.nip23LongContent

import kotlin.math.ceil
import kotlin.math.max

/**
 * Calculates estimated reading time for markdown content.
 * Uses 238 WPM for prose (Brysbaert 2019), 80 WPM for code blocks,
 * and Medium's image decay formula (12 sec first, -1 each, min 3).
 */
object ReadingTimeCalculator {
    private const val PROSE_WPM = 238.0
    private const val CODE_WPM = 80.0

    private val WHITESPACE_REGEX = "\\s+".toRegex()
    private val IMAGE_REGEX = Regex("!\\[.*?]\\(.*?\\)")
    private val IMAGE_STRIP_REGEX = Regex("!\\[.*?]\\(.*?\\)")
    private val LINK_REGEX = Regex("\\[([^]]*)]\\([^)]*\\)")
    private val FORMATTING_REGEX = Regex("[*_~`#>]")
    private val LIST_MARKER_REGEX = Regex("^-\\s+|^\\d+\\.\\s+")
    private val HORIZONTAL_RULE_REGEX = Regex("^---+$|^\\*\\*\\*+$")

    fun calculate(markdownContent: String): Int {
        var proseWords = 0
        var codeWords = 0
        var imageCount = 0
        var inCodeBlock = false

        markdownContent.lines().forEach { line ->
            val trimmed = line.trim()

            if (trimmed.startsWith("```")) {
                inCodeBlock = !inCodeBlock
                return@forEach
            }

            if (inCodeBlock) {
                codeWords += trimmed.split(WHITESPACE_REGEX).count { it.isNotBlank() }
                return@forEach
            }

            // Count images
            val imageMatches = IMAGE_REGEX.findAll(trimmed)
            imageCount += imageMatches.count()

            // Strip markdown syntax for word counting
            val stripped =
                trimmed
                    .replace(IMAGE_STRIP_REGEX, "") // images
                    .replace(LINK_REGEX, "$1") // links -> text only
                    .replace(FORMATTING_REGEX, "") // formatting
                    .replace(LIST_MARKER_REGEX, "") // list markers
                    .replace(HORIZONTAL_RULE_REGEX, "") // horizontal rules

            proseWords += stripped.split(WHITESPACE_REGEX).count { it.isNotBlank() }
        }

        // Medium's image time decay: 12 sec first, -1 each, min 3
        val imageSeconds = (0 until imageCount).sumOf { max(12 - it, 3) }

        val totalMinutes = (proseWords / PROSE_WPM) + (codeWords / CODE_WPM) + (imageSeconds / 60.0)
        return max(1, ceil(totalMinutes).toInt())
    }
}
