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
package com.vitorpamplona.amethyst.commons.search

/**
 * The nip19 code the search box holds *in its entirety*, or null.
 *
 * `Nip19Parser` extracts a code from anywhere inside a string, which is right for finding a
 * mention in a note and wrong for reading a search box. Once `from:`/`to:` existed, "does this
 * text contain an npub" started answering yes for an author filter — so pasting a code and typing
 * a filter became indistinguishable, and the box acted on the filter as though it were a paste.
 *
 * Whole-input is the distinction that separates them: a paste is the only thing the reader typed,
 * while a token always carries its prefix and a filtered query almost always carries other words.
 *
 * Returns the code with any `nostr:` prefix stripped, ready to hand to a parser. Hex is not
 * considered here — an unprefixed 64-character string is ambiguous between a pubkey and an event
 * id, and the finders already resolve it both ways.
 */
fun wholeInputNip19(text: String?): String? {
    val trimmed = text?.trim()?.removePrefix("nostr:") ?: return null
    if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
    if (NIP19_PREFIXES.none { trimmed.startsWith(it, ignoreCase = true) }) return null
    return trimmed
}

private val NIP19_PREFIXES = listOf("npub1", "nprofile1", "note1", "nevent1", "naddr1", "nsec1")
