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
package com.vitorpamplona.amethyst.commons.ui.navigation.bottombars

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalScreenLayout
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav

/**
 * Reserves the visual space the `AppBottomBar` occupies on root tab entries so a
 * FloatingActionButton stays at the same vertical position whether or not the bar is
 * rendered. `AppBottomBar` hides itself off [INav.showsBottomBar] entries (in-app
 * navigations) — without this padding the FAB drops by `AppBottomBarHeight` there.
 *
 * The system-navigation-bar inset is already handled by the surrounding Scaffold, so
 * only the bar's content height needs to be reserved.
 */
val FABPaddingFromBottom = 30.dp

@Composable
fun Modifier.fabBottomBarPadding(nav: INav): Modifier =
    if (!nav.showsBottomBar() || LocalScreenLayout.current.isLargeScreen) {
        // In-app pushes hide the bar on phones; large screens never render it at all.
        padding(bottom = FABPaddingFromBottom)
    } else {
        this
    }

/**
 * Convenience wrapper that places [content] in a [Box] with [fabBottomBarPadding] applied.
 * Use this around `floatingActionButton` / `floatingButton` slots whose body is a reusable
 * named FAB composable that does not accept a `modifier` parameter.
 */
@Composable
fun FabBottomBarPadded(
    nav: INav,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fabBottomBarPadding(nav)) {
        content()
    }
}
