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
package com.vitorpamplona.amethyst.commons.relayauth

import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose

/**
 * What a subscription's declared [SubPurpose] means for a NIP-42 auth prompt.
 *
 * Every filter the app opens is built as an
 * [ExplainedFilter][com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter],
 * which records *why* it exists at the point it is constructed. The auth path used to throw that
 * away and re-infer intent from the filter's tag shape, which is wrong in two common cases:
 *
 * - **Reading your own inbox.** Notifications and DMs are `#p` = me with no `authors`, so they match
 *   no shape rule at all. The prompt fell through to the blank "Use this relay" wording, or borrowed
 *   the label of unrelated pending traffic sharing the socket — announcing an outbound notification
 *   while the user was reading.
 * - **Reading a thread.** Reactions/zaps are fetched with `#e` against *note ids*
 *   (`ReactionsFilterAssembler`), which is shape-identical to reading a NIP-28 channel. The prompt
 *   asked "Open 3f8a12c9?" about a note that was never a room.
 *
 * Returning `null` means "this purpose says nothing useful about identity disclosure" — the caller
 * falls back to tag-shape inference, which is still the right answer for a plain
 * [Filter][com.vitorpamplona.quartz.nip01Core.relay.filters.Filter] built outside the assemblers.
 */
fun SubPurpose.toAuthPurposeKind(): AuthPurposeKind? =
    when (this) {
        // Things other people addressed to us. `#p` = me, no authors — invisible to shape rules.
        SubPurpose.NOTIFICATIONS,
        SubPurpose.DIRECT_MESSAGES,
        SubPurpose.NUTZAP_INBOX,
        -> AuthPurposeKind.MY_INBOX

        // A conversation on screen: the thread itself, its engagement, and the events it references.
        SubPurpose.THREAD,
        SubPurpose.ENGAGEMENT,
        SubPurpose.REFERENCED_EVENTS,
        -> AuthPurposeKind.THREAD

        // Rooms: every chat protocol plus the community timeline they hang off.
        SubPurpose.PUBLIC_CHATS,
        SubPurpose.RELAY_GROUPS,
        SubPurpose.EPHEMERAL_CHATS,
        SubPurpose.GEOHASH_CHATS,
        SubPurpose.LIVE_CHAT,
        SubPurpose.LIVE_ROOMS,
        SubPurpose.COMMUNITY_CHATS,
        SubPurpose.COMMUNITY_FEED,
        -> AuthPurposeKind.READ_VENUE

        // Reading people: timelines, profiles and the metadata that decorates them.
        SubPurpose.HOME_FEED,
        SubPurpose.DISCOVER_FEED,
        SubPurpose.MEDIA_FEED,
        SubPurpose.TAG_FEED,
        SubPurpose.TOPIC_FEED,
        SubPurpose.USER_PROFILE,
        SubPurpose.PROFILE_METADATA,
        SubPurpose.FOLLOW_LISTS,
        -> AuthPurposeKind.READ_OUTBOX

        // Our own housekeeping, discovery queries and everything else: nothing a prompt can say about
        // *who* is affected, so let the tag shape try instead of inventing a counterparty.
        SubPurpose.ACCOUNT_DATA,
        SubPurpose.RELAY_LISTS,
        SubPurpose.MODERATION,
        SubPurpose.WALLET,
        SubPurpose.MINT_DIRECTORY,
        SubPurpose.NWC,
        SubPurpose.SIGNER,
        SubPurpose.ENCRYPTED_GROUPS,
        SubPurpose.COORDINATOR_GROUPS,
        SubPurpose.SEARCH,
        SubPurpose.ADD_ONS,
        SubPurpose.GAMES,
        SubPurpose.RELAY_INFO,
        SubPurpose.OTHER,
        -> null
    }
