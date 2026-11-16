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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.ui.theme.Font10SP
import com.vitorpamplona.amethyst.commons.ui.theme.Font6SP
import com.vitorpamplona.amethyst.commons.ui.theme.Font8SP
import com.vitorpamplona.amethyst.commons.ui.theme.SmallBorder

/**
 * Trust-score chip drawn over the bottom of an avatar, sized to the avatar: regular above 34dp,
 * small above 23dp, smallest below.
 */
@Composable
fun ScoreTag(
    score: Int,
    size: Dp,
    modifier: Modifier,
) {
    if (size > 34.dp) {
        ScoreTagRegular(score, modifier)
    } else if (size > 23.dp) {
        ScoreTagSmall(score, modifier)
    } else {
        ScoreTagSmallest(score, modifier)
    }
}

@Composable
fun ScoreTagRegular(
    score: Int,
    modifier: Modifier,
) {
    Text(
        text = score.toString(),
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = Font10SP,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        lineHeight = Font10SP,
        modifier =
            modifier
                .clip(SmallBorder)
                .background(Color.Black)
                .padding(horizontal = 4.dp, vertical = 0.dp),
    )
}

@Composable
fun ScoreTagSmall(
    score: Int,
    modifier: Modifier,
) {
    Text(
        text = score.toString(),
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = Font8SP,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        lineHeight = Font8SP,
        modifier =
            modifier
                .clip(SmallBorder)
                .background(Color.Black)
                .padding(horizontal = 3.dp, vertical = 0.dp),
    )
}

@Composable
fun ScoreTagSmallest(
    score: Int,
    modifier: Modifier,
) {
    Text(
        text = score.toString(),
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = Font6SP,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        lineHeight = Font6SP,
        modifier =
            modifier
                .clip(SmallBorder)
                .background(Color.Black)
                .padding(horizontal = 2.dp, vertical = 0.dp),
    )
}
