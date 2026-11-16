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
package com.vitorpamplona.amethyst.commons.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

// Total accessors for ad-hoc JSON trees (kotlinx.serialization) — bridge envelopes, persisted
// blobs, evaluateJavascript results. Every field is optional and possibly attacker-controlled,
// so each accessor degrades to null instead of throwing on an absent, JSON-null, or mistyped
// value. A codec that WANTS to reject malformed input (e.g. NappletProtocolJson) should keep
// throwing accessors instead of these.

/** Parses [raw] as a JSON object, or null when it is malformed or not an object. */
fun parseJsonObjectOrNull(raw: String): JsonObject? = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()

/**
 * The primitive at [key] rendered as a string, or null when absent, JSON-null, or not a
 * primitive. Numbers and booleans coerce to their literal text (org.json `optString` style);
 * use a `isString`-guarded read instead where a quoted string must be told apart from them.
 */
fun JsonObject.stringOrNull(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

/** The primitive at [key] as an Int, or null when absent or not parseable as one. */
fun JsonObject.intOrNull(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

/** The primitive at [key] as a Long, or null when absent or not parseable as one. */
fun JsonObject.longOrNull(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

/** The primitive at [key] as a Double, or null when absent or not parseable as one. */
fun JsonObject.doubleOrNull(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

/** The primitive at [key] as a Boolean, or null when absent or not parseable as one. */
fun JsonObject.booleanOrNull(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

/** The nested object at [key], or null when absent or not an object. */
fun JsonObject.objectOrNull(key: String): JsonObject? = this[key] as? JsonObject

/** A copy of this object with [key] set to [value] ([JsonObject] is immutable). */
fun JsonObject.withString(
    key: String,
    value: String,
): JsonObject = JsonObject(this + (key to JsonPrimitive(value)))
