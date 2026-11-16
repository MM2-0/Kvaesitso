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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey

/**
 * Shortens a hex key or npub for display.
 * Example: "npub1abcdefghijklmnop..." -> "npub1abc…mnop"
 *
 * @return Shortened string with ellipsis in the middle, or original if <= 16 chars
 */
fun String.toShortDisplay(prefixSize: Int = 0): String {
    if (length <= prefixSize + 16) return this
    return replaceRange(prefixSize + 8, length - 8, "…")
}

/**
 * Converts a ByteArray to a shortened hex display.
 */
fun ByteArray.toHexShortDisplay(): String = toHexKey().toShortDisplay()

/**
 * Shortens a HexKey for display.
 */
fun HexKey.toDisplayHexKey(): String = this.toShortDisplay()
