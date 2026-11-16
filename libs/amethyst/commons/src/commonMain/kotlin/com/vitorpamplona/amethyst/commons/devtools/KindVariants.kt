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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** How an event's `content` is written, as far as can be told without a schema. */
enum class ContentFormat {
    EMPTY,
    JSON_OBJECT,
    JSON_ARRAY,
    ENCRYPTED,
    TEXT,
}

/**
 * A group of sampled events that are written the same way: all from one client
 * ([KindVariantGrouping.CLIENT]) or all with the same tag names and content format
 * ([KindVariantGrouping.SHAPE]).
 */
@Immutable
data class KindVariant(
    val key: String,
    /** The client's name for a client group, null for events without a `client` tag or for a shape group. */
    val client: String?,
    val count: Int,
    val authors: Int,
    /** For a shape group, the clients that produced it; for a client group, just that client. */
    val clients: ImmutableList<Pair<String?, Int>>,
    /** The newest event of the group. */
    val example: Event,
)

enum class KindVariantGrouping {
    CLIENT,
    SHAPE,
}

/**
 * Folds a sample of events of one kind into the ways that kind is actually written: once by the
 * client that published each event (its NIP-89 `client` tag) and once by shape (the set of tag
 * names plus the content format). Keeps only counts and one example per group, so a sample of
 * thousands of events costs little more than its ids, and a group's [KindVariant] is rebuilt only
 * after it gained an event, so publishing the groups while a sample runs costs a sort, not a fold.
 */
class KindVariantCollector {
    private val seen = HashSet<HexKey>()
    private val byClient = LinkedHashMap<String, Accumulator>()
    private val byShape = LinkedHashMap<String, Accumulator>()

    val total get() = seen.size

    /** Adds [event]; false when it was already counted. */
    fun add(event: Event): Boolean {
        if (!seen.add(event.id)) return false
        val client = clientOf(event)
        val format = contentFormatOf(event.content)
        byClient.getOrPut(client ?: NO_CLIENT) { Accumulator(client) }.add(event, client)
        byShape.getOrPut(shapeOf(event, format)) { Accumulator(null) }.add(event, client)
        return true
    }

    fun variants(grouping: KindVariantGrouping): ImmutableList<KindVariant> {
        val source = if (grouping == KindVariantGrouping.CLIENT) byClient else byShape
        return source.entries
            .map { (key, acc) -> acc.snapshot(key) }
            .sortedByDescending { it.count }
            .toImmutableList()
    }

    private class Accumulator(
        val client: String?,
    ) {
        var count = 0
        val authors = HashSet<HexKey>()
        val clients = HashMap<String?, Int>()
        var example: Event? = null

        /** The last snapshot, until the next event changes the group. */
        private var snapshot: KindVariant? = null

        fun add(
            event: Event,
            client: String?,
        ) {
            count++
            authors.add(event.pubKey)
            clients[client] = (clients[client] ?: 0) + 1

            val current = example
            if (current == null || event.createdAt > current.createdAt) example = event
            snapshot = null
        }

        fun snapshot(key: String) =
            snapshot ?: KindVariant(
                key = key,
                client = client,
                count = count,
                authors = authors.size,
                clients =
                    clients.entries
                        .sortedByDescending { it.value }
                        .map { it.key to it.value }
                        .toImmutableList(),
                example = example!!,
            ).also { snapshot = it }
    }

    companion object {
        private const val NO_CLIENT = "\u0000none"

        /** The client name from a NIP-89 `["client", name, address?, relay?]` tag. */
        fun clientOf(event: Event): String? =
            event.tags
                .firstOrNull { it.size > 1 && it[0] == "client" }
                ?.get(1)
                ?.trim()
                ?.ifEmpty { null }

        /** Tag names (each once, sorted) plus the content format: what a parser has to handle. */
        fun shapeOf(
            event: Event,
            format: ContentFormat,
        ): String {
            val names =
                event.tags
                    .mapNotNull { it.getOrNull(0) }
                    .distinct()
                    .sorted()
            return names.joinToString(",") + "|" + format.name
        }

        private val BASE64 = Regex("^[A-Za-z0-9+/]+={0,2}$")

        fun contentFormatOf(content: String): ContentFormat {
            val text = content.trim()
            return when {
                text.isEmpty() -> ContentFormat.EMPTY
                text.startsWith("{") && text.endsWith("}") -> ContentFormat.JSON_OBJECT
                text.startsWith("[") && text.endsWith("]") -> ContentFormat.JSON_ARRAY
                // NIP-04 carries its IV after "?iv="; NIP-44 v2 is one unbroken base64 payload.
                "?iv=" in text -> ContentFormat.ENCRYPTED
                text.length >= 64 && BASE64.matches(text) -> ContentFormat.ENCRYPTED
                else -> ContentFormat.TEXT
            }
        }
    }
}
