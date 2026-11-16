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
package com.vitorpamplona.amethyst.commons.model

/**
 * The outcome of redeeming a Concord invite link (CORD-05). Separating the failure
 * modes lets the UI tell the user *why* it failed and — crucially — whether
 * retrying could ever help, so a link we can never open doesn't strand the user on
 * an endless "redeeming…" spinner with a retry button that loops forever.
 */
sealed interface ConcordInviteResult {
    /** Redeemed and joined; navigate to [communityId]. */
    data class Joined(
        val communityId: String,
    ) : ConcordInviteResult

    /** The link itself is malformed, or this account can't join (read-only key). Retrying can't help. */
    data object InvalidLink : ConcordInviteResult

    /**
     * No invite bundle was reachable on any relay — a transient miss (relays down,
     * link too new to have propagated, or expired). Retrying may help.
     */
    data object NotReachable : ConcordInviteResult

    /**
     * The link was revoked: the newest event at its coordinate is a `vsk=9` revocation
     * tombstone (CORD-05 §2). Retrying can't help — the owner retired this link.
     */
    data object Revoked : ConcordInviteResult

    /**
     * The bundle opened fine, but its `expires_at` has passed. Retrying can't help —
     * unlike [Revoked] the owner didn't retire the link, it simply timed out, so the
     * user's next step is to ask for a fresh one.
     */
    data object Expired : ConcordInviteResult

    /**
     * The link opens, but this community's roster has banned us (CORD-04).
     *
     * A Refounding re-mints every outstanding link onto the new root, and a removed member keeps the
     * URL and its unlock token forever — so honouring the link alone would hand the new keys to the
     * very account the rotation expelled.
     */
    data object Banned : ConcordInviteResult

    /**
     * The bundle event was found but could not be opened with the link's token —
     * typically because it was minted by a newer/incompatible Concord client whose
     * bundle format this app can't read yet. Retrying can't help.
     */
    data object Incompatible : ConcordInviteResult

    /**
     * The invite was good, but the membership could not be written to the Community List: the
     * List is not loaded yet, or every fragment this device holds is full while the rest are
     * missing (CORD-02 §8). Nothing was announced. Retrying can help once the List loads.
     */
    data object NotSaved : ConcordInviteResult

    /**
     * This invite is already being accepted (a second tap, or the hub and Notifications at once).
     * Nothing was done; the accept already running reports the outcome.
     */
    data object InProgress : ConcordInviteResult
}
