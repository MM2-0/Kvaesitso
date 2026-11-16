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
package com.vitorpamplona.amethyst.commons.model.chats

/**
 * The distinct conversation protocols that the Messages inbox weaves into a single list. Each is
 * independently toggleable in Settings › Messages: turning one off both hides its rows from the
 * inbox and drops its kinds/assemblers from the always-on downloading routes.
 *
 * [code] is the stable on-disk identifier (do NOT rename — it is what [encode]/[decode] persist);
 * the enum ordinal is never stored, so entries may be reordered freely.
 */
enum class ChatFeedType(
    val code: String,
) {
    /** NIP-17 private messages (gift-wrapped kind 14/15). */
    NIP17("nip17"),

    /** NIP-04 legacy encrypted direct messages (kind 4). */
    NIP04("nip04"),

    /** NIP-28 public chat channels (kind 40/42). */
    NIP28("nip28"),

    /** NIP-29 relay-based groups (kind 9 and friends). */
    NIP29("nip29"),

    /** Marmot / MLS end-to-end encrypted group chats (kind 445). */
    MARMOT("marmot"),

    /** Concord encrypted communities (gift-wrapped plane streams). */
    CONCORD("concord"),

    /**
     * cordn — MLS group chat coordinated by an MCP server rather than a relay.
     *
     * Listed beside Marmot and sharing nothing with it: the two are separate
     * protocols that happen to both be MLS, and the inbox is one of the few
     * places they meet at all (see §3.1 of
     * `amethyst/plans/2026-09-19-cordn-ui.md`). Toggling this off hides cordn
     * rows and stops its sync loops; it does not touch Marmot.
     */
    CORDN("cordn"),

    /** Geohash location channels (kind 20000). */
    GEOHASH("geohash"),

    /** Ephemeral relay-scoped chat rooms (kind 23333). */
    EPHEMERAL("ephemeral"),
    ;

    companion object {
        /** Every type, enabled by default so a fresh (or never-customized) account loads everything. */
        val ALL: Set<ChatFeedType> = entries.toTypedArray().toSet()

        fun fromCode(code: String?): ChatFeedType? = entries.firstOrNull { it.code == code }

        /** Serializes a set of types as their comma-joined [code]s, for SharedPreferences. */
        fun encode(types: Set<ChatFeedType>): String = types.joinToString(",") { it.code }

        /** Parses a comma-joined [code] list back to a set, dropping any unknown codes. */
        fun decode(joined: String?): Set<ChatFeedType> =
            joined
                ?.split(",")
                ?.mapNotNull { fromCode(it.trim()) }
                ?.toSet()
                ?: emptySet()
    }
}
