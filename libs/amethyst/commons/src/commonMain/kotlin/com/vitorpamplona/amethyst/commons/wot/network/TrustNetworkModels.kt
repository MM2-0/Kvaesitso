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
package com.vitorpamplona.amethyst.commons.wot.network

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkHeader
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkIndex
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkSyncResult

/** A loaded trust network: who the provider asserts about, and where it came from. */
@Immutable
class TrustNetwork(
    val header: TrustNetworkHeader,
    val index: TrustNetworkIndex,
) {
    fun isFrom(provider: ServiceProviderTag) = header.provider == provider.pubkey && header.relay == provider.relayUrl.url
}

/**
 * The account's rank provider once its kind 10040 (or the backup of it) has been read and any
 * private part decrypted: [provider] is then the final answer, null meaning there is none. The
 * flow carrying it stays null until then, so "not known yet" never reads as "no provider".
 */
@Immutable
class ResolvedProvider(
    val provider: ServiceProviderTag?,
)

/**
 * The provider's card for someone, seen between syncs (a profile on screen fetched it) and newer
 * than the loaded index. Held in memory until the next sync, which downloads it for good.
 */
@Immutable
class TrustOverlayCard(
    /** The member rank (see `memberRank`), or null when this card removes them. */
    val rank: Int?,
    val followers: Int,
    val createdAt: Long,
)

/** Why someone is or is not in the user's network. [isKnown] is null when no network is active. */
enum class TrustVerdict(
    val isKnown: Boolean?,
) {
    /** No network for the current provider: callers behave as if the feature were off. */
    NO_NETWORK(null),
    SELF(true),
    FOLLOW(true),

    /** The provider ranks them at or above the minimum score. */
    TRUSTED(true),

    /** The provider scored them, below the minimum score. */
    BELOW_MIN_SCORE(false),

    /** The provider has no card for them. */
    NOT_IN_NETWORK(false),
}

/** How one sync ended. */
sealed interface TrustNetworkOutcome {
    /** The network was replaced by [result]'s. */
    class Applied(
        val result: TrustNetworkSyncResult,
    ) : TrustNetworkOutcome

    /** The relay had nothing new; only the check time moved, to [header]'s. */
    class Unchanged(
        val header: TrustNetworkHeader,
    ) : TrustNetworkOutcome

    /** The provider has published no cards yet (it is still computing a new user's scores). */
    data object NoScoresYet : TrustNetworkOutcome

    /** A download is due but waits for an unmetered network. */
    data object WaitingForUnmetered : TrustNetworkOutcome

    /** The provider changed while this ran, so its result was dropped. */
    data object ProviderChanged : TrustNetworkOutcome

    /** The relay walk did not finish; nothing was applied. [detail] says why, for logs. */
    class Incomplete(
        val detail: String?,
    ) : TrustNetworkOutcome

    class Failed(
        val message: String,
    ) : TrustNetworkOutcome
}

/** What one sync did: the [kind] that ran, its [outcome], and the relay's [result] when it got one. */
class TrustNetworkRun(
    val kind: TrustNetworkSyncStatus.Kind,
    val outcome: TrustNetworkOutcome,
    val result: TrustNetworkSyncResult? = null,
) {
    /** The network now reflects the relay (replaced, or confirmed unchanged). */
    val applied: Boolean get() = outcome is TrustNetworkOutcome.Applied || outcome is TrustNetworkOutcome.Unchanged
}

/** Why the last sync did not apply, for the settings screen. */
@Immutable
sealed interface TrustNetworkProblem {
    data object NoScoresYet : TrustNetworkProblem

    data class Failed(
        val message: String,
    ) : TrustNetworkProblem
}

/** What the background sync is doing, for the settings screen. */
@Immutable
data class TrustNetworkSyncStatus(
    val running: Kind? = null,
    /** Cards verified so far by the running sync. */
    val verified: Int = 0,
    /** The relay's count for the running sync, when it gave one. */
    val expected: Int? = null,
    /** A cold download is due but waits for an unmetered network. */
    val waitingForUnmetered: Boolean = false,
    /** Why the last sync did not apply, or null when it did. */
    val problem: TrustNetworkProblem? = null,
) {
    enum class Kind { DOWNLOAD, UPDATE, FULL_CHECK }
}
