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
package com.vitorpamplona.amethyst.chats

import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Turning NIP-17 off takes its rumors out of the rooms, and the cache only holds notes weakly, so a
 * rumor can be collected while its wrap stays cached. A wrap still cached is skipped on arrival, so
 * the re-fetch after turning NIP-17 back on would never unwrap it again. The unload therefore drops
 * the wrap and seal too, keeping the rumor for the re-index of what the cache still holds.
 */
class ForgetGiftWrapsTest {
    @Test
    fun forgetsTheWrapAndSealButKeepsTheRumor() {
        val me = "c1".repeat(32)
        val rumor = ChatMessageEvent("c2".repeat(32), "c3".repeat(32), 200, arrayOf(arrayOf("p", me)), "hi", "")
        val seal = SealEvent("c4".repeat(32), "c3".repeat(32), 200, emptyArray(), "x", "dd".repeat(64))
        val wrap = GiftWrapEvent("c5".repeat(32), "c6".repeat(32), 210, arrayOf(arrayOf("p", me)), "x", "dd".repeat(64))
        wrap.innerEventId = seal.id

        val wrapNote = LocalCache.getOrCreateNote(wrap.id).apply { event = wrap }
        val sealNote = LocalCache.getOrCreateNote(seal.id).apply { event = seal }
        val rumorNote = LocalCache.getOrCreateNote(rumor.id).apply { event = rumor }
        rumorNote.recordRumorHost(wrap)

        LocalCache.pruner.forgetGiftWraps(listOf(rumorNote))

        assertNull("the wrap must leave the cache so a re-fetch unwraps it again", LocalCache.getNoteIfExists(wrap.id))
        assertNull("the seal must leave with it", LocalCache.getNoteIfExists(seal.id))
        assertNotNull("the rumor stays for the re-index", LocalCache.getNoteIfExists(rumor.id))
        assertNull(rumorNote.rumorHost)
        // Keep both alive past the asserts so a GC can't pass this for the wrong reason.
        assertNotNull(wrapNote.event)
        assertNotNull(sealNote.event)
    }
}
