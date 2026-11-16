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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.dal

import com.vitorpamplona.amethyst.commons.notifications.dal.NotificationFeedFilter
import com.vitorpamplona.amethyst.commons.relayClient.account.nip01Notifications.NotificationsPerKeyKinds2
import com.vitorpamplona.amethyst.commons.relayClient.event.watchers.RepliesAndReactionsKinds2
import com.vitorpamplona.amethyst.commons.relayClient.event.watchers.RootScopedRepliesKinds
import com.vitorpamplona.amethyst.service.notifications.NotificationDispatcher
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import com.vitorpamplona.quartz.nip34Git.reply.GitReplyEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusAppliedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusClosedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusDraftEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusOpenEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the full NIP-34 collaboration surface across the four notification-plumbing
 * lists that have to move together — miss any one and either the event is never
 * asked for, or it arrives but never renders:
 *
 * 1. [NotificationsPerKeyKinds2] — the inbox-relay `#p`=me subscription. If the
 *    kind isn't listed here, the event never reaches this device unless it
 *    happens to arrive through some other subscription (a repo thread view,
 *    home-feed spillover). This is what gives you a merge notification when
 *    you close the app and come back an hour later.
 * 2. [RepliesAndReactionsKinds2] — the `#e`=<targetId> engagement subscription
 *    that fires when a patch/PR/issue row is on screen. This is what brings the
 *    status events onto the item (its replies, where
 *    [com.vitorpamplona.amethyst.commons.nip34Git.status.GitStatuses] reads them) so the
 *    closed/merged pill can render on the repo page.
 * 3. [NotificationFeedFilter.NOTIFICATION_KINDS] — the in-app Notifications tab
 *    kind gate. Without this, the event arrives from (1), sits in LocalCache,
 *    and never renders a row.
 * 4. [NotificationDispatcher.NOTIFICATION_KINDS] — the push/tray observer's
 *    kind gate. Without this, the event arrives from (1), sits in LocalCache,
 *    and never fires a system notification.
 *
 * A regression on any of (1)–(4) silently drops one specific transition
 * (comment on your PR, PR merged, patch closed, …) and there is no other
 * place to catch it.
 */
class Nip34NotificationCoverageTest {
    /**
     * Every NIP-34 event that participants care about — patch, issue, PR,
     * PR update (revision), legacy git-reply comment (1622, deprecated by
     * NIP-22 but still in the wild), and the four status transitions.
     * A NIP-22 [com.vitorpamplona.quartz.nip22Comments.CommentEvent] handles
     * modern comments through its own separate wiring.
     */
    @Suppress("DEPRECATION")
    private val nip34ParticipantKinds =
        setOf(
            GitPatchEvent.KIND,
            GitIssueEvent.KIND,
            GitPullRequestEvent.KIND,
            GitPullRequestUpdateEvent.KIND,
            GitReplyEvent.KIND,
            GitStatusOpenEvent.KIND,
            GitStatusAppliedEvent.KIND,
            GitStatusClosedEvent.KIND,
            GitStatusDraftEvent.KIND,
        )

    @Test
    fun `every NIP-34 participant kind is subscribed on inbox relays`() {
        val missing = nip34ParticipantKinds - NotificationsPerKeyKinds2.toSet()
        assertTrue(
            "NIP-34 kinds $missing are missing from NotificationsPerKeyKinds2. Without a " +
                "`#p`=me subscription for these kinds, the event never lands on the device — " +
                "so no merge/close notification can ever fire.",
            missing.isEmpty(),
        )
    }

    @Test
    fun `every NIP-34 participant kind renders on the Android notifications tab`() {
        val missing = nip34ParticipantKinds - NotificationFeedFilter.NOTIFICATION_KINDS.toSet()
        assertTrue(
            "NIP-34 kinds $missing are missing from NotificationFeedFilter.NOTIFICATION_KINDS. " +
                "The event arrives from the p-tag subscription but the kind gate drops it " +
                "before it can render on the Notifications tab.",
            missing.isEmpty(),
        )
    }

    @Test
    fun `every NIP-34 participant kind fires a push notification`() {
        val missing = nip34ParticipantKinds - NotificationDispatcher.NOTIFICATION_KINDS
        assertTrue(
            "NIP-34 kinds $missing are missing from NotificationDispatcher.NOTIFICATION_KINDS. " +
                "The event arrives and renders in-app but no system-tray push fires — " +
                "the user has to open the app to see it.",
            missing.isEmpty(),
        )
    }

    /**
     * A status/update event's discovery path when a repo or PR is on screen: the
     * engagement subscription. Without this the closed/merged pill on a repo
     * listing can never populate — the status event's only other route to the
     * device is the `#p`=me subscription, which only fires for accounts that were
     * pre-tagged as participants. Patches/issues/PRs are self-anchored (they ARE
     * the target, not events about the target), so they are intentionally NOT
     * expected here.
     *
     * Which of the two engagement filters carries a kind depends on how that kind
     * anchors itself, so the two halves are asserted separately below: statuses
     * and replies use a `root`-marked lowercase `e`, PR revisions use NIP-22's
     * uppercase `E`. Asserting the wrong half passes the kind list while matching
     * nothing on the wire.
     */
    @Suppress("DEPRECATION")
    @Test
    fun `status kinds are pulled by the lowercase-e engagement subscription`() {
        val eAnchoredActivityKinds =
            setOf(
                GitReplyEvent.KIND,
                GitStatusOpenEvent.KIND,
                GitStatusAppliedEvent.KIND,
                GitStatusClosedEvent.KIND,
                GitStatusDraftEvent.KIND,
            )
        val missing = eAnchoredActivityKinds - RepliesAndReactionsKinds2.toSet()
        assertTrue(
            "Kinds $missing are missing from RepliesAndReactionsKinds2. When a repo/PR row is " +
                "on screen the app fetches replies + reactions targeting the visible events — " +
                "this is where GitStatuses gets its data. Missing kinds mean the closed/" +
                "merged pill on a repo listing never populates for anyone who isn't a p-tagged " +
                "participant of the PR.",
            missing.isEmpty(),
        )
    }

    /**
     * Kind 1619 has no lowercase `e` tag at all — NIP-34's PR Update example carries
     * only `["E", <pull-request-event-id>]` — so it can only ever arrive through the
     * uppercase root-scope filter. It sat in [RepliesAndReactionsKinds2] for a while
     * and matched nothing there.
     */
    @Test
    fun `PR-update kind is pulled by the uppercase-E engagement subscription`() {
        assertTrue(
            "GitPullRequestUpdateEvent (1619) is missing from RootScopedRepliesKinds. It anchors " +
                "at the PR it revises with NIP-22's uppercase `E` and carries no lowercase `e`, " +
                "so the `#e` engagement filter can never surface a PR's revision chain.",
            GitPullRequestUpdateEvent.KIND in RootScopedRepliesKinds,
        )
        assertFalse(
            "GitPullRequestUpdateEvent (1619) is back in RepliesAndReactionsKinds2, the `#e` " +
                "filter's kind list. 1619 has no lowercase `e` tag, so the entry matches nothing " +
                "and only makes the filter look like it covers PR updates.",
            GitPullRequestUpdateEvent.KIND in RepliesAndReactionsKinds2,
        )
    }
}
