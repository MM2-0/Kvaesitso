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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.Font12SP
import com.vitorpamplona.amethyst.commons.ui.theme.HalfPadding
import com.vitorpamplona.amethyst.commons.ui.theme.StdPadding
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText

/**
 * A day (or status) divider between chat messages: hairlines either side of a caption-sized
 * label. Caption type, not bold: it marks where the conversation crossed a day, it is not a
 * heading the eye should land on before the messages.
 */
@Composable
fun ChatDivisor(
    info: String,
    /**
     * Tints both rules and the label. Unspecified keeps the default, which is every
     * date divisor; an unread marker passes the accent so the line it draws reads as a
     * status rather than another date.
     */
    color: Color = Color.Unspecified,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = StdPadding) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = DividerThickness,
            color = if (color.isSpecified) color else DividerDefaults.color,
        )
        Text(
            text = info,
            fontWeight = FontWeight.Medium,
            fontSize = Font12SP,
            color = if (color.isSpecified) color else MaterialTheme.colorScheme.placeholderText,
            modifier = HalfPadding,
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = DividerThickness,
            color = if (color.isSpecified) color else DividerDefaults.color,
        )
    }
}

/**
 * Something that happened to the conversation rather than a message in it (a rename): one
 * centered line in caption type, no bubble and no rules, so it never competes with what
 * people said.
 */
@Composable
fun ChatSystemCaption(text: String) {
    Text(
        text = text,
        fontSize = Font12SP,
        color = MaterialTheme.colorScheme.placeholderText,
        textAlign = TextAlign.Center,
        modifier = ChatSystemCaptionModifier,
    )
}

private val ChatSystemCaptionModifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp)
