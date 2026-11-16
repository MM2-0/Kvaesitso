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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.algoFeeds.FavoriteAlgoFeedsSnapshot
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteAndMap
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.dvm_home_retry
import com.vitorpamplona.amethyst.commons.resources.dvm_home_status_error
import com.vitorpamplona.amethyst.commons.resources.dvm_home_status_payment_required
import com.vitorpamplona.amethyst.commons.resources.dvm_home_status_processing
import com.vitorpamplona.amethyst.commons.resources.dvm_home_status_requesting
import com.vitorpamplona.amethyst.commons.resources.dvm_home_status_requesting_all
import com.vitorpamplona.amethyst.commons.resources.dvm_offline_banner
import com.vitorpamplona.amethyst.commons.ui.components.LoadNote
import com.vitorpamplona.amethyst.commons.ui.components.LoadingAnimation
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.dvms.DvmPaymentActions
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.dvms.rememberDvmHeartbeatFresh
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent

@Composable
fun HomeAlgoFeedStatusBanner(
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
) {
    val topFilter by accountViewModel.account.settings.defaultHomeFollowList
        .collectAsStateWithLifecycle()

    when (val filter = topFilter) {
        is TopFilter.FavoriteAlgoFeed -> SingleAlgoFeedBanner(filter, accountViewModel, nav, modifier)
        is TopFilter.AllFavoriteAlgoFeeds -> AllFavoriteAlgoFeedsBanner(accountViewModel, modifier)
        else -> Unit
    }
}

@Composable
private fun SingleAlgoFeedBanner(
    favFeed: TopFilter.FavoriteAlgoFeed,
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
) {
    val snapshot by accountViewModel.account.favoriteAlgoFeedsOrchestrator
        .observe(favFeed.address)
        .collectAsStateWithLifecycle()

    val heartbeatFresh by rememberDvmHeartbeatFresh(favFeed.address, accountViewModel)

    // The DVM is down (or its beats never reach us): the offline banner supersedes the
    // requesting/error/payment banner, and shows even when last-known content is on screen.
    if (!heartbeatFresh) {
        BannerCard(modifier) {
            BannerMessageRow(
                message = stringRes(Res.string.dvm_offline_banner),
                showSpinner = false,
            )
        }
        return
    }

    // Hide the banner when the feed is already populated.
    if (snapshot.ids.isNotEmpty() || snapshot.addresses.isNotEmpty()) return

    val feedAddressValue = favFeed.address.toValue()

    LoadNote(baseNoteHex = feedAddressValue) { feedNote ->
        val resolvedName by
            observeNoteAndMap(feedNote ?: return@LoadNote, accountViewModel) { note ->
                (note.event as? AppDefinitionEvent)
                    ?.appMetaData()
                    ?.name
                    ?.takeIf { it.isNotBlank() }
                    ?: (note as? com.vitorpamplona.amethyst.commons.model.AddressableNote)?.dTag()
                    ?: ""
            }

        BannerCard(modifier) {
            val status = snapshot.latestStatus?.status()

            when {
                snapshot.errorMessage != null -> {
                    BannerMessageRow(
                        message = stringRes(Res.string.dvm_home_status_error),
                        showSpinner = false,
                    )
                    Spacer(modifier = StdVertSpacer)
                    RetryButton { accountViewModel.refreshFavoriteAlgoFeed(favFeed.address) }
                }

                status?.code == "payment-required" -> {
                    BannerMessageRow(
                        message =
                            status.description.ifBlank {
                                stringRes(Res.string.dvm_home_status_payment_required)
                            },
                        showSpinner = false,
                    )
                    Spacer(modifier = StdVertSpacer)
                    var statusOverride by remember { mutableStateOf<String?>(null) }
                    val msg = statusOverride
                    if (msg != null) {
                        Text(text = msg, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = StdVertSpacer)
                    }
                    snapshot.latestStatus?.let {
                        DvmPaymentActions(
                            latestStatus = it,
                            accountViewModel = accountViewModel,
                            nav = nav,
                            onStatusUpdate = { statusOverride = it },
                        )
                    }
                }

                status?.code == "error" -> {
                    BannerMessageRow(
                        message =
                            status.description.ifBlank {
                                stringRes(Res.string.dvm_home_status_error)
                            },
                        showSpinner = false,
                    )
                    Spacer(modifier = StdVertSpacer)
                    RetryButton { accountViewModel.refreshFavoriteAlgoFeed(favFeed.address) }
                }

                status?.code == "processing" -> {
                    BannerMessageRow(
                        message =
                            status.description.ifBlank {
                                stringRes(Res.string.dvm_home_status_processing)
                            },
                        showSpinner = true,
                    )
                }

                else -> {
                    BannerMessageRow(
                        message = stringRes(Res.string.dvm_home_status_requesting, resolvedName),
                        showSpinner = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun AllFavoriteAlgoFeedsBanner(
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
) {
    val addresses by accountViewModel.account.favoriteAlgoFeedsList.flow
        .collectAsStateWithLifecycle()

    if (addresses.isEmpty()) return

    // Map every address to its freshness first (non-short-circuiting) so per-address
    // composable call sites stay stable instead of appearing/disappearing with freshness.
    val anyHeartbeatFresh =
        addresses
            .map { address ->
                rememberDvmHeartbeatFresh(address, accountViewModel).value
            }.any { it }
    if (!anyHeartbeatFresh) {
        BannerCard(modifier) {
            BannerMessageRow(
                message = stringRes(Res.string.dvm_offline_banner),
                showSpinner = false,
            )
        }
        return
    }

    // Observe each DVM's snapshot so we can decide whether to hide the banner
    // based on the aggregate state. Hide it as soon as any DVM has produced a
    // feed; only error out when every one of them has errored.
    val snapshots: List<FavoriteAlgoFeedsSnapshot> =
        addresses.map { address ->
            val snap by accountViewModel.account.favoriteAlgoFeedsOrchestrator
                .observe(address)
                .collectAsStateWithLifecycle()
            snap
        }

    val anyResponded = snapshots.any { it.ids.isNotEmpty() || it.addresses.isNotEmpty() }
    if (anyResponded) return

    val allErrored = snapshots.all { it.errorMessage != null || it.latestStatus?.status()?.code == "error" }

    BannerCard(modifier) {
        if (allErrored) {
            BannerMessageRow(
                message = stringRes(Res.string.dvm_home_status_error),
                showSpinner = false,
            )
            Spacer(modifier = StdVertSpacer)
            RetryButton {
                addresses.forEach { accountViewModel.refreshFavoriteAlgoFeed(it) }
            }
        } else {
            BannerMessageRow(
                message = stringRes(Res.string.dvm_home_status_requesting_all),
                showSpinner = true,
            )
        }
    }
}

@Composable
private fun BannerCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Sits above the feed instead of in the topBar Column, so adding/removing
    // the banner doesn't shift the tabs/filter up and down. tonalElevation
    // gives it a faint surface tint so it reads as a floating overlay.
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp)) { content() }
    }
}

@Composable
private fun BannerMessageRow(
    message: String,
    showSpinner: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        if (showSpinner) {
            LoadingAnimation(indicatorSize = 14.dp, circleWidth = 2.dp)
            Spacer(modifier = StdHorzSpacer)
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun RetryButton(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Text(stringRes(Res.string.dvm_home_retry))
    }
}
