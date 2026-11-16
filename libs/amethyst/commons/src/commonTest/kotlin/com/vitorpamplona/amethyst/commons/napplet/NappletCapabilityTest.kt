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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NappletCapabilityTest {
    @Test
    fun mapsOnlyConformingDomainsExactly() {
        assertEquals(NappletCapability.IDENTITY, NappletCapability.fromNapDomain("identity"))
        assertEquals(NappletCapability.RELAY, NappletCapability.fromNapDomain("relay"))
        assertEquals(NappletCapability.STORAGE, NappletCapability.fromNapDomain("storage"))
        assertEquals(NappletCapability.RESOURCE, NappletCapability.fromNapDomain("resource"))
        assertEquals(NappletCapability.THEME, NappletCapability.fromNapDomain("theme"))
        assertEquals(NappletCapability.INC, NappletCapability.fromNapDomain("inc"))
        assertTrue("inc" in NappletCapability.supportedNapDomains)
        assertEquals(NappletCapability.UPLOAD, NappletCapability.fromNapDomain("upload"))
        assertEquals(NappletCapability.COMMON, NappletCapability.fromNapDomain("common"))
        assertEquals(NappletCapability.INTENT, NappletCapability.fromNapDomain("intent"))
    }

    @Test
    fun unknownDomainMapsToNullNotAFallbackGrant() {
        // Domains we don't broker yet must stay unknown (default-deny), not fall through.
        assertNull(NappletCapability.fromNapDomain("cvm"))
        assertNull(NappletCapability.fromNapDomain("filesystem"))
        assertNull(NappletCapability.fromNapDomain("shell"))
        assertNull(NappletCapability.fromNapDomain("keys"))
        assertNull(NappletCapability.fromNapDomain("value"))
        assertNull(NappletCapability.fromNapDomain("notify"))
        assertNull(NappletCapability.fromNapDomain("Relay"))
        assertNull(NappletCapability.fromNapDomain(" storage "))
        assertNull(NappletCapability.fromNapDomain(""))
    }

    @Test
    fun resolveSeparatesKnownFromUnknownAndFlags() {
        val resolved = resolveRequiredCapabilities(listOf("identity", "relay", "notify", "value"))

        assertEquals(
            setOf(NappletCapability.IDENTITY, NappletCapability.RELAY),
            resolved.capabilities,
        )
        assertEquals(listOf(UnknownNapDomain("notify"), UnknownNapDomain("value")), resolved.unknown)
        assertTrue(resolved.hasUnknown)
    }

    @Test
    fun resolveWithOnlyKnownDomainsHasNoUnknownFlag() {
        val resolved = resolveRequiredCapabilities(listOf("identity"))
        assertFalse(resolved.hasUnknown)
    }
}
