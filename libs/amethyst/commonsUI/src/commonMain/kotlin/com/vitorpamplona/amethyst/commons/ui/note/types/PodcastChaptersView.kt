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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.podcasts.fetchPreviewText
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.podcast_chapters_count
import com.vitorpamplona.amethyst.commons.ui.note.PodcastSoundbites
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size18Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.podcasts.PodcastChapter
import com.vitorpamplona.quartz.podcasts.PodcastChapters

/**
 * The episode's Podcasting-2.0 chapters (from the off-event `chapters.json` referenced by the
 * `chapters` tag), fetched and rendered as a collapsible, tappable list. Tapping a chapter calls
 * [onSeek] with its start in milliseconds so the host can seek the live media controller — the same
 * contract as [PodcastSoundbites].
 */
@Composable
fun PodcastChaptersView(
    chaptersUrl: String,
    onSeek: (startMillis: Long) -> Unit,
    accountViewModel: AccountViewModel,
) {
    val chapters by produceState(initialValue = emptyList<PodcastChapter>(), chaptersUrl) {
        val body = accountViewModel.httpClientBuilder.fetchPreviewText(chaptersUrl)
        value = body?.let { PodcastChapters.parse(it)?.chapters }?.filter { it.title?.isNotBlank() == true } ?: emptyList()
    }

    if (chapters.isEmpty()) return

    var expanded by remember(chaptersUrl) { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        CollapsibleHeader(
            symbol = MaterialSymbols.AutoMirrored.FormatListBulleted,
            title = pluralStringRes(Res.plurals.podcast_chapters_count, chapters.size, chapters.size),
            expanded = expanded,
            onToggle = { expanded = !expanded },
        )

        if (expanded) {
            chapters.forEach { chapter ->
                ChapterRow(chapter, onSeek)
            }
        }
    }
}

@Composable
private fun ChapterRow(
    chapter: PodcastChapter,
    onSeek: (Long) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onSeek(chapter.startSeconds() * 1000) }
                .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = formatChapterTime(chapter.startTime),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = chapter.title.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun CollapsibleHeader(
    symbol: MaterialSymbol,
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            symbol = symbol,
            contentDescription = null,
            modifier = Size18Modifier,
            tint = MaterialTheme.colorScheme.grayText,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Icon(
            symbol = if (expanded) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.grayText,
        )
    }
}

private fun formatChapterTime(totalSeconds: Double): String {
    val total = totalSeconds.toLong()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    val two = { n: Long -> if (n < 10) "0$n" else "$n" }
    return if (h > 0) "$h:${two(m)}:${two(s)}" else "$m:${two(s)}"
}
