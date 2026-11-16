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
package com.vitorpamplona.amethyst.commons.ui.payments

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import com.vitorpamplona.amethyst.commons.model.payments.PaymentTargetTypes
import com.vitorpamplona.quartz.nipA3PaymentTargets.PaymentTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the device can do with one `payto` target type. */
@Immutable
data class PayToAppInfo(
    /** An installed app accepts the hand-off URI. */
    val resolves: Boolean,
    /** The chosen app's own name, null when no single default app applies. */
    val label: String? = null,
    /** The chosen app's launcher icon, already masked and sized. */
    val icon: ImageBitmap? = null,
)

/**
 * Answers "can anything on this device open this payment target, and what does it look like?"
 * for the zap picker's hand-off chip. Android asks the package manager; platforms that cannot
 * tell answer nothing, so only web targets (which any browser opens) get a chip.
 */
interface PayToAppProbe {
    /** Every type probed so far, keyed by [PaymentTargetTypes.probeKeyFor]. */
    val flow: StateFlow<Map<String, PayToAppInfo>>

    /** Synchronous read for code that runs inside `remember {}`. */
    fun peek(rawType: String): PayToAppInfo? = flow.value[PaymentTargetTypes.probeKeyFor(rawType)]

    /**
     * Probes every distinct type in [targets] and merges the answers into [flow]. [iconPx] is
     * the size the chip draws at. Blocking: call it from `Dispatchers.IO`.
     */
    fun warm(
        targets: List<PaymentTarget>,
        iconPx: Int,
    )

    object None : PayToAppProbe {
        override val flow: StateFlow<Map<String, PayToAppInfo>> = MutableStateFlow<Map<String, PayToAppInfo>>(emptyMap()).asStateFlow()

        override fun warm(
            targets: List<PaymentTarget>,
            iconPx: Int,
        ) {}
    }
}
