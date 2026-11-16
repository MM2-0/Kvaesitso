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
package com.vitorpamplona.amethyst.commons.devtools

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.util.toPrettyJson
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NRelay
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** What a decoded value means, so the UI can label it in the reader's language. */
enum class Nip19Field {
    PUBLIC_KEY,
    PRIVATE_KEY,
    EVENT_ID,
    AUTHOR,
    KIND,
    IDENTIFIER,
    ADDRESS,
    RELAY,
    EVENT_JSON,
}

@Immutable
data class Nip19Row(
    val field: Nip19Field,
    val value: String,
)

/** Another way to write the same thing: [prefix] is the bech32 HRP (`npub`, `nevent`, ...). */
@Immutable
data class Nip19Encoding(
    val prefix: String,
    val value: String,
)

/**
 * The NIP-19 converter's answer for one input: a decoded entity with its parts and the
 * other encodings it converts to, a bare 64-char hex (which NIP-19 cannot tell apart — a
 * key or an event id — so every reading is offered), or nothing recognizable.
 */
@Immutable
sealed interface Nip19Inspection {
    @Immutable
    data class Decoded(
        val prefix: String,
        val rows: ImmutableList<Nip19Row>,
        val encodings: ImmutableList<Nip19Encoding>,
        val isSecret: Boolean = false,
    ) : Nip19Inspection

    @Immutable
    data class RawHex(
        val hex: HexKey,
        val encodings: ImmutableList<Nip19Encoding>,
    ) : Nip19Inspection

    data object Invalid : Nip19Inspection

    data object Empty : Nip19Inspection
}

/**
 * Decodes anything a user pastes into the NIP-19 tool: bech32 entities with or without a
 * `nostr:` prefix (NIP-21), and raw hex. Mirrors `amy decode` / `amy encode`, so the app
 * and the CLI agree on every field.
 */
object Nip19Inspector {
    fun inspect(input: String): Nip19Inspection {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return Nip19Inspection.Empty

        if (trimmed.length == 64 && Hex.isHex(trimmed)) {
            val hex = trimmed.lowercase()
            return Nip19Inspection.RawHex(
                hex = hex,
                encodings =
                    persistentListOf(
                        Nip19Encoding("npub", NPub.create(hex)),
                        Nip19Encoding("nprofile", NProfile.create(hex, emptyList())),
                        Nip19Encoding("note", NNote.create(hex)),
                        Nip19Encoding("nevent", NEvent.create(hex, null, null, emptyList())),
                    ),
            )
        }

        val parsed = Nip19Parser.uriToRoute(trimmed) ?: return Nip19Inspection.Invalid
        return when (val entity = parsed.entity) {
            is NPub ->
                decoded(
                    "npub",
                    listOf(Nip19Row(Nip19Field.PUBLIC_KEY, entity.hex)),
                    listOf(
                        Nip19Encoding("nprofile", NProfile.create(entity.hex, emptyList())),
                        Nip19Encoding("nostr", "nostr:" + NPub.create(entity.hex)),
                    ),
                )

            is NSec -> {
                val pubKey = entity.toPubKeyHex()
                decoded(
                    "nsec",
                    listOf(
                        Nip19Row(Nip19Field.PRIVATE_KEY, entity.hex),
                        Nip19Row(Nip19Field.PUBLIC_KEY, pubKey),
                    ),
                    listOf(Nip19Encoding("npub", NPub.create(pubKey))),
                    isSecret = true,
                )
            }

            is NNote ->
                decoded(
                    "note",
                    listOf(Nip19Row(Nip19Field.EVENT_ID, entity.hex)),
                    listOf(Nip19Encoding("nevent", NEvent.create(entity.hex, null, null, emptyList()))),
                )

            is NEvent ->
                decoded(
                    "nevent",
                    buildList {
                        add(Nip19Row(Nip19Field.EVENT_ID, entity.hex))
                        entity.author?.let { add(Nip19Row(Nip19Field.AUTHOR, it)) }
                        entity.kind?.let { add(Nip19Row(Nip19Field.KIND, it.toString())) }
                        entity.relay.forEach { add(Nip19Row(Nip19Field.RELAY, it.url)) }
                    },
                    buildList {
                        add(Nip19Encoding("note", NNote.create(entity.hex)))
                        entity.author?.let { add(Nip19Encoding("npub", NPub.create(it))) }
                    },
                )

            is NProfile ->
                decoded(
                    "nprofile",
                    buildList {
                        add(Nip19Row(Nip19Field.PUBLIC_KEY, entity.hex))
                        entity.relay.forEach { add(Nip19Row(Nip19Field.RELAY, it.url)) }
                    },
                    listOf(Nip19Encoding("npub", NPub.create(entity.hex))),
                )

            is NAddress ->
                decoded(
                    "naddr",
                    buildList {
                        add(Nip19Row(Nip19Field.KIND, entity.kind.toString()))
                        add(Nip19Row(Nip19Field.AUTHOR, entity.author))
                        add(Nip19Row(Nip19Field.IDENTIFIER, entity.dTag))
                        add(Nip19Row(Nip19Field.ADDRESS, "${entity.kind}:${entity.author}:${entity.dTag}"))
                        entity.relay.forEach { add(Nip19Row(Nip19Field.RELAY, it.url)) }
                    },
                    listOf(Nip19Encoding("npub", NPub.create(entity.author))),
                )

            is NRelay ->
                decoded(
                    "nrelay",
                    entity.relay.map { Nip19Row(Nip19Field.RELAY, it) },
                    emptyList(),
                )

            is NEmbed -> {
                val event = entity.event
                decoded(
                    "nembed",
                    listOf(
                        Nip19Row(Nip19Field.EVENT_ID, event.id),
                        Nip19Row(Nip19Field.AUTHOR, event.pubKey),
                        Nip19Row(Nip19Field.KIND, event.kind.toString()),
                        Nip19Row(Nip19Field.EVENT_JSON, event.toPrettyJson()),
                    ),
                    listOf(
                        Nip19Encoding("note", NNote.create(event.id)),
                        Nip19Encoding("nevent", NEvent.create(event.id, event.pubKey, event.kind, emptyList())),
                    ),
                )
            }

            else -> Nip19Inspection.Invalid
        }
    }

    private fun decoded(
        prefix: String,
        rows: List<Nip19Row>,
        encodings: List<Nip19Encoding>,
        isSecret: Boolean = false,
    ) = Nip19Inspection.Decoded(prefix, rows.toImmutableList(), encodings.toImmutableList(), isSecret)
}
