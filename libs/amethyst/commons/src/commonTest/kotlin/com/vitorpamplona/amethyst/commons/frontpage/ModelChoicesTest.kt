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
import kotlin.test.assertNull

class ModelChoicesTest {
    private val openRouter =
        listOf(
            "anthropic/claude-sonnet-4.5",
            "openai/gpt-4o-mini",
            "anthropic/claude-3.5-haiku",
            "meta-llama/llama-3.3-70b-instruct:free",
            "google/gemini-2.5-pro",
            "openai/gpt-5",
            "liquid/lfm-40b",
            "mystery-model",
        )

    @Test
    fun groupsByVendorInRankedOrder() {
        val groups = ModelChoices.group(openRouter)
        assertEquals(listOf("Anthropic", "OpenAI", "Meta", "Google", "Liquid", ""), groups.map { it.label })
        assertEquals(listOf("anthropic/claude-sonnet-4.5", "anthropic/claude-3.5-haiku"), groups[0].models.map { it.id })
        assertEquals(listOf("openai/gpt-4o-mini", "openai/gpt-5"), groups[1].models.map { it.id })
        // Nobody can tell whose it is: last, with the vendor left to the screen to name.
        assertEquals("", groups.last().vendor)
    }

    @Test
    fun aProviderOwnListIsGroupedByName() {
        val groups = ModelChoices.group(listOf("claude-opus-4-1-20250805", "gpt-4.1", "llama3.3:latest", "gemma3"))
        assertEquals(listOf("Anthropic", "OpenAI", "Meta", "Google"), groups.map { it.label })
    }

    @Test
    fun namesAreReadable() {
        assertEquals("Claude Sonnet 4.5", ModelChoices.readableName("anthropic/claude-sonnet-4-5-20250929"))
        assertEquals("GPT 4o Mini", ModelChoices.readableName("openai/gpt-4o-2024-08-06-mini"))
        assertEquals("Llama 3.3 70B Instruct (free)", ModelChoices.readableName("meta-llama/llama-3.3-70b-instruct:free"))
        assertEquals("Gemini 2.5 Pro", ModelChoices.readableName("google/gemini-2.5-pro"))
        assertEquals("Llama3.3", ModelChoices.readableName("llama3.3:latest"))
        assertEquals("o4 Mini", ModelChoices.readableName("openai/o4-mini"))
        assertEquals("DeepSeek R1 (free)", ModelChoices.readableName("deepseek/deepseek-r1:free"))
        assertEquals("Gemma 3 27B IT", ModelChoices.readableName("google/gemma-3-27b-it"))
        // The endpoint's own name wins, without the vendor it already sits under.
        assertEquals("Claude Sonnet 4.5", ModelChoices.readableName("anthropic/claude-sonnet-4.5", "Anthropic: Claude Sonnet 4.5"))
    }

    @Test
    fun theListedNameComesFromTheEndpoint() {
        val listed = readModels("""{"data":[{"id":"x-ai/grok-4","name":"xAI: Grok 4"},{"name":"models/gemini-2.5-pro"}]}""")
        assertEquals("Grok 4", ModelChoices.choices(listed.map { it.id }, listed.associateBy { it.id }).first().name)
        // Gemini's `name` is its id, not a label.
        assertNull(listed.last().name)
    }

    @Test
    fun searchMatchesEveryWordInIdNameOrVendor() {
        assertEquals(listOf("anthropic/claude-3.5-haiku"), ModelChoices.group(openRouter, query = "anthropic haiku").flatMap { it.models }.map { it.id })
        assertEquals(listOf("meta-llama/llama-3.3-70b-instruct:free"), ModelChoices.group(openRouter, query = "META 70b").flatMap { it.models }.map { it.id })
        assertEquals(emptyList(), ModelChoices.group(openRouter, query = "nothing-like-it"))
    }

    @Test
    fun aTypedIdIsOfferedOnlyWhenTheListLacksIt() {
        assertEquals("my-finetune", ModelChoices.typedId(" my-finetune ", openRouter))
        assertNull(ModelChoices.typedId("openai/gpt-5", openRouter))
        assertNull(ModelChoices.typedId("two words", openRouter))
        assertNull(ModelChoices.typedId("", openRouter))
    }

    @Test
    fun theCatalogFiltersAsTheGroupsDo() {
        val catalog = ModelChoices.catalog(openRouter)
        for (query in listOf("", "claude", "OPEN ai", "70b free", "nothing-like-it", "liquid")) {
            assertEquals(ModelChoices.group(openRouter, query = query), catalog.group(query), query)
        }
        // Read once: the search text is the id, the name and the vendor, lower case.
        val entry = catalog.entries.first { it.choice.id == "anthropic/claude-sonnet-4.5" }
        assertEquals("anthropic", entry.vendor)
        assertEquals("Anthropic", entry.label)
        assertEquals(entry.haystack, entry.haystack.lowercase())
    }
}
