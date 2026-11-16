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
import kotlin.test.assertTrue

class FrontPageCostTest {
    private val openRouter =
        """{"data":[{"id":"anthropic/claude-sonnet-4.5","pricing":{"prompt":"0.000003","completion":"0.000015"}},{"id":"free/model","pricing":{"prompt":"0","completion":"0"}},{"id":"plain"}]}"""

    @Test
    fun pricesAreReadWhereTheEndpointGivesThem() {
        val models = readModels(openRouter).associateBy { it.id }
        assertEquals(0.000003, models.getValue("anthropic/claude-sonnet-4.5").promptPerToken)
        assertEquals(0.000015, models.getValue("anthropic/claude-sonnet-4.5").completionPerToken)
        assertTrue(models.getValue("free/model").priced)
        // No pricing is "not said", never "free".
        assertNull(models.getValue("plain").promptPerToken)
        assertEquals(listOf("gemini-2.5-pro"), readModels("""{"models":[{"name":"models/gemini-2.5-pro"}]}""").map { it.id })
    }

    @Test
    fun theEstimateCountsThePlanPassAndThePage() {
        val sonnet = readModels(openRouter).first()
        // 400,000 characters in is 100,000 tokens, read by the plan and again by the page with
        // the posts in full; a plan and a page out.
        val estimate = estimateCost(sonnet, digestChars = 300_000, briefChars = 100_000)!!
        assertEquals((200_000 + approxTokens(EXPECTED_FULL_CHARS)) * 0.000003 + (PLAN_OUTPUT_TOKENS + EXPECTED_OUTPUT_TOKENS) * 0.000015, estimate, 1e-9)
        assertEquals("$0.92", formatUsd(estimate))
        // Deep reasons in both calls, and says so in the price.
        val deep = estimateCost(sonnet, digestChars = 300_000, briefChars = 100_000, effort = WritingEffort.DEEP)!!
        assertEquals(estimate + 2 * DEEP_REASONING_TOKENS * 0.000015, deep, 1e-9)
    }

    @Test
    fun noPricesNoEstimate() {
        assertNull(estimateCost(AgentModel("plain"), 100_000))
        assertNull(estimateCost(null, 100_000))
        assertNull(actualCost(AgentModel("plain"), Usage(10, 10)))
    }

    @Test
    fun theActualCostIsTheReportedUsageAtTheListedPrices() {
        val sonnet = readModels(openRouter).first()
        assertEquals(20_000 * 0.000003 + 8_000 * 0.000015, actualCost(sonnet, Usage(20_000, 8_000))!!, 1e-9)
        // A provider that reports no usage gets no cost, rather than a guess.
        assertNull(actualCost(sonnet, Usage(null, 8_000)))
    }

    @Test
    fun dollarsToTheCent() {
        assertEquals("<$0.01", formatUsd(0.004))
        assertEquals("$0.08", formatUsd(0.0849))
        assertEquals("$1.20", formatUsd(1.2))
        assertEquals("$12.00", formatUsd(11.999))
    }
}
