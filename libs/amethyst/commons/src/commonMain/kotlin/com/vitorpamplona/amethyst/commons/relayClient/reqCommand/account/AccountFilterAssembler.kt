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
package com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.relayClient.AccountScopedQuery
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.buzz.BuzzMembershipEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.drafts.AccountDraftsEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.marmot.MarmotGroupEventsEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.metadata.AccountMetadataEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.nip01Notifications.AccountNotificationsEoseFromInboxRelaysManager
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.nip01Notifications.AccountNotificationsHistoryEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.nip47WalletConnect.NwcNotificationsEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.nip59GiftWraps.AccountGiftWrapsEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.nip59GiftWraps.AccountGiftWrapsHistoryEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.nip60Cashu.CashuWalletEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.nip60Cashu.CashuWalletHistoryEoseManager
import com.vitorpamplona.amethyst.commons.viewmodels.AccountFeedContentStates
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient

// This allows multiple screen to be listening to logged-in accounts.
//
// Carries only what an account can supply with no screen attached, so the
// background registry can mount the always-on loaders for accounts the user
// opted into keeping active while the app is away. Screens use the richer
// [AccountUiQueryState] below.
@Stable
open class AccountQueryState(
    override val account: Account,
    val otherAccounts: Set<HexKey>,
) : AccountScopedQuery {
    /**
     * The feeds this account renders, when a screen is attached — null for
     * background accounts.
     *
     * The only always-on reader is
     * [com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.nip01Notifications.AccountNotificationsEoseFromInboxRelaysManager],
     * which reads it as a cold-start `since` floor via `lastNoteCreatedAtIfFilled()`.
     * That floor only arms once the feed holds a full page, and nothing fills a
     * feed that has no UI — so for a background account it could only ever
     * return null anyway. Leaving the field off the background key states that
     * rather than pretending there is a feed to consult.
     */
    open val feedContentStates: AccountFeedContentStates? = null
}

/**
 * The key for an account with a screen attached. Adds the feed states, which
 * lets the notification loaders floor their cold-start queries at the depth the
 * rendered feed already reaches instead of asking all-time again.
 */
@Stable
class AccountUiQueryState(
    account: Account,
    override val feedContentStates: AccountFeedContentStates,
    otherAccounts: Set<HexKey>,
) : AccountQueryState(account, otherAccounts)

/**
 * Always-on account loaders: metadata, gift wraps, drafts, inbox-relay
 * notifications, marmot group events. Foreground-only loaders live in
 * [AccountForegroundFilterAssembler].
 */
@Stable
class AccountFilterAssembler(
    client: INostrClient,
) : ComposeSubscriptionManager<AccountQueryState>() {
    // Live tail: the recent week of gift wraps, always open at the top for new messages.
    val giftWraps = AccountGiftWrapsEoseManager(client, ::preferredKeys)

    // History: older gift wraps, loaded on demand in bounded one-shot slices. Foreground account only:
    // see [foregroundKeys].
    val giftWrapsHistory = AccountGiftWrapsHistoryEoseManager(client, ::foregroundKeys)

    // Live tail: the recent week of notifications from the inbox + group host relays.
    val notifications = AccountNotificationsEoseFromInboxRelaysManager(client, ::preferredKeys)

    // History: older notifications, paged backward by until+limit per relay, driven by the feed's markers.
    val notificationsHistory = AccountNotificationsHistoryEoseManager(client, ::foregroundKeys)

    // History: older NIP-60 spending rows (kind:7376), paged backward by until+limit per outbox relay,
    // driven by the wallet's transaction list. The live wallet subscription below reads six kinds in one
    // uncapped REQ, so history — the most numerous of them — is exactly what a relay's cap truncates.
    val cashuWalletHistory = CashuWalletHistoryEoseManager(client, ::foregroundKeys)

    val group =
        listOf(
            AccountMetadataEoseManager(client, ::preferredKeys),
            giftWraps,
            giftWrapsHistory,
            AccountDraftsEoseManager(client, ::preferredKeys),
            notifications,
            notificationsHistory,
            // Live tail: NIP-47 wallet notifications (payment_received) on each connected wallet's own relay.
            NwcNotificationsEoseManager(client, ::preferredKeys),
            // NIP-60 wallet + NIP-61 nutzap inbox. Mounted here rather than run from a collector
            // inside CashuWalletState, so it starts and stops with every other account-level loader.
            CashuWalletEoseManager(client, ::preferredKeys),
            cashuWalletHistory,
            MarmotGroupEventsEoseManager(client, ::preferredKeys),
            // What a Buzz workspace relay addresses to me personally: membership verdicts (44100/44101)
            // and my hidden-DM snapshot (30622). Feeds both the channel-invite prompts and Buzz DM
            // discovery, which read them back out of LocalCache rather than each opening a `#p=me` REQ.
            BuzzMembershipEoseManager(client, ::preferredKeys),
        )

    /**
     * One key per account, preferring a screen's [AccountUiQueryState] over the
     * background registry's key.
     *
     * An account can be mounted twice — the user is looking at it *and* asked to keep
     * it running in the background. The managers below all dedup by user, but by
     * keeping whichever key they meet first, which is just whoever mounted first.
     * Resolving it here means the account being looked at keeps the feed-backed
     * cold-start floor instead of losing it to a race.
     */
    private fun preferredKeys(): Set<AccountQueryState> =
        allKeys()
            .groupBy { it.account.userProfile().pubkeyHex }
            .values
            .mapTo(mutableSetOf()) { keys ->
                keys.firstOrNull { it.feedContentStates != null } ?: keys.first()
            }

    /**
     * Only the accounts a screen is attached to, for the three history pagers.
     *
     * Each history manager drives ONE [BackwardRelayPager][com.vitorpamplona.amethyst.commons.relayClient.paging.BackwardRelayPager],
     * which every subscription re-binds to its own account, and the screens read that pager's status. Fed
     * every logged-in account, the last background account to subscribe took the pager: the account on
     * screen then paged only the relays it shared with that one, dropped its own events (they arrive for an
     * unbound account), and its chat cards reported the other account's relays. History is paged by
     * on-screen markers anyway, so a background account has nothing to page.
     */
    private fun foregroundKeys(): Set<AccountQueryState> = preferredKeys().filterTo(mutableSetOf()) { it is AccountUiQueryState }

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun destroy() = group.forEach { it.destroy() }
}
