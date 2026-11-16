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
package com.vitorpamplona.amethyst.commons.richtext

import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.nip29RelayGroups.GroupInviteLink
import com.vitorpamplona.quartz.utils.DualCase
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.startsWithAny
import com.vitorpamplona.quartz.utils.urldetector.Url
import com.vitorpamplona.quartz.utils.urldetector.detection.UrlDetector
import kotlinx.coroutines.CancellationException

@Stable
class Urls(
    val withScheme: Set<String> = emptySet(),
    val withoutScheme: Set<String> = emptySet(),
    val emails: Set<String> = emptySet(),
    val bech32s: Set<String> = emptySet(),
    val relayUrls: Set<String> = emptySet(),
    val blossomUris: Set<String> = emptySet(),
    // NIP-29 group invite links in the `<relay>'<groupId>[?code=<code>]` form (Wisp/0xchat).
    // The literal, whole-span text so fixMissingSpaces can keep it atomic and wordIdentifier
    // can match it. See [UrlParser.parseValidUrls] for how these are recovered from the
    // relay URLs the detector already found.
    val groupLinks: Set<String> = emptySet(),
)

val httpScheme = listOf(DualCase("http"))
val websocketScheme = listOf(DualCase("ws"))
val nostrScheme = listOf(DualCase("nostr"))
val blossomScheme = listOf(DualCase("blossom"))

class UrlParser {
    fun Char.isAsciiLetter(): Boolean = (this in 'a'..'z' || this in 'A'..'Z')

    fun Url.isValidTopLevelDomain(): Boolean {
        // IPv6 literal hosts are bracketed (e.g. [2001:db8::1]) and have no dotted TLD, so the
        // letter-first TLD rule below would wrongly reject them. The detector already validated
        // the bracketed address as a syntactically correct IPv6 literal, so accept it directly.
        if (host.startsWith('[')) return true

        /*
        According to the TLD Applicant Guidebook published June 2012, ICANN does not allow numbers in TLDs.
         */
        val startOfTopDomain = host.lastIndexOf('.') + 1
        return if (startOfTopDomain < host.length) {
            val topLevelDomain = host.substring(startOfTopDomain)
            topLevelDomain.isNotEmpty() && topLevelDomain[0].isAsciiLetter()
        } else {
            false
        }
    }

    fun Url.wroteWithSchema(): Boolean = urlMarker.hasScheme()

    fun Url.isEmail(): Boolean =
        urlMarker.hasUsernamePassword() &&
            !urlMarker.hasQuery() &&
            !urlMarker.hasFragment() &&
            originalUrl.contains('@') &&
            path == "/"

    fun parseValidUrls(content: String): Urls {
        val urls = UrlDetector(content).detect()

        val completeUrls = mutableSetOf<String>()
        val urlsWithoutScheme = mutableSetOf<String>()
        val emails = mutableSetOf<String>()
        val bech32 = mutableSetOf<String>()
        val relays = mutableSetOf<String>()
        val blossom = mutableSetOf<String>()
        val groupLinks = mutableSetOf<String>()

        urls.forEach { url ->
            try {
                if (url.isValidTopLevelDomain()) {
                    if (url.wroteWithSchema()) {
                        if (url.originalUrl.startsWithAny(httpScheme)) {
                            // quick exit
                            completeUrls.add(url.originalUrl)
                        } else if (url.originalUrl.startsWithAny(nostrScheme)) {
                            bech32.add(url.originalUrl)
                        } else if (url.originalUrl.startsWithAny(websocketScheme)) {
                            relays.add(url.originalUrl)
                            // A relay URL glued to `'<groupId>` in the source is a NIP-29 group
                            // invite link. The detector always ends the host at the apostrophe
                            // (host names can't contain `'`), so we recover the whole
                            // `<relay>'<groupId>[?code=<code>]` span here rather than teaching the
                            // URL grammar about it — see the peek in collectGroupLinks.
                            collectGroupLinks(content, url.originalUrl, groupLinks)
                        } else if (url.originalUrl.startsWithAny(blossomScheme)) {
                            blossom.add(url.originalUrl)
                        } else {
                            completeUrls.add(url.originalUrl)
                        }
                    } else {
                        // emails are understood as urls from the detector.
                        if (url.isEmail()) {
                            Patterns.EMAIL_ADDRESS.findAll(url.originalUrl).forEach {
                                emails.add(it.value)
                            }
                        } else {
                            urlsWithoutScheme.add(url.originalUrl)
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("UrlParser", "Trying to parse url `${url.originalUrl}` from `$content`", e)
            }
        }

        return Urls(
            withScheme = completeUrls,
            withoutScheme = urlsWithoutScheme,
            emails = emails,
            bech32s = bech32,
            relayUrls = relays,
            blossomUris = blossom,
            groupLinks = groupLinks,
        )
    }

    /**
     * For every occurrence of [relayUrl] in [content], check whether it is immediately
     * followed by `'<groupId>[?code=<code>]` and, if so, add the full literal span to
     * [out]. This runs only for the handful of websocket URLs the detector already found,
     * so a note with no relay link costs nothing extra.
     */
    private fun collectGroupLinks(
        content: String,
        relayUrl: String,
        out: MutableSet<String>,
    ) {
        var from = 0
        while (true) {
            val idx = content.indexOf(relayUrl, from)
            if (idx < 0) break
            val end = idx + relayUrl.length
            from = end
            if (end < content.length && content[end] == '\'') {
                val suffixLen = GroupInviteLink.suffixLength(content, end + 1)
                if (suffixLen > 0) {
                    out.add(content.substring(idx, end + 1 + suffixLen))
                }
            }
        }
    }
}
