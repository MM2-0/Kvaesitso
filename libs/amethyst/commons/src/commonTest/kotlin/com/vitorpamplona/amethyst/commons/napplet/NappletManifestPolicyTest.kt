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
import com.vitorpamplona.quartz.nip5dNapplets.root.RootNappletEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NappletManifestPolicyTest {
    private val zero = "00".repeat(32)
    private val hash = "ab".repeat(32)

    private fun manifest(vararg tags: Array<String>) = RootNappletEvent(zero, zero, 0, arrayOf(arrayOf("x", hash), *tags), "A test app", zero)

    @Test
    fun exposesOnlyImplementedDeclaredDomains() {
        val event = manifest(arrayOf("R", "relay"), arrayOf("O", "theme"), arrayOf("O", "future"))
        assertEquals(listOf("relay", "theme"), event.exposedDomains())
        assertTrue(event.unsupportedRequired().isEmpty())
        assertTrue(event.isLaunchable())
    }

    @Test
    fun anUnimplementedRequiredDomainBlocksLaunch() {
        val event = manifest(arrayOf("R", "relay"), arrayOf("R", "future"))
        assertEquals(listOf("future"), event.unsupportedRequired())
        assertFalse(event.isLaunchable())
    }

    @Test
    fun anInvalidManifestIsNotLaunchable() {
        assertFalse(RootNappletEvent(zero, zero, 0, arrayOf(arrayOf("x", hash)), " ", zero).isLaunchable())
        // The retired multi-file form must not run under any identity.
        assertFalse(RootNappletEvent(zero, zero, 0, arrayOf(arrayOf("path", "/index.html", hash), arrayOf("x", hash, "aggregate")), "", zero).isLaunchable())
        assertFalse(RootNappletEvent(zero, zero, 0, arrayOf(arrayOf("x", hash), arrayOf("x", hash)), "d", zero).isLaunchable())
        assertFalse(RootNappletEvent(zero, zero, 0, arrayOf(arrayOf("x", hash), arrayOf("d", "root")), "d", zero).isLaunchable())
        assertFalse(NamedNappletEvent(zero, zero, 0, arrayOf(arrayOf("x", hash)), "d", zero).isLaunchable())
        assertFalse(NamedNappletEvent(zero, zero, 0, arrayOf(arrayOf("x", hash), arrayOf("d", "")), "d", zero).isLaunchable())
        assertTrue(NamedNappletEvent(zero, zero, 0, arrayOf(arrayOf("x", hash), arrayOf("d", "feed")), "d", zero).isLaunchable())
    }

    @Test
    fun aDomainIsMatchedExactly() {
        // Domain names are exact lowercase strings: `Theme` is not `theme`, and is never exposed.
        val event = manifest(arrayOf("O", "Theme"), arrayOf("R", "relay"))
        assertEquals(listOf("relay"), event.exposedDomains())
        assertTrue(event.isLaunchable())
        assertEquals(listOf("NAP-RELAY"), manifest(arrayOf("R", "NAP-RELAY")).unsupportedRequired())
    }

    @Test
    fun outboxIsAnImplementedDomain() {
        // The published Minesweeper and Game Boy Color napplets require outbox.
        assertTrue(manifest(arrayOf("R", "identity"), arrayOf("R", "outbox")).isLaunchable())
        assertTrue(manifest(arrayOf("R", "identity"), arrayOf("R", "outbox"), arrayOf("R", "relay"), arrayOf("R", "resource")).isLaunchable())
        assertEquals(listOf("notify"), manifest(arrayOf("R", "outbox"), arrayOf("R", "notify"), arrayOf("R", "fs")).unsupportedRequired())
        assertEquals(listOf("identity", "outbox"), manifest(arrayOf("R", "identity"), arrayOf("R", "outbox")).exposedDomains())
    }

    @Test
    fun fsIsAnImplementedDomain() {
        // PhotoHop (the "Fotohop" root napplet) requires fs and can use keys, storage and theme.
        val photoHop = manifest(arrayOf("R", "fs"), arrayOf("O", "keys"), arrayOf("O", "storage"), arrayOf("O", "theme"))
        assertTrue(photoHop.isLaunchable())
        assertEquals(listOf("fs", "storage", "theme"), photoHop.exposedDomains())
    }
}
