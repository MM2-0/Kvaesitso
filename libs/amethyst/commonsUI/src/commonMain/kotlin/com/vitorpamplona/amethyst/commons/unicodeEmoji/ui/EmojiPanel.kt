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
package com.vitorpamplona.amethyst.commons.unicodeEmoji.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState.EmojiMedia
import com.vitorpamplona.amethyst.commons.ui.components.PlatformBackHandler
import com.vitorpamplona.amethyst.commons.ui.insets.KeyboardState
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.insets.keyboardAsState

/** Which composer the docked panel is writing into, how, and which [EmojiPanelHost] shows it. */
@Stable
internal class EmojiPanelRequest(
    val owner: Any,
    val region: Any,
    val emojiPacks: EmojiPackState?,
    val onPickUnicode: (String) -> Unit,
    val onPickCustom: (EmojiMedia) -> Unit,
)

/** The docked emoji panel of a window: at most one composer, in one [EmojiPanelHost], has it open. */
@Stable
class EmojiPanelState {
    internal var request by mutableStateOf<EmojiPanelRequest?>(null)

    /** Focus is inside the panel: its search field, which brings up the keyboard of its own. */
    internal var focused by mutableStateOf(false)

    fun close() {
        request = null
        focused = false
    }
}

internal val LocalEmojiPanel = staticCompositionLocalOf<EmojiPanelState?> { null }

/** The nearest [EmojiPanelHost]: where a composer's panel docks. */
internal val LocalEmojiPanelRegion = staticCompositionLocalOf<Any?> { null }

/**
 * The window's emoji panel state, for the [EmojiPanelHost]s inside [content].
 *
 * The panel is the fallback for when there is no on-screen keyboard to pick emoji from: Desktop,
 * or a phone with a hardware keyboard or with the keyboard put away. So it closes as soon as the
 * keyboard comes up for anything but the panel's own search (a tap in the text), and the buttons
 * hide while the keyboard is up. Unlike a menu it stays open between picks, so several emoji go in
 * one after another.
 */
@Composable
fun EmojiPanelScope(content: @Composable () -> Unit) {
    val state = remember { EmojiPanelState() }
    val keyboard = keyboardAsState()

    // The search field takes focus on the tap, before the keyboard's inset starts to grow, so a
    // keyboard brought up by the search never reads as one brought up by the text. Focus moving
    // from the search back to the text, with the keyboard still up, closes the panel too.
    LaunchedEffect(state, keyboard) {
        snapshotFlow { keyboard.value == KeyboardState.Opened && !state.focused }
            .collect { if (it) state.close() }
    }
    CompositionLocalProvider(LocalEmojiPanel provides state, content = content)
}

/**
 * Docks the emoji panel at the bottom of [content], full width, when a composer inside it opens
 * one: the view that owns the composer gives up the space, not the whole window. Wrap the
 * window's main content, each deck column, and the conversation pane of a two-pane screen; a
 * composer docks in the nearest one. Back closes the panel. Outside an [EmojiPanelScope] it only
 * shows [content].
 *
 * A host that stops being [active] closes its panel: a deck column hands Back to the main screen
 * when it loses focus, so its panel would otherwise stay open with nothing left to close it.
 */
@Composable
fun EmojiPanelHost(
    active: Boolean = true,
    content: @Composable () -> Unit,
) {
    val state = LocalEmojiPanel.current
    if (state == null) {
        content()
        return
    }
    val region = remember { Any() }
    // Read here, not in this scope's body, so opening or closing a panel recomposes only the host
    // that shows it, not every host in the window and the screens they hold.
    val open by remember(state, region) { derivedStateOf { state.request?.region === region } }

    LaunchedEffect(active) {
        if (!active && state.request?.region === region) state.close()
    }

    CompositionLocalProvider(LocalEmojiPanelRegion provides region) {
        // While the panel's search has the keyboard up, the panel and the screen above it sit on
        // top of the keyboard. The padding consumes the inset, so the screen does not add its own.
        Column(Modifier.fillMaxSize().then(if (open) Modifier.imePaddingSafe() else Modifier)) {
            // The panel covers the navigation bar, so the screen above it leaves no room for one.
            Box(Modifier.weight(1f).then(if (open) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier)) {
                content()
            }

            if (open) DockedPanel(state, region)
        }
    }
}

@Composable
private fun DockedPanel(
    state: EmojiPanelState,
    region: Any,
) {
    val request = state.request?.takeIf { it.region === region } ?: return

    // Registered only while open, and after the screens, so it is the newest handler and Back
    // closes the panel before it leaves the screen.
    PlatformBackHandler { state.close() }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().onFocusChanged { state.focused = it.hasFocus },
    ) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider()
            EmojiPicker(
                emojiPacks = request.emojiPacks,
                onPickUnicode = request.onPickUnicode,
                onPickCustom = request.onPickCustom,
                modifier = Modifier.fillMaxWidth(),
                onClose = state::close,
            )
        }
    }
}

/**
 * The composer's button for the docked panel: toggles it, and hides while the keyboard is up unless
 * its panel is open (the keyboard is then the panel's search's).
 */
@Composable
internal fun EmojiPanelButton(
    host: EmojiPanelState,
    region: Any,
    emojiPacks: EmojiPackState?,
    onPickUnicode: (String) -> Unit,
    onPickCustom: (EmojiMedia) -> Unit,
    tint: Color,
    modifier: Modifier,
) {
    val owner = remember { Any() }
    val pickUnicode by rememberUpdatedState(onPickUnicode)
    val pickCustom by rememberUpdatedState(onPickCustom)
    val keyboard by keyboardAsState()

    // A composer that goes away takes its panel with it: as soon as its screen stops being the
    // one in front, since a screen being left stays composed while it animates out.
    DisposableEffect(host) {
        onDispose { if (host.request?.owner === owner) host.close() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        if (host.request?.owner === owner) host.close()
    }

    val open = host.request?.owner === owner

    // The on-screen keyboard has an emoji key of its own.
    if (keyboard == KeyboardState.Opened && !open) return

    Box(modifier) {
        EmojiPickerIcon(if (open) MaterialTheme.colorScheme.primary else tint) {
            if (open) {
                host.close()
            } else {
                host.request = EmojiPanelRequest(owner, region, emojiPacks, { pickUnicode(it) }, { pickCustom(it) })
            }
        }
    }
}
