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
package com.vitorpamplona.amethyst.commons.actions

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip02FollowList.tags.ContactTag

/**
 * Pure event-building "verbs" for the NIP-02 kind:3 contact list.
 *
 * Builds a signed [ContactListEvent] but does NOT publish it. The Amethyst
 * Android UI flow does more than these builders — non-UI callers are
 * responsible for the rest:
 *
 *  * **Publish.** Hand the returned event to your relay client. Android
 *    uses `Account.sendMyPublicAndPrivateOutbox`, amy uses `Context.publish`.
 *  * **Writeable check.** Skip the call when the active signer is read-only
 *    (e.g. an npub-only login). Building will fail at the sign step
 *    otherwise.
 *  * **Relay hint.** Pass [relayHint] pointing at one of the target's
 *    advertised kind:10002 write relays so readers can find the followed
 *    user. The in-app flow does this via `User.bestRelayHint()`.
 *  * **No-op detection.** When the user already follows the target, the
 *    underlying builder short-circuits to the same [currentContactList].
 *    Compare `result.id == currentContactList?.id` to detect this.
 *  * **Local cache update.** If your caller has a local event cache, feed
 *    the new event back in so the UI / next read sees the update without
 *    a relay round-trip.
 *
 * Canonical entry point for non-UI callers (CLI commands, Android App
 * Functions adapters, automation scripts): takes pubkeys as [HexKey] rather
 * than the UI-model `User`, so it has no cache or scope dependency and is
 * trivially testable.
 */
object FollowActions {
    /**
     * Build a kind:3 contact list update that follows [pubkeyToFollow].
     *
     * If [currentContactList] is non-null, the new event is derived from it
     * (preserving the existing follow set and content). If it is null, a fresh
     * kind:3 is created containing only this pubkey.
     *
     * Returns the (already signed) event ready to be published to outbox
     * relays. When the user already follows [pubkeyToFollow] the underlying
     * builder returns [currentContactList] unchanged — callers that want to
     * detect "no-op" can compare event ids.
     */
    suspend fun buildFollow(
        signer: NostrSigner,
        pubkeyToFollow: HexKey,
        currentContactList: ContactListEvent?,
        relayHint: NormalizedRelayUrl? = null,
    ): ContactListEvent =
        if (currentContactList != null) {
            ContactListEvent.followUser(currentContactList, pubkeyToFollow, signer)
        } else {
            ContactListEvent.createFromScratch(
                followUsers = listOf(ContactTag(pubkeyToFollow, relayHint, null)),
                relayUse = emptyMap(),
                signer = signer,
            )
        }

    /**
     * Batch-follow variant — adds every pubkey in [pubkeysWithHints] to the
     * follow set in a single kind:3 update. Pubkeys already present in
     * [currentContactList] are skipped by the underlying builder.
     */
    suspend fun buildFollowBatch(
        signer: NostrSigner,
        pubkeysWithHints: List<Pair<HexKey, NormalizedRelayUrl?>>,
        currentContactList: ContactListEvent?,
    ): ContactListEvent {
        val contacts = pubkeysWithHints.map { (pk, hint) -> ContactTag(pk, hint, null) }
        return if (currentContactList != null) {
            ContactListEvent.followUsers(currentContactList, contacts, signer)
        } else {
            ContactListEvent.createFromScratch(
                followUsers = contacts,
                relayUse = emptyMap(),
                signer = signer,
            )
        }
    }

    /**
     * Build a kind:3 contact list update that removes [pubkeyToUnfollow].
     *
     * Returns `null` when [currentContactList] is `null` or has no tags —
     * there is nothing to unfollow, and callers should treat this as a no-op
     * rather than publishing an empty replacement event.
     */
    suspend fun buildUnfollow(
        signer: NostrSigner,
        pubkeyToUnfollow: HexKey,
        currentContactList: ContactListEvent?,
    ): ContactListEvent? =
        if (currentContactList != null && currentContactList.tags.isNotEmpty()) {
            ContactListEvent.unfollowUser(currentContactList, pubkeyToUnfollow, signer)
        } else {
            null
        }

    /**
     * Batch-unfollow variant — removes every pubkey in [pubkeysToRemove] from
     * the follow set in a single kind:3 update.
     *
     * Returns the original [currentContactList] if none of the pubkeys were
     * present (no-op), or `null` when [currentContactList] is empty/null.
     */
    suspend fun buildUnfollowBatch(
        signer: NostrSigner,
        pubkeysToRemove: Set<HexKey>,
        currentContactList: ContactListEvent?,
    ): ContactListEvent? {
        if (currentContactList == null || currentContactList.tags.isEmpty()) return null
        return ContactListEvent.unfollowUsers(currentContactList, pubkeysToRemove, signer)
    }
}
