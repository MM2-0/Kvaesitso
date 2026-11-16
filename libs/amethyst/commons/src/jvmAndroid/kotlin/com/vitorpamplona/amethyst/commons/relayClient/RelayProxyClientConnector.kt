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
package com.vitorpamplona.amethyst.commons.relayClient

import com.vitorpamplona.amethyst.commons.service.connectivity.ConnectivityStatus
import com.vitorpamplona.amethyst.commons.service.resourceusage.UsageKeys
import com.vitorpamplona.amethyst.commons.tor.RelayClassification
import com.vitorpamplona.amethyst.commons.tor.TorRelayEvaluation
import com.vitorpamplona.amethyst.commons.tor.TorRelaySettings
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import okhttp3.OkHttpClient

class RelayProxyClientConnector(
    val torEvaluator: StateFlow<TorRelayEvaluation>,
    val torConnection: StateFlow<OkHttpClient>,
    val clearConnection: StateFlow<OkHttpClient>,
    val connectivityStatus: StateFlow<ConnectivityStatus>,
    /**
     * Whether Tor can build circuits right now. A change re-runs [apply], which reconnects the
     * Tor-routed relays the websocket builder held back while Tor was still bootstrapping.
     */
    val torBootstrapped: Flow<Boolean>,
    val client: INostrClient,
    val scope: CoroutineScope,
    /**
     * Called with the cause every time this connector *decides* to reconnect, so the
     * usage ledger can attribute relay churn without this class knowing where the
     * counters go. Causes are the `UsageKeys.TRIGGER_*` constants — see
     * [UsageKeys.relayTrigger] for why these are an upper bound rather than a count
     * of reconnects actually performed.
     */
    val onTrigger: (String) -> Unit = {},
) {
    data class RelayServiceInfra(
        val evaluator: TorRelayEvaluation,
        val torConnection: OkHttpClient,
        val clearConnection: OkHttpClient,
        val connectivity: ConnectivityStatus,
        val torBootstrapped: Boolean,
    )

    // The OkHttp clients in use the last time we forced a reconnect. These are only
    // rebuilt when something connection-relevant changes (Tor's SOCKS port appears,
    // wifi<->cellular switch), so comparing them tells us whether a wakeup is a real
    // transport change or just noise (Tor bootstrap status churn, connectivity blips).
    private var lastTorConnection: OkHttpClient? = null
    private var lastClearConnection: OkHttpClient? = null

    // The network we were last on. The OkHttp clients above are rebuilt off the metered
    // bit, so they only tell us about wifi<->cellular; they say nothing about wifi A ->
    // wifi B, a VPN coming up, or a captive portal clearing. Those all mint a new
    // networkHandle, and after them every existing socket is bound to an interface that
    // is gone and every accumulated backoff was earned against a network we have left.
    private var lastNetworkId: Long? = null

    // The user's Tor preferences. TorRelayEvaluation has no equals() and a fresh instance is
    // emitted on unrelated churn, so we track the settings by value. Without this, flipping a
    // Tor toggle while Tor is already up leaves both OkHttpClient references identical ->
    // transportChanged=false -> a relay parked on a 5-minute backoff keeps waiting it out on
    // a transport it is no longer using.
    //
    // Deliberately only [TorRelaySettings], not the trusted/DM/money relay sets that
    // TorRelayEvaluation also carries: those churn constantly while an account's relay lists
    // load from the network (observed firing three times during a single cold start). A relay
    // moving between classifications can flip its transport too, but forgiving the whole
    // pool's backoff every time any relay list updates is far more damage than making that
    // one relay serve out its delay.
    private var lastTorSettings: TorRelaySettings? = null

    // The relay-classification sets (trusted/DM/money) from the last apply(). A relay moving into or
    // out of one of these flips its transport (e.g. marking a relay Trusted moves it off Tor onto
    // clearnet) WITHOUT changing TorRelaySettings, so `torPolicyChanged` above misses it and the
    // flipped relay would sit out its (now-irrelevant) backoff. We track these so such a relay can
    // skip its retry delay on the next reconnect — scoped to onlyIfChanged, so only the relays that
    // actually flipped re-dial and the rest of the pool's backoff is left untouched.
    private var lastClassification: RelayClassification? = null

    @OptIn(FlowPreview::class)
    val relayServices =
        combine(
            torEvaluator,
            torConnection,
            clearConnection,
            connectivityStatus,
            torBootstrapped,
        ) { torSettings, torConnection, clearConnection, connectivity, torBootstrapped ->
            RelayServiceInfra(torSettings, torConnection, clearConnection, connectivity, torBootstrapped)
        }.debounce(100)
            .onEach { apply(it) }
            .onStart {
                Log.d("ManageRelayServices", "Resuming Relay Services")
                client.connect()
            }.onCompletion {
                Log.d("ManageRelayServices", "Pausing Relay Services")
                client.disconnect()
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.WhileSubscribed(30000),
                null,
            )

    /**
     * Decides what a change in the relay infrastructure means for the pool. Split out of the
     * flow so the decision table can be exercised directly, without a debounce and a shared
     * StateFlow in the way.
     */
    fun apply(infra: RelayServiceInfra) {
        val networkId = (infra.connectivity as? ConnectivityStatus.Active)?.networkId
        val torSettings = infra.evaluator.torSettings

        when {
            infra.connectivity is ConnectivityStatus.StartingService -> {
                // ignore
            }

            infra.connectivity is ConnectivityStatus.Off -> {
                Log.d("ManageRelayServices") { "Connectivity Off: Pausing Relay Services ${infra.connectivity}" }
                if (client.isActive()) {
                    // Counted inside the guard: the upstream combine() re-emits Off
                    // repeatedly and only this branch does any work.
                    onTrigger(UsageKeys.TRIGGER_OFF)
                    client.disconnect()
                }
                if (infra.torBootstrapped) {
                    Log.d("ManageRelayServices", "Connectivity off, Tor idle")
                }
                // disconnect() already cleared every relay's backoff. Forget the network
                // so the next Active is treated as a fresh start rather than a change.
                lastNetworkId = null
            }

            infra.connectivity is ConnectivityStatus.Active && !client.isActive() -> {
                Log.d("ManageRelayServices", "Connectivity On: Resuming Relay Services")

                if (infra.torBootstrapped) {
                    Log.d("ManageRelayServices", "Connectivity resumed, Tor active")
                }

                // only calls this if the client is not active. Otherwise goes to the else below
                onTrigger(UsageKeys.TRIGGER_COLD_START)
                client.connect()
                lastNetworkId = networkId
                lastTorSettings = torSettings
                lastTorConnection = infra.torConnection
                lastClearConnection = infra.clearConnection
                lastClassification = infra.evaluator.classification
            }

            else -> {
                // Only skip the per-relay exponential backoff when the actual HTTP
                // transport changed. Otherwise (e.g. Tor still bootstrapping, the SOCKS
                // port not yet listening) honor each relay's backoff so we don't
                // reconnect-fail-reconnect on every unrelated infrastructure event.
                val transportChanged =
                    infra.torConnection !== lastTorConnection || infra.clearConnection !== lastClearConnection

                // A different network entirely. Every socket is bound to an interface that
                // no longer carries traffic, and needsToReconnect() cannot see that (it only
                // compares the proxy and the timeouts), so those sockets would otherwise sit
                // there until OkHttp's 120s ping finally fails.
                val networkChanged = networkId != null && lastNetworkId != null && networkId != lastNetworkId

                // Same network and same OkHttp clients, but the user re-classified which
                // relays go through Tor. The relays whose transport flipped must re-dial now.
                val torPolicyChanged = lastTorSettings != null && torSettings != lastTorSettings

                // A relay moved into/out of the trusted/DM/money sets (e.g. marked Trusted to move it
                // off Tor onto clearnet). That flips only that relay's transport, not TorRelaySettings,
                // so let onlyIfChanged pick out the flipped relay(s) and skip THEIR retry delay —
                // without resetBackoff(), so the rest of the pool's backoff is untouched (these sets
                // churn while relay lists load, and forgiving the whole pool then would be too much).
                //
                // One comparison over the whole classification, not one per category: this used to
                // be a four-way `||` and adding a category meant remembering to extend it. Missing
                // a term fails silently — the affected relays keep a socket on a transport the
                // policy has already moved them off.
                val classificationChanged =
                    lastClassification != null && infra.evaluator.classification != lastClassification

                val previousNetworkId = lastNetworkId

                lastTorConnection = infra.torConnection
                lastClearConnection = infra.clearConnection
                lastNetworkId = networkId ?: lastNetworkId
                lastTorSettings = torSettings
                lastClassification = infra.evaluator.classification

                if (networkChanged) {
                    Log.d("ManageRelayServices") {
                        "Network identity changed ($previousNetworkId -> $networkId), rebuilding every relay connection"
                    }
                    // The expensive branch, and the one the churn investigation is
                    // aimed at: a full teardown re-dials the whole pool and replays
                    // every REQ.
                    //
                    // Only this cause is counted here, so a wifi<->cellular handoff —
                    // which mints a new network handle AND rebuilds the OkHttp clients
                    // off the metered bit — is booked as netid alone. relay.trigger.transport
                    // therefore undercounts exactly the case one would most want it for;
                    // read it as "transport changed WITHOUT the handle changing".
                    onTrigger(UsageKeys.TRIGGER_NETID)
                    // Full teardown: disconnect() drops the dead sockets AND clears each
                    // relay's backoff, so the new network starts from a clean slate.
                    client.reconnect(onlyIfChanged = false, ignoreRetryDelays = true)
                } else {
                    // Non-exclusive: count each independently so the report shows
                    // which combination fired.
                    if (transportChanged) onTrigger(UsageKeys.TRIGGER_TRANSPORT)
                    if (torPolicyChanged) onTrigger(UsageKeys.TRIGGER_TOR_POLICY)
                    if (classificationChanged) onTrigger(UsageKeys.TRIGGER_CLASSIFICATION)

                    val freshStart = transportChanged || torPolicyChanged
                    if (freshStart) {
                        // The failures behind the current backoffs were measured against a
                        // transport we are no longer using. ignoreRetryDelays alone only
                        // skips the gate once and still doubles the stored delay, so a relay
                        // that fails this one dial would come back worse off than before.
                        client.resetBackoff()
                    }

                    Log.d("ManageRelayServices") {
                        "Relay Services have changed, reconnecting relays that need to " +
                            "(transportChanged=$transportChanged torPolicyChanged=$torPolicyChanged " +
                            "classificationChanged=$classificationChanged)"
                    }
                    client.reconnect(
                        onlyIfChanged = true,
                        ignoreRetryDelays = freshStart || classificationChanged,
                    )
                }
            }
        }
    }
}
