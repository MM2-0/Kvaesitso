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
package com.vitorpamplona.amethyst.commons.ui.layouts.listItem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.github
import com.vitorpamplona.amethyst.commons.resources.profile_banner
import com.vitorpamplona.amethyst.commons.ui.components.NewItemsBubble
import com.vitorpamplona.amethyst.commons.ui.layouts.ChatHeaderLayout
import com.vitorpamplona.amethyst.commons.ui.layouts.listItem.SlimListItem
import com.vitorpamplona.amethyst.commons.ui.note.elements.TimeAgo
import com.vitorpamplona.amethyst.commons.ui.painterRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.Height4dpModifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size55Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.quartz.utils.TimeUtils

private const val PREVIEW_AUTHOR = "This is my author"
private const val PREVIEW_MESSAGE = "This is a message from this person"

@Composable
@Preview
fun ChannelNamePreview() {
    ThemeComparisonColumn {
        Column {
            ChatHeaderLayout(
                channelPicture = {
                    Image(
                        painter = painterRes(Res.drawable.github, 1),
                        contentDescription = stringRes(id = Res.string.profile_banner),
                        contentScale = ContentScale.FillWidth,
                    )
                },
                firstRow = {
                    Text(PREVIEW_AUTHOR, Modifier.weight(1f))
                    TimeAgo(TimeUtils.now())
                },
                secondRow = {
                    Text(PREVIEW_MESSAGE, Modifier.weight(1f))
                    NewItemsBubble()
                },
                onClick = {},
            )

            HorizontalDivider(thickness = DividerThickness)

            SlimListItem(
                headlineContent = {
                    Row(verticalAlignment = CenterVertically) {
                        Text(PREVIEW_AUTHOR, Modifier.weight(1f))
                        TimeAgo(TimeUtils.now())
                    }
                },
                supportingContent = {
                    Row(verticalAlignment = CenterVertically) {
                        Text(PREVIEW_MESSAGE, Modifier.weight(1f))
                        Spacer(modifier = Height4dpModifier)
                        NewItemsBubble()
                    }
                },
                leadingContent = {
                    Image(
                        painter = painterRes(Res.drawable.github, 2),
                        contentDescription = stringRes(id = Res.string.profile_banner),
                        contentScale = ContentScale.FillWidth,
                        modifier = Size55Modifier,
                    )
                },
            )

            HorizontalDivider(thickness = DividerThickness)

            ListItem(
                headlineContent = {
                    Row(verticalAlignment = CenterVertically) {
                        Text(PREVIEW_AUTHOR, Modifier.weight(1f))
                        TimeAgo(TimeUtils.now())
                    }
                },
                supportingContent = {
                    Row(verticalAlignment = CenterVertically) {
                        Text(PREVIEW_MESSAGE, Modifier.weight(1f))
                        Spacer(modifier = Height4dpModifier)
                        NewItemsBubble()
                    }
                },
                leadingContent = {
                    Image(
                        painter = painterRes(Res.drawable.github, 2),
                        contentDescription = stringRes(id = Res.string.profile_banner),
                        contentScale = ContentScale.FillWidth,
                        modifier = Size55Modifier,
                    )
                },
            )

            HorizontalDivider(thickness = DividerThickness)
        }
    }
}
