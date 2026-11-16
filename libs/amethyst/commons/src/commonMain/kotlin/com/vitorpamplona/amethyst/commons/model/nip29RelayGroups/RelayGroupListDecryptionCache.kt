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
package com.vitorpamplona.amethyst.commons.model.nip29RelayGroups

import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.PrivateTagArrayEventCache
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.SimpleGroupListEvent
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.groupSet
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.groups

/**
 * Decrypt-once cache for the NIP-51 simple-groups list (kind 10009). The list's
 * joined groups can be public tags or NIP-44 private items (encrypted to self);
 * this merges both and parses the `group` items, memoizing the decrypt so the
 * always-on observers don't each pay a signer round-trip.
 */
class RelayGroupListDecryptionCache(
    val signer: NostrSigner,
) {
    val cachedPrivateLists = PrivateTagArrayEventCache<SimpleGroupListEvent>(signer)

    fun cachedGroupSet(event: SimpleGroupListEvent) = cachedPrivateLists.mergeTagListPrecached(event).groupSet()

    fun cachedGroups(event: SimpleGroupListEvent) = cachedPrivateLists.mergeTagListPrecached(event).groups()

    suspend fun groupSet(event: SimpleGroupListEvent) = cachedPrivateLists.mergeTagList(event).groupSet()

    suspend fun groups(event: SimpleGroupListEvent) = cachedPrivateLists.mergeTagList(event).groups()
}
