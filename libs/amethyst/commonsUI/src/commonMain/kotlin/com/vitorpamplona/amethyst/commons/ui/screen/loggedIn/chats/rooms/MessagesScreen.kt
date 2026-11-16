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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.rooms

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.commons.ui.layouts.WidthClass
import com.vitorpamplona.amethyst.commons.ui.layouts.widthClassOf
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.rememberDetailPaneSelection
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.listDetail.CollapseOpenChatToFullScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.rooms.singlepane.MessagesSinglePane
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.rooms.twopane.MessagesTwoPane
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun MessagesScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Decide single- vs two-pane from the pane this screen actually occupies, not the
    // window: on large screens the shell already spends width on the rail / permanent
    // drawer / notification panel, so window-level size classes would overestimate.
    // widthClassOf keeps the Compact/Medium/Expanded breakpoints in one place (the shell's)
    // instead of restating 600/840 here.
    // Above the size switch, so the open chat outlives a collapse to Compact (handed over below).
    val selection = rememberDetailPaneSelection()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val paneWidthClass = widthClassOf(maxWidth)

        if (paneWidthClass == WidthClass.Compact) {
            CollapseOpenChatToFullScreen(selection, nav)
            MessagesSinglePane(
                knownFeedContentState = accountViewModel.feedStates.dmKnown,
                newFeedContentState = accountViewModel.feedStates.dmNew,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        } else {
            MessagesTwoPane(
                knownFeedContentState = accountViewModel.feedStates.dmKnown,
                newFeedContentState = accountViewModel.feedStates.dmNew,
                widthSizeClass = paneWidthClass,
                selection = selection,
                paneWidth = maxWidth.value,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}
