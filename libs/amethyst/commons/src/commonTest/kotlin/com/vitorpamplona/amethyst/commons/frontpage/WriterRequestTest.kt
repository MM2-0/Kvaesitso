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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What a writer call sends: its limit, its effort and its cache mark, per provider. */
class WriterRequestTest {
    private val openRouter = "https://openrouter.ai/api/v1"
    private val anthropic = "https://api.anthropic.com/v1"
    private val openAi = "https://api.openai.com/v1"
    private val other = "https://api.deepseek.com/v1"
    private val messages = listOf(ChatMessage("system", "brief"), ChatMessage("user", "\n\nplan it", "header and digest"))

    private fun body(
        url: String,
        model: String = "m",
        maxTokens: Int? = 48_000,
        effort: WritingEffort = WritingEffort.STANDARD,
    ) = Json.parseToJsonElement(chatRequestBody(url, model, messages, true, maxTokens, effort)).jsonObject

    @Test
    fun maxTokensGoesOutOnEveryProvider() {
        assertEquals(48_000, body(openRouter)["max_tokens"]?.jsonPrimitive?.int)
        assertEquals(48_000, body(anthropic)["max_tokens"]?.jsonPrimitive?.int)
        assertEquals(48_000, body(other)["max_tokens"]?.jsonPrimitive?.int)
        // OpenAI's reasoning models take only max_completion_tokens.
        assertEquals(48_000, body(openAi)["max_completion_tokens"]?.jsonPrimitive?.int)
        assertNull(body(openAi)["max_tokens"])
        // Anthropic needs one even when none is given.
        assertEquals(DEFAULT_OUTPUT_TOKENS, body(anthropic, maxTokens = null)["max_tokens"]?.jsonPrimitive?.int)
    }

    @Test
    fun deepMapsToEachProvidersKnobAndStandardSendsNothing() {
        assertEquals(
            "high",
            body(openRouter, effort = WritingEffort.DEEP)["reasoning"]
                ?.jsonObject
                ?.get("effort")
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals("high", body(openAi, effort = WritingEffort.DEEP)["reasoning_effort"]?.jsonPrimitive?.content)
        assertEquals("high", body(other, effort = WritingEffort.DEEP)["reasoning_effort"]?.jsonPrimitive?.content)
        val thinking = body(anthropic, effort = WritingEffort.DEEP)["thinking"]?.jsonObject
        assertEquals("enabled", thinking?.get("type")?.jsonPrimitive?.content)
        assertEquals(DEEP_THINKING_TOKENS, thinking?.get("budget_tokens")?.jsonPrimitive?.int)
        // The budget stays under the call's limit (a plan call is small).
        assertEquals(
            6_000,
            body(anthropic, maxTokens = 12_000, effort = WritingEffort.DEEP)["thinking"]
                ?.jsonObject
                ?.get("budget_tokens")
                ?.jsonPrimitive
                ?.int,
        )
        for (url in listOf(openRouter, anthropic, openAi, other)) {
            val plain = body(url)
            listOf("reasoning", "reasoning_effort", "thinking").forEach { assertNull(plain[it], "$url $it") }
        }
    }

    @Test
    fun theCacheMarkGoesOnlyWhereItIsDocumented() {
        val marked = body(openRouter, model = "anthropic/claude-opus-4.8")["messages"]!!.jsonArray[1].jsonObject["content"] as JsonArray
        assertEquals("header and digest", marked[0].jsonObject["text"]?.jsonPrimitive?.content)
        assertEquals(
            "ephemeral",
            marked[0]
                .jsonObject["cache_control"]
                ?.jsonObject
                ?.get("type")
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals("\n\nplan it", marked[1].jsonObject["text"]?.jsonPrimitive?.content)
        assertFalse(marked[1].jsonObject.containsKey("cache_control"))
        // The system message carries no mark; every other provider gets plain text.
        assertTrue(body(openRouter, model = "anthropic/claude-opus-4.8")["messages"]!!.jsonArray[0].jsonObject["content"] is JsonPrimitive)
        for ((url, model) in listOf(openRouter to "openai/gpt-5", anthropic to "claude-opus-4-8", openAi to "gpt-5")) {
            val content = body(url, model)["messages"]!!.jsonArray[1].jsonObject["content"]
            assertEquals("header and digest\n\nplan it", content?.jsonPrimitive?.content, url)
        }
    }

    @Test
    fun theOutputLimitIsTheModelsOwnCappedElseTheDefault() {
        assertEquals(64_000, outputTokenLimit(AgentModel("a", maxOutputTokens = 128_000), 50_000))
        assertEquals(16_384, outputTokenLimit(AgentModel("a", maxOutputTokens = 16_384), 50_000))
        assertEquals(46_000, outputTokenLimit(AgentModel("a", contextLength = 98_000), 50_000))
        assertEquals(MIN_PAGE_TOKENS, outputTokenLimit(AgentModel("a", contextLength = 32_000), 50_000))
        assertEquals(DEFAULT_OUTPUT_TOKENS, outputTokenLimit(AgentModel("a"), 50_000))
        assertEquals(DEFAULT_OUTPUT_TOKENS, outputTokenLimit(null, 50_000))
        assertEquals(PLAN_MAX_TOKENS, planTokenLimit(AgentModel("a", maxOutputTokens = 128_000)))
        assertEquals(8_192, planTokenLimit(AgentModel("a", maxOutputTokens = 8_192)))
    }

    @Test
    fun theModelListCarriesEachModelsLimits() {
        val models =
            readModels(
                """{"data":[
                  {"id":"anthropic/claude-opus-4.8","context_length":1000000,"top_provider":{"context_length":1000000,"max_completion_tokens":128000}},
                  {"id":"claude-opus-4-8","type":"model","max_input_tokens":1000000,"max_tokens":128000},
                  {"id":"tiny","context_length":32768},
                  {"id":"gpt-5"}]}""",
            ).associateBy { it.id }
        assertEquals(128_000L, models.getValue("anthropic/claude-opus-4.8").maxOutputTokens)
        assertEquals(1_000_000L, models.getValue("anthropic/claude-opus-4.8").contextLength)
        assertEquals(128_000L, models.getValue("claude-opus-4-8").maxOutputTokens)
        assertEquals(32_768L, models.getValue("tiny").contextLength)
        assertNull(models.getValue("gpt-5").maxOutputTokens)
    }

    @Test
    fun anOpusClassModelRanksFirst() {
        val ranked = rankModels(listOf("openai/gpt-5", "anthropic/claude-sonnet-5.5", "anthropic/claude-opus-5.5", "anthropic/claude-haiku-5.5"))
        assertEquals("anthropic/claude-opus-5.5", ranked.first())
        assertEquals("anthropic/claude-sonnet-5.5", ranked[1])
    }

    @Test
    fun theEffortIsTheSchedulesParamAndStandardIsItsAbsence() {
        val none = observerSettingsOnly()
        assertEquals(WritingEffort.STANDARD, FrontPageParams.effort(none))
        val deep = FrontPageParams.withEffort(none, WritingEffort.DEEP)
        assertEquals(WritingEffort.DEEP, FrontPageParams.effort(deep))
        assertEquals(listOf("deep"), deep.paramValues(FrontPageParams.EFFORT))
        // Understood here, though Quartz 2.17's ObserverParams doesn't list it.
        assertTrue(FrontPageParams.understands(deep))
        assertEquals(emptyList(), FrontPageParams.unknownParams(deep))
        val standard = FrontPageParams.withEffort(deep, WritingEffort.STANDARD)
        assertNull(standard.param(FrontPageParams.EFFORT))
        assertEquals(WritingEffort.STANDARD, WritingEffort.of("high"))
        assertEquals(WritingEffort.DEEP, WritingEffort.of(" Deep "))
        assertFalse(FrontPageParams.understands(deep.withParam("size", "big")))
    }
}
