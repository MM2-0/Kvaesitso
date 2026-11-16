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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * An naddr cannot hold a d-tag over 255 bytes, so `NAddress.create` throws for one. D-tags are
 * chosen by whoever wrote the event, and `AddressableNote.toNAddr()` backs `idNote()`,
 * `toNEvent()`, `idDisplayNote()` and `toNostrUri()` of every addressable note on screen, so it
 * must cite such a note some other way instead of throwing.
 */
class AddressCitationTest {
    private val pubKey = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val id = "fae5f3ae5b5bd5e7aa8a97d8a06b61f7d1a7ad5d52a3f0d4e4e1b23d1f2a3b4c"

    @Test
    fun aShortDTagIsAnNaddr() {
        val note = AddressableNote(Address(LongFormContentEvent.KIND, pubKey, "slug"))
        assertEquals(NAddress.create(LongFormContentEvent.KIND, pubKey, "slug", null), note.toNAddr())
        assertEquals("nostr:" + note.toNAddr(), note.toNostrUri())
    }

    @Test
    fun anOverlongDTagIsCitedByTheLoadedEvent() {
        val dTag = "d".repeat(300)
        val note = AddressableNote(Address(LongFormContentEvent.KIND, pubKey, dTag))
        note.event = LongFormContentEvent(id, pubKey, 1, arrayOf(arrayOf("d", dTag)), "", "")

        assertEquals(NEvent.create(id, pubKey, LongFormContentEvent.KIND, null), note.toNAddr())
        assertEquals("nostr:" + note.toNAddr(), note.toNostrUri())
        assertEquals(note.toNAddr(), note.idNote())
    }

    @Test
    fun anOverlongDTagWithNoEventFallsBackToTheAddressValue() {
        val address = Address(LongFormContentEvent.KIND, pubKey, "d".repeat(300))
        val note = AddressableNote(address)

        assertEquals(address.toValue(), note.toNAddr())
        assertEquals("nostr:" + address.toValue(), note.toNostrUri())
    }
}
