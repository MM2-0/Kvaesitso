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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.nip30CustomEmojis.ui.EmojiPreviewDialog
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.search_emojis_header
import com.vitorpamplona.amethyst.commons.resources.search_emojis_show_all
import com.vitorpamplona.amethyst.commons.search.EmojiSearchHit
import com.vitorpamplona.amethyst.commons.ui.components.HoverTooltip
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/** How many emoji tiles show before "Show all": three rows or so on a phone. */
private const val COLLAPSED_HITS = 18

private val HitWidth = 68.dp
private val HitTile = 56.dp
private val HitShape = RoundedCornerShape(12.dp)

/**
 * The single emojis a search found, as labelled tiles above the notes: the emoji, and under it the
 * shortcode a reader types to use it. Tapping one opens [EmojiPreviewDialog] with the pack it came
 * from.
 */
@Composable
fun EmojiSearchResults(
    hits: List<EmojiSearchHit>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Keyed on the hits: "Show all" for one search must not leave the next one opened up.
    var expanded by rememberSaveable(hits) { mutableStateOf(false) }
    var preview by remember { mutableStateOf<EmojiSearchHit?>(null) }
    val shown = if (expanded || hits.size <= COLLAPSED_HITS) hits else hits.subList(0, COLLAPSED_HITS)

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(
            text = stringRes(Res.string.search_emojis_header),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.placeholderText,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )

        val tileColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        val tileModifier = remember(tileColor) { Modifier.size(HitTile).clip(HitShape).background(tileColor) }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            shown.forEach { hit -> EmojiHitTile(hit, tileModifier) { preview = hit } }
        }

        if (!expanded && hits.size > COLLAPSED_HITS) {
            TextButton(onClick = { expanded = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(pluralStringRes(Res.plurals.search_emojis_show_all, hits.size, hits.size))
            }
        }
    }

    preview?.let { hit ->
        EmojiPreviewDialog(
            emoji = hit.emoji,
            packNote = hit.pack,
            accountViewModel = accountViewModel,
            nav = nav,
            onDismiss = { preview = null },
        )
    }
}

@Composable
private fun EmojiHitTile(
    hit: EmojiSearchHit,
    tileModifier: Modifier,
    onClick: () -> Unit,
) {
    val label = remember(hit.emoji.code) { ":${hit.emoji.code}:" }
    HoverTooltip(label) {
        Column(
            modifier =
                Modifier
                    .width(HitWidth)
                    .clip(HitShape)
                    .clickable(onClick = onClick)
                    .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = tileModifier, contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = hit.emoji.url,
                    contentDescription = label,
                    modifier = Modifier.size(44.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            Text(
                text = hit.emoji.code,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
            )
        }
    }
}
