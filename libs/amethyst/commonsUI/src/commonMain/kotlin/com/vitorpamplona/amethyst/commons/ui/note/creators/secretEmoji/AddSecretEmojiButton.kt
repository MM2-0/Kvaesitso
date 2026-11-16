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
package com.vitorpamplona.amethyst.commons.ui.note.creators.secretEmoji

import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.secret_emoji_maker_explainer
import com.vitorpamplona.amethyst.commons.ui.components.HoverTooltip
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier

@Composable
fun AddSecretEmojiButton(
    isSecretEmojiActive: Boolean,
    onClick: () -> Unit,
) {
    HoverTooltip(stringRes(Res.string.secret_emoji_maker_explainer)) {
        IconButton(
            onClick = { onClick() },
        ) {
            if (!isSecretEmojiActive) {
                Icon(
                    symbol = MaterialSymbols.Assistant,
                    contentDescription = stringRes(id = Res.string.secret_emoji_maker_explainer),
                    modifier = Size20Modifier,
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            } else {
                Icon(
                    symbol = MaterialSymbols.Assistant,
                    contentDescription = stringRes(id = Res.string.secret_emoji_maker_explainer),
                    modifier = Size20Modifier,
                    tint = BitcoinOrange,
                )
            }
        }
    }
}
