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

import androidx.compose.runtime.Stable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.vitorpamplona.amethyst.commons.model.navigation.DrawerSectionId
import com.vitorpamplona.amethyst.commons.model.navigation.drawerSectionIdsFromNames
import com.vitorpamplona.amethyst.commons.model.navigation.toNames
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Device-global persistence for the drawer section headings the user has collapsed, so the side menu
 * comes back folded the way they left it instead of springing fully open on every launch. Which
 * headings are folded is a per-device view choice, so unlike the hidden rows beside it in the drawer
 * it is never published to relays.
 *
 * Mirrors [RelayGroupDeletionStore]: app-wide (not per-account), loads the saved names on
 * construction, then writes every later change back. Takes the [DataStore] rather than a `Context`
 * so the whole cycle is exercised by a plain unit test against a temp file.
 *
 * Construct once, eagerly. The restore is a fire-and-forget coroutine, not a barrier, so the flow
 * reads as "nothing collapsed" until it lands; building this at app startup rather than on first use
 * puts that read many frames ahead of the drawer's first composition (and the store's file has
 * already been parsed by then, for `UiSharedPreferences`). Worst case if it ever lost that race is
 * cosmetic — a heading renders open and then folds — which is why no one waits on it.
 */
@Stable
class DrawerSectionCollapsePreferences(
    private val store: DataStore<Preferences>,
    scope: CoroutineScope,
) {
    private val collapsed = MutableStateFlow<Set<DrawerSectionId>>(emptySet())

    /** The collapsed headings; the drawer collects this to decide which sections render their rows. */
    val flow: StateFlow<Set<DrawerSectionId>> = collapsed.asStateFlow()

    init {
        scope.launch {
            restoreFromDisk()
            // drop(1) skips the value present at collection start, which restoreFromDisk already wrote.
            collapsed.drop(1).collect { persist(it) }
        }
    }

    /** Collapses [section] if expanded, expands it if collapsed. Safe to call from the main thread. */
    fun toggle(section: DrawerSectionId) = collapsed.update { if (section in it) it - section else it + section }

    private suspend fun restoreFromDisk() {
        try {
            val raw = store.data.first()[KEY] ?: return
            collapsed.value = drawerSectionIdsFromNames(raw)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("DrawerSectionCollapsePrefs") { "Error reading collapsed drawer sections: ${e.message}" }
        }
    }

    private suspend fun persist(sections: Set<DrawerSectionId>) {
        try {
            store.edit { prefs -> prefs[KEY] = sections.toNames() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("DrawerSectionCollapsePrefs") { "Error writing collapsed drawer sections: ${e.message}" }
        }
    }

    companion object {
        private val KEY = stringSetPreferencesKey("ui.drawer.collapsedSections")
    }
}
