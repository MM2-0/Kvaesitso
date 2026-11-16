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
package com.vitorpamplona.amethyst.commons.model.nip51Lists.muteList

import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.PrivateTagArrayEventCache
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.mutedThreadIdSet
import com.vitorpamplona.quartz.nip51Lists.muteList.mutedUserIdSet
import com.vitorpamplona.quartz.nip51Lists.muteList.mutedUsers
import com.vitorpamplona.quartz.nip51Lists.muteList.mutedUsersAndWords
import com.vitorpamplona.quartz.nip51Lists.muteList.mutedWordSet
import com.vitorpamplona.quartz.nip51Lists.muteList.mutedWords

class MuteListDecryptionCache(
    val signer: NostrSigner,
) {
    val cachedPrivateLists = PrivateTagArrayEventCache<MuteListEvent>(signer)

    fun cachedUsersAndWords(event: MuteListEvent) = cachedPrivateLists.mergeTagListPrecached(event).mutedUsersAndWords()

    fun cachedUsers(event: MuteListEvent) = cachedPrivateLists.mergeTagListPrecached(event).mutedUsers()

    fun cachedUserIdSet(event: MuteListEvent) = cachedPrivateLists.mergeTagListPrecached(event).mutedUserIdSet()

    fun cachedWords(event: MuteListEvent) = cachedPrivateLists.mergeTagListPrecached(event).mutedWords()

    fun cachedWordSet(event: MuteListEvent) = cachedPrivateLists.mergeTagListPrecached(event).mutedWordSet()

    fun cachedThreadIdSet(event: MuteListEvent) = cachedPrivateLists.mergeTagListPrecached(event).mutedThreadIdSet()

    suspend fun mutedUsersAndWords(event: MuteListEvent) = cachedPrivateLists.mergeTagList(event).mutedUsersAndWords()

    suspend fun mutedUsers(event: MuteListEvent) = cachedPrivateLists.mergeTagList(event).mutedUsers()

    suspend fun mutedUserIdSet(event: MuteListEvent) = cachedPrivateLists.mergeTagList(event).mutedUserIdSet()

    suspend fun mutedWords(event: MuteListEvent) = cachedPrivateLists.mergeTagList(event).mutedWords()

    suspend fun mutedWordSet(event: MuteListEvent) = cachedPrivateLists.mergeTagList(event).mutedWordSet()

    suspend fun mutedThreadIdSet(event: MuteListEvent) = cachedPrivateLists.mergeTagList(event).mutedThreadIdSet()
}
