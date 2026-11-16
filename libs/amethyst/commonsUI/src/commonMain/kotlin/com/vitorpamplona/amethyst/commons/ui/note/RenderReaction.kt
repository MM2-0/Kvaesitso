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

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.emojicoder.EmojiCoder
import com.vitorpamplona.amethyst.commons.ui.components.AnimatedBorderTextCornerRadius
import com.vitorpamplona.amethyst.commons.ui.note.LikedIcon
import com.vitorpamplona.amethyst.commons.ui.richtext.InLineIconRenderer
import com.vitorpamplona.amethyst.commons.ui.theme.Size28Modifier
import com.vitorpamplona.amethyst.commons.util.customEmojiUrlOrNull
import com.vitorpamplona.quartz.nip30CustomEmoji.CustomEmoji
import kotlinx.collections.immutable.persistentListOf

@Composable
fun RenderReaction(reactionType: String) {
    // A custom emoji arrives here normalized to `:shortcode:url` (see firstFullCharOrEmoji). A
    // bare `:shortcode:` whose event carries no matching emoji tag has no image to draw: show the
    // shortcode as text instead of an empty image.
    val customEmojiUrl = remember(reactionType) { reactionType.customEmojiUrlOrNull() }

    if (customEmojiUrl != null) {
        val url = customEmojiUrl

        InLineIconRenderer(
            persistentListOf(
                CustomEmoji.ImageUrlType(url),
            ),
            style = SpanStyle(color = MaterialTheme.colorScheme.onBackground),
            maxLines = 1,
            fontSize = 22.sp,
        )
    } else {
        when (reactionType) {
            "+" -> {
                LikedIcon(modifier = Size28Modifier)
            }

            "-" -> {
                Text(
                    text = "\uD83D\uDC4E",
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    fontSize = 22.sp,
                )
            }

            else -> {
                if (EmojiCoder.isCoded(reactionType)) {
                    AnimatedBorderTextCornerRadius(
                        reactionType,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 20.sp,
                    )
                } else {
                    Text(
                        reactionType,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        fontSize = 22.sp,
                    )
                }
            }
        }
    }
}
