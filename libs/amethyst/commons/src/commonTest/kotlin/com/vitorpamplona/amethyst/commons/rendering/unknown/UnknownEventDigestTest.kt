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
package com.vitorpamplona.amethyst.commons.rendering.unknown

import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnknownEventDigestTest {
    private val alice = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val noteId = "5c83da77af1dec6d7289834998ad7aafbd9e2191396d75ec3cc27f5a77226f36"

    private fun event(
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ) = Event("0".repeat(64), alice, 1_790_000_000, kind, tags, content, "0".repeat(128))

    @Test
    fun blankIsEmpty() {
        assertIs<ContentShape.Empty>(classifyContent("  \n "))
    }

    @Test
    fun sentencesAndShortWordsAreProse() {
        assertIs<ContentShape.Prose>(classifyContent("Just finished a 5k run, feeling great"))
        assertIs<ContentShape.Prose>(classifyContent("gm"))
        assertIs<ContentShape.Prose>(classifyContent("[spam] buy now at example.com"))
    }

    @Test
    fun linksAndNostrEntitiesAreProse() {
        assertIs<ContentShape.Prose>(classifyContent("https://example.com/a/very/long/path/" + "x".repeat(150)))
        assertIs<ContentShape.Prose>(classifyContent("nostr:nevent1" + "q".repeat(150)))
        assertIs<ContentShape.Prose>(classifyContent("lnbc" + "1".repeat(200)))
    }

    @Test
    fun jsonObjectsBecomeFields() {
        val shape = classifyContent("""{"status":"ok","count":3,"items":[1,2],"meta":{"a":1}}""")
        assertIs<ContentShape.Json>(shape)
        assertEquals(listOf("status", "count", "items", "meta"), shape.fields.map { it.key })
        assertEquals("ok", shape.fields[0].value)
        assertEquals("[2]", shape.fields[2].value)
        assertTrue(shape.fields[3].nested)
    }

    @Test
    fun jsonArraysAreCounted() {
        val shape = classifyContent("""[["p","abc"],["e","def"]]""")
        assertIs<ContentShape.Json>(shape)
        assertTrue(shape.isArray)
        assertEquals(2, shape.size)
    }

    @Test
    fun nip04IsEncrypted() {
        val shape = classifyContent("aGVsbG8gd29ybGQgdGhpcyBpcyBhIHRlc3Q=?iv=MTIzNDU2Nzg5MDEyMzQ1Ng==")
        assertEquals(ContentShape.Encrypted(EncryptionScheme.NIP04), shape)
    }

    @Test
    fun nip44IsEncrypted() {
        // Version byte 2, then 98 more bytes: base64 starting with "Ag".
        val payload = "Ag" + "A".repeat(134)
        assertEquals(ContentShape.Encrypted(EncryptionScheme.NIP44), classifyContent(payload))
    }

    @Test
    fun unpaddedNip44IsEncrypted() {
        assertEquals(ContentShape.Encrypted(EncryptionScheme.NIP44), classifyContent("Ag" + "B".repeat(133)))
    }

    @Test
    fun hexAndBase64AreEncoded() {
        assertEquals(ContentShape.Encoded(BlobEncoding.HEX, 64), classifyContent(noteId))
        assertEquals(BlobEncoding.BASE64, (classifyContent("Zm9v".repeat(40)) as ContentShape.Encoded).encoding)
        // Base64 wrapped onto lines still has no spaces between words.
        assertEquals(BlobEncoding.BASE64, (classifyContent("Zm9v".repeat(20) + "\n" + "YmFy".repeat(20)) as ContentShape.Encoded).encoding)
    }

    @Test
    fun digestSortsCommonTags() {
        val digest =
            UnknownEventDigest.of(
                event(
                    31_337,
                    arrayOf(
                        arrayOf("d", "my-thing"),
                        arrayOf("alt", "A recipe for sourdough bread"),
                        arrayOf("title", "Sourdough"),
                        arrayOf("summary", "Bread that takes three days"),
                        arrayOf("image", "https://example.com/bread.jpg"),
                        arrayOf("imeta", "url https://example.com/crumb.jpg", "m image/jpeg", "dim 800x600"),
                        arrayOf("p", alice, "wss://relay.example.com", "author"),
                        arrayOf("e", noteId, "", "root"),
                        arrayOf("a", "30023:$alice:bread-intro"),
                        arrayOf("t", "baking"),
                        arrayOf("t", "bread"),
                        arrayOf("r", "https://example.com"),
                        arrayOf("published_at", "1790000000"),
                        arrayOf("servings", "8"),
                        arrayOf("ingredient", "flour", "500g"),
                    ),
                    "Mix, wait, bake.",
                ),
            )

        assertEquals("my-thing", digest.identifier)
        assertEquals("Sourdough", digest.title)
        assertEquals("Bread that takes three days", digest.summary)
        assertEquals("A recipe for sourdough bread", digest.alt)
        assertEquals(listOf("https://example.com/bread.jpg", "https://example.com/crumb.jpg"), digest.media.map { it.url })
        assertEquals("image/jpeg", digest.media[1].mimeType)
        assertEquals(listOf(DigestPerson(alice, "author")), digest.people)
        assertEquals("root", digest.events.single().marker)
        assertEquals(
            "bread-intro",
            digest.addresses
                .single()
                .address.dTag,
        )
        assertEquals(listOf("baking", "bread"), digest.hashtags)
        assertEquals(listOf("https://example.com"), digest.links)
        assertEquals(1_790_000_000L, digest.publishedAt)
        assertEquals(listOf(RawTag("servings", listOf("8")), RawTag("ingredient", listOf("flour", "500g"))), digest.otherTags)
        assertEquals("Mix, wait, bake.", digest.readableText)
    }

    @Test
    fun inlineHashtagsAreNotRepeated() {
        val digest = UnknownEventDigest.of(event(9_999, arrayOf(arrayOf("t", "food"), arrayOf("t", "bread")), "great #Food"))
        assertEquals(listOf("bread"), digest.uncitedHashtags)
    }

    @Test
    fun tagsTheNoteRowDrawsAreNotRawTags() {
        val digest = UnknownEventDigest.of(event(9_999, arrayOf(arrayOf("nonce", "123", "20"), arrayOf("zap", alice, "", "1"), arrayOf("-"), arrayOf("x", "y")), ""))
        assertEquals(listOf(RawTag("x", listOf("y"))), digest.otherTags)
    }

    @Test
    fun deeplyNestedJsonIsNotParsed() {
        // The JSON parser recurses: a hundred thousand brackets overflow the stack.
        val depth = 100_000
        assertEquals(ContentShape.Encoded(BlobEncoding.JSON, depth * 2), classifyContent("[".repeat(depth) + "]".repeat(depth)))
        assertIs<ContentShape.Encoded>(classifyContent("{\"a\":".repeat(depth) + "1" + "}".repeat(depth)))
    }

    @Test
    fun bracketsInsideStringsDoNotCountAsNesting() {
        val shape = classifyContent("""{"note":"${"[".repeat(100)}"}""")
        assertIs<ContentShape.Json>(shape)
        assertEquals("note", shape.fields.single().key)
    }

    @Test
    fun hugeJsonIsNotParsedWhileDrawing() {
        val big = "{" + (0 until 60_000).joinToString(",") { "\"key$it\":\"value $it\"" } + "}"
        assertEquals(BlobEncoding.JSON, (classifyContent(big) as ContentShape.Encoded).encoding)
    }

    @Test
    fun repeatedAndFallbackTagsStayWithTheOtherTags() {
        val digest =
            UnknownEventDigest.of(
                event(
                    9_999,
                    arrayOf(
                        arrayOf("title", "First"),
                        arrayOf("title", "Second"),
                        arrayOf("name", "A name"),
                        arrayOf("summary", "Short"),
                        arrayOf("description", "Long"),
                        arrayOf("published_at", "100"),
                        arrayOf("published_at", "200"),
                    ),
                    "",
                ),
            )
        assertEquals("First", digest.title)
        assertEquals("Short", digest.summary)
        assertEquals(100L, digest.publishedAt)
        assertEquals(
            listOf(RawTag("title", listOf("Second")), RawTag("published_at", listOf("200")), RawTag("name", listOf("A name")), RawTag("description", listOf("Long"))),
            digest.otherTags,
        )
    }

    @Test
    fun nameFillsAMissingTitle() {
        val digest = UnknownEventDigest.of(event(9_999, arrayOf(arrayOf("name", "A name"), arrayOf("subject", "A subject")), ""))
        assertEquals("A name", digest.title)
        assertEquals(listOf(RawTag("subject", listOf("A subject"))), digest.otherTags)
    }

    @Test
    fun malformedReferencesStayAsRawTags() {
        val digest = UnknownEventDigest.of(event(9_999, arrayOf(arrayOf("p", "not-a-key"), arrayOf("image", "ipfs://cid")), ""))
        assertTrue(digest.people.isEmpty())
        assertTrue(digest.media.isEmpty())
        assertEquals(listOf("p", "image"), digest.otherTags.map { it.name })
    }

    @Test
    fun altStandsInForUnreadableContent() {
        val digest = UnknownEventDigest.of(event(9_999, arrayOf(arrayOf("alt", "Encrypted wallet backup")), noteId))
        assertEquals("Encrypted wallet backup", digest.readableText)
        assertNull(digest.title)
    }
}
