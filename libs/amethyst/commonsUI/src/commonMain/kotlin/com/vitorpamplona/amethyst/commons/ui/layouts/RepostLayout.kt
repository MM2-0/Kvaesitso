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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.ui.note.RepostIcon
import com.vitorpamplona.amethyst.commons.ui.theme.Size55dp
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText

@Composable
@Preview
private fun GenericRepostSectionPreview() {
    GenericRepostLayout(
        baseAuthorPicture = { Text("ab") },
        repostAuthorPicture = { Text("cd") },
    )
}

// The proportions of the 55dp layout: two 35dp pictures in opposite corners and an 18dp icon. As
// fractions of the box, they follow whatever size the box is given, by [size] or by its parent.
private const val PICTURE_FRACTION = 35f / 55f
private const val ICON_FRACTION = 18f / 55f

/** The pictures of a repost's author and of the reposted note's author, [size] wide, or less if the parent says so. */
@Composable
fun GenericRepostLayout(
    baseAuthorPicture: @Composable () -> Unit,
    repostAuthorPicture: @Composable () -> Unit,
    size: Dp = Size55dp,
) {
    Box(modifier = remember(size) { Modifier.size(size) }) {
        Box(remember { PictureModifier.align(Alignment.TopStart) }) { baseAuthorPicture() }

        Box(remember { IconModifier.align(Alignment.BottomStart).padding(1.dp) }) {
            RepostIcon(modifier = Modifier.fillMaxSize(), MaterialTheme.colorScheme.placeholderText)
        }

        Box(remember { PictureModifier.align(Alignment.BottomEnd) }, contentAlignment = Alignment.BottomEnd) {
            repostAuthorPicture()
        }
    }
}

private val PictureModifier = Modifier.fillMaxSize(PICTURE_FRACTION)
private val IconModifier = Modifier.fillMaxSize(ICON_FRACTION)
