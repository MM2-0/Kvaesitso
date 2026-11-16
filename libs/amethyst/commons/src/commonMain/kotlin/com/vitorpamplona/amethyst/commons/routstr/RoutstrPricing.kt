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

import com.vitorpamplona.amethyst.commons.frontpage.WritingEffort
import com.vitorpamplona.amethyst.commons.frontpage.editionTokens
import com.vitorpamplona.amethyst.commons.frontpage.systemPrompt
import kotlin.math.ceil

/** Routstr prices in sats, with the same token estimate the USD estimate uses. */
object RoutstrPricing {
    /**
     * One Observer edition with [sats]: `prompt_tokens * prompt + completion_tokens * completion
     * + request` for each of its two calls, rounded up to a whole sat. The tokens are the USD
     * estimate's ([editionTokens]: a digest of [digestChars], the plan and the page, Deep's
     * reasoning). Null when the node gives no prices.
     */
    fun editionSats(
        sats: SatsPricing?,
        digestChars: Int,
        briefChars: Int = systemPrompt().length,
        effort: WritingEffort = WritingEffort.STANDARD,
    ): Long? {
        val prompt = sats?.prompt ?: return null
        val completion = sats.completion ?: return null
        val tokens = editionTokens(digestChars, briefChars, effort)
        val cost = tokens.input * prompt + tokens.output * completion + 2 * (sats.request ?: 0.0)
        if (!cost.isFinite() || cost < 0) return null
        return ceil(cost).toLong()
    }

    /** [SatsPricing.maxCost] rounded up to a whole sat: what the node reserves before it answers. */
    fun maxCostSats(sats: SatsPricing?): Long? = sats?.maxCost?.takeIf { it.isFinite() && it >= 0 }?.let { ceil(it).toLong() }

    /**
     * Whether [balanceMsats] may not cover one edition: under the node's reserve for one request
     * ([SatsPricing.maxCost]), or under the edition's estimate. False when neither is known.
     */
    fun isLow(
        balanceMsats: Long?,
        sats: SatsPricing?,
        estimateSats: Long?,
    ): Boolean {
        val balance = balanceMsats ?: return false
        val maxCost = maxCostSats(sats)
        if (maxCost != null && balance < maxCost * 1000) return true
        if (estimateSats != null && balance < estimateSats * 1000) return true
        return false
    }
}
