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
package com.vitorpamplona.amethyst.commons.napplet.permissions

import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentity
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock

/**
 * The per-applet permission ledger: the single place that decides whether a brokered
 * operation may run. It layers two scopes:
 *
 * - **persistent** grants ([GrantState.ALLOW_ALWAYS] / [GrantState.DENY]) from the
 *   injected [NappletPermissionStore], and
 * - **session** grants ([GrantState.ALLOW_SESSION]) held in memory for this instance.
 *
 * A persistent [GrantState.DENY] always wins — a user who blocked a capability is never
 * silently re-prompted or overridden by a session grant. Recording an [GrantState.ALLOW_ONCE]
 * is a no-op (it authorizes only the in-flight request and is applied by the broker, not
 * remembered here).
 */
class NappletPermissionLedger private constructor(
    private val store: NappletPermissionStore,
    /**
     * The account session grants belong to, read at call time. This ledger is a process-wide
     * singleton shared by every account, and a bare coordinate carries no account (it is
     * `<appAuthor>:<identifier>`), so without this an "allow for this session" granted under one
     * npub would authorize the same applet under every other npub on the device. The persistent
     * store is namespaced the same way.
     */
    private val accountPubKey: () -> String,
    private val lock: KmpLock,
    // "<account>\u0000<coordinate>" -> capability -> ALLOW_SESSION (the only state kept here).
    private val session: MutableMap<String, MutableMap<NappletCapability, GrantState>>,
) {
    constructor(
        store: NappletPermissionStore,
        accountPubKey: () -> String = { "" },
    ) : this(store, accountPubKey, KmpLock(), mutableMapOf())

    /**
     * This ledger for [pubKey] alone, whoever is logged in: a broker acting for one account (a
     * full-screen app survives an account switch) must judge and record by that account's grants. It
     * shares this ledger's session grants, so [endSession] still clears them.
     */
    fun forAccount(pubKey: String): NappletPermissionLedger = NappletPermissionLedger(store.forAccount(pubKey), { pubKey }, lock, session)

    private fun sessionKey(identity: NappletIdentity) = "${accountPubKey()}\u0000${identity.coordinate}"

    /**
     * The standing decision for ([identity], [capability]) without prompting. A persistent
     * [GrantState.DENY] takes precedence over any session allow.
     */
    suspend fun decide(
        identity: NappletIdentity,
        capability: NappletCapability,
    ): PermissionDecision {
        val persistent = store.load(identity.coordinate)[capability]
        if (persistent == GrantState.DENY) return PermissionDecision.DENY
        if (persistent == GrantState.ALLOW_ALWAYS) return PermissionDecision.ALLOW

        val sessionGrant = lock.withLock { session[sessionKey(identity)]?.get(capability) }
        if (sessionGrant == GrantState.ALLOW_SESSION) return PermissionDecision.ALLOW

        return PermissionDecision.ASK
    }

    /**
     * Persists or remembers a user's decision. [GrantState.ALLOW_ALWAYS] and
     * [GrantState.DENY] go to the store; [GrantState.ALLOW_SESSION] is kept in memory;
     * [GrantState.ALLOW_ONCE] and [GrantState.ASK] are not recorded.
     */
    suspend fun record(
        identity: NappletIdentity,
        capability: NappletCapability,
        grant: GrantState,
    ) {
        when (grant) {
            GrantState.ALLOW_ALWAYS, GrantState.DENY -> {
                // A new persistent decision supersedes any lingering session grant.
                lock.withLock { session[sessionKey(identity)]?.remove(capability) }
                store.store(identity.coordinate, capability, grant)
            }
            GrantState.ALLOW_SESSION ->
                lock.withLock {
                    session.getOrPut(sessionKey(identity)) { mutableMapOf() }[capability] = grant
                }
            GrantState.ALLOW_ONCE, GrantState.ASK -> Unit // transient; not remembered
        }
    }

    /** All persisted standing grants, keyed by coordinate — for the permissions-management UI. */
    suspend fun allPersistedGrants(): Map<String, Map<NappletCapability, GrantState>> = store.all()

    /** Forgets a single capability grant for [identity] — both persisted and in-session. */
    suspend fun revoke(
        identity: NappletIdentity,
        capability: NappletCapability,
    ) {
        lock.withLock { session[sessionKey(identity)]?.remove(capability) }
        store.remove(identity.coordinate, capability)
    }

    /** Forgets all grants for [identity] — both the persisted and the in-session ones. */
    suspend fun revokeAll(identity: NappletIdentity) {
        lock.withLock { session.remove(sessionKey(identity)) }
        store.clear(identity.coordinate)
    }

    /** Drops only the in-memory session grants (e.g. when the user closes all applets). */
    fun endSession() {
        lock.withLock { session.clear() }
    }
}
