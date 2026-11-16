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
package com.vitorpamplona.amethyst.commons.model.nip11RelayInfo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.relays.nip11RelayInfo.Nip11CachedRetriever
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun loadRelayInfo(relay: NormalizedRelayUrl): State<Nip11RelayInformation> = loadRelayInfo(relay, LocalCache.appHost.nip11Cache)

/**
 * Eagerly warms the NIP-11 cache for a whole set of [relays] **in parallel** (each fetch on its own
 * coroutine), so callers that later read the cache — e.g. the NIP-29 relay-signed group check — get
 * hits instead of cold fetches. Warming a set serially would sum every relay's latency and let one
 * slow/unreachable relay stall the rest until its socket timeout; fanning out bounds the wait to the
 * slowest single fetch. [onEachLoaded] fires (on an IO thread) as each relay resolves, so a screen
 * can re-evaluate incrementally as docs arrive. The cache dedups, so re-warming is cheap.
 */
@Composable
fun WarmNip11(
    relays: Collection<NormalizedRelayUrl>,
    onEachLoaded: () -> Unit = {},
) {
    val cache = LocalCache.appHost.nip11Cache
    LaunchedEffect(relays) {
        coroutineScope {
            relays.forEach { relay ->
                launch {
                    cache.loadRelayInfo(
                        relay = relay,
                        onInfo = { onEachLoaded() },
                        onError = { _, _, _ -> onEachLoaded() },
                    )
                }
            }
        }
    }
}

@Composable
fun loadRelayInfo(
    relay: NormalizedRelayUrl,
    nip11Cache: Nip11CachedRetriever,
): State<Nip11RelayInformation> =
    produceState(
        nip11Cache.getFromCache(relay),
        relay,
    ) {
        nip11Cache.loadRelayInfo(
            relay = relay,
            onInfo = {
                value = it
            },
            onError = { url, errorCode, exceptionMessage ->
                Log.e("RelayInfo") { "Error loading relay info for ${relay.url}: $errorCode - $exceptionMessage" }
            },
        )
    }
