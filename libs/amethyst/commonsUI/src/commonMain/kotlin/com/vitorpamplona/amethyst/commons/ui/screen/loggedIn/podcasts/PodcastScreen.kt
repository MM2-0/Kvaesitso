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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.podcasts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.podcasts.dal.OnePodcastFeedViewModel
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.podcast_no_episodes
import com.vitorpamplona.amethyst.commons.resources.route_podcasts
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedError
import com.vitorpamplona.amethyst.commons.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.elements.MoreOptionsButton
import com.vitorpamplona.amethyst.commons.ui.note.types.PodcastBookmarkButton
import com.vitorpamplona.amethyst.commons.ui.screen.SaveableFeedState
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.podcasts.PodcastTrailerListItem
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.podcasts.datasource.OnePodcastFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.ui.theme.Size10dp
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent
import com.vitorpamplona.quartz.nipXXPodcasting20.metadata.Podcasting20PodcastMetadata
import com.vitorpamplona.quartz.nipXXPodcasting20.metadata.resolvePodcastShow
import com.vitorpamplona.quartz.nipXXPodcasting20.trailer.Podcasting20TrailerEvent
import com.vitorpamplona.quartz.podcasts.PodcastShow

@Composable
fun PodcastScreen(
    pubkey: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val podcast = remember(pubkey) { LocalCache.checkGetOrCreateUser(pubkey) } ?: return
    // A show is either NIP-F4 (kind 10154) or Podcasting-2.0 (kind 30078, d=podcast-metadata).
    // Resolve both addresses; whichever has an event is this podcast's metadata.
    val f4Note =
        remember(pubkey) {
            LocalCache.getOrCreateAddressableNote(PodcastMetadataEvent.createAddress(pubkey))
        }
    val p20Note =
        remember(pubkey) {
            LocalCache.getOrCreateAddressableNote(
                Address(AppSpecificDataEvent.KIND, pubkey, Podcasting20PodcastMetadata.PODCAST_METADATA_D_TAG),
            )
        }

    val feedViewModel: OnePodcastFeedViewModel =
        rememberViewModel(
            key = pubkey + "OnePodcastFeedViewModel",
            factory = OnePodcastFeedViewModel.Factory(pubkey, accountViewModel.account),
        )

    PodcastScreen(podcast, f4Note, p20Note, feedViewModel, accountViewModel, nav)
}

@Composable
fun PodcastScreen(
    podcast: User,
    f4Note: Note,
    p20Note: Note,
    feedViewModel: OnePodcastFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(feedViewModel)
    // Fetches the show metadata (NIP-F4 kind 10154 / Podcasting-2.0 kind 30078) and every episode
    // (kind 54 / kind 30054) + trailer authored by this podcast's key from its outbox relays.
    OnePodcastFilterAssemblerSubscription(podcast, accountViewModel)

    val f4Event by observeNoteEvent<PodcastMetadataEvent>(f4Note, accountViewModel)
    val p20Event by observeNoteEvent<AppSpecificDataEvent>(p20Note, accountViewModel)
    val show: PodcastShow? = remember(f4Event, p20Event) { resolvePodcastShow(f4Event) ?: resolvePodcastShow(p20Event) }
    val metadataNote = if (f4Event != null) f4Note else p20Note

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            TopBarExtensibleWithBackButton(
                title = {
                    Text(
                        text = show?.showTitle() ?: stringRes(Res.string.route_podcasts),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                },
                actions = {
                    // Only offer these once the show metadata has loaded — there must be a resolved
                    // event to bookmark / act on. The bookmark button reflects bookmarked state; the
                    // 3-dot button is the standard NoteCompose options menu, sitting to its right.
                    if (show != null) {
                        PodcastBookmarkButton(metadataNote, accountViewModel)
                        Spacer(Modifier.width(Size10dp))
                        MoreOptionsButton(metadataNote, accountViewModel = accountViewModel, nav = nav)
                        Spacer(Modifier.width(Size10dp))
                    }
                },
                nav = nav,
            )
        },
        accountViewModel = accountViewModel,
    ) {
        RefresheableBox(feedViewModel, true) {
            SaveableFeedState(feedViewModel.feedState, scrollStateKey = null) { listState ->
                PodcastScreenBody(
                    metadataNote = metadataNote,
                    show = show,
                    feedViewModel = feedViewModel,
                    listState = listState,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}

@Composable
private fun PodcastScreenBody(
    metadataNote: Note,
    show: PodcastShow?,
    feedViewModel: OnePodcastFeedViewModel,
    listState: LazyListState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val feedState by feedViewModel.feedState.feedContent.collectAsStateWithLifecycle()

    when (val state = feedState) {
        is FeedState.Loaded ->
            PodcastEpisodesList(metadataNote, show, state, listState, accountViewModel, nav)

        is FeedState.Empty ->
            PodcastHeaderWithStatus(metadataNote, show, listState, accountViewModel, nav) {
                StatusText(stringRes(Res.string.podcast_no_episodes))
            }

        is FeedState.FeedError ->
            PodcastHeaderWithStatus(metadataNote, show, listState, accountViewModel, nav) {
                FeedError(state.errorMessage) { feedViewModel.invalidateData() }
            }

        is FeedState.Loading ->
            PodcastHeaderWithStatus(metadataNote, show, listState, accountViewModel, nav) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
    }
}

@Composable
private fun PodcastEpisodesList(
    metadataNote: Note,
    show: PodcastShow?,
    loaded: FeedState.Loaded,
    listState: LazyListState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()

    LazyColumn(
        state = listState,
        contentPadding = rememberFeedContentPadding(FeedPadding),
    ) {
        item("header") {
            // The list mixes in trailers; the header count should reflect episodes only.
            val episodeCount = items.list.count { it.event !is Podcasting20TrailerEvent }
            PodcastHeader(metadataNote, show, episodeCount, accountViewModel, nav)
        }

        itemsIndexed(
            items.list,
            key = { _, item -> item.idHex },
            contentType = { _, item -> if (item.event is Podcasting20TrailerEvent) "trailer" else "episode" },
        ) { index, item ->
            if (item.event is Podcasting20TrailerEvent) {
                PodcastTrailerListItem(item, accountViewModel, nav)
            } else {
                PodcastEpisodeListItem(item, accountViewModel, nav)
            }

            if (index < items.list.lastIndex) {
                HorizontalDivider(thickness = DividerThickness)
            }
        }
    }
}

@Composable
private fun PodcastHeaderWithStatus(
    metadataNote: Note,
    show: PodcastShow?,
    listState: LazyListState,
    accountViewModel: AccountViewModel,
    nav: INav,
    status: @Composable () -> Unit,
) {
    LazyColumn(
        state = listState,
        contentPadding = rememberFeedContentPadding(FeedPadding),
    ) {
        item("header") {
            PodcastHeader(metadataNote, show, null, accountViewModel, nav)
        }
        item("status") { status() }
    }
}

@Composable
private fun StatusText(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.grayText,
            textAlign = TextAlign.Center,
        )
    }
}
