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
package com.vitorpamplona.amethyst.commons.relays.index

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.prefs.Preferences

/**
 * User-configurable set of relays used to fetch profile metadata
 * (kind 0) and follow lists (kind 3) — the "index relays" set passed
 * to `FeedMetadataCoordinator` in the Desktop app and to `wot sync`
 * in `amy`.
 *
 * Backed by [java.util.prefs.Preferences] at a fixed node
 * `com/vitorpamplona/amethyst/relays/index` (JVM-user-scoped). The
 * shared node means Desktop and `amy` running as the same OS user
 * observe the same setting without extra plumbing.
 *
 * Not per-account: users typically have a single preferred set of
 * index relays regardless of which account is currently logged in.
 * If per-account overrides become necessary later, layer a per-user
 * key on top; this class stays the base.
 *
 * CSV serialisation for the persisted value matches what
 * `DesktopAccountRelays` uses for its categories — no JSON dep, no
 * `Serializable` contract. URLs are normalised via
 * [RelayUrlNormalizer.normalizeOrNull] at both write and read time so
 * malformed entries never enter the effective set.
 */
class PreferencesIndexRelays(
    private val prefs: Preferences = Preferences.userRoot().node(NODE_NAME),
) {
    private val mutableRelays: MutableStateFlow<Set<NormalizedRelayUrl>> =
        MutableStateFlow(parse(prefs.get(KEY_URLS, "")))

    /**
     * Current user override. Empty when the user has not configured
     * anything — callers should route through [effective] to get the
     * defaults-fallback resolved set.
     */
    val relays: StateFlow<Set<NormalizedRelayUrl>> = mutableRelays.asStateFlow()

    fun setRelays(new: Set<NormalizedRelayUrl>) {
        mutableRelays.value = new
        prefs.put(KEY_URLS, new.joinToString(",") { it.url })
    }

    /**
     * Resolves the set the relay client should actually use — the user
     * override when non-empty, otherwise [DEFAULT_INDEX_RELAYS]. Never
     * returns empty (unless the caller has explicitly reset both the
     * override and the defaults to empty, which would require a code
     * change here).
     */
    fun effective(): Set<NormalizedRelayUrl> = mutableRelays.value.ifEmpty { DEFAULT_INDEX_RELAYS }

    companion object {
        const val NODE_NAME = "com/vitorpamplona/amethyst/relays/index"
        const val KEY_URLS = "urls"

        /**
         * Byte-for-byte identical to `DefaultRelays.RELAYS` at
         * `desktopApp/.../network/RelayStatus.kt`. Preserves current
         * behaviour for users who never open the settings UI.
         *
         * Note: `commons/AmethystDefaults.kt` also has
         * `DefaultIndexerRelayList` (Purple Pages, Coracle …) which is
         * more purpose-built for indexing. Adopting it is a separate
         * ticket — see the plan's "Out of Scope" section.
         */
        val DEFAULT_INDEX_RELAYS: Set<NormalizedRelayUrl> =
            listOf(
                "wss://nos.lol",
                "wss://nostr.wine",
                "wss://relay.noswhere.com",
                "wss://relay.primal.net",
            ).mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
                .toSet()

        internal fun parse(csv: String): Set<NormalizedRelayUrl> =
            csv
                .split(",")
                .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
                .mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
                .toSet()
    }
}
