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

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.ui.richtext.CreateTextWithEmoji

/**
 * A user's name on one line: [displayName] with its custom emojis resolved against [tags], or the
 * shortened npub of [user] while no name is known.
 *
 * Callers pick the name (a petname, the profile's best name, ...) and the tags its emojis come from.
 */
@Composable
fun UserNameText(
    user: User,
    displayName: String?,
    tags: ImmutableListOfLists<String>?,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Bold,
    textColor: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
) {
    if (displayName != null) {
        CreateTextWithEmoji(
            text = displayName,
            tags = tags,
            fontWeight = fontWeight,
            textAlign = textAlign,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
            color = textColor,
        )
    } else {
        Text(
            text = remember(user) { user.pubkeyDisplayHex() },
            fontWeight = fontWeight,
            modifier = modifier,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = textColor,
            textAlign = textAlign,
        )
    }
}
