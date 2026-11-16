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
package com.vitorpamplona.amethyst.commons.blurhash

import kotlin.math.ceil
import kotlin.math.log

object Base83 {
    fun encode(value: Long): String =
        if (value > 82) {
            encode(value, ceil(log((value + 1).toDouble(), 83.0)).toInt())
        } else {
            encode(value, 1)
        }

    fun encode(
        value: Long,
        length: Int,
    ): String {
        val buffer = CharArray(length)
        encode(value, length, buffer, 0)
        return buffer.concatToString()
    }

    fun encode(
        value: Long,
        length: Int,
        buffer: CharArray,
        offset: Int,
    ) {
        var exp = 1L
        for (i in 1..length) {
            val digit = (value / exp % 83).toInt()
            buffer[offset + length - i] = ALPHABET[digit]
            exp *= 83
        }
    }

    fun decodeAt(
        str: String,
        at: Int = 0,
    ): Int = valueOf(str[at])

    fun decodeFixed2(
        str: String,
        from: Int = 0,
    ): Int = valueOf(str[from]) * 83 + valueOf(str[from + 1])

    fun decode(
        str: String,
        from: Int = 0,
        to: Int = str.length,
    ): Int {
        var result = 0
        for (i in from until to) {
            result = result * 83 + valueOf(str[i])
        }
        return result
    }

    val ALPHABET: CharArray = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~".toCharArray()

    /**
     * Value of [c] in the base-83 alphabet, or 0 for any character outside it.
     * Blurhashes come from attacker-controlled `imeta` tags, so a non-Latin-1
     * character must degrade to a wrong colour, not an
     * ArrayIndexOutOfBoundsException out of the image fetcher.
     */
    private fun valueOf(c: Char): Int {
        val code = c.code
        return if (code < charMap.size) charMap[code] else 0
    }

    private val charMap =
        ALPHABET
            .mapIndexed { i, c -> c.code to i }
            .toMap()
            .let { charMap ->
                Array(255) {
                    charMap[it] ?: 0
                }
            }
}
