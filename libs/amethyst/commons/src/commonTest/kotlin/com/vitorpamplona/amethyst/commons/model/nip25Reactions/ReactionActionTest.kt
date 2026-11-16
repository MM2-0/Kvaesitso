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
package com.vitorpamplona.amethyst.commons.model.nip25Reactions

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTags
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip29RelayGroups.hTag
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class ReactionActionTest {
    private val alicePriv = "0000000000000000000000000000000000000000000000000000000000000007"
    private val aliceSigner = NostrSignerInternal(KeyPair(alicePriv.hexToByteArray()))

    private val bobPriv = "0000000000000000000000000000000000000000000000000000000000000008"
    private val bobSigner = NostrSignerInternal(KeyPair(bobPriv.hexToByteArray()))

    private val carolPriv = "0000000000000000000000000000000000000000000000000000000000000009"
    private val carolSigner = NostrSignerInternal(KeyPair(carolPriv.hexToByteArray()))

    @Test
    fun reactionToPublicNote_isPublic() =
        runTest {
            val note = aliceSigner.sign(TextNoteEvent.build("hello world"))

            var publicCalls = 0
            ReactionAction.reactToWithGroupSupport(
                eventHint = EventHintBundle(note, null),
                reaction = "+",
                signer = bobSigner,
                onPublic = { reaction ->
                    publicCalls++
                    assertTrue(reaction.sig.isNotEmpty(), "public reaction must be signed")
                    assertTrue(reaction.tags.any { it.size >= 2 && it[0] == "e" && it[1] == note.id })
                    assertFalse(
                        reaction.tags.any { it.isNotEmpty() && it[0] == "h" },
                        "a reaction to a non-group note must not carry an `h` tag",
                    )
                },
                onPrivate = { fail("reaction to a public note must not be gift-wrapped") },
            )
            assertEquals(1, publicCalls)
        }

    @Test
    fun reactionToRelayGroupMessage_carriesTheGroupHTag() =
        runTest {
            // A NIP-29 group chat message: a kind-9 ChatEvent scoped by `h`.
            val groupId = "abcd1234"
            val groupMessage = aliceSigner.sign(ChatEvent.build("gm") { hTag(groupId) })

            var publicCalls = 0
            ReactionAction.reactToWithGroupSupport(
                eventHint = EventHintBundle(groupMessage, null),
                reaction = "+",
                signer = bobSigner,
                onPublic = { reaction ->
                    publicCalls++
                    // Standard NIP-25 targeting …
                    assertTrue(reaction.tags.any { it.size >= 2 && it[0] == "e" && it[1] == groupMessage.id })
                    assertTrue(reaction.tags.any { it.size >= 2 && it[0] == "p" && it[1] == aliceSigner.pubKey })
                    // … plus the group `h` tag copied from the target, so the like
                    // stays in the group and the recipient's `#p`+`#h` host-relay
                    // notification query can match it.
                    assertTrue(
                        reaction.tags.any { it.size >= 2 && it[0] == "h" && it[1] == groupId },
                        "a reaction to a NIP-29 group message must copy the group's `h` tag",
                    )
                },
                onPrivate = { fail("a public group message reaction must not be gift-wrapped") },
            )
            assertEquals(1, publicCalls)
        }

    @Test
    fun reactionToUnsealedRumor_isGiftWrappedToAllParticipants() =
        runTest {
            // Alice's private reply (rumor) tagging Bob and Carol. Receivers
            // materialize rumors with an empty signature.
            val signed =
                aliceSigner.sign(
                    TextNoteEvent.build("private reply") {
                        pTags(
                            listOf(
                                PTag(bobSigner.pubKey, null),
                                PTag(carolSigner.pubKey, null),
                            ),
                        )
                    },
                )
            val rumor = TextNoteEvent(signed.id, signed.pubKey, signed.createdAt, signed.tags, signed.content, "")

            // Bob reacts: the reaction must be wrapped to every participant
            // (Alice the author, Carol the other p-tag, and Bob's self-copy)
            // and never reach the public callback.
            var privateCalls = 0
            ReactionAction.reactToWithGroupSupport(
                eventHint = EventHintBundle(rumor, null),
                reaction = "+",
                signer = bobSigner,
                onPublic = { fail("reaction to a rumor must not be public: its e-tag would leak the rumor id") },
                onPrivate = { result ->
                    privateCalls++
                    assertTrue(result.msg.tags.any { it.size >= 2 && it[0] == "e" && it[1] == rumor.id })

                    val recipients = result.wraps.mapNotNull { it.recipientPubKey() }.toSet()
                    assertEquals(
                        setOf(aliceSigner.pubKey, bobSigner.pubKey, carolSigner.pubKey),
                        recipients,
                        "wraps must cover the rumor author, every tagged user, and the sender's self-copy",
                    )
                },
            )
            assertEquals(1, privateCalls)
        }
}
