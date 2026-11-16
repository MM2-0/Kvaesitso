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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.wallet_sats
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size16Modifier

/**
 * Heart color of the Liked icon vector; tints reaction activity cards the way
 * bitcoinColor tints the zap ones.
 */
val LikeTint = Color(0xFFCA395F)

/**
 * Gradient card frame shared by the reaction / zap / nutzap / onchain-zap
 * renderings, mirroring the onchain card's look: rounded corners and a soft
 * top-to-bottom wash of the kind's tint.
 *
 * The content lambda receives a stable [transparentCardBackground] state to hand
 * down to the embedded note and comment: everything inside the card sits on the
 * orange (or like-tinted) wash, so inner content must draw transparent and let
 * that wash show through. Passing the parent feed / MultiSetCard background here
 * instead would paint the inner note black (the app background) or flash it with
 * the new-note highlight, breaking the card's solid tint.
 */
@Composable
fun ActivityCardFrame(
    tint: Color,
    content: @Composable ColumnScope.(transparentCardBackground: MutableState<Color>) -> Unit,
) {
    val transparentCardBackground = remember { mutableStateOf<Color>(Color.Transparent) }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        colors =
                            listOf(
                                tint.copy(alpha = 0.16f),
                                tint.copy(alpha = 0.04f),
                            ),
                    ),
                ).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content(transparentCardBackground)
        }
    }
}

/** Circular kind badge: a small filled circle of the tint with a white glyph. */
@Composable
fun ActivityBadge(
    tint: Color,
    icon: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(tint),
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

/** Right-aligned kind label, the static cousin of the onchain card's dashed pill. */
@Composable
fun ActivityPill(
    label: String,
    tint: Color,
) {
    Box(
        modifier =
            Modifier
                .border(1.4.dp, tint.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** First line of the card: badge, sender → recipient avatars, and the kind pill. */
@Composable
fun ActivityHeaderRow(
    tint: Color,
    pillLabel: String?,
    badge: @Composable () -> Unit,
    senderAvatar: @Composable () -> Unit,
    recipientAvatar: (@Composable () -> Unit)?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        badge()
        senderAvatar()
        recipientAvatar?.let {
            Icon(
                symbol = MaterialSymbols.AutoMirrored.ArrowForwardIos,
                contentDescription = null,
                tint = tint,
                modifier = Size16Modifier,
            )
            it()
        }

        Spacer(Modifier.weight(1f))

        pillLabel?.let { ActivityPill(it, tint) }
    }
}

/** Big amount line, matching the onchain card's typography. */
@Composable
fun ActivityAmountRow(
    amount: String,
    tint: Color,
) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = amount,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.ExtraBold,
            color = tint,
        )
        Text(
            text = stringRes(Res.string.wallet_sats),
            style = MaterialTheme.typography.titleSmall,
            color = tint,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
}
