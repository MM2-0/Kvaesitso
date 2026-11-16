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
import kotlin.test.assertTrue

class WriterTest {
    @Test
    fun readsStreamedChunksUsageAndTheEnd() {
        val chunk = parseStreamLine("""data: {"choices":[{"delta":{"content":"<html>"}}]}""")
        assertEquals("<html>", chunk.text)
        val usage = parseStreamLine("""data: {"choices":[],"usage":{"prompt_tokens":10,"completion_tokens":5}}""").usage
        assertEquals(15L, usage?.total)
        assertTrue(parseStreamLine("data: [DONE]").done)
        assertEquals("", parseStreamLine(": keep-alive").text)
        assertEquals("", parseStreamLine("data: {broken").text)
    }

    @Test
    fun readsANonStreamedAnswer() {
        val written = readCompletion("""{"choices":[{"message":{"content":"<p>x</p>"}}],"usage":{"prompt_tokens":3}}""")
        assertEquals("<p>x</p>", written.text)
        assertEquals(3L, written.usage?.promptTokens)
    }

    @Test
    fun extractsTheDocumentFromAWrappedAnswer() {
        assertEquals("<!doctype html><html><body>x</body></html>", extractDocument("```html\n<!doctype html><html><body>x</body></html>\n```"))
        assertEquals("<html><body>x</body></html>", extractDocument("Here is your paper:\n<html><body>x</body></html>\nEnjoy!"))
    }

    @Test
    fun ranksLongFormWritersFirst() {
        val ranked = rankModels(listOf("text-embedding-3-large", "gpt-4o-mini", "anthropic/claude-haiku-4.5", "anthropic/claude-sonnet-4.5", "openai/gpt-5", "google/gemini-2.5-pro"))
        assertEquals("anthropic/claude-sonnet-4.5", ranked.first())
        assertTrue("text-embedding-3-large" !in ranked)
        assertTrue(ranked.indexOf("openai/gpt-5") < ranked.indexOf("gpt-4o-mini"))
    }

    @Test
    fun readsBothModelListShapes() {
        assertEquals(listOf("a", "b"), readModelIds("""{"data":[{"id":"a"},{"id":"b"}]}"""))
        assertEquals(listOf("gemini-2.5-pro"), readModelIds("""{"models":[{"name":"models/gemini-2.5-pro"}]}"""))
        assertEquals(emptyList(), readModelIds("<html>"))
    }

    @Test
    fun anthropicGetsRoomForAWholePage() {
        assertTrue(chatRequestBody("https://api.anthropic.com/v1", "m", emptyList(), true).contains("\"max_tokens\":32000"))
        assertTrue(chatRequestBody("https://openrouter.ai/api/v1", "m", emptyList(), true).contains("\"include_usage\":true"))
    }

    @Test
    fun theDigestGoesInsideTheCorpusBlockAndTheBriefHasTheStylesheet() {
        val message = userMessage("DIGEST", "Alice")
        assertTrue(message.indexOf("<corpus>") < message.indexOf("DIGEST"))
        assertTrue(message.contains("Reader: Alice"))
        assertTrue(systemPrompt().contains("color-scheme: light"))
        assertTrue(repairMessage(listOf(Violation(Violation.Kind.QUOTE, "not found", "x"))).contains("- QUOTE: not found"))
    }

    private fun streamOf(vararg lines: String): Written {
        val reader = StreamReader {}
        for (line in lines) if (reader.read(line)) break
        return reader.finish()
    }

    @Test
    fun aStreamEndsAtDoneAfterStop() {
        val written =
            streamOf(
                """data: {"choices":[{"delta":{"content":"<html>"}}]}""",
                """data: {"choices":[{"delta":{},"finish_reason":"stop"}]}""",
                "data: [DONE]",
                """data: {"choices":[{"delta":{"content":"ignored"}}]}""",
            )
        assertEquals("<html>", written.text)
        // [DONE] alone still ends a provider's stream that never names a finish reason.
        assertEquals("<p>", streamOf("""data: {"choices":[{"delta":{"content":"<p>"}}]}""", "data: [DONE]").text)
    }

    @Test
    fun aCutOffStreamIsNeverAPaper() {
        val length =
            runCatching {
                streamOf("""data: {"choices":[{"delta":{"content":"<html>"},"finish_reason":"length"}]}""", "data: [DONE]")
            }.exceptionOrNull()
        assertEquals(WriterFailure.CUT_OFF, (length as WriterException).failure)

        val error = runCatching { streamOf("""data: {"choices":[{"delta":{"content":"<html>"}}]}""", """data: {"error":{"message":"overloaded"}}""") }.exceptionOrNull()
        assertEquals(WriterFailure.CUT_OFF, (error as WriterException).failure)
        assertEquals("overloaded", error.providerSaid)

        val unfinished = runCatching { streamOf("""data: {"choices":[{"delta":{"content":"<html>"}}]}""") }.exceptionOrNull()
        assertEquals(WriterFailure.CUT_OFF, (unfinished as WriterException).failure)
    }

    @Test
    fun aWholeAnswerIsCheckedTheSameWay() {
        assertEquals("<p>x</p>", readCompletion("""{"choices":[{"message":{"content":"<p>x</p>"}}]}""").text)
        assertEquals("<p>x</p>", readCompletion("""{"choices":[{"message":{"content":"<p>x</p>"},"finish_reason":"stop"}]}""").text)
        val cut = runCatching { readCompletion("""{"choices":[{"message":{"content":"<p>x"},"finish_reason":"length"}]}""") }.exceptionOrNull()
        assertEquals(WriterFailure.CUT_OFF, (cut as WriterException).failure)
        val error = runCatching { readCompletion("""{"error":{"message":"bad"}}""") }.exceptionOrNull()
        assertEquals(WriterFailure.CUT_OFF, (error as WriterException).failure)
    }

    @Test
    fun printProgressSkipsMostStreamChunks() {
        assertTrue(progressWorthShowing(PrintStep.Probe, PrintStep.Write(1, 0)))
        assertFalse(progressWorthShowing(PrintStep.Write(1, 0), PrintStep.Write(1, 50)))
        assertTrue(progressWorthShowing(PrintStep.Write(1, 0), PrintStep.Write(1, WRITE_PROGRESS_CHARS)))
        assertTrue(progressWorthShowing(PrintStep.Write(1, 5_000), PrintStep.Write(2, 10)))
        assertTrue(progressWorthShowing(PrintStep.Write(1, 5_000), PrintStep.Check(1)))
    }
}
