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
package com.vitorpamplona.amethyst.commons.model.nip02FollowList

import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip02FollowList.ReadWrite
import com.vitorpamplona.quartz.nip02FollowList.tags.ContactTag

/**
 * Shared action for following/unfollowing users.
 * Creates NIP-02 ContactList events (kind 3).
 */
object FollowAction {
    /**
     * Follows a user by creating an updated ContactListEvent.
     *
     * @param pubKeyHex The hex public key of the user to follow
     * @param signer The NostrSigner to sign the event
     * @param currentContactList The current contact list event (if any)
     * @return Signed ContactListEvent with the user added
     * @throws IllegalStateException if signer is not writeable
     */
    suspend fun follow(
        pubKeyHex: String,
        signer: NostrSigner,
        currentContactList: ContactListEvent? = null,
    ): ContactListEvent {
        if (!signer.isWriteable()) {
            throw IllegalStateException("Cannot follow: signer is not writeable")
        }

        val updatedEvent =
            if (currentContactList != null) {
                // Add to existing contact list
                ContactListEvent.followUser(
                    earlierVersion = currentContactList,
                    pubKeyHex = pubKeyHex,
                    signer = signer,
                )
            } else {
                // Create new contact list from scratch
                ContactListEvent.createFromScratch(
                    followUsers = listOf(ContactTag(pubKeyHex)),
                    relayUse = emptyMap<String, ReadWrite>(),
                    signer = signer,
                )
            }

        return updatedEvent
    }

    /**
     * Unfollows a user by creating an updated ContactListEvent.
     *
     * @param pubKeyHex The hex public key of the user to unfollow
     * @param signer The NostrSigner to sign the event
     * @param currentContactList The current contact list event (required for unfollow)
     * @return Signed ContactListEvent with the user removed
     * @throws IllegalStateException if signer is not writeable
     * @throws IllegalArgumentException if currentContactList is null
     */
    suspend fun unfollow(
        pubKeyHex: String,
        signer: NostrSigner,
        currentContactList: ContactListEvent?,
    ): ContactListEvent {
        if (!signer.isWriteable()) {
            throw IllegalStateException("Cannot unfollow: signer is not writeable")
        }

        requireNotNull(currentContactList) {
            "Cannot unfollow: current contact list is required"
        }

        // Remove from existing contact list
        val updatedEvent =
            ContactListEvent.unfollowUser(
                earlierVersion = currentContactList,
                pubKeyHex = pubKeyHex,
                signer = signer,
            )

        return updatedEvent
    }

    /**
     * Checks if a user is currently followed in the contact list.
     *
     * @param pubKeyHex The hex public key to check
     * @param currentContactList The current contact list event (if any)
     * @return true if the user is followed, false otherwise
     */
    fun isFollowing(
        pubKeyHex: String,
        currentContactList: ContactListEvent?,
    ): Boolean {
        if (currentContactList == null) return false

        return currentContactList.isFollowing(pubKeyHex)
    }
}
