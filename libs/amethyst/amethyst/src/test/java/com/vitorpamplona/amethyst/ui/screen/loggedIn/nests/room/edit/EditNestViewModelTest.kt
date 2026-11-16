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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.edit

import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nests.room.edit.EditNestViewModel
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.StatusTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditNestViewModelTest {
    private val host = "a".repeat(64)
    private val speakerA = "b".repeat(64)
    private val speakerB = "c".repeat(64)

    private fun original(
        dTag: String = "rt-1",
        roomName: String = "Old Name",
        summary: String = "Old summary",
        endpoint: String = "https://moq.example.com",
        service: String = "https://auth.example.com",
        extraParticipants: List<Array<String>> = emptyList(),
    ): MeetingSpaceEvent {
        val tags =
            buildList<Array<String>> {
                add(arrayOf("d", dTag))
                add(arrayOf("room", roomName))
                add(arrayOf("status", StatusTag.STATUS.LIVE.code))
                add(arrayOf("service", service))
                add(arrayOf("endpoint", endpoint))
                add(arrayOf("summary", summary))
                add(arrayOf("p", host, "", "host"))
                addAll(extraParticipants)
            }.toTypedArray()
        return MeetingSpaceEvent(
            id = "0".repeat(64),
            pubKey = host,
            createdAt = 100L,
            tags = tags,
            content = "",
            sig = "0".repeat(128),
        )
    }

    private fun form(
        dTag: String,
        roomName: String,
        summary: String = "",
        imageUrl: String = "",
        endpointUrl: String = "https://moq.example.com",
        serviceUrl: String = "https://auth.example.com",
    ) = EditNestViewModel.FormState(
        dTag = dTag,
        roomName = roomName,
        summary = summary,
        imageUrl = imageUrl,
        endpointUrl = endpointUrl,
        serviceUrl = serviceUrl,
        isPublishing = false,
        error = null,
    )

    @Test
    fun republishKeepsSameDTagAndUpdatesFields() {
        val src = original(dTag = "rt-42", roomName = "Old", summary = "Old summary")
        val newForm = form(dTag = "rt-42", roomName = "New name", summary = "New summary")

        val template =
            EditNestViewModel.buildEditTemplate(src, newForm, StatusTag.STATUS.LIVE)

        // Same d-tag → same address → relay treats as replacement.
        val dTag = template.tags.firstOrNull { it.firstOrNull() == "d" }?.getOrNull(1)
        assertEquals("rt-42", dTag)

        // The rebuilt template emits the canonical `title` tag (per the
        // deployed nostrnests reference); a legacy `room` tag from the
        // source event MUST be dropped, not duplicated.
        val titleTag = template.tags.firstOrNull { it.firstOrNull() == "title" }?.getOrNull(1)
        assertEquals("New name", titleTag)
        assertNull(template.tags.firstOrNull { it.firstOrNull() == "room" })

        val summaryTag = template.tags.firstOrNull { it.firstOrNull() == "summary" }?.getOrNull(1)
        assertEquals("New summary", summaryTag)
    }

    @Test
    fun republishPreservesAllPromotedParticipants() {
        // Two extra speakers were promoted in the original event.
        val src =
            original(
                dTag = "rt-1",
                extraParticipants =
                    listOf(
                        arrayOf("p", speakerA, "", "speaker"),
                        arrayOf("p", speakerB, "", "speaker"),
                    ),
            )
        val newForm = form(dTag = "rt-1", roomName = "Renamed")

        val template =
            EditNestViewModel.buildEditTemplate(src, newForm, StatusTag.STATUS.LIVE)

        val pTagPubkeys = template.tags.filter { it.firstOrNull() == "p" }.map { it[1] }
        // Host plus two speakers — none can be lost on a rename.
        assertTrue("host preserved", host in pTagPubkeys)
        assertTrue("speakerA preserved", speakerA in pTagPubkeys)
        assertTrue("speakerB preserved", speakerB in pTagPubkeys)
        assertEquals("no duplicate p-tags", 3, pTagPubkeys.size)
    }

    @Test
    fun closeRoomFlipsStatusAndKeepsDTag() {
        val src = original(dTag = "rt-99")
        val sameForm = form(dTag = "rt-99", roomName = src.room().orEmpty())

        val template =
            EditNestViewModel.buildEditTemplate(src, sameForm, StatusTag.STATUS.ENDED)

        val statusTag = template.tags.firstOrNull { it.firstOrNull() == "status" }?.getOrNull(1)
        assertEquals(StatusTag.STATUS.ENDED.code, statusTag)
        val dTag = template.tags.firstOrNull { it.firstOrNull() == "d" }?.getOrNull(1)
        assertEquals("rt-99", dTag)
    }

    @Test
    fun emptySummaryAndImageOmitTags() {
        val src = original(dTag = "rt-1", roomName = "X", summary = "anything")
        val emptied = form(dTag = "rt-1", roomName = "X", summary = "  ", imageUrl = "")

        val template =
            EditNestViewModel.buildEditTemplate(src, emptied, StatusTag.STATUS.LIVE)

        // Blank inputs MUST NOT carry over from the original; otherwise
        // a "delete the summary" edit would silently fail.
        assertNull(template.tags.firstOrNull { it.firstOrNull() == "summary" })
        assertNull(template.tags.firstOrNull { it.firstOrNull() == "image" })
    }
}
