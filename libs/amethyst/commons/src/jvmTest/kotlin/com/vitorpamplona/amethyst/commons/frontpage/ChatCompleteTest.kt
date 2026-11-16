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

import com.vitorpamplona.amethyst.commons.service.http.RoleBasedOkHttpClients
import com.vitorpamplona.quartz.nipFAAgentKeys.tags.AgentKeyTag
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Chat Completions call against a faked provider: stream, fallback, and failures. */
class ChatCompleteTest {
    private class Answer(
        val code: Int,
        val type: String,
        val body: String,
    )

    private val requests = mutableListOf<String>()

    private fun clients(vararg answers: Answer): RoleBasedOkHttpClients {
        val queue = answers.toMutableList()
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor(
                    Interceptor { chain ->
                        requests.add(Buffer().also { chain.request().body?.writeTo(it) }.readUtf8())
                        val answer = queue.removeAt(0)
                        Response
                            .Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(answer.code)
                            .message("x")
                            .body(answer.body.toResponseBody(answer.type.toMediaType()))
                            .build()
                    },
                ).build()
        return object : RoleBasedOkHttpClients {
            override fun proxyPortForVideo(url: String): Int? = null

            override fun okHttpClientForNip05(url: String) = client

            override fun okHttpClientForUploads(url: String) = client

            override fun okHttpClientForImage(url: String) = client

            override fun okHttpClientForVideo(url: String) = client

            override fun okHttpClientForMoney(url: String) = client

            override fun okHttpClientForPreview(url: String) = client

            override fun okHttpClientForPushRegistration(url: String) = client
        }
    }

    private val key = AgentKeyTag("sk-or-v1-x", "https://openrouter.ai/api/v1")
    private val messages = listOf(ChatMessage("user", "hi"))

    @Test
    fun streamsThePageAndItsUsage() =
        runBlocking {
            val sse =
                listOf(
                    """data: {"choices":[{"delta":{"content":"<html>"}}]}""",
                    """data: {"choices":[{"delta":{"content":"</html>"}}]}""",
                    """data: {"choices":[],"usage":{"prompt_tokens":7,"completion_tokens":3}}""",
                    "data: [DONE]",
                ).joinToString("\n\n")
            val seen = mutableListOf<Int>()
            val written = clients(Answer(200, "text/event-stream", sse)).chatComplete(key, "m", messages) { (it as? WriterEvent.Text)?.let { text -> seen.add(text.total) } }
            assertEquals("<html></html>", written.text)
            assertEquals(10L, written.usage?.total)
            assertEquals(listOf(6, 13), seen)
            assertTrue(requests.single().contains("\"stream\":true"))
            // Nothing asks OpenRouter for reasoning: a `reasoning` object may turn (paid) thinking on.
            assertFalse(requests.single().contains("\"reasoning\""))
        }

    @Test
    fun aProviderThatAnswersWholeIsSaidToSendItAllAtOnce() =
        runBlocking {
            val events = mutableListOf<WriterEvent>()
            val written =
                clients(
                    Answer(400, "application/json", """{"error":"stream not supported"}"""),
                    Answer(200, "application/json", """{"choices":[{"message":{"content":"<p>ok</p>"}}]}"""),
                ).chatComplete(key, "m", messages) { events.add(it) }
            assertEquals("<p>ok</p>", written.text)
            assertEquals(listOf(WriterEvent.Sent(true), WriterEvent.Sent(false), WriterEvent.AllAtOnce), events.take(3))
            assertEquals(9, (events.last() as WriterEvent.Text).total)
        }

    @Test
    fun fallsBackWhenTheProviderRefusesToStream() =
        runBlocking {
            val written =
                clients(
                    Answer(400, "application/json", """{"error":"stream not supported"}"""),
                    Answer(200, "application/json", """{"choices":[{"message":{"content":"<p>ok</p>"}}]}"""),
                ).chatComplete(key, "m", messages) {}
            assertEquals("<p>ok</p>", written.text)
            assertTrue(requests[1].contains("\"stream\":false"))
        }

    @Test
    fun aRefusedDeepCallIsSentOnceMoreWithoutItsExtras() =
        runBlocking {
            val written =
                clients(
                    Answer(400, "application/json", """{"error":"unknown field reasoning"}"""),
                    Answer(400, "application/json", """{"error":"unknown field reasoning"}"""),
                    Answer(200, "application/json", """{"choices":[{"message":{"content":"<p>ok</p>"}}]}"""),
                ).chatComplete(key, "m", messages, maxTokens = 48_000, effort = WritingEffort.DEEP) {}
            assertEquals("<p>ok</p>", written.text)
            assertTrue(requests[0].contains("\"reasoning\":{\"effort\":\"high\"}"))
            assertTrue(requests[0].contains("\"max_tokens\":48000"))
            assertFalse(requests[2].contains("reasoning"))
            assertFalse(requests[2].contains("max_tokens"))
        }

    @Test
    fun aPlainCallIsNotRetriedOnARefusal() {
        val failure =
            assertFailsWith<WriterException> {
                runBlocking {
                    clients(
                        Answer(400, "application/json", "{}"),
                        Answer(400, "application/json", "{}"),
                    ).chatComplete(key, "m", messages) {}
                }
            }
        assertEquals(400, failure.status)
        assertEquals(2, requests.size)
    }

    @Test
    fun aRejectedKeySaysSo() {
        val failure =
            assertFailsWith<WriterException> {
                runBlocking { clients(Answer(401, "application/json", "{}")).chatComplete(key, "m", messages) {} }
            }
        assertEquals(WriterFailure.REJECTED, failure.failure)
    }

    @Test
    fun listsModels() =
        runBlocking {
            assertEquals(listOf("a/b"), clients(Answer(200, "application/json", """{"data":[{"id":"a/b"}]}""")).listAgentModels(key).map { it.id })
        }

    @Test
    fun aKeyIsNeverSentInCleartextOffThisDevice() {
        val cleartext = AgentKeyTag("sk-or-v1-x", "http://openrouter.example/api/v1")
        val failure = assertFailsWith<WriterException> { runBlocking { clients().chatComplete(cleartext, "m", messages) {} } }
        assertEquals(WriterFailure.INSECURE_URL, failure.failure)
        assertFailsWith<WriterException> { runBlocking { clients().listAgentModels(cleartext) } }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun aMalformedCustomUrlIsAFailureNotACrash() {
        val broken = AgentKeyTag("sk", "https://exa mple.com/v1")
        assertFailsWith<WriterException> { runBlocking { clients().listAgentModels(broken) } }
        val nohost = AgentKeyTag("", "https://")
        assertFailsWith<WriterException> { runBlocking { clients().chatComplete(nohost, "m", messages) {} } }
    }

    @Test
    fun aStreamCutOffIsAFailure() {
        val sse = """data: {"choices":[{"delta":{"content":"<html>"},"finish_reason":"length"}]}""" + "\n\ndata: [DONE]"
        val failure = assertFailsWith<WriterException> { runBlocking { clients(Answer(200, "text/event-stream", sse)).chatComplete(key, "m", messages) {} } }
        assertEquals(WriterFailure.CUT_OFF, failure.failure)
    }
}
