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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.nip65

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip65ListOf
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.taggedAs
import com.vitorpamplona.amethyst.commons.relays.ui.RelayCountResult
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.events_from_you
import com.vitorpamplona.amethyst.commons.resources.events_to_you
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common.BasicRelaySetupInfo
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common.RelayListAutoSaver
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common.relaySetupInfoBuilder
import com.vitorpamplona.amethyst.commons.util.replace
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.count
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Stable
class Nip65RelayListViewModel : ViewModel() {
    private lateinit var accountViewModel: AccountViewModel
    private lateinit var account: Account

    private val _homeRelays = MutableStateFlow<List<BasicRelaySetupInfo>>(emptyList())
    val homeRelays = _homeRelays.asStateFlow()

    private val _notificationRelays = MutableStateFlow<List<BasicRelaySetupInfo>>(emptyList())
    val notificationRelays = _notificationRelays.asStateFlow()

    private val _homeCountResults = MutableStateFlow<Map<NormalizedRelayUrl, RelayCountResult>>(emptyMap())
    val homeCountResults = _homeCountResults.asStateFlow()

    private val _notifCountResults = MutableStateFlow<Map<NormalizedRelayUrl, RelayCountResult>>(emptyMap())
    val notifCountResults = _notifCountResults.asStateFlow()

    var hasModified = false

    /** Signs and publishes every edit instead of waiting for [create]; see BasicRelaySetupInfoModel.autoSave. */
    var autoSave = false
        private set

    /** Snapshots are (outbox, inbox), the order the screen shows them in. */
    private val autoSaver = RelayListAutoSaver<Pair<List<NormalizedRelayUrl>, List<NormalizedRelayUrl>>>(viewModelScope)

    fun init(
        accountViewModel: AccountViewModel,
        autoSave: Boolean = false,
    ) {
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account
        this.autoSave = autoSave
    }

    fun load() {
        // Same rules as BasicRelaySetupInfoModel.load: keep the lists while an edit is pending,
        // and only reload when the account's lists moved away from the ones on screen.
        if (autoSave) {
            if (!autoSaver.isIdle) return
            val lists = account.nip65RelayList
            if (Pair(lists.ownWriteRelays().distinct(), lists.ownReadRelays().distinct()) == autoSaver.persisted) return
        }

        clear()
        autoSaver.persisted = snapshot()
        loadRelayDocuments()
        loadCounts()
    }

    fun create() {
        if (hasModified) {
            val lists = snapshot()
            accountViewModel.launchSigner {
                publish(lists)
                clear()
            }
        }
    }

    /** Publishes the pending auto-save edit now instead of after the debounce, e.g. on leaving. */
    fun flushAutoSave() {
        autoSaver.cancelPending()
        if (!autoSave || !hasModified) return
        hasModified = false

        autoSaver.save(
            snapshot = snapshot(),
            launch = { save -> accountViewModel.launchSigner { save() } },
            publish = ::publish,
        )
    }

    private fun snapshot() = Pair(_homeRelays.value.map { it.relay }, _notificationRelays.value.map { it.relay })

    private fun markModified() {
        hasModified = true
        if (autoSave) autoSaver.schedule(::flushAutoSave)
    }

    private suspend fun publish(lists: Pair<List<NormalizedRelayUrl>, List<NormalizedRelayUrl>>) {
        val (writes, reads) = lists
        account.sendNip65RelayList(nip65ListOf(writes.toSet(), reads.toSet()))
    }

    fun loadRelayDocuments() {
        viewModelScope.launch(Dispatchers.IO) {
            _homeRelays.value.forEach { item ->
                LocalCache.appHost.nip11Cache.loadRelayInfo(
                    relay = item.relay,
                    onInfo = {
                        toggleHomePaidRelay(item, it.limitation?.payment_required ?: false)
                    },
                    onError = { _, _, _ -> },
                )
            }

            _notificationRelays.value.forEach { item ->
                LocalCache.appHost.nip11Cache.loadRelayInfo(
                    relay = item.relay,
                    onInfo = {
                        toggleNotifPaidRelay(item, it.limitation?.payment_required ?: false)
                    },
                    onError = { _, _, _ -> },
                )
            }
        }
    }

    private fun loadCounts() {
        _homeCountResults.value = emptyMap()
        _notifCountResults.value = emptyMap()

        val client = accountViewModel.account.client

        _homeRelays.value.forEach { item ->
            viewModelScope.launch(Dispatchers.IO) {
                val result = client.taggedAs(SubPurpose.RELAY_INFO).count(item.relay, Filter(authors = listOf(account.pubKey)))
                if (result != null) {
                    val countResult =
                        RelayCountResult(
                            listOf(
                                RelayCountResult.CountEntry(
                                    label = Res.string.events_from_you,
                                    count = result.count,
                                    approximate = result.approximate,
                                ),
                            ),
                        )
                    _homeCountResults.update { it + (item.relay to countResult) }
                }
            }
        }

        _notificationRelays.value.forEach { item ->
            viewModelScope.launch(Dispatchers.IO) {
                val result = client.taggedAs(SubPurpose.RELAY_INFO).count(item.relay, Filter(tags = mapOf("p" to listOf(account.pubKey))))
                if (result != null) {
                    val countResult =
                        RelayCountResult(
                            listOf(
                                RelayCountResult.CountEntry(
                                    label = Res.string.events_to_you,
                                    count = result.count,
                                    approximate = result.approximate,
                                ),
                            ),
                        )
                    _notifCountResults.update { it + (item.relay to countResult) }
                }
            }
        }
    }

    fun clear() {
        hasModified = false
        // Marked like every other relay list, so the outbox shows when it waits for Tor.
        val torEvaluation = accountViewModel.host.torRelayEvaluation.value
        _homeRelays.update {
            // Every entry of the user's own list, local ones included, so saving round-trips it.
            val relayList = account.nip65RelayList.ownWriteRelays()

            relayList
                .map { relaySetupInfoBuilder(it, forcesTor = torEvaluation.useTor(it)) }
                .distinctBy { it.relay }
        }

        _notificationRelays.update {
            val relayList = account.nip65RelayList.ownReadRelays()

            relayList
                .map { relaySetupInfoBuilder(it, forcesTor = torEvaluation.useTor(it)) }
                .distinctBy { it.relay }
        }
    }

    fun addHomeRelay(relay: BasicRelaySetupInfo) {
        if (_homeRelays.value.any { it.relay == relay.relay }) return

        _homeRelays.update { it.plus(relay) }
        markModified()
    }

    fun deleteHomeRelay(relay: BasicRelaySetupInfo) {
        _homeRelays.update { it.minus(relay) }
        markModified()
    }

    fun deleteHomeAll() {
        _homeRelays.update { _ -> emptyList() }
        markModified()
    }

    fun toggleHomePaidRelay(
        relay: BasicRelaySetupInfo,
        paid: Boolean,
    ) {
        _homeRelays.update { it.replace(relay, relay.copy(paidRelay = paid)) }
    }

    fun moveHomeRelay(
        from: Int,
        to: Int,
    ) {
        _homeRelays.update { list ->
            list.toMutableList().apply {
                add(to, removeAt(from))
            }
        }
        markModified()
    }

    fun addNotifRelay(relay: BasicRelaySetupInfo) {
        if (_notificationRelays.value.any { it.relay == relay.relay }) return

        _notificationRelays.update { it.plus(relay) }
        markModified()
    }

    fun deleteNotifRelay(relay: BasicRelaySetupInfo) {
        _notificationRelays.update { it.minus(relay) }
        markModified()
    }

    fun deleteNotifAll() {
        _notificationRelays.update { _ -> emptyList() }
        markModified()
    }

    fun moveNotifRelay(
        from: Int,
        to: Int,
    ) {
        _notificationRelays.update { list ->
            list.toMutableList().apply {
                add(to, removeAt(from))
            }
        }
        markModified()
    }

    fun toggleNotifPaidRelay(
        relay: BasicRelaySetupInfo,
        paid: Boolean,
    ) {
        _notificationRelays.update { it.replace(relay, relay.copy(paidRelay = paid)) }
    }
}
