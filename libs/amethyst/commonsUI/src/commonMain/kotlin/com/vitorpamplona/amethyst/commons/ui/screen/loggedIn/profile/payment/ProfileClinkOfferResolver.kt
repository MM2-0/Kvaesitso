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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.payment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vitorpamplona.amethyst.commons.model.nip01Core.UserInfo
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.clink.pointers.ClinkPointerParser
import com.vitorpamplona.quartz.experimental.clink.pointers.NOffer
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Id
import com.vitorpamplona.quartz.utils.cache.ConcurrentLruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * Process-wide cache of NIP-05 `.well-known` `clink_offer` lookups, keyed by the
 * lowercased nip05 address (NIP-05 identifiers are case-insensitive). Without it, every
 * profile visit (and every relay-pushed kind-0 refresh while a profile is open) would
 * re-fetch the domain's nostr.json. Caches "no offer" results too so profiles without one
 * aren't re-hit. A [ResolvedClinkOffer] wrapper holds the nullable parsed pointer
 * (the cache can't store nulls); absence means "not fetched yet".
 */
private class ResolvedClinkOffer(
    val noffer: NOffer?,
)

private val clinkOfferNip05Cache = ConcurrentLruCache<String, ResolvedClinkOffer>(256)

/**
 * Resolves a profile's advertised CLINK Offer, preferring the kind-0 `clink_offer`
 * field and falling back to the NIP-05 `.well-known` `clink_offer` (cached). Used
 * by the profile header chip and the Send Payment screen so both agree on whether
 * the CLINK rail exists.
 */
@Composable
fun rememberProfileClinkOffer(
    userInfo: UserInfo?,
    accountViewModel: AccountViewModel,
): NOffer? {
    val kind0Offer =
        remember(userInfo) {
            userInfo?.info?.clinkOffer()?.let { ClinkPointerParser.parse(it) as? NOffer }
        }

    var offer by remember(userInfo) { mutableStateOf(kind0Offer) }

    val nip05 = userInfo?.info?.nip05
    LaunchedEffect(kind0Offer, nip05) {
        if (kind0Offer != null) {
            offer = kind0Offer
            return@LaunchedEffect
        }
        // Fall back to the NIP-05 .well-known clink_offer (cached per address).
        val id = nip05?.let { Nip05Id.parse(it) }
        offer =
            if (nip05 != null && id != null) {
                // Distinguish "cache miss" from a cached "no offer" (null) so we don't refetch.
                val cacheKey = nip05.lowercase()
                val cached = clinkOfferNip05Cache.get(cacheKey)
                if (cached != null) {
                    cached.noffer
                } else {
                    val fetched = withContext(Dispatchers.IO) { accountViewModel.nip05ClientBuilder().loadClinkOffer(id) }
                    val parsed = fetched?.let { ClinkPointerParser.parse(it) as? NOffer }
                    clinkOfferNip05Cache.put(cacheKey, ResolvedClinkOffer(parsed))
                    parsed
                }
            } else {
                null
            }
    }

    return offer
}
