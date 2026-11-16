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
package com.vitorpamplona.amethyst.commons.model.cache

import com.vitorpamplona.amethyst.commons.relays.nip11RelayInfo.Nip11CachedRetriever
import com.vitorpamplona.amethyst.commons.relays.nip11RelayInfo.Nip11Fetcher
import com.vitorpamplona.amethyst.commons.service.UsageCounter
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.RelayStats
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import com.vitorpamplona.quartz.nip57Zaps.validate.LnurlEndpointInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob

/**
 * The handful of things the event cache needs from the application shell around it.
 *
 * The cache itself is a plain in-memory store and can run without any of them, so every
 * member here has a default that is the honest answer for a host that does not provide it
 * — an own scope, nowhere to spill NIP-95 blobs, no relay identity, no stats sink. A front end
 * installs its own via [EventCache.appHost]; Android's `Amethyst` does so at startup,
 * Desktop and unit tests keep the defaults.
 *
 * This exists so [LocalCache] can live in `commons` without naming `Amethyst.instance`,
 * `BuildConfig` or `android.os.Looper`.
 *
 * **Android's `:napplet` process must never reach the cache.** Now that [LocalCache] lives
 * in `commons`, `:nappletHost` can see it, where before it could not compile a reference at
 * all. A stray one there would no longer fail fast on an unset `Amethyst.instance` — it
 * would quietly build a second, empty cache in that process, with its own IO scope nothing
 * cancels. Processes share no memory: the populated cache exists only in `main`.
 */
interface LocalCacheHost {
    /** Scope for the cache's own background work: NIP-BC chain lookups and LNURL fetches. */
    val scope: CoroutineScope

    /**
     * Whether this is a debug build. Gates the two very chatty per-event log lines that
     * dump full event JSON while back-filling a relay with a deletion or a newer version.
     */
    val isDebug: Boolean

    /**
     * Where NIP-95 `FileStorageEvent` blobs are spilled, so their bytes can leave memory once
     * they are stored. `null` keeps the event in memory and skips the spill.
     */
    val nip95Blobs: Nip95BlobStore?

    /**
     * Relays' NIP-11 documents, shared by the cache (relay `self` keys) and the screens that show
     * relay details. The default fetches nothing.
     */
    val nip11Cache: Nip11CachedRetriever get() = OfflineNip11Cache

    /** Per-relay counters the anti-spam filter reports duplicate events to. */
    val relayStats: RelayStats?

    /**
     * The relay's own pubkey, as its NIP-11 document advertises it (`self`), used to accept
     * relay-signed NIP-29 group metadata. `null` means unknown, which accepts the event.
     */
    fun relaySelfPubKey(relay: NormalizedRelayUrl): HexKey? = null

    /** The resource-usage ledger's counters; counts nothing by default. */
    val usageCounter: UsageCounter get() = UsageCounter.None

    /**
     * The relay's NIP-11 document as far as the shell has it in memory. Never fetches: `null`
     * (or a document with nothing in it) means "not loaded yet", which callers must treat as
     * unknown rather than as "unsupported".
     */
    fun relayInfo(relay: NormalizedRelayUrl): Nip11RelayInformation? = null

    /**
     * LNURL-pay endpoint metadata the shell has already resolved for [lnurlpUrl], used to
     * validate a zap receipt's signer against the provider's `nostrPubkey` (NIP-57 Appendix F).
     *
     * `null` is "not cached", which is what a cache miss already means here: the receipt takes
     * the slower resolver path instead. A host that caches nothing therefore behaves like a
     * cold cache, not like a broken one.
     */
    fun lnurlEndpoint(lnurlpUrl: String): LnurlEndpointInfo? = null

    /** The neutral host: an own IO scope and nothing else. */
    companion object Default : LocalCacheHost {
        override val scope: CoroutineScope by lazy { CoroutineScope(Dispatchers.IO + SupervisorJob()) }
        override val isDebug = false
        override val nip95Blobs: Nip95BlobStore? = null
        override val relayStats: RelayStats? = null
    }
}

private val OfflineNip11Cache = Nip11CachedRetriever(Nip11Fetcher.Offline)
