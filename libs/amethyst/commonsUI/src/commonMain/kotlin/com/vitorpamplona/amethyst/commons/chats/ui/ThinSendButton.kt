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
package com.vitorpamplona.amethyst.commons.chats.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.accessibility_send
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size25Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText

/**
 * The composer's send button. Idle it is a quiet glyph; once there is something to send it
 * becomes a filled accent disc, so the one action the screen is waiting for is the thing
 * that stands out, and an outline-gray arrow no longer looks disabled when it is not.
 */
@Composable
fun ThinSendButton(
    isActive: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    IconButton(
        enabled = isActive,
        // modifier = modifier,
        onClick = onClick,
    ) {
        if (isActive) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = SendDiscModifier.background(MaterialTheme.colorScheme.primary, CircleShape),
            ) {
                Icon(
                    symbol = MaterialSymbols.AutoMirrored.Send,
                    contentDescription = stringRes(id = Res.string.accessibility_send),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = SendGlyphModifier,
                )
            }
        } else {
            Icon(
                symbol = MaterialSymbols.AutoMirrored.Send,
                contentDescription = stringRes(id = Res.string.accessibility_send),
                tint = MaterialTheme.colorScheme.placeholderText,
                modifier = Size25Modifier,
            )
        }
    }
}

private val SendDiscModifier = Modifier.size(36.dp)
private val SendGlyphModifier = Modifier.size(19.dp)
