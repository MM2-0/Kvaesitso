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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.listDetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.ui.components.ScopedViewModelStore
import com.vitorpamplona.amethyst.commons.ui.layouts.HorizontalTwoPane
import com.vitorpamplona.amethyst.commons.ui.layouts.WidthClass
import com.vitorpamplona.amethyst.commons.ui.layouts.widthClassOf
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.DetailPaneNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.TwoPaneNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.rememberDetailPaneSelection
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.unicodeEmoji.ui.EmojiPanelHost
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * A chat list that opens its chats beside itself when there is room, like the Messages screen.
 *
 * On a Compact pane this is just [list] on [nav]: tapping a chat pushes it. Wider, [list] takes the
 * left pane and the chats it opens ([isDetailRoute]) render in the right one through
 * [ChatDetailPane], so the list stays put while the user hops between chats. The decision reads
 * the pane this screen occupies, not the window, for the same reason `MessagesScreen` does.
 *
 * [isDetailRoute] should be a top-level function reference: it is captured by the remembered nav.
 */
@Composable
fun ChatListDetailScreen(
    isDetailRoute: (Route) -> Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
    list: @Composable (INav) -> Unit,
) {
    // Above the size switch, so the open chat outlives a collapse to Compact (handed over below).
    val selection = rememberDetailPaneSelection()

    // Painted here because nothing else paints the detail pane while no chat is open: the list
    // brings its own Scaffold, the empty pane does not, and the window showed through it (Swing's
    // light grey on desktop). MessagesTwoPane gets the same from its Scaffold.
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val paneWidthClass = widthClassOf(maxWidth)

        if (paneWidthClass == WidthClass.Compact) {
            CollapseOpenChatToFullScreen(selection, nav)
            list(nav)
        } else {
            val scope = rememberCoroutineScope()
            val twoPaneNav = remember(nav, selection) { TwoPaneNav(nav, scope, isDetailRoute, selection) }
            val detailNav = remember(twoPaneNav) { DetailPaneNav(twoPaneNav) }
            val split = listDetailSplit(paneWidthClass, maxWidth.value)

            HorizontalTwoPane(
                first = { list(twoPaneNav) },
                second = {
                    selection.value?.let { route ->
                        PaneScopedChat(route) {
                            ChatDetailPane(route, accountViewModel, detailNav)
                        }
                    }
                },
                splitFraction = split.fraction,
                modifier = Modifier.fillMaxSize(),
                onDividerDrag = split.onDrag,
            )
        }
    }
}

/**
 * A chat in a detail pane, with what a nav entry would give it: a fresh composition per chat (not
 * every chat screen keys its remembered UI state, sheets and jump targets, by chat) and its own view
 * model store, cleared when the user moves to another chat. Without the store, every chat opened in
 * the pane kept its feed view models alive in the list's entry, and the chats that create their
 * composer model unkeyed (NIP-17 rooms, NIP-28 channels) shared one composer across chats.
 */
@Composable
fun PaneScopedChat(
    route: Route,
    content: @Composable () -> Unit,
) {
    key(route) {
        // The chat's composer docks the emoji panel under this pane, not under the list beside it.
        EmojiPanelHost {
            ScopedViewModelStore(content)
        }
    }
}

/**
 * When the pane shrinks to Compact (a window resize, a fold, the notification panel docking) while a
 * chat is open beside the list, opens that chat as a full screen instead of silently dropping it.
 */
@Composable
fun CollapseOpenChatToFullScreen(
    selection: MutableState<Route?>,
    nav: INav,
) {
    LaunchedEffect(selection) {
        val open = selection.value ?: return@LaunchedEffect
        selection.value = null
        nav.nav(open)
    }
}

/** The list pane's share of the width by default: a third when Expanded, a little more when Medium. */
fun listDetailSplitFraction(widthClass: WidthClass): Float = if (widthClass == WidthClass.Expanded) 1f / 3f else 1f / 2.5f

/** Where a list/detail split falls ([fraction] of the width for the list) and what a drag of its divider does. */
class ListDetailSplit(
    val fraction: Float,
    val onDrag: (deltaDp: Float) -> Unit,
)

/**
 * The split of a list/detail pane [paneWidth] dp wide: the list at the width the user dragged it to
 * (kept per device, shared by Messages and every chat list), or by default
 * [listDetailSplitFraction]; the list and the chat each keep at least [MIN_LIST_DETAIL_PANE_DP].
 */
@Composable
fun listDetailSplit(
    widthClass: WidthClass,
    paneWidth: Float,
): ListDetailSplit {
    val prefs = LocalAppServices.current.paneWidthPrefs
    val saved by prefs.flow.collectAsStateWithLifecycle()
    val listWidth = fitListWidth(paneWidth, saved.chatList ?: (paneWidth * listDetailSplitFraction(widthClass)))
    return ListDetailSplit(listWidth / paneWidth) { delta -> prefs.setChatList(fitListWidth(paneWidth, listWidth + delta)) }
}

/** The list keeps at least this much, in dp, and so does the chat beside it. */
const val MIN_LIST_DETAIL_PANE_DP = 260f

/** [width] for the list in a pane [paneWidth] dp wide, leaving the chat its room. */
fun fitListWidth(
    paneWidth: Float,
    width: Float,
): Float = width.coerceIn(MIN_LIST_DETAIL_PANE_DP, maxOf(MIN_LIST_DETAIL_PANE_DP, paneWidth - MIN_LIST_DETAIL_PANE_DP))
