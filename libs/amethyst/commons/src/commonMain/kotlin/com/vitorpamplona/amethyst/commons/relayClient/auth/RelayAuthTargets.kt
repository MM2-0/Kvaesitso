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
package com.vitorpamplona.amethyst.commons.relayClient.auth

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip42RelayAuth.tags.RelayTag

/**
 * One `relay` tag of a NIP-42 auth event (kind 22242), as a person deciding whether to sign it
 * must see it.
 *
 * The requesting app writes that tag verbatim, and a fresh signature over it lets the app log in
 * to that relay as the user (see `NostrSignerPermissionLedger` on why 22242 is never auto-allowed).
 * So the address shown is the one being signed: never the normalizer's repaired version (it adds
 * schemes, trims `%20`, splits run-on URLs), and never a value whose hidden characters could make
 * it read as a different relay.
 */
@Immutable
data class RelayAuthTarget(
    /** The tag value, with invisible and direction-changing characters escaped as `\uXXXX`. */
    val display: String,
    /** For the relay's icon and NIP-11 info only. Null when the value is not a relay we could reach. */
    val url: NormalizedRelayUrl?,
    /** The value is not a plain relay address: it does not normalize, needed rewriting, or hid characters. */
    val unusual: Boolean,
)

/** Every `relay` tag of a NIP-42 auth event or template, in tag order. Usually just one. */
fun TagArray.relayAuthTargets(): List<RelayAuthTarget> =
    mapNotNull { tag ->
        if (tag.size > 1 && tag[0] == RelayTag.TAG_NAME && tag[1].isNotEmpty()) relayAuthTarget(tag[1]) else null
    }

fun relayAuthTarget(raw: String): RelayAuthTarget {
    val escaped = escapeHiddenChars(raw)
    val url = RelayUrlNormalizer.normalizeOrNull(raw)
    val plain = url != null && escaped == raw && sameAddress(raw, url)
    return RelayAuthTarget(
        // Only the default `wss://` is dropped: an insecure `ws://` stays visible.
        display = if (plain) url.url.removePrefix("wss://").removeSuffix("/") else escaped,
        url = url,
        unusual = !plain,
    )
}

/** Only scheme/host case and a trailing slash may differ; anything else means the normalizer rewrote it. */
private fun sameAddress(
    raw: String,
    url: NormalizedRelayUrl,
) = raw.trimEnd('/').equals(url.url.trimEnd('/'), ignoreCase = true)

private fun isHidden(c: Char) =
    c.isISOControl() ||
        c in '\u200B'..'\u200F' ||
        c in '\u202A'..'\u202E' ||
        c in '\u2060'..'\u2069' ||
        c == '\u061C' ||
        c == '\uFEFF'

private fun escapeHiddenChars(raw: String): String {
    if (raw.none(::isHidden)) return raw
    return buildString(raw.length + 16) {
        raw.forEach { c ->
            if (isHidden(c)) {
                append("\\u")
                append(
                    c.code
                        .toString(16)
                        .uppercase()
                        .padStart(4, '0'),
                )
            } else {
                append(c)
            }
        }
    }
}
