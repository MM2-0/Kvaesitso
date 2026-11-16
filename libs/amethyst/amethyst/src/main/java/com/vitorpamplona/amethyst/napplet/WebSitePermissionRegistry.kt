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
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.browser.BrowserSitePermission
import com.vitorpamplona.amethyst.commons.browser.BrowserSitePermission.Decision
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The per-site permission file, on the app-wide holder rather than a `Context` delegate (the same
 * `filesDir/datastore/web_site_permissions.preferences_pb` path the delegate used).
 *
 * Main process only: [Amethyst.instance] is deliberately unset in the `:napplet` sandbox, which reaches
 * these answers through the broker.
 */
private val webSitePermissionDataStore: DataStore<Preferences>
    get() = Amethyst.instance.appStores.getDataStore("web_site_permissions")

/**
 * The user's answers to web sites' camera / microphone / location requests, per **origin**
 * (`https://example.com`), the way Chrome's site settings keep them. Absent = [Decision.ASK]: the browser
 * prompts the next time the site asks. The same answer holds on Tor and the open web — routing never
 * changes it.
 *
 * Lives only in the **main process**. The keyless browser reads and writes it through the broker
 * ([com.vitorpamplona.amethyst.napplethost.NappletIpc.MSG_QUERY_SITE_PERMISSIONS] /
 * [com.vitorpamplona.amethyst.napplethost.NappletIpc.MSG_SET_SITE_PERMISSION]); the Connected Apps detail
 * screen shows and resets it. In-memory state is authoritative for the session, written through to a
 * DataStore stored under `"<origin>|<permission>"` keys.
 */
object WebSitePermissionRegistry {
    private val _decisions = MutableStateFlow<Map<String, Map<BrowserSitePermission, Decision>>>(emptyMap())

    /** origin → permission → decision (only answered permissions appear). */
    val decisions: StateFlow<Map<String, Map<BrowserSitePermission, Decision>>> = _decisions.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext != null) return
        val ctx = context.applicationContext
        appContext = ctx
        scope.launch {
            val loaded = mutableMapOf<String, MutableMap<BrowserSitePermission, Decision>>()
            webSitePermissionDataStore.data.first().asMap().forEach { (key, value) ->
                val origin = key.name.substringBeforeLast('|')
                val permission = BrowserSitePermission.fromKey(key.name.substringAfterLast('|')) ?: return@forEach
                val decision = runCatching { Decision.valueOf(value.toString()) }.getOrNull() ?: return@forEach
                loaded.getOrPut(origin) { mutableMapOf() }[permission] = decision
            }
            // Answers given this session (before hydration finished) win over the disk copy.
            _decisions.update { current ->
                (loaded.keys + current.keys).associateWith { origin -> loaded[origin].orEmpty() + current[origin].orEmpty() }
            }
        }
    }

    fun decision(
        origin: String,
        permission: BrowserSitePermission,
    ): Decision = _decisions.value[origin]?.get(permission) ?: Decision.ASK

    fun set(
        origin: String,
        permission: BrowserSitePermission,
        decision: Decision,
    ) {
        _decisions.update { current ->
            val forOrigin = current[origin].orEmpty().toMutableMap()
            if (decision == Decision.ASK) forOrigin.remove(permission) else forOrigin[permission] = decision
            if (forOrigin.isEmpty()) current - origin else current + (origin to forOrigin)
        }
        if (appContext == null) return
        scope.launch {
            webSitePermissionDataStore.edit { prefs ->
                val key = stringPreferencesKey("$origin|${permission.key}")
                if (decision == Decision.ASK) prefs.remove(key) else prefs[key] = decision.name
            }
        }
    }
}
