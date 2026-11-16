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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.drss.DrssChapter
import com.vitorpamplona.amethyst.commons.drss.DrssChapters
import com.vitorpamplona.amethyst.commons.drss.DrssClipWatch
import com.vitorpamplona.amethyst.commons.drss.DrssListenRates
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.drss_cancel
import com.vitorpamplona.amethyst.commons.resources.drss_chapter_next
import com.vitorpamplona.amethyst.commons.resources.drss_chapter_previous
import com.vitorpamplona.amethyst.commons.resources.drss_chapters
import com.vitorpamplona.amethyst.commons.resources.drss_clip
import com.vitorpamplona.amethyst.commons.resources.drss_clip_card
import com.vitorpamplona.amethyst.commons.resources.drss_clip_delete
import com.vitorpamplona.amethyst.commons.resources.drss_clip_earlier
import com.vitorpamplona.amethyst.commons.resources.drss_clip_end
import com.vitorpamplona.amethyst.commons.resources.drss_clip_later
import com.vitorpamplona.amethyst.commons.resources.drss_clip_note
import com.vitorpamplona.amethyst.commons.resources.drss_clip_play
import com.vitorpamplona.amethyst.commons.resources.drss_clip_preview
import com.vitorpamplona.amethyst.commons.resources.drss_clip_range
import com.vitorpamplona.amethyst.commons.resources.drss_clip_save
import com.vitorpamplona.amethyst.commons.resources.drss_clip_start
import com.vitorpamplona.amethyst.commons.resources.drss_clip_title
import com.vitorpamplona.amethyst.commons.resources.drss_clips
import com.vitorpamplona.amethyst.commons.resources.drss_skip_back
import com.vitorpamplona.amethyst.commons.resources.drss_skip_back_short
import com.vitorpamplona.amethyst.commons.resources.drss_skip_forward
import com.vitorpamplona.amethyst.commons.resources.drss_skip_forward_short
import com.vitorpamplona.amethyst.commons.resources.drss_speed
import com.vitorpamplona.amethyst.commons.resources.drss_speed_description
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.note.platform.EpisodeControl
import com.vitorpamplona.amethyst.commons.ui.note.platform.EpisodePlayhead
import com.vitorpamplona.amethyst.commons.ui.note.platform.LocalNotePlatform
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.drss.clips.clipRangeMillis
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

private const val SKIP_BACK_MS = 15_000L
private const val SKIP_FORWARD_MS = 30_000L

/** How far before the playhead a new clip opens, and the longest clip, as drss.io's clipper. */
private const val CLIP_WINDOW_MS = 30_000L
private const val CLIP_MAX_MS = 300_000L
private const val CLIP_NUDGE_MS = 1_000L

private val PanelShape = RoundedCornerShape(12.dp)

/**
 * A long-form episode's audio, resumed where drss.io's listening-progress record (kind 30078, or
 * the older 30054) left it, with drss's controls: skips, the feed's speed, chapters from the show
 * notes, and clips. Starts on its own when [autoPlay] is set and calls [onEnded] when the episode
 * plays to its end. Draws nothing for an article without audio.
 *
 * Amethyst reads those records but writes none: kind 30078 is NIP-78 app data, which only the app
 * that owns it signs.
 */
@Composable
fun DrssEpisodeAudio(
    note: AddressableNote,
    event: LongFormContentEvent,
    accountViewModel: AccountViewModel,
    autoPlay: Boolean = false,
    onEnded: (() -> Unit)? = null,
) {
    val audio = remember(event) { event.episodeAudio() } ?: return
    val progress by observeMyListening(note, accountViewModel)
    val control = remember(note.address) { EpisodeControl() }
    val chapters = remember(event) { DrssChapters.parse(event.content) }
    val account = accountViewModel.account
    val feed = note.address.pubKeyHex
    val rates by account.drss.rates.flow
        .collectAsStateWithLifecycle()
    val speed = rates[feed] ?: 1f

    // Read only where it is needed (a click, the current chapter): it changes every second, and
    // reading it here would redraw the whole player that often.
    val playhead = remember(note.address) { mutableStateOf<EpisodePlayhead?>(null) }
    val clipWatch = remember(note.address) { mutableStateOf<DrssClipWatch?>(null) }
    var capture by remember(note.address) { mutableStateOf<LongRange?>(null) }

    val ended by rememberUpdatedState(onEnded)

    LaunchedEffect(note.address) {
        if (autoPlay) control.play()
    }

    val playRange: (LongRange) -> Unit =
        remember(control) {
            { range ->
                clipWatch.value = DrssClipWatch(range)
                control.seek(range.first, play = true)
            }
        }

    val onPlayhead: (EpisodePlayhead) -> Unit =
        remember(control) {
            { sample ->
                val wasEnded = playhead.value?.hasEnded == true
                playhead.value = sample
                clipWatch.value?.let { watch ->
                    when (watch.onSample(sample.positionMillis, sample.isPlaying)) {
                        DrssClipWatch.Step.PAUSE -> {
                            control.pause()
                            clipWatch.value = null
                        }
                        DrssClipWatch.Step.DONE -> clipWatch.value = null
                        DrssClipWatch.Step.KEEP -> {}
                    }
                }
                if (sample.hasEnded && !wasEnded) ended?.invoke()
            }
        }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        progress?.let { DrssHeardBadge(it) }
        LocalNotePlatform.current.EpisodeAudioPlayer(
            audioUrl = audio.url,
            mimeType = audio.mimeType,
            title = event.title(),
            image = event.image(),
            note = note,
            startAtSeconds = progress?.position()?.takeIf { it > 0 },
            speed = speed,
            control = control,
            onPlayhead = onPlayhead,
            accountViewModel = accountViewModel,
        )

        DrssEpisodeControls(
            playhead = playhead,
            chapters = chapters,
            speed = speed,
            onSpeed = { account.drss.rates.set(feed, it) },
            control = control,
            canClip = account.isWriteable() && capture == null,
            onClip = {
                val now = playhead.value?.positionMillis ?: 0
                val duration = playhead.value?.durationMillis
                val end = if (now > 0) now else CLIP_WINDOW_MS.coerceAtMost(duration ?: CLIP_WINDOW_MS)
                capture = (end - CLIP_WINDOW_MS).coerceAtLeast(0)..end
            },
        )

        capture?.let { range ->
            DrssClipMaker(
                range = range,
                durationMillis = { playhead.value?.durationMillis },
                onChange = { capture = it },
                onPreview = playRange,
                onSave = { comment ->
                    capture = null
                    accountViewModel.launchSigner {
                        account.drss.saveClip(note, range.first, range.last, comment, account)
                    }
                },
                onCancel = { capture = null },
            )
        }

        if (chapters.isNotEmpty()) {
            DrssChapterList(chapters, playhead) { control.seek(it * 1000, play = true) }
        }

        DrssEpisodeClips(note, accountViewModel, playRange)
    }
}

@Composable
private fun DrssEpisodeControls(
    playhead: State<EpisodePlayhead?>,
    chapters: List<DrssChapter>,
    speed: Float,
    onSpeed: (Float) -> Unit,
    control: EpisodeControl,
    canClip: Boolean,
    onClip: () -> Unit,
) {
    fun positionMillis() = playhead.value?.positionMillis ?: 0

    // Spoken labels for the text buttons, which show only "−15s", "+30s" and the speed.
    val backLabel = stringRes(Res.string.drss_skip_back)
    val forwardLabel = stringRes(Res.string.drss_skip_forward)
    val speedLabel = stringRes(Res.string.drss_speed_description)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (chapters.isNotEmpty()) {
            IconButton(onClick = { control.seek(DrssChapters.previousStart(chapters, positionMillis() / 1000) * 1000) }) {
                Icon(symbol = MaterialSymbols.SkipPrevious, contentDescription = stringRes(Res.string.drss_chapter_previous))
            }
        }
        TextButton(
            onClick = { control.seek((positionMillis() - SKIP_BACK_MS).coerceAtLeast(0)) },
            modifier = Modifier.semantics { contentDescription = backLabel },
        ) {
            Text(stringRes(Res.string.drss_skip_back_short), maxLines = 1)
        }
        TextButton(
            onClick = {
                val target = positionMillis() + SKIP_FORWARD_MS
                val durationMillis = playhead.value?.durationMillis
                control.seek(if (durationMillis != null) target.coerceAtMost(durationMillis) else target)
            },
            modifier = Modifier.semantics { contentDescription = forwardLabel },
        ) {
            Text(stringRes(Res.string.drss_skip_forward_short), maxLines = 1)
        }
        if (chapters.isNotEmpty()) {
            val hasNext by remember(chapters) { derivedStateOf { DrssChapters.nextStart(chapters, positionMillis() / 1000) != null } }
            IconButton(onClick = { DrssChapters.nextStart(chapters, positionMillis() / 1000)?.let { control.seek(it * 1000) } }, enabled = hasNext) {
                Icon(symbol = MaterialSymbols.SkipNext, contentDescription = stringRes(Res.string.drss_chapter_next))
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(
            onClick = {
                val rates = DrssListenRates.RATES
                onSpeed(rates[(rates.indexOf(speed) + 1) % rates.size])
            },
            modifier = Modifier.semantics { contentDescription = speedLabel },
        ) {
            Text(
                text = stringRes(Res.string.drss_speed, formatRate(speed)),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        if (canClip) {
            TextButton(onClick = onClip) { Text(stringRes(Res.string.drss_clip), maxLines = 1) }
        }
    }
}

/** `1`, `1.25`, `1.5`, `2`. */
private fun formatRate(rate: Float): String {
    val hundredths = (rate * 100).toInt()
    return when {
        hundredths % 100 == 0 -> (hundredths / 100).toString()
        hundredths % 10 == 0 -> "${hundredths / 100}.${(hundredths % 100) / 10}"
        else -> "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
    }
}

@Composable
private fun DrssChapterList(
    chapters: List<DrssChapter>,
    playhead: State<EpisodePlayhead?>,
    onSeek: (Long) -> Unit,
) {
    val current by remember(chapters) { derivedStateOf { DrssChapters.currentIndex(chapters, (playhead.value?.positionMillis ?: 0) / 1000) } }
    Text(stringRes(Res.string.drss_chapters), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.grayText)
    Column(Modifier.fillMaxWidth().clip(PanelShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))) {
        chapters.forEachIndexed { index, chapter ->
            val isCurrent = index == current
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().clickable { onSeek(chapter.seconds) }.padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = formatListenClock(chapter.seconds),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.grayText,
                    modifier = Modifier.width(56.dp),
                )
                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The range of a new clip, nudged a second at a time, a note, and save. */
@Composable
private fun DrssClipMaker(
    range: LongRange,
    durationMillis: () -> Long?,
    onChange: (LongRange) -> Unit,
    onPreview: (LongRange) -> Unit,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var comment by remember { mutableStateOf("") }
    val nudge: (Boolean, Int) -> Unit = { start, direction ->
        var first = range.first
        var last = range.last
        if (start) {
            first = (first + direction * CLIP_NUDGE_MS).coerceAtLeast(0)
        } else {
            last = last + direction * CLIP_NUDGE_MS
            durationMillis()?.let { last = last.coerceAtMost(it) }
        }
        if (last > first && last - first <= CLIP_MAX_MS) onChange(first..last)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringRes(Res.string.drss_clip_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DrssClipEdge(stringRes(Res.string.drss_clip_start), range.first, { nudge(true, -1) }, { nudge(true, 1) })
            DrssClipEdge(stringRes(Res.string.drss_clip_end), range.last, { nudge(false, -1) }, { nudge(false, 1) })
        }
        SpellCheckedOutlinedTextField(
            value = comment,
            onValueChange = { comment = it },
            placeholder = { Text(stringRes(Res.string.drss_clip_note)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { onPreview(range) }) { Text(stringRes(Res.string.drss_clip_preview)) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text(stringRes(Res.string.drss_cancel)) }
            Button(onClick = { onSave(comment.trim()) }) { Text(stringRes(Res.string.drss_clip_save)) }
        }
    }
}

@Composable
private fun DrssClipEdge(
    label: String,
    millis: Long,
    onEarlier: () -> Unit,
    onLater: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.grayText)
        IconButton(onClick = onEarlier, modifier = Modifier.size(36.dp)) {
            Icon(symbol = MaterialSymbols.Remove, contentDescription = stringRes(Res.string.drss_clip_earlier), modifier = Modifier.size(18.dp))
        }
        Text(formatListenClock(millis / 1000), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onLater, modifier = Modifier.size(36.dp)) {
            Icon(symbol = MaterialSymbols.Add, contentDescription = stringRes(Res.string.drss_clip_later), modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * The clips people made of this episode (highlights of it with a range), each played on this
 * player; the user's own can be deleted.
 */
@Composable
private fun DrssEpisodeClips(
    episode: AddressableNote,
    accountViewModel: AccountViewModel,
    onPlay: (LongRange) -> Unit,
) {
    // Highlights are kept by no index the cache can use for an `a` filter, so the first look reads
    // the whole cache: off the main thread, with the ranges read there too.
    val ranged by remember(episode.address) {
        LocalCache
            .observeNotes(Filter(kinds = listOf(HighlightEvent.KIND), tags = mapOf("a" to listOf(episode.address.toValue()))))
            .map { clips -> clips.mapNotNull { clip -> (clip.event as? HighlightEvent)?.clipRangeMillis()?.let { clip to it } }.sortedBy { it.second.first } }
            .flowOn(Dispatchers.Default)
    }.collectAsStateWithLifecycle(emptyList())
    if (ranged.isEmpty()) return

    Text(stringRes(Res.string.drss_clips), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.grayText)
    Column(Modifier.fillMaxWidth().clip(PanelShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))) {
        ranged.forEach { (clip, range) -> DrssClipRow(clip, range, accountViewModel, onPlay) }
    }
}

@Composable
private fun DrssClipRow(
    clip: Note,
    range: LongRange,
    accountViewModel: AccountViewModel,
    onPlay: (LongRange) -> Unit,
) {
    val event = clip.event as? HighlightEvent ?: return
    val isMine = event.pubKey == accountViewModel.account.pubKey
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().clickable { onPlay(range) }.padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
    ) {
        IconButton(onClick = { onPlay(range) }) {
            Icon(symbol = MaterialSymbols.PlayArrow, contentDescription = stringRes(Res.string.drss_clip_play), tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringRes(Res.string.drss_clip_range, formatListenClock(range.first / 1000), formatListenClock(range.last / 1000)),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            val words = event.comment()?.ifBlank { null } ?: event.quote().ifBlank { null }
            if (words != null) {
                Text(words, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            clip.author?.let { author ->
                Text(author.toBestDisplayName(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.grayText, maxLines = 1)
            }
        }
        if (isMine) {
            IconButton(onClick = { accountViewModel.delete(clip) }) {
                Icon(symbol = MaterialSymbols.Delete, contentDescription = stringRes(Res.string.drss_clip_delete), tint = MaterialTheme.colorScheme.grayText)
            }
        }
    }
}

/** A clip's range on a highlight card, standing in for a quote when the clip has no transcript. */
@Composable
fun DrssClipLabel(
    rangeMillis: LongRange,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(symbol = MaterialSymbols.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Text(
            text = stringRes(Res.string.drss_clip_card, formatListenClock(rangeMillis.first / 1000), formatListenClock(rangeMillis.last / 1000)),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
