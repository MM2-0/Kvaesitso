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
package com.vitorpamplona.amethyst.commons.hashtags

import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.collections.List as ____KtList

object CustomHashTagIcons

private var customHashTagIconsAllIconsCache: ____KtList<ImageVector>? = null

val CustomHashTagIcons.AllIcons: ____KtList<ImageVector>
    get() {
        if (customHashTagIconsAllIconsCache != null) {
            return customHashTagIconsAllIconsCache!!
        }
        customHashTagIconsAllIconsCache =
            listOf(
                Lightning,
                Mate,
                Skull,
                Coffee,
                Plebs,
                Weed,
                Cashu,
                Grownostr,
                Footstr,
                Flowerstr,
                Btc,
                Zap,
                Tunestr,
                Nostr,
                Gamestr,
            )
        return customHashTagIconsAllIconsCache!!
    }
