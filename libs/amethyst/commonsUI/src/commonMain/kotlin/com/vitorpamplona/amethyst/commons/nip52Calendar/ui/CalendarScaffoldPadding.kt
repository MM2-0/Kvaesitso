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
package com.vitorpamplona.amethyst.commons.nip52Calendar.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalDisappearingScaffoldPadding

/**
 * Applies the surrounding [com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold]'s reserved
 * top/bottom space as padding. The scaffold places content at y=0 by design — it lets feeds
 * scroll *behind* the disappearing bar — but the month/week/day views are static-headered grids,
 * so without this modifier the grid header would render under the top app bar. Outside a
 * scaffold the local default is zero and the modifier is a no-op.
 */
@Composable
fun Modifier.disappearingScaffoldPadding(): Modifier =
    composed {
        val padding = LocalDisappearingScaffoldPadding.current
        this.padding(padding)
    }
