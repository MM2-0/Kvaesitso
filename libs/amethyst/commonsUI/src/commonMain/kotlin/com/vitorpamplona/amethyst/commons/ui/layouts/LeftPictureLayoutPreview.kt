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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.github
import com.vitorpamplona.amethyst.commons.resources.profile_banner
import com.vitorpamplona.amethyst.commons.ui.layouts.LeftPictureLayout
import com.vitorpamplona.amethyst.commons.ui.note.LikeIcon
import com.vitorpamplona.amethyst.commons.ui.note.TextCount
import com.vitorpamplona.amethyst.commons.ui.note.ZappedIcon
import com.vitorpamplona.amethyst.commons.ui.painterRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.commons.ui.theme.Size16Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText

@Composable
@Preview
fun LeftPictureLayoutPreview() {
    ThemeComparisonColumn { LeftPictureLayoutPreviewCard() }
}

@Composable
fun LeftPictureLayoutPreviewCard() {
    LeftPictureLayout(
        onImage = {
            Image(
                painter = painterRes(Res.drawable.github, 3),
                contentDescription = stringRes(id = Res.string.profile_banner),
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxSize().clip(QuoteBorder),
            )
        },
        onTitleRow = {
            Text(
                text = "This is my title",
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            Spacer(modifier = StdHorzSpacer)
            LikeIcon(
                iconSizeModifier = Size16Modifier,
                grayTint = MaterialTheme.colorScheme.onSurface,
            )
            TextCount(12, MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = StdHorzSpacer)
            ZappedIcon(Size20Modifier)
            TextCount(120, MaterialTheme.colorScheme.onSurface)
        },
        onDescription = {
            Text(
                "This is 3-line description, This is 3-line description, This is 3-line description, This is 3-line description",
                color = MaterialTheme.colorScheme.placeholderText,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp,
            )
        },
        onBottomRow = { Text("This is my Moderator List") },
    )
}
