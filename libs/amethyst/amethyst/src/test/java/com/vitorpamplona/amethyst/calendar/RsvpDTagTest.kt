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
package com.vitorpamplona.amethyst.calendar

import com.vitorpamplona.amethyst.commons.ui.note.types.rsvpAddressFor
import com.vitorpamplona.amethyst.commons.ui.note.types.rsvpDTagFor
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RsvpDTagTest {
    private val targetA = Address(31923, "alice-pubkey", "my-event")
    private val targetB = Address(31922, "bob-pubkey", "another-event")
    private val myPubKey = "me-pubkey"

    @Test
    fun rsvpDTag_isDeterministic_forSameTarget() {
        // Two calls for the same target must produce the same d-tag — that's the dedupe
        // contract that makes RSVP buttons reflect "my current status" rather than a history
        // of taps.
        assertEquals(rsvpDTagFor(targetA), rsvpDTagFor(targetA))
    }

    @Test
    fun rsvpDTag_differsAcrossTargets() {
        assertNotEquals(rsvpDTagFor(targetA), rsvpDTagFor(targetB))
    }

    @Test
    fun rsvpDTag_encodesAllAddressComponents() {
        // Make sure the d-tag is stable across kind + pubkey + dtag axes, so a copy-paste of
        // the same dTag across different kinds (or different authors) doesn't collide.
        val sameKind = Address(targetA.kind, "another-pub", targetA.dTag)
        val sameAuthor = Address(targetA.kind, targetA.pubKeyHex, "another-dtag")
        val sameDtagDifferentKind = Address(31922, targetA.pubKeyHex, targetA.dTag)

        val original = rsvpDTagFor(targetA)
        assertNotEquals(original, rsvpDTagFor(sameKind))
        assertNotEquals(original, rsvpDTagFor(sameAuthor))
        assertNotEquals(original, rsvpDTagFor(sameDtagDifferentKind))
    }

    @Test
    fun rsvpAddressFor_usesRsvpKindAndMyPubKey() {
        val addr = rsvpAddressFor(myPubKey, targetA)
        assertEquals(CalendarRSVPEvent.KIND, addr.kind)
        assertEquals(myPubKey, addr.pubKeyHex)
        assertEquals(rsvpDTagFor(targetA), addr.dTag)
    }
}
