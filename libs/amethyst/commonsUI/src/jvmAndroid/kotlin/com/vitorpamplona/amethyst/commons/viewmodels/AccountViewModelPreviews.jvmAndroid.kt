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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.InMemoryGeohashIdentityStore
import com.vitorpamplona.amethyst.commons.model.UiSettingsFlow
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.location.LocationResult
import com.vitorpamplona.amethyst.commons.model.marmot.MarmotGroupNotifier
import com.vitorpamplona.amethyst.commons.model.nip46Signer.Nip46ConsentPrompter
import com.vitorpamplona.amethyst.commons.relayClient.assemblers.CashuMintDirectoryFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.nip47WalletConnect.NWCPaymentFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.RelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.commons.service.http.EmptyRoleBasedHttpClientBuilder
import com.vitorpamplona.amethyst.commons.service.http.EncryptionKeyCache
import com.vitorpamplona.amethyst.commons.service.lnurl.LnurlHttpTransport
import com.vitorpamplona.amethyst.commons.service.pow.PoWJobFailure
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.UnavailableMediaUploader
import com.vitorpamplona.amethyst.commons.state.UiSettingsState
import com.vitorpamplona.amethyst.commons.tor.MoneyOpRelayRouting
import com.vitorpamplona.amethyst.commons.tor.TorRelayEvaluation
import com.vitorpamplona.amethyst.commons.tor.TorRelaySettings
import com.vitorpamplona.amethyst.commons.tor.TorSettingsFlow
import com.vitorpamplona.amethyst.commons.tor.TorType
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.RelayOfflineTracker
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.EmptyIAuthStatus
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.RelayAuthSnapshot
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.RelayStats
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip03Timestamp.EmptyOtsResolverBuilder
import com.vitorpamplona.quartz.nip05DnsIdentifiers.EmptyNip05Client
import com.vitorpamplona.quartz.nip60Cashu.mintApi.OkHttpMintTransport
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import okhttp3.OkHttpClient

private var mockedCache: AccountViewModel? = null

@Suppress("ViewModelConstructorInComposable")
@Composable
actual fun mockAccountViewModel(): AccountViewModel {
    mockedCache?.let { return it }

    val scope = rememberCoroutineScope()

    val uiState =
        UiSettingsState(
            uiSettingsFlow = UiSettingsFlow(),
            isMobileOrMeteredConnection = MutableStateFlow(false),
            scope = scope,
        )

    val keyPair =
        KeyPair(
            privKey = Hex.decode("0f761f8a5a481e26f06605a1d9b3e9eba7a107d351f43c43a57469b788274499"),
            pubKey = Hex.decode("989c3734c46abac7ce3ce229971581a5a6ee39cdd6aa7261a55823fa7f8c4799"),
            forceReplacePubkey = false,
        )

    val client = EmptyNostrClient()
    val authenticator = EmptyIAuthStatus

    val nwcFilters = NWCPaymentFilterAssembler(client)
    val failureTracker = RelayOfflineTracker(client)

    val account =
        Account(
            settings = AccountSettings(keyPair),
            signer = NostrSignerInternal(keyPair),
            geolocationFlow = { MutableStateFlow<LocationResult>(LocationResult.Loading) },
            nwcFilterAssembler = { nwcFilters },
            cashuMintDirectoryFilterAssembler = { CashuMintDirectoryFilterAssembler(client) },
            cashuMintTransport = OkHttpMintTransport { OkHttpClient() },
            otsResolverBuilder = { EmptyOtsResolverBuilder.build() },
            cache = LocalCache,
            client = client,
            scope = scope,
            appVersion = "preview",
            encryptionKeyCache = EncryptionKeyCache(),
            saveSettings = {},
            marmotNotifier = { MarmotGroupNotifier.None },
            nip46Consent = Nip46ConsentPrompter.Unanswered,
            geohashIdentityStore = InMemoryGeohashIdentityStore(),
            marmotStreamTransportFactory = { error("Previews do not stream") },
        )

    return AccountViewModel(
        account = account,
        settings = uiState,
        torSettings = TorSettingsFlow(torType = MutableStateFlow(TorType.OFF)),
        httpClientBuilder = EmptyRoleBasedHttpClientBuilder(),
        dataSources = RelaySubscriptionsCoordinator(LocalCache, client, authenticator, failureTracker, scope),
        nip05ClientBuilder = { EmptyNip05Client() },
        host = PreviewAccountViewModelHost,
    ).also {
        mockedCache = it
    }
}

private var vitorCache: AccountViewModel? = null

@Suppress("ViewModelConstructorInComposable")
@Composable
actual fun mockVitorAccountViewModel(): AccountViewModel {
    mockedCache?.let { return it }

    val scope = rememberCoroutineScope()

    val uiState =
        UiSettingsState(
            uiSettingsFlow = UiSettingsFlow(),
            isMobileOrMeteredConnection = MutableStateFlow(false),
            scope = scope,
        )

    val keyPair =
        KeyPair(
            pubKey = Hex.decode("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"),
        )

    val client = EmptyNostrClient()
    val authenticator = EmptyIAuthStatus

    val nwcFilters = NWCPaymentFilterAssembler(client)
    val failureTracker = RelayOfflineTracker(client)

    val account =
        Account(
            settings = AccountSettings(keyPair),
            signer = NostrSignerInternal(keyPair),
            geolocationFlow = { MutableStateFlow<LocationResult>(LocationResult.Loading) },
            nwcFilterAssembler = { nwcFilters },
            cashuMintDirectoryFilterAssembler = { CashuMintDirectoryFilterAssembler(client) },
            cashuMintTransport = OkHttpMintTransport { OkHttpClient() },
            otsResolverBuilder = { EmptyOtsResolverBuilder.build() },
            cache = LocalCache,
            client = EmptyNostrClient(),
            scope = scope,
            appVersion = "preview",
            encryptionKeyCache = EncryptionKeyCache(),
            saveSettings = {},
            marmotNotifier = { MarmotGroupNotifier.None },
            nip46Consent = Nip46ConsentPrompter.Unanswered,
            geohashIdentityStore = InMemoryGeohashIdentityStore(),
            marmotStreamTransportFactory = { error("Previews do not stream") },
        )

    return AccountViewModel(
        account = account,
        settings = uiState,
        torSettings = TorSettingsFlow(torType = MutableStateFlow(TorType.OFF)),
        httpClientBuilder = EmptyRoleBasedHttpClientBuilder(),
        dataSources = RelaySubscriptionsCoordinator(LocalCache, client, authenticator, failureTracker, scope),
        nip05ClientBuilder = { EmptyNip05Client() },
        host = PreviewAccountViewModelHost,
    ).also {
        vitorCache = it
    }
}

/** Nothing to probe, dismiss or pay from inside a preview. */
private object PreviewAccountViewModelHost : AccountViewModelHost {
    override val memoryPressure: Flow<Unit> = emptyFlow()
    override val localBlossomCacheAvailable: Flow<Boolean> = flowOf(false)
    override val powPublishFailures: Flow<PoWJobFailure> = emptyFlow()
    override val relayStats = RelayStats(EmptyNostrClient())
    override val torRelayEvaluation: StateFlow<TorRelayEvaluation> = MutableStateFlow(TorRelayEvaluation(TorRelaySettings()))
    override val relayAuthState: StateFlow<PersistentMap<NormalizedRelayUrl, RelayAuthSnapshot>> = MutableStateFlow(persistentMapOf())
    override val mediaUploader: MediaUploader = UnavailableMediaUploader
    override val websocketBuilder = BasicOkHttpWebSocket.Builder { OkHttpClient() }
    override val lnurlTransport = LnurlHttpTransport { throw IllegalStateException("Previews do not reach the network") }
    override val moneyOpRelays = MoneyOpRelayRouting.None
    override val savedAccounts: Flow<Set<HexKey>> = flowOf(emptySet())

    override fun dismissNotificationFor(eventId: HexKey) = Unit

    override fun openLightningWallet(invoice: String) = false
}
