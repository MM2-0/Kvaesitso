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
package com.vitorpamplona.amethyst.commons.drss

import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * The playback speed the user picked for each feed, by the show's pubkey. drss.io keeps it in the
 * browser, not in an event, so it stays on this device too; a feed without one plays at 1x.
 */
@Stable
class DrssListenRates {
    private val rates = MutableStateFlow<Map<HexKey, Float>>(emptyMap())
    val flow: StateFlow<Map<HexKey, Float>> = rates

    fun of(feed: HexKey): Float = rates.value[feed] ?: 1f

    fun set(
        feed: HexKey,
        rate: Float,
    ) {
        if (rate !in RATES) return
        rates.update { if (rate == 1f) it - feed else it + (feed to rate) }
    }

    fun restore(saved: Map<HexKey, Float>) {
        rates.value = saved.filterValues { it in RATES && it != 1f }
    }

    companion object {
        /** The speeds drss.io offers. */
        val RATES = listOf(1f, 1.25f, 1.5f, 2f)
    }
}
