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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.cyberspace.BagSweep
import com.vitorpamplona.amethyst.commons.cyberspace.BagSweepQuote
import com.vitorpamplona.amethyst.commons.cyberspace.BagSweepState
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cyberspace_bag_box
import com.vitorpamplona.amethyst.commons.resources.cyberspace_bag_cancel
import com.vitorpamplona.amethyst.commons.resources.cyberspace_bag_damaged
import com.vitorpamplona.amethyst.commons.resources.cyberspace_bag_destination
import com.vitorpamplona.amethyst.commons.resources.cyberspace_bag_dropped
import com.vitorpamplona.amethyst.commons.resources.cyberspace_bag_empty
import com.vitorpamplona.amethyst.commons.resources.cyberspace_bag_measuring
import com.vitorpamplona.amethyst.commons.resources.cyberspace_bag_no_hint
import com.vitorpamplona.amethyst.commons.resources.cyberspace_bag_not_found
import com.vitorpamplona.amethyst.commons.resources.cyberspace_bag_opaque
import com.vitorpamplona.amethyst.commons.resources.cyberspace_bag_out_of_reach
import com.vitorpamplona.amethyst.commons.resources.cyberspace_bag_progress
import com.vitorpamplona.amethyst.commons.resources.cyberspace_bag_search
import com.vitorpamplona.amethyst.commons.resources.cyberspace_bag_title
import com.vitorpamplona.amethyst.commons.resources.cyberspace_bag_too_slow_hours
import com.vitorpamplona.amethyst.commons.resources.cyberspace_bag_too_slow_minutes
import com.vitorpamplona.amethyst.commons.resources.cyberspace_bag_unknown_kind
import com.vitorpamplona.amethyst.commons.resources.cyberspace_bag_unsigned
import com.vitorpamplona.amethyst.commons.ui.note.SnoObjectCard
import com.vitorpamplona.amethyst.commons.ui.note.SnoObjectUnreadableCard
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.cyberspace.CyberspaceBagContents
import com.vitorpamplona.quartz.cyberspace.CyberspaceBagEvent
import com.vitorpamplona.quartz.cyberspace.CyberspaceBagItem
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoPaletteRef
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoResult
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoShardEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Entry for a region bag (`CYBERSPACE_V2.md` §7.6, kind 33330) — content
 * somebody hid at a place, addressed by a hash of a hash.
 *
 * The card's job is to say what the search costs and then not start it. §7.7's
 * hint is the hider's difficulty knob, which makes the price part of the
 * content: a box of 4,096 regions is a minute of a phone, and a box of 2^30 is
 * a day of it, chosen by a stranger. So the gap is read off two tags while the
 * row composes — arithmetic, no trees — and the card either offers a button or
 * says the box is out of reach.
 *
 * **Every sweep is a tap, including a gap-0 hint.** §7.7 calls that one "a
 * destination the seeker can compute or walk to directly, not a search", and it
 * costs about as long as a frame. It still waits for the tap, because a reader
 * who learns that some bags open themselves has learned an expectation a
 * hostile bag can hide inside.
 *
 * What comes out renders through the cards that already exist: a `3330` shard
 * through [SnoObjectCard], a `kind 1` as its text, anything else named and
 * skipped. §7.6 decides the attribution and it is not the obvious one —
 * *placement* belongs to the bag's author, *authorship* only to a signed item's
 * own pubkey — so an unsigned item says so under it rather than borrowing
 * either name.
 */
@Composable
fun RenderCyberspaceBag(
    baseNote: Note,
    accountViewModel: AccountViewModel,
) {
    val noteEvent = baseNote.event as? CyberspaceBagEvent ?: return
    val quote = remember(noteEvent) { BagSweep.quote(noteEvent) }

    var state by remember(noteEvent) { mutableStateOf<BagSweepState?>(null) }
    var job by remember(noteEvent) { mutableStateOf<Job?>(null) }

    // Scrolling the row away is a cancel: this scope dies with the composition,
    // and the sweep is a cold flow, so the work stops at the next candidate.
    // Nothing here should outlive the card that asked for it.
    val scope = rememberCoroutineScope()

    Column(MaterialTheme.colorScheme.replyModifier.padding(10.dp)) {
        Text(
            text = stringResource(Res.string.cyberspace_bag_title),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text =
                when (quote) {
                    is BagSweepQuote.Hidden -> stringResource(Res.string.cyberspace_bag_no_hint)
                    is BagSweepQuote.OutOfReach -> stringResource(Res.string.cyberspace_bag_out_of_reach, quote.gapBits)
                    is BagSweepQuote.Searchable ->
                        if (quote.destination) {
                            stringResource(Res.string.cyberspace_bag_destination)
                        } else {
                            stringResource(Res.string.cyberspace_bag_box, quote.candidates.toString())
                        }
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.placeholderText,
        )

        if (quote is BagSweepQuote.Searchable) {
            Sweeper(
                state = state,
                onStart = {
                    job =
                        scope.launch {
                            BagSweep
                                .sweep(noteEvent)
                                .flowOn(BagSweep.SweepDispatcher)
                                .collect { state = it }
                        }
                },
                onCancel = {
                    job?.cancel()
                    job = null
                    state = null
                },
            )
        }

        when (val settled = state) {
            is BagSweepState.Opened -> Contents(settled.contents, accountViewModel)
            is BagSweepState.NotFound -> Footnote(stringResource(Res.string.cyberspace_bag_not_found))
            is BagSweepState.OutOfReach -> Footnote(outOfReach(settled.estimateMillis))
            else -> {}
        }
    }
}

/** The button, or the progress and the stop that replace it while a sweep runs. */
@Composable
private fun Sweeper(
    state: BagSweepState?,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    when (state) {
        null -> TextButton(onClick = onStart) { Text(stringResource(Res.string.cyberspace_bag_search)) }

        is BagSweepState.Measuring -> Progress(null, stringResource(Res.string.cyberspace_bag_measuring), onCancel)

        is BagSweepState.Running ->
            Progress(
                fraction = if (state.candidates > 0) state.examined.toFloat() / state.candidates else null,
                label = stringResource(Res.string.cyberspace_bag_progress, state.examined.toString(), state.candidates.toString()),
                onCancel = onCancel,
            )

        // Settled. A cancel resets the state to null and the button comes back;
        // an outcome does not, because sweeping the same box again derives the
        // same keys and reaches the same answer.
        else -> {}
    }
}

@Composable
private fun Progress(
    fraction: Float?,
    label: String,
    onCancel: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.placeholderText,
            )
            Spacer(Modifier.height(4.dp))
            if (fraction == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onCancel) { Text(stringResource(Res.string.cyberspace_bag_cancel)) }
    }
}

/**
 * What the bag held (§7.6's two plaintext shapes).
 *
 * A null [contents] is the one case that is genuinely a broken bag: the
 * `lookup_id` matched, and §7.2 makes that a hash of the very key we tried, so
 * a GCM tag that then fails is damage rather than a wrong reader.
 */
@Composable
private fun Contents(
    contents: CyberspaceBagContents?,
    accountViewModel: AccountViewModel,
) {
    when (contents) {
        null -> Footnote(stringResource(Res.string.cyberspace_bag_damaged))

        is CyberspaceBagContents.Opaque ->
            Footnote(asText(contents.bytes) ?: pluralStringResource(Res.plurals.cyberspace_bag_opaque, contents.bytes.size, contents.bytes.size))

        is CyberspaceBagContents.Items -> {
            if (contents.items.isEmpty() && contents.dropped == 0) {
                Footnote(stringResource(Res.string.cyberspace_bag_empty))
            }
            contents.items.forEach { item ->
                Spacer(Modifier.height(6.dp))
                Item(item, accountViewModel)
            }
            // §7.6: a forged item costs itself and nothing else, but it is
            // still worth saying that something was thrown away.
            if (contents.dropped > 0) {
                Spacer(Modifier.height(6.dp))
                Footnote(pluralStringResource(Res.plurals.cyberspace_bag_dropped, contents.dropped, contents.dropped))
            }
        }
    }
}

/**
 * One item, through whichever card already draws its kind.
 *
 * The `3330` case is what a bag is usually for: DECK-0003 §3.2's shard, whose
 * payload is the same format a standalone object carries, which is why
 * [RenderSnoShard] and this end at the same card. An item that came out of a
 * bag is never in [com.vitorpamplona.amethyst.commons.model.cache.LocalCache] —
 * it was ciphertext a moment ago — so there is no `Note` to hand the usual
 * renderers, and these draw from the event directly.
 */
@Composable
private fun Item(
    item: CyberspaceBagItem,
    accountViewModel: AccountViewModel,
) {
    when (val event = item.event) {
        is SnoShardEvent -> {
            val first = remember(event) { event.shard() }
            WithSnoPalette(first.payloadOrNull()?.paletteRef ?: SnoPaletteRef.BuiltIn, accountViewModel) { palette ->
                when (val parsed = remember(event, palette) { if (palette == null) first else event.shard(palette) }) {
                    is SnoResult.Invalid -> SnoObjectUnreadableCard(parsed.rule)
                    is SnoResult.Valid -> SnoObjectCard(payload = parsed.payload, eventId = event.id)
                }
            }
        }

        is TextNoteEvent -> Text(text = event.content, style = MaterialTheme.typography.bodyMedium)

        // §7.6: "a reader that does not understand an item's kind skips it and
        // renders the rest". Named rather than silent, so the count adds up.
        else -> Footnote(stringResource(Res.string.cyberspace_bag_unknown_kind, event.kind))
    }

    // §7.6: an item without a `sig` is allowed, "its `pubkey` is then a claim,
    // and readers MUST NOT present it as verified". Placement still belongs to
    // the bag's author either way, which is why this only disclaims authorship.
    if (!item.verified) Footnote(stringResource(Res.string.cyberspace_bag_unsigned))
}

/** A small grey line: this card's only other voice. */
@Composable
private fun Footnote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.placeholderText,
    )
}

/** The refusal, in whichever unit does not read as a wall of minutes. */
@Composable
private fun outOfReach(estimateMillis: Long): String {
    val minutes = estimateMillis / 60_000L
    return if (minutes >= MINUTES_PER_HOUR) {
        val hours = (minutes / MINUTES_PER_HOUR).toInt()
        pluralStringResource(Res.plurals.cyberspace_bag_too_slow_hours, hours, hours)
    } else {
        pluralStringResource(Res.plurals.cyberspace_bag_too_slow_minutes, minutes.toInt(), minutes.toInt())
    }
}

/**
 * §7.6's other shape as text, or null when it is not UTF-8 — "a text note or a
 * file", and a file should not be shown as a wall of replacement characters.
 */
private fun asText(bytes: ByteArray): String? {
    val text = bytes.decodeToString()
    return if (text.encodeToByteArray().contentEquals(bytes)) text else null
}

private const val MINUTES_PER_HOUR = 60L
