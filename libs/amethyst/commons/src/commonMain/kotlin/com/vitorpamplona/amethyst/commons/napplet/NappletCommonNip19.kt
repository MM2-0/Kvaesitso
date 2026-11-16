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
package com.vitorpamplona.amethyst.commons.napplet

import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletRequest
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletResponse
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.bech32.Bech32
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NRelay

/**
 * NAP-COMMON's NIP-19 helpers: the public identifiers only. `nsec` is never encoded or decoded, so a
 * napplet can't use the shell to handle a private key. Keys and ids come back as lowercase hex.
 */
object NappletCommonNip19 {
    /** NAP-COMMON's limit on what `decodeNip19` reads. */
    const val MAX_DECODE_LENGTH = 5000

    private val HEX64 = Regex("^[0-9a-f]{64}$")

    fun isHexKey(value: String?): Boolean = value != null && HEX64.matches(value)

    fun encode(input: NappletRequest.CommonEncodeNip19): NappletResponse {
        val relays = input.relays.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
        val value =
            when (input.type) {
                "npub" -> input.hex?.takeIf(::isHexKey)?.let { NPub.create(it) }
                "note" -> input.hex?.takeIf(::isHexKey)?.let { NNote.create(it) }
                "nprofile" -> input.pubkey?.takeIf(::isHexKey)?.let { NProfile.create(it, relays) }
                "nevent" ->
                    input.eventId?.takeIf(::isHexKey)?.let { id ->
                        if (input.author != null && !isHexKey(input.author)) return NappletResponse.CommonFailure("invalid-pubkey")
                        NEvent.create(id, input.author, input.kind, relays)
                    }
                "naddr" -> {
                    val pubkey = input.pubkey?.takeIf(::isHexKey)
                    val kind = input.kind
                    if (pubkey != null && kind != null && kind >= 0 && input.identifier != null) NAddress.createOrNull(kind, pubkey, input.identifier, relays) else null
                }
                "nrelay" -> input.relay?.let(RelayUrlNormalizer::normalizeOrNull)?.let(::nrelay)
                else -> return NappletResponse.CommonFailure("unsupported-nip19-type")
            } ?: return NappletResponse.CommonFailure("invalid-nip19")
        return NappletResponse.CommonEncoded(value, input.type)
    }

    fun decode(value: String): NappletResponse {
        if (value.length > MAX_DECODE_LENGTH) return NappletResponse.CommonFailure("invalid-nip19")
        val bare = value.trim().removePrefix("nostr:")
        if (bare.lowercase().startsWith("nsec")) return NappletResponse.CommonFailure("unsupported-nip19-type")
        val parsed = Nip19Parser.uriToRoute(bare)
        // The whole value must be one identifier, not one somewhere inside other text.
        if (parsed == null || parsed.additionalChars != null || !bare.equals(parsed.nip19raw, ignoreCase = true)) return NappletResponse.CommonFailure("invalid-nip19")
        return when (val entity = parsed.entity) {
            is NPub -> NappletResponse.CommonDecoded("npub", mapOf("hex" to entity.hex, "pubkey" to entity.hex))
            is NNote -> NappletResponse.CommonDecoded("note", mapOf("hex" to entity.hex, "eventId" to entity.hex))
            is NProfile -> NappletResponse.CommonDecoded("nprofile", mapOf("pubkey" to entity.hex), relays = entity.relay.map { it.url })
            is NEvent ->
                NappletResponse.CommonDecoded(
                    "nevent",
                    buildMap {
                        put("eventId", entity.hex)
                        entity.author?.let { put("author", it) }
                    },
                    kind = entity.kind,
                    relays = entity.relay.map { it.url },
                )
            is NAddress ->
                NappletResponse.CommonDecoded(
                    "naddr",
                    mapOf("pubkey" to entity.author, "identifier" to entity.dTag),
                    kind = entity.kind,
                    relays = entity.relay.map { it.url },
                )
            is NRelay -> entity.relay.firstOrNull()?.let { NappletResponse.CommonDecoded("nrelay", mapOf("relay" to it)) } ?: NappletResponse.CommonFailure("invalid-nip19")
            else -> NappletResponse.CommonFailure("unsupported-nip19-type")
        }
    }

    /** A hex key, `npub` or (with its relay hints) `nprofile`, as hex; null when it is none of them. */
    fun pubkeyOf(target: String): Pair<HexKey, List<NormalizedRelayUrl>>? {
        val bare = target.trim().removePrefix("nostr:")
        if (isHexKey(bare)) return bare to emptyList()
        if (bare.lowercase().startsWith("nsec")) return null
        val parsed = Nip19Parser.uriToRoute(bare)?.takeIf { it.additionalChars == null } ?: return null
        return when (val entity = parsed.entity) {
            is NPub -> entity.hex to emptyList()
            is NProfile -> entity.hex to entity.relay
            else -> null
        }
    }

    // NIP-19's nrelay: one TLV record (type 0) holding the relay URL. Quartz only decodes it, as NIP-19
    // deprecated it, but NAP-COMMON still lists it.
    private fun nrelay(relay: NormalizedRelayUrl): String? {
        val url = relay.url.encodeToByteArray()
        if (url.size > 255) return null
        return Bech32.encodeBytes("nrelay", byteArrayOf(0, url.size.toByte()) + url, Bech32.Encoding.Bech32)
    }
}
