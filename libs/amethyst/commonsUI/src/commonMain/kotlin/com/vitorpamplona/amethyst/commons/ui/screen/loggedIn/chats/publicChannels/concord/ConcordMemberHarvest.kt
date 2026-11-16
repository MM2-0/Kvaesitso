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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.taggedAs
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPagesFromPool
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * How far back the member-roster harvest pages each channel. Concord membership includes every observed
 * author (CORD-02 §5), but they only appear by decrypting their messages — so a complete roster needs
 * history, not just the live tail. This bounds the history pulled onto the device: members whose last
 * post predates the window aren't counted. Tune here to trade completeness for data.
 */
private const val CONCORD_MEMBER_HARVEST_WINDOW_SECS = 90L * 24 * 60 * 60

/**
 * A headless, run-once background sweep that fills in a Concord community's **full** member roster.
 *
 * The live channel subscriptions only carry the recent tail the relay serves, so [observedAuthors]
 * (CORD-02 §5) sees only recent posters — a fraction of the real membership. This pages every folded
 * channel's history back to [CONCORD_MEMBER_HARVEST_WINDOW_SECS] in one pooled fetch. The wraps ride
 * the app's normal ingest (the global `CacheClientConnector` → `concordSessions.ingest`), which decrypts
 * each and folds its author into `observedAuthors` — so the members screen's count fills in as the sweep
 * runs, with no extra plumbing here. `session.beginMemberHarvest()` gates it to once per community.
 *
 * Mount it from the members screen; it self-cancels with the composition. AUTH is free — the channel
 * planes' stream keys are already registered for these relays (same path the live sub uses).
 */
@Composable
fun ConcordMemberHarvest(
    communityId: String,
    accountViewModel: AccountViewModel,
) {
    val account = accountViewModel.account
    val revision by account.concordSessions.revision.collectAsStateWithLifecycle()
    val session = remember(communityId, revision) { account.concordSessions.sessionFor(communityId) }

    androidx.compose.runtime.LaunchedEffect(session, revision) {
        val s = session ?: return@LaunchedEffect
        // Each folded channel's derived plane pubkey is a REQ author. Empty until the Control Plane folds
        // its channels — a later revision re-runs this effect, so we harvest as soon as they appear.
        val planePks = s.channelAddresses().toList()
        if (planePks.isEmpty()) return@LaunchedEffect
        if (!s.beginMemberHarvest()) return@LaunchedEffect

        val relays =
            account.concordChannelList.liveCommunities.value
                .firstOrNull { it.id == communityId }
                ?.relays
                ?.mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it) }
                .orEmpty()
        if (relays.isEmpty()) return@LaunchedEffect

        val filter =
            Filter(
                kinds = listOf(ConcordStreamEnvelope.KIND_WRAP),
                authors = planePks,
                since = TimeUtils.now() - CONCORD_MEMBER_HARVEST_WINDOW_SECS,
            )
        withContext(Dispatchers.IO) {
            runCatchingCancellable {
                // Events land via the global ingest path, so onEvent is a no-op — we only drive the paging.
                account.client.taggedAs(SubPurpose.COMMUNITY_CHATS, "Members").fetchAllPagesFromPool(relays.associateWith { listOf(filter) }) { _, _ -> }
            }
        }
    }
}
