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
package com.vitorpamplona.amethyst.commons.ui.text

import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Swapping one mention for another: the author selects a mention chip and pastes a different
 * profile over it. [MentionPreservingInputTransformation] used to treat that like an IME
 * rewriting the chip and deleted the mention, pasted text and all.
 */
class MentionSwapTest {
    private val oldMention = "nostr:npub1fek9zhdjretyf5lhj068px49ncjk96x5vvf73euzumy79rjxfausxc58k8"
    private val newMention = "nostr:npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z"
    private val text = "welcome $oldMention drummer"
    private val start = text.indexOf(oldMention)
    private val end = start + oldMention.length

    private fun applyChange(stage: TextFieldBuffer.() -> Unit): String {
        val state = TextFieldState(text)
        state.edit {
            stage()
            with(MentionPreservingInputTransformation) { transformInput() }
        }
        return state.text.toString()
    }

    @Test
    fun pastingAnotherMentionOverAChip_keepsTheNewMention() {
        assertEquals("welcome $newMention drummer", applyChange { replace(start, end, newMention) })
    }

    @Test
    fun pastingThroughTheKeyboard_keepsTheNewMention() {
        // The IME path trims the prefix both share, so the change starts inside the old mention.
        val shared = "nostr:npub1".length
        val result = applyChange { replace(start + shared, end, newMention.substring(shared)) }
        assertEquals("welcome $newMention drummer", result)
    }

    @Test
    fun pastingABareNpubOverAChip_keepsIt() {
        val bare = newMention.removePrefix("nostr:")
        assertEquals("welcome $bare drummer", applyChange { replace(start, end, bare) })
    }

    @Test
    fun pastingAnNprofileOverAChip_keepsIt() {
        val nprofile = NProfile.createNostrUri("aa9047325603dacd4f8142093567973566de3b1e20a89557b728c3be4c6a844b", emptyList())
        assertEquals("welcome $nprofile drummer", applyChange { replace(start, end, nprofile) })
    }

    @Test
    fun rewritingAChipWithPlainText_stillCollapsesIt() {
        // The SwiftKey case the guard exists for: the IME rewrites the chip with one word of its
        // display name.
        assertEquals("welcome drummer", applyChange { replace(start, end, "@John") })
    }

    @Test
    fun deletingPartOfAChip_stillCollapsesIt() {
        assertEquals("welcome drummer", applyChange { replace(end - 4, end, "") })
    }

    @Test
    fun corruptingAnNprofileChip_stillCollapsesIt() {
        // nprofile has no fixed length, so the regex alone still matches what is left; the
        // checksum is what tells the swap apart.
        val nprofile = NProfile.createNostrUri("aa9047325603dacd4f8142093567973566de3b1e20a89557b728c3be4c6a844b", emptyList())
        val state = TextFieldState("welcome $nprofile drummer")
        val mentionEnd = "welcome ".length + nprofile.length
        state.edit {
            replace(mentionEnd - 3, mentionEnd, "")
            with(MentionPreservingInputTransformation) { transformInput() }
        }
        assertEquals("welcome drummer", state.text.toString())
    }
}
