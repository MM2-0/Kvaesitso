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
package com.vitorpamplona.amethyst.commons.ui.feeds

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.action_dismiss
import com.vitorpamplona.amethyst.commons.resources.action_try_again
import com.vitorpamplona.amethyst.commons.resources.chats_history_all_caught_up
import com.vitorpamplona.amethyst.commons.resources.chats_history_by_relay
import com.vitorpamplona.amethyst.commons.resources.chats_history_incomplete
import com.vitorpamplona.amethyst.commons.resources.chats_history_incomplete_sub
import com.vitorpamplona.amethyst.commons.resources.chats_history_older
import com.vitorpamplona.amethyst.commons.resources.chats_history_reached_start
import com.vitorpamplona.amethyst.commons.resources.chats_history_relay_since
import com.vitorpamplona.amethyst.commons.resources.chats_history_relays
import com.vitorpamplona.amethyst.commons.resources.chats_history_relays_title
import com.vitorpamplona.amethyst.commons.resources.chats_history_stalled_retry
import com.vitorpamplona.amethyst.commons.resources.chats_history_subtitle
import com.vitorpamplona.amethyst.commons.resources.chats_history_subtitle_no_date
import com.vitorpamplona.amethyst.commons.resources.chats_history_waiting
import com.vitorpamplona.quartz.nip01Core.relay.client.paging.RelayPagingProgress
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

// How long the "All caught up" state lingers before the card collapses away.
private const val ALL_DONE_VISIBLE_MS = 2200L

/**
 * The "older history" status card for a per-relay [BackwardRelayPager]-backed feed, shown at one
 * protocol's oldest-loaded boundary (rooms list and conversation). It tells the user exactly what the
 * app is reaching for: which protocol, how many relays it is asking, and how far back it has paged.
 * When that protocol runs dry it does NOT just vanish — it crossfades to an "All caught up" state,
 * holds for a beat, then collapses away. When it stops short because relays stalled it says so and
 * stays put (an *incomplete* window — messages may still be out there).
 *
 * Shared across front ends; pass the platform's locale date formatter as [formatReachDate] so the card
 * carries no `java.time` / `NSDateFormatter` dependency.
 *
 * @param protocolName human label woven into sentences, e.g. "encrypted" / "legacy".
 * @param protocolTag  short technical tag for the subtitle, e.g. "NIP-17" / "NIP-04".
 * @param reachedBack  epoch seconds of the oldest point reached so far (the deepest `until` cursor).
 * @param relayProgress per-relay reach (where each relay's window is, done/stalled). Tapping the card
 *   opens a popup listing them; pass empty to make the card non-interactive.
 * @param formatReachDate formats an epoch-seconds reach point to a short label (e.g. "Jun 2026").
 */
@Composable
fun DmHistoryLoadingCard(
    protocolName: String,
    protocolTag: String,
    loading: Boolean,
    exhausted: Boolean,
    relayCount: Int,
    stalledCount: Int,
    reachedBack: Long?,
    relayProgress: Map<NormalizedRelayUrl, RelayPagingProgress> = emptyMap(),
    formatReachDate: (epochSeconds: Long) -> String,
    modifier: Modifier = Modifier,
) {
    // Exhausted ("nothing more reachable right now") splits two ways and must NOT read the same:
    //  - caughtUp: every relay genuinely bottomed out (empty page). This is the real "all caught up".
    //  - incomplete: we stopped only because some relays are stalled (auth-walled / offline / silent),
    //    so messages may still be out there. It must say so, stay put, and let the user tap to see which.
    val caughtUp = exhausted && stalledCount <= 0
    val incomplete = exhausted && stalledCount > 0

    // Relays that still have older history to pull: not done and not stalled. Unlike [relayCount] (only
    // those fetching a page *right now*), this also counts relays that returned a page and PARKED — the
    // `⋯` paused state — so the subtitle doesn't read as a bare tag while the tap-popup lists N relays.
    val reaching = remember(relayProgress) { relayProgress.values.count { !it.done && !it.stalled } }

    // Only the genuine caught-up state lingers then collapses; an incomplete window stays so it can be acted on.
    var collapsed by remember { mutableStateOf(false) }
    LaunchedEffect(caughtUp) {
        collapsed =
            if (caughtUp) {
                delay(ALL_DONE_VISIBLE_MS)
                true
            } else {
                false
            }
    }

    var showRelays by remember { mutableStateOf(false) }
    if (showRelays) {
        DmHistoryRelayDialog(protocolTag, relayProgress, formatReachDate) { showRelays = false }
    }

    AnimatedVisibility(
        visible = !collapsed,
        modifier = modifier,
        enter = fadeIn(),
        exit = shrinkVertically(tween(400)) + fadeOut(tween(250)),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .then(if (relayProgress.isNotEmpty()) Modifier.clickable { showRelays = true } else Modifier),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            tonalElevation = 2.dp,
        ) {
            val phase =
                when {
                    caughtUp -> HistoryPhase.CaughtUp
                    incomplete -> HistoryPhase.Incomplete
                    else -> HistoryPhase.Loading
                }
            Crossfade(targetState = phase, animationSpec = tween(500), label = "dmHistoryState") { state ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                        when (state) {
                            HistoryPhase.CaughtUp ->
                                Text(
                                    "✓",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            HistoryPhase.Incomplete ->
                                // Same glyph the per-relay dialog uses for a stalled relay, same error colour —
                                // signals "stopped early, some relays didn't answer", not "done".
                                Text(
                                    "…",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            HistoryPhase.Loading ->
                                if (loading) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    // Paused: not caught up, but not actively loading (the auto-fill stopped short
                                    // of exhaustion, or we're between pages). Show a static "more" glyph so the
                                    // icon slot is never blank — resumes on scroll.
                                    Text(
                                        "⋯",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text =
                                when (state) {
                                    HistoryPhase.CaughtUp -> stringResource(Res.string.chats_history_all_caught_up)
                                    HistoryPhase.Incomplete -> stringResource(Res.string.chats_history_incomplete)
                                    HistoryPhase.Loading -> stringResource(Res.string.chats_history_older, protocolName)
                                },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text =
                                when (state) {
                                    HistoryPhase.CaughtUp -> stringResource(Res.string.chats_history_reached_start, protocolName)
                                    HistoryPhase.Incomplete -> incompleteSubtitle(stalledCount)
                                    HistoryPhase.Loading -> historySubtitle(protocolTag, reaching, stalledCount, reachedBack, formatReachDate)
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** The three terminal-vs-loading faces of the history card: still loading/paused, genuinely caught up, or
 * stopped early because relays stalled. Kept distinct so an incomplete window never reads as "all caught up". */
private enum class HistoryPhase { Loading, CaughtUp, Incomplete }

/** Subtitle for the "stopped early" state: how many relays we couldn't reach, with a hint to tap for the list.
 * Shared with the reply placeholder so both read identically. */
@Composable
fun incompleteSubtitle(stalledCount: Int): String =
    stringResource(
        Res.string.chats_history_incomplete_sub,
        pluralStringResource(Res.plurals.chats_history_relays, stalledCount, stalledCount),
    )

@Composable
fun historySubtitle(
    protocolTag: String,
    relayCount: Int,
    stalledCount: Int,
    reachedBack: Long?,
    formatReachDate: (epochSeconds: Long) -> String,
): String {
    val backLabel = remember(reachedBack) { reachedBack?.let(formatReachDate) }
    // Middle segment: relays still working on it — fetching a page OR parked with more to pull ("N relays")
    // — or, when none are reaching but some can't be reached, what we're waiting on ("waiting on N relays").
    // With neither (every relay done), just the tag. [relayCount] here is the reaching count, not in-flight.
    val middle =
        when {
            relayCount > 0 -> pluralStringResource(Res.plurals.chats_history_relays, relayCount, relayCount)
            stalledCount > 0 ->
                stringResource(
                    Res.string.chats_history_waiting,
                    pluralStringResource(Res.plurals.chats_history_relays, stalledCount, stalledCount),
                )
            else -> return protocolTag
        }
    return if (backLabel != null) {
        stringResource(Res.string.chats_history_subtitle, protocolTag, middle, backLabel)
    } else {
        stringResource(Res.string.chats_history_subtitle_no_date, protocolTag, middle)
    }
}

/**
 * Popup shown when the history card is tapped: one row per relay with its state glyph (✓ done, … stalled,
 * ↓ still reaching) and how far back it has paged ("since <date>"), deepest-reaching first. A stalled relay
 * also gets a one-line hint that it retries when the screen is reopened.
 */
@Composable
fun DmHistoryRelayDialog(
    protocolTag: String,
    relayProgress: Map<NormalizedRelayUrl, RelayPagingProgress>,
    formatReachDate: (epochSeconds: Long) -> String,
    onDismiss: () -> Unit,
) {
    val rows = remember(relayProgress) { relayProgress.entries.sortedBy { it.value.reachedUntil } }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_dismiss)) }
        },
        title = { Text(stringResource(Res.string.chats_history_relays_title, protocolTag)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                rows.forEach { (relay, p) ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = relayStateGlyph(p),
                                color = relayStateColor(p),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(22.dp),
                            )
                            Text(
                                text = relayShortName(relay),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(Res.string.chats_history_relay_since, formatReachDate(p.reachedUntil)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        if (p.stalled) StalledRetryHint()
                    }
                }
            }
        },
    )
}

/** The "stopped early, retries on reopen" caption shown under a stalled relay in the per-relay popups.
 * Retry is demand-driven (no timer): reopening the screen re-binds and clears the stalled set, which
 * retries it — so that's what we tell the user rather than a countdown we can't honour. */
@Composable
private fun StalledRetryHint() {
    Text(
        text = stringResource(Res.string.chats_history_stalled_retry),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(start = 22.dp, top = 2.dp),
    )
}

private fun relayStateGlyph(p: RelayPagingProgress) =
    when {
        p.done -> "✓"
        p.stalled -> "…"
        else -> "↓"
    }

@Composable
private fun relayStateColor(p: RelayPagingProgress): Color =
    when {
        p.done -> MaterialTheme.colorScheme.primary
        p.stalled -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

private fun relayShortName(relay: NormalizedRelayUrl): String =
    relay.url
        .substringAfter("://")
        .trimEnd('/')
        .substringBefore('/')

/**
 * Popup shown when an in-stream "Loading" marker is tapped: the relays whose window sits at that point
 * in the stream, each with its protocol tag, state glyph (✓ done · … stalled · ↓ reaching) and how far
 * back it has paged — so the otherwise-terse `Loading: ↓ N` divider stops being a dead end and its
 * meaning is explorable. A stalled relay also gets the retry-on-reopen hint. Deepest-reaching first.
 */
@Composable
fun RelayReachDetailDialog(
    cursors: List<RelayReachCursor>,
    formatReachDate: (epochSeconds: Long) -> String,
    // When provided and at least one relay is stalled, the dialog offers a "Try Again" action that
    // re-advances the stalled relays on demand (and suppresses the "retries on reopen" hint, since the
    // caller retries actively). Null keeps the passive, dismiss-only dialog (the DM callers).
    onRetry: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val rows = remember(cursors) { cursors.sortedBy { it.reachedUntil } }
    val showRetry = onRetry != null && rows.any { it.state == RelayReachState.STALLED }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            if (showRetry) {
                TextButton(onClick = {
                    onRetry()
                    onDismiss()
                }) { Text(stringResource(Res.string.action_try_again)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_dismiss)) }
            }
        },
        dismissButton =
            if (showRetry) {
                { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_dismiss)) } }
            } else {
                null
            },
        title = { Text(stringResource(Res.string.chats_history_by_relay)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                rows.forEach { c ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = reachGlyph(c.state),
                                color = reachColor(c.state),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(22.dp),
                            )
                            if (c.protocol.isNotEmpty()) {
                                Text(
                                    text = c.protocol,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                text = c.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(Res.string.chats_history_relay_since, formatReachDate(c.reachedUntil)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        // The passive "retries on reopen" caption only applies without an active Try Again
                        // action; with one, the button (and the caller's auto-retry) covers it.
                        if (c.state == RelayReachState.STALLED && onRetry == null) StalledRetryHint()
                    }
                }
            }
        },
    )
}
