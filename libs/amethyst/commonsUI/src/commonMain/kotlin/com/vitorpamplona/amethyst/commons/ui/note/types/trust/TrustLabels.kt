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
package com.vitorpamplona.amethyst.commons.ui.note.types.trust

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.util.countToHumanReadable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip85TrustedAssertions.AssertionMetric
import com.vitorpamplona.quartz.utils.Hex

private val COUNT_SUFFIX = Regex("_cnt$")
private val CAMEL_HUMP = Regex("([a-z])([A-Z])")

/**
 * A metric tag's name as words, for the ones no label is written for:
 * `zap_cnt` -> "Zaps", `personalizedGrapeRank_influence` -> "Personalized grape rank influence".
 * The same rule Brainstorm's `metricNoun` uses, so both clients name an unknown metric alike.
 */
fun metricWords(name: String): String {
    val words =
        name
            .replace(COUNT_SUFFIX, "s")
            .replace(CAMEL_HUMP, "$1 $2")
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
            .lowercase()
    return words.replaceFirstChar { it.uppercase() }
}

/**
 * A metric's value for a stat tile: whole numbers below ten thousand in full (a follower count of
 * 1,234 should not read "1K"), larger ones compact, and decimals exactly as published.
 */
fun formatMetricValue(metric: AssertionMetric): String {
    if (!metric.isWhole()) return metric.value
    val long = metric.value.toLongOrNull() ?: return metric.value
    return if (long in -9_999..9_999) long.toString() else countToHumanReadable(long)
}

/**
 * What one kind-10040 row delegates: `30382:rank` (kind 30382, qualifier `rank`), a bare
 * Trusted List kind `30392`, a D-list assistant entry `39998:dlist-header`, or anything else
 * that begins with a kind (kept [raw] so an unknown entry still shows as written).
 */
@Immutable
data class DesignationService(
    val raw: String,
    val kind: Int?,
    val qualifier: String?,
)

/** One provider a 10040 designates, with every service it is designated for, in tag order. */
@Immutable
data class DesignatedProvider(
    val pubKey: HexKey,
    val relay: NormalizedRelayUrl?,
    val services: List<DesignationService>,
    val isPrivate: Boolean,
)

private fun parseDesignationRow(tag: Array<String>): Triple<DesignationService, HexKey, NormalizedRelayUrl?>? {
    if (tag.size < 2) return null
    val what = tag[0]
    if (what.isEmpty() || !what[0].isDigit()) return null
    val pubKey = tag[1]
    if (pubKey.length != 64 || !Hex.isHex64(pubKey)) return null
    val relay = tag.getOrNull(2)?.takeIf { it.isNotBlank() }?.let { RelayUrlNormalizer.normalizeOrNull(it) }
    val kind = what.substringBefore(':').toIntOrNull()
    val qualifier = if (what.contains(':')) what.substringAfter(':').ifEmpty { null } else null
    return Triple(DesignationService(what, kind, qualifier), pubKey.lowercase(), relay)
}

/**
 * The rows of a Treasure Map (kind 10040) grouped by provider: one entry per (pubkey, relay),
 * listing what it is designated for. A row is anything shaped `[<kind…>, <64-hex pubkey>, <relay>?]`,
 * so NIP-85's `30382:rank`, Trusted Lists' bare `30392` and the D-list assistant's
 * `39998:dlist-header` all show, while `["client", …]` and friends do not. A missing or
 * unparseable relay keeps the row: the pubkey is the part a reader cannot do without.
 *
 * [privateTags] are the decrypted half, when the viewer could read it; their providers are kept
 * apart from the public ones so the card can say which delegations are private.
 */
fun designatedProviders(
    publicTags: TagArray,
    privateTags: TagArray? = null,
): List<DesignatedProvider> {
    val out = LinkedHashMap<Triple<HexKey, String, Boolean>, MutableList<DesignationService>>()
    val relays = HashMap<Triple<HexKey, String, Boolean>, NormalizedRelayUrl?>()

    fun collect(
        tags: TagArray,
        isPrivate: Boolean,
    ) {
        for (tag in tags) {
            val (service, pubKey, relay) = parseDesignationRow(tag) ?: continue
            val key = Triple(pubKey, relay?.url ?: "", isPrivate)
            val services = out.getOrPut(key) { mutableListOf() }
            relays[key] = relay
            if (services.none { it.raw == service.raw }) services.add(service)
        }
    }

    collect(publicTags, false)
    privateTags?.let { collect(it, true) }

    return out.map { (key, services) -> DesignatedProvider(key.first, relays[key], services, key.third) }
}

private const val TITLE_MAX = 80

/** True for a relay's address, which a score's title names as "relay <host>". */
fun isRelayIdentifier(identifier: String): Boolean {
    val id = identifier.trim().lowercase()
    return id.startsWith("wss://") || id.startsWith("ws://")
}

/**
 * What a score's title calls a NIP-73 identifier, so the thing scored leads the card: a web page
 * by its host and path, a relay by its host, anything else (a hashtag, `isbn:…`) as written.
 */
fun identifierTitleName(identifier: String): String {
    val id = identifier.trim()
    val scheme = id.substringBefore("://", "").lowercase()
    val name =
        if (scheme == "http" || scheme == "https" || scheme == "ws" || scheme == "wss") {
            val rest = id.substringAfter("://")
            val host = rest.substringBefore('/').substringBefore('?').substringBefore('#')
            val path =
                rest
                    .drop(host.length)
                    .substringBefore('?')
                    .substringBefore('#')
                    .trimEnd('/')
            if (scheme.startsWith("ws")) host else host + path
        } else {
            id
        }
    return if (name.length > TITLE_MAX) name.take(TITLE_MAX - 1) + "\u2026" else name
}
