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
package com.vitorpamplona.amethyst.commons.ui.broadcast

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.frontpage.PrintJobEnd
import com.vitorpamplona.amethyst.commons.model.BooleanType
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.service.broadcast.BroadcastEvent
import com.vitorpamplona.amethyst.commons.service.broadcast.canAutoDismiss
import com.vitorpamplona.amethyst.commons.service.pow.PoWJobState
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalScreenLayout
import com.vitorpamplona.amethyst.commons.ui.layouts.NavigationStyle
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.frontpage.FrontPagePrintJobContent
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.frontpage.rememberBackgroundPrint
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay

/**
 * Displays broadcast progress UI components:
 * - BroadcastBanner: Shows active broadcasts with progress; hides once every post is out
 * - BroadcastDetailsSheet: Shows detailed relay status on tap
 *
 * The relay-progress part is hidden when the "Tracked broadcasts" UI setting
 * is off, but the NIP-13 mining phase always shows — the user needs to see
 * (and be able to cancel) posts still burning CPU in the queue. So does a front
 * page printing while the reader is elsewhere: its step, how long it has run,
 * then, briefly, how it ended; tapping it opens the front page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplayBroadcastProgress(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val useTrackedBroadcasts by accountViewModel.settings.uiSettingsFlow.useTrackedBroadcasts
        .collectAsStateWithLifecycle()
    val trackingEnabled = useTrackedBroadcasts == BooleanType.ALWAYS

    val powQueue = LocalAppServices.current.powPublishQueue

    val miningJobs by powQueue.jobs
        .collectAsStateWithLifecycle()
    val trackedBroadcasts by accountViewModel.broadcastTracker.activeBroadcasts.collectAsStateWithLifecycle()
    val visibleBroadcasts = remember(trackedBroadcasts) { trackedBroadcasts.filterNot { it.hidden }.toImmutableList() }
    val activeBroadcasts = if (trackingEnabled) visibleBroadcasts else persistentListOf()

    val print = rememberBackgroundPrint(accountViewModel)

    // State for details sheet
    var seeDetails by remember { mutableStateOf(false) }

    if (activeBroadcasts.isEmpty() && miningJobs.isEmpty() && print == null && !seeDetails) return

    if (!seeDetails) {
        DisplaySnack(
            activeBroadcasts,
            miningJobs,
            { if (activeBroadcasts.isNotEmpty()) seeDetails = true },
            accountViewModel,
            backgroundWork =
                print?.let { (printer, job) ->
                    {
                        FrontPagePrintJobContent(
                            job,
                            onOpen = {
                                if (!job.running) printer.dismissJob()
                                nav.nav(Route.FrontPage((job.end as? PrintJobEnd.Ready)?.day))
                            },
                            onHide = printer::hideJob,
                            onDismiss = printer::dismissJob,
                            log = printer.log,
                        )
                    }
                },
        )

        // Keyed on the decision, not the list: a slow relay answering during
        // the grace period must not push the dismissal back. Until every post
        // is out — still sending, or an outbox relay failed — the banner stays.
        // After the delay, hide what is out by then: the list may have changed
        // without the decision flipping, if a new post went out within a frame.
        val canAutoDismiss = activeBroadcasts.canAutoDismiss()
        val latestBroadcasts by rememberUpdatedState(activeBroadcasts)
        LaunchedEffect(canAutoDismiss) {
            if (canAutoDismiss) {
                delay(1_500)
                accountViewModel.broadcastTracker.hide(latestBroadcasts.filter { it.isOut }.ids())
            }
        }
    } else {
        MultiBroadcastDetailsSheet(
            broadcasts = activeBroadcasts,
            onDismiss = {
                accountViewModel.runOnIO {
                    accountViewModel.broadcastTracker.hide(activeBroadcasts.ids())
                }
                seeDetails = false
            },
            onRetryRelay = { b: BroadcastEvent, relay: NormalizedRelayUrl ->
                accountViewModel.runOnIO {
                    accountViewModel.broadcastTracker.retry(
                        broadcast = b,
                        client = accountViewModel.account.client,
                        specificRelay = relay,
                    )
                }
            },
            onRetryAllFailed = { b: BroadcastEvent ->
                accountViewModel.runOnIO {
                    accountViewModel.broadcastTracker.retry(
                        broadcast = b,
                        client = accountViewModel.account.client,
                    )
                }
            },
        )
    }
}

@Composable
fun DisplaySnack(
    activeBroadcasts: ImmutableList<BroadcastEvent>,
    miningJobs: ImmutableList<PoWJobState>,
    onTap: () -> Unit,
    accountViewModel: AccountViewModel,
    backgroundWork: (@Composable () -> Unit)? = null,
) {
    val powQueue = LocalAppServices.current.powPublishQueue
    // Above the screen's FAB (and the bottom bar under it, on a phone), never over it.
    val clearance = if (LocalScreenLayout.current.navigationStyle == NavigationStyle.BOTTOM_BAR) 152.dp else 128.dp
    Box(modifier = Modifier.fillMaxSize()) {
        BroadcastBanner(
            broadcasts = activeBroadcasts,
            miningJobs = miningJobs,
            onCancelJob = { powQueue.cancel(it) },
            onSendWithoutPow = { powQueue.sendWithoutPow(it) },
            onTap = onTap,
            onRetryAll = {
                activeBroadcasts.forEach { b ->
                    accountViewModel.runOnIO {
                        accountViewModel.broadcastTracker.retry(
                            broadcast = b,
                            client = accountViewModel.account.client,
                        )
                    }
                }
            },
            onDismiss = {
                accountViewModel.broadcastTracker.hide(activeBroadcasts.ids())
            },
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = clearance)
                    // Capped first, then a share of what is left: the other order never caps.
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(0.94f),
            backgroundWork = backgroundWork,
        )
    }
}

private fun List<BroadcastEvent>.ids() = mapTo(HashSet()) { it.id }
