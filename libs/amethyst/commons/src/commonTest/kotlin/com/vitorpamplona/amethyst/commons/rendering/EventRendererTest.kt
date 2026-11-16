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
package com.vitorpamplona.amethyst.commons.rendering

import com.vitorpamplona.amethyst.commons.actions.ReplyActions
import com.vitorpamplona.amethyst.commons.rendering.json.JsonEventFormatter
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.metadata.UserMetadata
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventRendererTest {
    private val alice = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000007".hexToByteArray()))
    private val bob = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000008".hexToByteArray()))

    private suspend fun raw(
        kind: Int,
        content: String,
        vararg tags: Array<String>,
    ): Event = alice.sign(1_700_000_000, kind, arrayOf(*tags), content)

    @Test
    fun textNoteBodyIsSplitIntoSpans() =
        runTest {
            val bobNpub = NPub.create(bob.pubKey)
            val note = alice.sign(TextNoteEvent.build("Hello #nostr see https://example.com/page and nostr:$bobNpub"))
            val rendered = EventRendererRegistry.render(note)

            assertEquals(TextNoteEvent.KIND, rendered.kind)
            assertEquals(note.id, rendered.eventId)
            assertNotNull(rendered.kindName)
            assertTrue(rendered.body.any { it is BodySpan.Hashtag && it.hashtag == "nostr" }, rendered.body.toString())
            assertTrue(rendered.body.any { it is BodySpan.Link && it.url == "https://example.com/page" }, rendered.body.toString())
            assertTrue(rendered.body.any { it is BodySpan.UserMention && it.pubKey == bob.pubKey }, rendered.body.toString())
            assertTrue(bob.pubKey in rendered.mentions)
            assertTrue("nostr" in rendered.hashtags)
            assertNull(rendered.replyTo)
            assertEquals(note.content, rendered.body.joinToString("") { it.text })
        }

    @Test
    fun imageUrlsBecomeMedia() =
        runTest {
            val note = alice.sign(TextNoteEvent.build("pic https://example.com/cat.jpg"))
            val rendered = EventRendererRegistry.render(note)
            assertEquals(listOf("https://example.com/cat.jpg"), rendered.media.map { it.url })
            assertEquals("image", rendered.media.single().type)
        }

    @Test
    fun replyCarriesReplyAndRootRefs() =
        runTest {
            val root = alice.sign(TextNoteEvent.build("root"))
            val mid = ReplyActions.replyTo(EventHintBundle(root), "mid", bob)
            val leaf = ReplyActions.replyTo(EventHintBundle(mid), "leaf", alice)

            val renderedMid = EventRendererRegistry.render(mid)
            assertEquals(root.id, renderedMid.replyTo?.eventId)
            assertEquals(root.id, renderedMid.root?.eventId)

            val renderedLeaf = EventRendererRegistry.render(leaf)
            assertEquals(mid.id, renderedLeaf.replyTo?.eventId)
            assertEquals(root.id, renderedLeaf.root?.eventId)
        }

    @Test
    fun authorIsEnrichedFromTheContext() =
        runTest {
            val note = alice.sign(TextNoteEvent.build("hi"))
            val metadata = UserMetadata().apply { name = "alice" }
            val rendered = EventRendererRegistry.render(note, RenderContext(mapOf(alice.pubKey to metadata)))
            assertEquals("alice", rendered.author.name)
            assertEquals("alice", rendered.author.bestName())
            assertEquals(NPub.create(alice.pubKey), rendered.author.npub)
        }

    @Test
    fun reactionsAreTyped() =
        runTest {
            val note = bob.sign(TextNoteEvent.build("hi"))
            val like = alice.sign(ReactionEvent.build("+", EventHintBundle(note)))
            val emoji = alice.sign(ReactionEvent.build("🤙", EventHintBundle(note)))

            val renderedLike = EventRendererRegistry.render(like)
            assertEquals(note.id, renderedLike.replyTo?.eventId)
            assertEquals("like", assertIs<RenderedDetails.Reaction>(renderedLike.details).type)
            assertEquals("", renderedLike.text)

            assertEquals("emoji", assertIs<RenderedDetails.Reaction>(EventRendererRegistry.render(emoji).details).type)
        }

    @Test
    fun repostEmbedsOnlyAGenuineCopy() =
        runTest {
            val note = bob.sign(TextNoteEvent.build("boost me"))
            val repost = alice.sign(RepostEvent.build(EventHintBundle(note)))

            val details = assertIs<RenderedDetails.Repost>(EventRendererRegistry.render(repost).details)
            assertEquals(note.id, details.target?.eventId)
            assertEquals("boost me", details.embedded?.text)

            // Same tags, but the embedded JSON was tampered with: the copy is dropped.
            val forgedJson = note.toJson().replace("boost me", "forged!!")
            val forged = alice.sign<Event>(repost.createdAt, RepostEvent.KIND, repost.tags, forgedJson)
            val forgedDetails = assertIs<RenderedDetails.Repost>(EventRendererRegistry.render(forged).details)
            assertEquals(note.id, forgedDetails.target?.eventId)
            assertNull(forgedDetails.embedded)
        }

    @Test
    fun profileContactAndRelayListsHaveDetails() =
        runTest {
            val profile = raw(0, """{"name":"alice","about":"hi there","nip05":"alice@example.com"}""")
            val renderedProfile = EventRendererRegistry.render(profile)
            assertEquals("alice", renderedProfile.title)
            assertEquals("alice@example.com", assertIs<RenderedDetails.Profile>(renderedProfile.details).nip05)
            assertTrue(renderedProfile.body.isEmpty(), "profile JSON is not prose")

            val contacts = raw(3, "", arrayOf("p", bob.pubKey), arrayOf("p", "not-a-key"))
            assertEquals(listOf(bob.pubKey), assertIs<RenderedDetails.ContactList>(EventRendererRegistry.render(contacts).details).follows)

            val relays = raw(10002, "", arrayOf("r", "wss://a.example.com"), arrayOf("r", "wss://b.example.com", "read"))
            val entries = assertIs<RenderedDetails.RelayList>(EventRendererRegistry.render(relays).details).relays
            assertEquals(listOf(true, true), entries.map { it.read })
            assertEquals(listOf(true, false), entries.map { it.write })
        }

    @Test
    fun deletionListsItsTargets() =
        runTest {
            val deletion = raw(5, "oops", arrayOf("e", "a".repeat(64)), arrayOf("k", "1"))
            val details = assertIs<RenderedDetails.Deletion>(EventRendererRegistry.render(deletion).details)
            assertEquals(listOf("a".repeat(64)), details.eventIds)
            assertEquals("oops", details.reason)
        }

    @Test
    fun commentCarriesNip22ParentAndRoot() =
        runTest {
            val article = raw(30023, "body", arrayOf("d", "slug"), arrayOf("title", "My Article"))
            val renderedArticle = EventRendererRegistry.render(article)
            assertEquals("My Article", renderedArticle.title)
            assertEquals("30023:${alice.pubKey}:slug", renderedArticle.address)

            val comment = ReplyActions.reply(EventHintBundle(article), "nice", bob)
            val renderedComment = EventRendererRegistry.render(comment)
            assertEquals(article.id, renderedComment.replyTo?.eventId)
            assertEquals(30023, renderedComment.root?.kind)
            assertEquals(article.id, renderedComment.root?.eventId)
        }

    @Test
    fun unknownKindsStillRender() =
        runTest {
            val odd = raw(31999, "whatever #Tag", arrayOf("p", bob.pubKey), arrayOf("t", "TAG"))
            val rendered = EventRendererRegistry.render(odd)
            assertNull(rendered.details)
            assertEquals(listOf(bob.pubKey), rendered.mentions)
            assertEquals(listOf("tag"), rendered.hashtags)
        }

    @Test
    fun jsonFormatterUsesStableKeys() =
        runTest {
            val note = alice.sign(TextNoteEvent.build("hi #there"))
            val map = JsonEventFormatter.toMap(EventRendererRegistry.render(note))
            assertEquals(note.id, map["event_id"])
            assertEquals(1, map["kind"])
            assertEquals(alice.pubKey, (map["author"] as Map<*, *>)["pubkey"])
            assertEquals(listOf("there"), map["hashtags"])
            assertTrue((map["body"] as List<*>).isNotEmpty())
            assertNull(JsonEventFormatter.toMap(EventRendererRegistry.render(note), includeBody = false)["body"])
        }

    @Test
    fun malformedPubkeysNeverThrow() =
        runTest {
            val bad = TextNoteEvent("a".repeat(64), "abc", 1_700_000_000, emptyArray(), "hi", "0".repeat(128))
            val rendered = EventRendererRegistry.render(bad)
            assertEquals("", rendered.author.npub)
            assertEquals("abc…", rendered.author.bestName())
        }

    @Test
    fun indexMentionOfAnATagIsAnAddressRef() =
        runTest {
            val address = "30023:${alice.pubKey}:slug"
            val note = raw(1, "see #[0]", arrayOf("a", address))
            val mention =
                EventRendererRegistry
                    .render(note)
                    .body
                    .filterIsInstance<BodySpan.EventMention>()
                    .single()
            assertNull(mention.ref.eventId)
            assertEquals(address, mention.ref.address)
        }

    @Test
    fun nip10PrecedenceMatchesQuartz() =
        runTest {
            val r = "1".repeat(64)
            val q = "2".repeat(64)
            val p = bob.pubKey
            // A root marker beats an unmarked e tag, as quartz's replyingTo() has it.
            val rootAndUnmarked = raw(1, "x", arrayOf("e", r, "", "root"), arrayOf("e", q))
            assertEquals(r, EventRendererRegistry.render(rootAndUnmarked).replyTo?.eventId)

            // Positional tags with an empty marker or a pubkey in slot 3 are still replies.
            val emptyMarker = raw(1, "x", arrayOf("e", r, "wss://r.example.com", "", p))
            assertEquals(r, EventRendererRegistry.render(emptyMarker).replyTo?.eventId)
            val pubkeyInSlot3 = raw(1, "x", arrayOf("e", r, "wss://r.example.com", p))
            assertEquals(r, EventRendererRegistry.render(pubkeyInSlot3).replyTo?.eventId)

            // Not an id: no reply ref.
            assertNull(EventRendererRegistry.render(raw(1, "x", arrayOf("e", "not-an-id"))).replyTo)
        }

    @Test
    fun addressOnlyRepostDoesNotEmbedAnUnrelatedEvent() =
        runTest {
            val unrelated = bob.sign(TextNoteEvent.build("unrelated"))
            val repost = raw(16, unrelated.toJson(), arrayOf("a", "30023:${bob.pubKey}:slug"), arrayOf("k", "30023"))
            val details = assertIs<RenderedDetails.Repost>(EventRendererRegistry.render(repost).details)
            assertEquals("30023:${bob.pubKey}:slug", details.target?.address)
            assertNull(details.embedded)

            val untargeted = raw(6, unrelated.toJson())
            assertNull(assertIs<RenderedDetails.Repost>(EventRendererRegistry.render(untargeted).details).embedded)
        }

    @Test
    fun commentRefsCarryAddressAndExternalIds() =
        runTest {
            val article = raw(30023, "body", arrayOf("d", "slug"))
            val comment = ReplyActions.reply(EventHintBundle(article), "nice", bob)
            val reply = EventRendererRegistry.render(comment).replyTo
            assertEquals(article.id, reply?.eventId)
            assertEquals("30023:${alice.pubKey}:slug", reply?.address)

            val onUrl = raw(1111, "hi", arrayOf("I", "https://example.com/"), arrayOf("K", "web"), arrayOf("i", "https://example.com/"), arrayOf("k", "web"))
            val rendered = EventRendererRegistry.render(onUrl)
            assertEquals("https://example.com/", rendered.root?.external)
            assertEquals("https://example.com/", rendered.replyTo?.external)
        }

    @Test
    fun reactionToAnAddressOnlyHasATarget() =
        runTest {
            val like = raw(7, "+", arrayOf("a", "30023:${bob.pubKey}:slug"))
            assertEquals("30023:${bob.pubKey}:slug", EventRendererRegistry.render(like).replyTo?.address)
        }

    @Test
    fun emojiWithPunctuationAndPdfLinksAreKept() =
        runTest {
            val note = raw(1, "hi :wave:, read https://example.com/doc.pdf", arrayOf("emoji", "wave", "https://example.com/wave.png"))
            val rendered = EventRendererRegistry.render(note)
            assertEquals(
                "https://example.com/wave.png",
                rendered.body
                    .filterIsInstance<BodySpan.CustomEmoji>()
                    .single()
                    .url,
            )
            assertTrue(rendered.body.any { it is BodySpan.Link && it.url == "https://example.com/doc.pdf" }, rendered.body.toString())
            assertEquals("file", rendered.media.single { it.url.endsWith(".pdf") }.type)
        }

    @Test
    fun aSpoofedZapRequestDoesNotNameTheZapper() =
        runTest {
            val provider = NostrSignerInternal(KeyPair("000000000000000000000000000000000000000000000000000000000000000a".hexToByteArray()))
            val request = alice.sign<Event>(1_700_000_000, 9734, arrayOf(arrayOf("p", bob.pubKey)), "great post")
            val receipt = provider.sign<Event>(1_700_000_001, 9735, arrayOf(arrayOf("p", bob.pubKey), arrayOf("description", request.toJson())), "")
            val proven = assertIs<RenderedDetails.Zap>(EventRendererRegistry.render(receipt).details)
            assertEquals(alice.pubKey, proven.sender)
            assertEquals("great post", proven.comment)

            val forged = request.toJson().replace("great post", "from jack")
            val spoofed = provider.sign<Event>(1_700_000_002, 9735, arrayOf(arrayOf("p", bob.pubKey), arrayOf("description", forged)), "")
            val details = assertIs<RenderedDetails.Zap>(EventRendererRegistry.render(spoofed).details)
            assertNull(details.sender)
            assertNull(details.comment)
        }
}
