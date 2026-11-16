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
package com.vitorpamplona.amethyst.commons.ui.navigation.host

import com.vitorpamplona.amethyst.commons.cashu.ui.CashuWalletCreatedScreen
import com.vitorpamplona.amethyst.commons.chats.ui.NewConversationScreen
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.nipsOnNostr.ui.EventKindScreen
import com.vitorpamplona.amethyst.commons.nipsOnNostr.ui.NipsScreen
import com.vitorpamplona.amethyst.commons.nipsOnNostr.ui.OfficialNipScreen
import com.vitorpamplona.amethyst.commons.ui.actions.NewUserMetadataScreen
import com.vitorpamplona.amethyst.commons.ui.actions.bolt12Offers.Bolt12OffersScreen
import com.vitorpamplona.amethyst.commons.ui.actions.mediaServers.AllMediaServersScreen
import com.vitorpamplona.amethyst.commons.ui.actions.mediaServers.BlossomBlobManagerScreen
import com.vitorpamplona.amethyst.commons.ui.actions.mediaServers.BlossomImportScreen
import com.vitorpamplona.amethyst.commons.ui.actions.nestsServers.NestsServersScreen
import com.vitorpamplona.amethyst.commons.ui.actions.paymentTargets.PaymentTargetsScreen
import com.vitorpamplona.amethyst.commons.ui.note.PayViaIntentScreen
import com.vitorpamplona.amethyst.commons.ui.note.UpdateReactionTypeScreen
import com.vitorpamplona.amethyst.commons.ui.note.nip22Comments.ReplyCommentPostScreen
import com.vitorpamplona.amethyst.commons.ui.note.share.ShareNoteAsImageFileScreen
import com.vitorpamplona.amethyst.commons.ui.note.share.ShareNoteAsImageScreen
import com.vitorpamplona.amethyst.commons.ui.note.share.ShareNoteAsQrScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.apps.recommendations.ProfileAppRecommendationsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.articles.ArticlesScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.backups.BackupConflictReviewScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.badges.BadgesScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.badges.award.AwardBadgeScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.badges.profile.ProfileBadgesScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.bookmarkgroups.default.BookmarkListScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.bookmarkgroups.display.BookmarkGroupScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.bookmarkgroups.list.ListOfBookmarkGroupsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.bookmarkgroups.list.metadata.BookmarkGroupMetadataScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.bookmarkgroups.membershipManagement.ArticleBookmarkListManagementScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.bookmarkgroups.membershipManagement.PostBookmarkListManagementScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.bookmarkgroups.old.OldBookmarkListScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.bookmarkgroups.podcasts.BookmarkedPodcastsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.bookmarkgroups.repositories.BookmarkedRepositoriesScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.browser.BrowserScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.buzz.AgentAttestationScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.buzz.AgentConsoleScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.buzz.AgentPersonaEditScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.buzz.AgentWorkBoardScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.buzz.BuzzCanvasScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.buzz.BuzzDmListScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.buzz.BuzzForumPostScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.buzz.BuzzForumThreadScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.buzz.BuzzInviteScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.buzz.BuzzNewDmScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.buzz.JobBoardScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.buzz.WorkflowRunBoardScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.CalendarCollectionsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.CalendarReminderSettingsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.CalendarsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.create.NewCalendarCollectionScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.create.NewCalendarEventScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.detail.CalendarEventDetailScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.cordnGroup.CordnCreateGroupScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.cordnGroup.CordnCreateMembersScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.cordnGroup.CordnGroupInfoScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.cordnGroup.CordnGroupListScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.cordnGroup.CordnInvitationsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.geohashChat.GeohashChatScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.geohashChat.GeohashChatsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.geohashChat.GeohashTeleportScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.geohashChat.NewGeohashChatScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.listDetail.ChatListDetailScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.listDetail.isConcordDetailRoute
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.listDetail.isGeohashDetailRoute
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.listDetail.isMarmotDetailRoute
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.listDetail.isPlatformChatDetailRoute
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.listDetail.isRelayGroupDetailRoute
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.marmotGroup.CreateGroupScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.marmotGroup.EditGroupInfoScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.marmotGroup.MarmotGroupChatScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.marmotGroup.MarmotGroupInfoScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.marmotGroup.MarmotGroupListScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.minichat.MinichatScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.ChatroomByAuthorScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.ChatroomScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.send.NewGroupDMScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord.ConcordChannelListScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord.ConcordChannelScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord.ConcordCreateScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord.ConcordEditScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord.ConcordHomeScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord.ConcordInviteLinksScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord.ConcordInviteScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord.ConcordMembersScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.ephemChat.EphemeralChatScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.ephemChat.metadata.NewEphemeralChatScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.nip28PublicChat.PublicChatChannelScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.nip28PublicChat.metadata.ChannelMetadataScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.nip53LiveActivities.LiveActivityChannelScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.RelayGroupBrowseScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.RelayGroupChannelListScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.RelayGroupChatScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.RelayGroupCreateScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.RelayGroupDiscoveryScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.RelayGroupEditScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.RelayGroupMembersScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.RelayGroupThreadsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.rooms.MessagesScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.share.ShareToDMScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chess.ChessGameScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chess.ChessLobbyScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.communities.CommunityScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.communities.list.CommunitiesScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.communities.newCommunity.EditCommunityScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.communities.newCommunity.NewCommunityScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.contactList.ContactListUsersScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.customFeeds.CustomFeedsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.customFeeds.EditCustomFeedScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.devtools.DevToolsHubScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.devtools.EventFinderScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.devtools.EventInspectorScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.devtools.KindBrowserScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.devtools.KindLookupScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.devtools.Nip19ToolScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.devtools.RelayConsoleScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.discover.DiscoverScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.discover.nip23LongForm.LongFormPostScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.discover.nip99Classifieds.NewProductScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.drafts.DraftListScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.drss.DrssCollectToScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.drss.DrssFollowIntoScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.drss.DrssHistoryScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.drss.DrssListenScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.drss.DrssListsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.drss.DrssRecordProvenanceScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.drss.DrssSaveToScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.drss.DrssShowEditScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.dvms.DvmContentDiscoveryScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.dvms.favorites.FavoriteAlgoFeedsListScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.emojipacks.browse.BrowseEmojiSetsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.emojipacks.display.EmojiPackScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.emojipacks.list.ListOfEmojiPacksScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.emojipacks.list.metadata.EmojiPackMetadataScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.emojipacks.membershipManagement.EmojiPackSelectionScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.emojipacks.membershipManagement.MyEmojiListScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.favorites.FavoriteAppsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.followPacks.feed.FollowPackFeedScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.followPacks.list.FollowPacksScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.frontpage.FrontPageScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.GeocachesScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.create.NewGeocacheScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.detail.GeocacheDetailScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.hunt.GeocacheHuntScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.hunt.NewGeocacheHuntScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.log.LogGeocacheFindScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geohash.GeoHashPostScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geohash.GeoHashScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepositories.GitRepositoriesScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepositories.newRepository.NewGitRepositoryScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.hashtag.HashtagPostScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.hashtag.HashtagScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.highlights.HighlightsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.highlights.NewHighlightScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.home.HomeScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.home.ShortNotePostScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.home.VoiceReplyScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.home.nip75Goals.NewGoalScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.interestSets.display.InterestSetScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.interestSets.list.ListOfInterestSetsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.interestSets.list.metadata.InterestSetMetadataScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.keyBackup.AccountBackupScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.lists.display.lists.PeopleListScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.lists.display.packs.FollowPackScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.lists.list.ListOfPeopleListsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.lists.list.metadata.FollowPackMetadataScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.lists.list.metadata.PeopleListMetadataScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.lists.memberEdit.FollowListAndPackAndUserScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.livestreams.LiveStreamsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.longs.LongsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.music.AddToMusicPlaylistSheet
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.music.MusicPlaylistsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.music.MusicTracksScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.music.NewMusicPlaylistScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.music.NewMusicTrackScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.napplets.ConnectedAppsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.napplets.NappletsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nests.NestsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nests.room.lobby.NestLobbyScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.newUser.ImportFollowListPickFollowsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.newUser.ImportFollowListSelectUserScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.notifications.NotificationScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.notifications.publicMessages.NewPublicMessageScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nsites.NsitesScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.pictures.PicturesScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.pinnednotes.PinnedNotesScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.podcasts.PodcastEpisodesScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.podcasts.PodcastScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.podcasts.PodcastsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.podcasts.authoring.EditPodcastShowScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.podcasts.authoring.NewPodcastEpisodeScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.podcasts.authoring.NewPodcastTrailerScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.podcasts.authoring.PodcastAuthoringScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.polls.PollPostScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.polls.PollsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.polls.results.PollResultsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.privacy.PrivacyOptionsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.products.ProductsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.ProfileScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.payment.SendPaymentScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.publicChats.PublicChatsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode.ShowQRScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.redirect.LoadRedirectScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relay.RelayFeedScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relayList.RelayListMembersScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relayauth.RelayAuthSettingsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.AllRelayListScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.RelayInformationScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.eventsync.EventSyncScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.nip43.RelayMembersScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.nip86.RelayManagementScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.subscriptions.ActiveSubscriptionsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.vanish.RequestToVanishScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.vanish.VanishEventsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.scheduledposts.ScheduledPostsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.search.SearchScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.AccentColorSettingsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.AgentKeysSettingsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.AllSettingsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.AudioVisualizerSettingsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.BlockedUsersScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.BottomBarSettingsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.CallSettingsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.ComposeSettingsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.DrawerSettingsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.HiddenWordsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.HomeTabsSettingsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.MessagesSettingsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.MutedThreadsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.NIP47SetupScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.NamecoinSettingsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.NotificationSettingsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.OtsSettingsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.PrivacyLockSettingsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.ProfileUiSettingsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.ReactionsSettingsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SearchEngineSettingsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SecurityFiltersScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SpammingUsersScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.UpdateZapAmountScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.UserSettingsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.VideoPlayerSettingsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.cordn.CordnBackupScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.cordn.CordnCoordinatorsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.cordn.CordnHubScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.cordn.CordnKeyPackagesScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.cordn.CordnLinkScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.cordn.CordnMigrateScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.nip46.Nip46ConnectedAppsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.nip46.Nip46SignerScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.routstr.RoutstrSetupScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.wot.WebOfTrustScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.shorts.ShortsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.softwareapps.SoftwareAppDetailScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.softwareapps.SoftwareAppsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.threadview.ThreadScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.url.UrlPostScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.url.UrlScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.video.VideoScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet.AddClinkDebitWalletScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet.AddNwcWalletScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet.AddWalletScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet.CashuMintRecommendationsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet.CashuMintsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet.CashuWalletScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet.CashuWalletSettingsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet.OnchainTransactionsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet.ReloadMintScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet.TopUpMintScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet.WalletDetailScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet.WalletReceiveScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet.WalletScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet.WalletSendScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet.WalletTransactionsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet.wizard.CashuWalletWizardScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.webBookmarks.WebBookmarksScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.workouts.NewWorkoutScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.workouts.WorkoutsScreen
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip34Git.review.GitCodeLocation

/**
 * Registers every destination whose screen is shared UI: the navigation table both front ends
 * render. A front end adds its own platform-only screens to the same [NavDestinations] after
 * calling this (Android: `appDestinations` in `AppNavigation.kt`).
 */
fun NavDestinations.sharedDestinations(accountViewModel: AccountViewModel) {
    composableCapped<Route.Home> { HomeScreen(accountViewModel, nav) }
    composable<Route.Message> { MessagesScreen(accountViewModel, nav) }
    composableArgs<Route.Video> { VideoScreen(accountViewModel, nav, it.attachments, it.message) }
    composableArgs<Route.Discover> { DiscoverScreen(it.initialTab, accountViewModel, nav) }
    composableArgs<Route.Notification> { NotificationScreen(it.scrollToEventId, accountViewModel, nav) }
    composableFromEnd<Route.Polls> { PollsScreen(accountViewModel, nav) }
    composableFromEnd<Route.Communities> { CommunitiesScreen(accountViewModel, nav) }
    composableFromEnd<Route.NewCommunity> { NewCommunityScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.EditCommunity> { EditCommunityScreen(Address(it.kind, it.pubKeyHex, it.dTag), accountViewModel, nav) }
    composableFromBottomArgs<Route.NewCalendarEvent> { NewCalendarEventScreen(nav, accountViewModel) }
    composableFromBottomArgs<Route.EditCalendarEvent> {
        NewCalendarEventScreen(nav, accountViewModel, editKind = it.kind, editPubKeyHex = it.pubKeyHex, editDTag = it.dTag)
    }
    composableFromEnd<Route.Nests> { NestsScreen(accountViewModel, nav) }
    composableFromEnd<Route.AccountBackup> { AccountBackupScreen(accountViewModel, nav) }
    composableFromEnd<Route.CordnBackup> { CordnBackupScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.AddToMusicPlaylist> { AddToMusicPlaylistSheet(trackAddress = it.trackAddress, accountViewModel = accountViewModel, nav = nav) }
    composableFromEnd<Route.DrssLists> { DrssListsScreen(accountViewModel, nav) }
    composableFromEnd<Route.DrssListen> { DrssListenScreen(accountViewModel, nav) }
    composableFromEnd<Route.DrssHistory> { DrssHistoryScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.DrssFollowInto> { DrssFollowIntoScreen(it.pubKeyHex, accountViewModel, nav) }
    composableFromEndArgs<Route.DrssSaveTo> { DrssSaveToScreen(it.articleAddress, accountViewModel, nav) }
    composableFromEndArgs<Route.DrssCollectTo> { DrssCollectToScreen(it.eventId, accountViewModel, nav) }
    composableFromEndArgs<Route.DrssRecordProvenance> { DrssRecordProvenanceScreen(it.articleAddress, accountViewModel, nav) }
    composableFromEndArgs<Route.DrssShowEdit> { DrssShowEditScreen(it.showAddress, accountViewModel, nav) }
    composableFromEnd<Route.Badges> { BadgesScreen(accountViewModel, nav) }
    composableFromEnd<Route.ProfileBadges> { ProfileBadgesScreen(accountViewModel, nav) }
    composableFromEnd<Route.ProfileAppRecommendations> { ProfileAppRecommendationsScreen(accountViewModel, nav) }
    composableFromBottomArgs<Route.AwardBadge> { AwardBadgeScreen(it.kind, it.pubKeyHex, it.dTag, accountViewModel, nav) }
    composableFromEndArgs<Route.Pictures> { PicturesScreen(accountViewModel, nav, it.attachments, it.message) }
    composableFromEnd<Route.Workouts> { WorkoutsScreen(accountViewModel, nav) }
    // The dashboard sizes its own columns; Explore caps its feed to the reading width itself.
    composableFromEnd<Route.GitRepositories>(capWidth = false) { GitRepositoriesScreen(accountViewModel, nav) }
    composableFromEnd<Route.NewGitRepository> { NewGitRepositoryScreen(accountViewModel, nav) }
    composableFromEnd<Route.Highlights> { HighlightsScreen(accountViewModel, nav) }
    composableFromEnd<Route.SoftwareApps> { SoftwareAppsScreen(accountViewModel, nav) }
    composableFromEnd<Route.Napplets> { NappletsScreen(accountViewModel, nav) }
    composableFromEnd<Route.Nsites> { NsitesScreen(accountViewModel, nav) }
    composableFromEnd<Route.Browser>(capWidth = false) { BrowserScreen(accountViewModel, nav) }
    composableFromEnd<Route.FavoriteApps> { FavoriteAppsScreen(accountViewModel, nav) }
    composableFromEnd<Route.ConnectedApps> { ConnectedAppsScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.Nip46Signer> { Nip46SignerScreen(accountViewModel, nav, it.connectUri) }
    composableFromEnd<Route.Nip46ConnectedApps> { Nip46ConnectedAppsScreen(accountViewModel, nav) }
    composableFromEnd<Route.RelayAuthSettings> { RelayAuthSettingsScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.SoftwareAppDetail> { SoftwareAppDetailScreen(Address(it.kind, it.pubKeyHex, it.dTag), accountViewModel, nav) }
    composableFromEnd<Route.Calendars> { CalendarsScreen(accountViewModel, nav) }
    composableFromEnd<Route.CalendarCollections> { CalendarCollectionsScreen(accountViewModel, nav) }
    composableFromEnd<Route.CalendarReminderSettings> { CalendarReminderSettingsScreen(nav) }
    composableFromEndArgs<Route.CalendarEventDetail> {
        CalendarEventDetailScreen(it.kind, it.pubKeyHex, it.dTag, accountViewModel, nav)
    }
    composableFromBottomArgs<Route.NewCalendarCollection> { NewCalendarCollectionScreen(nav, accountViewModel, it.dTag) }
    composableFromEnd<Route.Products> { ProductsScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.Geocaches> { GeocachesScreen(it.initialTab, accountViewModel, nav) }
    composableFromEndArgs<Route.GeocacheDetail> {
        GeocacheDetailScreen(it.kind, it.pubKeyHex, it.dTag, accountViewModel, nav)
    }
    composableFromEndArgs<Route.GeocacheHunt> {
        GeocacheHuntScreen(it.kind, it.pubKeyHex, it.dTag, accountViewModel, nav)
    }
    composableFromBottomArgs<Route.LogGeocacheFind> {
        LogGeocacheFindScreen(it.kind, it.pubKeyHex, it.dTag, accountViewModel, nav)
    }
    composableFromBottomArgs<Route.NewGeocache> {
        NewGeocacheScreen(nav, accountViewModel, prefillGeohash = it.geohash)
    }
    composableFromBottomArgs<Route.EditGeocache> {
        NewGeocacheScreen(nav, accountViewModel, editKind = it.kind, editPubKeyHex = it.pubKeyHex, editDTag = it.dTag)
    }
    composableFromBottomArgs<Route.NewGeocacheHunt> { NewGeocacheHuntScreen(nav, accountViewModel, seedCache = it.seedCache) }
    composableFromBottomArgs<Route.EditGeocacheHunt> {
        NewGeocacheHuntScreen(nav, accountViewModel, editKind = it.kind, editPubKeyHex = it.pubKeyHex, editDTag = it.dTag)
    }
    composableFromEndArgs<Route.Shorts> { ShortsScreen(accountViewModel, nav, it.attachments, it.message) }
    composableFromEnd<Route.PublicChats> { PublicChatsScreen(accountViewModel, nav) }
    composableFromEnd<Route.RelayGroups> { RelayGroupDiscoveryScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.BuzzDmList>(capWidth = false) { route ->
        ChatListDetailScreen(::isRelayGroupDetailRoute, accountViewModel, nav) { BuzzDmListScreen(route.relayUrl, accountViewModel, it) }
    }
    composableFromEndArgs<Route.BuzzNewDm> { BuzzNewDmScreen(it.relayUrl, accountViewModel, nav) }
    composableFromEnd<Route.FollowPacks> { FollowPacksScreen(accountViewModel, nav) }
    composableFromEnd<Route.LiveStreams> { LiveStreamsScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.AgentConsole> { AgentConsoleScreen(it.relayUrl, accountViewModel, nav) }
    composableFromEnd<Route.AgentAttestation> { AgentAttestationScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.AgentPersonaEdit> { AgentPersonaEditScreen(it.slug, accountViewModel, nav) }
    composableFromEndArgs<Route.NestLobby> { NestLobbyScreen(it.addressValue, accountViewModel, nav) }
    composableFromEnd<Route.Longs> { LongsScreen(accountViewModel, nav) }
    composableFromEnd<Route.Articles> { ArticlesScreen(accountViewModel, nav) }
    composableFromEnd<Route.MusicTracks> { MusicTracksScreen(accountViewModel, nav) }
    composableFromEnd<Route.MusicPlaylists> { MusicPlaylistsScreen(accountViewModel, nav) }
    composableFromEnd<Route.PodcastEpisodes> { PodcastEpisodesScreen(accountViewModel, nav) }
    composableFromEnd<Route.Podcasts> { PodcastsScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.Podcast> { PodcastScreen(it.pubkey, accountViewModel, nav) }
    composableFromEnd<Route.PodcastAuthoring> { PodcastAuthoringScreen(accountViewModel, nav) }
    composableFromEnd<Route.EditPodcastShow> { EditPodcastShowScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.NewPodcastEpisode> { NewPodcastEpisodeScreen(editDTag = it.dTag, accountViewModel = accountViewModel, nav = nav) }
    composableFromEnd<Route.NewPodcastTrailer> { NewPodcastTrailerScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.NewMusicTrack> { NewMusicTrackScreen(editDTag = it.dTag, accountViewModel = accountViewModel, nav = nav) }
    composableFromEndArgs<Route.NewMusicPlaylist> { NewMusicPlaylistScreen(editDTag = it.dTag, accountViewModel = accountViewModel, nav = nav) }
    composableCapped<Route.Chess> { ChessLobbyScreen(accountViewModel, nav) }
    composableFromEnd<Route.Wallet> { WalletScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.WalletSend> { WalletSendScreen(it.walletId, accountViewModel, nav) }
    composableFromEndArgs<Route.WalletReceive> { WalletReceiveScreen(it.walletId, accountViewModel, nav) }
    composableFromEndArgs<Route.WalletTransactions> { WalletTransactionsScreen(it.walletId, accountViewModel, nav) }
    composableFromEnd<Route.OnchainTransactions> { OnchainTransactionsScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.WalletDetail> { WalletDetailScreen(it.walletId, accountViewModel, nav) }
    composableFromEnd<Route.WalletAdd> { AddWalletScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.WalletAddNwc> { AddNwcWalletScreen(accountViewModel, nav, it.nip47) }
    composableFromEnd<Route.CashuWalletMints> { CashuMintsScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.WalletAddClinkDebit> { AddClinkDebitWalletScreen(accountViewModel, nav, it.ndebit) }
    composableFromEnd<Route.CashuWallet> { CashuWalletScreen(accountViewModel, nav) }
    composableFromEnd<Route.CashuWalletWizard> { CashuWalletWizardScreen(accountViewModel, nav) }
    composableFromEnd<Route.CashuWalletCreated> { CashuWalletCreatedScreen(nav) }
    composableFromEnd<Route.CashuWalletSettings> { CashuWalletSettingsScreen(accountViewModel, nav) }
    composableFromEnd<Route.CashuMintRecommendations> { CashuMintRecommendationsScreen(accountViewModel, nav) }
    composableFromBottomArgs<Route.SendPayment> { SendPaymentScreen(it.userHex, it.method, it.lnAddressOverride, it.btcAddressOverride, accountViewModel, nav) }
    composableFromEnd<Route.Lists> { ListOfPeopleListsScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.MyPeopleListView> { PeopleListScreen(it.dTag, accountViewModel, nav) }
    composableFromEndArgs<Route.MyFollowPackView> { FollowPackScreen(it.dTag, accountViewModel, nav) }
    composableFromBottomArgs<Route.PeopleListManagement> { FollowListAndPackAndUserScreen(it.userToAdd, accountViewModel, nav) }
    composableFromBottomArgs<Route.PeopleListMetadataEdit> { PeopleListMetadataScreen(it.dTag, accountViewModel, nav) }
    composableFromBottomArgs<Route.FollowPackMetadataEdit> { FollowPackMetadataScreen(it.dTag, accountViewModel, nav) }
    composableFromEnd<Route.BookmarkGroups> { ListOfBookmarkGroupsScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.BookmarkGroupView> { BookmarkGroupScreen(it.dTag, it.bookmarkType, accountViewModel, nav) }
    composableFromBottomArgs<Route.BookmarkGroupMetadataEdit> { BookmarkGroupMetadataScreen(it.dTag, accountViewModel, nav) }
    composableFromEnd<Route.InterestSets> { ListOfInterestSetsScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.InterestSetView> { InterestSetScreen(it.dTag, accountViewModel, nav) }
    composableFromBottomArgs<Route.InterestSetMetadataEdit> { InterestSetMetadataScreen(it.dTag, accountViewModel, nav) }
    composableFromBottomArgs<Route.PostBookmarkManagement> { PostBookmarkListManagementScreen(it.postId, accountViewModel, nav) }
    composableFromBottomArgs<Route.ArticleBookmarkManagement> { ArticleBookmarkListManagementScreen(Address(it.kind, it.pubKeyHex, it.dTag), accountViewModel, nav) }
    composableFromEnd<Route.EmojiPacks> { ListOfEmojiPacksScreen(accountViewModel, nav) }
    composableFromEnd<Route.MyEmojiList> { MyEmojiListScreen(accountViewModel, nav) }
    composableFromEnd<Route.BrowseEmojiSets> { BrowseEmojiSetsScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.EmojiPackView> { EmojiPackScreen(it.dTag, accountViewModel, nav) }
    composableFromBottomArgs<Route.EmojiPackMetadataEdit> { EmojiPackMetadataScreen(it.dTag, accountViewModel, nav) }
    composableFromBottomArgs<Route.EmojiPackSelection> { EmojiPackSelectionScreen(Address(it.kind, it.pubKeyHex, it.dTag), accountViewModel, nav) }
    composableFromBottomArgs<Route.QRDisplay> { ShowQRScreen(it.pubkey, accountViewModel, nav, it.startScanning) }
    composableFromBottomArgs<Route.ManualZapSplitPayment> { PayViaIntentScreen(it.paymentId, accountViewModel, nav) }
    composableFromBottomArgs<Route.ReloadMint> { ReloadMintScreen(it.requestId, accountViewModel, nav) }
    composableFromBottomArgs<Route.TopUpMint> { TopUpMintScreen(it.mintUrl, accountViewModel, nav) }
    composableFromBottomArgs<Route.EditProfile> { NewUserMetadataScreen(nav, accountViewModel) }
    composableCappedArgs<Route.Search> { SearchScreen(it.query, accountViewModel, nav) }
    composableFromEnd<Route.AllSettings> { AllSettingsScreen(accountViewModel, nav) }
    composableFromEnd<Route.SecurityFilters> { SecurityFiltersScreen(accountViewModel, nav) }
    composableFromEnd<Route.PrivacyLockSettings> { PrivacyLockSettingsScreen(nav) }
    composableFromEnd<Route.CustomFeeds> { CustomFeedsScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.EditCustomFeed> { EditCustomFeedScreen(it.id, accountViewModel, nav) }
    composableFromEnd<Route.WebOfTrust> { WebOfTrustScreen(accountViewModel, nav) }
    composableFromEnd<Route.BlockedUsers> { BlockedUsersScreen(accountViewModel, nav) }
    composableFromEnd<Route.SpammingUsers> { SpammingUsersScreen(accountViewModel, nav) }
    composableFromEnd<Route.HiddenWords> { HiddenWordsScreen(accountViewModel, nav) }
    composableFromEnd<Route.MutedThreads> { MutedThreadsScreen(accountViewModel, nav) }
    composableFromEnd<Route.PrivacyOptions> { PrivacyOptionsScreen(nav) }
    composableFromEnd<Route.NamecoinSettings> { NamecoinSettingsScreen(nav) }
    composableFromEnd<Route.OtsSettings> { OtsSettingsScreen(nav) }
    composableFromEnd<Route.Bookmarks> { BookmarkListScreen(accountViewModel, nav) }
    composableFromEnd<Route.OldBookmarks> { OldBookmarkListScreen(accountViewModel, nav) }
    composableFromEnd<Route.PinnedNotes> { PinnedNotesScreen(accountViewModel, nav) }
    composableFromEnd<Route.BookmarkedRepositories> { BookmarkedRepositoriesScreen(accountViewModel, nav) }
    composableFromEnd<Route.BookmarkedPodcasts> { BookmarkedPodcastsScreen(accountViewModel, nav) }
    composableFromEnd<Route.WebBookmarks> { WebBookmarksScreen(accountViewModel, nav) }
    composableFromEnd<Route.Drafts> { DraftListScreen(accountViewModel, nav) }
    composableFromEnd<Route.ScheduledPosts> { ScheduledPostsScreen(accountViewModel, nav) }
    composableFromEnd<Route.Settings> { SettingsScreen(accountViewModel, nav) }
    composableFromEnd<Route.ComposeSettings> { ComposeSettingsScreen(accountViewModel, nav) }
    composableFromEnd<Route.UserSettings> { UserSettingsScreen(accountViewModel, nav) }
    composableFromEnd<Route.ReactionsSettings> { ReactionsSettingsScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.UpdateReactionType> { UpdateReactionTypeScreen(accountViewModel, nav) }
    composableFromEnd<Route.MessagesSettings> { MessagesSettingsScreen(accountViewModel, nav) }
    composableFromEnd<Route.AccentColorSettings> { AccentColorSettingsScreen(accountViewModel, nav) }
    composableFromEnd<Route.AudioVisualizerSettings> { AudioVisualizerSettingsScreen(accountViewModel, nav) }
    composableFromEnd<Route.BottomBarSettings> { BottomBarSettingsScreen(accountViewModel, nav) }
    composableFromEnd<Route.DrawerSettings> { DrawerSettingsScreen(accountViewModel, nav) }
    composableFromEnd<Route.HomeTabsSettings> { HomeTabsSettingsScreen(accountViewModel, nav) }
    composableFromEnd<Route.ProfileUiSettings> { ProfileUiSettingsScreen(accountViewModel, nav) }
    composableFromEnd<Route.SearchEngineSettings> { SearchEngineSettingsScreen(accountViewModel, nav) }
    composableFromEnd<Route.AgentKeysSettings> { AgentKeysSettingsScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.RoutstrSetup> { RoutstrSetupScreen(it, accountViewModel, nav) }
    // The paper's own HTML sets its measure: the screen gets the whole pane (see PanelFreeRoutes).
    composableFromEndArgs<Route.FrontPage>(capWidth = false) { FrontPageScreen(accountViewModel, nav, it.day) }
    composableFromEnd<Route.VideoPlayerSettings> { VideoPlayerSettingsScreen(accountViewModel, nav) }
    composableFromEnd<Route.CallSettings> { CallSettingsScreen(accountViewModel, nav) }
    composableFromEnd<Route.NotificationSettings> { NotificationSettingsScreen(accountViewModel, nav) }
    composableFromEnd<Route.ImportFollowsSelectUser> { ImportFollowListSelectUserScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.ImportFollowsPickFollows> {
        ImportFollowListPickFollowsScreen(it.userHex, accountViewModel, nav)
    }
    composableFromEndArgs<Route.Nip47NWCSetup> { NIP47SetupScreen(accountViewModel, nav, it.nip47) }
    composableFromEndArgs<Route.UpdateZapAmount> { UpdateZapAmountScreen(accountViewModel, nav, it.nip47) }
    composableFromEndArgs<Route.EditRelays> { AllRelayListScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.ActiveSubscriptions> { ActiveSubscriptionsScreen(accountViewModel, nav) }
    composableFromEnd<Route.EventSync> { EventSyncScreen(accountViewModel, nav) }
    composableFromEnd<Route.DevTools> { DevToolsHubScreen(nav) }
    composableFromEndArgs<Route.DevToolsNip19> { Nip19ToolScreen(it.input, accountViewModel, nav) }
    composableFromEndArgs<Route.DevToolsEventInspector> { EventInspectorScreen(it.noteKey, nav) }
    composableFromEnd<Route.DevToolsKinds> { KindBrowserScreen(nav) }
    composableFromEndArgs<Route.DevToolsKindUsage> { KindLookupScreen(it.kind, accountViewModel, nav) }
    composableFromEndArgs<Route.DevToolsEventFinder> { EventFinderScreen(it.eventId, accountViewModel, nav) }
    composableFromEndArgs<Route.DevToolsRelayConsole>(capWidth = false) { RelayConsoleScreen(it.url, accountViewModel, nav) }
    composableFromEnd<Route.RequestToVanish> { RequestToVanishScreen(accountViewModel, nav) }
    composableFromEnd<Route.VanishEvents> { VanishEventsScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.EditMediaServers> { AllMediaServersScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.ManageBlossomBlobs> { BlossomBlobManagerScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.ImportBlossomBlobs> { BlossomImportScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.EditNestsServers> { NestsServersScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.CordnLink> { CordnLinkScreen(accountViewModel, nav, it.link) }
    composableFromEnd<Route.CordnCoordinators> { CordnCoordinatorsScreen(accountViewModel, nav) }
    composableFromEnd<Route.CordnKeyPackages> { CordnKeyPackagesScreen(accountViewModel, nav) }
    composableFromEnd<Route.CordnHub> { CordnHubScreen(accountViewModel, nav) }
    composableFromEnd<Route.CordnMigrate> { CordnMigrateScreen(accountViewModel, nav) }
    composableFromEnd<Route.EditFavoriteAlgoFeeds> { FavoriteAlgoFeedsListScreen(accountViewModel, nav) }
    composableFromEnd<Route.EditPaymentTargets> { PaymentTargetsScreen(accountViewModel, nav) }
    composableFromEnd<Route.EditBolt12Offers> { Bolt12OffersScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.ContentDiscovery> { DvmContentDiscoveryScreen(it.id, accountViewModel, nav) }
    composableFromEndArgs<Route.Profile> { ProfileScreen(it.id, accountViewModel, nav) }
    composableFromEndArgs<Route.Note> { ThreadScreen(it.id, accountViewModel, nav) }
    composableFromEndArgs<Route.Nip> { OfficialNipScreen(it.number, it.anchor, accountViewModel, nav) }
    composableFromEndArgs<Route.EventKind> { EventKindScreen(it.kind, accountViewModel, nav) }
    composableFromEnd<Route.Nips> { NipsScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.ShareNoteAsQr> { ShareNoteAsQrScreen(it.id, accountViewModel, nav) }
    composableFromEndArgs<Route.ShareNoteAsImage> { ShareNoteAsImageScreen(it.id, accountViewModel, nav) }
    composableFromEndArgs<Route.ShareNoteAsImageFile> { ShareNoteAsImageFileScreen(it.id, accountViewModel, nav) }
    composableFromEndArgs<Route.ContactListUsers> { ContactListUsersScreen(it.noteId, accountViewModel, nav) }
    composableFromEndArgs<Route.RelayListMembers> { RelayListMembersScreen(it.noteId, accountViewModel, nav) }
    composableFromEndArgs<Route.PollResults> { PollResultsScreen(it.noteId, accountViewModel, nav) }
    composableFromEndArgs<Route.Hashtag> { HashtagScreen(it, accountViewModel, nav) }
    composableFromEndArgs<Route.Geohash> { GeoHashScreen(it, accountViewModel, nav) }
    composableFromEndArgs<Route.Url> { UrlScreen(it, accountViewModel, nav) }
    composableFromEndArgs<Route.RelayFeed> { RelayFeedScreen(it, accountViewModel, nav) }
    composableFromEndArgs<Route.ChessGame>(capWidth = false) { ChessGameScreen(it.gameId, accountViewModel, nav) }
    composableFromEndArgs<Route.RelayInfo> { RelayInformationScreen(it.url, accountViewModel, nav) }
    composableFromEndArgs<Route.BackupConflictReview> { BackupConflictReviewScreen(it.slot, accountViewModel, nav) }
    composableFromEndArgs<Route.RelayManagement> { RelayManagementScreen(it.url, accountViewModel, nav) }
    composableFromEndArgs<Route.RelayMembers> { RelayMembersScreen(it.url, accountViewModel, nav) }
    composableFromEndArgs<Route.Community> { CommunityScreen(Address(it.kind, it.pubKeyHex, it.dTag), accountViewModel, nav) }
    composableFromEndArgs<Route.FollowPack> { FollowPackFeedScreen(Address(it.kind, it.pubKeyHex, it.dTag), accountViewModel, nav) }
    composableFromEndArgs<Route.Room> { ChatroomScreen(it.toKey(), it.message, it.attachment, it.replyId, it.draftId, it.expiresDays, accountViewModel, nav) }
    composableFromEndArgs<Route.RoomByAuthor> { ChatroomByAuthorScreen(it.id, null, accountViewModel, nav) }
    composableFromEnd<Route.MarmotGroupList>(capWidth = false) {
        ChatListDetailScreen(::isMarmotDetailRoute, accountViewModel, nav) { MarmotGroupListScreen(accountViewModel, it) }
    }
    composableFromEndArgs<Route.MarmotGroupChat> {
        MarmotGroupChatScreen(
            nostrGroupId = it.nostrGroupId,
            draftMessage = it.message,
            replyToInnerNote = it.replyId,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
    composableFromEndArgs<Route.MarmotGroupInfo> { MarmotGroupInfoScreen(it.nostrGroupId, accountViewModel, nav) }
    composableFromEndArgs<Route.CordnGroupInfo> {
        CordnGroupInfoScreen(it.coordinatorPubKey, it.gid, accountViewModel, nav)
    }
    composableFromEnd<Route.CordnGroupList>(capWidth = false) {
        ChatListDetailScreen(::isPlatformChatDetailRoute, accountViewModel, nav) { CordnGroupListScreen(accountViewModel, it) }
    }
    composableFromBottom<Route.CordnCreateGroup> { CordnCreateGroupScreen(accountViewModel, nav) }
    composableFromBottom<Route.CordnCreateGroupMembers> { CordnCreateMembersScreen(accountViewModel, nav) }
    composableFromEnd<Route.CordnInvitations> { CordnInvitationsScreen(accountViewModel, nav) }
    composableFromBottom<Route.CreateMarmotGroup> { CreateGroupScreen(accountViewModel, nav) }
    composableFromBottomArgs<Route.MarmotGroupEditInfo> { EditGroupInfoScreen(it.nostrGroupId, accountViewModel, nav) }
    composableFromEndArgs<Route.PublicChatChannel> {
        PublicChatChannelScreen(it.id, it.draftId, it.replyTo, accountViewModel, nav)
    }
    composableFromEndArgs<Route.LiveActivityChannel> {
        LiveActivityChannelScreen(
            Address(it.kind, it.pubKeyHex, it.dTag),
            draftId = it.draftId,
            replyToId = it.replyTo,
            accountViewModel,
            nav,
        )
    }
    composableFromEndArgs<Route.EphemeralChat> {
        EphemeralChatScreen(
            id = it.id,
            relayUrl = it.relayUrl,
            draftId = it.draftId,
            replyToId = it.replyTo,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
    composableFromEndArgs<Route.GeohashChat> {
        GeohashChatScreen(
            geohash = it.geohash,
            teleported = it.teleported,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
    composableFromEnd<Route.GeohashChats>(capWidth = false) {
        ChatListDetailScreen(::isGeohashDetailRoute, accountViewModel, nav) { GeohashChatsScreen(accountViewModel, it) }
    }
    composableFromBottomArgs<Route.NewGeohashChat> { NewGeohashChatScreen(accountViewModel, nav) }
    composableFromBottomArgs<Route.GeohashTeleport> { GeohashTeleportScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.RelayGroup> {
        RelayGroupChatScreen(
            id = it.id,
            relayUrl = it.relayUrl,
            draftId = it.draftId,
            replyToId = it.replyTo,
            inviteCode = it.inviteCode,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
    composableFromEndArgs<Route.RelayGroupServer>(capWidth = false) { route ->
        ChatListDetailScreen(::isRelayGroupDetailRoute, accountViewModel, nav) {
            RelayGroupChannelListScreen(
                relayUrl = route.relayUrl,
                accountViewModel = accountViewModel,
                nav = it,
            )
        }
    }
    composableFromEndArgs<Route.Concord> {
        ConcordChannelScreen(
            communityId = it.communityId,
            channelId = it.channelId,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
    composableFromEndArgs<Route.ChatMinichat> {
        MinichatScreen(
            rootId = it.rootId,
            concordCommunityId = it.concordCommunityId,
            concordChannelId = it.concordChannelId,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
    composableFromEndArgs<Route.ConcordServer>(capWidth = false) { route ->
        ChatListDetailScreen(::isConcordDetailRoute, accountViewModel, nav) {
            ConcordChannelListScreen(
                communityId = route.communityId,
                accountViewModel = accountViewModel,
                nav = it,
            )
        }
    }
    composableFromEndArgs<Route.ConcordMembers> {
        ConcordMembersScreen(
            communityId = it.communityId,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
    composableFromEndArgs<Route.ConcordInviteLinks> {
        ConcordInviteLinksScreen(
            communityId = it.communityId,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
    composableFromEndArgs<Route.ConcordEdit> {
        ConcordEditScreen(
            communityId = it.communityId,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
    composableFromEndArgs<Route.ConcordInvite> {
        ConcordInviteScreen(
            link = it.link,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
    composableFromEndArgs<Route.BuzzInvite> { BuzzInviteScreen(it.link, accountViewModel, nav) }
    composableFromEnd<Route.Concords> { ConcordHomeScreen(accountViewModel, nav) }
    composableFromEnd<Route.ConcordCreate> { ConcordCreateScreen(accountViewModel, nav) }
    composableFromEndArgs<Route.RelayGroupMembers> {
        RelayGroupMembersScreen(
            id = it.id,
            relayUrl = it.relayUrl,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
    composableFromEndArgs<Route.RelayGroupThreads> {
        RelayGroupThreadsScreen(
            id = it.id,
            relayUrl = it.relayUrl,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
    composableFromEndArgs<Route.BuzzCanvas> { BuzzCanvasScreen(it.channelId, it.relayUrl, accountViewModel, nav) }
    composableFromEndArgs<Route.BuzzJobBoard> { JobBoardScreen(it.channelId, it.relayUrl, accountViewModel, nav) }
    composableFromEndArgs<Route.BuzzWorkflowBoard> { WorkflowRunBoardScreen(it.channelId, it.relayUrl, accountViewModel, nav) }
    composableFromEndArgs<Route.BuzzAgentWork> { AgentWorkBoardScreen(it.channelId, it.relayUrl, accountViewModel, nav) }
    composableFromBottomArgs<Route.BuzzForumPost> { BuzzForumPostScreen(it.channelId, it.relayUrl, accountViewModel, nav) }
    composableFromEndArgs<Route.BuzzForumThread> { BuzzForumThreadScreen(it.channelId, it.relayUrl, it.rootId, accountViewModel, nav) }
    composableFromEndArgs<Route.RelayGroupCreate> {
        RelayGroupCreateScreen(
            relayUrl = it.relayUrl,
            isForum = it.isForum,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
    composableFromEndArgs<Route.RelayGroupEdit> {
        RelayGroupEditScreen(
            id = it.id,
            relayUrl = it.relayUrl,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
    composableFromEndArgs<Route.RelayGroupBrowse> {
        RelayGroupBrowseScreen(
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
    composableFromBottomArgs<Route.ChannelMetadataEdit> { ChannelMetadataScreen(it.id, accountViewModel, nav) }
    composableFromBottomArgs<Route.NewEphemeralChat> { NewEphemeralChatScreen(accountViewModel, nav) }
    composableFromBottom<Route.NewConversation> { NewConversationScreen(nav) }
    composableFromBottomArgs<Route.NewGroupDM> { NewGroupDMScreen(it.message, it.attachment, accountViewModel, nav) }
    composableFromBottomArgs<Route.ShareToDM> { ShareToDMScreen(it.message, it.attachment, accountViewModel, nav) }
    composableArgs<Route.EventRedirect> { LoadRedirectScreen(it.id, it.isPrivate, accountViewModel, nav) }
    composableFromBottomArgs<Route.GeoPost> {
        GeoHashPostScreen(
            geohash = it.geohash,
            message = it.message,
            attachment = it.attachment,
            replyId = it.replyTo,
            quoteId = it.quote,
            draftId = it.draft,
            accountViewModel,
            nav,
        )
    }
    composableFromBottomArgs<Route.NewPublicMessage> {
        NewPublicMessageScreen(
            to = it.toKey(),
            replyId = it.replyId,
            draftId = it.draftId,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
    composableFromBottom<Route.NewGoal> {
        NewGoalScreen(
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
    composableFromBottomArgs<Route.NewWorkout> {
        NewWorkoutScreen(
            prefill = it,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
    composableFromBottomArgs<Route.HashtagPost> {
        HashtagPostScreen(
            hashtag = it.hashtag,
            message = it.message,
            attachment = it.attachment,
            replyId = it.replyTo,
            quoteId = it.quote,
            draftId = it.draft,
            accountViewModel,
            nav,
        )
    }
    composableFromBottomArgs<Route.UrlPost> {
        UrlPostScreen(
            url = it.url,
            message = it.message,
            attachment = it.attachment,
            replyId = it.replyTo,
            quoteId = it.quote,
            draftId = it.draft,
            accountViewModel,
            nav,
        )
    }
    composableFromBottomArgs<Route.GenericCommentPost> {
        ReplyCommentPostScreen(
            replyId = it.replyTo,
            message = it.message,
            attachment = it.attachment,
            quoteId = it.quote,
            draftId = it.draft,
            reviewLocation = it.reviewPath?.let { path -> GitCodeLocation(path, it.reviewCommit, it.reviewLine, it.reviewLine, it.reviewRemovedSide) },
            accountViewModel,
            nav,
        )
    }
    composableFromBottomArgs<Route.NewProduct> {
        NewProductScreen(
            message = it.message,
            attachment = it.attachment,
            quoteId = it.quote,
            draftId = it.draft,
            accountViewModel,
            nav,
        )
    }
    composableFromBottomArgs<Route.NewLongFormPost> {
        LongFormPostScreen(
            draftId = it.draft,
            versionId = it.version,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
    composableFromBottomArgs<Route.NewShortNote> {
        ShortNotePostScreen(
            message = it.message,
            attachment = it.attachment,
            baseReplyToId = it.baseReplyTo,
            quoteId = it.quote,
            forkId = it.fork,
            versionId = it.version,
            draftId = it.draft,
            groupThreadId = it.groupThreadId,
            groupThreadRelayUrl = it.groupThreadRelayUrl,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
    composableFromBottomArgs<Route.NewPoll> {
        PollPostScreen(
            message = it.message,
            draftId = it.draft,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
    composableFromBottomArgs<Route.NewHighlight> {
        NewHighlightScreen(
            quote = it.quote,
            url = it.url,
            prefix = it.prefix,
            suffix = it.suffix,
            comment = it.comment,
            context = it.context,
            sourceAddress = it.sourceAddress,
            sourceEventId = it.sourceEventId,
            author = it.author,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
    composableFromBottomArgs<Route.VoiceReply> {
        VoiceReplyScreen(
            replyToNoteId = it.replyToNoteId,
            recordingFilePath = it.recordingFilePath,
            mimeType = it.mimeType,
            duration = it.duration,
            amplitudesJson = it.amplitudes,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}
