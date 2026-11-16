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
package com.vitorpamplona.amethyst.commons.frontpage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WriteProgressTest {
    /** As OpenRouter streams a reasoning model: keep-alives, then reasoning (twice over), then the page. */
    private val openRouter =
        listOf(
            ": OPENROUTER PROCESSING",
            "",
            ": OPENROUTER PROCESSING",
            """data: {"id":"gen-1","choices":[{"index":0,"delta":{"role":"assistant","content":"","reasoning":"Let me rank ","reasoning_details":[{"type":"reasoning.text","text":"Let me rank ","format":"unknown","index":0}]},"finish_reason":null}]}""",
            """data: {"id":"gen-1","choices":[{"index":0,"delta":{"role":"assistant","content":"","reasoning":"the desks.","reasoning_details":[{"type":"reasoning.text","text":"the desks.","format":"unknown","index":0}]},"finish_reason":null}]}""",
            """data: {"id":"gen-1","choices":[{"index":0,"delta":{"role":"assistant","content":"<html><body><h1>The Nostr Observer</h1><h2>Relays ","reasoning":null},"finish_reason":null}]}""",
            """data: {"id":"gen-1","choices":[{"index":0,"delta":{"role":"assistant","content":"go quiet</h2><p>…</p>"},"finish_reason":null}]}""",
            """data: {"id":"gen-1","choices":[{"index":0,"delta":{"content":"</body></html>"},"finish_reason":"stop"}],"usage":{"prompt_tokens":10,"completion_tokens":20}}""",
            "data: [DONE]",
        )

    /** As DeepSeek streams its reasoner: `reasoning_content`, then `content`. */
    private val deepSeek =
        listOf(
            """data: {"choices":[{"index":0,"delta":{"role":"assistant","content":null,"reasoning_content":"Hmm, "},"finish_reason":null}]}""",
            """data: {"choices":[{"index":0,"delta":{"content":null,"reasoning_content":"which story leads?"},"finish_reason":null}]}""",
            """data: {"choices":[{"index":0,"delta":{"content":"<p>ok</p>","reasoning_content":null},"finish_reason":"stop"}]}""",
            "data: [DONE]",
        )

    private fun read(lines: List<String>): Pair<Written, List<WriterEvent>> {
        val events = mutableListOf<WriterEvent>()
        val reader = StreamReader { events.add(it) }
        for (line in lines) if (reader.read(line)) break
        return reader.finish() to events
    }

    @Test
    fun openRoutersReasoningIsReadOnceAndItsKeepAlivesSayNothing() {
        val (written, events) = read(openRouter)
        assertEquals("<html><body><h1>The Nostr Observer</h1><h2>Relays go quiet</h2><p>…</p></body></html>", written.text)
        assertEquals(30L, written.usage?.total)
        assertEquals("Let me rank the desks.", events.filterIsInstance<WriterEvent.Reasoning>().joinToString("") { it.delta })
        assertEquals(WriterEvent.FirstByte, events.first())
        assertEquals(2, events.count { it == WriterEvent.KeepAlive })
        // A keep-alive is no text and no reasoning.
        assertEquals("", parseStreamLine(": OPENROUTER PROCESSING").text)
        assertTrue(parseStreamLine(": OPENROUTER PROCESSING").keepAlive)
    }

    @Test
    fun deepSeeksReasoningContentIsReasoning() {
        val (written, events) = read(deepSeek)
        assertEquals("<p>ok</p>", written.text)
        assertEquals("Hmm, which story leads?", events.filterIsInstance<WriterEvent.Reasoning>().joinToString("") { it.delta })
    }

    @Test
    fun noProviderIsAskedForReasoning() {
        // A `reasoning` object may turn thinking on, and bill it, where a model makes it optional.
        val messages = listOf(ChatMessage("user", "hi"))
        assertFalse(chatRequestBody("https://openrouter.ai/api/v1", "m", messages, stream = true).contains("reasoning"))
        assertFalse(chatRequestBody("https://openrouter.ai/api/v1", "anthropic/claude-sonnet-4.5", messages, stream = false).contains("reasoning"))
        assertFalse(chatRequestBody("https://api.openai.com/v1", "m", messages, stream = true).contains("reasoning"))
        assertFalse(chatRequestBody("https://api.deepseek.com", "m", messages, stream = true).contains("reasoning"))
        assertFalse(chatRequestBody("https://openrouter.ai.evil.example/v1", "m", messages, stream = true).contains("reasoning"))
    }

    @Test
    fun theSectionIsTheLastHeadingClosedSoFar() {
        assertNull(latestSection("<html><body><h1>The Nostr Observer</h1>"))
        assertNull(latestSection("<h1>The Nostr Observer</h1><h2>Relays go"))
        assertEquals("Relays go quiet", latestSection("<h1>The Nostr Observer</h1><h2 class=\"lead\">Relays <em>go</em>\n quiet</h2><p>Body</p><h3>Half"))
        assertEquals("Second & last", latestSection("<h2>First</h2><p>x</p><h3>Second &amp; last</h3>"))
        // A desk kicker counts as a section too.
        assertEquals("Bitcoin", latestSection("<h2>First</h2><p>x</p><span class=\"kicker small\">Bitcoin</span>"))
        val long = "A".repeat(200)
        assertEquals(SECTION_MAX_CHARS, latestSection("<h2>$long</h2>")!!.length)
    }

    @Test
    fun theStepGoesWaitingThinkingWriting() {
        val log = PrintLogBook { 100L }
        val tracker = WriteTracker(round = 1, since = 90L, log = log)
        assertEquals(WritePhase.WAITING, tracker.step.phase)
        assertEquals(90L, tracker.step.since)
        assertNull(tracker.on(WriterEvent.Sent(streaming = true)))
        assertNull(tracker.on(WriterEvent.FirstByte))
        assertNull(tracker.on(WriterEvent.KeepAlive))
        assertEquals(WritePhase.THINKING, tracker.on(WriterEvent.Reasoning("Let me "))?.phase)
        // More reasoning shows nothing new.
        assertNull(tracker.on(WriterEvent.Reasoning("think.")))
        val writing = tracker.on(WriterEvent.Text("<h1>The Nostr Observer</h1>", 27))
        assertEquals(WritePhase.WRITING, writing?.phase)
        assertNull(writing?.section)
        // The section is looked for once enough page has come since the last look.
        val body = "<p>" + "x".repeat(SECTION_SCAN_CHARS) + "</p>"
        assertEquals("Relays go quiet", tracker.on(WriterEvent.Text("<h2>Relays go quiet</h2>$body", 51 + body.length))?.section)
        assertEquals(
            listOf(PrintLogKind.REQUEST_SENT, PrintLogKind.FIRST_BYTE, PrintLogKind.THINKING, PrintLogKind.WRITING, PrintLogKind.SECTION),
            log.log.value.entries
                .map { it.kind },
        )
        assertEquals("Let me think.", log.log.value.reasoning)
    }

    @Test
    fun theSectionIsLookedForOncePerStretchOfPage() {
        val tracker = WriteTracker(round = 1, since = 0)
        tracker.on(WriterEvent.Text("<h1>The Nostr Observer</h1>", 27))
        // Closed, but too soon after the last look: not yet.
        assertNull(tracker.on(WriterEvent.Text("<h2>Lead</h2>", 40))?.section)
        // More page, no '>' in it: the closed heading is still found once enough has come.
        val filler = "y".repeat(SECTION_SCAN_CHARS)
        assertEquals("Lead", tracker.on(WriterEvent.Text(filler, 40 + filler.length))?.section)
    }

    @Test
    fun followingTheReasoningKeepsCountingPastTheCap() {
        val log = PrintLogBook { 0L }
        log.start("OpenRouter · m")
        repeat(100) { log.reasoning("x".repeat(100)) }
        val capped = log.log.value
        repeat(10) { log.reasoning("y".repeat(100)) }
        val later = log.log.value
        assertEquals(PrintLogBook.REASONING_KEPT, later.reasoning.length)
        assertTrue(later.reasoningReceived > capped.reasoningReceived)
        assertEquals(11_000L, later.reasoningReceived)
    }

    @Test
    fun aProviderThatSendsItAllAtOnceIsNeverWritingCharacterByCharacter() {
        val tracker = WriteTracker(round = 1, since = 0)
        assertEquals(WritePhase.ALL_AT_ONCE, tracker.on(WriterEvent.AllAtOnce)?.phase)
        assertEquals(WritePhase.ALL_AT_ONCE, tracker.on(WriterEvent.Text("<h2>Whole</h2>", 14))?.phase)
    }

    @Test
    fun theLogKeepsTheReasoningsTail() {
        val log = PrintLogBook { 0L }
        log.start("OpenRouter · m")
        repeat(100) { log.reasoning("x".repeat(100)) }
        log.add(PrintLogKind.WRITING)
        assertEquals(PrintLogBook.REASONING_KEPT, log.log.value.reasoning.length)
        assertEquals(
            listOf(PrintLogKind.ASKED, PrintLogKind.WRITING),
            log.log.value.entries
                .map { it.kind },
        )
        // The next print starts clean.
        log.start("OpenRouter · m")
        assertEquals("", log.log.value.reasoning)
        assertEquals(1, log.log.value.entries.size)
    }
}
