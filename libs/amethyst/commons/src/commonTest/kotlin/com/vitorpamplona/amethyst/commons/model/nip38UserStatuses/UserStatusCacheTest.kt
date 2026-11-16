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
package com.vitorpamplona.amethyst.commons.model.nip38UserStatuses

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.UserContext
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip38UserStatus.UserStatusEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class UserStatusCacheTest {
    private val pubKey = "b".repeat(64)
    private val sig = "c".repeat(128)
    private val author = User(pubKey, UserContext { addr -> AddressableNote(addr) })

    private fun status(
        id: Char,
        content: String,
        createdAt: Long,
    ) = UserStatusEvent(id.toString().repeat(64), pubKey, createdAt, arrayOf(arrayOf("d", UserStatusEvent.MUSIC)), content, sig)

    @Test
    fun aClearedStatusLeavesTheList() {
        val cache = UserStatusCache()
        val note = AddressableNote(Address(UserStatusEvent.KIND, pubKey, UserStatusEvent.MUSIC))

        note.loadEvent(status('1', "Song - Band", 100), author, emptyList())
        cache.addStatus(note)
        assertEquals(listOf(note), cache.statuses.value)

        // The same address now holds the newer, blank version: nothing to show any more.
        note.loadEvent(status('2', "", 200), author, emptyList())
        cache.addStatus(note)
        assertEquals(emptyList(), cache.statuses.value)

        // And a new track brings it back.
        note.loadEvent(status('3', "Other Song - Band", 300), author, emptyList())
        cache.addStatus(note)
        assertEquals(listOf(note), cache.statuses.value)
    }
}
