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
package com.vitorpamplona.amethyst.commons.ui.note.creators.zapsplits

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.ZapSplit
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.zap_split_title
import com.vitorpamplona.amethyst.commons.resources.zaps
import com.vitorpamplona.amethyst.commons.ui.components.HoverTooltip
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier

@Composable
fun ForwardZapToButton(
    isActive: Boolean,
    onClick: () -> Unit,
) {
    HoverTooltip(stringRes(Res.string.zap_split_title)) {
        IconButton(
            onClick = { onClick() },
        ) {
            if (!isActive) {
                ZapSplitIcon(tint = MaterialTheme.colorScheme.onBackground)
            } else {
                ZapSplitIcon(tint = BitcoinOrange)
            }
        }
    }
}

@Composable
fun ZapSplitIcon(
    modifier: Modifier = Size20Modifier,
    tint: Color = BitcoinOrange,
) {
    Icon(
        imageVector = ZapSplit,
        contentDescription = stringRes(id = Res.string.zap_split_title),
        modifier = modifier,
        tint = tint,
    )
}

@Preview
@Composable
fun ZapSplitPreview() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .height(20.dp)
                .width(25.dp),
        ) {
            Icon(
                symbol = MaterialSymbols.Bolt,
                contentDescription = stringRes(id = Res.string.zaps),
                modifier =
                    Modifier
                        .size(20.dp)
                        .align(Alignment.CenterStart),
                tint = BitcoinOrange,
            )
            Icon(
                symbol = MaterialSymbols.AutoMirrored.ArrowForwardIos,
                contentDescription = stringRes(id = Res.string.zaps),
                modifier =
                    Modifier
                        .size(13.dp)
                        .align(Alignment.CenterEnd),
                tint = BitcoinOrange,
            )
        }
        ZapSplitIcon(tint = BitcoinOrange)
    }
}
