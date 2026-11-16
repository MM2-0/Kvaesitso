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
package com.vitorpamplona.amethyst.commons.moderation.notifications

private val CONTROL_CHARS = Regex("\\p{Cntrl}")
private val RTL_OVERRIDES = Regex("[\u202A-\u202E\u2066-\u2069]")
private val ZERO_WIDTH = Regex("[\u200B-\u200D\uFEFF]")
private val WHITESPACE = Regex("\\s+")
private val URL_PATTERN = Regex("https?://\\S+")

fun sanitizeForToast(
    raw: String,
    maxLen: Int = 120,
): String =
    raw
        .replace(CONTROL_CHARS, " ")
        .replace(RTL_OVERRIDES, "")
        .replace(ZERO_WIDTH, "")
        .replace(WHITESPACE, " ")
        .trim()
        .take(maxLen)

fun sanitizeTitleForToast(
    displayName: String,
    maxLen: Int = 60,
): String =
    sanitizeForToast(displayName, maxLen)
        .replace(URL_PATTERN, "")
        .trim()
