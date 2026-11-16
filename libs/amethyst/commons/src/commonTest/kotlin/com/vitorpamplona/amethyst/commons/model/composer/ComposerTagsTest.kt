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
package com.vitorpamplona.amethyst.commons.model.composer

import com.vitorpamplona.amethyst.commons.model.cache.EventCache
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.quotes.QTag
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The composer's text tagging as amy runs it: [NewMessageTagger] over a plain
 * [EventCache], then [messageTags] / [pTagsWithHints] / [quoteMessage].
 */
class ComposerTagsTest {
    private val alice = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000007".hexToByteArray()))
    private val bob = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000008".hexToByteArray()))
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.example.com")!!

    @Test
    fun messageTagsCoverProfilesEventsHashtagsAndLinks() =
        runTest {
            val cited = alice.sign(TextNoteEvent.build("cited"))
            val npub = NPub.create(alice.pubKey)
            val text = "hey nostr:$npub and again nostr:$npub, see nostr:${NEvent.create(cited.id, null, null, relay)} #Nostr https://example.com/x"
            val note = bob.sign(TextNoteEvent.build(text) { messageTags(text) })

            assertEquals(listOf(alice.pubKey), note.tags.mapNotNull(PTag::parseKey))
            assertEquals(listOf(cited.id), note.tags.mapNotNull(QTag::parseEventId))
            assertEquals(setOf("nostr"), note.hashtags().mapTo(mutableSetOf()) { it.lowercase() })
            assertTrue(note.tags.any { it[0] == "r" && it[1] == "https://example.com/x" })
        }

    @Test
    fun taggerRewritesBareKeysAndCollectsAuthors() =
        runTest {
            val cache = EventCache()
            val cited = alice.sign(TextNoteEvent.build("cited"))
            cache.justConsume(cited, null, true)
            cache.getNoteIfExists(cited.id)!!.addRelay(relay)

            val bobNpub = NPub.create(bob.pubKey)
            val tagger = NewMessageTagger(message = "hi @$bobNpub", dao = cache)
            tagger.run()
            assertTrue(tagger.message.contains("nostr:nprofile1"), tagger.message)

            val withNote = NewMessageTagger(message = "see nostr:${NEvent.create(cited.id, null, null, null)}", dao = cache)
            withNote.run()
            // The cited note's author is notified, and the rewritten link carries the relay we saw it on.
            assertEquals(listOf(alice.pubKey), withNote.pTagsWithHints(cache.relayHints)?.map { it.pubKey })
            val rewritten = assertIs<NEvent>(Nip19Parser.uriToRoute(withNote.message.substringAfter("nostr:"))?.entity)
            assertEquals(listOf(relay), rewritten.relay)
        }

    @Test
    fun quoteTextIsTheComposersPrefill() =
        runTest {
            val cache = EventCache()
            val target = alice.sign(TextNoteEvent.build("quotable"))
            cache.justConsume(target, null, true)
            val message = quoteMessage("look at this", cache.getNoteIfExists(target.id)!!)

            assertTrue(message.startsWith("look at this\nnostr:nevent1"), message)
            val quote = bob.sign(TextNoteEvent.build(message) { messageTags(message) })
            assertEquals(listOf(target.id), quote.tags.mapNotNull(QTag::parseEventId))
        }

    @Test
    fun quotingAnAddressableNoteUsesItsCoordinate() =
        runTest {
            val cache = EventCache()
            val article: Event =
                alice.sign(1_700_000_000, LongFormContentEvent.KIND, arrayOf(arrayOf("d", "slug"), arrayOf("title", "T")), "body")
            cache.justConsume(article, null, true)
            val note = cache.getAddressableNoteIfExists((article as LongFormContentEvent).address())!!
            val message = quoteMessage("", note)

            val naddr = assertIs<NAddress>(Nip19Parser.uriToRoute(message.trim())?.entity)
            assertEquals("slug", naddr.dTag)
            val quote = bob.sign(TextNoteEvent.build(message) { messageTags(message) })
            assertEquals(listOf(article.addressTag()), quote.tags.mapNotNull(QTag::parseAddressId))
        }

    @Test
    fun pTagsWithHintsFillsAMissingHintFromTheCache() =
        runTest {
            val cache = EventCache()
            cache.relayHints.addKey(bob.pubKey, relay)
            val tagger = NewMessageTagger(message = "hi nostr:${NPub.create(bob.pubKey)}", dao = cache)
            tagger.run()
            assertEquals(listOf(PTag(bob.pubKey, relay)), tagger.pTagsWithHints(cache.relayHints))
        }
}
