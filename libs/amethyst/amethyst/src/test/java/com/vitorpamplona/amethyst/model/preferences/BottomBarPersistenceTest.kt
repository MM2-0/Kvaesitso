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
import com.vitorpamplona.amethyst.commons.model.navigation.BottomBarEntry
import com.vitorpamplona.amethyst.commons.model.navigation.DefaultBottomBarEntries
import com.vitorpamplona.amethyst.commons.model.navigation.NavBarItem
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the per-account bottom-bar persistence: the pinned list now lives inside the NIP-78
 * app-specific data blob ([AccountNavigationPreferencesInternal], one field of
 * [com.vitorpamplona.amethyst.commons.model.AccountSyncedSettingsInternal]) rather than the app-global
 * DataStore, so every account keeps its own bar and it syncs across the user's devices.
 */
class BottomBarPersistenceTest {
    @Test
    fun defaultsRoundTripThroughSyncedSettingsBlob() {
        val decoded = JsonMapper.fromJson<AccountNavigationPreferencesInternal>(JsonMapper.toJson(AccountNavigationPreferencesInternal()))
        assertEquals(DefaultBottomBarEntries, decoded.bottomBarItems)
    }

    @Test
    fun blobWrittenBeforeTheNavigationFieldExistedDecodesToCurrentDefaults() {
        // Older clients (and older Amethyst builds) never wrote the `bottomBarItems` field. The default
        // means such a blob decodes to whatever DefaultBottomBarEntries the installed build ships — no
        // migration from the old app-global setting is attempted, matching the intended behavior.
        val decoded = JsonMapper.fromJson<AccountNavigationPreferencesInternal>("{}")
        assertEquals(DefaultBottomBarEntries, decoded.bottomBarItems)
    }

    @Test
    fun customizedBarRoundTripsThroughSyncedSettingsBlob() {
        val custom =
            listOf<BottomBarEntry>(
                BottomBarEntry.BuiltIn(NavBarItem.HOME),
                BottomBarEntry.Favorite("url:https://example.com"),
            )
        val decoded = JsonMapper.fromJson<AccountNavigationPreferencesInternal>(JsonMapper.toJson(AccountNavigationPreferencesInternal(custom)))
        assertEquals(custom, decoded.bottomBarItems)
    }
}
