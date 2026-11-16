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
package com.vitorpamplona.amethyst.commons.relayClient.auth

import com.vitorpamplona.amethyst.commons.relayauth.AuthPurpose
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPolicy
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.concurrent.ConcurrentMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** What the user chose when asked whether to authenticate with a relay. */
enum class UserAuthChoice {
    /**
     * Authenticate, and remember it for this run of the app only (see [RelayAuthSessionGrants]) —
     * reconnects to the same relay are answered silently, and the next cold start asks again.
     */
    ALLOW_ONCE,

    /** Authenticate now and remember ALLOW for this relay. */
    ALWAYS_ALLOW,

    /**
     * Authenticate now and switch the asking account's top-level policy to
     * [RelayAuthPolicy.ALWAYS], so every relay that
     * asks is answered without a prompt. The account-wide counterpart of [ALWAYS_ALLOW]; the dialog
     * confirms it before sending it, because it is a global setting.
     */
    ALWAYS_ALLOW_EVERYWHERE,

    /** Do not authenticate and remember DENY for this relay. */
    BLOCK,

    /**
     * Do not authenticate, and switch the asking account's top-level policy to
     * [RelayAuthPolicy.NEVER], so no relay is ever
     * answered again. The account-wide counterpart of [BLOCK], confirmed the same way — and, like
     * every route through [com.vitorpamplona.amethyst.commons.model.Account.changeDefaultRelayAuthPolicy],
     * it drops this run's session grants, which would otherwise outrank the policy it just set.
     */
    NEVER_ALLOW_EVERYWHERE,

    /** No decision (dismissed or timed out) — do not authenticate, don't remember. */
    DISMISS,
    ;

    /**
     * The account-wide policy this choice sets, or null for the four answers that are only about the
     * relay being asked about. Lets a caller apply the setting without re-deriving which choices are
     * account-wide.
     */
    val policyEverywhere: RelayAuthPolicy?
        get() =
            when (this) {
                ALWAYS_ALLOW_EVERYWHERE -> RelayAuthPolicy.ALWAYS
                NEVER_ALLOW_EVERYWHERE -> RelayAuthPolicy.NEVER
                ALLOW_ONCE, ALWAYS_ALLOW, BLOCK, DISMISS -> null
            }
}

/**
 * A pending "should I authenticate with this relay?" question, surfaced to the UI. The relay
 * connection coroutine is suspended on [reply] until the user (or a timeout) answers.
 *
 * @param askingAccount the account whose identity would be revealed. One socket is shared by every
 *   logged-in account, so the prompt has to name *whose* npub is at stake — it is the disclosure
 *   being authorized, and the only part of it the user cannot infer from context.
 * @param isMyOwnRelay [relayUrl] is in that account's own relay list. Lets the dialog explain a
 *   challenge from the user's own infrastructure instead of falling back to the blank wording.
 */
class RelayAuthPrompt(
    val relayUrl: NormalizedRelayUrl,
    val purposes: List<AuthPurpose>,
    val askingAccount: HexKey,
    val isMyOwnRelay: Boolean,
    private val reply: CompletableDeferred<UserAuthChoice>,
) {
    fun respond(choice: UserAuthChoice) {
        reply.complete(choice)
    }

    private val shown = CompletableDeferred<Unit>()

    /**
     * Called by the host the moment this prompt is actually put on screen. The answer window is
     * measured from here, not from when the challenge arrived — the host shows one dialog at a time,
     * so a prompt can sit queued behind another for a long while, and its clock must not be running
     * during that.
     */
    fun markShown() {
        shown.complete(Unit)
    }

    internal suspend fun awaitShown() = shown.await()

    /** True once answered by the user or resolved by the bus (e.g. timed out). */
    val isResolved: Boolean get() = reply.isCompleted

    /** Runs [block] when this prompt is resolved by any path, so the UI can stop showing it. */
    fun onResolved(block: () -> Unit) {
        reply.invokeOnCompletion { block() }
    }
}

/**
 * Bridges the background NIP-42 auth path to the UI: when a challenge resolves to ASK, the auth
 * coroutine calls [requestDecision] and suspends; a Composable collects [prompts], shows a dialog,
 * and calls [RelayAuthPrompt.respond]. Concurrent challenges for the same relay share one prompt so
 * the user isn't asked twice, and an unanswered prompt resolves to [UserAuthChoice.DISMISS] after
 * [timeoutMs] so a connection never hangs forever waiting on a UI that may not be present.
 */
class RelayAuthPromptBus(
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val queueWaitMs: Long = DEFAULT_QUEUE_WAIT_MS,
) {
    // replay so a challenge raised *before* the UI host subscribes — cold start, an account switch,
    // any moment no RelayAuthPromptHost is collecting — isn't dropped (which would stall the auth
    // coroutine the full timeout and then silently DISMISS). The host filters out any already-
    // resolved prompt it replays, so re-delivering stale ones is harmless.
    private val mutablePrompts = MutableSharedFlow<RelayAuthPrompt>(replay = 32, extraBufferCapacity = 32)
    val prompts: SharedFlow<RelayAuthPrompt> = mutablePrompts

    // Keyed by (relay, account), not relay alone: the dialog names the account whose npub would be
    // revealed, so two accounts challenged by the same relay are two different questions and must not
    // share one answer. Concurrent challenges for the SAME account still collapse into one prompt,
    // which is what this dedupe was always for.
    private val inFlight = ConcurrentMap<Pair<NormalizedRelayUrl, HexKey>, CompletableDeferred<UserAuthChoice>>()

    suspend fun requestDecision(
        relayUrl: NormalizedRelayUrl,
        purposes: List<AuthPurpose>,
        askingAccount: HexKey,
        isMyOwnRelay: Boolean,
    ): UserAuthChoice {
        // Lock-free ownership: getOrPut atomically installs OUR deferred or returns the
        // incumbent, and identity tells us which happened. The owner is the challenge that
        // first created the prompt; any concurrent challenge for the same relay awaits the
        // same answer. (The spare deferred allocated on a lost race is garbage-collected;
        // prompts are user-facing and rare, so that race is essentially never hit.)
        val key = relayUrl to askingAccount
        val mine = CompletableDeferred<UserAuthChoice>()
        val deferred = inFlight.getOrPut(key) { mine }
        val isOwner = deferred === mine

        // Only the owner surfaces a dialog and owns its deadline. A second challenge for the same
        // (relay, account) just rides along on the owner's answer — it must NOT run a deadline of its
        // own, because resolving the shared deferred would tear down a dialog the user is still
        // looking at.
        if (!isOwner) return rideAlong(deferred)

        val prompt = RelayAuthPrompt(relayUrl, purposes, askingAccount, isMyOwnRelay, deferred)
        mutablePrompts.emit(prompt)
        return try {
            awaitOrTimeout(prompt, deferred)
        } finally {
            inFlight.remove(key)
        }
    }

    /**
     * Waits for the owner's answer without imposing a deadline that could resolve the shared prompt.
     * The cap exists only so a cancelled owner can't strand this caller forever; it deliberately
     * returns [UserAuthChoice.DISMISS] *locally* rather than completing the deferred.
     */
    private suspend fun rideAlong(deferred: CompletableDeferred<UserAuthChoice>): UserAuthChoice = withTimeoutOrNull(queueWaitMs + 2 * timeoutMs) { deferred.await() } ?: UserAuthChoice.DISMISS

    /**
     * Waits for the user's answer, giving them [timeoutMs] **from the moment the dialog is on screen**
     * rather than from the moment the challenge arrived.
     *
     * That distinction is the whole point. The host renders one dialog at a time, so when several
     * relays challenge at once every prompt but the first is queued and invisible — and with a single
     * deadline measured from arrival, those queued prompts expired without ever being shown. The user
     * saw nothing, the relay was silently denied, and a click on a dialog that had already expired was
     * swallowed whole: `complete()` is a no-op on a resolved deferred, so the auth was never sent and
     * not even the "always allow" rule was written.
     */
    private suspend fun awaitOrTimeout(
        prompt: RelayAuthPrompt,
        deferred: CompletableDeferred<UserAuthChoice>,
    ): UserAuthChoice {
        // Is anything able to display this? With no host collecting — a headless background process,
        // or a cold start before the UI subscribes — nobody can ever answer, and the relay coroutine
        // must not hang. That case is what the timeout has always been for, so it keeps the old clock.
        val hasHost = withTimeoutOrNull(timeoutMs) { mutablePrompts.subscriptionCount.first { it > 0 } } != null

        if (hasHost) {
            // Wait for this prompt's turn at the front of the host's queue. Capped, so a host that
            // stops rendering (backgrounded mid-queue) still can't suspend the connection forever.
            withTimeoutOrNull(queueWaitMs) { prompt.awaitShown() }
        }

        // The answer window proper. If the user already answered while we were waiting above, this
        // returns immediately.
        withTimeoutOrNull(timeoutMs) { deferred.await() }?.let { return it }

        // Timed out: resolve the deferred so any UI still showing this prompt can drop it, and so a
        // concurrent waiter on the same deferred gets an answer too. complete() is a no-op if a late
        // user response already won the race.
        deferred.complete(UserAuthChoice.DISMISS)
        return deferred.await()
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 60_000L

        /** How long a prompt may wait its turn behind other dialogs before we give up on it. */
        const val DEFAULT_QUEUE_WAIT_MS = 5 * 60_000L
    }
}
