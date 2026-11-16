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
package com.vitorpamplona.amethyst.commons.ui.layouts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.ui.components.NewItemsBubble
import com.vitorpamplona.amethyst.commons.ui.theme.ChatRowAvatarModifier
import com.vitorpamplona.amethyst.commons.ui.theme.grayText

private val ChatRowPadding = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
private val ChatRowAvatarGap = Modifier.width(12.dp)
private val ChatRowLineGap = Modifier.padding(top = 3.dp)
private val ChatRowDotGap = Modifier.width(8.dp)

/**
 * Title weight of a Messages-list row. Read rows sit at Medium so the list is calm; only a row with
 * something new goes Bold, which makes bold *mean* unread instead of being on every line.
 */
fun chatRowTitleWeight(hasNewMessages: Boolean): FontWeight = if (hasNewMessages) FontWeight.Bold else FontWeight.Medium

/**
 * One Messages-list row: picture, then a title line over a preview line.
 *
 * The row owns its unread emphasis so every room kind agrees on it: [hasNewMessages] turns the title
 * Bold (titles inherit it from [LocalTextStyle] — don't hardcode a weight in [firstRow]), lifts the
 * preview from gray to full-contrast (preview text inherits [LocalContentColor] — don't hardcode a
 * color in [secondRow]), and draws the unread dot at the end of the preview line.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatHeaderLayout(
    channelPicture: @Composable () -> Unit,
    firstRow: @Composable RowScope.() -> Unit,
    secondRow: @Composable RowScope.() -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    hasNewMessages: Boolean = false,
) {
    Row(
        modifier =
            Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .then(ChatRowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(ChatRowAvatarModifier) { channelPicture() }

        Spacer(modifier = ChatRowAvatarGap)

        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            CompositionLocalProvider(
                LocalTextStyle provides LocalTextStyle.current.copy(fontWeight = chatRowTitleWeight(hasNewMessages)),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    firstRow()
                }
            }

            CompositionLocalProvider(
                LocalContentColor provides if (hasNewMessages) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.grayText,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = ChatRowLineGap,
                ) {
                    secondRow()

                    if (hasNewMessages) {
                        Spacer(modifier = ChatRowDotGap)
                        NewItemsBubble()
                    }
                }
            }
        }
    }
}
