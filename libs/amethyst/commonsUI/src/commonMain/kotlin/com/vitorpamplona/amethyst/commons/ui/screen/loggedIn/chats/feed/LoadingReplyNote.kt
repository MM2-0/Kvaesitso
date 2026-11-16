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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.chats.formatHistoryReachDate
import com.vitorpamplona.amethyst.commons.relayClient.paging.PagingStatus
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.chats_reply_not_found
import com.vitorpamplona.amethyst.commons.resources.chats_reply_searched
import com.vitorpamplona.amethyst.commons.resources.chats_reply_searching_history
import com.vitorpamplona.amethyst.commons.ui.feeds.DmHistoryRelayDialog
import com.vitorpamplona.amethyst.commons.ui.feeds.historySubtitle
import com.vitorpamplona.amethyst.commons.ui.feeds.incompleteSubtitle
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** Which DM history pager backs the conversation an unloaded reply belongs to. */
enum class DmReplyProtocol {
    // NIP-17 gift wraps. The rumor id of the reply target is NOT queryable on relays (only the outer
    // 1059 wrap id is), so the only way to surface it is to keep paging gift-wrap history until the wrap
    // carrying it is decrypted — hence we drive the account-wide gift-wrap history pager.
    NIP17,

    // NIP-04 legacy DMs (kind 4). Paged per relay for the open conversation.
    NIP04,
}

/**
 * The inner-quote placeholder for a reply whose target message has not been paged in yet — used in
 * place of the generic [com.vitorpamplona.amethyst.commons.ui.note.BlankNote] ("post not found") that the main
 * feeds show. A reply target inside a conversation isn't *missing*, it's simply older than the window
 * loaded so far; for gift wraps it can't even be fetched by id. So instead of declaring it lost, this
 * card actively walks the conversation's history backward — kicking the protocol's `loadMore` each time
 * the previous page settles — until either the target decrypts (the surrounding
 * [com.vitorpamplona.amethyst.commons.ui.note.WatchNoteEvent] crossfades the real message in and disposes this)
 * or that protocol's history runs dry, at which point it settles into the terminal "not found" text.
 *
 * It runs regardless of scroll position (no oldest-end gate like the scroll-driven loader) precisely so
 * that opening a thread and seeing a reply to something off-screen pulls that something in on its own.
 * The drive loop is idempotent and gated on the pager's own `loadingMore`/`exhausted`, so several
 * unloaded replies on screen — and the scroll loader — all coalesce onto the same paging window.
 */
@Composable
fun LoadingReplyNote(
    protocol: DmReplyProtocol,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
) {
    val giftWrapsHistory = remember(accountViewModel) { accountViewModel.dataSources().account.giftWrapsHistory }
    val nip04History = remember(accountViewModel) { accountViewModel.dataSources().chatroom.nip04History }

    val loadingFlow: StateFlow<Boolean> =
        when (protocol) {
            DmReplyProtocol.NIP17 -> giftWrapsHistory.loadingMore
            DmReplyProtocol.NIP04 -> nip04History.loadingMore
        }
    val statusFlow: StateFlow<PagingStatus> =
        when (protocol) {
            DmReplyProtocol.NIP17 -> giftWrapsHistory.status
            DmReplyProtocol.NIP04 -> nip04History.status
        }
    val protocolTag =
        when (protocol) {
            DmReplyProtocol.NIP17 -> "NIP-17"
            DmReplyProtocol.NIP04 -> "NIP-04"
        }

    // One snapshot collector instead of five; the fields below are plain reads off it (downstream unchanged).
    val status by statusFlow.collectAsStateWithLifecycle()
    val exhausted = status.exhausted
    val relayCount = status.relayCount
    val stalledCount = status.stalledCount
    val reachedBack = status.reachedBack
    val relayProgress = status.relayProgress

    LaunchedEffect(protocol, loadingFlow, statusFlow) {
        // Step the next, older page whenever the previous one has settled and history isn't exhausted.
        // The target may surface mid-page (this composable then leaves composition and cancels us); if
        // not, we keep walking until the protocol bottoms out and the filter stops passing.
        combine(loadingFlow, statusFlow) { loading, s -> !loading && !s.exhausted }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                Log.d("DMPagination") { "reply blank: widen → $protocol advanceAll (searching for unloaded reply)" }
                when (protocol) {
                    DmReplyProtocol.NIP17 -> giftWrapsHistory.advanceAll()
                    DmReplyProtocol.NIP04 -> nip04History.advanceAll()
                }
            }
    }

    // Tapping opens the same per-relay popup the history card uses, so when the search gives up the user
    // can see exactly which relays were reached and which stalled. Empty progress keeps it non-interactive.
    var showRelays by remember { mutableStateOf(false) }
    if (showRelays) {
        DmHistoryRelayDialog(protocolTag, relayProgress, ::formatHistoryReachDate) { showRelays = false }
    }

    // Same chrome as DmHistoryLoadingCard (the older-history status card at the oldest end) so an
    // unloaded reply reads as the same kind of "reaching back into history" state, just inline in the
    // quote: rounded translucent surface, a spinner-in-a-box, then the status line.
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .then(if (relayProgress.isNotEmpty()) Modifier.clickable { showRelays = true } else Modifier),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        tonalElevation = 2.dp,
    ) {
        // When the walk gives up it splits the same way the history card does: some relays stalled
        // (couldn't reach them — the message may still be out there) vs every relay genuinely bottomed
        // out (it really isn't in your history). Either way we say what happened instead of a bare glyph.
        val stalledOut = exhausted && stalledCount > 0
        Crossfade(targetState = exhausted, animationSpec = tween(500), label = "loadingReplyState") { isExhausted ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                    when {
                        stalledOut ->
                            // Stalled-out: same red "…" the per-relay dialog and history card use for unreachable.
                            Text(
                                "…",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                            )
                        isExhausted ->
                            // Genuinely searched everything and it isn't there.
                            Text(
                                "✕",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        else -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text =
                            if (isExhausted) {
                                stringRes(Res.string.chats_reply_not_found)
                            } else {
                                stringRes(Res.string.chats_reply_searching_history)
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // While loading: which protocol, how many relays, how far back. When it gives up: either
                    // "N relays unreachable · tap to see which" (stalled) or "searched every relay · tap to see".
                    Text(
                        text =
                            when {
                                stalledOut -> incompleteSubtitle(stalledCount)
                                isExhausted -> stringRes(Res.string.chats_reply_searched)
                                else -> historySubtitle(protocolTag, relayCount, stalledCount, reachedBack, ::formatHistoryReachDate)
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
