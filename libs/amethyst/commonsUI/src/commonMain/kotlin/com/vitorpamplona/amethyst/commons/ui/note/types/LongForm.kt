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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.long_form_published
import com.vitorpamplona.amethyst.commons.resources.long_form_reading_minutes
import com.vitorpamplona.amethyst.commons.resources.preview_card_image_for
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.BaseUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.NoteUsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.WatchAuthor
import com.vitorpamplona.amethyst.commons.ui.note.elements.DefaultImageHeader
import com.vitorpamplona.amethyst.commons.ui.note.elements.DefaultImageHeaderBackground
import com.vitorpamplona.amethyst.commons.ui.note.elements.TimeAgo
import com.vitorpamplona.amethyst.commons.ui.note.elements.TimeAgoStyle
import com.vitorpamplona.amethyst.commons.ui.note.elements.ToggleableTimeAgoText
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Font10SP
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlin.math.abs

private const val WORDS_PER_MINUTE = 225
private val COVER_ASPECT_RATIO = 16f / 9f

@Composable
fun RenderLongFormContent(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? LongFormContentEvent ?: return

    LongFormHeader(noteEvent, note, accountViewModel)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LongFormHeader(
    noteEvent: LongFormContentEvent,
    note: Note,
    accountViewModel: AccountViewModel,
) {
    val image = noteEvent.image()
    val title = noteEvent.title()
    val summary =
        remember(noteEvent) {
            noteEvent.summary()?.ifBlank { null } ?: noteEvent.content.take(200).ifBlank { null }
        }
    val topics =
        remember(noteEvent) {
            noteEvent
                .topics()
                .distinct()
                .take(3)
                .toImmutableList()
        }
    val readingMinutes = remember(noteEvent) { estimateReadingMinutes(noteEvent.content) }
    // NIP-23: `published_at` is when the article first went out; `created_at` moves with every edit.
    val publishedAt = remember(noteEvent) { noteEvent.publishedAt() }

    Column(MaterialTheme.colorScheme.replyModifier) {
        LongFormCoverImage(image, note, accountViewModel)

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            summary?.let {
                Spacer(Modifier.padding(top = 6.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.grayText,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.padding(top = 12.dp))
            AuthorMetaRow(note, publishedAt, readingMinutes, accountViewModel)

            // How much of this episode the user has heard, when it is one.
            (note as? AddressableNote)?.let {
                DrssHeardBadge(it, noteEvent, accountViewModel, Modifier.padding(top = 8.dp))
            }

            if (topics.isNotEmpty()) {
                Spacer(Modifier.padding(top = 8.dp))
                LongFormTopicChips(topics)
            }
        }
    }
}

@Composable
private fun LongFormCoverImage(
    image: String?,
    note: Note,
    accountViewModel: AccountViewModel,
) {
    val imageShape = RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp)
    val imageModifier =
        Modifier
            .fillMaxWidth()
            .aspectRatio(COVER_ASPECT_RATIO)
            .clip(imageShape)

    Box(imageModifier) {
        if (image != null) {
            MyAsyncImage(
                imageUrl = image,
                contentDescription = stringRes(Res.string.preview_card_image_for, image),
                contentScale = ContentScale.Crop,
                mainImageModifier = Modifier.fillMaxWidth(),
                loadedImageModifier = imageModifier,
                accountViewModel = accountViewModel,
                onLoadingBackground = { DefaultImageHeaderBackground(note, accountViewModel, imageModifier) },
                onError = { DefaultImageHeader(note, accountViewModel, imageModifier) },
            )
        } else {
            DefaultImageHeader(note, accountViewModel, imageModifier)
        }
    }
}

/** The article's `t` topics as small chips; each opens its hashtag feed when [nav] is given. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LongFormTopicChips(
    topics: ImmutableList<String>,
    nav: INav? = null,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Size5dp),
        verticalArrangement = Arrangement.spacedBy(Size5dp),
    ) {
        topics.forEach { topic ->
            TopicChip(
                topic,
                if (nav != null) Modifier.clickable { nav.nav(Route.Hashtag(topic.lowercase())) } else Modifier,
            )
        }
    }
}

/** The article's date (its `published_at` when it has one) and reading time: "• 2d · 6 min read". */
@Composable
fun LongFormDateAndReadingTime(
    note: Note,
    publishedAt: Long?,
    readingMinutes: Int,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (publishedAt != null) TimeAgo(publishedAt) else TimeAgo(note)

        MetaSeparator()
        ReadingTimeBadge(readingMinutes)
    }
}

/**
 * An article's `published_at` when it says something its `created_at` does not: the thread view
 * already dates the article by `created_at` in its author row (the last edit, for an addressable
 * article), so the header repeats a date only when the article was first published materially
 * earlier (or later) than that.
 */
fun publishedAtWorthShowing(
    publishedAt: Long?,
    createdAt: Long,
): Long? = publishedAt?.takeIf { abs(it - createdAt) >= TimeUtils.ONE_HOUR }

/**
 * The thread header's line: "Published 3d · 6 min read" when [publishedAt] is set (see
 * [publishedAtWorthShowing]), just the reading time otherwise.
 */
@Composable
fun LongFormPublishedAndReadingTime(
    publishedAt: Long?,
    readingMinutes: Int,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (publishedAt != null) {
            Text(
                text = stringRes(Res.string.long_form_published),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.grayText,
                maxLines = 1,
            )
            Spacer(Modifier.padding(start = 4.dp))
            ToggleableTimeAgoText(
                timestamp = publishedAt,
                style = TimeAgoStyle.Short,
                color = MaterialTheme.colorScheme.grayText,
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
            )
            MetaSeparator()
        }
        ReadingTimeBadge(readingMinutes)
    }
}

@Composable
private fun TopicChip(
    topic: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "#$topic",
        style = MaterialTheme.typography.labelSmall,
        fontSize = Font10SP,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.grayText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun AuthorMetaRow(
    note: Note,
    publishedAt: Long?,
    readingMinutes: Int,
    accountViewModel: AccountViewModel,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        WatchAuthor(baseNote = note, accountViewModel) { user ->
            BaseUserPicture(user, 25.dp, accountViewModel, Modifier)
        }

        Spacer(Modifier.padding(start = 8.dp))

        NoteUsernameDisplay(
            baseNote = note,
            weight = Modifier.weight(1f, fill = false),
            textColor = MaterialTheme.colorScheme.onSurface,
            accountViewModel = accountViewModel,
        )

        MetaSeparator()
        LongFormDateAndReadingTime(note, publishedAt, readingMinutes)
    }
}

@Composable
private fun MetaSeparator() {
    Text(
        text = " · ",
        color = MaterialTheme.colorScheme.grayText,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun ReadingTimeBadge(minutes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            symbol = MaterialSymbols.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.grayText,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.padding(start = 4.dp))
        Text(
            text = stringRes(Res.string.long_form_reading_minutes, minutes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.grayText,
            maxLines = 1,
        )
    }
}

fun estimateReadingMinutes(content: String): Int {
    if (content.isBlank()) return 1
    val words = content.split(Regex("\\s+")).count { it.isNotBlank() }
    return maxOf(1, (words + WORDS_PER_MINUTE - 1) / WORDS_PER_MINUTE)
}
