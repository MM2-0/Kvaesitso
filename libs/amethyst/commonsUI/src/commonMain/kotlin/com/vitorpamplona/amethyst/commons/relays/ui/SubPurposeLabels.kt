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
package com.vitorpamplona.amethyst.commons.relays.ui

import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurposeGroup
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cordn_groups_title
import com.vitorpamplona.amethyst.commons.resources.kind_follow_list
import com.vitorpamplona.amethyst.commons.resources.kind_profile
import com.vitorpamplona.amethyst.commons.resources.marmot_group
import com.vitorpamplona.amethyst.commons.resources.nests
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_title
import com.vitorpamplona.amethyst.commons.resources.public_chat
import com.vitorpamplona.amethyst.commons.resources.relay_explain_account_data
import com.vitorpamplona.amethyst.commons.resources.relay_explain_community_chats
import com.vitorpamplona.amethyst.commons.resources.relay_explain_direct_messages
import com.vitorpamplona.amethyst.commons.resources.relay_explain_encrypted_groups
import com.vitorpamplona.amethyst.commons.resources.relay_explain_engagement
import com.vitorpamplona.amethyst.commons.resources.relay_explain_ephemeral_chats
import com.vitorpamplona.amethyst.commons.resources.relay_explain_follows
import com.vitorpamplona.amethyst.commons.resources.relay_explain_geohash_chats
import com.vitorpamplona.amethyst.commons.resources.relay_explain_home
import com.vitorpamplona.amethyst.commons.resources.relay_explain_live_chat
import com.vitorpamplona.amethyst.commons.resources.relay_explain_live_rooms
import com.vitorpamplona.amethyst.commons.resources.relay_explain_mint_directory
import com.vitorpamplona.amethyst.commons.resources.relay_explain_moderation
import com.vitorpamplona.amethyst.commons.resources.relay_explain_notifications
import com.vitorpamplona.amethyst.commons.resources.relay_explain_nutzap_inbox
import com.vitorpamplona.amethyst.commons.resources.relay_explain_nwc
import com.vitorpamplona.amethyst.commons.resources.relay_explain_profiles
import com.vitorpamplona.amethyst.commons.resources.relay_explain_public_chats
import com.vitorpamplona.amethyst.commons.resources.relay_explain_referenced
import com.vitorpamplona.amethyst.commons.resources.relay_explain_relay_groups
import com.vitorpamplona.amethyst.commons.resources.relay_explain_relay_lists
import com.vitorpamplona.amethyst.commons.resources.relay_explain_wallet
import com.vitorpamplona.amethyst.commons.resources.relay_purpose_add_ons
import com.vitorpamplona.amethyst.commons.resources.relay_purpose_community_chats
import com.vitorpamplona.amethyst.commons.resources.relay_purpose_community_feeds
import com.vitorpamplona.amethyst.commons.resources.relay_purpose_dm_inbox
import com.vitorpamplona.amethyst.commons.resources.relay_purpose_engagement
import com.vitorpamplona.amethyst.commons.resources.relay_purpose_ephemeral_chats
import com.vitorpamplona.amethyst.commons.resources.relay_purpose_geohash_chats
import com.vitorpamplona.amethyst.commons.resources.relay_purpose_home_feed
import com.vitorpamplona.amethyst.commons.resources.relay_purpose_live_chat
import com.vitorpamplona.amethyst.commons.resources.relay_purpose_media
import com.vitorpamplona.amethyst.commons.resources.relay_purpose_mint_directory
import com.vitorpamplona.amethyst.commons.resources.relay_purpose_nutzap_inbox
import com.vitorpamplona.amethyst.commons.resources.relay_purpose_nwc
import com.vitorpamplona.amethyst.commons.resources.relay_purpose_observing_profiles
import com.vitorpamplona.amethyst.commons.resources.relay_purpose_other
import com.vitorpamplona.amethyst.commons.resources.relay_purpose_referenced
import com.vitorpamplona.amethyst.commons.resources.relay_purpose_relay_groups
import com.vitorpamplona.amethyst.commons.resources.relay_purpose_relay_info
import com.vitorpamplona.amethyst.commons.resources.relay_purpose_relay_list_finder
import com.vitorpamplona.amethyst.commons.resources.relay_purpose_reports_from_follows
import com.vitorpamplona.amethyst.commons.resources.relay_purpose_search
import com.vitorpamplona.amethyst.commons.resources.relay_purpose_tags
import com.vitorpamplona.amethyst.commons.resources.relay_purpose_thread
import com.vitorpamplona.amethyst.commons.resources.relay_purpose_topics
import com.vitorpamplona.amethyst.commons.resources.relay_purpose_your_account
import com.vitorpamplona.amethyst.commons.resources.relay_purpose_your_wallet
import com.vitorpamplona.amethyst.commons.resources.route_chess
import com.vitorpamplona.amethyst.commons.resources.route_discover
import com.vitorpamplona.amethyst.commons.resources.route_notifications
import org.jetbrains.compose.resources.StringResource

/**
 * The one place a [SubPurpose] becomes words, shared by the relay screens and the always-on
 * notification so both call the same job the same thing.
 *
 * **Reuses the app's existing vocabulary wherever it exists** — nav routes (`Home`, `Discover`,
 * `Messages`, `Notifications`, `Chess`), event-kind names (`Reports`, `Profile`, `Follow List`,
 * `Outbox Relays`, `Drafts`, `Reactions`), and feature names (`Communities`, `Nests`,
 * `Marmot Group`, `Wallet`). A parallel vocabulary invented here would read as fuzzy exactly because
 * the words match nothing the user can navigate to, and it would need its own translations.
 * New strings exist only for the handful of jobs the app never had to name before.
 */
object SubPurposeLabels {
    fun labelOf(purpose: SubPurpose): StringResource =
        when (purpose) {
            // account — always on
            SubPurpose.ACCOUNT_DATA -> Res.string.relay_purpose_your_account
            SubPurpose.PROFILE_METADATA -> Res.string.relay_purpose_observing_profiles
            SubPurpose.RELAY_LISTS -> Res.string.relay_purpose_relay_list_finder
            SubPurpose.FOLLOW_LISTS -> Res.string.kind_follow_list
            SubPurpose.MODERATION -> Res.string.relay_purpose_reports_from_follows
            SubPurpose.WALLET -> Res.string.relay_purpose_your_wallet
            SubPurpose.NUTZAP_INBOX -> Res.string.relay_purpose_nutzap_inbox
            SubPurpose.MINT_DIRECTORY -> Res.string.relay_purpose_mint_directory
            SubPurpose.NWC -> Res.string.relay_purpose_nwc
            SubPurpose.SIGNER -> Res.string.nip46_signer_title
            // messages — always on
            SubPurpose.NOTIFICATIONS -> Res.string.route_notifications
            SubPurpose.DIRECT_MESSAGES -> Res.string.relay_purpose_dm_inbox
            SubPurpose.PUBLIC_CHATS -> Res.string.public_chat
            SubPurpose.RELAY_GROUPS -> Res.string.relay_purpose_relay_groups
            SubPurpose.EPHEMERAL_CHATS -> Res.string.relay_purpose_ephemeral_chats
            SubPurpose.GEOHASH_CHATS -> Res.string.relay_purpose_geohash_chats
            SubPurpose.LIVE_CHAT -> Res.string.relay_purpose_live_chat
            SubPurpose.COMMUNITY_CHATS -> Res.string.relay_purpose_community_chats
            SubPurpose.ENCRYPTED_GROUPS -> Res.string.marmot_group
            SubPurpose.COORDINATOR_GROUPS -> Res.string.cordn_groups_title
            SubPurpose.LIVE_ROOMS -> Res.string.nests
            // feeds
            SubPurpose.HOME_FEED -> Res.string.relay_purpose_home_feed
            SubPurpose.DISCOVER_FEED -> Res.string.route_discover
            SubPurpose.MEDIA_FEED -> Res.string.relay_purpose_media
            SubPurpose.TAG_FEED -> Res.string.relay_purpose_tags
            SubPurpose.COMMUNITY_FEED -> Res.string.relay_purpose_community_feeds
            SubPurpose.TOPIC_FEED -> Res.string.relay_purpose_topics
            // current screen
            SubPurpose.THREAD -> Res.string.relay_purpose_thread
            SubPurpose.USER_PROFILE -> Res.string.kind_profile
            SubPurpose.SEARCH -> Res.string.relay_purpose_search
            SubPurpose.ENGAGEMENT -> Res.string.relay_purpose_engagement
            SubPurpose.REFERENCED_EVENTS -> Res.string.relay_purpose_referenced
            SubPurpose.ADD_ONS -> Res.string.relay_purpose_add_ons
            SubPurpose.GAMES -> Res.string.route_chess
            SubPurpose.RELAY_INFO -> Res.string.relay_purpose_relay_info
            SubPurpose.OTHER -> Res.string.relay_purpose_other
        }

    /**
     * Whether this job earns its own line in the always-on notification.
     *
     * Derived from the taxonomy rather than hand-listed: the [SubPurposeGroup.ACCOUNT] and
     * [SubPurposeGroup.MESSAGES] groups are exactly the jobs that keep running with the app closed,
     * which is what that notification is about. Feeds and current-screen work tear themselves down on
     * backgrounding, so itemising them would add noise precisely when nobody is looking.
     */
    fun isWorthNamingInNotification(purpose: SubPurpose): Boolean = purpose.group == SubPurposeGroup.ACCOUNT || purpose.group == SubPurposeGroup.MESSAGES

    /**
     * How this subscription actually works — the strategy, not the intent.
     *
     * Only the jobs whose relay footprint surprises people have one; the rest are self-evident from
     * their label. Written from the code they describe: `MODERATION` says it asks each relay your
     * follows publish to because `UserReportsSubAssembler` walks `declaredFollowsPerOutboxRelay`,
     * and `NOTIFICATIONS` mentions the follows-wide probe because
     * `AccountNotificationsEoseFromRandomRelaysManager` subscribes to every follows relay.
     */
    fun explainerOf(purpose: SubPurpose): StringResource? =
        when (purpose) {
            SubPurpose.NOTIFICATIONS -> Res.string.relay_explain_notifications
            SubPurpose.DIRECT_MESSAGES -> Res.string.relay_explain_direct_messages
            SubPurpose.PUBLIC_CHATS -> Res.string.relay_explain_public_chats
            SubPurpose.RELAY_GROUPS -> Res.string.relay_explain_relay_groups
            SubPurpose.EPHEMERAL_CHATS -> Res.string.relay_explain_ephemeral_chats
            SubPurpose.GEOHASH_CHATS -> Res.string.relay_explain_geohash_chats
            SubPurpose.LIVE_CHAT -> Res.string.relay_explain_live_chat
            SubPurpose.COMMUNITY_CHATS -> Res.string.relay_explain_community_chats
            SubPurpose.ENCRYPTED_GROUPS -> Res.string.relay_explain_encrypted_groups
            SubPurpose.LIVE_ROOMS -> Res.string.relay_explain_live_rooms
            SubPurpose.ACCOUNT_DATA -> Res.string.relay_explain_account_data
            SubPurpose.PROFILE_METADATA -> Res.string.relay_explain_profiles
            SubPurpose.RELAY_LISTS -> Res.string.relay_explain_relay_lists
            SubPurpose.FOLLOW_LISTS -> Res.string.relay_explain_follows
            SubPurpose.MODERATION -> Res.string.relay_explain_moderation
            SubPurpose.WALLET -> Res.string.relay_explain_wallet
            SubPurpose.NUTZAP_INBOX -> Res.string.relay_explain_nutzap_inbox
            SubPurpose.MINT_DIRECTORY -> Res.string.relay_explain_mint_directory
            SubPurpose.NWC -> Res.string.relay_explain_nwc
            SubPurpose.HOME_FEED -> Res.string.relay_explain_home
            SubPurpose.ENGAGEMENT -> Res.string.relay_explain_engagement
            SubPurpose.REFERENCED_EVENTS -> Res.string.relay_explain_referenced
            else -> null
        }
}
