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
package com.vitorpamplona.amethyst.commons.model.privateChats

import com.vitorpamplona.quartz.nip04Dm.messages.EncryptedDmEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.videoCurationSet.VideoCurationSetEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatPreviewTest {
    private val me = "aa".repeat(32)
    private val other = "bb".repeat(32)
    private val stranger = "cc".repeat(32)
    private val someSig = "dd".repeat(64)

    private val ciphertext = "0tyoSVovKSK9uDKLUVMs137TD0b+vz+fjoJN+gG3Bk4=?iv=6r6yPQ=="

    private fun encryptedDm(
        author: String = other,
        recipient: String = me,
    ) = EncryptedDmEvent(
        id = "dm".padEnd(64, '0'),
        pubKey = author,
        createdAt = 1_000,
        tags = arrayOf(arrayOf("p", recipient)),
        content = ciphertext,
        sig = someSig,
    )

    private fun nip17Rumor() =
        ChatMessageEvent(
            id = "cm".padEnd(64, '0'),
            pubKey = other,
            createdAt = 1_000,
            tags = arrayOf(arrayOf("p", me)),
            content = "hello there",
            sig = someSig,
        )

    private fun nip17Rename(content: String = "") =
        ChatMessageEvent(
            id = "rn".padEnd(64, '0'),
            pubKey = other,
            createdAt = 1_000,
            tags = arrayOf(arrayOf("p", me), arrayOf("subject", "Weekend plans")),
            content = content,
            sig = someSig,
        )

    // ---- subject-only renames ------------------------------------------------

    @Test
    fun renameWithoutTextIsSubjectOnly() {
        assertTrue(nip17Rename().isSubjectOnlyChatMessage())
        assertTrue(nip17Rename(content = "  \n").isSubjectOnlyChatMessage())
    }

    @Test
    fun renameWithTextIsARegularMessage() {
        assertFalse(nip17Rename(content = "new goals").isSubjectOnlyChatMessage())
    }

    @Test
    fun messageWithoutSubjectIsNotARename() {
        assertFalse(nip17Rumor().isSubjectOnlyChatMessage())
    }

    @Test
    fun renamePreviewNamesTheNewSubject() {
        assertEquals(
            ChatPreview.SubjectChange("Weekend plans"),
            chatPreviewOf(nip17Rename(), decrypted = null, myPubKey = me, canDecrypt = true),
        )
    }

    @Test
    fun renameWithTextPreviewsTheText() {
        assertEquals(
            ChatPreview.Body("new goals"),
            chatPreviewOf(nip17Rename(content = "new goals"), decrypted = null, myPubKey = me, canDecrypt = true),
        )
    }

    // ---- hasEncryptedContent -------------------------------------------------

    @Test
    fun nip04DmCarriesCiphertext() {
        assertTrue(encryptedDm().hasEncryptedContent())
    }

    @Test
    fun nip17RumorIsAlreadyPlaintext() {
        assertFalse(nip17Rumor().hasEncryptedContent())
    }

    // A NIP-51 list keeps its private members as a NIP-44 payload in `content`. This is the real
    // wire content of a divine.video kind-30005 video list, which used to render on screen as the
    // base64 blob it is.
    private val nip44ListContent =
        "AqQxhO1d0DzuJQtkWdshs3Fvi/1OXp2sYl9rDAykMBOvD06Tg0DLP69/i03iA1SD/RHVp+TEUoG8k+0FUkr7fiEcSyWx9k37o5URbcGsESdMK9d5vAFm9Yjp5tatUuZa2gJG"

    private fun videoList(content: String = nip44ListContent) =
        VideoCurationSetEvent(
            id = "vl".padEnd(64, '0'),
            pubKey = other,
            createdAt = 1_000,
            tags = arrayOf(arrayOf("d", "my_vine_list"), arrayOf("title", "My List")),
            content = content,
            sig = someSig,
        )

    private fun muteList(content: String = nip44ListContent) =
        MuteListEvent(
            id = "ml".padEnd(64, '0'),
            pubKey = other,
            createdAt = 1_000,
            tags = arrayOf(arrayOf("p", stranger)),
            content = content,
            sig = someSig,
        )

    @Test
    fun anAddressableListWithPrivateMembersCarriesCiphertext() {
        assertTrue(videoList().hasEncryptedContent())
    }

    @Test
    fun aStandardListWithPrivateMembersCarriesCiphertext() {
        assertTrue(muteList().hasEncryptedContent())
    }

    @Test
    fun anAllPublicListHasNothingToHide() {
        // No private members means no payload, and an empty string is not ciphertext — saying it
        // was would hide a perfectly readable list behind a "private" label.
        assertFalse(videoList(content = "").hasEncryptedContent())
        assertFalse(muteList(content = "").hasEncryptedContent())
    }

    // ---- chatPreviewOf -------------------------------------------------------

    @Test
    fun plaintextMessageRendersItsContent() {
        assertEquals(
            ChatPreview.Body("hello there"),
            chatPreviewOf(nip17Rumor(), decrypted = null, myPubKey = me, canDecrypt = true),
        )
    }

    @Test
    fun decryptedDmRendersThePlaintext() {
        assertEquals(
            ChatPreview.Body("hi!"),
            chatPreviewOf(encryptedDm(), decrypted = "hi!", myPubKey = me, canDecrypt = true),
        )
    }

    /** The regression under test: the raw base64 blob must never become the preview. */
    @Test
    fun pendingDmNeverFallsThroughToCiphertext() {
        val preview = chatPreviewOf(encryptedDm(), decrypted = null, myPubKey = me, canDecrypt = true)

        assertEquals(ChatPreview.Decrypting, preview)
        assertFalse(preview is ChatPreview.Body)
    }

    @Test
    fun readOnlyAccountCannotEverDecrypt() {
        assertEquals(
            ChatPreview.Undecryptable,
            chatPreviewOf(encryptedDm(), decrypted = null, myPubKey = me, canDecrypt = false),
        )
    }

    @Test
    fun dmBetweenOtherPeopleIsUndecryptableNotPending() {
        assertEquals(
            ChatPreview.Undecryptable,
            chatPreviewOf(
                encryptedDm(author = other, recipient = stranger),
                decrypted = null,
                myPubKey = me,
                canDecrypt = true,
            ),
        )
    }

    @Test
    fun dmIAuthoredIsStillDecryptableByMe() {
        assertEquals(
            ChatPreview.Decrypting,
            chatPreviewOf(
                encryptedDm(author = me, recipient = other),
                decrypted = null,
                myPubKey = me,
                canDecrypt = true,
            ),
        )
    }

    @Test
    fun missingEventReportsMissing() {
        assertEquals(
            ChatPreview.Missing,
            chatPreviewOf(null, decrypted = null, myPubKey = me, canDecrypt = true),
        )
    }
}
