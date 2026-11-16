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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.taggedAs
import com.vitorpamplona.amethyst.commons.relays.ui.CountFilter
import com.vitorpamplona.amethyst.commons.relays.ui.RelayCountResult
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common.BasicRelaySetupInfo
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common.relaySetupInfoBuilder
import com.vitorpamplona.amethyst.commons.util.replace
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.count
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

abstract class BasicRelaySetupInfoModel : ViewModel() {
    lateinit var accountViewModel: AccountViewModel
    lateinit var account: Account

    private val _relays = MutableStateFlow<List<BasicRelaySetupInfo>>(emptyList())
    val relays = _relays.asStateFlow()

    private val _countResults = MutableStateFlow<Map<NormalizedRelayUrl, RelayCountResult>>(emptyMap())
    val countResults = _countResults.asStateFlow()

    var hasModified = false

    /**
     * When on, every edit is signed and published (debounced by [RelayListAutoSaver]) instead of
     * waiting for [create]. The all-relays settings screen turns it on; the add-relay dialogs keep
     * their Save button.
     */
    var autoSave = false
        private set

    private val autoSaver = RelayListAutoSaver<List<NormalizedRelayUrl>>(viewModelScope)

    fun init(
        accountViewModel: AccountViewModel,
        autoSave: Boolean = false,
    ) {
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account
        this.autoSave = autoSave
    }

    fun load() {
        if (autoSave) {
            // An edit is waiting or being signed: rebuilding the list from the account now would
            // put back the list that save is about to replace.
            if (!autoSaver.isIdle) return
            // Back from a sub-screen and the account's list is still the one on screen: keep it,
            // with its paid flags and counts, instead of querying every relay again. A list that
            // changed meanwhile (a relay feed followed, a relay blocked) reloads, so the next edit
            // here doesn't publish over it.
            if (relayListBuilder().map { it.relay } == autoSaver.persisted) return
        }

        clear()
        autoSaver.persisted = _relays.value.map { it.relay }
        loadRelayDocuments()
        loadCounts()
    }

    abstract fun getRelayList(): List<NormalizedRelayUrl>?

    abstract suspend fun saveRelayList(urlList: List<NormalizedRelayUrl>)

    open fun countFilters(relayUrl: NormalizedRelayUrl): List<CountFilter> = emptyList()

    fun create() {
        if (hasModified) {
            accountViewModel.launchSigner {
                saveRelayList(_relays.value.map { it.relay })
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
            snapshot = _relays.value.map { it.relay },
            launch = { save -> accountViewModel.launchSigner { save() } },
            publish = { saveRelayList(it) },
        )
    }

    private fun markModified() {
        hasModified = true
        if (autoSave) autoSaver.schedule(::flushAutoSave)
    }

    fun loadRelayDocuments() {
        viewModelScope.launch(Dispatchers.IO) {
            _relays.value.forEach { item ->
                LocalCache.appHost.nip11Cache.loadRelayInfo(
                    relay = item.relay,
                    onInfo = {
                        togglePaidRelay(item, it.limitation?.payment_required ?: false)
                    },
                    onError = { _, _, _ -> },
                )
            }
        }
    }

    private fun loadCounts() {
        _countResults.value = emptyMap()

        val client = account.client
        val relayList = _relays.value
        if (relayList.isEmpty()) return

        relayList.forEach { item ->
            val filters = countFilters(item.relay)
            if (filters.isEmpty()) return@forEach

            filters.forEach { countFilter ->
                viewModelScope.launch(Dispatchers.IO) {
                    val result = client.taggedAs(SubPurpose.RELAY_INFO).count(item.relay, countFilter.filter)
                    if (result != null) {
                        _countResults.update { currentMap ->
                            val current = currentMap[item.relay] ?: RelayCountResult()
                            val entries = current.counts.toMutableList()
                            val newEntry =
                                RelayCountResult.CountEntry(
                                    label = countFilter.label,
                                    count = result.count,
                                    approximate = result.approximate,
                                )
                            val existing = entries.indexOfFirst { it.label == countFilter.label }
                            if (existing >= 0) entries[existing] = newEntry else entries.add(newEntry)
                            currentMap + (item.relay to RelayCountResult(entries))
                        }
                    }
                }
            }
        }
    }

    open fun relayListBuilder(): List<BasicRelaySetupInfo> {
        val relayList = getRelayList() ?: emptyList()

        return relayList
            .map {
                relaySetupInfoBuilder(
                    normalized = it,
                    forcesTor =
                        accountViewModel.host.torRelayEvaluation.value
                            .useTor(it),
                )
            }.distinctBy { it.relay }
    }

    fun clear() {
        _relays.update { relayListBuilder() }
    }

    fun addRelay(relay: BasicRelaySetupInfo) {
        if (relays.value.any { it.relay == relay.relay }) return

        _relays.update { it.plus(relay) }
        markModified()
    }

    fun deleteRelay(relay: BasicRelaySetupInfo) {
        _relays.update { it.minus(relay) }
        markModified()
    }

    fun moveRelay(
        from: Int,
        to: Int,
    ) {
        _relays.update { list ->
            list.toMutableList().apply {
                add(to, removeAt(from))
            }
        }
        markModified()
    }

    fun deleteAll() {
        _relays.update { _ -> emptyList() }
        markModified()
    }

    fun togglePaidRelay(
        relay: BasicRelaySetupInfo,
        paid: Boolean,
    ) {
        _relays.update { it.replace(relay, relay.copy(paidRelay = paid)) }
    }
}
