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
package com.vitorpamplona.amethyst.commons.ui.actions.mediaServers

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.mediaServers.ServerHealth
import com.vitorpamplona.amethyst.commons.mediaServers.cachedMediaServerHealth
import com.vitorpamplona.amethyst.commons.mediaServers.probeMediaServer
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerType
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.Rfc3986
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Stable
class BlossomServersViewModel : ViewModel() {
    private lateinit var accountViewModel: AccountViewModel
    private lateinit var account: Account
    private var httpClientBuilder: IRoleBasedHttpClientBuilder? = null

    private val _fileServers = MutableStateFlow<List<ServerName>>(emptyList())
    val fileServers = _fileServers.asStateFlow()

    /** Reachability status per server, keyed by [ServerName.baseUrl]. */
    private val _health = MutableStateFlow<Map<String, ServerHealth>>(emptyMap())
    val health = _health.asStateFlow()

    private var isModified = false

    fun init(accountViewModel: AccountViewModel) {
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account
        this.httpClientBuilder = accountViewModel.httpClientBuilder
    }

    fun load() {
        refresh()
        checkAllHealth()
    }

    fun refresh() {
        isModified = false
        _fileServers.update { toServerNames(obtainFileServers()) }
        pruneHealth()
    }

    /** Moves a server to a new position; list order is the upload/fallback priority. */
    fun moveServer(
        from: Int,
        to: Int,
    ) {
        _fileServers.update { list ->
            if (from !in list.indices || to !in list.indices) return@update list
            list.toMutableList().apply { add(to, removeAt(from)) }
        }
        isModified = true
    }

    /** Re-probes every server currently in the list. Fresh cached results are reused. */
    fun checkAllHealth() {
        _fileServers.value.forEach { probeServer(it.baseUrl) }
    }

    private fun probeServer(serverUrl: String) {
        val builder = httpClientBuilder ?: return

        // A probe is already in flight for this URL — don't launch a duplicate.
        if (_health.value[serverUrl] == ServerHealth.Checking) return

        // Reuse a still-fresh cached status instead of hitting the network again.
        cachedMediaServerHealth(serverUrl)?.let { cachedStatus ->
            _health.update { it + (serverUrl to cachedStatus) }
            return
        }

        _health.update { it + (serverUrl to ServerHealth.Checking) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = builder.probeMediaServer(serverUrl)
            _health.update { it + (serverUrl to result) }
        }
    }

    /** Drops health entries for servers no longer in the list so the map can't grow unbounded. */
    private fun pruneHealth() {
        val liveUrls = _fileServers.value.mapTo(HashSet()) { it.baseUrl }
        _health.update { statuses -> statuses.filterKeys { it in liveUrls } }
    }

    fun addServerList(serverList: List<String>) {
        var added = false
        serverList.forEach { if (addServerInternal(it)) added = true }
        if (added) persist()
    }

    fun addServer(serverUrl: String) {
        if (addServerInternal(serverUrl)) persist()
    }

    /** Adds a server to the in-memory list (no persist). Returns true if it was new. */
    private fun addServerInternal(serverUrl: String): Boolean {
        val normalizedUrl =
            try {
                Rfc3986.normalize(serverUrl.trim())
            } catch (e: Exception) {
                serverUrl
            }
        val serverNameReference =
            try {
                Rfc3986.host(normalizedUrl)
            } catch (e: Exception) {
                normalizedUrl
            }
        val serverRef =
            ServerName(
                serverNameReference,
                normalizedUrl,
                ServerType.Blossom,
            )
        if (_fileServers.value.any { it.baseUrl == serverRef.baseUrl }) return false

        _fileServers.update { it.plus(serverRef) }
        probeServer(serverRef.baseUrl)
        isModified = true
        return true
    }

    fun removeServer(
        name: String = "",
        serverUrl: String,
    ) {
        viewModelScope.launch {
            val serverName =
                name.ifBlank {
                    runCatching {
                        Rfc3986.host(serverUrl)
                    }.getOrNull()
                } ?: serverUrl
            _fileServers.update {
                it.minus(
                    ServerName(serverName, serverUrl, ServerType.Blossom),
                )
            }
            pruneHealth()
            isModified = true
            persist()
        }
    }

    /**
     * Publishes any pending change. Called after each discrete edit (add/remove) and,
     * for a reorder, once the drag gesture completes — so a drag doesn't publish a
     * kind-10063 event on every intermediate swap.
     */
    fun persistPending() = persist()

    private fun persist() {
        if (!isModified) return
        isModified = false
        accountViewModel.launchSigner {
            account.sendBlossomServersList(_fileServers.value.map { it.baseUrl })
        }
    }

    private fun obtainFileServers(): List<String> = account.blossomServers.flow.value

    companion object {
        /**
         * Turns the kind-10063 server URLs into list rows. Drops invalid URLs and repeated ones:
         * a list written by another client can name a server twice, and the screen keys its
         * rows by URL, so a repeat would crash the LazyColumn.
         */
        fun toServerNames(serverUrls: List<String>): List<ServerName> =
            serverUrls.distinct().mapNotNull { serverUrl ->
                try {
                    ServerName(
                        Rfc3986.host(serverUrl),
                        serverUrl,
                        ServerType.Blossom,
                    )
                } catch (e: Exception) {
                    Log.d("MediaServersViewModel", "Invalid URL in Blossom server list")
                    null
                }
            }
    }
}
