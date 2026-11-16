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
package com.vitorpamplona.amethyst.napplet

import android.content.Context
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.amethyst.Amethyst
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * The per-site routing file, on the app-wide holder rather than a `Context`
 * delegate. Same path the delegate resolved to, so nothing migrates.
 *
 * Main process only: [Amethyst.instance] is deliberately unset in the
 * `:napplet` sandbox, and this registry is only touched from the browser
 * chrome that runs in the main process.
 */
private val webUrlNetworkDataStore: DataStore<Preferences>
    get() = Amethyst.instance.appStores.getDataStore("weburl_network")

/**
 * Per-web-client network-routing preference: whether a favorited URL / browsed site routes through
 * **Tor** (the default when Tor is active) or over the **open web**. The web-client sibling of
 * [NsiteNetworkRegistry] — same model, but keyed by the site's **host** (so every page of a site
 * shares the choice and it survives across launches).
 *
 * This exists because some sites' servers reject Tor exit connections; the user toggles such a site to
 * the open web once from its control puck, and we must remember that so it doesn't break again next
 * time. Absent = Tor (the safe default).
 *
 * Lives only in the **main process** (where the browser chrome runs); an in-memory map is authoritative
 * for the session with write-through persistence.
 */
object WebAppNetworkRegistry {
    private const val OPEN_WEB = "OPEN"
    private const val TOR = "TOR"

    // host -> useTor. Absent means "never chosen" -> "Applets and Websites" (see [useTor]).
    private val modes = ConcurrentHashMap<String, Boolean>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var appContext: Context? = null

    @Volatile private var hydration: Job? = null

    /** Binds the app context and hydrates the on-disk preferences into memory. Idempotent. */
    fun init(context: Context) {
        if (appContext != null) return
        val ctx = context.applicationContext
        appContext = ctx
        hydration =
            scope.launch {
                webUrlNetworkDataStore.data.first().asMap().forEach { (key, value) ->
                    // putIfAbsent: never clobber a choice made in this session before hydration finished.
                    modes.putIfAbsent(key.name, value != OPEN_WEB)
                }
            }
    }

    /**
     * Suspends until [init]'s on-disk hydration has finished, so [useTor] reflects the user's remembered
     * per-site choices instead of the bare Tor default. The startup preloader awaits this before making
     * any routing decision, so a site the user pinned to the open web isn't wrongly treated as Tor on a
     * cold start (which would leave a Tor-incompatible site failing until the next visit). Returns at once
     * once hydrated, or immediately if [init] was never called.
     */
    suspend fun awaitReady() {
        hydration?.join()
    }

    /** The host key for [url] (e.g. `vitorpamplona.com`), or the raw string if it has no host. */
    fun hostKeyOf(url: String): String = runCatching { url.toUri().host }.getOrNull()?.takeIf { it.isNotBlank() } ?: url

    /**
     * Whether the site behind [url] routes through Tor: the user's choice for its host, else the Tor
     * settings' "Applets and Websites" switch. Tor until the stored choices have loaded (or when [init]
     * never ran), so a site pinned to Tor never starts on the open web because its choice wasn't read.
     */
    fun useTor(url: String): Boolean = modes[hostKeyOf(url)] ?: (hydration?.isCompleted != true || Amethyst.instance.torPrefs.value.appletsAndWebsitesViaTor.value)

    /** Records [useTor] for the host behind [url] in memory and persists it. */
    fun set(
        url: String,
        useTor: Boolean,
    ) {
        val host = hostKeyOf(url)
        modes[host] = useTor
        appContext ?: return
        scope.launch {
            webUrlNetworkDataStore.edit { it[stringPreferencesKey(host)] = if (useTor) TOR else OPEN_WEB }
        }
    }
}
