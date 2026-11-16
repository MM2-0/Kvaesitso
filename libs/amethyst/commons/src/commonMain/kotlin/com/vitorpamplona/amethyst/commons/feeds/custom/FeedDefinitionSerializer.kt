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
package com.vitorpamplona.amethyst.commons.feeds.custom

import com.vitorpamplona.amethyst.commons.util.booleanOrNull
import com.vitorpamplona.amethyst.commons.util.intOrNull
import com.vitorpamplona.amethyst.commons.util.longOrNull
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.intOrNull as primitiveIntOrNull

object FeedDefinitionSerializer {
    private val json = Json { ignoreUnknownKeys = true }

    fun serializeList(feeds: List<FeedDefinition>): String {
        val array =
            buildJsonArray {
                feeds.forEach { add(serializeFeed(it)) }
            }
        return json.encodeToString(JsonArray.serializer(), array)
    }

    fun deserializeList(jsonString: String): List<FeedDefinition> {
        if (jsonString.isBlank()) return emptyList()
        val array = json.parseToJsonElement(jsonString) as? JsonArray ?: return emptyList()
        return array.mapNotNull { node -> (node as? JsonObject)?.let { deserializeFeed(it) } }
    }

    private fun serializeFeed(feed: FeedDefinition): JsonObject =
        buildJsonObject {
            put("id", feed.id)
            put("name", feed.name)
            put("emoji", feed.emoji)
            put("pinned", feed.pinned)
            put("pinOrder", feed.pinOrder)
            put("refreshMode", feed.refreshMode.name)
            put("createdAt", feed.createdAt)
            put("source", serializeSource(feed.source))
        }

    private fun deserializeFeed(node: JsonObject): FeedDefinition? {
        val id = node.string("id") ?: return null
        val name = node.string("name") ?: return null
        val emoji = node.string("emoji") ?: ""
        val pinned = node.booleanOrNull("pinned") ?: false
        val pinOrder = node.intOrNull("pinOrder") ?: Int.MAX_VALUE
        val refreshMode =
            node.string("refreshMode")?.let {
                try {
                    RefreshMode.valueOf(it)
                } catch (_: Exception) {
                    RefreshMode.LIVE_STREAM
                }
            } ?: RefreshMode.LIVE_STREAM
        val createdAt = node.longOrNull("createdAt") ?: 0L
        val source = (node["source"] as? JsonObject)?.let { deserializeSource(it) } ?: return null

        return FeedDefinition(
            id = id,
            name = name,
            emoji = emoji,
            pinned = pinned,
            pinOrder = pinOrder,
            source = source,
            refreshMode = refreshMode,
            createdAt = createdAt,
        )
    }

    private fun serializeSource(source: FeedSource): JsonObject =
        buildJsonObject {
            when (source) {
                is FeedSource.Filter -> {
                    put("type", "filter")
                    put("hashtags", stringArray(source.hashtags))
                    put("authors", stringArray(source.authors))
                    put("relays", stringArray(source.relays))
                    put("excludeAuthors", stringArray(source.excludeAuthors))
                    put("excludeKeywords", stringArray(source.excludeKeywords))
                    put(
                        "kinds",
                        buildJsonArray { source.kinds.forEach { add(JsonPrimitive(it)) } },
                    )
                }

                is FeedSource.PeopleList -> {
                    put("type", "people_list")
                    put("kind", source.kind)
                    put("pubkey", source.pubkey)
                    put("dTag", source.dTag)
                }

                is FeedSource.InterestSet -> {
                    put("type", "interest_set")
                    put("kind", source.kind)
                    put("pubkey", source.pubkey)
                    put("dTag", source.dTag)
                }

                is FeedSource.DVM -> {
                    put("type", "dvm")
                    put("kind", source.kind)
                    put("pubkey", source.pubkey)
                    put("dTag", source.dTag)
                }

                is FeedSource.SingleRelay -> {
                    put("type", "single_relay")
                    put("url", source.url)
                }

                FeedSource.Global -> {
                    put("type", "global")
                }

                FeedSource.Following -> {
                    put("type", "following")
                }
            }
        }

    private fun deserializeSource(node: JsonObject): FeedSource? {
        val type = node.string("type") ?: return null
        return when (type) {
            "filter" -> {
                FeedSource.Filter(
                    hashtags = node.stringList("hashtags") ?: return null,
                    authors = node.stringList("authors") ?: return null,
                    relays = node.stringList("relays") ?: return null,
                    excludeAuthors = node.stringList("excludeAuthors") ?: return null,
                    excludeKeywords = node.stringList("excludeKeywords") ?: return null,
                    kinds = node.intList("kinds") ?: return null,
                )
            }

            "people_list" -> {
                FeedSource.PeopleList(
                    kind = node.intOrNull("kind") ?: 30000,
                    pubkey = node.string("pubkey") ?: return null,
                    dTag = node.string("dTag") ?: return null,
                )
            }

            "interest_set" -> {
                FeedSource.InterestSet(
                    kind = node.intOrNull("kind") ?: 30015,
                    pubkey = node.string("pubkey") ?: return null,
                    dTag = node.string("dTag") ?: return null,
                )
            }

            "dvm" -> {
                FeedSource.DVM(
                    kind = node.intOrNull("kind") ?: 31990,
                    pubkey = node.string("pubkey") ?: return null,
                    dTag = node.string("dTag") ?: return null,
                )
            }

            "single_relay" -> {
                FeedSource.SingleRelay(
                    url = node.string("url") ?: return null,
                )
            }

            "global" -> FeedSource.Global
            "following" -> FeedSource.Following
            else -> null
        }
    }

    private fun stringArray(values: Iterable<String>): JsonArray = buildJsonArray { values.forEach { add(JsonPrimitive(it)) } }

    // Deliberately stricter than the shared stringOrNull: a persisted feed field must be an actual
    // quoted string — a number or boolean here is a corrupt record, not a value to coerce.
    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.stringList(key: String) =
        (this[key] as? JsonArray)
            ?.map { (it as? JsonPrimitive)?.content.orEmpty() }
            ?.toImmutableList()

    private fun JsonObject.intList(key: String) =
        (this[key] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.primitiveIntOrNull }
            ?.toImmutableList()
}
