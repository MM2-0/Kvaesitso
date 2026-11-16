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
package com.vitorpamplona.amethyst.ui.tor

import org.junit.Assert.assertEquals
import org.junit.Test

class TorProgressNoticeTest {
    @Test
    fun `opted out hides the notification whatever Tor is doing`() {
        assertEquals(TorProgressNotice.Hidden, TorProgressNotice.from(false, TorServiceStatus.Connecting, -1))
        assertEquals(TorProgressNotice.Hidden, TorProgressNotice.from(false, TorServiceStatus.Bootstrapping(9050), 500))
        assertEquals(TorProgressNotice.Hidden, TorProgressNotice.from(false, TorServiceStatus.Active(9050), 1000))
    }

    @Test
    fun `tor off hides the notification`() {
        assertEquals(TorProgressNotice.Hidden, TorProgressNotice.from(true, TorServiceStatus.Off, -1))
    }

    @Test
    fun `connecting shows the starting phase`() {
        assertEquals(TorProgressNotice.Starting, TorProgressNotice.from(true, TorServiceStatus.Connecting, -1))
    }

    @Test
    fun `bootstrapping turns permille into a percentage`() {
        assertEquals(TorProgressNotice.Downloading(0), TorProgressNotice.from(true, TorServiceStatus.Bootstrapping(9050), 0))
        assertEquals(TorProgressNotice.Downloading(42), TorProgressNotice.from(true, TorServiceStatus.Bootstrapping(9050), 427))
        assertEquals(TorProgressNotice.Downloading(100), TorProgressNotice.from(true, TorServiceStatus.Bootstrapping(9050), 1000))
    }

    @Test
    fun `bootstrapping with no reported progress is indeterminate`() {
        assertEquals(TorProgressNotice.Downloading(null), TorProgressNotice.from(true, TorServiceStatus.Bootstrapping(9050), -1))
    }

    @Test
    fun `out-of-range progress is clamped`() {
        assertEquals(TorProgressNotice.Downloading(100), TorProgressNotice.from(true, TorServiceStatus.Bootstrapping(9050), 1500))
    }

    @Test
    fun `active shows ready`() {
        assertEquals(TorProgressNotice.Ready, TorProgressNotice.from(true, TorServiceStatus.Active(9050), 1000))
    }
}
