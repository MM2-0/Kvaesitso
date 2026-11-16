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
package com.vitorpamplona.amethyst.service.notifications

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Quick Settings tile that temporarily enables or disables the always-on background
 * notification service — a battery-saver "airplane mode" — without digging through
 * Settings.
 *
 * The tile toggles the **global master** switch ([LocalPreferences.setNotificationServiceEnabled]),
 * not any per-account setting: when off, [AlwaysOnNotificationServiceManager] tears down
 * every layer for all accounts; when on, each account's own "keep active in the
 * background" flag decides who participates. The master is persisted, so an explicit off
 * survives restarts and crashes.
 *
 * The switch is global, so the tile does not depend on a logged-in account. It only
 * needs `Amethyst.instance` (main process) to reach [LocalPreferences]; during the brief
 * cold-start window before that is built — or in the `:napplet` process, which never
 * hosts this tile — it renders as unavailable and the next `onStartListening` recovers.
 */
class NotificationServiceTileService : TileService() {
    companion object {
        private const val TAG = "NotifServiceTile"
    }

    private var scope: CoroutineScope? = null
    private var watchJob: Job? = null

    /** True once `Amethyst.instance` (and therefore [LocalPreferences]) is reachable. */
    private fun preferencesReady(): Boolean =
        try {
            Amethyst.instance
            true
        } catch (e: Exception) {
            false
        }

    override fun onStartListening() {
        super.onStartListening()
        // onStartListening/onStopListening are balanced by the framework, but cancel
        // any stale collector defensively so a re-entrant start can't leak a scope.
        watchJob?.cancel()
        scope?.cancel()

        refreshTile()
        if (!preferencesReady()) return

        val newScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        scope = newScope
        // Keep the tile in sync when the master is flipped elsewhere (the Settings
        // toggle) while the shade is open.
        watchJob =
            newScope.launch {
                LocalPreferences.notificationServiceEnabledFlow().collectLatest {
                    refreshTile()
                }
            }
    }

    override fun onStopListening() {
        watchJob?.cancel()
        watchJob = null
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (!preferencesReady()) {
            Log.w(TAG, "Preferences not ready; ignoring tile click")
            refreshTile()
            return
        }
        LocalPreferences.setNotificationServiceEnabled(!LocalPreferences.isNotificationServiceEnabled())
        refreshTile()
    }

    private fun refreshTile() {
        val tile = qsTile ?: return

        if (!preferencesReady()) {
            tile.state = Tile.STATE_UNAVAILABLE
        } else {
            val enabled = LocalPreferences.isNotificationServiceEnabled()
            tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.subtitleCompat(
                getString(
                    if (enabled) {
                        R.string.always_on_notif_tile_subtitle_on
                    } else {
                        R.string.always_on_notif_tile_subtitle_off
                    },
                ),
            )
        }

        // Label and icon come from the manifest (<service android:label/android:icon>),
        // which is the tile's default — no need to reset them on every refresh.
        tile.updateTile()
    }

    private fun Tile.subtitleCompat(text: CharSequence) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            subtitle = text
        }
    }
}
