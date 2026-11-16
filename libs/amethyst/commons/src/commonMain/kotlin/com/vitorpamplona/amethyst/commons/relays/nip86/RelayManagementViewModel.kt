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
package com.vitorpamplona.amethyst.commons.relays.nip86

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.relayManagement.Nip86Executor
import com.vitorpamplona.amethyst.commons.wot.sortedByFollowsThenTrust
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip86RelayManagement.Nip86Client
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.AllowedEvent
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.AllowedPubkey
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.BannedEvent
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.BannedPubkey
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.BlockedIp
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.EventNeedingModeration
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.Nip86Method
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.Nip86Request
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class PubkeyUser(
    val user: User,
    val reason: String?,
)

@Stable
class RelayManagementViewModel(
    relayUrl: NormalizedRelayUrl,
    account: Account,
    private val retriever: Nip86Executor,
) : ViewModel() {
    val client = Nip86Client(relayUrl, account.signer)

    private val _supportedMethods = MutableStateFlow<ImmutableList<String>>(persistentListOf())
    val supportedMethods: StateFlow<ImmutableList<String>> = _supportedMethods

    private val _bannedPubkeys = MutableStateFlow<List<BannedPubkey>>(emptyList())
    val bannedPubkeys: StateFlow<List<BannedPubkey>> = _bannedPubkeys

    private val _allowedPubkeys = MutableStateFlow<List<AllowedPubkey>>(emptyList())
    val allowedPubkeys: StateFlow<List<AllowedPubkey>> = _allowedPubkeys

    private val _bannedEvents = MutableStateFlow<List<BannedEvent>>(emptyList())
    val bannedEvents: StateFlow<List<BannedEvent>> = _bannedEvents

    private val _allowedEvents = MutableStateFlow<List<AllowedEvent>>(emptyList())
    val allowedEvents: StateFlow<List<AllowedEvent>> = _allowedEvents

    private val _eventsNeedingModeration = MutableStateFlow<List<EventNeedingModeration>>(emptyList())
    val eventsNeedingModeration: StateFlow<List<EventNeedingModeration>> = _eventsNeedingModeration

    private val _allowedKinds = MutableStateFlow<List<Int>>(emptyList())
    val allowedKinds: StateFlow<List<Int>> = _allowedKinds

    private val _disallowedKinds = MutableStateFlow<List<Int>>(emptyList())
    val disallowedKinds: StateFlow<List<Int>> = _disallowedKinds

    private val _blockedIps = MutableStateFlow<List<BlockedIp>>(emptyList())
    val blockedIps: StateFlow<List<BlockedIp>> = _blockedIps

    val bannedPubkeyUsers: Flow<List<PubkeyUser>> =
        _bannedPubkeys.map { list ->
            list
                .mapNotNull { entry ->
                    LocalCache.checkGetOrCreateUser(entry.pubkey)?.let { PubkeyUser(it, entry.reason) }
                }.sortedByFollowsThenTrust(account, { it.user.pubkeyHex }, follows = account.allFollows.flow.value.authors)
        }

    val allowedPubkeyUsers: Flow<List<PubkeyUser>> =
        _allowedPubkeys.map { list ->
            list
                .mapNotNull { entry ->
                    LocalCache.checkGetOrCreateUser(entry.pubkey)?.let { PubkeyUser(it, entry.reason) }
                }.sortedByFollowsThenTrust(account, { it.user.pubkeyHex }, follows = account.allFollows.flow.value.authors)
        }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun loadSupportedMethods() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val response = retriever.execute(client, Nip86Request.supportedMethods())
            if (response.error != null) {
                _error.value = response.error
            } else {
                _supportedMethods.value = client.parseSupportedMethods(response)?.toImmutableList() ?: persistentListOf()
            }
            _isLoading.value = false
        }
    }

    fun loadBannedPubkeys() {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.listBannedPubkeys())
            if (response.error != null) {
                _error.value = response.error
            } else {
                _bannedPubkeys.value = client.parseBannedPubkeys(response)?.distinctBy { it.pubkey } ?: emptyList()
            }
        }
    }

    fun loadAllowedPubkeys() {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.listAllowedPubkeys())
            if (response.error != null) {
                _error.value = response.error
            } else {
                _allowedPubkeys.value = client.parseAllowedPubkeys(response)?.distinctBy { it.pubkey } ?: emptyList()
            }
        }
    }

    fun loadBannedEvents() {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.listBannedEvents())
            if (response.error != null) {
                _error.value = response.error
            } else {
                _bannedEvents.value = client.parseBannedEvents(response)?.distinctBy { it.id } ?: emptyList()
            }
        }
    }

    fun loadAllowedEvents() {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.listAllowedEvents())
            if (response.error != null) {
                _error.value = response.error
            } else {
                _allowedEvents.value = client.parseAllowedEvents(response)?.distinctBy { it.id } ?: emptyList()
            }
        }
    }

    fun loadEventsNeedingModeration() {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.listEventsNeedingModeration())
            if (response.error != null) {
                _error.value = response.error
            } else {
                _eventsNeedingModeration.value = client.parseEventsNeedingModeration(response)?.distinctBy { it.id } ?: emptyList()
            }
        }
    }

    fun loadAllowedKinds() {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.listAllowedKinds())
            if (response.error != null) {
                _error.value = response.error
            } else {
                _allowedKinds.value = client.parseAllowedKinds(response)?.distinctBy { it } ?: emptyList()
            }
        }
    }

    fun loadDisallowedKinds() {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.listDisallowedKinds())
            if (response.error != null) {
                _error.value = response.error
            } else {
                _disallowedKinds.value = client.parseDisallowedKinds(response)?.distinctBy { it } ?: emptyList()
            }
        }
    }

    fun loadBlockedIps() {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.listBlockedIps())
            if (response.error != null) {
                _error.value = response.error
            } else {
                _blockedIps.value = client.parseBlockedIps(response)?.distinctBy { it.ip } ?: emptyList()
            }
        }
    }

    fun banPubkey(
        pubkey: String,
        reason: String? = null,
    ) {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.banPubkey(pubkey, reason))
            if (response.error != null) {
                _error.value = response.error
            } else {
                // NIP-86: banning also drops the pubkey from the allow list.
                loadBannedPubkeys()
                loadIfSupported(Nip86Method.LIST_ALLOWED_PUBKEYS) { loadAllowedPubkeys() }
            }
        }
    }

    fun unbanPubkey(pubkey: String) {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.unbanPubkey(pubkey))
            if (response.error != null) {
                _error.value = response.error
            } else {
                loadBannedPubkeys()
            }
        }
    }

    fun allowPubkey(
        pubkey: String,
        reason: String? = null,
    ) {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.allowPubkey(pubkey, reason))
            if (response.error != null) {
                _error.value = response.error
            } else {
                // NIP-86: allowing also lifts any ban on the pubkey.
                loadAllowedPubkeys()
                loadIfSupported(Nip86Method.LIST_BANNED_PUBKEYS) { loadBannedPubkeys() }
            }
        }
    }

    fun unallowPubkey(pubkey: String) {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.unallowPubkey(pubkey))
            if (response.error != null) {
                _error.value = response.error
            } else {
                loadAllowedPubkeys()
            }
        }
    }

    fun banEvent(
        eventId: String,
        reason: String? = null,
    ) {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.banEvent(eventId, reason))
            if (response.error != null) {
                _error.value = response.error
            } else {
                reloadEventLists()
            }
        }
    }

    fun unbanEvent(eventId: String) {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.unbanEvent(eventId))
            if (response.error != null) {
                _error.value = response.error
            } else {
                loadBannedEvents()
            }
        }
    }

    /**
     * Approves an event: NIP-86 `allowevent` puts it on the relay's event
     * allow list and lifts any ban on it (it no longer means "unban").
     */
    fun allowEvent(
        eventId: String,
        reason: String? = null,
    ) {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.allowEvent(eventId, reason))
            if (response.error != null) {
                _error.value = response.error
            } else {
                reloadEventLists()
            }
        }
    }

    fun unallowEvent(eventId: String) {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.unallowEvent(eventId))
            if (response.error != null) {
                _error.value = response.error
            } else {
                loadAllowedEvents()
            }
        }
    }

    private fun reloadEventLists() {
        loadIfSupported(Nip86Method.LIST_EVENTS_NEEDING_MODERATION) { loadEventsNeedingModeration() }
        loadIfSupported(Nip86Method.LIST_BANNED_EVENTS) { loadBannedEvents() }
        loadIfSupported(Nip86Method.LIST_ALLOWED_EVENTS) { loadAllowedEvents() }
    }

    private inline fun loadIfSupported(
        method: String,
        load: () -> Unit,
    ) {
        if (_supportedMethods.value.contains(method)) load()
    }

    fun changeRelayName(newName: String) {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.changeRelayName(newName))
            if (response.error != null) {
                _error.value = response.error
            }
        }
    }

    fun changeRelayDescription(newDescription: String) {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.changeRelayDescription(newDescription))
            if (response.error != null) {
                _error.value = response.error
            }
        }
    }

    fun changeRelayIcon(newIconUrl: String) {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.changeRelayIcon(newIconUrl))
            if (response.error != null) {
                _error.value = response.error
            }
        }
    }

    fun allowKind(kind: Int) {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.allowKind(kind))
            if (response.error != null) {
                _error.value = response.error
            } else {
                loadAllowedKinds()
                loadIfSupported(Nip86Method.LIST_DISALLOWED_KINDS) { loadDisallowedKinds() }
            }
        }
    }

    fun disallowKind(kind: Int) {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.disallowKind(kind))
            if (response.error != null) {
                _error.value = response.error
            } else {
                loadAllowedKinds()
                loadIfSupported(Nip86Method.LIST_DISALLOWED_KINDS) { loadDisallowedKinds() }
            }
        }
    }

    fun blockIp(
        ip: String,
        reason: String? = null,
    ) {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.blockIp(ip, reason))
            if (response.error != null) {
                _error.value = response.error
            } else {
                loadBlockedIps()
            }
        }
    }

    fun unblockIp(ip: String) {
        viewModelScope.launch {
            val response = retriever.execute(client, Nip86Request.unblockIp(ip))
            if (response.error != null) {
                _error.value = response.error
            } else {
                loadBlockedIps()
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun loadAllLists() {
        loadIfSupported(Nip86Method.LIST_BANNED_PUBKEYS) { loadBannedPubkeys() }
        loadIfSupported(Nip86Method.LIST_ALLOWED_PUBKEYS) { loadAllowedPubkeys() }
        loadIfSupported(Nip86Method.LIST_BANNED_EVENTS) { loadBannedEvents() }
        loadIfSupported(Nip86Method.LIST_ALLOWED_EVENTS) { loadAllowedEvents() }
        loadIfSupported(Nip86Method.LIST_EVENTS_NEEDING_MODERATION) { loadEventsNeedingModeration() }
        loadIfSupported(Nip86Method.LIST_ALLOWED_KINDS) { loadAllowedKinds() }
        loadIfSupported(Nip86Method.LIST_DISALLOWED_KINDS) { loadDisallowedKinds() }
        loadIfSupported(Nip86Method.LIST_BLOCKED_IPS) { loadBlockedIps() }
    }
}
