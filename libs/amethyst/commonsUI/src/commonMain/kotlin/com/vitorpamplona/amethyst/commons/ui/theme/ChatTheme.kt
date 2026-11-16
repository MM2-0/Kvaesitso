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
package com.vitorpamplona.amethyst.commons.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Chat bubble corners — the single source of truth for every front end. The
// small 4dp corner is the "tail" pointing at the author's side. A grouped run
// reads as one continuous unit: full rounding only at the very top and bottom
// of the group, edges that touch a neighboring message of the same run stay
// sharp (Top = visually first / oldest, Bottom = visually last).
val ChatBubbleShapeMe = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
val ChatBubbleShapeMeTop = RoundedCornerShape(18.dp, 18.dp, 6.dp, 6.dp)
val ChatBubbleShapeMeMiddle = RoundedCornerShape(6.dp, 6.dp, 6.dp, 6.dp)
val ChatBubbleShapeMeBottom = RoundedCornerShape(6.dp, 6.dp, 4.dp, 18.dp)
val ChatBubbleShapeThem = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp)
val ChatBubbleShapeThemTop = RoundedCornerShape(4.dp, 18.dp, 6.dp, 6.dp)
val ChatBubbleShapeThemMiddle = RoundedCornerShape(6.dp, 6.dp, 6.dp, 6.dp)
val ChatBubbleShapeThemBottom = RoundedCornerShape(6.dp, 6.dp, 18.dp, 18.dp)

// Chat bubble modifiers

// Message bubble internal padding
val MessageBubbleLimits = Modifier.padding(start = 10.dp, end = 10.dp, top = 7.dp, bottom = 6.dp)

// Chat author area
val ChatAuthorBox = Modifier.size(20.dp)
val ChatAuthorImage = Modifier.size(20.dp).clip(shape = CircleShape)

// Spacing values reused across chat composables
val ChatRowColSpacing5dp = Arrangement.spacedBy(5.dp)
val ChatHalfHalfVertPadding = Modifier.padding(vertical = 3.dp)
val ChatStdHorzSpacer = Modifier.width(5.dp)
val ChatStdPadding = Modifier.padding(10.dp)
val ChatReactionRowHeight = Modifier.height(20.dp)

// Common size values
val ChatSize20dp = 20.dp
val ChatSize34dp = 34.dp

// Chat color extensions for ColorScheme
val ColorScheme.chatBubbleBackground: Color
    get() = if (isLight) onSurface.copy(alpha = 0.08f) else onSurface.copy(alpha = 0.12f)

val ColorScheme.chatBubbleDraftBackground: Color
    get() = onSurface.copy(alpha = 0.15f)

val ColorScheme.chatBubbleMeBackground: Color
    get() = primary.copy(alpha = 0.32f)

val ColorScheme.chatPlaceholderText: Color
    get() = onSurface.copy(alpha = 0.42f)
