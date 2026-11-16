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
package com.vitorpamplona.amethyst.commons.util

import kotlin.test.Test
import kotlin.test.assertEquals

class DevicePlatformTest {
    @Test
    fun mapsJvmPropertiesToNip82Identifiers() {
        assertEquals(listOf("darwin-arm64", "darwin-x86_64"), desktopPlatformIds("Mac OS X", "aarch64"))
        assertEquals(listOf("darwin-x86_64"), desktopPlatformIds("Mac OS X", "x86_64"))
        assertEquals(listOf("windows-x86_64"), desktopPlatformIds("Windows 11", "amd64"))
        assertEquals(listOf("windows-aarch64", "windows-x86_64"), desktopPlatformIds("Windows 11", "aarch64"))
        assertEquals(listOf("linux-x86_64"), desktopPlatformIds("Linux", "amd64"))
        assertEquals(listOf("linux-aarch64"), desktopPlatformIds("Linux", "aarch64"))
        assertEquals(emptyList(), desktopPlatformIds("SunOS", "sparcv9"))
    }
}
