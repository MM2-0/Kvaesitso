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
package com.vitorpamplona.amethyst.commons.wot.onboarding

import com.vitorpamplona.amethyst.commons.model.trustedAssertions.TrustProviderRow
import com.vitorpamplona.amethyst.commons.wot.onboarding.brainstorm.BrainstormOnboarding
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ProviderTypes
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag

/**
 * A NIP-85 trust provider whose sign-up Amethyst knows how to run.
 *
 * NIP-85 says how a provider publishes scores and how a user points to it (kind 10040), but not
 * how a user becomes a provider's customer or learns the per-user keys it signs with. Each
 * provider has its own API for that, so each one is an implementation of this interface.
 *
 * The provider decides the kind 10040 rows ([TrustProviderRegistration.rows]): it knows which
 * key serves which tag. The user can also sign up on the provider's own site ([setupUrl]),
 * which publishes the list itself. Writing the rows by hand is the fallback for when that goes
 * wrong, or for a provider without either.
 */
interface TrustProviderOnboarding {
    /** Stable id, for saving which guided provider the user picked. */
    val id: String

    /** The provider's name, as shown to the user. */
    val name: String

    /** Where to read about the provider. */
    val homepage: String

    /** Where the user can sign up and publish their kind 10040 on the provider's own site. */
    val setupUrl: String

    /**
     * The provider's own observer (a NIP-05 or npub), when it has one: the user can copy its
     * kind 10040 rows to see the network as the provider's default view, without signing up.
     */
    val houseObserver: String? get() = null

    /** The relay the provider publishes its kind 30382 cards to. */
    val relay: NormalizedRelayUrl

    /**
     * Relays the provider reads the social graph from. Before a sign-up, Amethyst sends the
     * user's profile, follow list, mute list and relay list there, so the first scores start
     * from the user's current graph instead of whatever those relays happened to hold.
     */
    val graphRelays: Set<NormalizedRelayUrl> get() = emptySet()

    /**
     * A relay the provider runs for the whole network, when it has one. A sign-up adds it to the
     * user's trusted, search, indexer and favorite relay lists, and moves every feed set to
     * Global onto it.
     */
    val networkRelay: NormalizedRelayUrl? get() = null

    /** Whether the 10040 entry [provider] points at this provider (to mark it "in use"). */
    fun serves(provider: ServiceProviderTag): Boolean

    /**
     * Signs in as [signer]'s user, asks the provider to compute their scores and returns the
     * kind 10040 rows it serves them with. Calls [onStep] as it goes. Throws
     * [TrustProviderException].
     */
    suspend fun register(
        signer: NostrSigner,
        onStep: (TrustProviderOnboardingStep) -> Unit = {},
    ): TrustProviderRegistration
}

enum class TrustProviderOnboardingStep {
    /** Sending the user's own graph events to [TrustProviderOnboarding.graphRelays]. */
    SHARING_GRAPH,
    SIGNING_IN,
    REQUESTING_SCORES,
    FETCHING_SERVICE_KEY,
}

/** What a sign-up returns: the kind 10040 rows the provider serves this user with. */
class TrustProviderRegistration(
    /** As the provider hands them over; always holds a `30382:rank` row. */
    val rows: List<TrustProviderRow>,
    /** False while the provider has not finished computing the first scores. */
    val scoresReady: Boolean,
) {
    init {
        require(rows.any { it.name == ProviderTypes.rank.toValue() }) { "A registration needs a 30382:rank row" }
    }

    /** The per-user key that signs the `30382:rank` cards: the network's provider. */
    val serviceKey: HexKey get() = rankRow.key

    /** Where the rank cards are published. */
    val relay: NormalizedRelayUrl get() = rankRow.relay

    private val rankRow: TrustProviderRow get() = rows.first { it.name == ProviderTypes.rank.toValue() }
}

class TrustProviderException(
    val reason: Reason,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    enum class Reason {
        /** The provider could not be reached, or answered with a server error. */
        UNREACHABLE,

        /** The provider rejected the sign-in (signature refused, challenge expired). */
        SIGN_IN_REJECTED,

        /** The user's signer declined to sign. */
        SIGNER_DECLINED,

        /** The provider answered something we do not understand. */
        UNEXPECTED_RESPONSE,
    }
}

/** The providers with guided sign-up, in the order the settings screen offers them. */
object KnownTrustProviders {
    fun all(http: TrustProviderHttp): List<TrustProviderOnboarding> = listOf(BrainstormOnboarding(http))
}
