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
package com.vitorpamplona.amethyst.commons.ui.note.hovercard

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.ui.components.LocalReadOnlyPreview
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.header.UserProfileDropDownMenu
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

/** How long the mouse rests on a face or name before its card opens. */
private val OpenDelay = 250.milliseconds

/** Time to cross the gap between the face and the card, or to come back, before it closes. */
private val CloseDelay = 300.milliseconds

private val CardGap = 4.dp

/**
 * Opens and closes one [UserHoverCard]. Resting on the anchor opens it after [OpenDelay];
 * leaving both the anchor and the card closes it after [CloseDelay], which also covers the
 * gap between the two.
 */
@Stable
class UserHoverCardState(
    private val scope: CoroutineScope,
) {
    var isOpen by mutableStateOf(false)
        private set

    /** The profile's action menu, asked for from the card. */
    var isMenuOpen by mutableStateOf(false)

    /**
     * Whether the menu has ever been opened here. Feeds hold two anchors per note, so the menu
     * isn't composed until first asked for; from then on it stays, because its nickname dialog
     * keeps running after the menu itself closes.
     */
    var wasMenuOpened by mutableStateOf(false)
        private set

    private var pending: Job? = null

    fun openMenu() {
        dismiss()
        wasMenuOpened = true
        isMenuOpen = true
    }

    fun onAnchorEnter() {
        pending?.cancel()
        if (!isOpen) {
            pending =
                scope.launch {
                    delay(OpenDelay)
                    isOpen = true
                }
        }
    }

    fun onCardEnter() {
        pending?.cancel()
    }

    fun onPointerExit() {
        pending?.cancel()
        if (isOpen) {
            pending =
                scope.launch {
                    delay(CloseDelay)
                    isOpen = false
                }
        }
    }

    fun dismiss() {
        pending?.cancel()
        isOpen = false
    }
}

/**
 * Wraps a face or a name so that resting the mouse on it opens [user]'s [UserHoverCard].
 *
 * Mouse only: touch has no hover, so phones and touch tablets never see the card and keep
 * the tap-to-open-profile they had. Your own face and read-only previews (signer prompts)
 * show no card.
 */
@Composable
fun UserHoverCardAnchor(
    user: User,
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (LocalReadOnlyPreview.current || accountViewModel.isLoggedUser(user)) {
        Box(modifier) { content() }
        return
    }

    val scope = rememberCoroutineScope()
    val state = remember(user) { UserHoverCardState(scope) }

    Box(modifier.anchorHover(state)) {
        content()

        if (state.isOpen) {
            val gap = with(LocalDensity.current) { CardGap.roundToPx() }
            val positionProvider = remember(gap) { BelowOrAboveAnchor(gap) }

            Popup(
                popupPositionProvider = positionProvider,
                onDismissRequest = state::dismiss,
                properties = PopupProperties(focusable = false),
            ) {
                UserHoverCard(
                    user = user,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    onOpenProfile = {
                        state.dismiss()
                        nav.nav(routeFor(user))
                    },
                    onMoreOptions = state::openMenu,
                    modifier = Modifier.cardHover(state),
                )
            }
        }

        // Drawn by the anchor, not the card: the card closes when the mouse moves to the menu,
        // and the menu's dialogs (nickname, reports) must outlive it.
        if (state.wasMenuOpened) {
            UserProfileDropDownMenu(user, state.isMenuOpen, { state.isMenuOpen = false }, accountViewModel)
        }
    }
}

private fun Modifier.anchorHover(state: UserHoverCardState) =
    pointerInput(state) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                // A finger has no hover; only a mouse opens the card.
                if (event.changes.firstOrNull()?.type != PointerType.Mouse) continue
                when (event.type) {
                    PointerEventType.Enter -> state.onAnchorEnter()
                    PointerEventType.Exit -> state.onPointerExit()
                    // Clicking the face opens the profile; the card would only be in the way.
                    PointerEventType.Press -> state.dismiss()
                }
            }
        }
    }

private fun Modifier.cardHover(state: UserHoverCardState) =
    pointerInput(state) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                when (event.type) {
                    PointerEventType.Enter -> state.onCardEnter()
                    PointerEventType.Exit -> state.onPointerExit()
                }
            }
        }
    }

/** Under the anchor, start-aligned; above it when the window has no room below. */
private class BelowOrAboveAnchor(
    private val gap: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val start =
            if (layoutDirection == LayoutDirection.Ltr) {
                anchorBounds.left
            } else {
                anchorBounds.right - popupContentSize.width
            }
        val x = start.coerceIn(0, max(0, windowSize.width - popupContentSize.width))

        val below = anchorBounds.bottom + gap
        val y =
            if (below + popupContentSize.height <= windowSize.height) {
                below
            } else {
                max(0, anchorBounds.top - gap - popupContentSize.height)
            }

        return IntOffset(x, y)
    }
}
