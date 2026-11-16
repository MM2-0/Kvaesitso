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
package com.vitorpamplona.amethyst.commons.connectedApps.signers

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock

/**
 * Persistence for the per-app internal-signer permissions: each app's [AppSignerPolicy]
 * trust level and any [NostrOpDecision] per-operation overrides. Keyed by napplet
 * coordinate (e.g. `"<authorPubKey>:<identifier>"`).
 *
 * The Android implementation uses one DataStore file per coordinate so loading one app's
 * permissions never reads another app's data — essential at scale (1000s of apps).
 * Tests use [InMemoryNostrSignerPermissionStore].
 */
interface NostrSignerPermissionStore {
    /** The stored trust level for [coordinate], or `null` if no policy has been set yet. */
    suspend fun loadPolicy(coordinate: String): AppSignerPolicy?

    /** Persist [policy] as the trust level for [coordinate]. */
    suspend fun storePolicy(
        coordinate: String,
        policy: AppSignerPolicy,
    )

    /** Remove the stored trust level for [coordinate]. */
    suspend fun clearPolicy(coordinate: String)

    /** The stored per-operation decision for ([coordinate], [op]), or `null` if none set. */
    suspend fun loadOpDecision(
        coordinate: String,
        op: NostrSignerOp,
    ): NostrOpDecision?

    /** Persist [decision] for ([coordinate], [op]). */
    suspend fun storeOpDecision(
        coordinate: String,
        op: NostrSignerOp,
        decision: NostrOpDecision,
    )

    /** Remove the per-operation decision for ([coordinate], [op]). */
    suspend fun clearOpDecision(
        coordinate: String,
        op: NostrSignerOp,
    )

    /** All stored trust levels, keyed by coordinate — for the permissions-management screen. */
    suspend fun allPolicies(): Map<String, AppSignerPolicy>

    /**
     * All per-operation overrides for [coordinate], keyed by [NostrSignerOp.key] — for the
     * permissions-management screen.
     */
    suspend fun allOpDecisions(coordinate: String): Map<String, NostrOpDecision>

    /** Remove all signer permissions (policy + all op overrides) for [coordinate]. */
    suspend fun clearAll(coordinate: String)

    /** The stored expiry timestamp (Unix epoch seconds) for [op] of [coordinate], or `null` = no expiry. */
    suspend fun loadOpExpiry(
        coordinate: String,
        op: NostrSignerOp,
    ): Long?

    /** Persist [expiresAt] (Unix epoch seconds) for ([coordinate], [op]). */
    suspend fun storeOpExpiry(
        coordinate: String,
        op: NostrSignerOp,
        expiresAt: Long,
    )

    /** Remove the expiry for ([coordinate], [op]). */
    suspend fun clearOpExpiry(
        coordinate: String,
        op: NostrSignerOp,
    )

    /** The last time (Unix epoch seconds) any auto-approved operation ran for [coordinate], or `null`. */
    suspend fun loadLastUsed(coordinate: String): Long?

    /** Persist [epochSeconds] as the last-used time for [coordinate]. */
    suspend fun storeLastUsed(
        coordinate: String,
        epochSeconds: Long,
    )
}

/** A thread-safe in-memory [NostrSignerPermissionStore] for tests and ephemeral sessions. */
class InMemoryNostrSignerPermissionStore : NostrSignerPermissionStore {
    private val lock = KmpLock()
    private val policies = mutableMapOf<String, AppSignerPolicy>()
    private val opDecisions = mutableMapOf<String, MutableMap<String, NostrOpDecision>>()
    private val opExpiries = mutableMapOf<String, MutableMap<String, Long>>()
    private val lastUsedMap = mutableMapOf<String, Long>()

    override suspend fun loadPolicy(coordinate: String): AppSignerPolicy? = lock.withLock { policies[coordinate] }

    override suspend fun storePolicy(
        coordinate: String,
        policy: AppSignerPolicy,
    ) = lock.withLock { policies[coordinate] = policy }

    override suspend fun clearPolicy(coordinate: String) =
        lock.withLock {
            policies.remove(coordinate)
            Unit
        }

    override suspend fun loadOpDecision(
        coordinate: String,
        op: NostrSignerOp,
    ): NostrOpDecision? = lock.withLock { opDecisions[coordinate]?.get(op.key) }

    override suspend fun storeOpDecision(
        coordinate: String,
        op: NostrSignerOp,
        decision: NostrOpDecision,
    ) = lock.withLock {
        opDecisions.getOrPut(coordinate) { mutableMapOf() }[op.key] = decision
    }

    override suspend fun clearOpDecision(
        coordinate: String,
        op: NostrSignerOp,
    ) = lock.withLock {
        opDecisions[coordinate]?.remove(op.key)
        Unit
    }

    override suspend fun allPolicies(): Map<String, AppSignerPolicy> = lock.withLock { policies.toMap() }

    override suspend fun allOpDecisions(coordinate: String): Map<String, NostrOpDecision> = lock.withLock { opDecisions[coordinate]?.toMap() ?: emptyMap() }

    override suspend fun clearAll(coordinate: String) =
        lock.withLock {
            policies.remove(coordinate)
            opDecisions.remove(coordinate)
            opExpiries.remove(coordinate)
            lastUsedMap.remove(coordinate)
            Unit
        }

    override suspend fun loadOpExpiry(
        coordinate: String,
        op: NostrSignerOp,
    ): Long? = lock.withLock { opExpiries[coordinate]?.get(op.key) }

    override suspend fun storeOpExpiry(
        coordinate: String,
        op: NostrSignerOp,
        expiresAt: Long,
    ) = lock.withLock { opExpiries.getOrPut(coordinate) { mutableMapOf() }[op.key] = expiresAt }

    override suspend fun clearOpExpiry(
        coordinate: String,
        op: NostrSignerOp,
    ) = lock.withLock {
        opExpiries[coordinate]?.remove(op.key)
        Unit
    }

    override suspend fun loadLastUsed(coordinate: String): Long? = lock.withLock { lastUsedMap[coordinate] }

    override suspend fun storeLastUsed(
        coordinate: String,
        epochSeconds: Long,
    ) = lock.withLock { lastUsedMap[coordinate] = epochSeconds }
}
