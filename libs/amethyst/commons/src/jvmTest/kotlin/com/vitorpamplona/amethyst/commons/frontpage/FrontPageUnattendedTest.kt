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
package com.vitorpamplona.amethyst.commons.frontpage

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip89AppHandlers.clientTag.NostrSignerWithClientTag
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The scheduled print's checks before it asks any relay: who can print with nobody there. */
class FrontPageUnattendedTest {
    private val keyPair = KeyPair()

    /** What the account hands out for an nsec login: the local key behind the client-tag wrapper. */
    private val nsecLogin = NostrSignerWithClientTag(inner = NostrSignerInternal(keyPair), clientName = "Amethyst")

    @Test
    fun `an nsec login prints unattended, through the account's signer wrapper`() {
        assertNull(unattendedBlocker(nsecLogin, JsoupPaperBoundary, busy = false))
    }

    @Test
    fun `an external or remote signer skips the print and asks for the reader`() {
        val amber = mockk<NostrSigner> { every { pubKey } returns keyPair.pubKey.toHexKey() }
        val wrapped = NostrSignerWithClientTag(inner = amber, clientName = "Amethyst")
        assertEquals(ScheduledOutcome.NEEDS_SIGNER, unattendedBlocker(wrapped, JsoupPaperBoundary, busy = false))
    }

    @Test
    fun `a read-only login has no key to decrypt with`() {
        val readOnly = NostrSignerInternal(KeyPair(pubKey = keyPair.pubKey))
        assertEquals(ScheduledOutcome.NEEDS_SIGNER, unattendedBlocker(readOnly, JsoupPaperBoundary, busy = false))
    }

    @Test
    fun `no boundary means the platform can't print, whoever signs`() {
        assertEquals(ScheduledOutcome.UNSUPPORTED, unattendedBlocker(nsecLogin, null, busy = false))
    }

    @Test
    fun `a print already running is left alone`() {
        assertEquals(ScheduledOutcome.BUSY, unattendedBlocker(nsecLogin, JsoupPaperBoundary, busy = true))
    }
}
