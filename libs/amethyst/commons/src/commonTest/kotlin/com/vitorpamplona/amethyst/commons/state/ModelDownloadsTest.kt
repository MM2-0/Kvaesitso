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
package com.vitorpamplona.amethyst.commons.state

import com.vitorpamplona.amethyst.commons.model.ConnectivityType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelDownloadsTest {
    @Test
    fun connectivityChoicesAllowTheirNetworks() {
        assertTrue(ConnectivityType.ALWAYS.allows(isOnMobileOrMeteredConnection = true))
        assertTrue(ConnectivityType.ALWAYS.allows(isOnMobileOrMeteredConnection = false))
        assertFalse(ConnectivityType.WIFI_ONLY.allows(isOnMobileOrMeteredConnection = true))
        assertTrue(ConnectivityType.WIFI_ONLY.allows(isOnMobileOrMeteredConnection = false))
        assertFalse(ConnectivityType.NEVER.allows(isOnMobileOrMeteredConnection = false))
    }

    @Test
    fun wifiOnlyStaysUnmeteredWhileOnWifiAndStopsOnMobileData() {
        assertEquals(ModelDownloads.UNMETERED_ONLY, ModelDownloads.of(ConnectivityType.WIFI_ONLY, isOnMobileOrMeteredConnection = false))
        assertEquals(ModelDownloads.NONE, ModelDownloads.of(ConnectivityType.WIFI_ONLY, isOnMobileOrMeteredConnection = true))
    }

    @Test
    fun alwaysAndNeverIgnoreTheConnection() {
        assertEquals(ModelDownloads.ANY_NETWORK, ModelDownloads.of(ConnectivityType.ALWAYS, isOnMobileOrMeteredConnection = true))
        assertEquals(ModelDownloads.NONE, ModelDownloads.of(ConnectivityType.NEVER, isOnMobileOrMeteredConnection = false))
    }
}
