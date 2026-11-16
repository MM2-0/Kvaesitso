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
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * An admin's removal (kind 4891) acts inside the admin's own group. Any member who creates a
 * group is its admin, so a removal that reached another group's messages, or one group's record
 * that overwrote another's, would let anyone delete what others read elsewhere.
 */
class MarmotModerationScopeTest {
    private val signer = NostrSignerSync(KeyPair())
    private val attackerGroup = "a".repeat(64)
    private val victimGroup = "b".repeat(64)

    private fun rumor(content: String): ChatEvent {
        val signed = signer.sign<ChatEvent>(1_000, ChatEvent.KIND, emptyArray(), content)
        return ChatEvent(signed.id, signed.pubKey, signed.createdAt, signed.tags, signed.content, "")
    }

    @Test
    fun aRemovalInOneGroupLeavesAnotherGroupsMessage() {
        val cache = EventCache()
        val groups = MarmotGroupList(signer.pubKey)
        val message = AccountMarmotActions.indexMarmotInnerEvent(cache, rumor("in the victim's group")).note
        groups.addMessage(victimGroup, message)
        groups.getOrCreateGroup(attackerGroup)

        assertSame(message, AccountMarmotActions.groupMessageToRemove(groups, victimGroup, message))
        assertNull(AccountMarmotActions.groupMessageToRemove(groups, attackerGroup, message))

        groups.applyAdminRemoval(attackerGroup, message.idHex, message)

        assertTrue(
            groups.rooms
                .get(victimGroup)!!
                .messages
                .contains(message),
        )
        assertFalse(groups.isAdminRemoved(victimGroup, message.idHex))
    }

    @Test
    fun aRemovalRecordedInOneGroupDoesNotOverwriteAnothers() {
        val groups = MarmotGroupList(signer.pubKey)
        val targetId = "c".repeat(64)

        groups.applyAdminRemoval(victimGroup, targetId, null)
        groups.applyAdminRemoval(attackerGroup, targetId, null)

        assertTrue(groups.isAdminRemoved(victimGroup, targetId))
        assertTrue(groups.isAdminRemoved(attackerGroup, targetId))

        groups.removeGroup(attackerGroup)
        assertFalse(groups.isAdminRemoved(attackerGroup, targetId))
        assertTrue(groups.isAdminRemoved(victimGroup, targetId))
    }
}
