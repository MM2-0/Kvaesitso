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
package com.vitorpamplona.amethyst.commons.nipsOnNostr.dal

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.LiveHiddenUsers
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The kind page's sample: one card per event, by authors the account doesn't hide. */
class KindEventsFeedFilterTest {
    private val author = NostrSignerSync(KeyPair())
    private val muted = NostrSignerSync(KeyPair())

    private val hidden = MutableStateFlow(LiveHiddenUsers.EMPTY)

    private val account =
        mockk<Account>().also {
            every { it.userProfile() } returns mockk<User>(relaxed = true).also { u -> every { u.pubkeyHex } returns "a".repeat(64) }
            every { it.hiddenUsers.flow } returns hidden
            every { it.isAcceptable(any<Note>()) } returns true
        }

    private fun consumed(
        signer: NostrSignerSync,
        kind: Int,
        createdAt: Long,
        vararg tags: Array<String>,
    ): Event {
        val event = signer.sign<Event>(EventTemplate(createdAt, kind, arrayOf(*tags), "v$createdAt"))
        LocalCache.justConsume(event, null, false)
        return event
    }

    private fun feedBy(
        kind: Int,
        signer: NostrSignerSync,
    ): List<Note> = KindEventsFeedFilter(kind, account).feed().filter { it.author?.pubkeyHex == signer.pubKey }

    @Test
    fun anEditedAddressableEventIsListedOnceAtItsNewestVersion() {
        val kind = 36_788
        consumed(author, kind, 1_700_000_000L, arrayOf("d", "edited"))
        val newest = consumed(author, kind, 1_700_000_500L, arrayOf("d", "edited"))
        val other = consumed(author, kind, 1_700_000_200L, arrayOf("d", "other"))

        val feed = feedBy(kind, author)

        // The cache also holds every version as a plain note by id; those are not cards of their own.
        assertEquals(2, feed.size)
        assertTrue(feed.all { it is AddressableNote })
        assertEquals(setOf(newest.id, other.id), feed.mapNotNull { it.event?.id }.toSet())
    }

    @Test
    fun aRegularEventIsListedOnce() {
        val kind = 7_377
        val event = consumed(author, kind, 1_700_000_300L)

        assertEquals(listOf(event.id), feedBy(kind, author).map { it.idHex })
    }

    @Test
    fun aHiddenAuthorIsLeftOut() {
        val kind = 7_378
        consumed(muted, kind, 1_700_000_300L)
        hidden.value = LiveHiddenUsers.EMPTY.copy(hiddenUsersHashCodes = setOf(muted.pubKey.hashCode()))

        assertEquals(emptyList(), feedBy(kind, muted))
    }
}
