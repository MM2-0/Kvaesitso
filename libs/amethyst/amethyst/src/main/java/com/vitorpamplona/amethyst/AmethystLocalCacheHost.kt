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
package com.vitorpamplona.amethyst

import com.vitorpamplona.amethyst.commons.model.cache.FileSystemNip95BlobStore
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.LocalCacheHost
import com.vitorpamplona.amethyst.commons.model.cache.Nip95BlobStore
import com.vitorpamplona.amethyst.commons.service.UsageCounter
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.RelayStats
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import com.vitorpamplona.quartz.nip57Zaps.validate.LnurlEndpointCache
import com.vitorpamplona.quartz.nip57Zaps.validate.LnurlEndpointInfo
import kotlinx.coroutines.CoroutineScope

/**
 * Binds the shared [LocalCache] in `commons` to this app's shell.
 *
 * Every member reads through to [modules] on each call rather than capturing a value, because
 * three of the four are lazy and are built long after the cache singleton exists.
 */
class AmethystLocalCacheHost(
    private val modules: AppModules,
    /**
     * Passed in rather than read here: the top-level `isDebug` and this override share a name,
     * so inside the class body the name would resolve to the override itself.
     */
    override val isDebug: Boolean,
) : LocalCacheHost {
    override val scope: CoroutineScope get() = modules.applicationIOScope

    // by lazy, not a getter: modules.nip95cache is itself lazy and creating the directory
    // is the point of touching it, so this must not happen once per NIP-95 event.
    override val nip95Blobs: Nip95BlobStore by lazy { FileSystemNip95BlobStore(modules.nip95cache.absolutePath) }

    override val relayStats: RelayStats get() = modules.relayStats

    override val usageCounter: UsageCounter = UsageCounter { key, amount -> modules.resourceUsage.add(key, amount) }

    override fun relaySelfPubKey(relay: NormalizedRelayUrl): HexKey? = modules.nip11Cache.getFromCache(relay).self

    override fun relayInfo(relay: NormalizedRelayUrl): Nip11RelayInformation = modules.nip11Cache.getFromCache(relay)

    override val nip11Cache get() = modules.nip11Cache

    // The zap path's LNURL cache is a quartz-side singleton the outbound-zap resolver fills.
    override fun lnurlEndpoint(lnurlpUrl: String): LnurlEndpointInfo? = LnurlEndpointCache.get(lnurlpUrl)
}
