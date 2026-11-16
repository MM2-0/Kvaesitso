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
package com.vitorpamplona.geode.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Strict boot-time check for a `[[mirror]].filter` JSON string. The
 * NIP-01 [com.vitorpamplona.quartz.nip01Core.relay.filters.Filter]
 * deserializer is tolerant by design — it skips unknown keys and drops
 * wrong-typed entries — which is right for untrusted wire input but
 * wrong for operator config: a typo like `{"kindss":[4]}` would silently
 * parse to an empty (match-everything) filter. Because this filter is
 * the containment boundary for a `trusted = true` upstream (it caps what
 * a skip-verify upstream may inject), a silent mis-parse would widen
 * that trust to the whole firehose. So we reject the obvious mistakes at
 * boot instead of degrading the boundary.
 */
object MirrorFilterValidator {
    /** NIP-01 filter keys the deserializer recognizes; anything else is a typo. */
    private val KNOWN_KEYS = setOf("ids", "authors", "kinds", "since", "until", "limit", "search")

    /** Keys whose value must be a JSON array. */
    private val ARRAY_KEYS = setOf("ids", "authors", "kinds")

    /**
     * Throws [IllegalArgumentException] when [json] is not a JSON object,
     * carries an unrecognized top-level key, or gives a list-typed field
     * (`ids`/`authors`/`kinds`, or a `#tag`/`&tag`) a non-array value.
     */
    fun validate(
        url: String,
        json: String,
    ) {
        val node =
            try {
                Json.parseToJsonElement(json)
            } catch (e: Exception) {
                throw IllegalArgumentException("[[mirror]] filter for $url is not valid JSON: $json", e)
            }
        require(node is JsonObject) {
            "[[mirror]] filter for $url must be a JSON object (a NIP-01 filter), got: $json"
        }
        node.keys.forEach { field ->
            val isTagKey = field.length > 1 && (field[0] == '#' || field[0] == '&')
            require(field in KNOWN_KEYS || isTagKey) {
                "[[mirror]] filter for $url has unknown key \"$field\" — a NIP-01 filter uses " +
                    "ids/authors/kinds/since/until/limit/search or #tag/&tag"
            }
            if (field in ARRAY_KEYS || isTagKey) {
                require(node[field] is JsonArray) {
                    "[[mirror]] filter for $url: \"$field\" must be a JSON array"
                }
            }
        }
    }
}
