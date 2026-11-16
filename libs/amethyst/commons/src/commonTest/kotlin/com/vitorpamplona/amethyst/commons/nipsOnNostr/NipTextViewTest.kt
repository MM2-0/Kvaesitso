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
package com.vitorpamplona.amethyst.commons.nipsOnNostr

import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NipTextViewTest {
    private fun nip(
        content: String,
        vararg tags: Array<String>,
    ) = NipTextEvent(
        id = "0".repeat(64),
        pubKey = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c",
        createdAt = 1794580631,
        tags = arrayOf(arrayOf("d", "x"), *tags),
        content = content,
        sig = "0".repeat(128),
    )

    @Test
    fun nostrHubNipKeepsBodyAndReadsStatusAndNamedKinds() {
        val view =
            NipTextView.of(
                nip(
                    "A user stores the API key of their AI provider so every client they sign into can run AI\n" +
                        "tasks for them, billed to the user.\n\n## Event\n\nKind `12000`, replaceable. See [NIP-44](44.md).",
                    arrayOf("title", "AI Agent Keys"),
                    arrayOf("k", "12000", "AI Agent Keys"),
                    arrayOf("status", "review"),
                ),
            )

        assertEquals("AI Agent Keys", view.title)
        assertNull(view.summary)
        assertEquals("review", view.status)
        assertEquals(listOf(NipDefinedKind(12000, "AI Agent Keys")), view.kinds)
        assertTrue(view.body.startsWith("A user stores"))
        assertTrue(view.body.endsWith("[NIP-44](${NipLinks.REPO_BLOB}44.md)."), view.body)
        assertEquals(
            "A user stores the API key of their AI provider so every client they sign into can run AI tasks for them, billed to the user.",
            view.preview,
        )
    }

    @Test
    fun dropsHeadingsThatRepeatTheTitleAndLiftsTheStatusLine() {
        val view =
            NipTextView.of(
                nip(
                    "Trusted Translations\n--------------------\n\n`draft` `optional`\n\nThis NIP allows anyone to post a translation on a `kind:76`.",
                    arrayOf("title", "Trusted Translations"),
                    arrayOf("k", "1011"),
                    arrayOf("k", "10041"),
                    arrayOf("k", "1011"),
                ),
            )

        assertEquals("This NIP allows anyone to post a translation on a `kind:76`.", view.body)
        assertEquals(listOf("draft", "optional"), view.flags)
        assertNull(view.status)
        assertEquals(listOf(1011, 10041), view.kinds.map { it.kind })
        assertEquals("This NIP allows anyone to post a translation on a kind:76.", view.preview)
    }

    @Test
    fun nipsRepoLayoutGivesNumberAndTitleWithoutTitleTag() {
        val view =
            NipTextView.of(
                nip("NIP-01\n======\n\nBasic protocol flow description\n-------------------------------\n\n`draft` `mandatory`\n\nThis NIP defines the basic protocol."),
            )

        assertEquals("01", view.number)
        assertEquals("Basic protocol flow description", view.title)
        assertEquals(listOf("draft", "mandatory"), view.flags)
        assertEquals("This NIP defines the basic protocol.", view.body)
    }

    @Test
    fun summaryComesFromTheTag() {
        val view = NipTextView.of(nip("Body.", arrayOf("title", "T"), arrayOf("summary", " Short. ")))
        assertEquals("Short.", view.summary)
    }

    @Test
    fun keepsAnOpeningHeadingThatIsNotTheTitle() {
        val view = NipTextView.of(nip("# Overview\n\nText.", arrayOf("title", "Something")))
        assertEquals("# Overview\n\nText.", view.body)
        assertEquals("Text.", view.preview)
    }

    @Test
    fun keepsAnOpeningLineOfInlineCode() {
        val view = NipTextView.of(nip("`kind:1` notes are short.\n\nMore.", arrayOf("title", "T")))
        assertTrue(view.flags.isEmpty())
        assertTrue(view.body.startsWith("`kind:1`"))
    }

    @Test
    fun linksReferenceDefinitionsAndAnchors() {
        val md = "See [NIP-44][] and [sig](01.md#signing).\n\n[NIP-44]: 44.md\n[Blossom]: https://github.com/hzrd149/blossom"
        assertEquals(
            "See [NIP-44][] and [sig](${NipLinks.REPO_BLOB}01.md#signing).\n\n[NIP-44]: ${NipLinks.REPO_BLOB}44.md\n[Blossom]: https://github.com/hzrd149/blossom",
            linkRelativeNips(md),
        )
    }

    @Test
    fun previewSkipsCodeAndReducesLinks() {
        val md = "```json\n{}\n```\n\nThis NIP defines how to publish Maven artifacts as [Blossom][] blobs and **pins** them with [events](https://x.y)."
        assertEquals("This NIP defines how to publish Maven artifacts as Blossom blobs and pins them with events.", plainPreview(md))
    }

    @Test
    fun linksPlainMentionsButNotCodeOrExistingLinks() {
        val md = "Uses NIP-44, see `NIP-04` and [NIP-17](17.md).\n\n```\nNIP-01\n```\n\n[NIP-59]: 59.md\n\nThis is NIP-7D's own text."
        val out = linkNipMentions(md, self = "7D")
        assertTrue(out.startsWith("Uses [NIP-44](${NipLinks.REPO_BLOB}44.md), see `NIP-04` and [NIP-17](17.md)."), out)
        assertTrue(out.contains("```\nNIP-01\n```"), out)
        assertTrue(out.contains("[NIP-59]: 59.md"), out)
        assertTrue(out.endsWith("This is NIP-7D's own text."), out)
    }

    @Test
    fun longNipsGetContents() {
        val body = (1..4).joinToString("\n\n") { "## Section $it\n\n" + "Lorem ipsum dolor sit amet. ".repeat(40) }
        val view = NipTextView.of(nip(body, arrayOf("title", "Long")))
        assertEquals(listOf("section-1", "section-2", "section-3", "section-4"), view.sections.map { it.anchor })
        assertTrue(view.hasContents)
        assertTrue(!NipTextView.of(nip("## A\n\nshort", arrayOf("title", "Short"))).hasContents)
    }

    @Test
    fun namedNipHeadingIsATitleNotANumber() {
        val view = NipTextView.of(nip("NIP-AMB\n=======\n\nThis NIP defines AMB.", arrayOf("title", "NIP-AMB")))
        assertNull(view.number)
        assertEquals("NIP-AMB", view.title)
        assertEquals("This NIP defines AMB.", view.body)
    }

    @Test
    fun cachedViewFollowsTheEventsContent() {
        // Same id, different text (only tests do this): the cache must not hand back the old view.
        assertEquals("First.", NipTextView.of(nip("First.", arrayOf("title", "T"))).body)
        assertEquals("Second.", NipTextView.of(nip("Second.", arrayOf("title", "T"))).body)
        val event = nip("Same.", arrayOf("title", "T"))
        assertTrue(NipTextView.of(event) === NipTextView.of(event))
    }
}
