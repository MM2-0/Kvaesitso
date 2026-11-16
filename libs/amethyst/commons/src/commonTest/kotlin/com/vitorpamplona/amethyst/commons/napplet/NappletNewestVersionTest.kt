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
package com.vitorpamplona.amethyst.commons.napplet

import com.vitorpamplona.quartz.nip5dNapplets.named.NamedNappletEvent
import kotlin.test.Test
import kotlin.test.assertSame

class NappletNewestVersionTest {
    private fun version(
        createdAt: Long,
        id: String = "aa".repeat(32),
    ) = NamedNappletEvent(id, "bb".repeat(32), createdAt, arrayOf(arrayOf("d", "app")), "", "cc".repeat(64))

    // A stranger's note may embed an old signed version of an app the user trusts: the newest one known runs.
    @Test
    fun anOlderEmbeddedVersionLosesToTheNewestKnown() {
        val old = version(1_000)
        val current = version(2_000)

        assertSame(current, NappletLaunch.newerOf(old, current))
        assertSame(current, NappletLaunch.newerOf(current, old))
    }

    @Test
    fun aTieGoesToTheLowerId() {
        val low = version(1_000, "01".repeat(32))
        val high = version(1_000, "02".repeat(32))

        assertSame(low, NappletLaunch.newerOf(high, low))
    }
}
