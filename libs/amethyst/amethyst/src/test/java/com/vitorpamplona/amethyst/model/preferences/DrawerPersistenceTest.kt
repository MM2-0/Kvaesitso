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
package com.vitorpamplona.amethyst.model.preferences

import com.vitorpamplona.amethyst.commons.model.AccountNavigationPreferencesInternal
import com.vitorpamplona.amethyst.commons.model.navigation.NavBarItem
import com.vitorpamplona.amethyst.commons.model.navigation.navBarItemsFromNames
import com.vitorpamplona.amethyst.commons.model.navigation.toNames
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the per-account side-menu persistence. The hidden rows ride along in the same NIP-78
 * app-specific data blob as the bottom bar, so every account keeps its own menu and it syncs across
 * the user's devices.
 */
class DrawerPersistenceTest {
    @Test
    fun defaultIsAnUntouchedMenu() {
        val decoded = JsonMapper.fromJson<AccountNavigationPreferencesInternal>(JsonMapper.toJson(AccountNavigationPreferencesInternal()))

        assertEquals(emptyList<String>(), decoded.hiddenDrawerItems)
    }

    @Test
    fun blobWrittenBeforeTheFieldExistedDecodesToAnUntouchedMenu() {
        // Every existing account is in this state, and so is every account that never opens the
        // screen — which is exactly why the preference stores hidden rows rather than visible ones.
        val decoded = JsonMapper.fromJson<AccountNavigationPreferencesInternal>("{}")

        assertEquals(emptyList<String>(), decoded.hiddenDrawerItems)
    }

    @Test
    fun hiddenRowsRoundTripThroughTheSyncedSettingsBlob() {
        val hidden = setOf(NavBarItem.DRAFTS, NavBarItem.BADGES)

        val json = JsonMapper.toJson(AccountNavigationPreferencesInternal(hiddenDrawerItems = hidden.toNames()))
        val decoded = JsonMapper.fromJson<AccountNavigationPreferencesInternal>(json)

        assertEquals(hidden, navBarItemsFromNames(decoded.hiddenDrawerItems))
    }

    @Test
    fun serializedFormIsDeterministic() {
        // Two equal sets must produce byte-identical JSON, or a republish that changed nothing would
        // still look like a change and churn the account's NIP-78 event.
        val a = JsonMapper.toJson(AccountNavigationPreferencesInternal(hiddenDrawerItems = setOf(NavBarItem.DRAFTS, NavBarItem.BADGES).toNames()))
        val b = JsonMapper.toJson(AccountNavigationPreferencesInternal(hiddenDrawerItems = setOf(NavBarItem.BADGES, NavBarItem.DRAFTS).toNames()))

        assertEquals(a, b)
    }

    @Test
    fun anIdFromANewerClientIsDroppedInsteadOfFailingTheWholeBlob() {
        // The names are stored as strings precisely for this: decoding them as the enum would throw
        // and take every other synced setting down with it.
        val json = """{"hiddenDrawerItems":["DRAFTS","SOME_SCREEN_FROM_THE_FUTURE"]}"""

        val decoded = JsonMapper.fromJson<AccountNavigationPreferencesInternal>(json)

        assertEquals(setOf(NavBarItem.DRAFTS), navBarItemsFromNames(decoded.hiddenDrawerItems))
    }
}
