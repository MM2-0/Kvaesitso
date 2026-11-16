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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.chats_history_fully_loaded
import com.vitorpamplona.amethyst.commons.resources.chats_history_fully_loaded_label
import com.vitorpamplona.amethyst.commons.resources.chats_history_loading_label
import com.vitorpamplona.amethyst.commons.resources.chats_history_relays
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

// A relay-reach divider is hair-thin; inlined here so the shared component carries no app-theme dep.
private val DividerThickness = 0.25.dp

/**
 * True when [reachedUntil] falls in the gap between a newer message (at [newerCreatedAt]) and its
 * next-older neighbour (at [olderCreatedAt], null past the oldest end): the newer side is strictly
 * newer than the cursor and the older side is at or below it (or absent). This single predicate both
 * places the marker ([RelayReachMarkers]) and decides when its paging sentinel is on screen
 * ([RelayReachSentinels]), so the two can never disagree about which gap a cursor lives in.
 */
internal fun reachedFallsInGap(
    reachedUntil: Long,
    newerCreatedAt: Long?,
    olderCreatedAt: Long?,
): Boolean =
    newerCreatedAt != null &&
        newerCreatedAt > reachedUntil &&
        (olderCreatedAt == null || olderCreatedAt <= reachedUntil)

/** How far one relay has paged into a feed's history, for an in-stream progress marker. */
enum class RelayReachState {
    // Still paging older — its marker slides down (older) as it advances.
    REACHING,

    // Accepted but not answering right now (auth CLOSE / unreachable / slow); kept open, still trying.
    STALLED,

    // Hit an empty page: nothing older on this relay, it has reached the bottom of its window.
    DONE,
}

/** One relay's marker entry within a gap. */
data class RelayReach(
    val name: String,
    val state: RelayReachState,
)

/**
 * One relay's window-limit: places a marker and carries the [advance] that pulls that relay's next,
 * older page. The marker sits at [reachedUntil] (the oldest point the relay has paged to);
 * [RelayReachMarkers] draws it and [RelayReachSentinels] fires [advance] while it is on
 * screen.
 *
 * @param key stable identity (protocol tag + relay url) so the sentinel survives list reorders.
 */
data class RelayReachCursor(
    val key: String,
    val name: String,
    val reachedUntil: Long,
    val state: RelayReachState,
    // Short protocol tag (e.g. "NIP-17" / "NIP-04") shown in the tap-through detail popup, so a marker
    // that mixes protocols in one gap isn't ambiguous. Empty when the feed has a single (implicit) kind.
    val protocol: String = "",
    val advance: () -> Unit,
)

/**
 * Drives demand-driven paging for every limit, **hoisted above the list** so its identity does not ride
 * on which row currently hosts the marker. Each non-done limit gets one stable effect (keyed by
 * [RelayReachCursor.key]) that watches the [listState] and pulls that relay's next page when its marker
 * is on screen.
 *
 * Why hoisted: the marker for a limit lives in exactly one gap (between the two rows straddling its
 * reached cursor). Placing the sentinel *inside* that row made its effect's identity ride the hosting
 * row — so any feed reorder (a live message, or a slow relay dribbling a history page) moved the gap to a
 * different row, tore the effect down and recreated it, and re-fired `advance()` on a static screen.
 * That re-armed stalled/auth relays into a silence-watchdog storm and could walk a delivering relay
 * back a window with no scroll. Hoisting the effect and driving it off **viewport visibility** instead
 * of composition presence removes that coupling.
 *
 * Fires `advance()` when (and only when) the marker's gap is among the currently visible rows AND either
 * it just scrolled into view OR its reached cursor moved (a page landed — keep paging while visible).
 * A reorder that keeps the marker on the same side of the fold changes neither, so it no longer re-fires.
 * A done relay drives nothing.
 *
 * @param createdAtAt createdAt of the list item at an index (null past the ends / for non-message rows),
 *   so the visible-gap test mirrors [RelayReachMarkers]'s placement against only the on-screen rows.
 */
@Composable
fun RelayReachSentinels(
    limits: List<RelayReachCursor>,
    listState: LazyListState,
    createdAtAt: (index: Int) -> Long?,
) {
    limits.forEach { lim ->
        if (lim.state == RelayReachState.DONE) return@forEach
        key(lim.key) {
            val reached = rememberUpdatedState(lim.reachedUntil)
            val advance = rememberUpdatedState(lim.advance)
            val getAt = rememberUpdatedState(createdAtAt)
            LaunchedEffect(Unit) {
                snapshotFlow {
                    val r = reached.value
                    val at = getAt.value
                    // Visible if any on-screen row is the "newer" side of the gap holding this cursor —
                    // the same predicate RelayReachMarkers uses to place the marker, but over the
                    // visible rows only.
                    val onScreen =
                        listState.layoutInfo.visibleItemsInfo.any { info ->
                            reachedFallsInGap(r, at(info.index), at(info.index + 1))
                        }
                    // Pair so distinctUntilChanged also lets a landed page (r moved) re-fire while visible,
                    // not just the off→on-screen transition.
                    onScreen to r
                }.distinctUntilChanged()
                    .collect { (onScreen, r) ->
                        if (onScreen) {
                            // One line per sentinel fire — a re-fire LOOP would show the same key firing
                            // over and over (and whether its reached cursor is drifting).
                            Log.d("DMPagination") { "marker fire ${lim.key} reachedUntil=$r" }
                            advance.value()
                        }
                    }
            }
        }
    }
}

/**
 * Reports whether any not-done [limits] marker is on screen, for a caller that decides **itself** when to
 * page (e.g. a conversation that pages the account-wide NIP-17 history one round at a time) instead of
 * letting [RelayReachSentinels] pull a page every time a marker shows up. Uses the same gap predicate as
 * [RelayReachMarkers], over the visible rows only. A frontier newer than every message (nothing on screen is
 * newer than it, so it has no gap to sit in) counts as visible while the newest row is.
 *
 * Hoisted above the list like [RelayReachSentinels]. [onVisibleChange] fires on each change, starting with
 * the first layout.
 */
@Composable
fun RelayReachVisibility(
    limits: List<RelayReachCursor>,
    listState: LazyListState,
    createdAtAt: (index: Int) -> Long?,
    onVisibleChange: (Boolean) -> Unit,
) {
    val current = rememberUpdatedState(limits)
    val getAt = rememberUpdatedState(createdAtAt)
    val report = rememberUpdatedState(onVisibleChange)
    LaunchedEffect(listState) {
        snapshotFlow {
            val frontiers = current.value.filter { it.state != RelayReachState.DONE }
            val at = getAt.value
            frontiers.isNotEmpty() &&
                listState.layoutInfo.visibleItemsInfo.any { info ->
                    val newer = at(info.index)
                    frontiers.any { lim ->
                        reachedFallsInGap(lim.reachedUntil, newer, at(info.index + 1)) ||
                            (info.index == 0 && newer != null && newer <= lim.reachedUntil)
                    }
                }
        }.distinctUntilChanged()
            .collect { report.value(it) }
    }
}

/**
 * Renders the window-limit markers for the relays whose limit falls in the gap between a newer message
 * (at [newerCreatedAt]) and its next-older neighbour (at [olderCreatedAt], null at the oldest end). Pure
 * UI: the load driving lives in [RelayReachSentinels], so this can be (re)placed freely per row on
 * every feed reorder without triggering any paging.
 *
 * A [DONE][RelayReachState.DONE] relay has no incompleteness frontier — it has loaded everything it has —
 * so it does NOT mark its own history bottom mid-stream (which would read like a false "incomplete below
 * here" line). Instead every done relay sinks to the **oldest-end gap** ([olderCreatedAt] null), where it
 * renders as one "fully loaded" marker. Only [REACHING][RelayReachState.REACHING] /
 * [STALLED][RelayReachState.STALLED] relays — the genuine "below here may still be incomplete" frontiers —
 * are placed at their reached cursor.
 */
@Composable
fun RelayReachMarkers(
    limits: List<RelayReachCursor>,
    newerCreatedAt: Long?,
    olderCreatedAt: Long?,
    // Tapped with the relays in this gap, so the caller can open a detail popup (which relays, how far
    // back, per protocol). Null leaves the marker a passive, non-interactive divider.
    onShowDetail: ((List<RelayReachCursor>) -> Unit)? = null,
) {
    val here =
        remember(limits, newerCreatedAt, olderCreatedAt) {
            limits.filter {
                if (it.state == RelayReachState.DONE) {
                    // Fully loaded → sink to the oldest end rather than mark a frontier it doesn't have.
                    newerCreatedAt != null && olderCreatedAt == null
                } else {
                    reachedFallsInGap(it.reachedUntil, newerCreatedAt, olderCreatedAt)
                }
            }
        }
    if (here.isEmpty()) return

    RelayReachMarker(here.map { RelayReach(it.name, it.state) }, onClick = onShowDetail?.let { cb -> { cb(here) } })
}

/**
 * A thin divider drawn between two messages marking the point one or more relays have paged down to.
 * As a relay loads older history its reached cursor drops, so the caller places this marker further
 * down (older) in the stream — relays that race ahead leave their marker deep while slower relays'
 * markers trail higher up, converging as they catch up.
 *
 * The line is always captioned so it's never a bare glyph cluster: the live frontiers
 * ([REACHING][RelayReachState.REACHING] / [STALLED][RelayReachState.STALLED]) read "Loading:"; the
 * oldest-end pile of [DONE][RelayReachState.DONE] relays reads "Fully loaded:". Each state then renders
 * one compact label: the host name(s) when one — or two short-named — relays sit at that state (the usual
 * converged case, where each relay rests at its own depth), or just a count when several pile up at the
 * same depth (e.g. all nine clustered at the oldest-end floor) so the line can't grow into an unreadable
 * comma list. In the rare mixed line (an active frontier sharing the oldest-end gap with done relays) the
 * caption is "Loading:", so the done chip is suffixed "(fully loaded)" to keep its meaning clear. Either
 * way the whole marker is tappable for the full per-relay breakdown. Reads e.g. "Loading: ↓ nostr.wine",
 * "Loading: ↓ 8 relays" or "Fully loaded: ✓ 8 relays".
 */
@Composable
private fun RelayReachMarker(
    entries: List<RelayReach>,
    onClick: (() -> Unit)? = null,
) {
    if (entries.isEmpty()) return

    val hasActiveFrontier = entries.any { it.state != RelayReachState.DONE }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(5.dp).then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), thickness = DividerThickness)
        // Always caption the line so it's never a bare glyph cluster: live frontiers are "Loading:"; the
        // oldest-end pile of only-done relays is "Fully loaded:".
        Text(
            text =
                stringResource(
                    if (hasActiveFrontier) Res.string.chats_history_loading_label else Res.string.chats_history_fully_loaded_label,
                ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
        // Present states in enum order. Written with an explicit `sortedBy` + `entry.key`/`entry.value`
        // (not `toSortedMap(compareBy { it.ordinal })` + a destructured `(state, list)`) because
        // Kotlin/Native's Compose compiler can't infer those inside this inline @Composable lambda
        // (commons iOS).
        entries
            .groupBy { it.state }
            .entries
            .sortedBy { it.key.ordinal }
            .forEachIndexed { index, entry ->
                val state = entry.key
                val list = entry.value
                if (index > 0) {
                    Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
                // Spell out 1–2 short host names; otherwise "N relays" for every state, so a count chip
                // always reads as a sentence ("↓ 8 relays", "✓ 8 relays") rather than a bare number. Only a
                // mixed line (caption "Loading:") needs the done chip tagged "(fully loaded)" — a pure-done
                // line already says so in its "Fully loaded:" caption.
                val names = list.map { it.name }
                val label = reachInlineNames(names) ?: pluralStringResource(Res.plurals.chats_history_relays, names.size, names.size)
                val chip = reachGlyph(state) + " " + label
                Text(
                    text = if (state == RelayReachState.DONE && hasActiveFrontier) chip + " " + stringResource(Res.string.chats_history_fully_loaded) else chip,
                    color = reachColor(state),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        HorizontalDivider(modifier = Modifier.weight(1f), thickness = DividerThickness)
    }
}

// Host names short enough to spell out inline on the single-line divider instead of collapsing to a
// bare count: a name up to [INLINE_NAME_MAX] when it's the lone relay of its state, or two names each
// up to [INLINE_TWO_NAMES_MAX] when a pair shares it. Longer hosts, or 3+ relays at one state, return
// null so the caller renders a count instead and the line can't grow unbounded — the tap-through dialog
// always lists them all.
private const val INLINE_NAME_MAX = 16
private const val INLINE_TWO_NAMES_MAX = 12

internal fun reachInlineNames(names: List<String>): String? =
    when {
        names.size == 1 && names[0].length <= INLINE_NAME_MAX -> names[0]
        names.size == 2 && names.all { it.length <= INLINE_TWO_NAMES_MAX } -> names.joinToString(", ")
        else -> null
    }

internal fun reachGlyph(state: RelayReachState) =
    when (state) {
        RelayReachState.REACHING -> "↓"
        RelayReachState.STALLED -> "…"
        RelayReachState.DONE -> "✓"
    }

@Composable
internal fun reachColor(state: RelayReachState): Color =
    when (state) {
        RelayReachState.REACHING -> MaterialTheme.colorScheme.onSurfaceVariant
        RelayReachState.STALLED -> MaterialTheme.colorScheme.error
        RelayReachState.DONE -> MaterialTheme.colorScheme.primary
    }
