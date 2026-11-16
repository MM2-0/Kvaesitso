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
package com.vitorpamplona.amethyst.commons.nipsOnNostr

/**
 * Where the official NIPs live and how links and mentions name them. The repo names each NIP's
 * file by a two-character upper-case number (`01.md`, `7D.md`, `EE.md`).
 */
object NipLinks {
    const val REPO_BLOB = "https://github.com/nostr-protocol/nips/blob/master/"
    const val RAW_BASE = "https://raw.githubusercontent.com/nostr-protocol/nips/master/"
    const val INDEX_RAW_URL = RAW_BASE + "README.md"

    private val NUMBER = Regex("^[0-9A-F]{2}$")

    /** `1` -> `01`, `nip-7d` -> `7D`, `NIP 46` -> `46`; null when it isn't a NIP number. */
    fun normalize(input: String): String? {
        var cleaned = input.trim().uppercase()
        cleaned = cleaned.removePrefix("NIP").trimStart('-', ' ', '_')
        if (cleaned.length == 1) cleaned = "0$cleaned"
        return cleaned.takeIf { NUMBER.matches(it) }
    }

    fun rawUrl(number: String) = "$RAW_BASE$number.md"

    fun repoUrl(number: String) = "$REPO_BLOB$number.md"

    /**
     * The NIP a URL points at, for links that should open in the app's reader: the nips repo's
     * master branch (`github.com/nostr-protocol/nips/blob/master/44.md`, raw or not, with or
     * without an anchor) and `nips.nostr.com/44`. Null for anything else: the README, and a file
     * on another branch, which is a proposal the reader (it fetches master) would not show, so it
     * keeps opening on GitHub.
     */
    fun numberFromUrl(url: String): String? {
        val noScheme = url.substringAfter("://", url).removePrefix("www.")
        val path = noScheme.substringBefore('#').substringBefore('?').trimEnd('/')

        val file =
            when {
                path.startsWith("github.com/nostr-protocol/nips/blob/master/") -> path.removePrefix("github.com/nostr-protocol/nips/blob/master/")
                path.startsWith("raw.githubusercontent.com/nostr-protocol/nips/master/") -> path.removePrefix("raw.githubusercontent.com/nostr-protocol/nips/master/")
                path.startsWith("raw.githubusercontent.com/nostr-protocol/nips/refs/heads/master/") -> path.removePrefix("raw.githubusercontent.com/nostr-protocol/nips/refs/heads/master/")
                path.startsWith("nips.nostr.com/") -> path.removePrefix("nips.nostr.com/") + ".md"
                else -> return null
            }
        // Only a file at the top of the repo: `44.md`, not `some/dir/44.md`.
        if (file.contains('/') || !file.endsWith(".md")) return null
        return file.removeSuffix(".md").takeIf { it.length == 2 }?.let { normalize(it) }
    }

    /** The anchor of a NIP URL (`44.md#encryption` -> `encryption`), or null. */
    fun anchorFromUrl(url: String): String? = url.substringAfter('#', "").substringBefore('?').ifBlank { null }

    // `NIP-44`, `nip-7d`, then any closing punctuation the sentence put after it.
    private val MENTION = Regex("^NIP-([0-9A-Fa-f]{2})([.,;:!?)\\]'\"]*|'s)$", RegexOption.IGNORE_CASE)

    /** The NIP a single word of text names (`NIP-44,` -> `44` + `,`), or null. */
    fun mention(word: String): Pair<String, String>? {
        // Cheap gate first: this runs on every word of every note.
        if (word.length !in 6..10) return null
        val c = word[0]
        if (c != 'N' && c != 'n') return null
        val match = MENTION.matchEntire(word) ?: return null
        val number = normalize(match.groupValues[1]) ?: return null
        return number to match.groupValues[2]
    }
}
