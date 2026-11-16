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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size14Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size35dp

/**
 * Configuration for zap button behavior and appearance. Never mutated after construction, so it
 * is marked immutable for Compose (its `Modifier` and `List` fields would otherwise make it unstable).
 */
@Immutable
data class ZapButtonConfig(
    val grayTint: Color = Color.Gray,
    val iconSize: Dp = Size35dp,
    val iconSizeModifier: Modifier = Size20Modifier,
    val animationModifier: Modifier = Size14Modifier,
    val showUserFinderSubscription: Boolean = false,
    val zapAmountChoices: List<Long>? = null,
    val thankYouText: String? = null,
    val buttonText: String? = null,
)

/**
 * Callbacks for zap button events
 */
data class ZapButtonCallbacks(
    val onZapComplete: ((Boolean) -> Unit)? = null,
)
