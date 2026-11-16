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
package com.vitorpamplona.amethyst.commons.ui.actions.nestsServers

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.ui.actions.mediaServers.BlossomServersViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip53LiveActivities.nestsServers.NestsServer
import com.vitorpamplona.quartz.nip53LiveActivities.nestsServers.NestsServersEvent
import com.vitorpamplona.quartz.utils.Rfc3986
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Edit-buffer for the user's audio-room (NIP-53 / nests) MoQ server
 * list. Mirror of [BlossomServersViewModel] but every entry carries
 * **two** URLs (the moq-relay WebTransport endpoint and the moq-auth
 * sidecar base URL) — those are the two values that end up in the
 * kind-30312 `streaming` and `auth` tags. The deployed nostrnests
 * reference puts these on different hosts, so we cannot collapse them
 * into a single URL.
 *
 * The flow:
 *   1. [init] binds the [AccountViewModel].
 *   2. [load] (or [refresh]) snapshots the saved list into the local
 *      [_servers] buffer.
 *   3. [addServer] / [removeServer] / [removeAllServers] mutate the
 *      buffer, marking [isModified] = true.
 *   4. [save] publishes a fresh kind-10112 [NestsServersEvent] via
 *      [Account.sendNestsServersList].
 */
@Stable
class NestsServersViewModel : ViewModel() {
    private lateinit var accountViewModel: AccountViewModel
    private lateinit var account: Account

    private val _servers = MutableStateFlow<List<NestsServerEntry>>(emptyList())
    val servers = _servers.asStateFlow()
    private var isModified = false

    fun init(accountViewModel: AccountViewModel) {
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account
    }

    fun load() = refresh()

    fun refresh() {
        isModified = false
        val current = account.nestsServers.flow.value
        _servers.update {
            current.map { it.toEntry() }
        }
    }

    /**
     * Add a server pair. Both URLs go on the wire as the third element
     * of the kind-10112 `server` tag (relay first, auth second). The
     * recommended-list "Add" button supplies both; the manual "Add a
     * server" field also asks for both.
     */
    fun addServer(
        relay: String,
        auth: String,
    ) {
        val normalisedRelay = normalize(relay)
        val normalisedAuth = normalize(auth)
        if (normalisedRelay.isBlank() || normalisedAuth.isBlank()) return
        val entry =
            NestsServerEntry(
                name = displayHostName(normalisedRelay),
                relay = normalisedRelay,
                auth = normalisedAuth,
            )
        if (_servers.value.any { it.relay == entry.relay }) return
        _servers.update { it + entry }
        isModified = true
    }

    fun addServerList(servers: List<NestsServerEntry>) {
        servers.forEach { addServer(it.relay, it.auth) }
    }

    fun removeServer(relay: String) {
        val before = _servers.value
        val after = before.filterNot { it.relay == relay }
        if (after.size != before.size) {
            _servers.update { after }
            isModified = true
        }
    }

    fun removeAllServers() {
        if (_servers.value.isNotEmpty()) {
            _servers.update { emptyList() }
            isModified = true
        }
    }

    fun save() {
        if (!isModified) return
        accountViewModel.launchSigner {
            account.sendNestsServersList(_servers.value.map { NestsServer(relay = it.relay, auth = it.auth) })
            refresh()
        }
    }

    private fun normalize(url: String): String =
        try {
            Rfc3986.normalize(url.trim())
        } catch (_: Throwable) {
            url.trim()
        }

    private fun displayHostName(url: String): String =
        try {
            Rfc3986
                .host(url)
                .removePrefix("moq-auth.")
                .removePrefix("moq.")
                .removePrefix("nests.")
        } catch (_: Throwable) {
            url
        }

    private fun NestsServer.toEntry() =
        NestsServerEntry(
            name = displayHostName(relay),
            relay = relay,
            auth = auth,
        )
}

/**
 * Display-friendly entry for [NestsServersViewModel].
 *   - [name] — short host label (e.g. `nostrnests.com`)
 *   - [relay] — moq-relay WebTransport URL (`https://moq.nostrnests.com:4443`)
 *   - [auth] — moq-auth sidecar base URL (`https://moq-auth.nostrnests.com`)
 */
data class NestsServerEntry(
    val name: String,
    val relay: String,
    val auth: String,
)
