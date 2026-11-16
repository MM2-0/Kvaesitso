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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nests.room.lifecycle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.viewmodels.NestViewModel
import com.vitorpamplona.quartz.experimental.nests.admin.AdminCommandEvent
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.presence.MeetingRoomPresenceEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.zap.Bolt12ZapEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Single entry point for every read-side `LocalCache` → [NestViewModel]
 * fan-out the room screen needs. Wraps four parallel collectors
 * (presence, chat, reactions, admin commands), the two eviction
 * tickers (presence + reactions), and the kick-authority check
 * into one call so the screen body doesn't have to thread the
 * VM through six LaunchedEffects.
 *
 * The wire subscription that populates LocalCache is
 * [com.vitorpamplona.amethyst.commons.nests.datasource.NestRoomFilterAssemblerSubscription];
 * this composable is purely the read side — observing what's
 * already in cache, not opening relay REQs of its own.
 */
@Composable
fun NestRoomEventCollectors(
    viewModel: NestViewModel,
    event: MeetingSpaceEvent,
    roomATag: String,
    localPubkey: String,
) {
    PresenceCollector(viewModel, roomATag)
    PresenceEvictionTicker(viewModel)
    ChatCollector(viewModel, roomATag)
    ReactionsCollector(viewModel, roomATag)
    ReactionsEvictionTicker(viewModel)
    ZapsCollector(viewModel, roomATag)
    ZapsEvictionTicker(viewModel)
    AdminCommandsCollector(viewModel, event, roomATag, localPubkey)
}

/**
 * Pump kind-10312 presence events from LocalCache into the VM's
 * aggregator. Drives the listener counter, participant grid, and
 * hand-raise queue.
 */
@Composable
private fun PresenceCollector(
    viewModel: NestViewModel,
    roomATag: String,
) {
    LaunchedEffect(viewModel, roomATag) {
        val filter =
            Filter(
                kinds = listOf(MeetingRoomPresenceEvent.KIND),
                tags = mapOf("a" to listOf(roomATag)),
            )
        LocalCache.observeEvents<MeetingRoomPresenceEvent>(filter).collect { events ->
            events.forEach { viewModel.onPresenceEvent(it) }
        }
    }
}

/**
 * Drop peers silent for >6 min (one missed 30-s heartbeat plus a
 * 5-min "still here" tolerance window so a transient relay hiccup
 * doesn't drop everyone). Self-cancels with the composable.
 */
@Composable
private fun PresenceEvictionTicker(viewModel: NestViewModel) {
    LaunchedEffect(viewModel) {
        while (isActive) {
            delay(PRESENCE_EVICT_INTERVAL_MS)
            viewModel.evictStalePresences(TimeUtils.now() - PRESENCE_STALE_THRESHOLD_SEC)
        }
    }
}

/** Pump kind-1311 chat messages into the VM's chat ledger. */
@Composable
private fun ChatCollector(
    viewModel: NestViewModel,
    roomATag: String,
) {
    LaunchedEffect(viewModel, roomATag) {
        val filter =
            Filter(
                kinds = listOf(LiveActivitiesChatMessageEvent.KIND),
                tags = mapOf("a" to listOf(roomATag)),
            )
        LocalCache.observeNotes(filter).collect { notes ->
            notes.forEach { viewModel.onChatEvent(it) }
        }
    }
}

/**
 * Pump kind-7 reactions into the VM's sliding-window aggregator.
 * The aggregator drops entries older than 30 s; the 1-s tick in
 * [ReactionsEvictionTicker] drives that eviction so the
 * floating-up overlay animation timing lives in one place rather
 * than per-component.
 */
@Composable
private fun ReactionsCollector(
    viewModel: NestViewModel,
    roomATag: String,
) {
    LaunchedEffect(viewModel, roomATag) {
        val filter =
            Filter(
                kinds = listOf(ReactionEvent.KIND),
                tags = mapOf("a" to listOf(roomATag)),
            )
        LocalCache.observeEvents<ReactionEvent>(filter).collect { events ->
            val nowSec = TimeUtils.now()
            events.forEach { viewModel.onReactionEvent(it, nowSec) }
        }
    }
}

/**
 * Pump kind-9735 zap receipts into the VM's chat ledger AND the
 * floating zap-overlay aggregator. The same note flows two places:
 *
 *   1. [NestViewModel.onChatEvent] — `ChatroomMessageCompose` routes
 *      `ZapReceiptEvent` notes through `RenderChatZap`, which is the same
 *      card live streams use. Sharing the chat ledger keeps zap
 *      cards interleaved with kind-1311 chat messages in time order.
 *
 *   2. [NestViewModel.onZapEvent] — same sliding-window pattern as
 *      reactions; drives the floating "⚡ Nsats" chip over the
 *      targeted participant's avatar (or the room itself).
 */
@Composable
private fun ZapsCollector(
    viewModel: NestViewModel,
    roomATag: String,
) {
    LaunchedEffect(viewModel, roomATag) {
        val filter =
            Filter(
                kinds = listOf(ZapReceiptEvent.KIND, Bolt12ZapEvent.KIND),
                tags = mapOf("a" to listOf(roomATag)),
            )
        LocalCache.observeNotes(filter).collect { notes ->
            val nowSec = TimeUtils.now()
            notes.forEach { note ->
                viewModel.onChatEvent(note)
                (note.event as? ZapReceiptEvent)?.let { viewModel.onZapEvent(it, nowSec) }
                (note.event as? Bolt12ZapEvent)?.let { viewModel.onZapEvent(it, nowSec) }
            }
        }
    }
}

@Composable
private fun ZapsEvictionTicker(viewModel: NestViewModel) {
    // Mirrors [ReactionsEvictionTicker] — only tick while there are
    // zaps to evict. The aggregator's last eviction empties
    // [NestViewModel.recentZaps], which flips [hasZaps] to false and
    // cancels the loop until the next zap arrives.
    val zaps by viewModel.recentZaps.collectAsState()
    val hasZaps = zaps.values.any { it.isNotEmpty() }
    LaunchedEffect(viewModel, hasZaps) {
        if (!hasZaps) return@LaunchedEffect
        while (isActive) {
            delay(REACTIONS_TICK_MS)
            viewModel.evictZaps(TimeUtils.now() - REACTION_WINDOW_SEC_LOCAL)
        }
    }
}

@Composable
private fun ReactionsEvictionTicker(viewModel: NestViewModel) {
    // Only tick while there are reactions to evict. The aggregator's
    // last eviction empties [NestViewModel.recentReactions], which
    // flips [hasReactions] to false and cancels the loop until the
    // next reaction arrives. A perpetually-quiet room costs no
    // scheduled work.
    val reactions by viewModel.recentReactions.collectAsState()
    val hasReactions = reactions.values.any { it.isNotEmpty() }
    LaunchedEffect(viewModel, hasReactions) {
        if (!hasReactions) return@LaunchedEffect
        while (isActive) {
            delay(REACTIONS_TICK_MS)
            viewModel.evictReactions(TimeUtils.now() - REACTION_WINDOW_SEC_LOCAL)
        }
    }
}

/**
 * Pump kind-4312 admin commands targeting THIS user. The
 * signer-must-be-host-or-moderator authority check happens
 * here (not in the relay) — only honour kicks where the signer's
 * `ParticipantTag.canSpeak()` returns true on the active
 * kind-30312, OR the signer IS the room author. nostrnests' UI
 * does the same gating.
 */
@Composable
private fun AdminCommandsCollector(
    viewModel: NestViewModel,
    event: MeetingSpaceEvent,
    roomATag: String,
    localPubkey: String,
) {
    LaunchedEffect(viewModel, roomATag, localPubkey) {
        // EGG-07 / nostrnests gate: ignore admin commands older than
        // 60 s. The relay filter narrows the firehose; the per-cmd
        // check below catches anything that slips through (cached
        // events, system clock skew, etc.).
        val sinceSec = TimeUtils.now() - ADMIN_COMMAND_FRESHNESS_SEC
        val filter =
            Filter(
                kinds = listOf(AdminCommandEvent.KIND),
                tags = mapOf("a" to listOf(roomATag), "p" to listOf(localPubkey)),
                since = sinceSec,
            )
        // Replay protection per EGG-07 #7: a single kick / mute must
        // act exactly once even when re-delivered from multiple
        // relays. Lifetime-of-collector dedup mirrors how nostrnests'
        // useAdminCommands.ts uses a `processedRef` set.
        val processed = mutableSetOf<String>()
        LocalCache.observeNewEvents<AdminCommandEvent>(filter).collect { cmd ->
            if (cmd.targetPubkey() != localPubkey) return@collect
            // Defensive freshness re-check: relay might have served a
            // cached older event despite the `since` hint, or the
            // user's clock might have jumped forward.
            if (TimeUtils.now() - cmd.createdAt > ADMIN_COMMAND_FRESHNESS_SEC) return@collect
            if (!processed.add(cmd.id)) return@collect
            val signerIsAuthorised =
                cmd.pubKey == event.pubKey ||
                    event.participants().any { it.pubKey == cmd.pubKey && (it.isHost() || it.isModerator()) }
            if (!signerIsAuthorised) return@collect
            when (cmd.action()) {
                AdminCommandEvent.Action.KICK -> viewModel.onKick()
                AdminCommandEvent.Action.MUTE -> viewModel.onForceMuted()
                null -> Unit // unknown verb, ignore
            }
        }
    }
}

/** Spec window from EGG-07 #7 — 60-second freshness gate on kind-4312. */
private const val ADMIN_COMMAND_FRESHNESS_SEC: Long = 60L

private const val PRESENCE_EVICT_INTERVAL_MS = 60_000L
private const val PRESENCE_STALE_THRESHOLD_SEC = 6L * 60L
private const val REACTIONS_TICK_MS = 1_000L

// Mirror of [com.vitorpamplona.amethyst.commons.viewmodels.REACTION_WINDOW_SEC]
// — the platform layer doesn't import from commons here, and a
// duplicate constant is cheaper than another import. If these
// drift, the 1-s tick still self-heals within one window length.
private const val REACTION_WINDOW_SEC_LOCAL = 10L
