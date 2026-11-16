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

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Persisted scope for an AUTH approval decision.
 *
 * `ONCE` is in-memory only — never written to disk. `ALWAYS` and `BLOCKED`
 * persist via [AuthApprovalStore].
 */
enum class AuthApprovalScope {
    /** Approve this session; don't persist. */
    ONCE,

    /** Approve indefinitely (or until the store's TTL expires the row). */
    ALWAYS,

    /** Reject indefinitely. Future AUTH challenges from this relay are silently dropped. */
    BLOCKED,
}

/**
 * The classifier verdict for a single AUTH challenge.
 *
 * `Allow` and `Block` are immediate. `Pending` means the user needs to decide;
 * the policy hands back a [CompletableDeferred] that the UI banner completes
 * once the user picks `[Once] [Always] [Never]`.
 */
sealed interface AuthApprovalDecision {
    /** Auto-sign the AUTH event for this relay. */
    data object Allow : AuthApprovalDecision

    /** Silently drop the AUTH challenge. */
    data object Block : AuthApprovalDecision

    /**
     * Suspend the signer until the user resolves the prompt.
     *
     * @property pending populated with the user's choice when the banner is
     *   actioned. The signer awaits this deferred; if it resolves to
     *   [AuthApprovalScope.BLOCKED] the AUTH is dropped, otherwise signed.
     */
    data class Pending(
        val pending: CompletableDeferred<AuthApprovalScope>,
    ) : AuthApprovalDecision
}

/**
 * A pending tier-2 AUTH approval surfaced to the user.
 *
 * Created when the policy decides a challenge needs user consent. Subscribers
 * (an `AccountAuthApprovals` ViewModel — wired in P2.5) render a banner with
 * `[Once] [Always] [Never]` buttons that resolve [decision] via `complete()`.
 *
 * `pendingCount` lets the banner coalesce multiple challenges from the same
 * relay into one row (`"<relay-url> requires authentication for 3 messages"`)
 * rather than stacking duplicate banners.
 */
data class PendingAuthApproval(
    val relayUrl: NormalizedRelayUrl,
    val decision: CompletableDeferred<AuthApprovalScope>,
    val pendingCount: Int = 1,
)

/**
 * Per-account approval store. Implementations persist `ALWAYS` / `BLOCKED`
 * grants (typically to a SQLite `auth_approvals` table, wired in P2.4).
 *
 * `getScope` returns `null` if no decision is recorded for the relay.
 */
interface AuthApprovalStore {
    /** Returns the persisted decision for `relayUrl`, or `null` if unknown. */
    suspend fun getScope(relayUrl: NormalizedRelayUrl): AuthApprovalScope?

    /**
     * Record a user decision. `ONCE` decisions are NOT persisted by contract —
     * the policy caches them in-memory for the current session only.
     */
    suspend fun setScope(
        relayUrl: NormalizedRelayUrl,
        scope: AuthApprovalScope,
    )

    /** Wipe all persisted approvals. Called on account delete / logout. */
    suspend fun clear()
}

/**
 * In-memory [AuthApprovalStore] used as a development scaffold and as the
 * `ONCE` cache layer on top of a persistent store. Tier-2 banner approvals
 * with `ONCE` scope live here for the session and are dropped on logout.
 */
class InMemoryAuthApprovalStore : AuthApprovalStore {
    private val scopes = mutableMapOf<NormalizedRelayUrl, AuthApprovalScope>()
    private val lock = Mutex()

    override suspend fun getScope(relayUrl: NormalizedRelayUrl): AuthApprovalScope? = lock.withLock { scopes[relayUrl] }

    override suspend fun setScope(
        relayUrl: NormalizedRelayUrl,
        scope: AuthApprovalScope,
    ) {
        lock.withLock { scopes[relayUrl] = scope }
    }

    override suspend fun clear() {
        lock.withLock { scopes.clear() }
    }
}

/**
 * The classifier between the relay client's `signWithAllLoggedInUsers` lambda
 * and the actual signer.
 *
 * Two tiers:
 *
 * - **Tier 1 (auto-allow):** the relay is in the user's own outbox or
 *   NIP-17 DM-inbox set, or has a persisted `ALWAYS` grant. Sign immediately,
 *   no prompt. These are relays the user has already declared they trust.
 * - **Tier 2 (prompt):** anything else, with the exception of relays that
 *   carry a persisted `BLOCKED` grant. Surface a [PendingAuthApproval] via
 *   [onPromptRequired] and suspend until the user resolves the
 *   [CompletableDeferred]. If `ONCE`, cache for this session; if `ALWAYS` or
 *   `BLOCKED`, persist via the store.
 *
 * No tier-3: every challenge is either auto-allowed, blocked by a persisted
 * decision, or surfaced to the user. There is no silent third path.
 *
 * @property selfApprovedRelays the union of own outbox + DM-inbox + any
 *   account-level pre-approval. Recomputed by the caller on Account state
 *   changes. Tier 1 if the challenger is in this set.
 * @property store persistence layer (SQLite-backed in production, in-memory in
 *   tests).
 * @property onPromptRequired called when a [PendingAuthApproval] needs to be
 *   surfaced to the UI. The UI subscribes to this side-channel and completes
 *   the contained [CompletableDeferred] with the user's pick.
 */
class AuthApprovalPolicy(
    val selfApprovedRelays: () -> Set<NormalizedRelayUrl>,
    val store: AuthApprovalStore,
    val onPromptRequired: (PendingAuthApproval) -> Unit,
) {
    /**
     * Decide what to do with an AUTH challenge from `relayUrl`.
     *
     * @return [AuthApprovalDecision.Allow] for tier-1 / persisted-ALWAYS,
     *   [AuthApprovalDecision.Block] for persisted-BLOCKED,
     *   [AuthApprovalDecision.Pending] (and emits to [onPromptRequired]) for
     *   unknown relays.
     */
    suspend fun classify(relayUrl: NormalizedRelayUrl): AuthApprovalDecision {
        // Persisted decision wins over tier-1: if user explicitly blocked a
        // relay that happens to also be in their outbox, respect the block.
        when (store.getScope(relayUrl)) {
            AuthApprovalScope.ALWAYS -> return AuthApprovalDecision.Allow
            AuthApprovalScope.BLOCKED -> return AuthApprovalDecision.Block
            AuthApprovalScope.ONCE -> return AuthApprovalDecision.Allow
            null -> Unit
        }

        if (relayUrl in selfApprovedRelays()) {
            return AuthApprovalDecision.Allow
        }

        val deferred = CompletableDeferred<AuthApprovalScope>()
        onPromptRequired(PendingAuthApproval(relayUrl, deferred))
        return AuthApprovalDecision.Pending(deferred)
    }

    /** Persist (or cache) the user's choice from a [PendingAuthApproval] resolution. */
    suspend fun recordDecision(
        relayUrl: NormalizedRelayUrl,
        scope: AuthApprovalScope,
    ) {
        // `ONCE` lives in-memory only (the InMemoryAuthApprovalStore handles
        // this transparently). `ALWAYS` and `BLOCKED` persist via the store.
        store.setScope(relayUrl, scope)
    }
}
