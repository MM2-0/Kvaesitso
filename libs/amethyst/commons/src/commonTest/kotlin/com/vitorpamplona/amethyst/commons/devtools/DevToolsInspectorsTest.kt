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
package com.vitorpamplona.amethyst.commons.devtools

import com.vitorpamplona.quartz.kinds.KindNames
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DevToolsInspectorsTest {
    private val secret = "0000000000000000000000000000000000000000000000000000000000000007"
    private val signer = NostrSignerInternal(KeyPair(secret.hexToByteArray()))
    private val pubKey = signer.pubKey

    @Test
    fun npubDecodesToItsKeyAndConvertsToNprofile() {
        val result = Nip19Inspector.inspect(NPub.create(pubKey))
        assertIs<Nip19Inspection.Decoded>(result)
        assertEquals("npub", result.prefix)
        assertEquals(Nip19Row(Nip19Field.PUBLIC_KEY, pubKey), result.rows.single())
        assertEquals(listOf("nprofile", "nostr"), result.encodings.map { it.prefix })
    }

    @Test
    fun nostrUriPrefixIsAccepted() {
        val result = Nip19Inspector.inspect("nostr:" + NPub.create(pubKey))
        assertIs<Nip19Inspection.Decoded>(result)
        assertEquals(pubKey, result.rows.single().value)
    }

    @Test
    fun nsecIsFlaggedSecretAndShowsItsPublicKey() {
        val result = Nip19Inspector.inspect(secret.hexToByteArray().toNsec())
        assertIs<Nip19Inspection.Decoded>(result)
        assertTrue(result.isSecret)
        assertEquals(pubKey, result.rows.first { it.field == Nip19Field.PUBLIC_KEY }.value)
    }

    @Test
    fun neventCarriesAuthorKindAndRelays() {
        val id = "a".repeat(64)
        val nevent = NEvent.create(id, pubKey, 1, emptyList())
        val result = Nip19Inspector.inspect(nevent)
        assertIs<Nip19Inspection.Decoded>(result)
        assertEquals(id, result.rows.first { it.field == Nip19Field.EVENT_ID }.value)
        assertEquals(pubKey, result.rows.first { it.field == Nip19Field.AUTHOR }.value)
        assertEquals("1", result.rows.first { it.field == Nip19Field.KIND }.value)
    }

    @Test
    fun naddrShowsTheATag() {
        val naddr = NAddress.create(30023, pubKey, "my-article", null)
        val result = Nip19Inspector.inspect(naddr)
        assertIs<Nip19Inspection.Decoded>(result)
        assertEquals("30023:$pubKey:my-article", result.rows.first { it.field == Nip19Field.ADDRESS }.value)
    }

    @Test
    fun rawHexOffersEveryReading() {
        val result = Nip19Inspector.inspect(pubKey.uppercase())
        assertIs<Nip19Inspection.RawHex>(result)
        assertEquals(pubKey, result.hex)
        assertEquals(NPub.create(pubKey), result.encodings.first { it.prefix == "npub" }.value)
        assertEquals(4, result.encodings.size)
    }

    @Test
    fun garbageIsInvalidAndBlankIsEmpty() {
        assertEquals(Nip19Inspection.Invalid, Nip19Inspector.inspect("hello world"))
        assertEquals(Nip19Inspection.Empty, Nip19Inspector.inspect("   "))
    }

    @Test
    fun signedEventVerifies() =
        runTest {
            val event = signer.sign<TextNoteEvent>(1_000, TextNoteEvent.KIND, emptyArray(), "hi")
            val result = EventInspector.inspect(event.toJson())
            assertIs<EventInspectResult.Parsed>(result)
            assertTrue(result.inspection.isValid)
            assertEquals(EventSource.JSON, result.inspection.source)
            assertEquals(KindNames.nipFor(TextNoteEvent.KIND), result.inspection.kindNip)
        }

    @Test
    fun editedContentBreaksTheIdAndSignature() =
        runTest {
            val event = signer.sign<TextNoteEvent>(1_000, TextNoteEvent.KIND, emptyArray(), "hi")
            val tampered = event.toJson().replace("\"hi\"", "\"bye\"")
            val result = EventInspector.inspect(tampered)
            assertIs<EventInspectResult.Parsed>(result)
            assertFalse(result.inspection.idValid)
            assertTrue(EventIssue.ID_MISMATCH in result.inspection.issues)
            assertEquals(event.id, result.inspection.event.id)
        }

    @Test
    fun relayFrameAndClientCommandAreUnwrapped() =
        runTest {
            val event = signer.sign<TextNoteEvent>(1_000, TextNoteEvent.KIND, emptyArray(), "hi")
            val fromRelay = EventInspector.inspect("""["EVENT","sub1",${event.toJson()}]""")
            assertIs<EventInspectResult.Parsed>(fromRelay)
            assertEquals(EventSource.RELAY_MESSAGE, fromRelay.inspection.source)

            val fromClient = EventInspector.inspect("""["EVENT",${event.toJson()}]""")
            assertIs<EventInspectResult.Parsed>(fromClient)
            assertEquals(EventSource.CLIENT_COMMAND, fromClient.inspection.source)
        }

    @Test
    fun nembedIsUnwrapped() =
        runTest {
            val event = signer.sign<TextNoteEvent>(1_000, TextNoteEvent.KIND, emptyArray(), "hi")
            val result = EventInspector.inspect(NEmbed.create(event))
            assertIs<EventInspectResult.Parsed>(result)
            assertEquals(EventSource.NEMBED, result.inspection.source)
            assertTrue(result.inspection.isValid)
        }

    @Test
    fun codesCarryAnNaddrOnlyForAddressableAndReplaceableKinds() =
        runTest {
            val note = signer.sign<TextNoteEvent>(1_000, TextNoteEvent.KIND, emptyArray(), "hi")
            val noteCodes = EventCodes.of(note)
            assertEquals(null, noteCodes.naddr)
            assertEquals(NPub.create(pubKey), noteCodes.npub)

            val article = signer.sign<Event>(1_000, 30023, arrayOf(arrayOf("d", "slug")), "body")
            assertEquals(NAddress.create(30023, pubKey, "slug", null), EventCodes.of(article).naddr)

            val metadata = signer.sign<Event>(1_000, 0, emptyArray(), "{}")
            assertEquals(NAddress.create(0, pubKey, "", null), EventCodes.of(metadata).naddr)
        }

    @Test
    fun notJsonIsUnparseable() {
        assertIs<EventInspectResult.Unparseable>(EventInspector.inspect("{not json"))
        assertIs<EventInspectResult.Unparseable>(EventInspector.inspect("""["NOTICE","hi"]"""))
    }

    @Test
    fun consoleReadsFrameLabelsAndChallenges() {
        assertEquals("EVENT", RelayConsoleViewModel.labelOf("""["EVENT","s",{}]"""))
        assertEquals("EOSE", RelayConsoleViewModel.labelOf("""  [ "EOSE", "s"]"""))
        assertEquals(null, RelayConsoleViewModel.labelOf("""{"id":"x"}"""))
        assertEquals("abc-123", RelayConsoleViewModel.challengeOf("""["AUTH","abc-123"]"""))
    }

    @Test
    fun variantsGroupByClientAndByShape() =
        runTest {
            val a = signer.sign<TextNoteEvent>(1_000, TextNoteEvent.KIND, arrayOf(arrayOf("client", "Amethyst"), arrayOf("t", "nostr")), "hi")
            val b = signer.sign<TextNoteEvent>(1_001, TextNoteEvent.KIND, arrayOf(arrayOf("client", "Amethyst"), arrayOf("e", "a".repeat(64), "", "root")), "yo")
            val c = signer.sign<TextNoteEvent>(1_002, TextNoteEvent.KIND, arrayOf(arrayOf("t", "nostr")), "")

            val collector = KindVariantCollector()
            listOf(a, b, c, a).forEach { collector.add(it) }
            assertEquals(3, collector.total)

            val byClient = collector.variants(KindVariantGrouping.CLIENT)
            assertEquals(listOf("Amethyst", null), byClient.map { it.client })
            val amethyst = byClient.first()
            assertEquals(2, amethyst.count)
            assertEquals(b.id, amethyst.example.id)

            // a, b and c each carry a different tag-name set.
            val byShape = collector.variants(KindVariantGrouping.SHAPE)
            assertEquals(3, byShape.size)
            assertEquals(listOf<String?>(null), byShape.first { it.example.id == c.id }.clients.map { it.first })

            // Same tag names, but c's content is empty and d's is text: two shapes, not one.
            val d = signer.sign<TextNoteEvent>(1_003, TextNoteEvent.KIND, arrayOf(arrayOf("t", "nostr")), "text")
            collector.add(d)
            assertEquals(4, collector.variants(KindVariantGrouping.SHAPE).size)
        }

    @Test
    fun unchangedGroupsKeepTheirSnapshot() =
        runTest {
            val a = signer.sign<TextNoteEvent>(1_000, TextNoteEvent.KIND, arrayOf(arrayOf("client", "Amethyst")), "hi")
            val b = signer.sign<TextNoteEvent>(1_001, TextNoteEvent.KIND, arrayOf(arrayOf("client", "Damus")), "hi")
            val c = signer.sign<TextNoteEvent>(1_002, TextNoteEvent.KIND, arrayOf(arrayOf("client", "Damus")), "yo")

            val collector = KindVariantCollector()
            collector.add(a)
            collector.add(b)
            val before = collector.variants(KindVariantGrouping.CLIENT).associateBy { it.client }
            collector.add(c)
            val after = collector.variants(KindVariantGrouping.CLIENT).associateBy { it.client }

            // Amethyst gained nothing: the same instance, so the screen skips its card.
            assertSame(before["Amethyst"], after["Amethyst"])
            assertEquals(2, after.getValue("Damus").count)
            assertEquals(c.id, after.getValue("Damus").example.id)
        }

    @Test
    fun contentFormats() {
        assertEquals(ContentFormat.EMPTY, KindVariantCollector.contentFormatOf("  "))
        assertEquals(ContentFormat.JSON_OBJECT, KindVariantCollector.contentFormatOf("""{"name":"x"}"""))
        assertEquals(ContentFormat.JSON_ARRAY, KindVariantCollector.contentFormatOf("""[["p","x"]]"""))
        assertEquals(ContentFormat.ENCRYPTED, KindVariantCollector.contentFormatOf("abc==?iv=def=="))
        assertEquals(ContentFormat.ENCRYPTED, KindVariantCollector.contentFormatOf("A".repeat(120)))
        assertEquals(ContentFormat.TEXT, KindVariantCollector.contentFormatOf("hello world"))
    }

    @Test
    fun filterDraftAcceptsNip19AndBuildsTheFilter() {
        val npub = NPub.create(pubKey)
        val result =
            FilterDraft(
                kinds = "1, 7",
                authors = "$npub ${"b".repeat(64)}",
                tagName = "#p",
                tagValues = npub,
                since = SinceWindow.DAY,
                limit = "10",
            ).build(now = 100_000)
        assertIs<FilterDraft.Result.Ok>(result)
        assertEquals(listOf(1, 7), result.filter.kinds)
        assertEquals(listOf(pubKey, "b".repeat(64)), result.filter.authors)
        assertEquals(mapOf("p" to listOf(pubKey)), result.filter.tags)
        assertEquals(100_000L - 86_400, result.filter.since)
        assertEquals(10, result.filter.limit)
    }

    @Test
    fun filterDraftPointsAtTheBadField() {
        val badKind = FilterDraft(kinds = "1, notakind").build()
        assertIs<FilterDraft.Result.Invalid>(badKind)
        assertEquals(FilterField.KINDS, badKind.field)
        assertEquals("notakind", badKind.token)

        val badAuthor = FilterDraft(authors = "npub1nope").build()
        assertIs<FilterDraft.Result.Invalid>(badAuthor)
        assertEquals(FilterField.AUTHORS, badAuthor.field)

        val tagWithoutValues = FilterDraft(tagName = "t").build()
        assertIs<FilterDraft.Result.Invalid>(tagWithoutValues)
        assertEquals(FilterField.TAG, tagWithoutValues.field)

        assertIs<FilterDraft.Result.Ok>(FilterDraft().build())
    }

    @Test
    fun consoleReadsSubscriptionIds() {
        assertEquals("console-1", RelayConsoleViewModel.subIdOf("""["REQ","console-1",{"kinds":[1]}]"""))
        assertEquals("abc", RelayConsoleViewModel.subIdOf("""["CLOSED","abc","error: nope"]"""))
    }
}
