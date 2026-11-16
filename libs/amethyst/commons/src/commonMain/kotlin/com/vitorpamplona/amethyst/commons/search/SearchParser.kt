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

import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec
import com.vitorpamplona.quartz.utils.Hex

/**
 * Parses search input for direct-lookup entries: npub, nprofile, nsec
 * (extracts pubkey), note, nevent, naddr, and 64-char hex keys.
 *
 * Hashtags are intentionally NOT returned here — they are handled by
 * [QueryParser], which extracts `#xxx` tokens into the query's hashtag
 * filter so the normal results pipeline drives a tag-filtered search.
 *
 * Shared between Android and Desktop for consistent Bech32 parsing.
 */
fun parseSearchInput(input: String): List<SearchResult> {
    if (input.isBlank()) return emptyList()

    val trimmed = input.trim()
    val results = mutableListOf<SearchResult>()

    // Whole-input only. `Nip19Parser` extracts a code from anywhere in a string, so without this
    // an author filter — `from:npub1…` — read as a pasted profile and produced a direct-lookup
    // row for somebody the reader was only filtering by.
    val parsed = wholeInputNip19(trimmed)?.let { Nip19Parser.uriToRoute(it)?.entity }
    if (parsed != null) {
        when (parsed) {
            is NPub -> {
                results.add(
                    SearchResult.UserResult(
                        pubKeyHex = parsed.hex,
                        displayId = trimmed.take(20) + "...",
                    ),
                )
            }

            is NProfile -> {
                results.add(
                    SearchResult.UserResult(
                        pubKeyHex = parsed.hex,
                        displayId = trimmed.take(20) + "...",
                    ),
                )
            }

            is NSec -> {
                results.add(
                    SearchResult.UserResult(
                        pubKeyHex = parsed.toPubKeyHex(),
                        displayId = "User from nsec",
                    ),
                )
            }

            is NNote -> {
                results.add(
                    SearchResult.NoteResult(
                        noteIdHex = parsed.hex,
                        displayId = trimmed.take(20) + "...",
                    ),
                )
            }

            is NEvent -> {
                results.add(
                    SearchResult.NoteResult(
                        noteIdHex = parsed.hex,
                        displayId = trimmed.take(20) + "...",
                    ),
                )
            }

            is NAddress -> {
                results.add(
                    SearchResult.AddressResult(
                        kind = parsed.kind,
                        pubKeyHex = parsed.author,
                        dTag = parsed.dTag,
                        displayId = trimmed.take(20) + "...",
                    ),
                )
            }

            else -> { }
        }
        return results
    }

    // Try to parse as hex (64-char pubkey or event id)
    if (trimmed.length == 64 && Hex.isHex(trimmed)) {
        results.add(
            SearchResult.UserResult(
                pubKeyHex = trimmed,
                displayId = trimmed.take(16) + "..." + trimmed.takeLast(8),
            ),
        )
        results.add(
            SearchResult.NoteResult(
                noteIdHex = trimmed,
                displayId = trimmed.take(16) + "..." + trimmed.takeLast(8),
            ),
        )
        return results
    }

    return results
}
