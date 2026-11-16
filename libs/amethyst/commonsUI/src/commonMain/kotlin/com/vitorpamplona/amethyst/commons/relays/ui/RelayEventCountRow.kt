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
package com.vitorpamplona.amethyst.commons.relays.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.relay_event_count
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Font10SP
import com.vitorpamplona.amethyst.commons.ui.theme.Size10Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.commons.util.countToHumanReadable

private val PillShape = RoundedCornerShape(12.dp)

@Composable
fun RelayEventCountRow(
    countResult: RelayCountResult?,
    modifier: Modifier,
) {
    if (countResult == null || countResult.counts.isEmpty()) return

    val pillColor = MaterialTheme.colorScheme.allGoodColor

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = modifier,
    ) {
        countResult.counts.forEachIndexed { index, entry ->
            if (index > 0) {
                Spacer(modifier = Modifier.width(6.dp))
            }

            val countText =
                if (entry.approximate) {
                    "~${countToHumanReadable(entry.count, stringRes(entry.label))}"
                } else {
                    countToHumanReadable(entry.count, stringRes(entry.label))
                }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .clip(PillShape)
                        .border(width = 1.dp, color = pillColor.copy(alpha = 0.4f), shape = PillShape)
                        .background(pillColor.copy(alpha = 0.1f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Icon(
                    symbol = MaterialSymbols.Storage,
                    contentDescription = stringRes(Res.string.relay_event_count),
                    modifier = Size10Modifier,
                    tint = pillColor,
                )

                Spacer(modifier = Modifier.width(3.dp))

                Text(
                    text = countText,
                    maxLines = 1,
                    fontSize = Font10SP,
                    fontWeight = FontWeight.Medium,
                    color = pillColor,
                )
            }
        }
    }
}
