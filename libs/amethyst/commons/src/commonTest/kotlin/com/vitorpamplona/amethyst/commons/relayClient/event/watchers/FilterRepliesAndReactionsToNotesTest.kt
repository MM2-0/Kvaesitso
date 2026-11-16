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
package com.vitorpamplona.amethyst.commons.relayClient.event.watchers

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip34Git.ci.jobResult.CiJobResultEvent
import com.vitorpamplona.quartz.nip34Git.ci.workflowProgress.CiWorkflowProgressEvent
import com.vitorpamplona.quartz.nip34Git.ci.workflowResult.CiWorkflowResultEvent
import com.vitorpamplona.quartz.nip34Git.coverNote.GitCoverNoteEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilterRepliesAndReactionsToNotesTest {
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://one.example")!!
    private val rootId = "a".repeat(64)
    private val author = "b".repeat(64)

    private fun note(id: String) = Note(id).apply { addRelaySync(relay) }

    private fun addressable(address: Address) = AddressableNote(address).apply { addRelaySync(relay) }

    @Test
    fun asksForNip22RootScopeSoNestedCommentsLoadOutsideTheThreadScreen() {
        val filters = filterRepliesAndReactionsToNotes(listOf(note(rootId)), null)!!

        val rootScope = filters.filter { it.filter.tags?.containsKey("E") == true }

        assertEquals(1, rootScope.size)
        assertEquals(
            listOf(rootId),
            rootScope
                .single()
                .filter.tags
                ?.get("E"),
        )
        assertTrue(
            rootScope
                .single()
                .filter.kinds!!
                .contains(CommentEvent.KIND),
        )
        // The uppercase scope must be its own REQ: tag names inside one filter are ANDed,
        // so merging `e` and `E` would only match comments carrying both.
        assertTrue(filters.none { it.filter.tags?.containsKey("e") == true && it.filter.tags?.containsKey("E") == true })
    }

    @Test
    fun asksForNip22RootAddressScopeOnAddressables() {
        val address = Address(30023, author, "article")
        val filters = filterRepliesAndReactionsToAddresses(listOf(addressable(address)), null)!!

        val rootScope = filters.filter { it.filter.tags?.containsKey("A") == true }

        assertEquals(1, rootScope.size)
        assertEquals(
            listOf(address.toValue()),
            rootScope
                .single()
                .filter.tags
                ?.get("A"),
        )
        assertTrue(
            rootScope
                .single()
                .filter.kinds!!
                .contains(CommentEvent.KIND),
        )
    }

    @Test
    fun ciRunsRideTheRootScopeAndCoverNotesTheLowercaseRoot() {
        val filters = filterRepliesAndReactionsToNotes(listOf(note(rootId)), null)!!

        val rootScope = filters.single { it.filter.tags?.containsKey("E") == true }.filter.kinds!!
        assertTrue(rootScope.contains(CiWorkflowResultEvent.KIND))
        assertTrue(rootScope.contains(CiWorkflowProgressEvent.KIND))
        // Job Results carry log tails: fetched by id from the runs sheet, never in the feed fan-out.
        assertFalse(filters.any { it.filter.kinds?.contains(CiJobResultEvent.KIND) == true })

        assertTrue(filters.any { it.filter.tags?.containsKey("e") == true && it.filter.kinds?.contains(GitCoverNoteEvent.KIND) == true })
    }
}
