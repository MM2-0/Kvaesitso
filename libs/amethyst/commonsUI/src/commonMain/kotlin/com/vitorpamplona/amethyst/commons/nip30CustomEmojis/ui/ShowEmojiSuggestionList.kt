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
package com.vitorpamplona.amethyst.commons.nip30CustomEmojis.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiSuggestionState
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.use_direct_url
import org.jetbrains.compose.resources.stringResource

private val DividerThickness = 0.25.dp
private val RowSpacing = 10.dp
private val EmojiSize = Modifier.size(40.dp)

@Composable
fun ShowEmojiSuggestionList(
    emojiSuggestions: EmojiSuggestionState,
    onSelect: (EmojiPackState.EmojiMedia) -> Unit,
    onFullSize: (EmojiPackState.EmojiMedia) -> Unit,
    modifier: Modifier = Modifier,
) {
    val suggestions by emojiSuggestions.results.collectAsStateWithLifecycle(emptyList())

    if (suggestions.isNotEmpty()) {
        LazyColumn(
            contentPadding = PaddingValues(top = 10.dp),
            modifier = modifier,
        ) {
            items(suggestions) {
                Row(
                    modifier =
                        Modifier.clickable { onSelect(it) }.padding(
                            start = 12.dp,
                            end = 12.dp,
                            top = 10.dp,
                            bottom = 10.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = spacedBy(RowSpacing),
                ) {
                    AsyncImage(
                        it.link,
                        contentDescription = it.code,
                        modifier = EmojiSize,
                    )
                    Text(it.code, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(
                        modifier = EmojiSize,
                        onClick = {
                            onFullSize(it)
                        },
                    ) {
                        Icon(
                            symbol = MaterialSymbols.OpenInFull,
                            contentDescription = stringResource(Res.string.use_direct_url),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                HorizontalDivider(
                    thickness = DividerThickness,
                )
            }
        }
    }
}
