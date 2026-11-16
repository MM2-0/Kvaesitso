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
package com.vitorpamplona.amethyst.commons.viewmodels

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.trustedAssertions.TrustProviderRow
import com.vitorpamplona.amethyst.commons.wot.onboarding.KnownTrustProviders
import com.vitorpamplona.amethyst.commons.wot.onboarding.TrustProviderException
import com.vitorpamplona.amethyst.commons.wot.onboarding.TrustProviderHttp
import com.vitorpamplona.amethyst.commons.wot.onboarding.TrustProviderOnboarding
import com.vitorpamplona.amethyst.commons.wot.onboarding.TrustProviderOnboardingStep
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip05DnsIdentifiers.INip05Client
import com.vitorpamplona.quartz.nip05DnsIdentifiers.resolveUserHexOrNull
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ProviderTypes
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

/** Where a guided sign-up is. */
@Immutable
sealed interface WebOfTrustSetup {
    data object Idle : WebOfTrustSetup

    /** Signing up with [providerId], at [step]. */
    data class Running(
        val providerId: String,
        val step: TrustProviderOnboardingStep,
    ) : WebOfTrustSetup

    /** Signed up; publishing the new provider list. */
    data class Saving(
        val providerId: String,
    ) : WebOfTrustSetup

    /** [reason] is null for an error the providers do not classify; [message] says what happened. */
    data class Failed(
        val providerId: String,
        val reason: TrustProviderException.Reason?,
        val message: String,
    ) : WebOfTrustSetup
}

/** Where copying another user's kind 10040 rows is. */
@Immutable
sealed interface WebOfTrustCopy {
    data object Idle : WebOfTrustCopy

    data object Loading : WebOfTrustCopy

    /** [rows] are [pubkey]'s public rows, ready to review and use. */
    data class Copied(
        val pubkey: HexKey,
        val rows: List<TrustProviderRow>,
    ) : WebOfTrustCopy

    data class Failed(
        val reason: Reason,
    ) : WebOfTrustCopy {
        enum class Reason {
            /** Not a key, nor a NIP-05 that resolves. */
            UNKNOWN_USER,

            /** No kind 10040 found for them. */
            NO_LIST,

            /** Their list names no `30382:rank` provider in public. */
            NO_RANK,
        }
    }
}

/**
 * The Web of Trust settings' actions: guided sign-ups ([providers]), which publish the kind 10040
 * rows the provider hands over, and the fallbacks of writing those rows by hand or copying another
 * user's (to see the network as they do). The network
 * itself (download, minimum score, removal) is read and changed through [Account] directly.
 */
class WebOfTrustViewModel(
    private val account: Account,
    http: TrustProviderHttp,
    private val nip05Client: INip05Client?,
) : ViewModel() {
    val providers: List<TrustProviderOnboarding> = KnownTrustProviders.all(http)

    private val _setup = MutableStateFlow<WebOfTrustSetup>(WebOfTrustSetup.Idle)
    val setup: StateFlow<WebOfTrustSetup> = _setup.asStateFlow()

    /**
     * The guided provider this view model last signed the user up with, until [acknowledge]: what
     * a screen shows its "done" state from, once [setup] is back to Idle.
     */
    private val _completed = MutableStateFlow<String?>(null)
    val completed: StateFlow<String?> = _completed.asStateFlow()

    fun acknowledge() {
        _completed.value = null
    }

    /** Whether hand-written rows go to the encrypted part of the kind 10040 (a fallback option). */
    private val _isPrivate = MutableStateFlow(false)
    val isPrivate: StateFlow<Boolean> = _isPrivate.asStateFlow()

    fun setPrivate(value: Boolean) {
        _isPrivate.value = value
    }

    /**
     * Sends the user's graph to [onboarding]'s graph relays, signs up with it and publishes the
     * rows it serves the user with, as the provider would (in the public tags), then adopts its
     * network relay. One sign-up at a time.
     */
    fun setUp(onboarding: TrustProviderOnboarding) {
        if (_setup.value is WebOfTrustSetup.Running || _setup.value is WebOfTrustSetup.Saving) return
        val sharing = onboarding.graphRelays.isNotEmpty()
        _setup.value = WebOfTrustSetup.Running(onboarding.id, if (sharing) TrustProviderOnboardingStep.SHARING_GRAPH else TrustProviderOnboardingStep.SIGNING_IN)
        viewModelScope.launch {
            _setup.value =
                try {
                    if (sharing) {
                        val accepted = account.shareTrustGraph(onboarding.graphRelays)
                        Log.d("WebOfTrust") { "${onboarding.name} graph relays took $accepted events" }
                        _setup.value = WebOfTrustSetup.Running(onboarding.id, TrustProviderOnboardingStep.SIGNING_IN)
                    }
                    val registration = onboarding.register(account.signer) { step -> _setup.value = WebOfTrustSetup.Running(onboarding.id, step) }
                    _setup.value = WebOfTrustSetup.Saving(onboarding.id)
                    account.setTrustProviderRows(registration.rows, isPrivate = false)
                    onboarding.networkRelay?.let { adoptNetworkRelay(it) }
                    _completed.value = onboarding.id
                    WebOfTrustSetup.Idle
                } catch (e: CancellationException) {
                    throw e
                } catch (e: TrustProviderException) {
                    WebOfTrustSetup.Failed(onboarding.id, e.reason, e.message.orEmpty())
                } catch (e: SignerExceptions) {
                    WebOfTrustSetup.Failed(onboarding.id, TrustProviderException.Reason.SIGNER_DECLINED, e.message.orEmpty())
                } catch (e: Exception) {
                    WebOfTrustSetup.Failed(onboarding.id, null, e.message ?: e::class.simpleName.orEmpty())
                }
        }
    }

    /** Best effort: the provider is already set, so a relay list the signer refuses does not undo it. */
    private suspend fun adoptNetworkRelay(relay: NormalizedRelayUrl) {
        try {
            account.adoptTrustProviderRelay(relay)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("WebOfTrust", "Could not add $relay to the relay lists", e)
        }
    }

    /** The key of [onboarding]'s own observer account, to show its picture; null when it has none. */
    suspend fun houseObserverKey(onboarding: TrustProviderOnboarding): HexKey? =
        try {
            onboarding.houseObserver?.let { resolveUserHexOrNull(it, nip05Client) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private val _copy = MutableStateFlow<WebOfTrustCopy>(WebOfTrustCopy.Idle)
    val copy: StateFlow<WebOfTrustCopy> = _copy.asStateFlow()

    /**
     * Reads the public kind 10040 rows of [who] (an npub, hex key or NIP-05) for the user to
     * review and use: their provider's cards are computed for them, so the network shows as
     * they see it. [hints] are relays that may hold their list.
     */
    fun copyRowsFrom(
        who: String,
        hints: Set<NormalizedRelayUrl> = emptySet(),
    ) {
        if (_copy.value is WebOfTrustCopy.Loading) return
        _copy.value = WebOfTrustCopy.Loading
        viewModelScope.launch {
            _copy.value =
                try {
                    val pubkey = resolveUserHexOrNull(who, nip05Client)
                    val rows = pubkey?.let { account.fetchTrustProviderRowsOf(it, hints) }
                    when {
                        pubkey == null -> WebOfTrustCopy.Failed(WebOfTrustCopy.Failed.Reason.UNKNOWN_USER)
                        rows == null -> WebOfTrustCopy.Failed(WebOfTrustCopy.Failed.Reason.NO_LIST)
                        rows.none { it.name == ProviderTypes.rank.toValue() } -> WebOfTrustCopy.Failed(WebOfTrustCopy.Failed.Reason.NO_RANK)
                        else -> WebOfTrustCopy.Copied(pubkey, rows)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    WebOfTrustCopy.Failed(WebOfTrustCopy.Failed.Reason.NO_LIST)
                }
        }
    }

    /** Copies the rows of [onboarding]'s own observer: the provider's default view. */
    fun copyHouseRows(onboarding: TrustProviderOnboarding) {
        val house = onboarding.houseObserver ?: return
        copyRowsFrom(house, setOf(onboarding.relay))
    }

    /** Publishes kind 10040 [rows] written by hand (they must include a `30382:rank` row). */
    fun useProviderRows(
        rows: List<TrustProviderRow>,
        onSaved: () -> Unit = {},
    ) {
        if (_setup.value is WebOfTrustSetup.Running || _setup.value is WebOfTrustSetup.Saving) return
        _setup.value = WebOfTrustSetup.Saving(MANUAL)
        viewModelScope.launch {
            _setup.value =
                try {
                    account.setTrustProviderRows(rows, _isPrivate.value)
                    _copy.value = WebOfTrustCopy.Idle
                    onSaved()
                    WebOfTrustSetup.Idle
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    WebOfTrustSetup.Failed(MANUAL, (e as? SignerExceptions)?.let { TrustProviderException.Reason.SIGNER_DECLINED }, e.message.orEmpty())
                }
        }
    }

    class Factory(
        private val account: Account,
        private val http: TrustProviderHttp,
        private val nip05Client: INip05Client?,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: KClass<T>,
            extras: CreationExtras,
        ): T = WebOfTrustViewModel(account, http, nip05Client) as T
    }

    companion object {
        /** [WebOfTrustSetup.Failed.providerId] for a provider entered by hand. */
        const val MANUAL = "manual"
    }
}
