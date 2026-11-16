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
package com.vitorpamplona.amethyst.commons.routstr

import com.vitorpamplona.amethyst.commons.frontpage.DEFAULT_DIGEST_BUDGET
import com.vitorpamplona.amethyst.commons.frontpage.WritingEffort
import com.vitorpamplona.amethyst.commons.frontpage.editionTokens
import com.vitorpamplona.amethyst.commons.frontpage.estimateCost
import com.vitorpamplona.amethyst.commons.frontpage.readModels
import com.vitorpamplona.amethyst.commons.frontpage.systemPrompt
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `GET /v1/models` in OpenRouter's shape with `sats_pricing`, and the sats per edition from it. */
class RoutstrPricingTest {
    private val body = javaClass.getResourceAsStream("/routstr/models-sample.json")!!.bufferedReader().readText()

    @Test
    fun readsTheModelsShape() {
        val models = RoutstrJson.models(body)
        assertEquals(4, models.size)
        val sonnet = models.first { it.id == "anthropic/claude-sonnet-5.5" }
        assertEquals("Anthropic: Claude Sonnet 5.5", sonnet.name)
        assertEquals(1_000_000L, sonnet.contextLength)
        assertEquals(64_000L, sonnet.maxCompletionTokens)
        assertEquals(0.000003, sonnet.usdPrompt)
        assertEquals(SatsPricing(0.003, 0.015, 0.0, 4307.2), sonnet.sats)
        // Numbers as strings, as some nodes send them.
        assertEquals(SatsPricing(0.00025, 0.002, 1.0, 812.5), models.first { it.id == "openai/gpt-5.2-mini" }.sats)
        assertFalse(models.first { it.id.startsWith("black-forest") }.textOutput)
        assertFalse(models.first { it.id.startsWith("meta-llama") }.enabled)
        assertNull(models.first { it.id.startsWith("meta-llama") }.sats)
    }

    @Test
    fun theSetupScreenListsTextModelsCheapestFirst() {
        val listed = RoutstrSetupViewModel.textModels(RoutstrJson.models(body))
        assertEquals(listOf("openai/gpt-5.2-mini", "anthropic/claude-sonnet-5.5", "meta-llama/llama-5-70b"), listed.map { it.id })
    }

    @Test
    fun editionSatsUsesTheUsdEstimatesTokens() {
        val sonnet = RoutstrJson.models(body).first { it.id == "anthropic/claude-sonnet-5.5" }
        val tokens = editionTokens(DEFAULT_DIGEST_BUDGET, systemPrompt().length)
        val expected = ceil(tokens.input * 0.003 + tokens.output * 0.015 + 0.0).toLong()
        assertEquals(expected, RoutstrPricing.editionSats(sonnet.sats, DEFAULT_DIGEST_BUDGET))
        assertEquals(expected, RoutstrSetupViewModel.editionSats(sonnet))
        // A few hundred sats an edition, against a 4,308-sat reserve: max_cost dominates.
        assertTrue(expected in 300..1_500, "$expected")
        assertEquals(4308L, RoutstrPricing.maxCostSats(sonnet.sats))

        // The request fee is added once per call: the plan and the page.
        val mini = RoutstrJson.models(body).first { it.id == "openai/gpt-5.2-mini" }
        assertEquals(ceil(tokens.input * 0.00025 + tokens.output * 0.002 + 2.0).toLong(), RoutstrPricing.editionSats(mini.sats, DEFAULT_DIGEST_BUDGET))
        // Deep costs more.
        assertTrue(RoutstrPricing.editionSats(sonnet.sats, DEFAULT_DIGEST_BUDGET, effort = WritingEffort.DEEP)!! > expected)

        assertNull(RoutstrPricing.editionSats(null, DEFAULT_DIGEST_BUDGET))
    }

    @Test
    fun theWritersModelListCarriesSatsAndKeepsUsd() {
        val models = readModels(body).associateBy { it.id }
        val sonnet = assertNotNull(models["anthropic/claude-sonnet-5.5"])
        assertTrue(sonnet.pricedInSats)
        assertEquals(0.015, sonnet.sats?.completion)
        // The USD estimate is untouched.
        assertNotNull(estimateCost(sonnet, DEFAULT_DIGEST_BUDGET))
        assertFalse(models.getValue("meta-llama/llama-5-70b").pricedInSats)
    }

    @Test
    fun lowBalanceIsUnderTheReserveOrTheEstimate() {
        val sats = SatsPricing(0.003, 0.015, 0.0, 4307.2)
        // A 1,000-sat key can't run it: under the 4,308-sat reserve, though over the edition.
        assertTrue(RoutstrPricing.isLow(1_000_000, sats, estimateSats = 370))
        assertFalse(RoutstrPricing.isLow(5_000_000, sats, estimateSats = 370))
        assertTrue(RoutstrPricing.isLow(300_000, SatsPricing(0.003, 0.015, 0.0, null), estimateSats = 370))
        assertFalse(RoutstrPricing.isLow(null, sats, 370))
        assertFalse(RoutstrPricing.isLow(10, null, null))
    }
}
