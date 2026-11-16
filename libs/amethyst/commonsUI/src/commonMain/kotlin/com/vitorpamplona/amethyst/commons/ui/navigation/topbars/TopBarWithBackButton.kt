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
package com.vitorpamplona.amethyst.commons.ui.navigation.topbars

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ArrowBackIcon

private val NoActions: @Composable RowScope.() -> Unit = {}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBarWithBackButton(
    caption: String,
    nav: INav,
    /** What a screen reader says for [caption] when the caption is short for more ("Today" for its full date). */
    captionDescription: String? = null,
    actions: @Composable RowScope.() -> Unit = NoActions,
) {
    // In a tab or a window of its own the strip or the title bar already names the screen, and there
    // is no back arrow: a bar holding nothing but that name again is left out.
    if (nav.titleShownOutside() && actions === NoActions) return

    ShorterTopAppBar(
        title = {
            Text(
                text = caption,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                modifier = if (captionDescription != null) Modifier.semantics { contentDescription = captionDescription } else Modifier,
            )
        },
        actions = actions,
        navigationIcon = {
            // No arrow where there is nothing to go back to: a bottom-nav root, a tab, a window.
            if (nav.canPop()) {
                IconButton(nav::popBack) {
                    ArrowBackIcon()
                }
            }
        },
    )
}
