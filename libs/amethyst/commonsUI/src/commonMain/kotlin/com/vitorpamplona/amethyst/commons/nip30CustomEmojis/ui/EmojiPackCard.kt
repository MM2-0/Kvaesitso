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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.emoji_pack_emoji_count
import com.vitorpamplona.amethyst.commons.resources.my_emoji_list_by_author
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes

private val CardShape = RoundedCornerShape(16.dp)
private val SheetShape = RoundedCornerShape(12.dp)
private val CellShape = RoundedCornerShape(8.dp)
private val CellGap = 6.dp

/** Above this many emojis the sheet becomes a 4-wide mosaic of [MOSAIC_CELLS], the last one "+N". */
private const val MOSAIC_CELLS = 16

/**
 * An emoji pack as a sticker sheet: the emojis themselves fill the top of the card at a size that
 * suits how many there are, so a two-emoji pack is two big emojis rather than two thumbnails and
 * four empty boxes, and a 175-emoji pack reads as a dense mosaic ending in "+160". Under it, the
 * pack's cover as a small icon, its title, author and size, and an optional [action] (the add
 * toggle on Browse).
 *
 * Cards come out at different heights, which is the point: on a staggered grid big packs take more
 * room than small ones instead of every pack being the same box.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EmojiPackCard(
    title: String,
    emojiUrls: List<String>,
    coverImage: String? = null,
    author: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        elevation = CardDefaults.elevatedCardElevation(),
    ) {
        // Clickable inside the card, not on its modifier: the card already clips its content to
        // its shape, and a clip on the outside would cut off the elevation shadow.
        Column(modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(10.dp)) {
            EmojiSheet(emojiUrls)

            Spacer(Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!coverImage.isNullOrBlank()) {
                    AsyncImage(
                        model = coverImage,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp).clip(CellShape),
                        contentScale = ContentScale.Crop,
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!author.isNullOrBlank()) {
                        Text(
                            text = stringRes(Res.string.my_emoji_list_by_author, author),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = pluralStringRes(Res.plurals.emoji_pack_emoji_count, emojiUrls.size, emojiUrls.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }

                action?.invoke()
            }
        }
    }
}

/** How many cells per row a pack of [count] emojis gets: fewer, bigger cells for small packs. */
internal fun sheetColumns(count: Int): Int =
    when {
        count <= 1 -> 1
        count == 2 -> 2
        count == 3 -> 3
        count == 4 -> 2
        count <= 9 -> 3
        else -> 4
    }

@Composable
private fun EmojiSheet(urls: List<String>) {
    val panel = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val sheetModifier =
        remember(panel) {
            Modifier
                .fillMaxWidth()
                .clip(SheetShape)
                .background(panel)
                .padding(8.dp)
        }

    when (urls.size) {
        0 -> {
            Box(modifier = sheetModifier.height(96.dp), contentAlignment = Alignment.Center) {
                Icon(
                    symbol = MaterialSymbols.EmojiEmotions,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        1 -> {
            Box(modifier = sheetModifier.height(120.dp), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = urls[0],
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }

        else -> {
            val columns = sheetColumns(urls.size)
            val overflow = urls.size > MOSAIC_CELLS
            val shown = if (overflow) urls.subList(0, MOSAIC_CELLS - 1) else urls
            val more = urls.size - shown.size

            Column(modifier = sheetModifier, verticalArrangement = Arrangement.spacedBy(CellGap)) {
                val cells = if (overflow) shown.size + 1 else shown.size
                val rows = (cells + columns - 1) / columns
                for (row in 0 until rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(CellGap)) {
                        for (col in 0 until columns) {
                            val index = row * columns + col
                            val cellModifier = Modifier.weight(1f).aspectRatio(1f)
                            when {
                                index < shown.size -> {
                                    AsyncImage(
                                        model = shown[index],
                                        contentDescription = null,
                                        modifier = cellModifier.clip(CellShape),
                                        contentScale = ContentScale.Fit,
                                    )
                                }

                                overflow && index == shown.size -> {
                                    Box(
                                        modifier = cellModifier.clip(CellShape).background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "+$more",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                        )
                                    }
                                }

                                // The ragged end of the last row: room, not an empty slot.
                                else -> {
                                    Spacer(cellModifier)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
