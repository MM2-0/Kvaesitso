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
package com.vitorpamplona.amethyst.commons.model.marmotGroups

import com.vitorpamplona.amethyst.commons.model.AccountMarmotActions
import com.vitorpamplona.amethyst.commons.model.cache.EventCache
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * A Marmot message deleted with a kind:5 stays deleted when its kind:445 is decrypted again.
 * Relays replay a group's recent kind:445s after a restart, and the replayed copy used to be
 * loaded onto a fresh note even though the cache refused it, so the message came back.
 */
class MarmotDeletedMessageReplayTest {
    private val signer = NostrSignerSync(KeyPair())

    @Test
    fun aDeletedMessageDecryptedAgainIsNotLoaded() {
        val cache = EventCache()
        val message = signer.sign<ChatEvent>(1_000, ChatEvent.KIND, emptyArray(), "this one gets deleted")
        val deletion = signer.sign<DeletionRequestEvent>(1_001, DeletionRequestEvent.KIND, arrayOf(arrayOf("e", message.id)), "")

        assertNotNull(AccountMarmotActions.indexMarmotInnerEvent(cache, message).note.event)
        AccountMarmotActions.indexMarmotInnerEvent(cache, deletion)
        assertNull(cache.getNoteIfExists(message.id)?.event)

        val replay = AccountMarmotActions.indexMarmotInnerEvent(cache, message)

        assertNull(replay.note.event)
        assertFalse(replay.isNew)
    }
}
