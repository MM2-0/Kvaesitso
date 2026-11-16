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
package com.vitorpamplona.amethyst.commons.notices.ui

import com.vitorpamplona.amethyst.commons.notices.ActionFailed
import com.vitorpamplona.amethyst.commons.notices.ConcordNotice
import com.vitorpamplona.amethyst.commons.notices.InvoiceNotice
import com.vitorpamplona.amethyst.commons.notices.NwcFailure
import com.vitorpamplona.amethyst.commons.notices.PowPublishFailed
import com.vitorpamplona.amethyst.commons.notices.SignerNotice
import com.vitorpamplona.amethyst.commons.notices.UserNotice
import com.vitorpamplona.amethyst.commons.notices.ZapNotice
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The notice wording resolves through compose resources, arguments and nested labels included. */
class ResUserNoticeResolverTest {
    private fun resolve(notice: UserNotice) = runBlocking { ResUserNoticeResolver.resolve(notice) }

    @Test
    fun plainNoticesHaveTitleAndMessage() {
        val text = resolve(SignerNotice.ReadOnly)
        assertTrue(text.title.isNotBlank())
        assertTrue(text.message.isNotBlank())
    }

    @Test
    fun anActionFailedOnBadDataIsNotBlamedOnTheSigner() {
        val text = resolve(ActionFailed(IllegalArgumentException("expected '{' at offset 0")))
        assertEquals("Action failed", text.title)
        assertTrue("could not read" in text.message, text.message)
        assertFalse("Signer" in text.title, text.title)
    }

    @Test
    fun argumentsAreFilledIn() {
        val text = resolve(InvoiceNotice.CallbackNotFound("alice@example.com"))
        assertTrue("alice@example.com" in text.message, text.message)
        assertFalse("%1" in text.message, text.message)
    }

    @Test
    fun aTimeoutUsesThePluralWithTheSeconds() {
        val text = resolve(ZapNotice.PayInvoiceFailed(NwcFailure.TimedOut))
        assertTrue("60" in text.message, text.message)
    }

    @Test
    fun nestedLabelsAreResolved() {
        val text = resolve(PowPublishFailed(ReactionEvent.KIND, willRetryOnRestart = false, message = "boom"))
        assertTrue("boom" in text.message, text.message)
        assertFalse("%" in text.message, text.message)
    }

    @Test
    fun aBlankCommunityNameFallsBackToTheUnnamedMessage() {
        val named = resolve(ConcordNotice.Kicked("Dev Lounge"))
        val unnamed = resolve(ConcordNotice.Kicked("  "))
        assertTrue("Dev Lounge" in named.message, named.message)
        assertFalse("%" in unnamed.message, unnamed.message)
    }
}
