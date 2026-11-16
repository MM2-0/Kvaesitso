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
package com.vitorpamplona.amethyst.commons.model.preferences

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * The widths, in dp, the user dragged the side-by-side panes of a wide window to: the docked
 * drawer, the docked notification panel, and the chat list beside an open chat (Messages and every
 * other chat list share it). Null is "never dragged": the layout's own default. The layout clamps
 * them to what the window can hold, so these are what the user asked for, not necessarily what is
 * drawn.
 */
@Immutable
data class PaneWidths(
    val drawer: Float? = null,
    val notifications: Float? = null,
    val chatList: Float? = null,
)

/**
 * Device-global persistence for [PaneWidths]: how wide a pane reads best depends on the screen,
 * so it is never published to relays. Mirrors [DrawerSectionCollapsePreferences]: loads on
 * construction (build it eagerly at startup), then writes changes back. A drag changes a width on
 * every frame, so writes wait until the drag has rested.
 */
@Stable
class PaneWidthPreferences(
    private val store: DataStore<Preferences>,
    scope: CoroutineScope,
) {
    private val widths = MutableStateFlow(PaneWidths())

    val flow: StateFlow<PaneWidths> = widths.asStateFlow()

    init {
        scope.launch {
            restoreFromDisk()
            @OptIn(FlowPreview::class)
            widths.drop(1).debounce(SAVE_DELAY_MS).collect { persist(it) }
        }
    }

    fun setDrawer(width: Float) = widths.update { it.copy(drawer = width) }

    fun setNotifications(width: Float) = widths.update { it.copy(notifications = width) }

    fun setChatList(width: Float) = widths.update { it.copy(chatList = width) }

    private suspend fun restoreFromDisk() {
        try {
            val prefs = store.data.first()
            widths.value = PaneWidths(prefs[DRAWER], prefs[NOTIFICATIONS], prefs[CHAT_LIST])
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("PaneWidthPrefs") { "Error reading pane widths: ${e.message}" }
        }
    }

    private suspend fun persist(value: PaneWidths) {
        try {
            store.edit { prefs ->
                prefs.putOrRemove(DRAWER, value.drawer)
                prefs.putOrRemove(NOTIFICATIONS, value.notifications)
                prefs.putOrRemove(CHAT_LIST, value.chatList)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("PaneWidthPrefs") { "Error writing pane widths: ${e.message}" }
        }
    }

    private fun MutablePreferences.putOrRemove(
        key: Preferences.Key<Float>,
        value: Float?,
    ) {
        if (value == null) remove(key) else this[key] = value
    }

    companion object {
        const val SAVE_DELAY_MS = 400L
        private val DRAWER = floatPreferencesKey("ui.panes.drawerWidth")
        private val NOTIFICATIONS = floatPreferencesKey("ui.panes.notificationsWidth")
        private val CHAT_LIST = floatPreferencesKey("ui.panes.chatListWidth")
    }
}
