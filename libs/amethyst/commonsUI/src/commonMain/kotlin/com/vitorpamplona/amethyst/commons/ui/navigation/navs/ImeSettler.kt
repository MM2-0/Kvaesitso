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
package com.vitorpamplona.amethyst.commons.ui.navigation.navs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.vitorpamplona.amethyst.commons.ui.insets.rememberSafeImeInsets
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** How long to wait for the IME inset to reach zero before navigating anyway. */
const val IME_SETTLE_TIMEOUT_MS = 700L

/**
 * Waits for the soft keyboard to be fully off screen. Installed on [Nav] so that every navigation
 * in the app serializes the IME and window animations instead of overlapping them.
 *
 * Navigating while the keyboard is up races the window animation against the IME's close animation.
 * On release builds — fast enough that the window animation wins — the IME
 * `WindowInsetsAnimationCompat` is cancelled before
 * its terminal (zero) frame reaches Compose. `WindowInsets.ime` is a single app-wide holder, so it
 * stays "animating" and every IME padding in the app — not just the screen being left — freezes at
 * the keyboard height. [SafeImeInsets][com.vitorpamplona.amethyst.commons.ui.insets.SafeImeInsets] is the backstop for when that freeze turns out to be
 * permanent; this is the prevention, and it is the cheaper of the two because it keeps the padding
 * from ever being wrong.
 *
 * This is not a composer-screen problem, which is why it lives here rather than in the screens.
 * Any destination that can hold focus in a text field can strand the padding on the way out, by any
 * exit: a back gesture, a top-bar button, a bottom-nav tab, or tapping a result. Search is the
 * clearest case — it focuses its field on arrival, so the keyboard is already up before the user
 * has done anything, and every way out of it is a navigation.
 */
fun interface ImeSettler {
    suspend fun settle()

    companion object {
        /** For [EmptyNav] and previews, where there is no window to read insets from. */
        val None = ImeSettler { }
    }
}

/**
 * Reads the same [SafeImeInsets][com.vitorpamplona.amethyst.commons.ui.insets.SafeImeInsets] that drives `Modifier.imePaddingSafe()`, so the settler and the
 * padding can never disagree about whether the keyboard is gone. Reading the raw `WindowInsets.ime`
 * here would make every navigation burn the full [IME_SETTLE_TIMEOUT_MS] once the inset is stranded,
 * waiting on a value that is never going to move again.
 *
 * Focus is cleared before hiding so nothing re-requests the IME as it retracts. The wait is bounded
 * by [IME_SETTLE_TIMEOUT_MS] — if the inset never reports zero, which is precisely the failure this
 * guards against, navigation still proceeds rather than stranding the user on the screen.
 */
@Composable
fun rememberImeSettler(): ImeSettler {
    val density = LocalDensity.current
    val imeInsets = rememberSafeImeInsets()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    return remember(density, imeInsets, keyboard, focusManager) {
        ImeSettler {
            if (imeInsets.getBottom(density) > 0) {
                focusManager.clearFocus(true)
                keyboard?.hide()
                withTimeoutOrNull(IME_SETTLE_TIMEOUT_MS) {
                    snapshotFlow { imeInsets.getBottom(density) }.first { it <= 0 }
                }
            }
        }
    }
}
