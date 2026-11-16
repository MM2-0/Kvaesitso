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
package com.vitorpamplona.amethyst.commons.actions

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplit
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReplyActionsTest {
    private val alicePriv = "0000000000000000000000000000000000000000000000000000000000000007"
    private val aliceSigner = NostrSignerInternal(KeyPair(alicePriv.hexToByteArray()))

    private val bobPriv = "0000000000000000000000000000000000000000000000000000000000000008"
    private val bobSigner = NostrSignerInternal(KeyPair(bobPriv.hexToByteArray()))

    @Test
    fun replyToTopLevelParent_setsRootToParentAndCarriesAuthor() =
        runTest {
            // Alice posts a top-level note (no e-tags = parent IS its own root).
            val parent = aliceSigner.sign(TextNoteEvent.build("hello"))
            assertTrue(parent.isNewThread(), "parent must be a fresh thread for this case")

            // Bob replies.
            val reply = ReplyActions.replyTo(EventHintBundle(parent, null), "hi alice", bobSigner)

            assertEquals(TextNoteEvent.KIND, reply.kind)
            assertEquals(bobSigner.pubKey, reply.pubKey)

            // Per `prepareETagsAsReplyTo`: when parent has no root, only a ROOT
            // marker is emitted (it doubles as the reply target). No separate
            // REPLY marker. `markedReplyTos()` should still resolve to parent.id.
            val root = reply.markedRoot()
            assertNotNull(root, "reply must carry a NIP-10 root marker")
            assertEquals(parent.id, root.eventId, "root marker must point at the top-level parent")

            // p-tag carry must include the parent's author so they're notified.
            val pubKeys = reply.tags.mapNotNull(PTag::parseKey)
            assertTrue(parent.pubKey in pubKeys, "reply must carry the parent's pubkey in p-tags")
        }

    @Test
    fun replyToDeepThread_carriesRootForwardAndChainsPTags() =
        runTest {
            // Build A (root) → B (alice's reply to A) → C (carol's reply to B).
            val a = aliceSigner.sign(TextNoteEvent.build("the original"))

            val carolPriv = "0000000000000000000000000000000000000000000000000000000000000009"
            val carolSigner = NostrSignerInternal(KeyPair(carolPriv.hexToByteArray()))

            val b = ReplyActions.replyTo(EventHintBundle(a, null), "good point", aliceSigner)

            // C replies to B — must carry A as root (not B), and reply to B.
            val c = ReplyActions.replyTo(EventHintBundle(b, null), "agreed", carolSigner)

            val rootC = c.markedRoot()
            assertNotNull(rootC, "deep reply must carry root marker")
            assertEquals(a.id, rootC.eventId, "deep reply's root must chain through to original")

            val replyC = c.markedReply()
            assertNotNull(replyC, "deep reply must carry reply marker")
            assertEquals(b.id, replyC.eventId, "deep reply's reply marker must point at immediate parent")

            // p-tag chain: must include both alice (root author / parent author) and parent.pubKey.
            val pubKeys = c.tags.mapNotNull(PTag::parseKey).toSet()
            assertTrue(aliceSigner.pubKey in pubKeys, "deep reply must carry root author in p-tags")
        }

    @Test
    fun replyEvent_isSignedAndKind1() =
        runTest {
            val parent = aliceSigner.sign(TextNoteEvent.build("seed"))
            val reply = ReplyActions.replyTo(EventHintBundle(parent, null), "thanks", bobSigner)

            assertEquals(TextNoteEvent.KIND, reply.kind)
            assertTrue(reply.id.length == 64, "reply id must be a 32-byte hex")
            assertTrue(reply.sig.length == 128, "reply must be signed (64-byte sig hex)")
            assertEquals("thanks", reply.content)
        }

    @Test
    fun replyDoesNotNotifyZapSplitBeneficiaries() =
        runTest {
            val beneficiary = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
            val parent =
                aliceSigner.sign(
                    TextNoteEvent.build("forwarding my zaps") {
                        zapSplit(ZapSplitSetup(beneficiary, null, 1.0))
                    },
                )
            // The split beneficiary is a linked key of the parent (for relay hints)...
            assertTrue(beneficiary in parent.linkedPubKeys())

            val reply = ReplyActions.replyTo(EventHintBundle(parent, null), "nice", bobSigner)

            // ...but not a participant in the thread, so the reply must not `p`-tag them.
            val pubKeys = reply.tags.mapNotNull(PTag::parseKey)
            assertFalse(beneficiary in pubKeys, "reply must not notify the parent's zap-split beneficiaries")
            assertTrue(parent.pubKey in pubKeys)
        }
}
