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
package com.vitorpamplona.amethyst.commons.model.nip72Communities

import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.PrivateTagArrayEventCache
import com.vitorpamplona.quartz.nip72ModCommunities.follow.CommunityListEvent
import com.vitorpamplona.quartz.nip72ModCommunities.follow.communities
import com.vitorpamplona.quartz.nip72ModCommunities.follow.communityIdSet
import com.vitorpamplona.quartz.nip72ModCommunities.follow.communityIds

class CommunityListDecryptionCache(
    val signer: NostrSigner,
) {
    val cachedPrivateLists = PrivateTagArrayEventCache<CommunityListEvent>(signer)

    fun cachedCommunityIds(event: CommunityListEvent) = cachedPrivateLists.mergeTagListPrecached(event).communityIds()

    fun cachedCommunityIdSet(event: CommunityListEvent) = cachedPrivateLists.mergeTagListPrecached(event).communityIdSet()

    fun cachedCommunities(event: CommunityListEvent) = cachedPrivateLists.mergeTagListPrecached(event).communities()

    suspend fun communityIds(event: CommunityListEvent) = cachedPrivateLists.mergeTagList(event).communityIds()

    suspend fun communityIdSet(event: CommunityListEvent) = cachedPrivateLists.mergeTagList(event).communityIdSet()

    suspend fun communities(event: CommunityListEvent) = cachedPrivateLists.mergeTagList(event).communities()
}
