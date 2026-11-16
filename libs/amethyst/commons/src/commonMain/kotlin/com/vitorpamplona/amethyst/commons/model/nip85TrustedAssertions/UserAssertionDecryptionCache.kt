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
package com.vitorpamplona.amethyst.commons.model.nip85TrustedAssertions

import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.PrivateTagArrayEventCache
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.UserAssertionEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.petName
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.summary

/**
 * Decrypts and caches the NIP-44 private tags of the account's own kind:30382
 * contact cards. Petname and summary always live in the encrypted part, so
 * reading them requires the account's main key ([signer]); cards signed by any
 * other key never decrypt here.
 */
class UserAssertionDecryptionCache(
    val signer: NostrSigner,
) {
    val cachedPrivateCards = PrivateTagArrayEventCache<UserAssertionEvent>(signer, cacheSize = 100)

    suspend fun petName(event: UserAssertionEvent) = cachedPrivateCards.mergeTagList(event).petName()

    suspend fun summary(event: UserAssertionEvent) = cachedPrivateCards.mergeTagList(event).summary()

    /**
     * The decrypted petname and summary plus the card's full decrypted tag list,
     * so renderers can resolve the NIP-30 `emoji` mappings stored alongside them.
     */
    suspend fun nickname(event: UserAssertionEvent): Nickname? = cachedPrivateCards.mergeTagList(event).toNickname()

    /**
     * Synchronous variant that only reads an already-decrypted card, for use as
     * the immediate value of UI flows. Returns null until the suspend path has
     * decrypted the card once.
     */
    fun cachedNickname(event: UserAssertionEvent): Nickname? = cachedPrivateCards.mergeTagListPrecached(event).toNickname()

    private fun TagArray.toNickname(): Nickname? {
        val name = petName()
        val summary = summary()
        if (name == null && summary == null) return null
        return Nickname(name, summary, toImmutableListOfLists())
    }
}
