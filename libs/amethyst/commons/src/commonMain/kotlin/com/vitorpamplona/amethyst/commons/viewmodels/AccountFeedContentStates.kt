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
package com.vitorpamplona.amethyst.commons.viewmodels

import com.vitorpamplona.amethyst.commons.articles.dal.ArticlesFeedFilter
import com.vitorpamplona.amethyst.commons.badges.dal.BadgesFeedFilter
import com.vitorpamplona.amethyst.commons.calendars.dal.CalendarAppointmentsFeedFilter
import com.vitorpamplona.amethyst.commons.calendars.dal.CalendarCollectionsFeedFilter
import com.vitorpamplona.amethyst.commons.chats.publicChannels.relayGroup.dal.RelayGroupDiscoveryFeedFilter
import com.vitorpamplona.amethyst.commons.chats.rooms.dal.ChatroomListKnownFeedFilter
import com.vitorpamplona.amethyst.commons.chats.rooms.dal.ChatroomListNewFeedFilter
import com.vitorpamplona.amethyst.commons.communities.list.dal.CommunitiesFeedFilter
import com.vitorpamplona.amethyst.commons.discover.nip23LongForm.DiscoverLongFormFeedFilter
import com.vitorpamplona.amethyst.commons.discover.nip28Chats.DiscoverChatFeedFilter
import com.vitorpamplona.amethyst.commons.discover.nip51FollowSets.DiscoverFollowSetsFeedFilter
import com.vitorpamplona.amethyst.commons.discover.nip53LiveActivities.DiscoverLiveFeedFilter
import com.vitorpamplona.amethyst.commons.discover.nip72Communities.DiscoverCommunityFeedFilter
import com.vitorpamplona.amethyst.commons.discover.nip90DVMs.DiscoverNIP89FeedFilter
import com.vitorpamplona.amethyst.commons.discover.nip99Classifieds.DiscoverMarketplaceFeedFilter
import com.vitorpamplona.amethyst.commons.drafts.dal.DraftEventsFeedFilter
import com.vitorpamplona.amethyst.commons.emojipacks.browse.dal.BrowseEmojiSetsFeedFilter
import com.vitorpamplona.amethyst.commons.feeds.ChannelFeedContentState
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.feeds.TopNavFilterState
import com.vitorpamplona.amethyst.commons.followPacks.list.dal.FollowPacksFeedFilter
import com.vitorpamplona.amethyst.commons.geocaches.dal.GeocacheFindsFeedFilter
import com.vitorpamplona.amethyst.commons.geocaches.dal.GeocacheHuntsFeedFilter
import com.vitorpamplona.amethyst.commons.geocaches.dal.GeocacheMineFeedFilter
import com.vitorpamplona.amethyst.commons.geocaches.dal.GeocachesFeedFilter
import com.vitorpamplona.amethyst.commons.gitRepositories.dal.GitRepositoriesFeedFilter
import com.vitorpamplona.amethyst.commons.highlights.dal.HighlightsFeedFilter
import com.vitorpamplona.amethyst.commons.home.dal.HomeConversationsFeedFilter
import com.vitorpamplona.amethyst.commons.home.dal.HomeEverythingFeedFilter
import com.vitorpamplona.amethyst.commons.home.dal.HomeLiveFilter
import com.vitorpamplona.amethyst.commons.home.dal.HomeNewThreadFeedFilter
import com.vitorpamplona.amethyst.commons.livestreams.dal.LiveStreamsFeedFilter
import com.vitorpamplona.amethyst.commons.longs.dal.LongsFeedFilter
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.privateChats.DM_CHAT_FEED_TYPES
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.amethyst.commons.music.dal.MusicPlaylistsFeedFilter
import com.vitorpamplona.amethyst.commons.music.dal.MusicTracksFeedFilter
import com.vitorpamplona.amethyst.commons.nests.dal.NestsFeedFilter
import com.vitorpamplona.amethyst.commons.nip64Chess.dal.ChessLobbyFeedFilter
import com.vitorpamplona.amethyst.commons.nipsOnNostr.dal.NipsFeedFilter
import com.vitorpamplona.amethyst.commons.notifications.CardFeedContentState
import com.vitorpamplona.amethyst.commons.notifications.NotificationSummaryState
import com.vitorpamplona.amethyst.commons.notifications.OpenPollsState
import com.vitorpamplona.amethyst.commons.notifications.dal.NotificationFeedFilter
import com.vitorpamplona.amethyst.commons.pictures.dal.PictureFeedFilter
import com.vitorpamplona.amethyst.commons.podcasts.dal.PodcastEpisodesFeedFilter
import com.vitorpamplona.amethyst.commons.podcasts.dal.PodcastsFeedFilter
import com.vitorpamplona.amethyst.commons.polls.dal.ClosedPollsFeedFilter
import com.vitorpamplona.amethyst.commons.polls.dal.OpenPollsFeedFilter
import com.vitorpamplona.amethyst.commons.polls.dal.PollsFeedFilter
import com.vitorpamplona.amethyst.commons.products.dal.ProductsFeedFilter
import com.vitorpamplona.amethyst.commons.publicChats.dal.PublicChatsFeedFilter
import com.vitorpamplona.amethyst.commons.shorts.dal.ShortsFeedFilter
import com.vitorpamplona.amethyst.commons.softwareapps.dal.SoftwareAppsFeedFilter
import com.vitorpamplona.amethyst.commons.video.dal.VideoFeedFilter
import com.vitorpamplona.amethyst.commons.webBookmarks.dal.WebBookmarkFeedFilter
import com.vitorpamplona.amethyst.commons.workouts.dal.WorkoutFeedFilter
import com.vitorpamplona.amethyst.commons.wot.network.TrustVerdicts
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip90Dvms.dvmHeartbeat.DvmHeartbeatEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AccountFeedContentStates(
    val account: Account,
    val scope: CoroutineScope,
    memoryPressure: Flow<Unit>,
) {
    val homeLive = ChannelFeedContentState(HomeLiveFilter(account), scope)
    val homeNewThreads = FeedContentState(HomeNewThreadFeedFilter(account), scope, LocalCache)
    val homeReplies = FeedContentState(HomeConversationsFeedFilter(account), scope, LocalCache)
    val homeEverything = FeedContentState(HomeEverythingFeedFilter(account), scope, LocalCache)

    val dmKnown = FeedContentState(ChatroomListKnownFeedFilter(account), scope, LocalCache)
    val dmNew = FeedContentState(ChatroomListNewFeedFilter(account), scope, LocalCache)

    val videoFeed = FeedContentState(VideoFeedFilter(account), scope, LocalCache)

    val discoverFollowSets = FeedContentState(DiscoverFollowSetsFeedFilter(account), scope, LocalCache)
    val discoverReads = FeedContentState(DiscoverLongFormFeedFilter(account), scope, LocalCache)
    val discoverMarketplace = FeedContentState(DiscoverMarketplaceFeedFilter(account), scope, LocalCache)
    val discoverDVMs = FeedContentState(DiscoverNIP89FeedFilter(account), scope, LocalCache)
    val discoverLive = FeedContentState(DiscoverLiveFeedFilter(account), scope, LocalCache)
    val discoverCommunities = FeedContentState(DiscoverCommunityFeedFilter(account), scope, LocalCache)
    val discoverPublicChats = FeedContentState(DiscoverChatFeedFilter(account), scope, LocalCache)

    val pollsFeed = FeedContentState(PollsFeedFilter(account), scope, LocalCache)
    val openPollsFeed = FeedContentState(OpenPollsFeedFilter(account), scope, LocalCache)
    val closedPollsFeed = FeedContentState(ClosedPollsFeedFilter(account), scope, LocalCache)

    val badgesFeed = FeedContentState(BadgesFeedFilter(account), scope, LocalCache)

    val browseEmojiSetsFeed = FeedContentState(BrowseEmojiSetsFeedFilter(account), scope, LocalCache)
    val communitiesList = FeedContentState(CommunitiesFeedFilter(account), scope, LocalCache)

    val picturesFeed = FeedContentState(PictureFeedFilter(account), scope, LocalCache)
    val workoutsFeed = FeedContentState(WorkoutFeedFilter(account), scope, LocalCache)
    val chessLobbyFeed = FeedContentState(ChessLobbyFeedFilter(account), scope, LocalCache)
    val gitRepositoriesFeed = FeedContentState(GitRepositoriesFeedFilter(account), scope, LocalCache)
    val nipsFeed = FeedContentState(NipsFeedFilter(account), scope, LocalCache)
    val highlightsFeed = FeedContentState(HighlightsFeedFilter(account), scope, LocalCache)
    val relayGroupsDiscoveryFeed = FeedContentState(RelayGroupDiscoveryFeedFilter(account), scope, LocalCache)
    val calendarAppointmentsFeed = FeedContentState(CalendarAppointmentsFeedFilter(account), scope, LocalCache)
    val calendarCollectionsFeed = FeedContentState(CalendarCollectionsFeedFilter(account), scope, LocalCache)
    val productsFeed = FeedContentState(ProductsFeedFilter(account), scope, LocalCache)
    val geocachesFeed = FeedContentState(GeocachesFeedFilter(account), scope, LocalCache)
    val geocacheFindsFeed = FeedContentState(GeocacheFindsFeedFilter(account), scope, LocalCache)
    val geocacheMineFeed = FeedContentState(GeocacheMineFeedFilter(account), scope, LocalCache)
    val geocacheHuntsFeed = FeedContentState(GeocacheHuntsFeedFilter(account), scope, LocalCache)
    val shortsFeed = FeedContentState(ShortsFeedFilter(account), scope, LocalCache)
    val publicChatsFeed = FeedContentState(PublicChatsFeedFilter(account), scope, LocalCache)
    val followPacksFeed = FeedContentState(FollowPacksFeedFilter(account), scope, LocalCache)
    val liveStreamsFeed = FeedContentState(LiveStreamsFeedFilter(account), scope, LocalCache)
    val nestsFeed = FeedContentState(NestsFeedFilter(account), scope, LocalCache)
    val longsFeed = FeedContentState(LongsFeedFilter(account), scope, LocalCache)
    val articlesFeed = FeedContentState(ArticlesFeedFilter(account), scope, LocalCache)
    val musicTracksFeed = FeedContentState(MusicTracksFeedFilter(account), scope, LocalCache)
    val musicPlaylistsFeed = FeedContentState(MusicPlaylistsFeedFilter(account), scope, LocalCache)
    val podcastEpisodesFeed = FeedContentState(PodcastEpisodesFeedFilter(account), scope, LocalCache)
    val podcastsFeed = FeedContentState(PodcastsFeedFilter(account), scope, LocalCache)
    val softwareAppsFeed = FeedContentState(SoftwareAppsFeedFilter(account), scope, LocalCache)

    val notifications = CardFeedContentState(NotificationFeedFilter(account), scope)
    val notificationsFollowing = CardFeedContentState(NotificationFeedFilter(account, TopFilter.AllFollows), scope)
    val notificationsEveryone = CardFeedContentState(NotificationFeedFilter(account, TopFilter.Global), scope)

    val notificationsOpenPolls = OpenPollsState(account, scope)

    val notificationSummary = NotificationSummaryState(account)

    val feedListOptions = TopNavFilterState(account, scope)

    val drafts = FeedContentState(DraftEventsFeedFilter(account), scope, LocalCache)

    val webBookmarks = FeedContentState(WebBookmarkFeedFilter(account), scope, LocalCache)

    init {
        // Under real memory pressure, trim every feed down to release the strong Note
        // references that would otherwise keep pruned cache objects alive.
        scope.launch(Dispatchers.IO) {
            memoryPressure.collect { trimFeedsToSize(200) }
        }

        // Co-maintainers' announcements merge repository cards into one per repository
        // (GitRepositoriesFeedFilter.sort), and a membership is only known once they arrive, which
        // the additive path never re-checks: rebuild the list when the memberships change.
        scope.launch(Dispatchers.IO) {
            @OptIn(FlowPreview::class)
            LocalCache
                .observeEvents<GitRepositoryEvent>(Filter(kinds = listOf(GitRepositoryEvent.KIND)))
                .drop(1)
                .debounce(500)
                .collect { gitRepositoriesFeed.invalidateData() }
        }

        // Marmot group list changes (new group, group marked known, group
        // metadata synced) don't flow through LocalCache.newEventBundles, so
        // the additive update path can't see them. Force a full feed rebuild
        // whenever the list changes so empty groups appear and placeholder
        // rows get replaced by real messages.
        scope.launch(Dispatchers.IO) {
            account.marmotGroupList.groupListChanges.collect {
                dmKnown.invalidateData()
                dmNew.invalidateData()
            }
        }

        // Same for cordn, and more so: a cordn message is an MLS envelope that
        // never enters LocalCache at all (§3.3 of
        // amethyst/plans/2026-09-19-cordn-ui.md), so nothing about a cordn room
        // can ever reach the additive path. Without this the inbox never shows
        // a cordn room — not when one is created, not when a Welcome is
        // accepted, and not after a relaunch that restored it — and since the
        // room list is the only way back into a room, a group became
        // unreachable the moment its screen was closed.
        //
        // `revision` and not `all`: `all` re-emits only when the room *set*
        // changes, so a message arriving for a room the inbox already lists
        // never reached this collector. The rows therefore kept whatever order
        // the build that first saw them gave — and since a cordn row sorts on
        // its newest message, that meant the inbox ordered cordn rooms by when
        // they were joined and never moved them again. `revision` bumps on the
        // set *and* on every message filed into a room. sample() keeps the
        // restore burst, when every coordinator's groups arrive at once, from
        // rebuilding the feed once per room.
        //
        // No drop(1), unlike the collectors around it. Those drop the replay
        // because the feed's first build already saw their state; cordn's
        // restore runs in its own launch from Account's constructor and often
        // finishes *after* that build, so the current value is exactly the one
        // that matters — a StateFlow replays it on subscribe and dropping it
        // waits for a change that, for an account whose rooms are all restored
        // rather than newly created, never comes. The cost of keeping it is one
        // extra rebuild at login.
        account.cordnRuntime?.let { runtime ->
            scope.launch(Dispatchers.IO) {
                @OptIn(FlowPreview::class)
                runtime.groups.revision
                    .sample(500)
                    .collect {
                        dmKnown.invalidateData()
                    }
            }
        }

        // Same for the NIP-29 joined-group list (kind 10009): joining/leaving changes the list but
        // doesn't flow through newEventBundles, so force a rebuild — otherwise a just-joined group
        // (whose messages haven't loaded yet) wouldn't appear on the Messages tab until a later event.
        scope.launch(Dispatchers.IO) {
            account.relayGroupList.liveRelayGroupList
                .drop(1)
                .collect {
                    dmKnown.invalidateData()
                }
        }

        // A pending channel invite is a row on Notifications and on Messages › New Requests, but
        // nothing about answering one flows through newEventBundles: accepting writes my kind-10009,
        // dismissing touches only local settings, and classification lands a kind-39000 that is not
        // itself a notification. Each of those changes whether the 44100 still belongs in either
        // feed, so rebuild when the projection moves — otherwise an answered invite would sit on the
        // tab until an unrelated event refreshed it. Arriving invites come through here too: neither
        // filter picks a 44100 up additively, so this is what puts a new one on screen.
        //
        // The card feeds need `clear()` first, because answering an invite REMOVES a row and their
        // additive refresh cannot express that: it diffs `feed()` against `lastNotes`, finds no *new*
        // notes, and bails without touching the list — leaving the answered invite in place. Clearing
        // drops the additive fast path so the refresh rebuilds the whole list, which is the only
        // branch that can shrink. FeedContentState (dmNew) rebuilds from feed() on invalidateData()
        // regardless, so it shrinks on its own.
        scope.launch(Dispatchers.IO) {
            account.channelInvites.pendingByEventId
                .drop(1)
                .collect {
                    listOf(notifications, notificationsFollowing, notificationsEveryone).forEach {
                        it.clear()
                        it.invalidateData()
                    }
                    dmNew.invalidateData()
                }
        }

        // Pending Concord Direct Invites are rows on Notifications and on Messages › New Requests, and
        // like the channel invites above none of their arrivals or answers flow through
        // newEventBundles (they are not cache events). Rebuild both, clearing the card feeds first so
        // an answered invite's row can disappear.
        scope.launch(Dispatchers.IO) {
            account.concord.pendingConcordDirectInviteNotes
                .drop(1)
                .collect {
                    listOf(notifications, notificationsFollowing, notificationsEveryone).forEach {
                        it.clear()
                        it.invalidateData()
                    }
                    dmNew.invalidateData()
                }
        }

        // Joining/leaving a geohash location channel (kind 10081 list) changes the Messages list but
        // no event flows through LocalCache, so force a rebuild — otherwise a just-joined cell (whose
        // ephemeral messages haven't arrived yet) wouldn't show its placeholder row until later.
        scope.launch(Dispatchers.IO) {
            account.geohashList.flow
                .drop(1)
                .collect {
                    dmKnown.invalidateData()
                }
        }

        // Flipping the NIP-29 view mode (inline groups vs one row per relay) changes what the
        // Messages feed emits for joined groups, but no event flows through LocalCache — force a
        // full rebuild so the list switches shape immediately.
        scope.launch(Dispatchers.IO) {
            account.settings.relayGroupViewMode
                .drop(1)
                .collect {
                    dmKnown.invalidateData()
                }
        }

        // A Concord control-plane fold is what first reveals a community's channels (and what makes
        // ConcordCommunitySession.state non-null, without which ChatroomListKnownFeedFilter emits
        // nothing at all for that community). None of it flows through LocalCache.newEventBundles,
        // so the additive path can't see it: a folded channel reaches the Messages tab only if a
        // message for it happens to arrive afterwards. Cold boot therefore shows a *subset* of a
        // community's channels, or omits a quiet community entirely, until some unrelated
        // invalidation fires. Rebuild on every structural change instead. `revision` bumps only on
        // fold/membership/rekey (never a plain message), and sample() coalesces the burst of folds
        // that lands as each control plane catches up — the same pairing Account.kt uses to drive
        // refreshConcordChannelIndex off this flow.
        scope.launch(Dispatchers.IO) {
            @OptIn(FlowPreview::class)
            account.concordSessions.revision
                .drop(1)
                .sample(500)
                .collect {
                    dmKnown.invalidateData()
                }
        }

        // Same for the Concord view mode (inline channels vs one row per community).
        scope.launch(Dispatchers.IO) {
            account.settings.concordViewMode
                .drop(1)
                .collect {
                    dmKnown.invalidateData()
                }
        }

        // Toggling a chat type on/off in Settings › Messages changes which sections the inbox shows,
        // but no event flows through LocalCache — force a full rebuild of both tabs so turned-off types
        // disappear (and re-enabled ones reappear from cache) immediately. Keyed off the APPLIED set:
        // the account first drops (or re-indexes) the DM rooms, so the rebuild never sees them half-done.
        scope.launch(Dispatchers.IO) {
            account.chatFeedToggles.applied
                .drop(1)
                .collect {
                    dmKnown.invalidateData()
                    dmNew.invalidateData()
                }
        }

        // Notifications only change when a DM protocol flips (its DMs leave them too), so the other chat
        // types don't rebuild all three. Clear first so the refresh removes cards instead of taking the
        // additive path.
        scope.launch(Dispatchers.IO) {
            account.chatFeedToggles.applied
                .map { it intersect DM_CHAT_FEED_TYPES }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    notifications.clear()
                    notifications.invalidateData()
                    notificationsFollowing.clear()
                    notificationsFollowing.invalidateData()
                    notificationsEveryone.clear()
                    notificationsEveryone.invalidateData()
                }
        }

        // Toggling a Home content type on/off in Settings › Home changes which event kinds the tabs
        // render, but no event flows through LocalCache — force a rebuild of all three home feeds so
        // hidden kinds disappear (and re-enabled ones reappear from cache) immediately.
        scope.launch(Dispatchers.IO) {
            account.settings.enabledHomeFeedTypes
                .drop(1)
                .collect {
                    homeNewThreads.invalidateData()
                    homeReplies.invalidateData()
                    homeEverything.invalidateData()
                }
        }

        // Pinning/unpinning a room changes its sort order, moves it between Known and
        // New, and adds/removes the placeholder row of a pinned room with no loaded
        // message — none of which flows through LocalCache. Force a rebuild of both.
        // This also fires when pins arrive via the synced AppSpecificData event.
        scope.launch(Dispatchers.IO) {
            account.settings.syncedSettings.chats.pinnedChatrooms
                .drop(1)
                .collect {
                    dmKnown.invalidateData()
                    dmNew.invalidateData()
                }
        }

        // The NIPs feed is read beyond its screen (the "Defined by" row of an unknown event, a kind
        // page's tab title), so a mute rebuilds it wherever it is shown. An additive feed only adds,
        // so without this a newly hidden author's NIP would stay listed.
        scope.launch(Dispatchers.IO) {
            account.hiddenUsers.flow
                .drop(1)
                .collect { nipsFeed.invalidateData() }
        }

        scope.launch(Dispatchers.IO) {
            account.hiddenUsers.flow.collect {
                dmKnown.invalidateData()
                dmNew.invalidateData()
                // Re-mute removes cards, not just adds them. CardFeedContentState's
                // refreshSuspended() takes an additive-only path when lastNotes is
                // populated, which keeps stale cards for notes that no longer pass
                // the filter. Clear first so the refresh hits the full-rebuild branch.
                notifications.clear()
                notifications.invalidateData()
                notificationsFollowing.clear()
                notificationsFollowing.invalidateData()
                notificationsEveryone.clear()
                notificationsEveryone.invalidateData()
            }
        }

        // A new Web of Trust answer (see Account.trustVerdicts) moves people between Known and
        // New Requests and in or out of Curated notifications, with no event flowing through
        // LocalCache. Rebuild those feeds; clear the card feeds first, since their refresh is
        // additive-only and would keep cards that no longer pass.
        scope.launch(Dispatchers.IO) {
            // Not "skip the first value": the network can finish loading before this collector
            // starts but after the feeds were first built. So the first active answers always
            // rebuild once, and after that only answers that differ from the last rebuild.
            // The first one at once (the feeds were built unfiltered moments ago, at start or
            // while the download ran); later ones settle for a second, as follows change in bursts.
            var builtWith: TrustVerdicts? = null
            @OptIn(FlowPreview::class)
            account.trustVerdicts
                .debounce { if (builtWith == null) 0L else 1_000L }
                .filter { verdicts -> builtWith?.let { !verdicts.sameAnswersAs(it) } ?: verdicts.isActive }
                .collect {
                    builtWith = it
                    dmKnown.invalidateData()
                    dmNew.invalidateData()
                    // Only Curated reads the verdicts; picking it later changes the feed key, and
                    // that rebuilds it.
                    if (account.settings.defaultNotificationFollowList.value is TopFilter.Selected) {
                        notifications.clear()
                        notifications.invalidateData()
                    }
                }
        }

        // Heartbeat staleness produces no cache event (a beat just ages past 420s), so re-check
        // the DVM discovery feed on a timer. refreshSuspended() no-ops when nothing changed.
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(60_000)
                discoverDVMs.invalidateData()
            }
        }
    }

    suspend fun init() {
        notificationSummary.initializeSuspend()
    }

    fun updateFeedsWith(newNotes: Set<Note>) {
        homeLive.updateFeedWith(newNotes)
        homeNewThreads.updateFeedWith(newNotes)
        homeReplies.updateFeedWith(newNotes)
        homeEverything.updateFeedWith(newNotes)

        dmKnown.updateFeedWith(newNotes)
        dmNew.updateFeedWith(newNotes)

        videoFeed.updateFeedWith(newNotes)

        discoverMarketplace.updateFeedWith(newNotes)
        discoverFollowSets.updateFeedWith(newNotes)
        discoverReads.updateFeedWith(newNotes)
        if (newNotes.any { it.event is DvmHeartbeatEvent }) {
            // A heartbeat is never a feed row, so the additive path would graft nothing and never
            // re-evaluate the announcement it just validated. Rebuild instead.
            discoverDVMs.invalidateData()
        } else {
            discoverDVMs.updateFeedWith(newNotes)
        }
        discoverLive.updateFeedWith(newNotes)
        discoverCommunities.updateFeedWith(newNotes)
        discoverPublicChats.updateFeedWith(newNotes)

        pollsFeed.updateFeedWith(newNotes)
        openPollsFeed.updateFeedWith(newNotes)
        closedPollsFeed.updateFeedWith(newNotes)

        badgesFeed.updateFeedWith(newNotes)

        browseEmojiSetsFeed.updateFeedWith(newNotes)
        communitiesList.updateFeedWith(newNotes)

        picturesFeed.updateFeedWith(newNotes)
        workoutsFeed.updateFeedWith(newNotes)
        chessLobbyFeed.updateFeedWith(newNotes)
        gitRepositoriesFeed.updateFeedWith(newNotes)
        nipsFeed.updateFeedWith(newNotes)
        highlightsFeed.updateFeedWith(newNotes)
        relayGroupsDiscoveryFeed.updateFeedWith(newNotes)
        productsFeed.updateFeedWith(newNotes)
        geocachesFeed.updateFeedWith(newNotes)
        geocacheFindsFeed.updateFeedWith(newNotes)
        geocacheMineFeed.updateFeedWith(newNotes)
        geocacheHuntsFeed.updateFeedWith(newNotes)
        shortsFeed.updateFeedWith(newNotes)
        publicChatsFeed.updateFeedWith(newNotes)
        followPacksFeed.updateFeedWith(newNotes)
        liveStreamsFeed.updateFeedWith(newNotes)
        nestsFeed.updateFeedWith(newNotes)
        longsFeed.updateFeedWith(newNotes)
        articlesFeed.updateFeedWith(newNotes)
        musicTracksFeed.updateFeedWith(newNotes)
        musicPlaylistsFeed.updateFeedWith(newNotes)
        podcastEpisodesFeed.updateFeedWith(newNotes)
        podcastsFeed.updateFeedWith(newNotes)
        softwareAppsFeed.updateFeedWith(newNotes)

        calendarAppointmentsFeed.updateFeedWith(newNotes)
        calendarCollectionsFeed.updateFeedWith(newNotes)

        notifications.updateFeedWith(newNotes)
        if (account.settings.splitNotificationsEnabled.value) {
            notificationsFollowing.updateFeedWith(newNotes)
            notificationsEveryone.updateFeedWith(newNotes)
        }
        notificationSummary.invalidateInsertData(newNotes)

        drafts.updateFeedWith(newNotes)

        webBookmarks.updateFeedWith(newNotes)
    }

    fun deleteNotes(newNotes: Set<Note>) {
        // The notification feeds are handled once, below, under the same split-setting gate the
        // insert path uses: each deleteFromFeed on a card feed is a full card rebuild.
        homeLive.deleteFromFeed(newNotes)
        homeNewThreads.deleteFromFeed(newNotes)
        homeReplies.deleteFromFeed(newNotes)
        homeEverything.deleteFromFeed(newNotes)

        dmKnown.updateFeedWith(newNotes)
        dmNew.updateFeedWith(newNotes)

        videoFeed.deleteFromFeed(newNotes)

        discoverMarketplace.deleteFromFeed(newNotes)
        discoverFollowSets.deleteFromFeed(newNotes)
        discoverReads.deleteFromFeed(newNotes)
        discoverDVMs.deleteFromFeed(newNotes)
        discoverLive.deleteFromFeed(newNotes)
        discoverCommunities.deleteFromFeed(newNotes)
        discoverPublicChats.deleteFromFeed(newNotes)

        pollsFeed.deleteFromFeed(newNotes)
        openPollsFeed.deleteFromFeed(newNotes)
        closedPollsFeed.deleteFromFeed(newNotes)

        badgesFeed.deleteFromFeed(newNotes)

        browseEmojiSetsFeed.deleteFromFeed(newNotes)
        communitiesList.deleteFromFeed(newNotes)

        picturesFeed.deleteFromFeed(newNotes)
        workoutsFeed.deleteFromFeed(newNotes)
        chessLobbyFeed.deleteFromFeed(newNotes)
        gitRepositoriesFeed.deleteFromFeed(newNotes)
        nipsFeed.deleteFromFeed(newNotes)
        highlightsFeed.deleteFromFeed(newNotes)
        relayGroupsDiscoveryFeed.deleteFromFeed(newNotes)
        productsFeed.deleteFromFeed(newNotes)
        geocachesFeed.deleteFromFeed(newNotes)
        geocacheFindsFeed.deleteFromFeed(newNotes)
        geocacheMineFeed.deleteFromFeed(newNotes)
        geocacheHuntsFeed.deleteFromFeed(newNotes)
        shortsFeed.deleteFromFeed(newNotes)
        publicChatsFeed.deleteFromFeed(newNotes)
        followPacksFeed.deleteFromFeed(newNotes)
        liveStreamsFeed.deleteFromFeed(newNotes)
        nestsFeed.deleteFromFeed(newNotes)
        longsFeed.deleteFromFeed(newNotes)
        articlesFeed.deleteFromFeed(newNotes)
        musicTracksFeed.deleteFromFeed(newNotes)
        musicPlaylistsFeed.deleteFromFeed(newNotes)
        podcastEpisodesFeed.deleteFromFeed(newNotes)
        podcastsFeed.deleteFromFeed(newNotes)
        softwareAppsFeed.deleteFromFeed(newNotes)

        calendarAppointmentsFeed.deleteFromFeed(newNotes)
        calendarCollectionsFeed.deleteFromFeed(newNotes)

        notifications.deleteFromFeed(newNotes)
        if (account.settings.splitNotificationsEnabled.value) {
            notificationsFollowing.deleteFromFeed(newNotes)
            notificationsEveryone.deleteFromFeed(newNotes)
        }
        notificationSummary.invalidateInsertData(newNotes)

        drafts.deleteFromFeed(newNotes)

        webBookmarks.deleteFromFeed(newNotes)
    }

    fun trimFeedsToSize(maxItems: Int) {
        homeNewThreads.trimToSize(maxItems)
        homeReplies.trimToSize(maxItems)
        homeEverything.trimToSize(maxItems)

        dmKnown.trimToSize(maxItems)
        dmNew.trimToSize(maxItems)

        videoFeed.trimToSize(maxItems)

        discoverFollowSets.trimToSize(maxItems)
        discoverReads.trimToSize(maxItems)
        discoverMarketplace.trimToSize(maxItems)
        discoverDVMs.trimToSize(maxItems)
        discoverLive.trimToSize(maxItems)
        discoverCommunities.trimToSize(maxItems)
        discoverPublicChats.trimToSize(maxItems)

        pollsFeed.trimToSize(maxItems)
        openPollsFeed.trimToSize(maxItems)
        closedPollsFeed.trimToSize(maxItems)

        badgesFeed.trimToSize(maxItems)
        browseEmojiSetsFeed.trimToSize(maxItems)
        communitiesList.trimToSize(maxItems)

        picturesFeed.trimToSize(maxItems)
        workoutsFeed.trimToSize(maxItems)
        chessLobbyFeed.trimToSize(maxItems)
        gitRepositoriesFeed.trimToSize(maxItems)
        nipsFeed.trimToSize(maxItems)
        highlightsFeed.trimToSize(maxItems)
        relayGroupsDiscoveryFeed.trimToSize(maxItems)
        calendarAppointmentsFeed.trimToSize(maxItems)
        calendarCollectionsFeed.trimToSize(maxItems)
        productsFeed.trimToSize(maxItems)
        geocachesFeed.trimToSize(maxItems)
        geocacheFindsFeed.trimToSize(maxItems)
        geocacheMineFeed.trimToSize(maxItems)
        geocacheHuntsFeed.trimToSize(maxItems)
        shortsFeed.trimToSize(maxItems)
        publicChatsFeed.trimToSize(maxItems)
        followPacksFeed.trimToSize(maxItems)
        liveStreamsFeed.trimToSize(maxItems)
        nestsFeed.trimToSize(maxItems)
        longsFeed.trimToSize(maxItems)
        articlesFeed.trimToSize(maxItems)
        musicTracksFeed.trimToSize(maxItems)
        musicPlaylistsFeed.trimToSize(maxItems)
        podcastEpisodesFeed.trimToSize(maxItems)
        podcastsFeed.trimToSize(maxItems)
        softwareAppsFeed.trimToSize(maxItems)

        notifications.trimToSize(maxItems)
        notificationsFollowing.trimToSize(maxItems)
        notificationsEveryone.trimToSize(maxItems)

        drafts.trimToSize(maxItems)
        webBookmarks.trimToSize(maxItems)
    }

    fun destroy() {
        notifications.destroy()
        notificationsFollowing.destroy()
        notificationsEveryone.destroy()
        notificationSummary.destroy()

        feedListOptions.destroy()
    }
}
