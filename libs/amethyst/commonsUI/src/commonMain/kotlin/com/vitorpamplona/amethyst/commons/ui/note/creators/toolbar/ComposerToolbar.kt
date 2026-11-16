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
package com.vitorpamplona.amethyst.commons.ui.note.creators.toolbar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalScreenLayout
import com.vitorpamplona.amethyst.commons.ui.layouts.NavigationStyle
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppPlatform
import com.vitorpamplona.amethyst.commons.ui.theme.Size10dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size35dp

/**
 * Whether a composer's tools wrap under the text rather than scroll in a strip at the bottom:
 * on the docked tier, a desktop-sized window. Not on the rail tier, which landscape phones reach,
 * where the keyboard leaves no height for a second row of tools.
 */
@Composable
fun composerToolsWrap(): Boolean = LocalScreenLayout.current.navigationStyle == NavigationStyle.PERMANENT_DRAWER

/** Lines the wrapped tools up with a composer's text: past the side padding and the avatar beside it. */
val ComposerToolsStartPadding: Dp = Size10dp + Size35dp

/** A composer field's start without an avatar beside it: just the side padding. */
val ComposerToolsFormStartPadding: Dp = Size10dp

/**
 * The modifier for a composer's content above its [ComposerToolbar]: it fills the height left over
 * while the tools sit in the bottom strip, and takes only the height it needs where they wrap, so
 * the tools sit under the text instead of at the bottom of a tall window. Taller content scrolls
 * inside it and the tools stay in view. Both read [composerToolsWrap], so they always agree.
 */
@Composable
fun ColumnScope.composerBody(): Modifier = Modifier.weight(1f, fill = !composerToolsWrap())

/**
 * A composer's tool buttons (gallery, files, drawing, emoji, zap split, content warning, …).
 *
 * On a phone they scroll sideways in a 50dp strip at the bottom, above the keyboard. On the docked
 * tier they wrap under the text instead, lined up with it from [startPadding]: a mouse cannot drag
 * that strip, so its last tools were out of reach, and on a tall window the strip sat far below a
 * short text. Give the content above [composerBody] so it makes room for the tools the same way.
 *
 * With a mouse ([com.vitorpamplona.amethyst.commons.ui.platform.AppPlatform.hasFinePointer]) the
 * wrapped tools drop Material's 48dp touch target and sit at their 40dp size; a finger keeps it,
 * on a tablet's wide layout as on a phone.
 *
 * [tools] is told whether it is wrapped, for a tool that needs its own bounds there (the voice
 * recorder fills its parent). It is movable, so a window crossing tiers moves the tools' state (a
 * recording, an open picker) instead of disposing it.
 */
@Composable
fun ComposerToolbar(
    modifier: Modifier = Modifier,
    startPadding: Dp = ComposerToolsStartPadding,
    tools: @Composable (wrapped: Boolean) -> Unit,
) {
    val wrapped = composerToolsWrap()
    // Read here, where it does not move, and handed to the tools as a parameter: content moved by
    // movableContentOf keeps what it composed, so a local provided around only one of the two
    // places it can land would not reliably reach buttons that already read the other value.
    val touchTarget = LocalMinimumInteractiveComponentSize.current
    val toolTarget = if (wrapped && LocalAppPlatform.current.hasFinePointer) Dp.Unspecified else touchTarget

    val movableTools =
        remember {
            movableContentOf { content: @Composable (Boolean) -> Unit, isWrapped: Boolean, target: Dp ->
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides target) {
                    content(isWrapped)
                }
            }
        }

    if (wrapped) {
        FlowRow(
            modifier = modifier.fillMaxWidth().padding(start = startPadding, end = Size10dp),
            itemVerticalAlignment = CenterVertically,
        ) {
            movableTools(tools, true, toolTarget)
        }
    } else {
        val scrollState = rememberScrollState()
        Row(
            modifier =
                modifier
                    .horizontalScroll(scrollState)
                    .fillMaxWidth()
                    .height(50.dp),
            verticalAlignment = CenterVertically,
        ) {
            movableTools(tools, false, toolTarget)
        }
    }
}
