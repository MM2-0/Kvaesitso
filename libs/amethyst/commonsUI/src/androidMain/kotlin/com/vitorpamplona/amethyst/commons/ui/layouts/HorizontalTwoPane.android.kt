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
package com.vitorpamplona.amethyst.commons.ui.layouts

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.window.layout.FoldingFeature
import com.google.accompanist.adaptive.FoldAwareConfiguration
import com.google.accompanist.adaptive.HorizontalTwoPaneStrategy
import com.google.accompanist.adaptive.TwoPane
import com.google.accompanist.adaptive.calculateDisplayFeatures

@Composable
actual fun HorizontalTwoPane(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
    splitFraction: Float,
    modifier: Modifier,
    onDividerDrag: ((deltaDp: Float) -> Unit)?,
) {
    // Fold detection needs an Activity window. Without one (a preview, a ComposeView hosted
    // elsewhere) there is no fold to avoid, so split the width plainly.
    val activity = LocalContext.current.findActivity()
    if (activity == null) {
        ProportionalTwoPane(first, second, splitFraction, modifier, onDividerDrag)
        return
    }

    val displayFeatures = calculateDisplayFeatures(activity)

    // Without a fold, a resizable split is the plain one with its splitter; along a fold the
    // hinge decides where the panes meet.
    if (onDividerDrag != null && displayFeatures.none { it is FoldingFeature }) {
        ProportionalTwoPane(first, second, splitFraction, modifier, onDividerDrag)
        return
    }

    val strategy = remember(splitFraction) { HorizontalTwoPaneStrategy(splitFraction = splitFraction) }

    TwoPane(
        first = first,
        second = second,
        strategy = strategy,
        displayFeatures = displayFeatures,
        foldAwareConfiguration = FoldAwareConfiguration.VerticalFoldsOnly,
        modifier = modifier,
    )
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
