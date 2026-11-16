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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.OnchainZapEntry
import com.vitorpamplona.amethyst.commons.model.OnchainZapStatus
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteZaps
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.CrossfadeToDisplayAmount
import com.vitorpamplona.amethyst.commons.ui.note.OnchainZappedIcon
import com.vitorpamplona.amethyst.commons.ui.note.PendingClockBadge
import com.vitorpamplona.amethyst.commons.ui.note.WatchUserMetadataAndFollowsAndRenderUserProfilePictureOrDefaultAuthor
import com.vitorpamplona.amethyst.commons.ui.theme.Size25dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size35Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.StdStartPadding
import com.vitorpamplona.amethyst.commons.util.showAmount
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.utils.BigDecimal
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

private fun onOnchainZapEntryClick(
    entry: OnchainZapEntry,
    nav: INav,
) {
    entry.source.author?.let { nav.nav(routeFor(it)) }
}

@Composable
internal fun WatchOnchainZapsAndRenderGallery(
    baseNote: Note,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    // Reuse the same flow the lightning gallery subscribes to. Note.addOnchainZap
    // invalidates flowSet.zaps, so this composable refreshes when on-chain zaps
    // arrive or upgrade pending → confirmed. The flow ALSO fires for lightning
    // zap arrivals on the same note — memoize on the onchainZaps map reference
    // (a fresh immutable map per onchain mutation, stable across lightning-only
    // updates) so a busy lightning thread doesn't churn this gallery's state.
    val zapsState by observeNoteZaps(baseNote, accountViewModel)
    val onchainZapsMap = zapsState?.note?.onchainZaps
    val entries =
        remember(onchainZapsMap) {
            onchainZapsMap?.values?.toImmutableList() ?: persistentListOf()
        }

    if (entries.isNotEmpty()) {
        // Drive re-verification of any non-CONFIRMED entries while this gallery
        // is on screen — covers home feed, profile, notifications, channels,
        // single-note view, threads. Per-note in-flight gating inside the cache
        // dedupes the work when multiple gallery instances for the same note
        // (lazy-list off/on screen flicker, split feed) all fire together.
        DriveOnchainZapReverification(baseNote, entries)
        RenderOnchainZapGallery(entries, nav, accountViewModel)
    }
}

/**
 * Drives on-chain zap re-verification for [note] while the gallery is composed.
 *
 * Three triggers fire reverify:
 * 1. First view of this note (independent of tip availability — covers the cold-
 *    start case where the chain backend isn't wired yet, or `tipHeight()` is slow).
 * 2. A new pending entry arrives (`entries.size` changes).
 * 3. The chain tip advances (the shared StateFlow emits a new value).
 *
 * The cache's per-note + per-event gates dedupe concurrent calls; this composable
 * doesn't need its own throttling.
 */
@Composable
private fun DriveOnchainZapReverification(
    note: Note,
    entries: ImmutableList<OnchainZapEntry>,
) {
    val pendingCount = remember(entries) { entries.count { it.status != OnchainZapStatus.CONFIRMED } }
    if (pendingCount == 0) return

    val resolver = LocalCache.onchainZapResolver

    // First-view kick — unconditional, doesn't wait for the tip flow. Keyed on
    // (idHex, pendingCount) so a brand-new pending entry arriving while the
    // gallery is still on screen also kicks an immediate reverify instead of
    // waiting up to a full tip-poll interval.
    LaunchedEffect(note.idHex, pendingCount) {
        resolver.reverifyOnchainZapsForNote(note)
    }

    // Tip-change kick — subscribes to the shared poller (lazy, WhileSubscribed
    // so only one HTTP poller runs across the whole UI no matter how many
    // galleries are visible). Skips the first emission (null) to avoid
    // duplicating the first-view kick above.
    val tip by resolver.onchainTipHeightFlow.collectAsStateWithLifecycle()
    LaunchedEffect(note.idHex, tip) {
        if (tip != null) {
            resolver.reverifyOnchainZapsForNote(note)
        }
    }
}

@Composable
private fun RenderOnchainZapGallery(
    entries: ImmutableList<OnchainZapEntry>,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    Row(Modifier.fillMaxWidth()) {
        GalleryIconColumn { iconAlignment ->
            OnchainZappedIcon(
                modifier = Modifier.size(Size25dp).align(iconAlignment),
            )
        }

        OnchainZapAuthorGallery(entries, nav, accountViewModel)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnchainZapAuthorGallery(
    entries: ImmutableList<OnchainZapEntry>,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    Column(modifier = StdStartPadding) {
        FlowRow {
            entries.forEach { entry ->
                OnchainZapEntryRow(entry, nav, accountViewModel)
            }
        }
    }
}

@Composable
private fun OnchainZapEntryRow(
    entry: OnchainZapEntry,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    val user = entry.source.author
    val isConfirmed = entry.status == OnchainZapStatus.CONFIRMED
    val avatarAlpha = if (isConfirmed) 1f else 0.6f

    // Anti-spoof: `claimedSats` comes from the kind:8333 `amount` tag, which is
    // attacker-controlled for incoming zaps. Only show the claimed amount for the
    // signed-in user's own outgoing zap (where the user knows what they sent) —
    // otherwise show the on-chain verified amount, or nothing while still unverified.
    val displaySats =
        when {
            isConfirmed || entry.status == OnchainZapStatus.PENDING -> entry.verifiedSats
            user != null && accountViewModel.isLoggedUser(user.pubkeyHex) -> entry.claimedSats
            else -> 0L
        }
    val amountText =
        remember(displaySats) {
            if (displaySats > 0L) showAmount(BigDecimal(displaySats)) else ""
        }

    Box(
        modifier = Size35Modifier.clickable { onOnchainZapEntryClick(entry, nav) },
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Only the avatar dims for unverified/pending entries. The amount overlay
        // and clock badge stay at full opacity so they remain readable.
        Box(modifier = Modifier.alpha(avatarAlpha)) {
            WatchUserMetadataAndFollowsAndRenderUserProfilePictureOrDefaultAuthor(
                user,
                accountViewModel,
                nav,
            )
        }

        if (amountText.isNotEmpty()) {
            CrossfadeToDisplayAmount(amountText)
        }

        if (!isConfirmed) {
            // TopStart so the badge doesn't collide with the FollowingIcon
            // that WatchUserMetadataAndFollowsAndRenderUserProfilePicture
            // paints at TopEnd for followed users.
            Box(modifier = Modifier.align(Alignment.TopStart)) {
                PendingClockBadge(modifier = Modifier.size(14.dp))
            }
        }
    }
}
