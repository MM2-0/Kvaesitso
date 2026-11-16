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
package com.vitorpamplona.amethyst.commons.model

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.actions.ConcordActions
import com.vitorpamplona.amethyst.commons.actions.DeletionActions
import com.vitorpamplona.amethyst.commons.actions.EmojiPackActions
import com.vitorpamplona.amethyst.commons.audio.VisualizerStyle
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.InMemoryNip46ClientStore
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46ClientStore
import com.vitorpamplona.amethyst.commons.connectedApps.signers.InMemoryNostrSignerPermissionStore
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionLedger
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionStore
import com.vitorpamplona.amethyst.commons.defaults.Constants
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.EventProcessor
import com.vitorpamplona.amethyst.commons.model.IAccount
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.VideoPostKind
import com.vitorpamplona.amethyst.commons.model.algoFeeds.FavoriteAlgoFeedsOrchestrator
import com.vitorpamplona.amethyst.commons.model.backups.BackupRestore
import com.vitorpamplona.amethyst.commons.model.backups.ReplaceableBackupConflict
import com.vitorpamplona.amethyst.commons.model.bolt12Offers.Bolt12OfferListState
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzChannelStars
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzHeldAttestations
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzWorkspaces
import com.vitorpamplona.amethyst.commons.model.buzz.ChannelInvitesState
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.filter
import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedToggles
import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedType
import com.vitorpamplona.amethyst.commons.model.composer.NewMessageTagger
import com.vitorpamplona.amethyst.commons.model.concord.ConcordChannel
import com.vitorpamplona.amethyst.commons.model.concord.ConcordChannelListState
import com.vitorpamplona.amethyst.commons.model.concord.ConcordSessionManager
import com.vitorpamplona.amethyst.commons.model.cordn.CordnRuntime
import com.vitorpamplona.amethyst.commons.model.drss.DrssState
import com.vitorpamplona.amethyst.commons.model.edits.PrivateStorageRelayListDecryptionCache
import com.vitorpamplona.amethyst.commons.model.edits.PrivateStorageRelayListState
import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatChannel
import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatListDecryptionCache
import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatListState
import com.vitorpamplona.amethyst.commons.model.localRelays.ForwardKind0ToLocalRelayState
import com.vitorpamplona.amethyst.commons.model.localRelays.LocalRelayListState
import com.vitorpamplona.amethyst.commons.model.location.LocationResult
import com.vitorpamplona.amethyst.commons.model.marmot.KeyPackageRelayListState
import com.vitorpamplona.amethyst.commons.model.marmot.MarmotGroupNotifier
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotGroupList
import com.vitorpamplona.amethyst.commons.model.marmotGroups.syncMetadataTo
import com.vitorpamplona.amethyst.commons.model.navigation.BottomBarEntry
import com.vitorpamplona.amethyst.commons.model.navigation.NavBarItem
import com.vitorpamplona.amethyst.commons.model.nip01UserMetadata.AccountHomeRelayState
import com.vitorpamplona.amethyst.commons.model.nip01UserMetadata.AccountMineRelayState
import com.vitorpamplona.amethyst.commons.model.nip01UserMetadata.AccountOutboxRelayState
import com.vitorpamplona.amethyst.commons.model.nip01UserMetadata.NotificationInboxRelayState
import com.vitorpamplona.amethyst.commons.model.nip01UserMetadata.UserMetadataState
import com.vitorpamplona.amethyst.commons.model.nip02FollowLists.DeclaredFollowsPerOutboxRelay
import com.vitorpamplona.amethyst.commons.model.nip02FollowLists.DeclaredFollowsPerUsingRelay
import com.vitorpamplona.amethyst.commons.model.nip02FollowLists.FollowListOutboxOrProxyRelays
import com.vitorpamplona.amethyst.commons.model.nip02FollowLists.FollowListReusedOutboxOrProxyRelays
import com.vitorpamplona.amethyst.commons.model.nip02FollowLists.FollowsPerOutboxRelay
import com.vitorpamplona.amethyst.commons.model.nip02FollowLists.Kind3FollowListState
import com.vitorpamplona.amethyst.commons.model.nip03Timestamp.OtsState
import com.vitorpamplona.amethyst.commons.model.nip17Dms.DmInboxRelayState
import com.vitorpamplona.amethyst.commons.model.nip17Dms.DmRelayListState
import com.vitorpamplona.amethyst.commons.model.nip18Reposts.RepostAction
import com.vitorpamplona.amethyst.commons.model.nip25Reactions.ReactionAction
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatListDecryptionCache
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatListState
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupListDecryptionCache
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupListState
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.OwnedEmojiPacksState
import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.UserStatusAction
import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.nowPlaying.NowPlaying
import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.nowPlaying.NowPlayingSettingsState
import com.vitorpamplona.amethyst.commons.model.nip46Signer.Nip46ConsentPrompter
import com.vitorpamplona.amethyst.commons.model.nip46Signer.Nip46SignerState
import com.vitorpamplona.amethyst.commons.model.nip46Signer.acceptsSignerTraffic
import com.vitorpamplona.amethyst.commons.model.nip47WalletConnect.NwcBackupState
import com.vitorpamplona.amethyst.commons.model.nip47WalletConnect.NwcInfoCache
import com.vitorpamplona.amethyst.commons.model.nip47WalletConnect.NwcSignerState
import com.vitorpamplona.amethyst.commons.model.nip51Lists.BookmarkListState
import com.vitorpamplona.amethyst.commons.model.nip51Lists.GitRepositoryListState
import com.vitorpamplona.amethyst.commons.model.nip51Lists.HiddenUsersState
import com.vitorpamplona.amethyst.commons.model.nip51Lists.OldBookmarkListState
import com.vitorpamplona.amethyst.commons.model.nip51Lists.PinListState
import com.vitorpamplona.amethyst.commons.model.nip51Lists.blockPeopleList.BlockPeopleListState
import com.vitorpamplona.amethyst.commons.model.nip51Lists.blockedRelays.BlockedRelayListDecryptionCache
import com.vitorpamplona.amethyst.commons.model.nip51Lists.blockedRelays.BlockedRelayListState
import com.vitorpamplona.amethyst.commons.model.nip51Lists.bookmarkSets.BookmarkSetsState
import com.vitorpamplona.amethyst.commons.model.nip51Lists.broadcastRelays.BroadcastRelayListDecryptionCache
import com.vitorpamplona.amethyst.commons.model.nip51Lists.broadcastRelays.BroadcastRelayListState
import com.vitorpamplona.amethyst.commons.model.nip51Lists.favoriteAlgoFeedsLists.FavoriteAlgoFeedsListDecryptionCache
import com.vitorpamplona.amethyst.commons.model.nip51Lists.favoriteAlgoFeedsLists.FavoriteAlgoFeedsListState
import com.vitorpamplona.amethyst.commons.model.nip51Lists.favoriteFollowSetsLists.FavoriteFollowSetsListDecryptionCache
import com.vitorpamplona.amethyst.commons.model.nip51Lists.favoriteFollowSetsLists.FavoriteFollowSetsListState
import com.vitorpamplona.amethyst.commons.model.nip51Lists.favoriteRelays.FavoriteRelayListDecryptionCache
import com.vitorpamplona.amethyst.commons.model.nip51Lists.favoriteRelays.FavoriteRelayListState
import com.vitorpamplona.amethyst.commons.model.nip51Lists.followSets.FollowSetDecryptionCache
import com.vitorpamplona.amethyst.commons.model.nip51Lists.followSets.FollowSetsState
import com.vitorpamplona.amethyst.commons.model.nip51Lists.followSets.StarterPacksState
import com.vitorpamplona.amethyst.commons.model.nip51Lists.geohashLists.GeohashListDecryptionCache
import com.vitorpamplona.amethyst.commons.model.nip51Lists.geohashLists.GeohashListState
import com.vitorpamplona.amethyst.commons.model.nip51Lists.indexerRelays.IndexerRelayListDecryptionCache
import com.vitorpamplona.amethyst.commons.model.nip51Lists.indexerRelays.IndexerRelayListState
import com.vitorpamplona.amethyst.commons.model.nip51Lists.interestLists.InterestListDecryptionCache
import com.vitorpamplona.amethyst.commons.model.nip51Lists.interestLists.InterestListState
import com.vitorpamplona.amethyst.commons.model.nip51Lists.interestSets.InterestSetsState
import com.vitorpamplona.amethyst.commons.model.nip51Lists.muteList.MuteListDecryptionCache
import com.vitorpamplona.amethyst.commons.model.nip51Lists.muteList.MuteListState
import com.vitorpamplona.amethyst.commons.model.nip51Lists.proxyRelays.ProxyRelayListDecryptionCache
import com.vitorpamplona.amethyst.commons.model.nip51Lists.proxyRelays.ProxyRelayListState
import com.vitorpamplona.amethyst.commons.model.nip51Lists.searchRelays.SearchRelayListDecryptionCache
import com.vitorpamplona.amethyst.commons.model.nip51Lists.searchRelays.SearchRelayListState
import com.vitorpamplona.amethyst.commons.model.nip51Lists.trustedRelays.TrustedRelayListDecryptionCache
import com.vitorpamplona.amethyst.commons.model.nip51Lists.trustedRelays.TrustedRelayListState
import com.vitorpamplona.amethyst.commons.model.nip53NestsServers.NestsServerListState
import com.vitorpamplona.amethyst.commons.model.nip56Reports.ReportAction
import com.vitorpamplona.amethyst.commons.model.nip60Cashu.CashuMintDirectoryState
import com.vitorpamplona.amethyst.commons.model.nip60Cashu.CashuWalletState
import com.vitorpamplona.amethyst.commons.model.nip62Vanish.VanishRequestsState
import com.vitorpamplona.amethyst.commons.model.nip65RelayList.Nip65RelayListState
import com.vitorpamplona.amethyst.commons.model.nip72Communities.CommunityListDecryptionCache
import com.vitorpamplona.amethyst.commons.model.nip72Communities.CommunityListState
import com.vitorpamplona.amethyst.commons.model.nip78AppSpecific.AppSpecificState
import com.vitorpamplona.amethyst.commons.model.nip85TrustedAssertions.TrustProviderListDecryptionCache
import com.vitorpamplona.amethyst.commons.model.nip85TrustedAssertions.UserAssertionDecryptionCache
import com.vitorpamplona.amethyst.commons.model.nip85TrustedAssertions.UserAssertionsState
import com.vitorpamplona.amethyst.commons.model.nip89AppHandlers.AppRecommendationsState
import com.vitorpamplona.amethyst.commons.model.nipA3PaymentTargets.NipA3PaymentTargetsState
import com.vitorpamplona.amethyst.commons.model.nipB7Blossom.BlossomServerListState
import com.vitorpamplona.amethyst.commons.model.nipBCOnchainZaps.OnchainWalletState
import com.vitorpamplona.amethyst.commons.model.nipFAAgentKeys.AgentKeyEditKind
import com.vitorpamplona.amethyst.commons.model.nipFAAgentKeys.AgentKeyEditor
import com.vitorpamplona.amethyst.commons.model.nipFAAgentKeys.AgentKeyState
import com.vitorpamplona.amethyst.commons.model.nipFAAgentKeys.fetchOwnRelayList
import com.vitorpamplona.amethyst.commons.model.nipFAAgentKeys.readAgentKeysForRewrite
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.ObserverScheduleState
import com.vitorpamplona.amethyst.commons.model.privateChatLastReadRoute
import com.vitorpamplona.amethyst.commons.model.privateChats.Chatroom
import com.vitorpamplona.amethyst.commons.model.privateChats.hasEncryptedContent
import com.vitorpamplona.amethyst.commons.model.rooms.MessageRoom
import com.vitorpamplona.amethyst.commons.model.rooms.isInRoom
import com.vitorpamplona.amethyst.commons.model.serverList.AssumedRelayListsState
import com.vitorpamplona.amethyst.commons.model.serverList.MergedFollowListsState
import com.vitorpamplona.amethyst.commons.model.serverList.MergedFollowPlusMineRelayListsState
import com.vitorpamplona.amethyst.commons.model.serverList.MergedFollowPlusMineWithIndexRelayListsState
import com.vitorpamplona.amethyst.commons.model.serverList.MergedFollowPlusMineWithSearchRelayListsState
import com.vitorpamplona.amethyst.commons.model.serverList.TrustedRelayListsState
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.FeedDecryptionCaches
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.FeedTopNavFilterState
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.OutboxLoaderState
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.SelectedListAuthorsState
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.amethyst.commons.model.trustedAssertions.TrustProviderListState
import com.vitorpamplona.amethyst.commons.model.trustedAssertions.TrustProviderRow
import com.vitorpamplona.amethyst.commons.model.trustedAssertions.publicRows
import com.vitorpamplona.amethyst.commons.nipACWebRtcCalls.CallManager
import com.vitorpamplona.amethyst.commons.nipACWebRtcCalls.CallState
import com.vitorpamplona.amethyst.commons.relayClient.auth.InMemoryRelayAuthPermissionStore
import com.vitorpamplona.amethyst.commons.relayClient.auth.RelayAuthPermissionCache
import com.vitorpamplona.amethyst.commons.relayClient.auth.RelayAuthPermissionLedger
import com.vitorpamplona.amethyst.commons.relayClient.auth.RelayAuthSessionGrants
import com.vitorpamplona.amethyst.commons.relayClient.auth.RelayAuthVenues
import com.vitorpamplona.amethyst.commons.relayClient.chatDelivery.ChatDeliveryTracker
import com.vitorpamplona.amethyst.commons.relayClient.nip47WalletConnect.NWCPaymentFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.notify.NotifyRequestsCache
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayClient.user.UserFinderAccount
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthCustomToggles
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPermissionStore
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPolicy
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.service.http.EncryptionKeyCache
import com.vitorpamplona.amethyst.commons.service.pow.PersistedPoWJob
import com.vitorpamplona.amethyst.commons.service.pow.PoWCategory
import com.vitorpamplona.amethyst.commons.service.pow.PoWPolicy
import com.vitorpamplona.amethyst.commons.service.pow.PoWPublishQueue
import com.vitorpamplona.amethyst.commons.service.pow.PoWReplay
import com.vitorpamplona.amethyst.commons.service.upload.FileHeader
import com.vitorpamplona.amethyst.commons.util.logTime
import com.vitorpamplona.amethyst.commons.viewmodels.ReplyMode
import com.vitorpamplona.amethyst.commons.wot.network.RelayTrustNetworkSource
import com.vitorpamplona.amethyst.commons.wot.network.TrustNetworkState
import com.vitorpamplona.amethyst.commons.wot.network.TrustNetworkStore
import com.vitorpamplona.amethyst.commons.wot.network.TrustVerdicts
import com.vitorpamplona.cordn.client.CordnBlobCipher
import com.vitorpamplona.crypto.aes.AESGCM
import com.vitorpamplona.crypto.aes.AES_GCM_NAME
import com.vitorpamplona.crypto.random.RandomInstance
import com.vitorpamplona.marmot.appComponents.agentTextStream.transport.MarmotQuicTransport
import com.vitorpamplona.marmot.client.MarmotManager
import com.vitorpamplona.marmot.client.MarmotPublisher
import com.vitorpamplona.marmot.client.MarmotPushCoordinator
import com.vitorpamplona.marmot.groups.MlsGroupStateStore
import com.vitorpamplona.quartz.buzz.media.BuzzImeta
import com.vitorpamplona.quartz.buzz.threading.buzzThread
import com.vitorpamplona.quartz.buzz.threading.buzzThreadRootForReplyTo
import com.vitorpamplona.quartz.concord.cord03Channels.ChannelChat
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelId
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChatEditEvent
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordDisappearing
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordTimerNoticeEvent
import com.vitorpamplona.quartz.experimental.bounties.BountyAddValueEvent
import com.vitorpamplona.quartz.experimental.edits.TextNoteModificationEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryBaseEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryPrologueEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryReadingStateEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStorySceneEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.image
import com.vitorpamplona.quartz.experimental.interactiveStories.summary
import com.vitorpamplona.quartz.experimental.interactiveStories.tags.StoryOptionTag
import com.vitorpamplona.quartz.experimental.profileGallery.ProfileGalleryEntryEvent
import com.vitorpamplona.quartz.experimental.profileGallery.blurhash
import com.vitorpamplona.quartz.experimental.profileGallery.dimension
import com.vitorpamplona.quartz.experimental.profileGallery.fromEvent
import com.vitorpamplona.quartz.experimental.profileGallery.hash
import com.vitorpamplona.quartz.experimental.profileGallery.image
import com.vitorpamplona.quartz.experimental.profileGallery.mimeType
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllWithHooks
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchFirst
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.relay.client.paging.RelayLoadingCursors
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrlOrNull
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hasMoreHashtagsThan
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTags
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUserIds
import com.vitorpamplona.quartz.nip01Core.tags.references.references
import com.vitorpamplona.quartz.nip03Timestamp.OtsResolver
import com.vitorpamplona.quartz.nip04Dm.PrivateDMCache
import com.vitorpamplona.quartz.nip04Dm.messages.EncryptedDmEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findNostrUris
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import com.vitorpamplona.quartz.nip10Notes.threadRootIdOrSelf
import com.vitorpamplona.quartz.nip13Pow.miner.PoWMiner
import com.vitorpamplona.quartz.nip13Pow.signer.PoWNostrSigner
import com.vitorpamplona.quartz.nip17Dm.NIP17Factory
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable
import com.vitorpamplona.quartz.nip17Dm.base.NIP17Group
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip18Reposts.quotes.quotes
import com.vitorpamplona.quartz.nip19Bech32.entities.Entity
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NRelay
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip22Comments.notify
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip29RelayGroups.hTag
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.previous
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip30CustomEmoji.selection.EmojiListEvent
import com.vitorpamplona.quartz.nip32Labeling.LabelEvent
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarning
import com.vitorpamplona.quartz.nip37Drafts.DraftEventCache
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.nip47WalletConnect.events.NwcInfoEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.AddressBookmark
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip56Reports.ReportType
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapCache
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplits
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiser
import com.vitorpamplona.quartz.nip58Badges.accepted.AcceptedBadgeSetEvent
import com.vitorpamplona.quartz.nip58Badges.accepted.tags.AcceptedBadge
import com.vitorpamplona.quartz.nip58Badges.award.BadgeAwardEvent
import com.vitorpamplona.quartz.nip58Badges.definition.BadgeDefinitionEvent
import com.vitorpamplona.quartz.nip58Badges.definition.tags.ThumbTag
import com.vitorpamplona.quartz.nip58Badges.profile.ProfileBadgesEvent
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.EphemeralGiftWrapEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapTemplateConversion
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MintHttpTransport
import com.vitorpamplona.quartz.nip62RequestToVanish.RequestToVanishEvent
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayInfo
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip68Picture.PictureMeta
import com.vitorpamplona.quartz.nip68Picture.pictureIMeta
import com.vitorpamplona.quartz.nip71Video.VideoMeta
import com.vitorpamplona.quartz.nip71Video.VideoNormalEvent
import com.vitorpamplona.quartz.nip71Video.VideoShortEvent
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.tags.ModeratorTag
import com.vitorpamplona.quartz.nip72ModCommunities.definition.tags.RelayTag
import com.vitorpamplona.quartz.nip72ModCommunities.rules.CommunityRulesEvent
import com.vitorpamplona.quartz.nip72ModCommunities.rules.tags.KindRuleTag
import com.vitorpamplona.quartz.nip72ModCommunities.rules.tags.PubkeyRuleTag
import com.vitorpamplona.quartz.nip72ModCommunities.rules.tags.WotTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.UserAssertionEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.poll.tags.PollType
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import com.vitorpamplona.quartz.nip89AppHandlers.clientTag.NostrSignerWithClientTag
import com.vitorpamplona.quartz.nip89AppHandlers.clientTag.withoutClientTag
import com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryRequest.DvmContentDiscoveryRequestEvent
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.nip92IMeta.imetas
import com.vitorpamplona.quartz.nip94FileMetadata.FileMetadataEvent
import com.vitorpamplona.quartz.nip94FileMetadata.blurhash
import com.vitorpamplona.quartz.nip94FileMetadata.dimension
import com.vitorpamplona.quartz.nip94FileMetadata.fileSize
import com.vitorpamplona.quartz.nip94FileMetadata.hash
import com.vitorpamplona.quartz.nip94FileMetadata.magnet
import com.vitorpamplona.quartz.nip94FileMetadata.mimeType
import com.vitorpamplona.quartz.nip94FileMetadata.originalHash
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.nip94FileMetadata.thumbhash
import com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.BaseVoiceEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceReplyEvent
import com.vitorpamplona.quartz.nipA3PaymentTargets.PaymentTarget
import com.vitorpamplona.quartz.nipB0WebBookmarks.WebBookmarkEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import com.vitorpamplona.quartz.nipFAAgentKeys.tags.AgentKeyTag
import com.vitorpamplona.quartz.utils.BigDecimal
import com.vitorpamplona.quartz.utils.DualCase
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.containsAny
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Path
import kotlin.coroutines.cancellation.CancellationException
import com.vitorpamplona.quartz.experimental.profileGallery.thumbhash as galleryThumbhash

/** How long past a disappearing message's deadline the sweep waits, to purge nearby deadlines in one pass (CORD-08). */
private const val CONCORD_EXPIRY_COALESCE_MS = 2_000L

/** Delay before the first stock-relay Direct Invite sweep, so it doesn't compete with the start-up fetches. */
private const val DIRECT_INVITE_SWEEP_START_MS = 20_000L

/** How often to sweep the stock relays for Direct Invites while the account is loaded. */
private const val DIRECT_INVITE_SWEEP_EVERY_MS = 15 * 60_000L

@OptIn(DelicateCoroutinesApi::class)
@Stable
class Account(
    val settings: AccountSettings = AccountSettings(KeyPair()),
    override val signer: NostrSigner,
    val geolocationFlow: () -> StateFlow<LocationResult>,
    val nwcFilterAssembler: () -> NWCPaymentFilterAssembler,
    val cashuMintDirectoryFilterAssembler: () -> com.vitorpamplona.amethyst.commons.relayClient.assemblers.CashuMintDirectoryFilterAssembler,
    /** How Cashu mint requests go out (the app's money-role HTTP: Tor or proxy per URL). */
    val cashuMintTransport: MintHttpTransport,
    val otsResolverBuilder: () -> OtsResolver,
    val cache: LocalCache,
    val client: INostrClient,
    val scope: CoroutineScope,
    /**
     * The running app's version name. The zap-the-devs prompt is dismissed per version, so a
     * donation in one release doesn't hide it in the next. Passed in rather than read from the
     * app's `BuildConfig`, which a shared module can't see.
     */
    val appVersion: String,
    /**
     * The app-wide store of media decryption keys. Encrypted attachments (Concord images, NIP-17
     * files) register their key here by URL, and the HTTP layer decrypts the blob on fetch.
     */
    val encryptionKeyCache: EncryptionKeyCache,
    /** Persists this account's settings. Called on the IO dispatcher, debounced, after changes. */
    val saveSettings: suspend (AccountSettings) -> Unit,
    /**
     * Where Marmot Welcomes and group messages are announced to the user (system notifications).
     * A provider, read when a message arrives, because the app builds its notifier after its accounts.
     */
    val marmotNotifier: () -> MarmotGroupNotifier,
    /** Asks the user to approve NIP-46 client connections and operations (the app's signer dialogs). */
    val nip46Consent: Nip46ConsentPrompter,
    /** Where this account's geohash-chat seed and nickname are kept (the app's encrypted storage). */
    val geohashIdentityStore: GeohashIdentityStore,
    /** Builds the raw-QUIC transport for agent text stream previews, on the account's scope. */
    val marmotStreamTransportFactory: (CoroutineScope) -> MarmotQuicTransport,
    /**
     * Where cordn keeps its encrypted group state, or null to run without it.
     *
     * A directory rather than a built runtime, because `CordnRuntime` needs
     * this account's [scope] and that only exists once the account does.
     * Nothing about it is shared with Marmot's stores above — cordn has its
     * own, by the §3.1 rule in `amethyst/plans/2026-09-19-cordn-ui.md`.
     */
    val cordnFilesDir: Path? = null,
    /**
     * Encrypts cordn's state at rest (the Android Keystore in the app). Called once, when
     * [cordnFilesDir] is set; without both, the account runs without cordn.
     */
    val cordnBlobCipher: (() -> CordnBlobCipher)? = null,
    val mlsGroupStateStore: MlsGroupStateStore? = null,
    val marmotMessageStore: com.vitorpamplona.marmot.groups.MarmotMessageStore? = null,
    val marmotKeyPackageStore: com.vitorpamplona.marmot.mip00KeyPackages.KeyPackageBundleStore? = null,
    /**
     * Durable publish obligations. Null means publish-before-apply does not
     * survive a restart, so a commit interrupted mid-publish is replaced by a
     * fresh one for the same epoch — a fork against the peers that took the
     * first.
     */
    val marmotPublishObligationStore: com.vitorpamplona.marmot.protocolCore.MarmotPublishObligationStore? = null,
    /**
     * Durable "already decided" markers for inbound events. Null means every
     * backdated gift wrap is re-unwrapped on every sync.
     */
    val marmotIngestDedupStore: com.vitorpamplona.marmot.MarmotIngestDedupStore? = null,
    /**
     * Durable push token records, stamps and tombstones. Null means a restart
     * forgets every tombstone, so a relayed but revoked token record can win
     * once and start waking a device its owner asked to be forgotten.
     */
    val marmotPushStateStore: com.vitorpamplona.marmot.mip05PushNotifications.MarmotPushStateStore? = null,
    val powQueue: () -> PoWPublishQueue? = { null },
    /**
     * Where the Web of Trust network index lives (the account's own directory), or null to
     * run without one. See [TrustNetworkState].
     */
    trustNetworkDir: Path? = null,
    /**
     * Builds a relay client for the trust network sync, apart from [client]: the shared client
     * files everything it receives into [cache], and a sync receives hundreds of thousands of
     * cards. Null disables syncing (the index on disk is still used).
     */
    trustNetworkClientBuilder: (() -> INostrClient)? = null,
    /** False on a metered network: background trust network downloads then wait. */
    canDownloadLargeFiles: () -> Boolean = { true },
    relayAuthPermissionStore: RelayAuthPermissionStore = InMemoryRelayAuthPermissionStore(),
    signerPermissionStore: NostrSignerPermissionStore = InMemoryNostrSignerPermissionStore(),
    nip46ClientStore: Nip46ClientStore = InMemoryNip46ClientStore(),
) : IAccount,
    UserFinderAccount {
    private var userProfileCache: User? = null

    override fun userProfile(): User = userProfileCache ?: cache.getOrCreateUser(signer.pubKey).also { userProfileCache = it }

    // IAccount interface properties
    override val pubKey: String get() = signer.pubKey
    override val showSensitiveContent: Boolean? get() = hiddenUsers.flow.value.showSensitiveContent
    override val hiddenWordsCase: List<DualCase> get() = hiddenUsers.flow.value.hiddenWordsCase
    override val hiddenUsersHashCodes: Set<Int> get() = hiddenUsers.flow.value.hiddenUsersHashCodes
    override val spammersHashCodes: Set<Int> get() = hiddenUsers.flow.value.spammersHashCodes

    // UserFinderAccount — narrow, read-only relay-hint view used by the shared
    // per-user metadata + per-note event finders (moved to commons). Snapshot
    // getters read `.value` fresh on every filter rebuild. userFinderPubkeyHex
    // doubles as the attribution pubkey for ExplainedFilter.accountPubKeys.
    override val userFinderPubkeyHex: HexKey get() = userProfile().pubkeyHex

    // No ifEmpty here on purpose: an empty kind:10086 is the user asking for no indexers, and
    // IndexerRelayListState already substitutes the defaults for the only case we may override —
    // never having seen the event. Re-substituting here would undo that choice.
    override fun indexRelays(): Set<NormalizedRelayUrl> = indexerRelayList.flow.value

    override fun outboxHomeRelays(): Set<NormalizedRelayUrl> = nip65RelayList.allFlowNoDefaults.value + privateStorageRelayList.flow.value + localRelayList.flow.value

    // searchRelayList.flow applies DefaultSearchRelayList internally when no kind:10007 has ever
    // been seen (SearchRelayListState.normalizeSearchRelayListWithBackup); an empty published list
    // stays empty. No ifEmpty here either way.
    override fun searchRelays(): Set<NormalizedRelayUrl> = (trustedRelayList.flow.value + searchRelayList.flow.value).toSet()

    override fun searchOnlyRelays(): Set<NormalizedRelayUrl> = searchRelayList.flow.value

    override fun followPlusAllMineWithSearchRelays(): Set<NormalizedRelayUrl> = followPlusAllMineWithSearch.flow.value

    override fun commonRelays(): Set<NormalizedRelayUrl> = followSharedOutboxesOrProxy.flow.value.ifEmpty { Constants.eventFinderRelays }

    override fun cardHomeRelays(): Set<NormalizedRelayUrl> = homeRelays.flow.value

    // Profiles on screen ask the providers for their cards even when the trust network holds
    // them: a card newer than the last sync updates the network until the next one
    // (TrustNetworkState.offer).
    override fun trustProvider(): ServiceProviderTag? = trustProviderList.liveUserRankProvider.value

    override fun followerCountProvider(): ServiceProviderTag? = trustProviderList.liveUserFollowerCount.value

    override fun declaredFollowsByOutboxRelay(): Map<NormalizedRelayUrl, Set<HexKey>> = declaredFollowsPerOutboxRelay.value

    val userMetadata = UserMetadataState(signer, cache, scope, settings)

    // Per-account NIP-42 ALLOW/DENY overrides, warm-cached in memory so a relay AUTH challenge is
    // answered without a disk read. Backed by a per-account file (see AccountCacheState).
    val relayAuthPermissions = RelayAuthPermissionCache(relayAuthPermissionStore, scope)

    // The `block/buzz` workspaces THIS account joined. Per account, not per device: the invite was
    // redeemed by this key and the relay grants membership to it alone — and this set makes the
    // relay first-party for NIP-42 (see AuthCoordinator.isFirstParty), so a device-global set would
    // hand every other logged-in account an automatic login on a workspace it never joined.
    // Restored/persisted per account by BuzzWorkspaceStore (see AccountCacheState).
    val buzzWorkspaces = BuzzWorkspaces()

    // The Buzz channels THIS account pinned. A star says which channels this user wants at the top
    // of the community view, so a shared set let one account reorder and badge every other one's
    // channel list. Restored/persisted per account by BuzzChannelStarStore.
    val buzzChannelStars = BuzzChannelStars()

    // Whether THIS account shares what the user listens to as its NIP-38 music status, and from
    // which apps. Per account because the status is signed and published by this key. Restored/
    // persisted per account by NowPlayingSettingsStore on Android.
    val nowPlayingSettings = NowPlayingSettingsState()

    // The NIP-OA attestation an owner issued to THIS account's key, attached to its Buzz-relay
    // AUTH so the relay grants virtual membership. Restored/persisted per account by
    // BuzzAttestationStore.
    val buzzAttestation = BuzzHeldAttestations(pubKey)

    // The relays this account approved by answering the NIP-42 prompt *without* the "remember"
    // switch. Deliberately in-memory only: it dies with this Account (i.e. with the process, or at
    // logout), which is what makes it a session grant rather than a stored ALLOW.
    val relayAuthSessionGrants = RelayAuthSessionGrants()

    // Per-account NIP-42 policy evaluator (blocked → per-relay override → global policy → prompt),
    // reading THIS account's own toggles, relay lists and follow graph. Cached here so every AUTH
    // path (foreground screen + background notification consumer) shares one instance, and so an
    // AUTH challenge is decided per account instead of folding every logged-in account together.
    val relayAuthLedger =
        RelayAuthPermissionLedger(
            store = relayAuthPermissions,
            globalPolicy = { settings.defaultRelayAuthPolicy.value },
            sessionGrants = relayAuthSessionGrants,
            customToggles = {
                RelayAuthCustomToggles(
                    myRelaysAndVenues = settings.relayAuthTrustMyRelaysAndVenues.value,
                    readFollows = settings.relayAuthTrustReadFollows.value,
                    messageFollows = settings.relayAuthTrustMessageFollows.value,
                    messageStrangers = settings.relayAuthTrustMessageStrangers.value,
                )
            },
            isInMyRelayList = { relayUrl -> relayUrl.normalizeRelayUrlOrNull()?.let { it in trustedRelays.flow.value } ?: false },
            isBlocked = { relayUrl -> relayUrl.normalizeRelayUrlOrNull()?.let { it in blockedRelayList.flow.value } ?: false },
            isFollowed = { pubkey -> pubkey in allFollows.flow.value.authors },
            isTrustedVenue = { relayUrl, venueId ->
                venueId in publicChatList.flowSet.value ||
                    venueId in communityList.flowSet.value ||
                    isJoinedRoomId(relayUrl, venueId) ||
                    Address.parse(venueId)?.pubKeyHex?.let { it in allFollows.flow.value.authors } == true
            },
            isVenueHostRelay = { relayUrl -> relayUrl.normalizeRelayUrlOrNull()?.let { it in venueHostRelays() } ?: false },
        )

    /**
     * Sets the global NIP-42 policy, dropping every session grant when it becomes
     * [RelayAuthPolicy.NEVER].
     *
     * The two halves belong together, which is why they live here instead of in the settings screen
     * that used to pair them: a session grant outranks the policy (see
     * [com.vitorpamplona.amethyst.commons.relayauth.RelayAuthResolver]), so "never log in" only
     * means what it says if the casual one-tap answers go with it. As a composable's `onClick` that
     * was a property of one screen rather than of the account, and any other caller of
     * [AccountSettings.changeDefaultRelayAuthPolicy] silently reintroduced grants that outlive the
     * switch-it-all-off answer.
     *
     * Stored Always/Never exceptions are deliberately left alone: those outrank the policy by
     * design, and the settings screen lists them, so they are a standing answer rather than a
     * casual one.
     */
    fun changeDefaultRelayAuthPolicy(policy: RelayAuthPolicy) {
        settings.changeDefaultRelayAuthPolicy(policy)
        if (policy == RelayAuthPolicy.NEVER) relayAuthSessionGrants.clear()
    }

    /**
     * Relays that exist here because *this account* joined a room on them: the host of every NIP-29
     * relay group on its kind-10009 list, plus the relays of every Concord community on its
     * kind-13302 list.
     *
     * Both are venues in the [RelayAuthCustomToggles.myRelaysAndVenues] sense but neither shows up in
     * a NIP-65/DM/search list, so nothing else in the auth path can see them: a NIP-29 group's content
     * is `#h`-scoped and never names the user, and a Concord plane is addressed to a derived stream
     * key rather than to anyone's pubkey.
     */
    fun venueHostRelays(): Set<NormalizedRelayUrl> =
        RelayAuthVenues.hostRelays(
            joinedGroups = relayGroupList.liveRelayGroupIds.value,
            joinedCommunities = concordChannelList.liveCommunities.value,
        )

    /**
     * True when [venueId], served by [relayUrl], is a room this account joined that the venue *lists*
     * above don't cover: a NIP-29 group id (from the kind-10009 list) or a Concord community id (from
     * the kind-13302 list). Those are the ids the subscription assemblers declare on their filters, so
     * this is what turns a `READ_VENUE`/`POST_VENUE` on a joined group or community into a trusted
     * venue.
     */
    private fun isJoinedRoomId(
        relayUrl: String,
        venueId: String,
    ): Boolean =
        RelayAuthVenues.isJoinedRoom(
            venueId = venueId,
            relayUrl = relayUrl.normalizeRelayUrlOrNull(),
            joinedGroups = relayGroupList.liveRelayGroupIds.value,
            joinedCommunities = concordChannelList.liveCommunities.value,
        )

    // Per-account relay NOTIFY (payment-prompt) cache. NotifyCoordinator attributes each incoming
    // NOTIFY to the account whose AUTH the relay rejected and drops it here, so a prompt for one
    // account never surfaces under another (the old cache was a process-wide singleton).
    val relayNotifications = NotifyRequestsCache()

    // Shared cache of connected wallets' kind 13194 info events (capabilities +
    // encryption + notification support). Backs NIP-44 negotiation in
    // NwcSignerState and notification gating in NwcPaymentNotificationWatcher.
    val nwcInfoCache =
        NwcInfoCache(
            fetch = { uri ->
                client.fetchFirst(
                    uri.relayUri,
                    ExplainedFilter(kinds = listOf(NwcInfoEvent.KIND), authors = listOf(uri.pubKeyHex), limit = 1, purpose = SubPurpose.NWC),
                ) as? NwcInfoEvent
            },
            scope = scope,
        )

    override val nip47SignerState = NwcSignerState(signer, nwcFilterAssembler, cache, scope, settings, nwcInfoCache)

    val nip65RelayList = Nip65RelayListState(signer, cache, scope, settings)
    val localRelayList = LocalRelayListState(signer, cache, scope, settings)

    /** Connected-Apps signer permission ledger, shared by napplets and the NIP-46 bunker. */
    val signerPermissionLedger = NostrSignerPermissionLedger(signerPermissionStore)

    /**
     * Runs this account as a NIP-46 remote signer for other apps when
     * [AccountSettings.nip46SignerEnabled] is on, listening on the inbox relays
     * and dispatching to [signer] (see [Nip46SignerState]).
     */
    val nip46Signer =
        Nip46SignerState(
            // Acting as someone else's bunker: the templates arriving here were composed by the
            // connected client, so they are signed exactly as received — our client tag would both
            // misattribute the event and change the id the client expects back.
            signer = signer.withoutClientTag(),
            client = client,
            ledger = signerPermissionLedger,
            clientStore = nip46ClientStore,
            inboxRelays = nip65RelayList.inboxFlow,
            scope = scope,
            settings = settings,
            consent = nip46Consent,
            relayAcceptsSignerTraffic = { cache.appHost.nip11Cache.acceptsSignerTraffic(it) },
        )

    val forwardKind0ToLocalRelay = ForwardKind0ToLocalRelayState(client, localRelayList, settings)

    val dmRelayList = DmRelayListState(signer, cache, scope, settings)

    val keyPackageRelayList = KeyPackageRelayListState(signer, cache, scope, settings)

    val privateStorageDecryptionCache = PrivateStorageRelayListDecryptionCache(signer)
    val privateStorageRelayList = PrivateStorageRelayListState(signer, cache, privateStorageDecryptionCache, scope, settings)

    val searchRelayListDecryptionCache = SearchRelayListDecryptionCache(signer)
    val searchRelayList = SearchRelayListState(signer, cache, searchRelayListDecryptionCache, scope, settings)

    val trustedRelayListDecryptionCache = TrustedRelayListDecryptionCache(signer)
    val trustedRelayList = TrustedRelayListState(signer, cache, trustedRelayListDecryptionCache, scope, settings)

    val proxyRelayListDecryptionCache = ProxyRelayListDecryptionCache(signer)
    val proxyRelayList = ProxyRelayListState(signer, cache, proxyRelayListDecryptionCache, scope, settings)

    val broadcastRelayListDecryptionCache = BroadcastRelayListDecryptionCache(signer)
    val broadcastRelayList = BroadcastRelayListState(signer, cache, broadcastRelayListDecryptionCache, scope, settings)

    val indexerRelayListDecryptionCache = IndexerRelayListDecryptionCache(signer)
    val indexerRelayList = IndexerRelayListState(signer, cache, indexerRelayListDecryptionCache, scope, settings)

    val favoriteRelayListDecryptionCache = FavoriteRelayListDecryptionCache(signer)
    val favoriteRelayList = FavoriteRelayListState(signer, cache, favoriteRelayListDecryptionCache, scope, settings)

    val blockedRelayListDecryptionCache = BlockedRelayListDecryptionCache(signer)
    val blockedRelayList = BlockedRelayListState(signer, cache, blockedRelayListDecryptionCache, scope, settings)

    val kind3FollowList = Kind3FollowListState(signer, cache, scope, settings)

    val ephemeralChatListDecryptionCache = EphemeralChatListDecryptionCache(signer)
    val ephemeralChatList = EphemeralChatListState(signer, cache, ephemeralChatListDecryptionCache, scope, settings)

    val relayGroupListDecryptionCache = RelayGroupListDecryptionCache(signer)
    val relayGroupList = RelayGroupListState(signer, cache, relayGroupListDecryptionCache, scope, settings)

    /**
     * Buzz channels somebody else added me to that I haven't answered yet, projected from the cached
     * kind-44100/44101 verdicts. Account state rather than screen state because the notifications DAL
     * reads it to decide whether a cached 44100 is still a live question.
     */
    val channelInvites =
        ChannelInvitesState(
            me = signer.pubKey,
            cache = cache,
            buzzWorkspaces = buzzWorkspaces,
            relayGroupList = relayGroupList,
            dismissed = settings.dismissedChannelInvites,
            scope = scope,
        )

    val concordChannelList = ConcordChannelListState(signer, cache, scope, settings)

    /**
     * The live read-path for joined Concord Channels: one folding session per
     * community, fed by inbound kind-1059 plane wraps. Kept in step with
     * [concordChannelList] and consulted by the giftwrap decrypt path so a Concord
     * plane wrap routes here instead of being dropped as an undecryptable DM.
     */
    val concordSessions = ConcordSessionManager(concordChannelList.liveCommunities, signer.pubKey, scope, ::consumeConcordRumorGated)

    /**
     * Sink for decrypted Concord rumors: drops a message whose author is banned in
     * the community's current fold before it ever becomes a Note, then delegates to
     * the cache. Bans that arrive *after* a message are handled by removing the
     * author's existing notes on re-fold (see `refreshConcordChannelIndex`); this
     * gate stops *new* posts from a banned author from appearing at all.
     */
    private fun consumeConcordRumorGated(
        communityId: String,
        channelIdHex: String,
        rumor: Event,
        seenOnRelays: Set<NormalizedRelayUrl>,
    ) {
        val authority =
            concordSessions
                .sessionFor(communityId)
                ?.state
                ?.value
                ?.authority
        if (authority?.isBanned(rumor.pubKey) == true) return
        // CORD-08 §3: an already-expired rumor is never stored. The session refuses it first; this
        // backs it up for any other caller of the sink.
        if (ConcordDisappearing.isExpired(rumor)) return
        registerConcordEncryptedImages(rumor)
        cache.consumeConcordRumor(communityId, channelIdHex, rumor, seenOnRelays)
    }

    /**
     * The CORD-08 §3 purge: drops every Concord rumor whose `expiration` has passed — its note, the
     * note of the wrap that carried it, and the wrap in its session's buffer (so no re-projection can
     * resurrect it). The rumor's own children (a reply, a reaction) are independent events and stay,
     * as on a delete; they carry their own expiration when the timer was on. Scheduled on
     * [ConcordSessionManager.nextExpiry], so it only ever runs when something is due.
     */
    fun sweepExpiredConcordMessages(now: Long = TimeUtils.now()) {
        val expired = concordSessions.sweepExpired(now)
        for (rumors in expired.values) {
            for (gone in rumors) {
                // Its attachments' decryption keys go with it: a cached key would keep the blob readable.
                gone.attachmentUrls.forEach { encryptionKeyCache.remove(it) }
                cache.getNoteIfExists(gone.rumorId)?.let { note ->
                    note.detachFromChildren()
                    cache.pruner.unlinkAndRemove(note)
                }
                cache.getNoteIfExists(gone.wrapId)?.let { cache.pruner.unlinkAndRemove(it) }
            }
        }
    }

    /** True for a Concord rumor whose own `expiration` has passed: never displayed (CORD-08 §3). */
    private fun isConcordExpired(note: Note): Boolean {
        val event = note.event ?: return false
        if (note.inGatherers?.any { it is ConcordChannel } != true) return false
        return ConcordDisappearing.isExpired(event)
    }

    /**
     * True for a Concord timer notice (CORD-08 §4) that must not be shown: malformed, or authored by
     * someone who does not hold MANAGE_METADATA in the community's current fold — anyone can spell
     * the tag, only staff are believed about policy.
     */
    private fun isUnbelievedConcordTimerNotice(note: Note): Boolean {
        val event = note.event as? ConcordTimerNoticeEvent ?: return false
        val channel = note.inGatherers?.firstNotNullOfOrNull { it as? ConcordChannel } ?: return true
        val authority =
            concordSessions
                .sessionFor(channel.channelId.communityId)
                ?.state
                ?.value
                ?.authority ?: return true
        return !ConcordDisappearing.isBelievedNotice(event, authority)
    }

    /**
     * Register any encrypted image attachments on a Concord message ([ChannelChat.encryptedImagesOf])
     * so the shared media pipeline can display them: the ciphertext blob's AES-256-GCM key/nonce go
     * into [encryptionKeyCache], and the OkHttp EncryptedBlobInterceptor
     * decrypts the blob transparently on fetch (keyed by URL) — the same path NIP-17 encrypted media
     * uses. Runs for both inbound wraps and our own local echo, so a sent image renders immediately.
     */
    internal fun registerConcordEncryptedImages(rumor: Event) {
        val images = ChannelChat.encryptedImagesOf(rumor)
        if (images.isEmpty()) return
        images.forEach { img ->
            if (img.algo == AES_GCM_NAME) {
                encryptionKeyCache.add(img.url, AESGCM(img.key, img.nonce), img.mimeType)
            }
        }
    }

    /**
     * Copies each folded community's metadata (name/icon, channel flags, this account's
     * membership) onto its [ConcordChannel] objects in the cache, and drops messages from
     * authors banned since they loaded. Runs account-wide on every
     * [com.vitorpamplona.amethyst.commons.model.concord.ConcordSessionManager] revision —
     * NOT gated behind the Concord hub screen — so every surface (the Messages-tab
     * community chip, the chat screen title) reflects the current fold, and bans apply,
     * even when the hub was never opened.
     */
    fun refreshConcordChannelIndex() {
        val myPubKey = signer.pubKey
        val relaysByCommunity =
            concordChannelList.liveCommunities.value.associate { entry ->
                entry.id to entry.relays.mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it) }
            }
        for (session in concordSessions.sessions()) {
            val state = session.state.value ?: continue
            val communityId = session.entry.id
            val relays = relaysByCommunity[communityId] ?: emptySet()
            for (channelIdHex in state.channels.keys) {
                val channel = cache.getOrCreateConcordChannel(ConcordChannelId(communityId, channelIdHex))
                // Invalidate the channel's metadata flow only on a real change so the Messages-row
                // name + community chip recompose when the fold first resolves them (they observe
                // metadata.stateFlow via observeChannel), without churning every row every tick.
                // A Private Channel is readable/postable only with its held key (CORD-03 §1).
                val keyHeld = ConcordActions.canAccessChannel(session.entry, state, channelIdHex)
                if (channel.updateFrom(state, relays, myPubKey, keyHeld)) channel.updateChannelInfo()
            }
            // Dropping a banned author's loaded notes walks every note of every channel, so it runs
            // when the community's banlist moves, not on every fold tick. New notes from a banned
            // author are already refused at ingest.
            val banned = state.authority.bannedMembers()
            if (banned.isNotEmpty() && concordBannedSwept[communityId] != banned) {
                for (channelIdHex in state.channels.keys) {
                    val channel = cache.getOrCreateConcordChannel(ConcordChannelId(communityId, channelIdHex))
                    channel.notes
                        .filter { _, note -> note.event?.pubKey?.let { state.authority.isBanned(it) } == true }
                        .forEach { channel.removeNote(it) }
                }
            }
            concordBannedSwept[communityId] = banned
        }
    }

    /** The banlist each community's loaded notes were last swept against ([refreshConcordChannelIndex]). */
    private val concordBannedSwept = HashMap<String, Set<HexKey>>()

    val publicChatListDecryptionCache = PublicChatListDecryptionCache(signer)
    val publicChatList = PublicChatListState(signer, cache, publicChatListDecryptionCache, scope, settings)

    val communityListDecryptionCache = CommunityListDecryptionCache(signer)
    val communityList = CommunityListState(signer, cache, communityListDecryptionCache, scope, settings)

    val interestListDecryptionCache = InterestListDecryptionCache(signer)
    val interestList = InterestListState(signer, cache, interestListDecryptionCache, scope, settings)

    val favoriteAlgoFeedsListDecryptionCache = FavoriteAlgoFeedsListDecryptionCache(signer)
    val favoriteAlgoFeedsList = FavoriteAlgoFeedsListState(signer, cache, favoriteAlgoFeedsListDecryptionCache, scope, settings)
    val favoriteAlgoFeedsOrchestrator = FavoriteAlgoFeedsOrchestrator(this, scope)

    val favoriteFollowSetsListDecryptionCache = FavoriteFollowSetsListDecryptionCache(signer)
    val favoriteFollowSetsList = FavoriteFollowSetsListState(signer, cache, favoriteFollowSetsListDecryptionCache, scope)

    val geohashListDecryptionCache = GeohashListDecryptionCache(signer)
    val geohashList = GeohashListState(signer, cache, geohashListDecryptionCache, scope, settings)

    // Anonymous, per-geohash throwaway identities for Bitchat-interoperable location chats.
    val geohashIdentity = GeohashChatIdentityState(signer, scope, geohashIdentityStore)

    val muteListDecryptionCache = MuteListDecryptionCache(signer)
    val muteList = MuteListState(signer, cache, muteListDecryptionCache, scope, settings)

    val trustProviderListDecryptionCache = TrustProviderListDecryptionCache(signer)
    val trustProviderList = TrustProviderListState(signer, cache, trustProviderListDecryptionCache, scope, settings)

    /** The NIP-85 rank provider's network: who counts as known beyond follows. */
    val trustNetwork =
        TrustNetworkState(
            rankProvider = trustProviderList.resolvedRankProvider,
            minTrustScore = settings.syncedSettings.security.minTrustScore,
            store = trustNetworkDir?.let { TrustNetworkStore(it) },
            source = trustNetworkClientBuilder?.let { RelayTrustNetworkSource(it) },
            scope = scope,
            canDownloadLarge = canDownloadLargeFiles,
        )

    /**
     * The Web of Trust decisions, published whenever an answer can change (network, minimum
     * score, follows, a card seen between syncs). Key feeds and screens on it; see [TrustVerdicts].
     */
    val trustVerdicts: StateFlow<TrustVerdicts> =
        trustNetwork.verdicts(
            me = signer.pubKey,
            follows = kind3FollowList.flow.map { it.authors }.stateIn(scope, SharingStarted.Eagerly, kind3FollowList.flow.value.authors),
            scope = scope,
        )

    /**
     * The Web of Trust decisions as of this instant. For one-off checks (a push, a room) that
     * cannot wait for [trustVerdicts] to publish, e.g. right after [TrustNetworkState.awaitLoaded].
     */
    fun currentTrustVerdicts(): TrustVerdicts = trustNetwork.snapshot(signer.pubKey, followingKeySet())

    override fun trustRankOf(pubkey: HexKey): Int? = trustNetwork.rankOf(pubkey)

    val followSetDecryptionCache = FollowSetDecryptionCache(signer)
    val blockPeopleList = BlockPeopleListState(signer, cache, followSetDecryptionCache, scope)
    val followSets = FollowSetsState(signer, cache, followSetDecryptionCache, scope)
    val starterPacks = StarterPacksState(signer, cache, scope)

    val hiddenUsers = HiddenUsersState(muteList.flow, blockPeopleList.flow, scope, settings)

    val bookmarkSets = BookmarkSetsState(signer, cache, scope)
    val drss = DrssState(signer, cache, scope)
    val interestSets = InterestSetsState(signer, cache, scope)
    val appRecommendations = AppRecommendationsState(signer, cache, scope)
    val oldBookmarkState = OldBookmarkListState(signer, cache, scope)
    val bookmarkState = BookmarkListState(signer, cache, scope)
    val gitRepositoryListState = GitRepositoryListState(signer, cache, scope)
    val pinState = PinListState(signer, cache, scope)
    val emoji = EmojiPackState(signer, cache, scope, settings.syncedSettings.reactions.reactionChoices)
    val ownedEmojiPacks = OwnedEmojiPacksState(signer, cache, scope)

    // needs `emoji` above: nickname edits resolve :shortcodes: against the account's packs
    val userAssertionDecryptionCache = UserAssertionDecryptionCache(signer)
    val userAssertions = UserAssertionsState(signer, cache, userAssertionDecryptionCache, emoji)

    val vanish = VanishRequestsState(signer, cache, client, scope)

    val appSpecific = AppSpecificState(signer, cache, scope, settings)

    val blossomServers = BlossomServerListState(signer, cache, scope, settings)

    val nestsServers =
        NestsServerListState(signer, cache, scope)

    // Relay settings
    val homeRelays = AccountHomeRelayState(nip65RelayList, privateStorageRelayList, localRelayList, scope)
    val outboxRelays = AccountOutboxRelayState(nip65RelayList, privateStorageRelayList, localRelayList, broadcastRelayList, scope)
    val mineRelays = AccountMineRelayState(nip65RelayList, privateStorageRelayList, localRelayList, proxyRelayList, scope)
    val dmRelays = DmInboxRelayState(dmRelayList, nip65RelayList, privateStorageRelayList, localRelayList, scope)
    val notificationRelays = NotificationInboxRelayState(nip65RelayList, localRelayList, scope)

    val nwcBackup = NwcBackupState(signer, client, scope, settings, outboxRelays.flow, nip65RelayList.inboxFlowNoDefaults)

    // Account-level notification history paging cursors (one scope per account): how far back each
    // notification relay has been paged by until+limit. Held here so they share the account's lifetime;
    // the history loader ([AccountNotificationsHistoryEoseManager]) binds its orchestrator to these.
    val notificationHistory = RelayLoadingCursors()

    // Per-relay backward-paging cursors for the NIP-60 spending history (kind:7376): how far back each
    // outbox relay has been paged by until+limit. Same lifetime rule as notificationHistory — held here
    // so paging progress survives leaving and re-entering the wallet screen; the history loader
    // ([CashuWalletHistoryEoseManager]) binds its orchestrator to these.
    val cashuHistory = RelayLoadingCursors()

    /**
     * NIP-BC on-chain wallet balance for this account's Taproot address. Cached
     * (one Esplora round trip per minute at most) so the zap picker can ask
     * synchronously whether an amount is payable on-chain before offering it.
     */
    val onchainWalletState =
        OnchainWalletState(
            pubKey = signer.pubKey,
            scope = scope,
            backend = { cache.onchainBackend },
        )

    /** The user's AI agent keys (kind 12000), first one preferred. */
    val agentKeys = AgentKeyState(signer, cache, scope)

    /** The morning front page's schedule (NIP-FB kind 34817, `d` = `observer`), shared by every device. */
    val observerSchedule = ObserverScheduleState(signer, cache, scope, client, nip65RelayList.outboxFlowNoDefaults)

    val cashuWalletState =
        CashuWalletState(
            pubKey = signer.pubKey,
            signer = signer,
            cache = cache,
            client = client,
            scope = scope,
            outboxRelaysFlow = outboxRelays.flow,
            inboxRelaysFlow = notificationRelays.flow,
            dmRelaysFlow = dmRelays.flow,
            settings = settings,
            mintTransport = cashuMintTransport,
        )

    /**
     * NIP-87 cashu mint directory — populated on-demand while the mint
     * picker is on screen. ViewModels call open()/close() ref-counted, the
     * relay subscription only runs while at least one opener is active.
     */
    val cashuMintDirectoryState =
        CashuMintDirectoryState(
            cache = cache,
            scope = scope,
            assembler = cashuMintDirectoryFilterAssembler(),
            followsFlow =
                kotlinx.coroutines.flow.MutableStateFlow(kind3FollowList.flow.value.authors).also { authorSet ->
                    scope.launch {
                        kind3FollowList.flow.collect { authorSet.value = it.authors }
                    }
                },
        )

    val trustedRelays = TrustedRelayListsState(nip65RelayList, privateStorageRelayList, localRelayList, dmRelayList, searchRelayList, indexerRelayList, proxyRelayList, trustedRelayList, broadcastRelayList, favoriteRelayList, trustProviderList.liveRelays, scope)

    /** Relays guessed on the user's behalf until their own lists arrive. Read only by Tor routing. */
    val assumedRelays = AssumedRelayListsState(nip65RelayList, searchRelayList, indexerRelayList, scope)

    // Follows Relays
    val followOutboxesOrProxy = FollowListOutboxOrProxyRelays(kind3FollowList, blockedRelayList, proxyRelayList, cache, scope)

    // only follow relays that are declared in more than one user.
    val followSharedOutboxesOrProxy = FollowListReusedOutboxOrProxyRelays(kind3FollowList, blockedRelayList, proxyRelayList, cache, scope)

    val followPlusAllMineWithIndex = MergedFollowPlusMineWithIndexRelayListsState(followOutboxesOrProxy, nip65RelayList, privateStorageRelayList, localRelayList, indexerRelayList, scope)
    val followPlusAllMineWithSearch = MergedFollowPlusMineWithSearchRelayListsState(followOutboxesOrProxy, nip65RelayList, privateStorageRelayList, localRelayList, searchRelayList, scope)
    val defaultGlobalRelays = MergedFollowPlusMineRelayListsState(followOutboxesOrProxy, nip65RelayList, privateStorageRelayList, localRelayList, scope)

    // keeps a cache of the declared outbox relays for each author
    val declaredFollowsPerOutboxRelay = DeclaredFollowsPerOutboxRelay(kind3FollowList, cache, scope).flow
    val declaredFollowsPerUsingRelay = DeclaredFollowsPerUsingRelay(kind3FollowList, cache, scope).flow

    // keeps a cache of the outbox relays for each author
    val followsPerRelay = FollowsPerOutboxRelay(kind3FollowList, blockedRelayList, proxyRelayList, cache, scope).flow

    // Merges all follow lists to create a single All Follows feed.
    val allFollows = MergedFollowListsState(kind3FollowList, followSets, starterPacks, interestList, geohashList, communityList, scope)

    val privateDMDecryptionCache = PrivateDMCache(signer)
    override val privateZapsDecryptionCache = PrivateZapCache(signer)
    val draftsDecryptionCache = DraftEventCache(signer)

    override val chatroomList = cache.getOrCreateChatroomList(signer.pubKey)
    override val marmotGroupList =
        MarmotGroupList(signer.pubKey)

    val newNotesPreProcessor = EventProcessor(this, cache)

    /**
     * The Settings › Messages load toggles, applied in one order (gate, rooms, then [ChatFeedToggles.applied]).
     * Everything that loads, shows or notifies a chat type asks here rather than the raw setting.
     */
    val chatFeedToggles: ChatFeedToggles =
        ChatFeedToggles(
            settings = settings,
            chatroomList = chatroomList,
            draftRumor = { draftsDecryptionCache.preCachedDraft(it) },
            decryptDraft = { draftsDecryptionCache.cachedDraft(it) },
            findCachedNotes = { predicate -> cache.notes.filter { _, note -> predicate(note) } },
            reindexDraft = { newNotesPreProcessor.consume(it) },
            onDmProtocolOff = { type ->
                // Calls signal over NIP-17's inbox: once it closes the peer's renegotiation and hangup
                // can't reach us, so end any call in progress rather than leave it hanging.
                if (type == ChatFeedType.NIP17) {
                    if (callManager.state.value is CallState.IncomingCall) callManager.rejectCall() else callManager.hangup()
                }
            },
            forgetGiftWraps = { cache.pruner.forgetGiftWraps(it) },
        )

    /**
     * Owns the WebRTC call state machine.
     *
     * Account-scoped on purpose: a call outlives the main UI. It runs in its own
     * [com.vitorpamplona.amethyst.ui.call.CallActivity] (a separate task, since MainActivity is
     * `singleInstance`) backed by a foreground service, so Android is free to destroy the
     * backgrounded MainActivity while the call is up — which it does routinely, e.g. a few hundred
     * milliseconds after CallActivity enters picture-in-picture on HOME. While this lived on
     * `AccountViewModel` (and ran on `viewModelScope`), that destruction cleared the ViewModel and
     * reset the call to Idle, hanging up mid-conversation.
     *
     * Torn down with the account: [scope] is cancelled by
     * `AccountCacheState.removeAccount`, which also calls [CallManager.dispose] for the
     * independent watchdog scope.
     */
    val callManager =
        CallManager(
            signer = signer,
            scope = scope,
            isFollowing = { isFollowing(it) },
            publishEvent = { wrap -> scope.launch { publishCallSignaling(wrap) } },
            isCallsEnabled = { chatFeedToggles.isCallingActive() },
        )

    // Per-message publish acceptance (relay OKs), feeding the delivery ticks on
    // own chat bubbles.
    val chatDeliveryTracker = ChatDeliveryTracker(client)

    /** Concord community orchestration (join/create/messages/moderation). */
    val concord = AccountConcordActions(this)

    /** Marmot/MLS group orchestration (create/members/admins/messages/key packages). */
    val marmot = AccountMarmotActions(this)

    /** NIP-29 relay-group + Buzz workspace orchestration. */
    val relayGroups = AccountRelayGroupActions(this)

    /** Zap/payment orchestration (NIP-57, NWC, BOLT12, onchain). */
    val zaps = AccountZapActions(this)

    /**
     * Relay routing + sign-and-publish choke point: computes which relays an event
     * should go to (outbox model, hints, channels, broadcast lists) and owns every
     * publish path. All Account send helpers delegate here.
     */
    val broadcaster = EventBroadcaster(this)

    val otsState = OtsState(signer, cache, otsResolverBuilder, scope, settings)

    /**
     * cordn, for this account, or null when no directory was supplied.
     *
     * Built here for the same reason [marmotManager] is: it needs [scope].
     * Opening coordinators and starting their sync loops is a separate,
     * suspending step ([CordnRuntime.start]) — constructing this touches no
     * network and no coordinator learns anything from it.
     */
    val cordnRuntime: CordnRuntime? =
        cordnFilesDir?.let { dir ->
            // Loud rather than silently cordn-less: a caller that sets the directory and forgets
            // the cipher would otherwise just lose the user's cordn groups with no error.
            val cipher = requireNotNull(cordnBlobCipher) { "cordnFilesDir is set but cordnBlobCipher is not" }
            CordnRuntime(
                accountSigner = signer,
                client = client,
                filesDir = dir,
                scope = scope,
                cipher = cipher(),
            )
        }

    val marmotManager: MarmotManager? =
        mlsGroupStateStore?.let {
            MarmotManager(
                signer,
                it,
                marmotMessageStore,
                marmotKeyPackageStore,
                // Publish-before-apply: a group-state change becomes canonical
                // only once a relay in the group's own scope returns OK true.
                // `publishAndConfirm` is exactly that "at least one
                // acknowledged accept" rule; a plain `publish` would report
                // success for bytes nobody took.
                MarmotPublisher { event, relays -> client.publishAndConfirm(event, relays, untilFirstAccept = true) },
                marmotPublishObligationStore,
                marmotIngestDedupStore,
                scope = scope,
            )
        }

    /**
     * Push token gossip (`features/push-notifications.md`) for the groups this
     * account is in.
     *
     * Present whenever Marmot itself is, because CONSUMING gossip costs nothing
     * and is what lets this client answer a peer's kind:447 later. Producing a
     * record of our own is a separate decision: it needs a device token and a
     * notification server public key, neither of which the protocol discovers.
     */
    val marmotPushCoordinator: MarmotPushCoordinator? =
        marmotManager?.let {
            marmotPushStateStore?.let { store -> MarmotPushCoordinator(it, store) } ?: MarmotPushCoordinator(it)
        }

    /**
     * Raw QUIC for agent text stream previews (`transports/quic.md`).
     *
     * Only the live preview needs it. A device that cannot open a QUIC
     * connection still participates fully — it reads every stream's
     * authoritative kind:9 like ordinary chat — which is why this is a
     * separate optional piece rather than part of [marmotManager].
     */
    val marmotStreamTransport: MarmotQuicTransport by lazy { marmotStreamTransportFactory(scope) }

    val paymentTargetsState = NipA3PaymentTargetsState(signer, cache, scope, settings)

    val bolt12OfferList = Bolt12OfferListState(signer, cache, scope, settings)

    val feedDecryptionCaches =
        FeedDecryptionCaches(
            peopleListCache = followSetDecryptionCache,
            muteListCache = muteListDecryptionCache,
            communityListCache = communityListDecryptionCache,
            hashtagCache = interestListDecryptionCache,
            geohashCache = geohashListDecryptionCache,
        )

    // The members of every list picked in a top bar, on any screen: see [SelectedListAuthorsState].
    // It must stay declared above the `live*FollowLists` below: Kotlin initializes properties in
    // declaration order, and each of them registers here while the account is being constructed.
    private val selectedLists = SelectedListAuthorsState(scope)

    /** The members of every list the user picked in a top bar, watched as feed authors while the app is in the foreground. */
    val selectedListAuthors: StateFlow<Set<HexKey>> = selectedLists.flow

    /** Builds a screen's top-nav flow and registers it in [selectedListAuthors]. */
    private fun selectedTopNavFilterFlow(listName: MutableStateFlow<TopFilter>): StateFlow<IFeedTopNavFilter> = topNavFilterFlow(listName).also(selectedLists::register)

    fun topNavFilterFlow(listName: MutableStateFlow<TopFilter>) =
        FeedTopNavFilterState(
            feedFilterListName = listName,
            kind3Follows = kind3FollowList.flow,
            allFollows = allFollows.flow,
            locationFlow = geolocationFlow,
            followsRelays = defaultGlobalRelays.flow,
            blockedRelays = blockedRelayList.flow,
            proxyRelays = proxyRelayList.flow,
            mineRelays = mineRelays.flow,
            relayFeeds = favoriteRelayList.flow,
            caches = feedDecryptionCaches,
            signer = signer,
            scope = scope,
            favoriteAlgoFeedsOrchestrator = favoriteAlgoFeedsOrchestrator,
            favoriteAlgoFeedAddresses = favoriteAlgoFeedsList.flow,
            interestSetHashtags = interestSets.hashtagsByIdentifier,
            customFeeds = settings.customFeeds,
        ).flow

    // App-ready Feeds
    val liveHomeFollowLists: StateFlow<IFeedTopNavFilter> = selectedTopNavFilterFlow(settings.defaultHomeFollowList)
    val liveHomeFollowListsPerRelay = OutboxLoaderState(liveHomeFollowLists, cache, scope).flow

    val liveStoriesFollowLists: StateFlow<IFeedTopNavFilter> = selectedTopNavFilterFlow(settings.defaultStoriesFollowList)
    val liveStoriesFollowListsPerRelay = OutboxLoaderState(liveStoriesFollowLists, cache, scope).flow

    val liveDiscoveryFollowLists: StateFlow<IFeedTopNavFilter> = selectedTopNavFilterFlow(settings.defaultDiscoveryFollowList)
    val liveDiscoveryFollowListsPerRelay = OutboxLoaderState(liveDiscoveryFollowLists, cache, scope).flow

    val liveNotificationFollowLists: StateFlow<IFeedTopNavFilter> = selectedTopNavFilterFlow(settings.defaultNotificationFollowList)
    val liveNotificationFollowListsPerRelay = OutboxLoaderState(liveNotificationFollowLists, cache, scope).flow

    // The split Notifications tabs pin a list each. Built once per account: the filters that read
    // them are rebuilt with every AccountViewModel, and each build would add another collector
    // that runs on [scope] until the account is removed.
    private val pinnedAllFollowsNotificationLists by lazy { topNavFilterFlow(MutableStateFlow(TopFilter.AllFollows)) }
    private val pinnedGlobalNotificationLists by lazy { topNavFilterFlow(MutableStateFlow(TopFilter.Global)) }

    /** The follow lists of a Notifications tab pinned to [filter], shared by every filter that pins the same one. */
    fun pinnedNotificationFollowLists(filter: TopFilter): StateFlow<IFeedTopNavFilter> =
        when (filter) {
            TopFilter.AllFollows -> pinnedAllFollowsNotificationLists
            TopFilter.Global -> pinnedGlobalNotificationLists
            else -> topNavFilterFlow(MutableStateFlow(filter))
        }

    val livePollsFollowLists: StateFlow<IFeedTopNavFilter> = selectedTopNavFilterFlow(settings.defaultPollsFollowList)
    val livePollsFollowListsPerRelay = OutboxLoaderState(livePollsFollowLists, cache, scope).flow

    val livePicturesFollowLists: StateFlow<IFeedTopNavFilter> = selectedTopNavFilterFlow(settings.defaultPicturesFollowList)
    val livePicturesFollowListsPerRelay = OutboxLoaderState(livePicturesFollowLists, cache, scope).flow

    val liveRelayGroupsDiscoveryFollowLists: StateFlow<IFeedTopNavFilter> = selectedTopNavFilterFlow(settings.defaultRelayGroupsDiscoveryFollowList)
    val liveRelayGroupsDiscoveryFollowListsPerRelay = OutboxLoaderState(liveRelayGroupsDiscoveryFollowLists, cache, scope).flow

    val liveNappletsFollowLists: StateFlow<IFeedTopNavFilter> = selectedTopNavFilterFlow(settings.defaultNappletsFollowList)
    val liveNappletsFollowListsPerRelay = OutboxLoaderState(liveNappletsFollowLists, cache, scope).flow

    val liveNsitesFollowLists: StateFlow<IFeedTopNavFilter> = selectedTopNavFilterFlow(settings.defaultNsitesFollowList)
    val liveNsitesFollowListsPerRelay = OutboxLoaderState(liveNsitesFollowLists, cache, scope).flow

    val liveWorkoutsFollowLists: StateFlow<IFeedTopNavFilter> = selectedTopNavFilterFlow(settings.defaultWorkoutsFollowList)
    val liveWorkoutsFollowListsPerRelay = OutboxLoaderState(liveWorkoutsFollowLists, cache, scope).flow

    // My Fitness reads the user's own workouts whatever the Workouts feed has selected, so it keys
    // the same workouts data source to a fixed "Mine" instead of defaultWorkoutsFollowList.
    val mineWorkoutsListName = MutableStateFlow<TopFilter>(TopFilter.Mine)
    val liveMineWorkoutsPerRelay = OutboxLoaderState(selectedTopNavFilterFlow(mineWorkoutsListName), cache, scope).flow

    val liveGitRepositoriesFollowLists: StateFlow<IFeedTopNavFilter> = selectedTopNavFilterFlow(settings.defaultGitRepositoriesFollowList)
    val liveGitRepositoriesFollowListsPerRelay = OutboxLoaderState(liveGitRepositoriesFollowLists, cache, scope).flow

    val liveHighlightsFollowLists: StateFlow<IFeedTopNavFilter> = selectedTopNavFilterFlow(settings.defaultHighlightsFollowList)
    val liveHighlightsFollowListsPerRelay = OutboxLoaderState(liveHighlightsFollowLists, cache, scope).flow

    val liveCalendarsFollowLists: StateFlow<IFeedTopNavFilter> = selectedTopNavFilterFlow(settings.defaultCalendarsFollowList)
    val liveCalendarsFollowListsPerRelay = OutboxLoaderState(liveCalendarsFollowLists, cache, scope).flow

    val liveProductsFollowLists: StateFlow<IFeedTopNavFilter> = selectedTopNavFilterFlow(settings.defaultProductsFollowList)
    val liveProductsFollowListsPerRelay = OutboxLoaderState(liveProductsFollowLists, cache, scope).flow

    val liveGeocachesFollowLists: StateFlow<IFeedTopNavFilter> = selectedTopNavFilterFlow(settings.defaultGeocachesFollowList)
    val liveGeocachesFollowListsPerRelay = OutboxLoaderState(liveGeocachesFollowLists, cache, scope).flow

    val liveShortsFollowLists: StateFlow<IFeedTopNavFilter> = selectedTopNavFilterFlow(settings.defaultShortsFollowList)
    val liveShortsFollowListsPerRelay = OutboxLoaderState(liveShortsFollowLists, cache, scope).flow

    val livePublicChatsFollowLists: StateFlow<IFeedTopNavFilter> = selectedTopNavFilterFlow(settings.defaultPublicChatsFollowList)
    val livePublicChatsFollowListsPerRelay = OutboxLoaderState(livePublicChatsFollowLists, cache, scope).flow

    val liveLiveStreamsFollowLists: StateFlow<IFeedTopNavFilter> = selectedTopNavFilterFlow(settings.defaultLiveStreamsFollowList)
    val liveLiveStreamsFollowListsPerRelay = OutboxLoaderState(liveLiveStreamsFollowLists, cache, scope).flow

    val liveNestsFollowLists: StateFlow<IFeedTopNavFilter> = selectedTopNavFilterFlow(settings.defaultNestsFollowList)
    val liveNestsFollowListsPerRelay = OutboxLoaderState(liveNestsFollowLists, cache, scope).flow

    val liveLongsFollowLists: StateFlow<IFeedTopNavFilter> = selectedTopNavFilterFlow(settings.defaultLongsFollowList)
    val liveLongsFollowListsPerRelay = OutboxLoaderState(liveLongsFollowLists, cache, scope).flow

    val liveArticlesFollowLists: StateFlow<IFeedTopNavFilter> = selectedTopNavFilterFlow(settings.defaultArticlesFollowList)
    val liveArticlesFollowListsPerRelay = OutboxLoaderState(liveArticlesFollowLists, cache, scope).flow

    /**
     * The drss river, the folder [drss] reads ([DrssState.readList]), as a feed: the listening
     * queue loads its episodes through it. Built on first use; until a river is picked it points
     * at nothing the queue asks for.
     */
    val drssRiverList: MutableStateFlow<TopFilter> by lazy {
        MutableStateFlow<TopFilter>(drss.readList.value?.let { TopFilter.PeopleList(it) } ?: TopFilter.Mine).also { list ->
            scope.launch { drss.readList.collect { river -> if (river != null) list.value = TopFilter.PeopleList(river) } }
        }
    }
    val liveDrssRiverPerRelay by lazy { OutboxLoaderState(topNavFilterFlow(drssRiverList), cache, scope).flow }

    val liveMusicTracksFollowLists: StateFlow<IFeedTopNavFilter> = selectedTopNavFilterFlow(settings.defaultMusicTracksFollowList)
    val liveMusicTracksFollowListsPerRelay = OutboxLoaderState(liveMusicTracksFollowLists, cache, scope).flow

    val liveMusicPlaylistsFollowLists: StateFlow<IFeedTopNavFilter> = selectedTopNavFilterFlow(settings.defaultMusicPlaylistsFollowList)
    val liveMusicPlaylistsFollowListsPerRelay = OutboxLoaderState(liveMusicPlaylistsFollowLists, cache, scope).flow

    val livePodcastEpisodesFollowLists: StateFlow<IFeedTopNavFilter> = selectedTopNavFilterFlow(settings.defaultPodcastEpisodesFollowList)
    val livePodcastEpisodesFollowListsPerRelay = OutboxLoaderState(livePodcastEpisodesFollowLists, cache, scope).flow

    val livePodcastsFollowLists: StateFlow<IFeedTopNavFilter> = selectedTopNavFilterFlow(settings.defaultPodcastsFollowList)
    val livePodcastsFollowListsPerRelay = OutboxLoaderState(livePodcastsFollowLists, cache, scope).flow

    val liveSoftwareAppsFollowLists: StateFlow<IFeedTopNavFilter> = selectedTopNavFilterFlow(settings.defaultSoftwareAppsFollowList)
    val liveSoftwareAppsFollowListsPerRelay = OutboxLoaderState(liveSoftwareAppsFollowLists, cache, scope).flow

    val liveBadgesFollowLists: StateFlow<IFeedTopNavFilter> = selectedTopNavFilterFlow(settings.defaultBadgesFollowList)
    val liveBadgesFollowListsPerRelay = OutboxLoaderState(liveBadgesFollowLists, cache, scope).flow

    val liveBrowseEmojiSetsFollowLists: StateFlow<IFeedTopNavFilter> = selectedTopNavFilterFlow(settings.defaultBrowseEmojiSetsFollowList)
    val liveBrowseEmojiSetsFollowListsPerRelay = OutboxLoaderState(liveBrowseEmojiSetsFollowLists, cache, scope).flow

    val liveCommunitiesFollowLists: StateFlow<IFeedTopNavFilter> = selectedTopNavFilterFlow(settings.defaultCommunitiesFollowList)
    val liveCommunitiesFollowListsPerRelay = OutboxLoaderState(liveCommunitiesFollowLists, cache, scope).flow

    val liveFollowPacksFollowLists: StateFlow<IFeedTopNavFilter> = selectedTopNavFilterFlow(settings.defaultFollowPacksFollowList)
    val liveFollowPacksFollowListsPerRelay = OutboxLoaderState(liveFollowPacksFollowLists, cache, scope).flow

    // App recommendations are read straight from LocalCache (no relay feed of its
    // own), so only the in-memory author/tag matcher is needed here, not a
    // per-relay outbox loader.
    val liveAppRecommendationsFollowLists: StateFlow<IFeedTopNavFilter> = selectedTopNavFilterFlow(settings.defaultAppRecommendationsFollowList)

    val liveChessFollowLists: StateFlow<IFeedTopNavFilter> = selectedTopNavFilterFlow(settings.defaultChessFollowList)
    val liveChessFollowListsPerRelay = OutboxLoaderState(liveChessFollowLists, cache, scope).flow

    override fun isWriteable(): Boolean = settings.isWriteable()

    suspend fun updateWarnReports(warnReports: Boolean): Boolean {
        if (settings.updateWarnReports(warnReports)) {
            sendNewAppSpecificData()
            return true
        }
        return false
    }

    suspend fun updateReportWarningThreshold(threshold: Int): Boolean {
        if (settings.updateReportWarningThreshold(threshold.coerceAtLeast(1))) {
            sendNewAppSpecificData()
            return true
        }
        return false
    }

    suspend fun updateSendKind0EventsToLocalRelay(send: Boolean): Boolean {
        if (settings.changeSendKind0EventsToLocalRelay(send)) {
            sendNewAppSpecificData()
            return true
        }
        return false
    }

    suspend fun updateAddClientTag(add: Boolean): Boolean {
        if (settings.updateAddClientTag(add)) {
            sendNewAppSpecificData()
            return true
        }
        return false
    }

    suspend fun updatePowDifficulty(difficulty: Int) {
        if (settings.updatePowDifficulty(difficulty)) {
            sendNewAppSpecificData()
        }
    }

    suspend fun updatePowCategory(
        category: PoWCategory,
        enabled: Boolean,
    ) {
        if (settings.updatePowCategory(category, enabled)) {
            sendNewAppSpecificData()
        }
    }

    suspend fun updateFilterSpam(filterSpam: Boolean): Boolean {
        if (settings.updateFilterSpam(filterSpam)) {
            if (!settings.syncedSettings.security.filterSpamFromStrangers.value) {
                hiddenUsers.resetTransientUsers()
            }

            sendNewAppSpecificData()
            return true
        }
        return false
    }

    suspend fun updateShowSensitiveContent(show: Boolean?) {
        if (settings.updateShowSensitiveContent(show)) {
            sendNewAppSpecificData()
        }
    }

    suspend fun updateMaxHashtagLimit(limit: Int) {
        if (settings.updateMaxHashtagLimit(limit)) {
            sendNewAppSpecificData()
        }
    }

    /**
     * Points the account's kind 10040 user-score entries (`30382:rank`, `30382:followers`) at
     * [providerKey] on [relay], replacing any previous provider and keeping every other entry.
     * The trust network then downloads that provider's cards.
     */
    suspend fun setTrustScoreProvider(
        providerKey: HexKey,
        relay: NormalizedRelayUrl,
        isPrivate: Boolean,
    ) {
        // The user is waiting for it: its first download may start on mobile data.
        trustNetwork.expectNewProvider()
        loadOwnTrustProviderListIfMissing()
        sendMyPublicAndPrivateOutbox(trustProviderList.withScoreProvider(providerKey, relay, isPrivate))
    }

    /**
     * Publishes the kind 10040 rows a provider set up for the user (see [TrustProviderRow]),
     * replacing the previous score provider. [rows] must include a `30382:rank` row.
     */
    suspend fun setTrustProviderRows(
        rows: List<TrustProviderRow>,
        isPrivate: Boolean,
    ) {
        trustNetwork.expectNewProvider()
        loadOwnTrustProviderListIfMissing()
        sendMyPublicAndPrivateOutbox(trustProviderList.withProviderRows(rows, isPrivate))
    }

    /**
     * Sends the events a trust provider computes the user's scores from (profile, follow list,
     * mute list, relay list) to [relays], and waits for the relays to answer so a sign-up that
     * follows starts from the current graph. Best effort: returns how many of the events at
     * least one relay accepted, and an event this device does not have is skipped.
     */
    suspend fun shareTrustGraph(relays: Set<NormalizedRelayUrl>): Int {
        if (relays.isEmpty()) return 0
        val events =
            listOfNotNull(
                userMetadata.getUserMetadataEvent(),
                kind3FollowList.getFollowListEvent(),
                muteList.getMuteList(),
                nip65RelayList.getNIP65RelayList(),
            )
        return coroutineScope {
            events.map { async { client.publishAndConfirm(it, relays) } }.awaitAll().count { it }
        }
    }

    /**
     * Adopts a trust provider's network [relay]: adds it to the trusted, search, indexer and
     * favorite relay lists (each only when missing, keeping what the list had) and moves every
     * feed set to Global onto it.
     */
    suspend fun adoptTrustProviderRelay(relay: NormalizedRelayUrl) {
        trustedRelayList.addRelay(relay)?.let { sendMyPublicAndPrivateOutbox(it) }
        searchRelayList.withRelay(relay)?.let { saveSearchRelayList(it) }
        indexerRelayList.withRelay(relay)?.let { saveIndexerRelayList(it) }
        if (relay !in favoriteRelayList.flowNoDefaults.value) followRelayFeed(relay)
        settings.moveGlobalFeedsTo(TopFilter.Relay(relay.url))
    }

    /**
     * Before a rewrite of the kind 10040: on a device that has not received the user's list yet
     * (a fresh login), building on nothing would publish a list with only the new rows and drop
     * every row other clients keep there (other providers, Trusted Lists).
     */
    private suspend fun loadOwnTrustProviderListIfMissing() {
        if (trustProviderList.getTrustProviderList() != null || settings.backupTrustProviderList != null) return
        newestTrustProviderList(signer.pubKey)?.let { cache.justConsumeMyOwnEvent(it) }
    }

    /**
     * The public kind 10040 rows of [pubkey]'s list, from the newest version found on their
     * outbox, the index relays and [hints]; null when no list was found. Copying them into the
     * user's own list ([setTrustProviderRows]) shows the network as [pubkey] sees it.
     */
    suspend fun fetchTrustProviderRowsOf(
        pubkey: HexKey,
        hints: Set<NormalizedRelayUrl> = emptySet(),
    ): List<TrustProviderRow>? = newestTrustProviderList(pubkey, hints)?.publicRows()

    /** [pubkey]'s newest kind 10040 in the cache or on their outbox, the index relays and [hints]. */
    private suspend fun newestTrustProviderList(
        pubkey: HexKey,
        hints: Set<NormalizedRelayUrl> = emptySet(),
    ): TrustProviderListEvent? {
        val cached = cache.getAddressableNoteIfExists(TrustProviderListEvent.createAddress(pubkey))?.event as? TrustProviderListEvent
        val relays = cache.getUserIfExists(pubkey)?.outboxRelays().orEmpty() + indexRelays() + hints
        val filter = Filter(kinds = listOf(TrustProviderListEvent.KIND), authors = listOf(pubkey), limit = 1)
        // fetchAll neither verifies nor stores: any relay asked could forge a newer list and pick
        // the provider the user is about to publish. Newest first, the first that verifies.
        val fetched =
            client
                .fetchAll(filters = relays.associateWith { listOf(filter) }, idleTimeoutMs = 8_000)
                .filter { it.kind == TrustProviderListEvent.KIND && it.pubKey == pubkey }
                .sortedByDescending { it.createdAt }
                .firstOrNull { it.verify() }
        return listOfNotNull(cached, fetched as? TrustProviderListEvent).maxByOrNull { it.createdAt }
    }

    /** Removes the user-score provider from the kind 10040, which turns Web of Trust filtering off. */
    suspend fun removeTrustScoreProvider() {
        loadOwnTrustProviderListIfMissing()
        trustProviderList.withoutScoreProvider()?.let { sendMyPublicAndPrivateOutbox(it) }
    }

    suspend fun updateMinTrustScore(score: Int) {
        if (settings.updateMinTrustScore(score)) {
            sendNewAppSpecificData()
        }
    }

    /** The reaction choices counterpart of [applyBottomBarItems] — same synchronous-apply, publish-after contract. */
    fun applyReactionTypes(reactionSet: List<String>): Boolean = settings.changeReactionTypes(reactionSet)

    suspend fun changeReactionRowItems(items: List<ReactionRowItem>) {
        if (settings.changeReactionRowItems(items)) {
            sendNewAppSpecificData()
        }
    }

    suspend fun changeCaptionsEnabled(enabled: Boolean) {
        if (settings.changeCaptionsEnabled(enabled)) {
            sendNewAppSpecificData()
        }
    }

    suspend fun changeVideoPlayerButtonItems(items: List<VideoPlayerButtonItem>) {
        if (settings.changeVideoPlayerButtonItems(items)) {
            sendNewAppSpecificData()
        }
    }

    suspend fun changeAudioVisualizer(style: VisualizerStyle) {
        if (settings.changeAudioVisualizer(style)) {
            sendNewAppSpecificData()
        }
    }

    /**
     * Applies the new bottom-bar list to the reactive synced-settings flow and returns whether it
     * changed. Non-suspending and only touches in-memory state, so callers invoke it synchronously on
     * the UI thread — rapid edits then stay strictly ordered instead of racing on the multi-threaded
     * signer dispatcher (an out-of-order write would revert the newer edit, which the settings screen
     * re-seeds from this flow). Pair a `true` result with [sendNewAppSpecificData] to publish.
     */
    fun applyBottomBarItems(items: List<BottomBarEntry>): Boolean = settings.changeBottomBarItems(items)

    /** The drawer counterpart of [applyBottomBarItems] — same synchronous-apply, publish-after contract. */
    fun applyHiddenDrawerItems(items: Set<NavBarItem>): Boolean = settings.changeHiddenDrawerItems(items)

    suspend fun toggleChatroomPin(room: ChatroomKey) {
        settings.toggleChatroomPin(room)
        sendNewAppSpecificData()
    }

    /**
     * Local state first, then publish. The local write is what every suppression point
     * reads, so it must not wait on the signer — publishing is best-effort sync.
     */
    suspend fun toggleMutedPublicChat(channelId: String) {
        settings.toggleMutedPublicChat(channelId)
        sendNewAppSpecificData()
    }

    suspend fun updateZapAmounts(
        amountSet: List<Long>,
        selectedZapType: ZapReceiptEvent.ZapType,
        nip47Update: Nip47WalletConnect.Nip47URINorm?,
    ) {
        var changed = false

        if (settings.changeZapAmounts(amountSet)) changed = true
        if (settings.changeDefaultZapType(selectedZapType)) changed = true
        if (settings.changeZapPaymentRequest(nip47Update)) changed = true

        if (changed) {
            sendNewAppSpecificData()
        }
    }

    suspend fun toggleDontTranslateFrom(languageCode: String) {
        settings.toggleDontTranslateFrom(languageCode)
        sendNewAppSpecificData()
    }

    suspend fun addDontTranslateFrom(languageCode: String) {
        settings.addDontTranslateFrom(languageCode)
        sendNewAppSpecificData()
    }

    suspend fun removeDontTranslateFrom(languageCode: String) {
        settings.removeDontTranslateFrom(languageCode)
        sendNewAppSpecificData()
    }

    suspend fun updateTranslateTo(languageCode: String) {
        if (settings.updateTranslateTo(languageCode)) {
            sendNewAppSpecificData()
        }
    }

    suspend fun prefer(
        source: String,
        target: String,
        preference: String,
    ) {
        settings.prefer(source, target, preference)
        sendNewAppSpecificData()
    }

    suspend fun sendNewAppSpecificData() = sendMyPublicAndPrivateOutbox(appSpecific.saveNewAppSpecificData())

    /** Reverts the toggle when no relay accepted, so the setting never claims a missing backup. */
    suspend fun enableNwcBackup(): Boolean {
        settings.changeBackupNwcWallets(true)
        val published = nwcBackup.publishNow()
        if (!published) {
            settings.changeBackupNwcWallets(false)
        }
        return published
    }

    suspend fun disableNwcBackup() {
        settings.changeBackupNwcWallets(false)
    }

    suspend fun deleteNwcBackup(): NwcBackupState.DeleteOutcome {
        settings.changeBackupNwcWallets(false)
        return nwcBackup.deleteBackup()
    }

    // ---
    // NIP-13 proof-of-work publishing
    // ---

    /**
     * Difficulty to mine [kind] at per this account's NIP-13 settings, or null
     * when the kind publishes immediately: master difficulty off, category
     * disabled, or one of [PoWPolicy]'s hard-excluded kinds (auth, zap
     * requests, NWC/bunker RPC, drafts, lists…).
     */
    fun powDifficultyFor(kind: Int): Int? =
        PoWPolicy.shouldMine(
            kind = kind,
            difficulty = settings.syncedSettings.proofOfWork.difficulty.value,
            enabledCategories = settings.syncedSettings.proofOfWork.enabledCategories.value,
        )

    /**
     * [powDifficultyFor] with a per-post override from the composer chip:
     * null defers to the account settings, 0 disables mining for this post,
     * a positive value forces that difficulty (hard-excluded kinds still win).
     */
    fun powDifficultyFor(
        kind: Int,
        overrideDifficulty: Int?,
    ): Int? =
        when {
            overrideDifficulty == null -> powDifficultyFor(kind)
            overrideDifficulty <= 0 -> null
            PoWPolicy.neverMine(kind) -> null
            else -> overrideDifficulty
        }

    /**
     * Parallel workers a single nonce search should use — the mining queue's
     * per-job budget (half the device's cores). 1 when no queue is wired.
     * Callers that run [PoWMiner] inside a queued job must pass this so the
     * job stays inside the queue's CPU budget.
     */
    fun powMinerWorkers(): Int = powQueue()?.minerThreads ?: 1

    /**
     * Enqueues [work] into the fire-and-forget mining queue. Returns false when
     * no queue is wired (headless/test accounts): callers must then run their
     * direct, un-mined send path instead.
     */
    fun mineInBackground(
        kind: Int,
        difficulty: Int,
        work: suspend (isActive: () -> Boolean) -> Unit,
    ): Boolean {
        val queue = powQueue() ?: return false
        queue.enqueueWork(kind, difficulty, owner = signer.pubKey, work = work)
        return true
    }

    /**
     * Enqueues [template] to be mined at [difficulty] and then handed to
     * [onMined], which should run the exact sign+send path the caller would
     * have used without PoW. Returns false when no queue is wired.
     *
     * When [replay] is given the job is checkpointed to disk so it survives
     * process death: on the next login the restorer re-mines the persisted
     * template and finishes it with the (headless) replay path instead of
     * [onMined]. Pass null for content that must not touch disk.
     *
     * The template is normalized to the final tag shape the signer will submit
     * (client tag included) before mining — a tag appended after mining would
     * invalidate the nonce.
     */
    fun <T : Event> mineTemplateInBackground(
        template: EventTemplate<T>,
        difficulty: Int,
        replay: PoWReplay? = null,
        onMined: suspend (EventTemplate<T>) -> Unit,
    ): Boolean {
        val queue = powQueue() ?: return false
        val finalTemplate = withFinalSignerTags(template)
        val record = replay?.toRecord(RandomInstance.randomChars(16), signer.pubKey, finalTemplate, difficulty)
        queue.enqueue(
            template = finalTemplate,
            pubKey = signer.pubKey,
            difficulty = difficulty,
            persistAs = record,
            // NIP-13 recommends refreshing created_at while mining; scheduled
            // posts keep their intentional future timestamp.
            refreshCreatedAt = replay !is PoWReplay.Schedule,
            onMined = onMined,
        )
        return true
    }

    /**
     * The one-liner for template send paths: when [template]'s kind should be
     * mined (per settings and the optional composer [overrideDifficulty]),
     * enqueue it and run [send] with the mined template once the nonce is
     * found; otherwise run [send] with [template] right now.
     */
    suspend fun <T : Event> sendMined(
        template: EventTemplate<T>,
        replay: PoWReplay?,
        overrideDifficulty: Int? = null,
        send: suspend (EventTemplate<T>) -> Unit,
    ) {
        val difficulty = powDifficultyFor(template.kind, overrideDifficulty)
        if (difficulty == null || !mineTemplateInBackground(template, difficulty, replay, send)) {
            send(template)
        }
    }

    /**
     * Queues wrap mining for pre-signed [seals] (see NIP17Factory.createSeals):
     * each seal gets its ephemeral-key envelope mined at [difficulty] on the
     * worker pool, then the wraps broadcast. Checkpointed under
     * [PersistedPoWJob.REPLAY_WRAPS] (the seals are already-signed ciphertext,
     * safe to persist) unless an [existingRecord] from the restorer is passed.
     * Returns false when no queue is wired.
     */
    fun mineWrapsInBackground(
        seals: List<NIP17Factory.AddressedSeal>,
        expirationDelta: Long?,
        difficulty: Int,
        existingRecord: PersistedPoWJob? = null,
        // The inner rumor's id (the note the chat feed displays), so the mined
        // wraps still register with the delivery-ticks tracker at publish time.
        // Null for restart-restored jobs, whose rumor id wasn't persisted.
        displayedNoteId: HexKey? = null,
        // Runs once the mined wraps are handed to the relays (e.g. to drop the
        // composer's draft). Not persisted: a restart-restored job skips it.
        onPublished: suspend () -> Unit = {},
    ): Boolean {
        val queue = powQueue() ?: return false
        if (seals.isEmpty()) return true

        val record =
            existingRecord
                ?: PersistedPoWJob(
                    id = RandomInstance.randomChars(16),
                    accountPubkey = signer.pubKey,
                    kind = GiftWrapEvent.KIND,
                    difficulty = difficulty,
                    templateJson = "",
                    replayType = PersistedPoWJob.REPLAY_WRAPS,
                    extraEventsJson = seals.map { it.seal.toJson() },
                    recipientPubkeys = seals.map { it.recipient },
                    wrapExpirationDelta = expirationDelta,
                    createdAtSec = TimeUtils.now(),
                )

        queue.enqueueStaged(
            kind = GiftWrapEvent.KIND,
            difficulty = difficulty,
            persistAs = record,
            mine = { isActive ->
                // the wrap's ephemeral key is generated inside the wrap build;
                // the conversion hook hands its pubkey back so the nonce can
                // commit to it. Single-threaded on purpose: the conversion is
                // a non-suspend hook deep inside the synchronous NIP-59 wrap
                // build, so it can't race PoWMiner.mine workers.
                val mineWrap: GiftWrapTemplateConversion = { template, ephemeralPubKey ->
                    PoWMiner.run(template, ephemeralPubKey, difficulty, isActive)
                }
                seals.map { NIP17Factory().wrapSeal(it, expirationDelta, templateConversion = mineWrap) }
            },
            publish = { wraps ->
                broadcastPrivately(wraps, displayedNoteId)
                onPublished()
            },
        )
        return true
    }

    private fun <T : Event> withFinalSignerTags(template: EventTemplate<T>): EventTemplate<T> {
        val currentSigner = signer
        if (currentSigner !is NostrSignerWithClientTag) return template

        val finalTags = currentSigner.prepareTags(template.tags)
        if (finalTags === template.tags) return template

        return EventTemplate(template.createdAt, template.kind, finalTags, template.content)
    }

    /**
     * A signer that mines [kindsToMine] at [difficulty] right before signing.
     * When the account signer stamps a client tag, the miner is layered inside
     * it so mining runs over the final tag set.
     */
    private fun miningSigner(
        difficulty: Int,
        kindsToMine: Set<Int>,
        isActive: () -> Boolean,
    ): NostrSigner {
        val currentSigner = signer
        val workers = powMinerWorkers()
        return if (currentSigner is NostrSignerWithClientTag) {
            NostrSignerWithClientTag(
                inner = PoWNostrSigner(currentSigner.inner, difficulty, kindsToMine, isActive, workers, TimeUtils::now),
                clientTag = currentSigner.clientTag,
                disabled = currentSigner.disabled,
            )
        } else {
            PoWNostrSigner(currentSigner, difficulty, kindsToMine, isActive, workers, TimeUtils::now)
        }
    }

    /**
     * True, after logging it, when [note] belongs to a [MessageRoom]. Everything about a room's
     * messages is the room's to send: the public paths here would name the message in a signed
     * event on relays. Refusing makes an action that forgot to ask the room fail instead of leak.
     */
    private fun refusesRoomNote(
        note: Note,
        action: String,
    ): Boolean {
        if (!note.isInRoom()) return false
        Log.e("Account") { "Refused to $action ${note.idHex} on a public path: it belongs to a room" }
        return true
    }

    suspend fun reactTo(
        note: Note,
        reaction: String,
    ) {
        if (refusesRoomNote(note, "react to")) return

        // Reactions to NIP-17 groups and unsealed rumors are gift-wrapped: the
        // inner kind-7 only ever travels as ciphertext, so mining it is pure
        // waste — those targets skip the queue and sign with the plain signer.
        val isPrivateTarget = note.event is NIP17Group || note.isPrivateRumor()

        val powDifficulty = if (isPrivateTarget) null else powDifficultyFor(ReactionEvent.KIND)
        if (powDifficulty != null) {
            val queue = powQueue()
            if (queue != null) {
                // toggle semantics while mining: a second tap on the same
                // reaction un-likes by cancelling the pending job instead of
                // publishing a duplicate (the mined event doesn't exist yet,
                // so hasReacted can't dedupe).
                val dedupeKey = "reaction:${note.idHex}:$reaction"
                if (queue.cancelByKey(dedupeKey)) return

                queue.enqueueWork(ReactionEvent.KIND, powDifficulty, dedupeKey, owner = signer.pubKey) { isActive ->
                    ReactionAction.reactTo(
                        note = note,
                        reaction = reaction,
                        by = userProfile(),
                        signer = miningSigner(powDifficulty, setOf(ReactionEvent.KIND), isActive),
                        onPublic = ::sendAutomatic,
                        onPrivate = ::broadcastPrivately,
                    )
                }
                return
            }
        }

        ReactionAction.reactTo(
            note = note,
            reaction = reaction,
            by = userProfile(),
            signer = signer,
            onPublic = ::sendAutomatic,
            onPrivate = ::broadcastPrivately,
        )
    }

    /**
     * Creates a reaction event without sending it.
     * Returns the event and target relays for tracked broadcasting.
     * Returns null if note has already been reacted to or note has no event.
     */
    suspend fun createReactionEvent(
        note: Note,
        reaction: String,
    ): Pair<Event, Set<NormalizedRelayUrl>>? {
        if (!signer.isWriteable()) return null
        if (note.hasReacted(userProfile(), reaction)) return null

        val eventHint = note.toEventHint<Event>() ?: return null

        // For NIP-17 private groups, we don't support tracked mode (too complex).
        // Unsealed rumors (empty sig) must never get a public reaction —
        // the e-tag would leak the private rumor id to public relays.
        if (eventHint.event is NIP17Group || eventHint.event.sig.isEmpty()) return null

        val event = ReactionAction.reactTo(eventHint, reaction, signer)
        val relays = computeRelayListToBroadcast(event)

        return event to relays
    }

    /**
     * Consumes a reaction event into local cache.
     * Called when tracked broadcasting succeeds.
     */
    fun consumeReactionEvent(event: Event) {
        cache.justConsumeMyOwnEvent(event)
    }

    /**
     * NIP-32: tags [note] with [hashtag] by publishing a kind 1985 label event using the
     * `#t` tag-association namespace. Fire-and-forget; signs and broadcasts immediately.
     */
    suspend fun labelHashtag(
        note: Note,
        hashtag: String,
    ) {
        createLabelHashtagEvent(note, hashtag)?.let { (event, relays) ->
            cache.justConsumeMyOwnEvent(event)
            client.publish(event, relays)
        }
    }

    /**
     * Builds and signs a NIP-32 hashtag label event for [note] without sending it.
     * Returns the signed event and target relays for tracked broadcasting, or null if
     * the account can't write or the note has no underlying event.
     */
    suspend fun createLabelHashtagEvent(
        note: Note,
        hashtag: String,
    ): Pair<Event, Set<NormalizedRelayUrl>>? {
        if (!signer.isWriteable()) return null

        val eventHint = note.toEventHint<Event>() ?: return null

        val template = LabelEvent.buildHashtagLabel(eventHint, hashtag)

        val event = signer.sign(template)
        val relays = computeRelayListToBroadcast(event)

        return event to relays
    }

    /**
     * Consumes a label event into local cache. Called when tracked broadcasting succeeds.
     */
    fun consumeLabelEvent(event: Event) {
        cache.justConsumeMyOwnEvent(event)
    }

    suspend fun report(
        note: Note,
        type: ReportType,
        content: String = "",
    ) {
        if (refusesRoomNote(note, "report")) return
        if (note.isPrivateRumor()) {
            // A kind-1984 e-tagging the rumor would leak the private id onto
            // public relays. Report the author instead (p-tag only).
            note.author?.let { report(it, type, content) }
            return
        }

        val powDifficulty = powDifficultyFor(ReportEvent.KIND)
        if (powDifficulty != null &&
            mineInBackground(ReportEvent.KIND, powDifficulty) { isActive ->
                sendMyPublicAndPrivateOutbox(
                    ReportAction.report(note, type, content, userProfile(), miningSigner(powDifficulty, setOf(ReportEvent.KIND), isActive)),
                )
            }
        ) {
            return
        }

        sendMyPublicAndPrivateOutbox(ReportAction.report(note, type, content, userProfile(), signer))
    }

    suspend fun report(
        user: User,
        type: ReportType,
        content: String = "",
    ) {
        val powDifficulty = powDifficultyFor(ReportEvent.KIND)
        if (powDifficulty != null &&
            mineInBackground(ReportEvent.KIND, powDifficulty) { isActive ->
                sendMyPublicAndPrivateOutbox(
                    ReportAction.report(user, type, content, userProfile(), miningSigner(powDifficulty, setOf(ReportEvent.KIND), isActive)),
                )
            }
        ) {
            return
        }

        sendMyPublicAndPrivateOutbox(ReportAction.report(user, type, content, userProfile(), signer))
    }

    suspend fun delete(note: Note) = delete(listOf(note))

    suspend fun delete(notes: List<Note>) {
        if (!isWriteable()) return

        val (myRumors, myNotes) =
            notes
                .filterNot { refusesRoomNote(it, "delete") }
                .filter { it.author == userProfile() && it.event != null }
                .partition { it.isPrivateRumor() }

        // Private rumors (NIP-17 DMs, private reactions) are retracted inside their own
        // conversation for the same reason, whichever caller asked for a plain delete.
        myRumors.forEach { deletePrivately(listOf(it), it) }

        if (myNotes.isNotEmpty()) {
            // chunks in 200 elements to avoid going over the 65KB limit for events.
            myNotes.chunked(DeletionActions.MAX_TARGETS_PER_EVENT).forEach { chunkedList ->
                val template = DeletionRequestEvent.build(chunkedList.mapNotNull { it.event })
                val deletionEvent = signer.sign(template)
                client.publish(deletionEvent, broadcaster.planner.computeDeletionRelays(chunkedList))
                cache.justConsumeMyOwnEvent(deletionEvent)
            }
        }
    }

    /**
     * Retracts rumor-only events (private reactions/replies) with a
     * gift-wrapped NIP-09 deletion delivered to the same participants as
     * the [target] rumor they referenced. A public deletion would e-tag
     * the private rumor ids onto public relays.
     */
    suspend fun deletePrivately(
        notes: List<Note>,
        target: Note,
    ) {
        if (!isWriteable()) return
        val targetEvent = target.event ?: return
        // A room's deletion goes to the room, not to the target's author as a DM.
        if (refusesRoomNote(target, "delete privately around")) return

        val myRumors = notes.filter { it.author == userProfile() }.mapNotNull { it.event }
        if (myRumors.isEmpty()) return

        val recipients = (targetEvent.taggedUserIds() + targetEvent.pubKey).distinct().minus(signer.pubKey)
        broadcastPrivately(
            NIP17Factory().createDeletionNIP17(DeletionRequestEvent.build(myRumors), recipients, signer),
        )
    }

    suspend fun delete(
        event: Event,
        additionalRelays: Set<NormalizedRelayUrl>,
    ) {
        if (!isWriteable()) return
        if (event.pubKey != signer.pubKey) return

        val deletionEvent = signer.sign(DeletionRequestEvent.build(listOf(event)))
        client.publish(deletionEvent, outboxRelays.flow.value + additionalRelays)
        cache.justConsumeMyOwnEvent(deletionEvent)
    }

    suspend fun createHTTPAuthorization(
        url: String,
        method: String,
        body: ByteArray? = null,
    ): HTTPAuthorizationEvent = signer.sign(HTTPAuthorizationEvent.build(url, method, body))

    suspend fun createBlossomUploadAuth(
        hash: HexKey,
        size: Long,
        alt: String,
        servers: List<String> = emptyList(),
    ) = blossomServers.createBlossomUploadAuth(hash, size, alt, servers)

    suspend fun createBlossomMediaAuth(
        hash: HexKey,
        size: Long,
        alt: String,
        servers: List<String> = emptyList(),
    ) = blossomServers.createBlossomMediaAuth(hash, size, alt, servers)

    suspend fun createBlossomDeleteAuth(
        hash: HexKey,
        alt: String,
        servers: List<String> = emptyList(),
    ) = blossomServers.createBlossomDeleteAuth(hash, alt, servers)

    suspend fun createBlossomListAuth(
        alt: String,
        servers: List<String> = emptyList(),
    ) = blossomServers.createBlossomListAuth(alt, servers)

    suspend fun boost(note: Note) {
        val powDifficulty = powDifficultyFor(RepostEvent.KIND)
        if (powDifficulty != null &&
            mineInBackground(RepostEvent.KIND, powDifficulty) { isActive ->
                repostNow(note, miningSigner(powDifficulty, setOf(RepostEvent.KIND, GenericRepostEvent.KIND), isActive))
            }
        ) {
            return
        }

        repostNow(note, signer)
    }

    private suspend fun repostNow(
        note: Note,
        repostSigner: NostrSigner,
    ) {
        RepostAction.repost(note, repostSigner)?.let { event ->
            client.publish(event, computeMyReactionToNote(note, event))
            cache.justConsumeMyOwnEvent(event)
        }
    }

    /**
     * Creates a boost event without sending it.
     * Returns the event and target relays for tracked broadcasting.
     */
    suspend fun createBoostEvent(note: Note): Pair<Event, Set<NormalizedRelayUrl>>? =
        RepostAction.repost(note, signer)?.let { event ->
            event to computeMyReactionToNote(note, event)
        }

    /**
     * Sends a boost event and updates the local cache.
     * Used after tracked broadcasting completes.
     */
    fun sendBoostEvent(
        event: Event,
        relays: Set<NormalizedRelayUrl>,
    ) {
        client.publish(event, relays)
        cache.justConsumeMyOwnEvent(event)
    }

    /**
     * Updates the local cache with a boost event.
     * Called when tracked broadcasting succeeds.
     */
    fun consumeBoostEvent(event: Event) {
        cache.justConsumeMyOwnEvent(event)
    }

    fun computeMyReactionToNote(
        note: Note,
        reaction: Event,
    ): Set<NormalizedRelayUrl> = broadcaster.planner.computeMyReactionToNote(note, reaction)

    // ------------------------------------------------------------------
    // Broadcast / relay-routing delegates (logic lives in EventBroadcaster).
    // ------------------------------------------------------------------

    fun computeRelayListToBroadcast(event: Event): Set<NormalizedRelayUrl> = broadcaster.computeRelayListToBroadcast(event)

    fun computeRelayListToBroadcast(note: Note): Set<NormalizedRelayUrl> = broadcaster.computeRelayListToBroadcast(note)

    suspend fun broadcast(note: Note) = broadcaster.broadcast(note)

    fun sendAutomatic(events: List<Event>) = broadcaster.sendAutomatic(events)

    fun sendAutomatic(event: Event?) = broadcaster.sendAutomatic(event)

    fun sendMyPublicAndPrivateOutbox(event: Event?) = broadcaster.sendMyPublicAndPrivateOutbox(event)

    fun sendMyPublicAndPrivateOutbox(events: List<Event>) = broadcaster.sendMyPublicAndPrivateOutbox(events)

    fun sendLiterallyEverywhere(event: Event) = broadcaster.sendLiterallyEverywhere(event)

    /**
     * Resolves a [ReplaceableBackupConflict] in favor of this device: re-signs the saved
     * version (same kind, tags and content, so NIP-44 private items stay readable, minus the
     * old client, proof-of-work and past expiration tags; see [BackupRestore.tagsToResign])
     * with a timestamp newer than the current one, and publishes it so it replaces that version.
     *
     * Does nothing if the conflict is stale (already resolved, replaced by a newer one, or
     * dropped because the event was deleted), which also makes a double tap harmless.
     */
    suspend fun restoreBackupOver(conflict: ReplaceableBackupConflict) {
        val token = settings.backupGuard.startRestoring(conflict) ?: return
        var restored = false
        try {
            val saved = conflict.saved
            val now = TimeUtils.now()
            // Newer than the version it replaces, but never far in the future: relays reject
            // those, and later edits signed at "now" would lose to it. A far-future external
            // version can't be outranked safely, and a restore signed at "now" would lose to it
            // everywhere, so it isn't sent: the conflict reopens and only keeping it works.
            val createdAt = (conflict.incoming.createdAt + 1).coerceAtLeast(now)
            if (createdAt > now + maxRestoreFutureSeconds) return
            val resigned = signer.sign<Event>(createdAt, saved.kind, BackupRestore.tagsToResign(saved, now), saved.content)
            broadcaster.sendRestoredVersion(resigned)
            restored = true
        } finally {
            settings.backupGuard.finishRestoring(conflict, token, restored)
        }
    }

    private val maxRestoreFutureSeconds = 15 * 60L

    /** Resolves a [ReplaceableBackupConflict] in favor of the current version. No-op when stale. */
    fun acceptExternalVersion(conflict: ReplaceableBackupConflict) = settings.backupGuard.keepIncoming(conflict)

    suspend fun <T : Event> signAndSendPrivately(
        template: EventTemplate<T>,
        relayList: Set<NormalizedRelayUrl>,
    ) = broadcaster.signAndSendPrivately(template, relayList)

    suspend fun <T : Event> signWithAndSendPrivately(
        template: EventTemplate<T>,
        signer: NostrSigner,
        relayList: Set<NormalizedRelayUrl>,
    ): T = broadcaster.signWithAndSendPrivately(template, signer, relayList)

    suspend fun <T : Event> signAndSendPrivatelyOrBroadcast(
        template: EventTemplate<T>,
        relayList: (T) -> List<NormalizedRelayUrl>?,
    ): T = broadcaster.signAndSendPrivatelyOrBroadcast(template, relayList)

    suspend fun <T : Event> signAndComputeBroadcast(
        template: EventTemplate<T>,
        broadcast: List<Event> = emptyList(),
    ): T = broadcaster.signAndComputeBroadcast(template, broadcast)

    suspend fun <T : Event> signAnonymouslyAndBroadcast(
        template: EventTemplate<T>,
        broadcast: List<Event> = emptyList(),
        anonymousSigner: NostrSigner = NostrSignerInternal(KeyPair()),
    ): T = broadcaster.signAnonymouslyAndBroadcast(template, broadcast, anonymousSigner)

    fun republishEventsTo(
        events: List<Event>,
        relays: Set<NormalizedRelayUrl>,
    ) = broadcaster.republishEventsTo(events, relays)

    fun upgradeAttestations() = otsState.upgradeAttestationsIfNeeded(::sendAutomatic)

    suspend fun follow(users: List<User>) = sendMyPublicAndPrivateOutbox(kind3FollowList.follow(users))

    suspend fun follow(user: User) = sendMyPublicAndPrivateOutbox(kind3FollowList.follow(user))

    suspend fun unfollow(user: User) = sendMyPublicAndPrivateOutbox(kind3FollowList.unfollow(user))

    suspend fun follow(channel: PublicChatChannel) = sendMyPublicAndPrivateOutbox(publicChatList.follow(channel))

    suspend fun unfollow(channel: PublicChatChannel) = sendMyPublicAndPrivateOutbox(publicChatList.unfollow(channel))

    suspend fun follow(channel: EphemeralChatChannel) = sendMyPublicAndPrivateOutbox(ephemeralChatList.follow(channel))

    suspend fun unfollow(channel: EphemeralChatChannel) = sendMyPublicAndPrivateOutbox(ephemeralChatList.unfollow(channel))

    suspend fun follow(channel: RelayGroupChannel) = sendMyPublicAndPrivateOutbox(relayGroupList.follow(channel))

    suspend fun unfollow(channel: RelayGroupChannel) = sendMyPublicAndPrivateOutbox(relayGroupList.unfollow(channel))

    /**
     * Post [text] into [rootNote]'s minichat — a kind-1111 thread reply rooted at that
     * message. Resolves the chat context from the note's gatherer; today it drives the
     * Concord channel path (NIP-28/NIP-29 public-chat minichats are a follow-up). Returns
     * false if the message isn't in a chat we can post a thread reply to.
     */
    suspend fun sendMinichatReply(
        rootNote: Note,
        text: String,
        imetas: List<IMetaTag> = emptyList(),
    ): Boolean {
        if (!isWriteable()) return false
        val gatherers = rootNote.inGatherers

        gatherers?.firstNotNullOfOrNull { it as? ConcordChannel }?.let { concord ->
            return this.concord.sendConcordChannelMessage(
                concord.channelId.communityId,
                concord.channelId.channelId,
                text,
                rootNote,
                ReplyMode.MINICHAT,
                imetas,
            )
        }

        // Public chats: a plain public kind-1111 comment rooted at the message. NIP-29 groups
        // additionally carry the `h` tag and go only to the host relay. Attached media rides as
        // NIP-92 `imeta` tags, with each URL appended to the content so any client renders it.
        val rootEvent = rootNote.event ?: return false

        // Resolve @/nostr: mentions the same way the full composer does, so a member cited in a
        // quick reply is notified (`p`) and their reference resolves. The reply-parent author is
        // already tagged by each builder below, so drop it from the body mentions to avoid a
        // duplicate `p`.
        // Buzz shows a `nostr:` reference verbatim, so a Buzz thread names people as plain `@Name`.
        val buzzGroup = gatherers?.firstNotNullOfOrNull { it as? RelayGroupChannel }?.takeIf { BuzzRelayDialect.isBuzz(it.groupId.relayUrl) }
        val tagger = NewMessageTagger(text, dao = LocalCache, userMentionsAsNames = buzzGroup != null)
        tagger.run()
        val mentions = tagger.pTags?.mapNotNull { it.pubkeyHex.takeIf { pk -> pk != rootEvent.pubKey } }.orEmpty()
        val finalText = appendMediaUrls(tagger.message, imetas)

        gatherers?.firstNotNullOfOrNull { it as? PublicChatChannel }?.let { chat ->
            val relays = chat.relays()
            val signed =
                signer.sign(
                    CommentEvent.replyBuilder(finalText, EventHintBundle(rootEvent, relays.firstOrNull())) {
                        notify(mentions.map { PTag(it) })
                        imetas(imetas)
                    },
                )
            cache.justConsumeMyOwnEvent(signed)
            client.publish(signed, relays.ifEmpty { outboxRelays.flow.value })
            return true
        }

        gatherers?.firstNotNullOfOrNull { it as? RelayGroupChannel }?.let { group ->
            val hostRelay = group.groupId.relayUrl
            val signed =
                if (BuzzRelayDialect.isBuzz(hostRelay)) {
                    // Buzz rejects kind-1111, so its minichat threads with a NIP-10 `reply`-marked `e`
                    // on a plain kind-9 chat — byte-identical to `_buildReplyTags` in Buzz's own client
                    // (direct reply -> one `reply` marker; nested -> `root` + `reply`), which is what
                    // [buzzThread] emits.
                    //
                    // This used to write kind-40002. Nothing in Buzz writes 40002 any more — every send
                    // path in their mobile, desktop and CLI clients emits kind 9, and their NOSTR.md
                    // grades 40002 "Buzz-only — no standard NIP-29 client renders these" against kind 9's
                    // blessed status. 40002 survives only as a read-compat tail from the
                    // 10002 -> 40001 -> 40002 migration, so we were the last active writer of a kind
                    // their clients no longer thread on. Reading 40002 stays supported (see
                    // [com.vitorpamplona.amethyst.commons.model.chats.isMinichatReply]).
                    //
                    // Attached media rides as URLs appended to the content, plus Buzz-shaped `imeta`.
                    val root = rootEvent.tags.buzzThreadRootForReplyTo(rootEvent.id)
                    signer.sign(
                        // Buzz draws an attachment only where the body links it as `![image](url)`.
                        ChatEvent.build(BuzzImeta.markdownMediaBody(finalText, imetas)) {
                            hTag(group.groupId.id)
                            buzzThread(root, rootEvent.id)
                            rootNote.author?.pubkeyHex?.let { pTag(PTag(it)) }
                            pTags(mentions.map { PTag(it) })
                            previous(group.previousEventRefs(pubKey))
                            // Buzz refuses the whole event over any imeta key it doesn't allow.
                            imetas(BuzzImeta.sanitize(imetas))
                        },
                    )
                } else {
                    signer.sign(
                        CommentEvent.replyBuilder(finalText, EventHintBundle(rootEvent, hostRelay)) {
                            hTag(group.groupId.id)
                            previous(group.previousEventRefs(pubKey))
                            notify(mentions.map { PTag(it) })
                            imetas(imetas)
                        },
                    )
                }
            cache.justConsumeMyOwnEvent(signed)
            client.publish(signed, setOf(hostRelay))
            return true
        }

        return false
    }

    /**
     * Appends each attachment URL not already present in [text] to the message content (newline
     * separated), so a plaintext media link renders inline in any client — mirroring
     * [com.vitorpamplona.quartz.concord.cord03Channels.ChannelChat.imageMessage]. Returns [text]
     * unchanged when there are no attachments.
     */
    private fun appendMediaUrls(
        text: String,
        imetas: List<IMetaTag>,
    ): String {
        if (imetas.isEmpty()) return text
        val extraUrls = imetas.map { it.url }.filter { it.isNotBlank() && !text.contains(it) }
        return (listOf(text) + extraUrls).filter { it.isNotBlank() }.joinToString("\n")
    }

    suspend fun follow(community: AddressableNote) = sendMyPublicAndPrivateOutbox(communityList.follow(community))

    suspend fun unfollow(community: AddressableNote) = sendMyPublicAndPrivateOutbox(communityList.unfollow(community))

    suspend fun followHashtag(tag: String) = sendMyPublicAndPrivateOutbox(interestList.follow(tag))

    suspend fun unfollowHashtag(tag: String) = sendMyPublicAndPrivateOutbox(interestList.unfollow(tag))

    suspend fun followFavoriteAlgoFeed(dvm: AddressBookmark) = sendMyPublicAndPrivateOutbox(favoriteAlgoFeedsList.follow(dvm))

    suspend fun unfollowFavoriteAlgoFeed(dvm: Address) = sendMyPublicAndPrivateOutbox(favoriteAlgoFeedsList.unfollow(dvm))

    fun isFavoriteAlgoFeed(dvm: Address): Boolean = favoriteAlgoFeedsList.flow.value.contains(dvm)

    suspend fun followFavoriteFollowSet(followSet: AddressBookmark) = sendMyPublicAndPrivateOutbox(favoriteFollowSetsList.follow(followSet))

    suspend fun unfollowFavoriteFollowSet(followSet: Address) = sendMyPublicAndPrivateOutbox(favoriteFollowSetsList.unfollow(followSet))

    suspend fun followGeohash(geohash: String) = sendMyPublicAndPrivateOutbox(geohashList.follow(geohash))

    suspend fun unfollowGeohash(geohash: String) = sendMyPublicAndPrivateOutbox(geohashList.unfollow(geohash))

    suspend fun approveCommunityPost(
        post: Note,
        community: AddressableNote,
    ) {
        val commEvent = community.event as? CommunityDefinitionEvent ?: return
        val postHint = post.toEventHint<Event>() ?: return
        val communityHint = community.toEventHint<CommunityDefinitionEvent>() ?: return

        val template = CommunityPostApprovalEvent.build(postHint, communityHint)

        val signedEvent = signer.sign(template)

        val relays = outboxRelays.flow.value + commEvent.relayUrls() + community.relays + (post.author?.inboxRelays() ?: emptyList())

        cache.justConsumeMyOwnEvent(signedEvent)
        client.publish(signedEvent, relays)
    }

    suspend fun sendWebBookmark(
        url: String,
        title: String?,
        description: String,
        hashtags: List<String> = emptyList(),
        editing: WebBookmarkEvent? = null,
    ) {
        if (!isWriteable()) return

        val now = TimeUtils.now()
        val template =
            WebBookmarkEvent.build(
                url,
                title,
                description,
                tags = hashtags,
                createdAt = now,
                firstPublishedAt = editing?.publishedAt() ?: now,
            )
        val signedEvent = signer.sign(template)

        cache.justConsumeMyOwnEvent(signedEvent)
        client.publish(signedEvent, computeRelayListToBroadcast(signedEvent))

        // A different d tag is a different address, so the edit would otherwise leave the old
        // bookmark behind. That happens when the URL was changed, and when re-saving a bookmark
        // stored under the pre-2026 NIP-B0 rule, which also dropped `http://` (the d tag then
        // reads as https, and saving the real http URL now keeps the scheme).
        if (editing != null && editing.dTag() != signedEvent.dTag()) {
            deleteWebBookmark(editing)
        }
    }

    suspend fun deleteWebBookmark(event: WebBookmarkEvent) {
        if (!isWriteable()) return

        val template = DeletionRequestEvent.build(listOf(event))
        val signedEvent = signer.sign(template)

        cache.justConsumeMyOwnEvent(signedEvent)
        client.publish(signedEvent, computeRelayListToBroadcast(signedEvent))
    }

    suspend fun sendBadgeDefinition(
        badgeId: String,
        name: String?,
        imageUrl: String?,
        imageDim: DimensionTag?,
        description: String?,
        thumbs: List<ThumbTag> = emptyList(),
    ) {
        if (!isWriteable()) return

        val template =
            BadgeDefinitionEvent.build(
                badgeId = badgeId,
                name = name,
                imageUrl = imageUrl,
                imageDimensions = imageDim,
                description = description,
                thumbs = thumbs,
            )
        val signedEvent = signer.sign(template)

        cache.justConsumeMyOwnEvent(signedEvent)
        client.publish(signedEvent, outboxRelays.flow.value)
    }

    suspend fun deleteBadgeDefinition(event: BadgeDefinitionEvent) {
        if (!isWriteable()) return
        if (event.pubKey != signer.pubKey) return

        val template = DeletionRequestEvent.build(listOf(event))
        val signedEvent = signer.sign(template)

        cache.justConsumeMyOwnEvent(signedEvent)
        client.publish(signedEvent, computeRelayListToBroadcast(signedEvent))
    }

    suspend fun sendBadgeAward(
        definition: BadgeDefinitionEvent,
        awardees: List<PTag>,
    ) {
        if (!isWriteable()) return
        if (awardees.isEmpty()) return

        val aTag = ATag(definition.kind, definition.pubKey, definition.dTag(), null)
        val template = BadgeAwardEvent.build(aTag, awardees)
        val signedEvent = signer.sign(template)

        val relays =
            outboxRelays.flow.value +
                awardees
                    .flatMap { cache.getOrCreateUser(it.pubKey).inboxRelays() ?: emptyList() }
                    .toSet()

        cache.justConsumeMyOwnEvent(signedEvent)
        client.publish(signedEvent, relays)
    }

    suspend fun sendCommunityDefinition(
        name: String,
        description: String,
        moderators: List<ModeratorTag>,
        image: String? = null,
        rules: String? = null,
        relays: List<RelayTag>? = null,
        dTag: String,
    ): CommunityDefinitionEvent? {
        if (!isWriteable()) return null

        val template =
            CommunityDefinitionEvent.build(
                name = name,
                description = description,
                moderators = moderators,
                image = image,
                rules = rules,
                relays = relays,
                dTag = dTag,
            )
        val signedEvent = signer.sign(template)

        cache.justConsumeMyOwnEvent(signedEvent)
        client.publish(signedEvent, computeRelayListToBroadcast(signedEvent))
        return signedEvent
    }

    /**
     * Publishes a sibling NIP-9B `kind:34551` rules document for a community we just
     * (or previously) defined with [sendCommunityDefinition]. The event is signed by
     * the community owner and addresses the definition through its `a` tag, sharing
     * the same `dTag` so it replaces in place when re-edited.
     */
    suspend fun sendCommunityRules(
        communityDTag: String,
        kindRules: List<KindRuleTag>,
        pubkeyRules: List<PubkeyRuleTag> = emptyList(),
        wotGates: List<WotTag> = emptyList(),
        maxEventSize: Int? = null,
        minRulesCreatedAt: Long? = null,
    ): CommunityRulesEvent? {
        if (!isWriteable()) return null

        val communityAddress = ATag(CommunityDefinitionEvent.KIND, signer.pubKey, communityDTag, null)

        val template =
            CommunityRulesEvent.build(
                dTag = communityDTag,
                communityAddress = communityAddress,
                kindRules = kindRules,
                pubkeyRules = pubkeyRules,
                wotGates = wotGates,
                maxEventSize = maxEventSize,
                minRulesCreatedAt = minRulesCreatedAt,
            )
        val signedEvent = signer.sign(template)

        cache.justConsumeMyOwnEvent(signedEvent)
        client.publish(signedEvent, computeRelayListToBroadcast(signedEvent))
        return signedEvent
    }

    private fun loadCurrentAcceptedBadges(): List<AcceptedBadge> {
        val newNote = cache.getAddressableNoteIfExists(ProfileBadgesEvent.createAddress(signer.pubKey))
        val newEvent = newNote?.event as? ProfileBadgesEvent
        if (newEvent != null) return newEvent.acceptedBadges()

        val oldNote = cache.getAddressableNoteIfExists(AcceptedBadgeSetEvent.createAddress(signer.pubKey))
        val oldEvent = oldNote?.event as? AcceptedBadgeSetEvent
        return oldEvent?.acceptedBadges() ?: emptyList()
    }

    /**
     * Serializes read-modify-write of the accepted-badges replaceable event so two
     * rapid toggles can't race each other into losing updates.
     */
    private val profileBadgesMutex = Mutex()

    /**
     * Returns a createdAt strictly greater than whatever ProfileBadgesEvent (or
     * the legacy AcceptedBadgeSetEvent) currently sits in cache. Needed because
     * LocalCache.consumeBaseReplaceable drops updates whose createdAt isn't
     * strictly greater, and TimeUtils.now() has only second resolution.
     */
    private fun nextProfileBadgesCreatedAt(): Long {
        val latest =
            maxOf(
                (cache.getAddressableNoteIfExists(ProfileBadgesEvent.createAddress(signer.pubKey))?.event?.createdAt) ?: 0L,
                (cache.getAddressableNoteIfExists(AcceptedBadgeSetEvent.createAddress(signer.pubKey))?.event?.createdAt) ?: 0L,
            )
        return maxOf(TimeUtils.now(), latest + 1)
    }

    suspend fun addAcceptedBadge(
        award: BadgeAwardEvent,
        definition: BadgeDefinitionEvent,
    ) {
        if (!isWriteable()) return

        val aTag = ATag(definition.kind, definition.pubKey, definition.dTag(), null)
        val eTag = ETag(award.id)

        val signedEvent =
            profileBadgesMutex.withLock {
                val current = loadCurrentAcceptedBadges()
                if (current.any { it.badgeAward.eventId == award.id }) return
                val updated = current + AcceptedBadge(aTag, eTag)

                val template = ProfileBadgesEvent.build(updated, createdAt = nextProfileBadgesCreatedAt())
                val signed = signer.sign(template)
                cache.justConsumeMyOwnEvent(signed)
                signed
            }

        client.publish(signedEvent, outboxRelays.flow.value)
    }

    suspend fun removeAcceptedBadge(award: BadgeAwardEvent) {
        if (!isWriteable()) return

        val signedEvent =
            profileBadgesMutex.withLock {
                val current = loadCurrentAcceptedBadges()
                val updated = current.filterNot { it.badgeAward.eventId == award.id }
                if (updated.size == current.size) return

                val template = ProfileBadgesEvent.build(updated, createdAt = nextProfileBadgesCreatedAt())
                val signed = signer.sign(template)
                cache.justConsumeMyOwnEvent(signed)
                signed
            }

        client.publish(signedEvent, outboxRelays.flow.value)
    }

    suspend fun pollRespond(
        event: PollEvent,
        responses: Set<String>,
    ) {
        val pollNote = cache.getOrCreateNote(event.id)
        if (refusesRoomNote(pollNote, "vote on")) return
        val poll = pollNote.toEventHint<PollEvent>()

        if (poll != null) {
            // The poll decides how its answers are counted, so it decides which builder applies:
            // a single-choice answer is the first tag only, and sending several would record
            // whichever one the reader saw first.
            val template =
                when (event.pollType()) {
                    PollType.SINGLE_CHOICE ->
                        responses.firstOrNull()?.let { PollResponseEvent.buildSingleChoice(poll, it) } ?: return
                    PollType.MULTI_CHOICE -> PollResponseEvent.buildMultipleChoice(poll, responses)
                }

            val signedEvent = signer.sign(template)

            cache.justConsumeMyOwnEvent(signedEvent)

            client.publish(signedEvent, computeRelayListToBroadcast(signedEvent))
        }
    }

    fun sendHeader(
        signedEvent: Event,
        relayList: Set<NormalizedRelayUrl>,
        onReady: (Note) -> Unit,
    ) {
        client.publish(signedEvent, relayList = relayList)
        cache.justConsumeMyOwnEvent(signedEvent)

        cache.getNoteIfExists(signedEvent.id)?.let { onReady(it) }
    }

    suspend fun sendVoiceMessage(
        url: String,
        mimeType: String?,
        hash: String,
        duration: Int,
        waveform: List<Float>,
    ) {
        signAndComputeBroadcast(VoiceEvent.build(url, mimeType, hash, duration, waveform))
    }

    suspend fun sendVoiceReplyMessage(
        url: String,
        mimeType: String?,
        hash: String,
        duration: Int,
        waveform: List<Float>,
        replyTo: EventHintBundle<BaseVoiceEvent>,
    ) {
        signAndComputeBroadcast(VoiceReplyEvent.build(url, mimeType, hash, duration, waveform, replyTo))
    }

    suspend fun sendAllAsOnePictureEvent(
        urlHeaderInfo: Map<String, FileHeader>,
        caption: String?,
        contentWarningReason: String?,
    ) {
        val iMetas =
            urlHeaderInfo.map {
                PictureMeta(
                    url = it.key,
                    mimeType = it.value.mimeType,
                    blurhash = it.value.blurHash?.blurhash,
                    dimension = it.value.dim,
                    alt = caption,
                    hash = it.value.hash,
                    size = it.value.size,
                    service = null,
                    fallback = emptyList(),
                    annotations = emptyList(),
                    thumbhash = it.value.thumbHash?.thumbhash,
                )
            }

        val template =
            PictureEvent.build(iMetas, caption ?: "") {
                caption?.let {
                    hashtags(findHashtags(it))
                    references(findURLs(it))
                    quotes(findNostrUris(it))
                }
                // add zap splits
                // add zap raiser
                // add geohashes
                // add title
                contentWarningReason?.let { contentWarning(contentWarningReason) }
            }

        signAndComputeBroadcast(template)
    }

    suspend fun sendHeader(
        url: String,
        magnetUri: String?,
        headerInfo: FileHeader,
        alt: String?,
        contentWarningReason: String?,
        originalHash: String? = null,
        videoKind: VideoPostKind = VideoPostKind.AUTO,
    ) {
        if (!isWriteable()) return

        val isImage = headerInfo.mimeType?.startsWith("image/") == true || RichTextParser.isImageUrl(url)
        val isVideo = headerInfo.mimeType?.startsWith("video/") == true || RichTextParser.isVideoUrl(url)
        // A local, so the null check below smart-casts: FileHeader lives in another module.
        val dim = headerInfo.dim

        val template =
            if (isImage) {
                PictureEvent.build(alt ?: "") {
                    alt?.let {
                        hashtags(findHashtags(it))
                        references(findURLs(it))
                        quotes(findNostrUris(it))
                    }
                    pictureIMeta(
                        url = url,
                        mimeType = headerInfo.mimeType,
                        blurhash = headerInfo.blurHash?.blurhash,
                        dimension = headerInfo.dim,
                        hash = headerInfo.hash,
                        size = headerInfo.size,
                        alt = alt,
                        thumbhash = headerInfo.thumbHash?.thumbhash,
                    )
                    // add zap splits
                    // add zap raiser
                    // add geohashes
                    // add title
                    contentWarningReason?.let { contentWarning(contentWarningReason) }
                }
            } else if (isVideo && dim != null) {
                val videoMeta =
                    VideoMeta(
                        url = url,
                        hash = headerInfo.hash,
                        size = headerInfo.size,
                        mimeType = headerInfo.mimeType,
                        dimension = headerInfo.dim,
                        blurhash = headerInfo.blurHash?.blurhash,
                        alt = alt,
                        thumbhash = headerInfo.thumbHash?.thumbhash,
                    )

                // The composer forces the kind when it was opened from a feed that only reads one of
                // them (Shorts, Longs) or from that feed's share target, so the post lands where the
                // user asked for it. Everywhere else the orientation decides.
                if (videoKind.isShort(dim)) {
                    VideoShortEvent.build(videoMeta, alt ?: "") {
                        contentWarningReason?.let { contentWarning(contentWarningReason) }
                    }
                } else {
                    VideoNormalEvent.build(videoMeta, alt ?: "") {
                        contentWarningReason?.let { contentWarning(contentWarningReason) }
                    }
                }
            } else {
                FileMetadataEvent.build(url, alt) {
                    hash(headerInfo.hash)
                    fileSize(headerInfo.size)

                    headerInfo.mimeType?.let { mimeType(it) }
                    headerInfo.dim?.let { dimension(it) }
                    headerInfo.blurHash?.let { blurhash(it.blurhash) }
                    headerInfo.thumbHash?.let { thumbhash(it.thumbhash) }

                    originalHash?.let { originalHash(it) }
                    magnetUri?.let { magnet(it) }

                    contentWarningReason?.let { contentWarning(contentWarningReason) }
                }
            }

        signAndComputeBroadcast(template)
    }

    /**
     * Creates a post event without sending it.
     * Returns the event, target relays, and extra events to broadcast.
     * For use with tracked broadcasting.
     */
    suspend fun <T : Event> createPostEvent(
        template: EventTemplate<T>,
        extraNotesToBroadcast: List<Event> = emptyList(),
    ): Triple<T, Set<NormalizedRelayUrl>, List<Event>> {
        val event = signer.sign(template)

        // Use event-based relay computation (not note-based, since note is empty)
        val relayList = computeRelayListToBroadcast(event)

        return Triple(event, relayList, extraNotesToBroadcast)
    }

    /**
     * Consumes a post event into local cache and sends extra events.
     * Called when tracked broadcasting succeeds.
     */
    fun consumePostEvent(
        event: Event,
        relays: Set<NormalizedRelayUrl>,
        extraNotesToBroadcast: List<Event>,
    ) {
        cache.justConsumeMyOwnEvent(event)
        extraNotesToBroadcast.forEach { client.publish(it, relays) }
    }

    /**
     * The live [AddressableNote] backing a draft tag for this account. It is the same cached
     * note that draft events are consumed into, so its `event` tracks the draft over time. The
     * composer holds onto it (via DraftTagState) so [LocalCache]'s weak reference can't collect
     * it before a deletion needs it, which would otherwise orphan the draft on the relays.
     */
    fun getOrCreateDraftNote(draftTag: String): AddressableNote = cache.getOrCreateAddressableNote(DraftWrapEvent.createAddress(signer.pubKey, draftTag))

    suspend fun createAndSendDraftIgnoreErrors(
        draftTag: String,
        template: EventTemplate<out Event>,
    ) {
        try {
            createAndSendDraftInner(draftTag, template)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }

    suspend fun createAndSendDraftInner(
        draftTag: String,
        template: EventTemplate<out Event>,
    ) {
        if (!isWriteable()) return

        val extraRelays = cache.getAddressableNoteIfExists(DraftWrapEvent.createAddressTag(signer.pubKey, draftTag))?.relays ?: emptyList()

        val rumor = RumorAssembler.assembleRumor(signer.pubKey, template)
        val draftEvent = DraftWrapEvent.create(draftTag, rumor, signer)
        draftsDecryptionCache.preload(draftEvent, rumor)

        cache.justConsumeMyOwnEvent(draftEvent)

        val relayList = (privateStorageRelayList.flow.value + localRelayList.flow.value + extraRelays).toSet()
        if (relayList.isNotEmpty()) {
            client.publish(draftEvent, relayList)
        }
    }

    suspend fun deleteDraftIgnoreErrors(draftNote: AddressableNote?) {
        try {
            deleteDraftInner(draftNote)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }

    suspend fun deleteDraftInner(draftNote: AddressableNote?) {
        if (!isWriteable()) return

        // Only a real, still-present draft needs a deletion signed. The note's event is null when
        // no draft was ever saved (e.g. auto-drafts disabled) and already empty once it has been
        // deleted — in both cases there is nothing to delete, so we avoid prompting the signer.
        val draftEvent = draftNote?.event as? DraftWrapEvent
        if (draftEvent == null || draftEvent.isDeleted()) return

        val draftTag = draftNote.dTag()
        val extraRelays = draftNote.relays

        val deletedDraft = DraftWrapEvent.createDeletedEvent(draftTag, signer)
        val deletionEvent = signer.sign(DeletionRequestEvent.build(listOf(deletedDraft)))

        val relayList = (privateStorageRelayList.flow.value + localRelayList.flow.value + extraRelays).toSet()

        cache.justConsumeMyOwnEvent(deletedDraft)
        cache.justConsumeMyOwnEvent(deletionEvent)

        if (relayList.isNotEmpty()) {
            client.publish(deletedDraft, relayList)
            client.publish(deletionEvent, relayList)
        }
    }

    suspend fun createInteractiveStoryReadingState(
        root: EventHintBundle<InteractiveStoryBaseEvent>,
        readingScene: EventHintBundle<InteractiveStoryBaseEvent>,
    ) {
        if (!isWriteable()) return

        val template =
            InteractiveStoryReadingStateEvent.build(
                root = root,
                currentScene = readingScene,
            )

        val event = signer.sign(template)

        // updates relays that already have this replaceable.
        val noteRelays = cache.getAddressableNoteIfExists(event.address())?.relays ?: emptyList()

        val relayList = privateStorageRelayList.flow.value + localRelayList.flow.value
        if (relayList.isNotEmpty()) {
            client.publish(event, relayList + noteRelays)
        } else {
            client.publish(event, outboxRelays.flow.value + noteRelays)
        }
        cache.justConsumeMyOwnEvent(event)
    }

    suspend fun updateInteractiveStoryReadingState(
        readingState: InteractiveStoryReadingStateEvent,
        readingScene: EventHintBundle<InteractiveStoryBaseEvent>,
    ) {
        if (!isWriteable()) return

        val template =
            InteractiveStoryReadingStateEvent.update(
                base = readingState,
                currentScene = readingScene,
            )

        val event = signer.sign(template)

        // updates relays that already have this replaceable.
        val noteRelays = cache.getAddressableNoteIfExists(event.address())?.relays ?: emptyList()

        val relayList = privateStorageRelayList.flow.value + localRelayList.flow.value
        if (relayList.isNotEmpty()) {
            client.publish(event, relayList + noteRelays)
        } else {
            client.publish(event, outboxRelays.flow.value + noteRelays)
        }
        cache.justConsumeMyOwnEvent(event)
    }

    fun mapEntitiesToNotes(entities: List<Entity>): List<Note> =
        entities.mapNotNull {
            when (it) {
                is NPub -> null
                is NProfile -> null
                is NNote -> cache.getOrCreateNote(it.hex)
                is NEvent -> cache.getOrCreateNote(it.hex)
                is NEmbed -> cache.getOrCreateNote(it.event.id)
                is NAddress -> cache.checkGetOrCreateAddressableNote(it.aTag())
                is NSec -> null
                is NRelay -> null
                else -> null
            }
        }

    suspend fun sendInteractiveStoryPrologue(
        baseId: String,
        title: String,
        content: String,
        options: List<StoryOptionTag>,
        summary: String? = null,
        image: String? = null,
        zapReceiver: List<ZapSplitSetup>? = null,
        contentWarningReason: String? = null,
        zapRaiserAmount: Long? = null,
        imetas: List<IMetaTag>? = null,
        draftTag: String? = null,
        relayList: Set<NormalizedRelayUrl>,
    ) {
        if (!isWriteable()) return

        val quotes = findNostrUris(content)

        val template =
            InteractiveStoryPrologueEvent.build(
                baseId = baseId,
                title = title,
                content = content,
                options = options,
            ) {
                summary?.let { summary(it) }
                image?.let { image(it) }
                hashtags(findHashtags(content))
                references(findURLs(content))
                quotes(quotes)
                zapRaiserAmount?.let { zapraiser(it) }
                zapReceiver?.let { zapSplits(it) }
                imetas?.let { imetas(it) }
                contentWarningReason?.let { contentWarning(contentWarningReason) }
            }

        if (draftTag != null) {
            createAndSendDraftIgnoreErrors(draftTag, template)
        } else {
            val it = signer.sign(template)
            cache.justConsumeMyOwnEvent(it)
            client.publish(it, relayList = relayList)

            mapEntitiesToNotes(quotes).forEach { it.event?.let { client.publish(it, relayList = relayList) } }
        }
    }

    suspend fun sendInteractiveStoryScene(
        baseId: String,
        title: String,
        content: String,
        options: List<StoryOptionTag>,
        zapReceiver: List<ZapSplitSetup>? = null,
        contentWarningReason: String? = null,
        zapRaiserAmount: Long? = null,
        imetas: List<IMetaTag>? = null,
        draftTag: String? = null,
        relayList: Set<NormalizedRelayUrl>,
    ) {
        if (!isWriteable()) return

        val quotes = findNostrUris(content)

        val template =
            InteractiveStorySceneEvent.build(
                baseId = baseId,
                title = title,
                content = content,
                options = options,
            ) {
                hashtags(findHashtags(content))
                references(findURLs(content))
                quotes(quotes)
                zapRaiserAmount?.let { zapraiser(it) }
                zapReceiver?.let { zapSplits(it) }
                imetas?.let { imetas(it) }
                contentWarningReason?.let { contentWarning(contentWarningReason) }
            }

        val broadcastNotes = mapEntitiesToNotes(quotes).toSet()

        if (draftTag != null) {
            createAndSendDraftIgnoreErrors(draftTag, template)
        } else {
            val it = signer.sign(template)
            cache.justConsumeMyOwnEvent(it)
            client.publish(it, relayList = relayList)

            broadcastNotes.forEach { it.event?.let { client.publish(it, relayList = relayList) } }
        }
    }

    suspend fun sendAddBounty(
        value: BigDecimal,
        bounty: Note,
    ) {
        if (!isWriteable()) return

        val bountyEvent = bounty.event as? TextNoteEvent ?: return
        val bountyAuthor = bounty.author ?: return

        val template =
            BountyAddValueEvent.build(
                amount = value,
                bountyRoot = EventHintBundle(bountyEvent, bounty.relayHintUrl()),
                bountyRootAuthor = bountyAuthor.toPTag(),
            )

        val relays = (bounty.relays + outboxRelays.flow.value).toSet()

        val newEvent = signer.sign(template)
        cache.justConsumeMyOwnEvent(newEvent)

        client.publish(newEvent, relayList = relays)
        client.publish(bountyEvent, relayList = relays)
    }

    suspend fun sendEdit(
        message: String,
        originalNote: Note,
        notify: HexKey?,
        summary: String? = null,
    ) {
        if (!isWriteable()) return

        val idHex = originalNote.event?.id ?: return

        val event =
            TextNoteModificationEvent.create(
                content = message,
                eventId = idHex,
                notify = notify,
                summary = summary,
                signer = signer,
            )

        cache.justConsumeMyOwnEvent(event)
        val note = cache.getOrCreateNote(event.id)
        val relayList = computeRelayListToBroadcast(note)

        client.publish(event, relayList = relayList)
    }

    override suspend fun sendNip04PrivateMessage(eventTemplate: EventTemplate<EncryptedDmEvent>) {
        if (!isWriteable()) return

        val newEvent = signer.sign(eventTemplate)
        val recipient = newEvent.verifiedRecipientPubKey()
        val destinationRelays = recipient?.let { cache.getOrCreateUser(it).dmInboxRelays() } ?: emptyList()

        cache.justConsumeMyOwnEvent(newEvent)
        client.publish(newEvent, outboxRelays.flow.value + destinationRelays)

        // Index into the chatroom immediately (same rationale as
        // broadcastPrivately) instead of waiting for the newEventBundles
        // batcher; the later batched re-delivery is deduped by the chatroom.
        cache.getNoteIfExists(newEvent.id)?.let { newNotesPreProcessor.consume(it) }

        markDmRoomAsRead(newEvent)
    }

    override suspend fun sendNip17EncryptedFile(template: EventTemplate<ChatMessageEncryptedFileHeaderEvent>) {
        if (!isWriteable()) return
        // See sendNip17PrivateMessage: a NIP-17 file sent with NIP-17 off would be dropped on arrival.
        if (!chatFeedToggles.isEnabled(ChatFeedType.NIP17)) {
            Log.w("Account") { "Not sending a NIP-17 file: NIP-17 is turned off in Messages settings" }
            return
        }

        val powDifficulty = powDifficultyFor(GiftWrapEvent.KIND)
        if (powDifficulty != null) {
            // Sign the inner event and every seal NOW, in the caller's
            // interaction context — an external signer (Amber/bunker) cannot
            // prompt from a background mining worker. Only the local-CPU
            // ephemeral-key wrap mining goes to the queue, checkpointed so a
            // process death mid-mine cannot lose the file announcement.
            val senderMessage = signer.sign(template)
            val seals = NIP17Factory().createSeals(senderMessage, senderMessage.groupMembers(), signer)
            if (mineWrapsInBackground(seals.seals, seals.expirationDelta, powDifficulty, displayedNoteId = senderMessage.id)) {
                // The wraps publish only after mining, but the user has already
                // replied — advance the read marker now.
                markDmRoomAsRead(senderMessage)
                return
            }
        }

        broadcastPrivately(NIP17Factory().createEncryptedFileNIP17(template, signer))
    }

    override suspend fun sendNip17PrivateMessage(template: EventTemplate<ChatMessageEvent>) = sendNip17PrivateMessage(template) {}

    /**
     * [onSent] runs once the wraps are handed to the relays — right away when
     * the message is not mined, after the nonce search when it is — so the
     * composer can keep its draft for as long as the message only exists in
     * the mining queue.
     */
    suspend fun sendNip17PrivateMessage(
        template: EventTemplate<ChatMessageEvent>,
        onSent: suspend () -> Unit,
    ) {
        // With NIP-17 off in Messages settings the message would be dropped on arrival (our own copy
        // included), so it never goes out. The composer already hides itself; this covers every other
        // way in: a room's subject dialog, an inline reply from a notification posted before the
        // switch, the desktop chat.
        if (!chatFeedToggles.isEnabled(ChatFeedType.NIP17)) {
            Log.w("Account") { "Not sending a NIP-17 message: NIP-17 is turned off in Messages settings" }
            return
        }
        val powDifficulty = powDifficultyFor(GiftWrapEvent.KIND)
        if (powDifficulty != null) {
            // See sendNip17EncryptedFile: sign inline, queue only wrap mining.
            val senderMessage = signer.sign(template)
            val seals = NIP17Factory().createSeals(senderMessage, senderMessage.groupMembers(), signer)
            if (mineWrapsInBackground(seals.seals, seals.expirationDelta, powDifficulty, displayedNoteId = senderMessage.id, onPublished = onSent)) {
                // The wraps publish only after mining, but the user has already
                // replied — advance the read marker now.
                markDmRoomAsRead(senderMessage)
                return
            }
        }

        broadcastPrivately(NIP17Factory().createMessageNIP17(template, signer))
        onSent()
    }

    /**
     * Publishes a kind-1 note privately: signs the template, then gift-wraps
     * the rumor to every p-tagged user plus a self-copy and sends each wrap
     * to the recipient's DM relays. Used for private replies (the parent's
     * author and participants are already p-tagged) and for private posts
     * (the Notify list is the audience). Nothing reaches public relays.
     *
     * [powOverrideDifficulty] is the composer chip's per-post override:
     * null follows the account's gift-wrap setting, 0 disables mining.
     * [onSent] runs once the wraps are handed to the relays (after mining,
     * when mined).
     */
    suspend fun sendPrivateNote(
        template: EventTemplate<TextNoteEvent>,
        powOverrideDifficulty: Int? = null,
        onSent: suspend () -> Unit = {},
    ) {
        if (!isWriteable()) return

        val powDifficulty = powDifficultyFor(GiftWrapEvent.KIND, powOverrideDifficulty)
        if (powDifficulty != null) {
            // See sendNip17EncryptedFile: sign inline, queue only wrap mining.
            val senderNote = signer.sign(template)
            val recipients =
                senderNote
                    .mentions()
                    .map { it.pubKey }
                    .plus(signer.pubKey)
                    .toSet()
            val seals = NIP17Factory().createSeals(senderNote, recipients, signer)
            if (mineWrapsInBackground(seals.seals, seals.expirationDelta, powDifficulty, displayedNoteId = senderNote.id, onPublished = onSent)) return
        }

        broadcastPrivately(NIP17Factory().createNoteNIP17(template, signer))
        onSent()
    }

    override suspend fun sendGiftWraps(wraps: List<GiftWrapEvent>) {
        wraps.forEach { wrap ->
            val relayList = computeRelayListToBroadcast(wrap)
            client.publish(wrap, relayList)
        }
    }

    suspend fun broadcastPrivately(signedEvents: NIP17Factory.Result) {
        broadcastPrivately(signedEvents.wraps, signedEvents.msg.id)
        markDmRoomAsRead(signedEvents.msg)
    }

    /**
     * [displayedNoteId] is the inner rumor's id (the note the chat feed shows).
     * When present, each wrap registers with the delivery-ticks tracker — this is
     * the only place the recipient -> wrap -> target-relays mapping exists, before
     * the wraps are aliased onto a single note.
     */
    suspend fun broadcastPrivately(
        wraps: List<GiftWrapEvent>,
        displayedNoteId: HexKey? = null,
    ) {
        if (displayedNoteId != null) {
            // Before the note is indexed below, so the bubble never renders a
            // frame without its state. Republishing the wraps we already hold
            // is the whole retry: they are signed and sealed, so nothing has
            // to be rebuilt and the recipients' copies keep the ids they were
            // given.
            chatDeliveryTracker.markSending(displayedNoteId) { broadcastPrivately(wraps, displayedNoteId) }
        }

        val mine = wraps.filter { (it.recipientPubKey() == signer.pubKey) }

        mine.forEach { giftWrap ->
            cache.justConsumeMyOwnEvent(giftWrap)
        }

        val id = mine.firstOrNull()?.id
        val mineNote = if (id == null) null else cache.getNoteIfExists(id)

        // Unwrap and index the self-copy BEFORE the publish loop, not after it
        // and not via the newEventBundles batcher (up to ~1s): the bubble is
        // then on screen while the wraps are still going out, and acceptances
        // land directly on the rumor note the chat renders instead of parking
        // on the wrap. The batcher re-delivers this note later; the
        // processor's replay path and the chatroom add are both idempotent.
        mineNote?.let { newNotesPreProcessor.consume(it) }

        var published = false

        wraps.forEach { wrap ->
            // Creates an alias
            if (mineNote != null && wrap.recipientPubKey() != signer.pubKey) {
                cache.getOrAddAliasNote(wrap.id, mineNote)
            }

            val relayList = computeRelayListToBroadcast(wrap)

            if (displayedNoteId != null) {
                wrap.recipientPubKey()?.let { recipient ->
                    chatDeliveryTracker.trackWrap(
                        displayedNoteId = displayedNoteId,
                        recipient = recipient,
                        wrapId = wrap.id,
                        targetRelays = relayList,
                        isSelf = recipient == signer.pubKey,
                    )
                }
            }

            if (relayList.isNotEmpty()) published = true
            client.publish(wrap, relayList)
        }

        if (displayedNoteId != null) {
            // Every wrap resolved to an empty relay set, so nothing was sent
            // anywhere and no OK will ever arrive. That used to be invisible —
            // the bubble simply sat on a clock forever. Say so, and let the
            // retry re-publish the same wraps once the user has DM relays.
            if (published) {
                chatDeliveryTracker.markSent(displayedNoteId)
            } else {
                chatDeliveryTracker.markFailed(displayedNoteId)
            }
        }
    }

    /**
     * Sending a message into a DM room means the user has caught up with what the room
     * showed when they replied: advance the local read marker to the newest known message —
     * not just the sent one, whose local clock may lag behind a skew-ahead peer's — so the
     * unread indicators clear without requiring the conversation to be reopened
     * (#1286, #1287). No-op for private events that don't belong to a room (private notes,
     * reactions, deletions).
     */
    private fun markDmRoomAsRead(event: Event) {
        if (event is ChatroomKeyable) {
            val room = event.chatroomKey(signer.pubKey)
            val newestInRoom =
                chatroomList.rooms
                    .get(room)
                    ?.newestMessage
                    ?.createdAt() ?: 0L
            markAsRead(privateChatLastReadRoute(room), maxOf(event.createdAt, newestInRoom))
        }
    }

    // --- Marmot Group Messaging ---

    suspend fun createStatus(newStatus: String) = sendMyPublicAndPrivateOutbox(UserStatusAction.create(newStatus, signer))

    suspend fun publishCallSignaling(wrap: EphemeralGiftWrapEvent) {
        val relayList = computeRelayListToBroadcast(wrap)
        client.publish(wrap, relayList)
    }

    suspend fun updateStatus(
        oldStatus: AddressableNote,
        newStatus: String,
    ) = sendMyPublicAndPrivateOutbox(UserStatusAction.update(oldStatus, newStatus, signer))

    suspend fun deleteStatus(oldStatus: AddressableNote) = sendMyPublicAndPrivateOutbox(UserStatusAction.delete(oldStatus, signer))

    suspend fun publishNowPlaying(
        track: NowPlaying,
        expiration: Long,
    ) = sendMyPublicAndPrivateOutbox(UserStatusAction.createMusic(track, expiration, signer))

    suspend fun clearNowPlaying() = sendMyPublicAndPrivateOutbox(UserStatusAction.clearMusic(signer))

    suspend fun removeEmojiPack(emojiPack: Note) = sendMyPublicAndPrivateOutbox(emoji.removeEmojiPack(emojiPack))

    /**
     * Adds [emojiPack] to the kind 10030 emoji list. Returns false, publishing nothing, when the
     * list is not on this device yet and no relay could say whether one exists: building on
     * nothing would publish a list holding only this pack, replacing the user's real one.
     */
    suspend fun addEmojiPack(emojiPack: Note): Boolean {
        if (!loadOwnEmojiListIfMissing()) return false
        sendMyPublicAndPrivateOutbox(emoji.addEmojiPack(emojiPack))
        return true
    }

    /**
     * Before the first write of the kind 10030 on a device that has not received it (a fresh
     * login, or relays still connecting), fetches the newest version from the outbox. True when
     * the list is now in the cache, or when a relay answered and has none; false when nobody
     * answered, so a missing list cannot be told from a list that has not arrived.
     */
    private suspend fun loadOwnEmojiListIfMissing(): Boolean {
        if (emoji.getEmojiList() != null) return true
        val relays = outboxRelays.flow.value
        if (relays.isEmpty()) return false
        val filter = Filter(kinds = listOf(EmojiListEvent.KIND), authors = listOf(signer.pubKey), limit = 1)
        val result = client.fetchAllWithHooks(relays.associateWith { listOf(filter) }, idleTimeoutMs = 8_000) { _, _ -> true }
        // The fetch neither verifies nor stores: newest first, the first that verifies.
        val newest =
            result.events
                .map { it.second }
                .filter { it.kind == EmojiListEvent.KIND && it.pubKey == signer.pubKey }
                .sortedByDescending { it.createdAt }
                .firstOrNull { it.verify() }
        if (newest != null) {
            cache.justConsumeMyOwnEvent(newest)
            return emoji.getEmojiList() != null
        }
        return result.anyRelayServed
    }

    suspend fun createOwnedEmojiPack(
        title: String,
        description: String? = null,
        image: String? = null,
    ) = ownedEmojiPacks.createPack(title, description, image, this)

    suspend fun updateOwnedEmojiPackMetadata(
        dTag: String,
        newTitle: String,
        newDescription: String?,
        newImage: String?,
    ) = ownedEmojiPacks.updateMetadata(dTag, newTitle, newDescription, newImage, this)

    suspend fun addEmojiToOwnedPack(
        dTag: String,
        emoji: EmojiUrlTag,
        isPrivate: Boolean,
    ) = ownedEmojiPacks.addEmoji(dTag, emoji, isPrivate, this)

    suspend fun addEmojisToOwnedPack(
        dTag: String,
        emojis: List<EmojiUrlTag>,
        isPrivate: Boolean,
        onConflict: EmojiPackActions.OnConflict,
    ) = ownedEmojiPacks.addEmojis(dTag, emojis, isPrivate, onConflict, this)

    suspend fun removeEmojiFromOwnedPack(
        dTag: String,
        shortcode: String,
        isPrivate: Boolean,
    ) = ownedEmojiPacks.removeEmoji(dTag, shortcode, isPrivate, this)

    suspend fun deleteOwnedEmojiPack(dTag: String) = ownedEmojiPacks.deletePack(dTag, this)

    suspend fun addToGallery(
        idHex: HexKey,
        url: String,
        relay: NormalizedRelayUrl?,
        blurhash: String?,
        dim: DimensionTag?,
        hash: String?,
        mimeType: String?,
        thumbhash: String? = null,
        image: String? = null,
    ) {
        val template =
            ProfileGalleryEntryEvent.build(url) {
                fromEvent(idHex, relay)
                hash?.let { hash(hash) }
                mimeType?.let { mimeType(it) }
                dim?.let { dimension(it) }
                blurhash?.let { blurhash(it) }
                thumbhash?.let { galleryThumbhash(it) }
                image?.let { image(it) }
            }

        val event = signer.sign(template)
        sendMyPublicAndPrivateOutbox(event)
    }

    suspend fun removeFromGallery(note: Note) {
        delete(note)
    }

    suspend fun addGitRepositoryBookmark(note: AddressableNote) {
        if (!isWriteable()) return
        sendMyPublicAndPrivateOutbox(gitRepositoryListState.addRepository(note))
    }

    suspend fun removeGitRepositoryBookmark(note: AddressableNote) {
        if (!isWriteable()) return
        gitRepositoryListState.removeRepository(note)?.let { sendMyPublicAndPrivateOutbox(it) }
    }

    suspend fun addBookmark(
        note: Note,
        isPrivate: Boolean,
    ) {
        if (!isWriteable() || note.isDraft()) return

        sendMyPublicAndPrivateOutbox(bookmarkState.addBookmark(note, isPrivate))
    }

    suspend fun removeBookmark(
        note: Note,
        isPrivate: Boolean,
    ) {
        if (!isWriteable() || note.isDraft()) return

        val event = bookmarkState.removeBookmark(note, isPrivate)
        if (event != null) {
            sendMyPublicAndPrivateOutbox(event)
        }
    }

    suspend fun removeBookmark(note: Note) {
        if (!isWriteable() || note.isDraft()) return

        val event = bookmarkState.removeBookmark(note)
        if (event != null) {
            sendMyPublicAndPrivateOutbox(event)
        }
    }

    suspend fun removeDeletedBookmarks(
        deletedEventIds: Set<String>,
        deletedAddresses: Set<Address>,
    ) {
        if (!isWriteable()) return
        val event = bookmarkState.removeDeletedBookmarks(deletedEventIds, deletedAddresses) ?: return
        sendMyPublicAndPrivateOutbox(event)
    }

    suspend fun removeDeletedOldBookmarks(
        deletedEventIds: Set<String>,
        deletedAddresses: Set<Address>,
    ) {
        if (!isWriteable()) return
        val event = oldBookmarkState.removeDeletedBookmarks(deletedEventIds, deletedAddresses) ?: return
        sendMyPublicAndPrivateOutbox(event)
    }

    /**
     * Creates a bookmark event without sending it.
     * Returns the event and target relays for tracked broadcasting.
     */
    suspend fun createAddBookmarkEvent(
        note: Note,
        isPrivate: Boolean,
    ): Pair<Event, Set<NormalizedRelayUrl>>? {
        if (!isWriteable() || note.isDraft()) return null

        val event = bookmarkState.addBookmark(note, isPrivate)
        val relays = outboxRelays.flow.value

        return event to relays
    }

    /**
     * Creates a remove bookmark event without sending it.
     * Returns the event and target relays for tracked broadcasting.
     */
    suspend fun createRemoveBookmarkEvent(
        note: Note,
        isPrivate: Boolean,
    ): Pair<Event, Set<NormalizedRelayUrl>>? {
        if (!isWriteable() || note.isDraft()) return null

        val event = bookmarkState.removeBookmark(note, isPrivate) ?: return null
        val relays = outboxRelays.flow.value

        return event to relays
    }

    /**
     * Consumes a bookmark event into local cache.
     * Called when tracked broadcasting succeeds.
     */
    fun consumeBookmarkEvent(event: Event) {
        cache.justConsumeMyOwnEvent(event)
    }

    suspend fun migrateOldBookmarksToNew() {
        if (!isWriteable()) return

        val oldList = oldBookmarkState.getBookmarkList() ?: return
        val oldPublic = oldList.publicBookmarks()
        val oldPrivate = oldList.privateBookmarks(signer) ?: emptyList()

        if (oldPublic.isEmpty() && oldPrivate.isEmpty()) return

        val existingNewList = bookmarkState.getBookmarkList()

        val newEvent =
            if (existingNewList != null) {
                val existingPublic = existingNewList.publicBookmarks()
                val existingPrivate = existingNewList.privateBookmarks(signer) ?: emptyList()

                val existingPublicIds = existingPublic.map { it.toTagIdOnly().toList() }.toSet()
                val existingPrivateIds = existingPrivate.map { it.toTagIdOnly().toList() }.toSet()

                val newPublic = oldPublic.filter { it.toTagIdOnly().toList() !in existingPublicIds }
                val newPrivate = oldPrivate.filter { it.toTagIdOnly().toList() !in existingPrivateIds }

                if (newPublic.isEmpty() && newPrivate.isEmpty()) return

                val mergedPublic = existingPublic + newPublic
                val mergedPrivate = existingPrivate + newPrivate

                BookmarkListEvent.create(
                    publicBookmarks = mergedPublic,
                    privateBookmarks = mergedPrivate,
                    signer = signer,
                )
            } else {
                BookmarkListEvent.create(
                    publicBookmarks = oldPublic,
                    privateBookmarks = oldPrivate,
                    signer = signer,
                )
            }

        sendMyPublicAndPrivateOutbox(newEvent)
    }

    suspend fun addPin(note: Note) {
        if (!isWriteable() || note.isDraft()) return

        sendMyPublicAndPrivateOutbox(pinState.addPin(note))
    }

    suspend fun removePin(note: Note) {
        if (!isWriteable() || note.isDraft()) return

        val event = pinState.removePin(note)
        if (event != null) {
            sendMyPublicAndPrivateOutbox(event)
        }
    }

    suspend fun removeDeletedPins(deletedNotes: Set<Note>) {
        if (!isWriteable()) return

        val event = pinState.removeDeletedPins(deletedNotes) ?: return
        sendMyPublicAndPrivateOutbox(event)
    }

    suspend fun createAddPinEvent(note: Note): Pair<Event, Set<NormalizedRelayUrl>>? {
        if (!isWriteable() || note.isDraft()) return null

        val event = pinState.addPin(note)
        val relays = outboxRelays.flow.value

        return event to relays
    }

    suspend fun createRemovePinEvent(note: Note): Pair<Event, Set<NormalizedRelayUrl>>? {
        if (!isWriteable() || note.isDraft()) return null

        val event = pinState.removePin(note) ?: return null
        val relays = outboxRelays.flow.value

        return event to relays
    }

    fun consumePinEvent(event: Event) {
        cache.justConsumeMyOwnEvent(event)
    }

    suspend fun createAuthEvent(
        relay: NormalizedRelayUrl,
        challenge: String,
    ): RelayAuthEvent = RelayAuthEvent.create(relay, challenge, signer)

    /**
     * NIP-FA's Editing: read the newest kind 12000 from the user's write relays before rewriting
     * it, so a key added on another device survives. Starting from nothing because the relays
     * could not be read would delete the user's other keys, so then nothing is written.
     */
    private suspend fun readAgentKeysBeforeWriting() {
        client.readAgentKeysForRewrite(agentKeyWriteRelays(), signer.pubKey, agentKeys.getAgentKeyEvent()) { event, relay ->
            cache.justConsume(event, relay, wasVerified = true)
        }
    }

    /**
     * The user's NIP-65 write relays, where NIP-FA reads and writes the keys: never a default or
     * a bootstrap relay. A fresh login may not hold the list yet ("no list" and "not loaded yet"
     * look the same), so it is asked of the bootstrap relays first; still none, there are none.
     */
    private suspend fun agentKeyWriteRelays(): Set<NormalizedRelayUrl> {
        nip65RelayList.nip65Event(nip65RelayList.nip65ListNote)?.let { return it.allWriteRelaysNorm()?.toSet().orEmpty() }
        val found =
            client.fetchOwnRelayList(indexerRelayList.flow.value + Constants.eventFinderRelays, signer.pubKey) { event, relay ->
                cache.justConsume(event, relay, wasVerified = true)
            }
        return found?.allWriteRelaysNorm()?.toSet().orEmpty()
    }

    /**
     * Saves, re-orders and removes AI agent keys one edit at a time, with the edit's progress in
     * its state: the settings screen shows it, and leaving the screen never drops a save halfway.
     */
    val agentKeyEditor =
        AgentKeyEditor(
            scope = scope,
            readBeforeWriting = ::readAgentKeysBeforeWriting,
            rewrite = { kind, key ->
                when (kind) {
                    AgentKeyEditKind.SAVE -> agentKeys.add(key)
                    AgentKeyEditKind.MAKE_DEFAULT -> agentKeys.setPreferred(key)
                    AgentKeyEditKind.REMOVE -> agentKeys.remove(key)
                }
            },
            current = { agentKeys.getAgentKeyEvent() },
            keepHere = { cache.justConsumeMyOwnEvent(it) },
            // To the same relays the read came from (NIP-FA, Editing).
            publish = { client.publishAndConfirm(it, agentKeyWriteRelays(), untilFirstAccept = true) },
        )

    /** Saves [key] as the preferred AI agent key (adding it if new) and publishes the event. */
    suspend fun addAgentKey(key: AgentKeyTag) = agentKeyEditor.run(AgentKeyEditKind.SAVE, key)

    suspend fun setPreferredAgentKey(key: AgentKeyTag) = agentKeyEditor.run(AgentKeyEditKind.MAKE_DEFAULT, key)

    suspend fun removeAgentKey(key: AgentKeyTag) = agentKeyEditor.run(AgentKeyEditKind.REMOVE, key)

    suspend fun hideWord(word: String) {
        sendMyPublicAndPrivateOutbox(muteList.hideWord(word))
    }

    suspend fun showWord(word: String) {
        sendMyPublicAndPrivateOutbox(blockPeopleList.showWord(word))
        sendMyPublicAndPrivateOutbox(muteList.showWord(word))
    }

    suspend fun hideHashtag(hashtag: String) {
        sendMyPublicAndPrivateOutbox(muteList.hideHashtag(hashtag))
    }

    suspend fun showHashtag(hashtag: String) {
        muteList.showHashtag(hashtag)?.let { sendMyPublicAndPrivateOutbox(it) }
    }

    suspend fun hideUser(pubkeyHex: HexKey) {
        sendMyPublicAndPrivateOutbox(muteList.hideUser(pubkeyHex))
    }

    /**
     * Nicknames a user by publishing the account's kind:30382 contact card about
     * them, with the petname, summary and their custom emoji mappings NIP-44
     * encrypted in the content. `null` clears a field. Goes out through the
     * account's extended outbox relays.
     */
    suspend fun updateUserAssertionPetName(
        pubkeyHex: HexKey,
        petName: String?,
        summary: String?,
    ) = sendMyPublicAndPrivateOutbox(userAssertions.updatePetNameAndSummary(pubkeyHex, petName, summary))

    suspend fun showUser(pubkeyHex: HexKey) {
        sendMyPublicAndPrivateOutbox(blockPeopleList.showUser(pubkeyHex))
        sendMyPublicAndPrivateOutbox(muteList.showUser(pubkeyHex))
        hiddenUsers.showUser(pubkeyHex)
    }

    suspend fun showUsers(pubkeys: List<HexKey>) {
        if (pubkeys.isEmpty()) return
        sendMyPublicAndPrivateOutbox(blockPeopleList.showUsers(pubkeys))
        sendMyPublicAndPrivateOutbox(muteList.showUsers(pubkeys))
        pubkeys.forEach { hiddenUsers.showUser(it) }
    }

    suspend fun showWords(words: List<String>) {
        if (words.isEmpty()) return
        sendMyPublicAndPrivateOutbox(blockPeopleList.showWords(words))
        sendMyPublicAndPrivateOutbox(muteList.showWords(words))
    }

    suspend fun muteThread(rootHex: HexKey) {
        if (isThreadMuted(rootHex)) return
        sendMyPublicAndPrivateOutbox(muteList.hideThread(rootHex))
    }

    suspend fun unmuteThread(rootHex: HexKey) {
        if (!isThreadMuted(rootHex)) return
        muteList.showThread(rootHex)?.let { sendMyPublicAndPrivateOutbox(it) }
    }

    fun resolveThreadRoot(note: Note): HexKey = note.event?.threadRootIdOrSelf() ?: note.idHex

    fun isThreadMuted(rootHex: HexKey): Boolean = hiddenUsers.flow.value.isThreadMuted(rootHex)

    suspend fun requestDVMContentDiscovery(
        dvmPublicKey: User,
        onReady: (event: DvmContentDiscoveryRequestEvent, relays: Set<NormalizedRelayUrl>) -> Unit,
    ) {
        val relays = nip65RelayList.inboxFlow.value.toSet()
        val request = signer.sign<DvmContentDiscoveryRequestEvent>(DvmContentDiscoveryRequestEvent.build(dvmPublicKey.pubkeyHex, signer.pubKey, relays))

        val relayList =
            dvmPublicKey.inboxRelays()?.toSet()?.ifEmpty { null }
                ?: (dvmPublicKey.allUsedRelays() + cache.relayHints.hintsForKey(dvmPublicKey.pubkeyHex))

        cache.justConsumeMyOwnEvent(request)
        onReady(request, relayList.toSet())
        delay(100)
        client.publish(request, relayList)
    }

    fun cachedDecryptContent(note: Note): String? = cachedDecryptContent(note.event)

    fun cachedDecryptContent(event: Event?): String? {
        if (event == null) return null

        return if (isWriteable()) {
            when {
                event is EncryptedDmEvent -> privateDMDecryptionCache.cachedDM(event)
                event is ZapRequestEvent && event.isPrivateZap() -> privateZapsDecryptionCache.cachedPrivateZap(event)?.content
                event is DraftWrapEvent -> draftsDecryptionCache.preCachedDraft(event)?.content
                else -> event.content
            }
        } else {
            // A read-only (npub-only) account holds no key, so nothing above can run. Returning
            // `content` verbatim would push the raw NIP-04/NIP-44 base64 blob straight into the
            // UI (chat bubbles, Messages previews, ...). Callers treat null as "not readable".
            if (event.hasEncryptedContent()) null else event.content
        }
    }

    suspend fun decryptContent(note: Note): String? {
        val event = note.event
        return when {
            event is EncryptedDmEvent && isWriteable() -> {
                privateDMDecryptionCache.decryptDM(event)
            }

            event is ZapRequestEvent && isWriteable() -> {
                if (event.isPrivateZap()) {
                    if (isWriteable()) {
                        privateZapsDecryptionCache.decryptPrivateZap(event)?.content
                    } else {
                        null
                    }
                } else {
                    event.content
                }
            }

            event is DraftWrapEvent && isWriteable() -> {
                draftsDecryptionCache.cachedDraft(event)?.content
            }

            // Encrypted kinds that reached here did so because this account is not writeable
            // (every branch above is gated on isWriteable). Their `content` is ciphertext —
            // hand back null rather than let the blob render. See cachedDecryptContent.
            event != null && event.hasEncryptedContent() -> null

            else -> {
                event?.content
            }
        }
    }

    suspend fun decryptZapOrNull(event: ZapRequestEvent): PrivateZapEvent? = if (event.isPrivateZap() && isWriteable()) privateZapsDecryptionCache.decryptPrivateZap(event) else null

    fun isAllHidden(users: Set<HexKey>): Boolean = users.all { isHidden(it) }

    override fun isHidden(user: User) = isHidden(user.pubkeyHex)

    fun isHidden(userHex: String): Boolean = hiddenUsers.flow.value.isUserHidden(userHex)

    override fun followingKeySet(): Set<HexKey> = kind3FollowList.flow.value.authors

    /**
     * A note in a Concord community this account has joined. Notes keep a gatherer reference
     * from every account and community that ever touched them, so membership is checked here.
     */
    fun isInJoinedConcordCommunity(note: Note?): Boolean =
        note?.inGatherers?.any { g ->
            g is ConcordChannel && concordSessions.sessionFor(g.channelId.communityId) != null
        } == true

    /**
     * A Concord message in a joined community, or a reaction/repost to one (those are not
     * attached to the channel themselves, so they are recognized through their target).
     */
    fun isConcordActivity(note: Note): Boolean {
        if (isInJoinedConcordCommunity(note)) return true
        val event = note.event
        return (event is ReactionEvent || event is RepostEvent || event is GenericRepostEvent) && isInJoinedConcordCommunity(note.replyTo?.lastOrNull())
    }

    /** True only when a trust network is active and [pubkey] is not in it (see [TrustVerdicts]). */
    fun isOutsideTrustNetwork(pubkey: HexKey): Boolean = currentTrustVerdicts().isOutside(pubkey)

    /**
     * [isKnownChatroom] for a room that may not be loaded yet (a push that beat it): then the
     * same rule is applied to [sender], the only member the message vouches for. The room's
     * other members are whoever the sender chose to tag, so a stranger tagging a follow (or a
     * well-ranked key) must not make the room Known.
     */
    fun isKnownChatroom(
        key: ChatroomKey,
        sender: HexKey,
    ): Boolean {
        val room = chatroomList.rooms.get(key)
        if (room != null) return isKnownChatroom(key, room)
        if (chatroomList.hasSentMessagesTo(key) || key in settings.syncedSettings.chats.pinnedChatrooms.value) return true
        if (sender in followingKeySet()) return true
        val verdicts = currentTrustVerdicts()
        return verdicts.isActive && verdicts.passes(sender)
    }

    /**
     * Whether a private chat room belongs in Known rather than New Requests: a sender is
     * followed or (when a trust network is active) in the network, this account has written to
     * the room, or the user pinned it (an explicit "I know this conversation"). The single rule
     * for the DM tabs and DM notifications, in-app and push.
     */
    fun isKnownChatroom(
        key: ChatroomKey,
        room: Chatroom,
        followingKeySet: Set<HexKey> = followingKeySet(),
    ): Boolean {
        if (room.senderIntersects(followingKeySet) || chatroomList.hasSentMessagesTo(key)) return true
        if (key in settings.syncedSettings.chats.pinnedChatrooms.value) return true
        val verdicts = currentTrustVerdicts()
        return verdicts.isActive && room.activeSenders.any { verdicts.passes(it.pubkeyHex) }
    }

    fun isAcceptable(user: User): Boolean {
        if (userProfile().pubkeyHex == user.pubkeyHex) {
            return true
        }

        if (user.pubkeyHex in followingKeySet()) {
            return true
        }

        if (!settings.syncedSettings.security.warnAboutPostsWithReports.value) {
            if (isHidden(user)) return false

            val reports = user.reportsOrNull() ?: return true

            return reports.reportsBy(userProfile()).isEmpty() // if user has not reported this post
        }

        if (isHidden(user)) return false

        val reports = user.reportsOrNull() ?: return true
        val reportWarningThreshold =
            settings.syncedSettings.security.reportWarningThreshold.value
                .coerceAtLeast(1)

        // if user hasn't hided this author
        return reports.reportsBy(userProfile()).isEmpty() &&
            // if user has not reported this post
            reports.countReportAuthorsBy(followingKeySet()) < reportWarningThreshold
    }

    private fun isAcceptableDirect(note: Note): Boolean {
        if (!settings.syncedSettings.security.warnAboutPostsWithReports.value) {
            return !note.hasReportsBy(userProfile())
        }
        val reportWarningThreshold =
            settings.syncedSettings.security.reportWarningThreshold.value
                .coerceAtLeast(1)
        return !note.hasReportsBy(userProfile()) &&
            // if user has not reported this post
            note.countReportAuthorsBy(followingKeySet()) < reportWarningThreshold
    }

    fun isDecryptedContentHidden(noteEvent: EncryptedDmEvent): Boolean =
        if (hiddenUsers.flow.value.hiddenWordsCase
                .isNotEmpty()
        ) {
            val decrypted = privateDMDecryptionCache.cachedDM(noteEvent)
            decrypted?.containsAny(hiddenUsers.flow.value.hiddenWordsCase) == true
        } else {
            false
        }

    fun isFollowing(user: User): Boolean = user.pubkeyHex in followingKeySet()

    fun isFollowing(user: HexKey): Boolean = user in followingKeySet()

    fun isKnown(user: User): Boolean = user.pubkeyHex in allFollows.flow.value.authors

    fun isKnown(user: HexKey): Boolean = user in allFollows.flow.value.authors

    fun maxHashtagLimit(): Int = settings.syncedSettings.security.maxHashtagLimit.value

    fun hasExcessiveHashtags(note: Note): Boolean {
        val limit = maxHashtagLimit()
        return limit > 0 && note.event?.hasMoreHashtagsThan(limit) == true
    }

    /**
     * True if [note] is a Concord channel message whose author is banned in that
     * community's current fold. Bans are per-community (not global mutes), so they
     * are enforced here at read time — the same "filter, don't delete" approach the
     * rest of the app uses. A ban that arrives after a message is applied on the
     * next feed pass.
     */
    private fun isConcordBanned(note: Note): Boolean {
        val channel = note.inGatherers?.firstNotNullOfOrNull { it as? ConcordChannel } ?: return false
        val author = note.author?.pubkeyHex ?: note.event?.pubKey ?: return false
        val authority =
            concordSessions
                .sessionFor(channel.channelId.communityId)
                ?.state
                ?.value
                ?.authority ?: return false
        return authority.isBanned(author)
    }

    override fun isAcceptable(note: Note): Boolean {
        if (isConcordBanned(note)) return false
        if (isConcordExpired(note) || isUnbelievedConcordTimerNotice(note)) return false
        val mutedThreads = hiddenUsers.flow.value.mutedThreads
        if (mutedThreads.isNotEmpty() && mutedThreads.contains(resolveThreadRoot(note))) return false
        return note.author?.let { isAcceptable(it) } ?: true &&
            // if user hasn't hided this author
            isAcceptableDirect(note) &&
            !hasExcessiveHashtags(note) &&
            (
                (note.event !is RepostEvent && note.event !is GenericRepostEvent) ||
                    (
                        note.replyTo?.firstOrNull { isAcceptableDirect(it) } !=
                            null
                    )
            ) // is not a reaction about a blocked post
    }

    fun getRelevantReports(note: Note): Set<Note> {
        val innerReports =
            if (note.event is RepostEvent || note.event is GenericRepostEvent) {
                note.replyTo?.flatMap { getRelevantReports(it) } ?: emptyList()
            } else {
                emptyList()
            }

        return (
            note.reportsBy(kind3FollowList.flow.value.authorsPlusMe) +
                (note.author?.reportsOrNull()?.reportsBy(kind3FollowList.flow.value.authorsPlusMe) ?: emptyList()) +
                innerReports
        ).toSet()
    }

    suspend fun saveDMRelayList(dmRelays: List<NormalizedRelayUrl>) = sendLiterallyEverywhere(dmRelayList.saveRelayList(dmRelays))

    suspend fun saveKeyPackageRelayList(keyPackageRelays: List<NormalizedRelayUrl>) {
        val oldRelays = keyPackageRelayList.flow.value
        val newRelays = keyPackageRelays.toSet()
        sendLiterallyEverywhere(keyPackageRelayList.saveRelayList(keyPackageRelays))
        // Only relays that joined lack these; the rest already hold them, and a removal needs nothing.
        val addedRelays = newRelays - oldRelays
        if (addedRelays.isNotEmpty()) {
            republishEventsTo(myKeyPackageEvents(), addedRelays)
        }
    }

    suspend fun savePrivateOutboxRelayList(relays: List<NormalizedRelayUrl>) {
        val oldRelays = privateStorageRelayList.flow.value
        val newRelays = relays.toSet()
        sendMyPublicAndPrivateOutbox(privateStorageRelayList.saveRelayList(relays))
        // Only relays that joined lack these; the rest already hold them, and a removal needs nothing.
        val addedRelays = newRelays - oldRelays
        if (addedRelays.isNotEmpty()) {
            republishEventsTo(accountSettingsEvents(), addedRelays)
        }
    }

    suspend fun saveSearchRelayList(searchRelays: List<NormalizedRelayUrl>) {
        val oldRelays = searchRelayList.flowNoDefaults.value
        val newRelays = searchRelays.toSet()
        sendMyPublicAndPrivateOutbox(searchRelayList.saveRelayList(searchRelays))
        // Only relays that joined lack these; the rest already hold them, and a removal needs nothing.
        val addedRelays = newRelays - oldRelays
        if (addedRelays.isNotEmpty()) {
            republishEventsTo(
                listOfNotNull(userMetadata.getUserMetadataEvent()),
                addedRelays,
            )
        }
    }

    suspend fun saveIndexerRelayList(trustedRelays: List<NormalizedRelayUrl>) {
        val oldRelays = indexerRelayList.flowNoDefaults.value
        val newRelays = trustedRelays.toSet()
        sendMyPublicAndPrivateOutbox(indexerRelayList.saveRelayList(trustedRelays))
        // Only relays that joined lack these; the rest already hold them, and a removal needs nothing.
        val addedRelays = newRelays - oldRelays
        if (addedRelays.isNotEmpty()) {
            republishEventsTo(
                listOfNotNull(
                    userMetadata.getUserMetadataEvent(),
                    kind3FollowList.getFollowListEvent(),
                ),
                addedRelays,
            )
        }
    }

    suspend fun saveBroadcastRelayList(trustedRelays: List<NormalizedRelayUrl>) {
        val oldRelays = broadcastRelayList.flow.value
        val newRelays = trustedRelays.toSet()
        sendMyPublicAndPrivateOutbox(broadcastRelayList.saveRelayList(trustedRelays))
        // Only relays that joined lack these; the rest already hold them, and a removal needs nothing.
        val addedRelays = newRelays - oldRelays
        if (addedRelays.isNotEmpty()) {
            republishEventsTo(accountSettingsEvents(), addedRelays)
        }
    }

    suspend fun saveLocalRelayList(relays: List<NormalizedRelayUrl>) {
        val oldRelays = localRelayList.flow.value
        val newRelays = relays.toSet()
        localRelayList.saveRelayList(relays) {}
        // Only relays that joined lack these; the rest already hold them, and a removal needs nothing.
        val addedRelays = newRelays - oldRelays
        if (addedRelays.isNotEmpty()) {
            republishEventsTo(accountSettingsEvents(), addedRelays)
        }
    }

    suspend fun saveProxyRelayList(trustedRelays: List<NormalizedRelayUrl>) = sendMyPublicAndPrivateOutbox(proxyRelayList.saveRelayList(trustedRelays))

    suspend fun saveTrustedRelayList(trustedRelays: List<NormalizedRelayUrl>) = sendMyPublicAndPrivateOutbox(trustedRelayList.saveRelayList(trustedRelays))

    suspend fun saveFavoriteRelayList(trustedRelays: List<NormalizedRelayUrl>) = sendMyPublicAndPrivateOutbox(favoriteRelayList.saveRelayList(trustedRelays))

    suspend fun followRelayFeed(url: NormalizedRelayUrl) = sendMyPublicAndPrivateOutbox(favoriteRelayList.addRelay(url))

    suspend fun unfollowRelayFeed(url: NormalizedRelayUrl) = sendMyPublicAndPrivateOutbox(favoriteRelayList.removeRelay(url))

    suspend fun saveBlockedRelayList(blockedRelays: List<NormalizedRelayUrl>) = sendMyPublicAndPrivateOutbox(blockedRelayList.saveRelayList(blockedRelays))

    /**
     * Blocks a single relay, leaving the rest of the kind-10006 list alone.
     *
     * Once published, [com.vitorpamplona.amethyst.commons.relayClient.BlockedRelayFilteringClient]
     * strips the relay from every REQ, COUNT and publish, so the pool drops the socket as soon as
     * the subscriptions that wanted it are recomputed.
     */
    suspend fun blockRelay(relay: NormalizedRelayUrl) = sendMyPublicAndPrivateOutbox(blockedRelayList.addRelay(relay))

    /**
     * Returns all known signed replaceable events that configure this account
     * (profile, contact list, relay lists, mute list, bookmarks, etc.). Events
     * that have never been created or downloaded are omitted.
     */
    fun accountSettingsEvents(): List<Event> =
        listOfNotNull(
            userMetadata.getUserMetadataEvent(),
            userMetadata.getExternalIdentitiesEvent(),
            kind3FollowList.getFollowListEvent(),
            nip65RelayList.getNIP65RelayList(),
            dmRelayList.getDMRelayList(),
            keyPackageRelayList.getKeyPackageRelayList(),
            privateStorageRelayList.getPrivateOutboxRelayList(),
            searchRelayList.getSearchRelayList(),
            trustedRelayList.getTrustedRelayList(),
            proxyRelayList.getProxyRelayList(),
            broadcastRelayList.getBroadcastRelayList(),
            indexerRelayList.getIndexerRelayList(),
            favoriteRelayList.getFavoriteRelayList(),
            blockedRelayList.getBlockedRelayList(),
            muteList.getMuteList(),
            bookmarkState.getBookmarkList(),
            pinState.getPinList(),
            blossomServers.getBlossomServersList(),
            nestsServers.getNestsServersList(),
            paymentTargetsState.getPaymentTargetsEvent(),
            trustProviderList.getTrustProviderList(),
            cache.getAddressableNoteIfExists(appSpecific.getAppSpecificDataAddress())?.event,
        )

    /**
     * Returns all currently-known signed KeyPackage events authored by this account.
     */
    fun myKeyPackageEvents(): List<Event> =
        cache.addressables
            .filter(KeyPackageEvent.KIND, signer.pubKey)
            .mapNotNull { it.event }

    /** Publishes the given events to each of the given relays. No-op if either list is empty. */
    suspend fun requestToVanish(
        relays: List<NormalizedRelayUrl>,
        reason: String,
        createdAt: Long,
    ) {
        if (!isWriteable() || relays.isEmpty()) return

        val template = RequestToVanishEvent.build(relays, reason, createdAt)
        val signedEvent = signer.sign(template)
        cache.justConsumeMyOwnEvent(signedEvent)
        client.publish(signedEvent, outboxRelays.flow.value + relays.toSet())
    }

    suspend fun requestToVanishFromEverywhere(
        reason: String,
        createdAt: Long,
    ) {
        if (!isWriteable()) return

        val template = RequestToVanishEvent.buildVanishFromEverywhere(reason, createdAt)
        val signedEvent = signer.sign(template)
        cache.justConsumeMyOwnEvent(signedEvent)
        client.publish(signedEvent, followPlusAllMineWithIndex.flow.value + client.availableRelaysFlow().value)
    }

    suspend fun sendNip65RelayList(relays: List<AdvertisedRelayInfo>) {
        val oldOutbox = nip65RelayList.outboxFlowNoDefaults.value
        val newOutbox =
            relays
                .filter { it.type.isWrite() }
                .map { it.relayUrl }
                .toSet()
        sendLiterallyEverywhere(nip65RelayList.saveRelayList(relays))
        // Only outbox relays that joined lack these; an inbox-only change or a removal needs nothing.
        val addedOutbox = newOutbox - oldOutbox
        if (addedOutbox.isNotEmpty()) {
            republishEventsTo(accountSettingsEvents(), addedOutbox)
        }
    }

    suspend fun sendBlossomServersList(servers: List<String>) = sendMyPublicAndPrivateOutbox(blossomServers.saveBlossomServersList(servers))

    suspend fun sendNestsServersList(servers: List<com.vitorpamplona.quartz.nip53LiveActivities.nestsServers.NestsServer>) = sendMyPublicAndPrivateOutbox(nestsServers.saveNestsServersList(servers))

    suspend fun savePaymentTargets(targets: List<PaymentTarget>) = sendMyPublicAndPrivateOutbox(paymentTargetsState.savePaymentTargets(targets))

    suspend fun saveBolt12Offers(offers: List<String>) = sendMyPublicAndPrivateOutbox(bolt12OfferList.saveOffers(offers))

    fun markAsRead(
        route: String,
        timestampInSecs: Long,
    ) = settings.markAsRead(route, timestampInSecs)

    fun loadLastRead(route: String): Long = settings.lastReadPerRoute.value[route]?.value ?: 0

    fun loadLastReadFlow(route: String) = settings.getLastReadFlow(route)

    fun hasDonatedInThisVersion() = settings.hasDonatedInVersion(appVersion)

    // Built once: the donation card reads it from composition, and a stateIn per call would add a
    // collector on [scope] every time the card recomposes, never to be removed.
    val donatedInThisVersion: StateFlow<Boolean> by lazy {
        settings
            .observeDonatedInVersion(appVersion)
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, hasDonatedInThisVersion())
    }

    fun markDonatedInThisVersion() = settings.markDonatedInThisVersion(appVersion)

    fun dismissPollNotification(noteId: String) = settings.dismissPollNotification(noteId)

    fun hasViewedPollResults(noteId: String) = settings.hasViewedPollResults(noteId)

    fun markPollResultsViewed(
        noteId: String,
        pollEndsAt: Long?,
    ) = settings.markPollResultsViewed(noteId, pollEndsAt)

    init {
        Log.d("AccountRegisterObservers", "Init")

        // Route incoming call signaling into the state machine as soon as the account exists, so
        // offers are not missed while no UI is mounted.
        newNotesPreProcessor.callManager = callManager

        // Blocking a relay has to forget any "just for now" login to it, or unblocking later would
        // silently resume authenticating off an answer given before the block. Blocking is the
        // strongest signal available here — the weaker per-relay "never" answer already drops the grant via
        // RelayAuthPermissionLedger.setDecision, so it would be odd for the stronger one not to.
        //
        // Observed rather than hooked onto the local block action because the kind-10006 list is
        // shared: a block published by another client arrives as a flow update with no call of ours
        // behind it.
        scope.launch {
            blockedRelayList.flow.collect { blocked ->
                relayAuthLedger.revokeSessionGrantsFor(blocked.map { it.url })
            }
        }

        // Start the Cashu wallet state observers AFTER all field initializers
        // complete — auto-redeem can fire as soon as start() returns, and it
        // calls back into sendLiterallyEverywhere which depends on
        // followPlusAllMineWithIndex (initialized after cashuWalletState).
        // Doing this in start() rather than in the state's own init { } closes
        // the race where a publish would land on a half-built Account.
        cashuWalletState.start { event -> sendLiterallyEverywhere(event) }

        // Reopen the cordn coordinators this account used last time.
        //
        // Deliberately its own launch rather than a branch of Marmot's block
        // below: the two protocols are independent by design (§3.1 of
        // amethyst/plans/2026-09-19-cordn-ui.md), and a cordn coordinator that
        // is down must not delay Marmot's restore, or the reverse. Failures
        // are already contained per coordinator inside start().
        cordnRuntime?.let { runtime ->
            scope.launch(Dispatchers.IO) {
                runtime.restore()
            }
        }

        nwcBackup.start()

        // Restore Marmot MLS group state on startup
        if (marmotManager != null) {
            // Derived kind:1210 rows go straight into the conversation. Only
            // DERIVED rows arrive here — one received over the wire is an
            // assertion by its sender and is dropped at ingest — so these are
            // safe to render with attribution.
            marmotManager.onSystemRowDerived = { groupId, row ->
                // Indexed like any inner event: a bare `note.event = row` left the row with no
                // author, which the Messages tab read as "No messages yet".
                val note = marmot.indexMarmotInnerEvent(row).note
                marmotGroupList.addMessage(groupId, note)
            }

            // A disappearing message that is gone from disk but still on screen
            // has not disappeared. Drop it from the conversation as it expires,
            // rather than waiting for the next read to omit it.
            marmotManager.onMessagesExpired = { groupId, expiredIds ->
                expiredIds.forEach { id ->
                    cache.getNoteIfExists(id)?.let {
                        marmotGroupList.removeMessage(groupId, it)
                        cache.removeLocally(it)
                    }
                }
            }

            scope.launch(Dispatchers.IO) {
                marmotManager.restoreAll()

                // Ensure the local user has a KeyPackage published to relays
                // so other users can invite them to groups. Without this,
                // freshly installed accounts (and accounts that never opened
                // the Marmot Group screen) would never have an active
                // KeyPackage on the relays, and any inviter trying to add
                // them would fail with "No KeyPackage found".
                //
                // The KeyPackage bundle (private keys included) is persisted
                // by KeyPackageRotationManager via marmotKeyPackageStore, so
                // restoreAll() above has already restored any previously
                // generated bundles. Only generate-and-publish if no active
                // bundle exists in memory after restore. Launched, not awaited: it
                // waits on relay confirmations, and the group history below must
                // not wait on the network.
                scope.launch(Dispatchers.IO) { marmot.ensureMarmotKeyPackagePublished() }

                // Refresh our leaf in groups where it predates support for a
                // component, after the randomized delay the spec asks for.
                scope.launch(Dispatchers.IO) { marmot.runMarmotLeafRefreshLoop() }

                // Sync MIP-01 metadata from restored groups to chatrooms and
                // re-hydrate decrypted messages from persistent storage.
                // Note: Marmot MLS application messages cannot be re-decrypted
                // after the ratchet advances, so persisted plaintext is the
                // only way to restore group history across restarts.
                marmotManager.activeGroupIds().forEach { groupId ->
                    val chatroom = marmotGroupList.getOrCreateGroup(groupId)
                    marmotManager.syncMetadataTo(groupId, chatroom)
                    // Force the kind:445 EOSE manager to re-poll its filter
                    // set so the restored group's per-`h`-tag subscription
                    // is actually sent to relays. Without this, restored
                    // groups would never receive new messages until the user
                    // explicitly created/joined another group.
                    marmotGroupList.notifyGroupChanged(groupId)

                    val storedMessages = marmotManager.loadStoredMessages(groupId)
                    if (storedMessages.isNotEmpty()) {
                        Log.d("Account") {
                            "Restoring ${storedMessages.size} Marmot message(s) for group $groupId"
                        }
                        storedMessages.forEach { json ->
                            try {
                                val innerEvent =
                                    com.vitorpamplona.quartz.nip01Core.core.Event
                                        .fromJson(json)
                                // wasVerified=true: MIP-03 inner events are
                                // unsigned rumors (empty sig), authenticated
                                // via the MLS credential-identity check in
                                // GroupEventHandler when first decrypted.
                                // Running Nostr sig verify here (justVerify
                                // via wasVerified=false) would silently drop
                                // kind:7 reactions / kind:5 deletions since
                                // they never carry a Schnorr signature.
                                // Same indexing as live decryption, so a restored
                                // kind:1009 edit is re-linked to its message too.
                                val innerNote = marmot.indexMarmotInnerEvent(innerEvent).note
                                marmotGroupList.addMessage(groupId, innerNote)
                                marmot.applyMarmotModeration(groupId, innerEvent)
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                Log.w(
                                    "Account",
                                    "Failed to restore persisted Marmot message for $groupId",
                                    e,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Direct Invites (CORD-05 §6) on our DM relays arrive live through the account's gift-wrap
        // subscription. Senders that can't find our lists fall back to the stock Concord relays, which
        // are swept periodically instead (single-flight, so an overlap with the hub's own request is
        // dropped). Shown on Notifications and Messages, so swept without the hub open.
        scope.launch {
            delay(DIRECT_INVITE_SWEEP_START_MS)
            while (isActive) {
                concord.requestConcordDirectInviteSweep()
                delay(DIRECT_INVITE_SWEEP_EVERY_MS)
            }
        }

        // An invite that parks may be a catch-up a Grant already authorizes, so adopt those as they
        // land rather than waiting for the next revision tick, and once Concord is turned back on.
        scope.launch {
            combine(
                concord.directInviteInbox.pending.map { it.keys },
                chatFeedToggles.applied.map { ChatFeedType.CONCORD in it },
            ) { keys, on -> keys to on }
                .distinctUntilChanged()
                .collect {
                    runCatchingCancellable { concord.drainConcordCatchUps() }.onFailure { Log.w("Concord", "catch-up drain failed", it) }
                }
        }

        // Keep Concord channel metadata (community name/icon, membership) live across the whole
        // app — not just the hub screen — so the Messages tab renders each channel's community
        // chip, and per-community bans apply, as soon as a Control Plane folds. The revision now
        // bumps only on *structural* change (a fold / membership / rekey, never a plain message),
        // so this fires rarely; sample() stays as a cheap coalescer for a burst of folds.
        // The channel index is what every open Concord screen reads (canPost, the dissolved/banned/cut
        // notices), so it refreshes on its own collector. Sharing the loop below made it wait behind
        // the network-bound drains: a dissolution landing live left the composer up until a restart.
        scope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            concordSessions.revision.sample(500).collect { refreshConcordChannelIndex() }
        }

        scope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            concordSessions.revision.sample(500).collect {
                // A revision also bumps when a base-rotation rekey lands; adopt ours if present.
                runCatchingCancellable { concord.drainConcordRekeys() }.onFailure { Log.w("Concord", "rekey drain failed", it) }
                // A promotion to staff delivers the Control Plane write key inside the Grant
                // itself (CORD-04 §3), so the fold that seats the role is also when it arrives.
                runCatchingCancellable { concord.drainConcordStaffGrants() }.onFailure { Log.w("Concord", "staff grant drain failed", it) }
                // A Private Channel rotation lands on its channel-rekey address (CORD-06 §2): adopt the new
                // key, or drop the channel when it cut us.
                runCatchingCancellable { concord.drainConcordChannelRekeys() }.onFailure { Log.w("Concord", "channel rekey drain failed", it) }
                // A Grant folding late turns a parked catch-up invite into one we adopt without a click.
                runCatchingCancellable { concord.drainConcordCatchUps() }.onFailure { Log.w("Concord", "catch-up drain failed", it) }
                // An honored Kick naming us (CORD-04 §6): leave the community locally and say so.
                runCatchingCancellable { concord.drainConcordKicks() }.onFailure { Log.w("Concord", "kick drain failed", it) }
                // A rotation we were *excluded* from produces no rekey to drain, so it can only be
                // found by re-resolving the invite link we joined through. Rate-limited internally.
                runCatchingCancellable { concord.recoverStrandedConcordCommunities() }.onFailure { Log.w("Concord", "stranded recovery failed", it) }
            }
        }

        // CORD-04 §7: a PIN_MESSAGES holder owes keyless readers the deletion omission and the Edit
        // refresh whether or not the channel is open, so the delayed pin duties run from the account —
        // on every structural tick (a new Pin List head, a fold) and whenever a delete or an Edit lands.
        // The scheduler itself waits 3–15 s, keeps one duty per channel and one attempt per debt.
        scope.launch {
            @OptIn(FlowPreview::class)
            merge(
                concordSessions.revision.map { },
                cache.live.newEventBundles
                    .filter { notes -> notes.any { it.event is DeletionRequestEvent || it.event is ConcordChatEditEvent } }
                    .map { },
            ).sample(1000).collect {
                runCatching { concord.scheduleConcordPinDuties(scope) }.onFailure { Log.w("Concord", "pin duty scheduling failed", it) }
            }
        }

        // CORD-08 §3: purge disappearing Concord messages when they expire. Sleeps until the earliest
        // deadline any joined community holds and never wakes while nothing carries one, so a
        // community without a timer costs nothing. A new earlier deadline restarts the wait.
        scope.launch(Dispatchers.IO) {
            concordSessions.nextExpiry.collectLatest { at ->
                if (at == null) return@collectLatest
                // Coalesced: sleep a little past the deadline and sweep everything due by then, so a
                // burst of messages sent seconds apart expires in one pass instead of one sweep each.
                val waitMs = (at - TimeUtils.now()) * 1000 + CONCORD_EXPIRY_COALESCE_MS
                if (waitMs > 0) delay(waitMs)
                runCatching { sweepExpiredConcordMessages() }.onFailure { Log.w("Concord", "expired-message sweep failed", it) }
            }
        }

        scope.launch {
            cache.antiSpam.flowSpam.collect {
                it.cache.spamMessages.snapshot().values.forEach { spammer ->
                    if (!hiddenUsers.isHidden(spammer.pubkeyHex) &&
                        spammer.shouldHide() &&
                        spammer.pubkeyHex != userProfile().pubkeyHex &&
                        spammer.pubkeyHex !in followingKeySet()
                    ) {
                        hiddenUsers.hideUser(spammer.pubkeyHex)
                    }
                }
            }
        }

        chatFeedToggles.start(scope)

        // Apart from the collector below, which indexes one batch at a time: a remote signer's
        // decrypts start as each batch arrives instead of waiting for the batches ahead of it.
        scope.launch {
            cache.live.newEventBundles.collect { newNotes -> newNotesPreProcessor.prefetch(newNotes) }
        }

        scope.launch {
            cache.live.newEventBundles.collect { newNotes ->
                logTime("Account ${userProfile().toBestDisplayName()} newEventBundle Update with ${newNotes.size} new notes") {
                    upgradeAttestations()
                    // Cards fetched for profiles on screen keep the trust network fresh between syncs.
                    trustNetwork.offer(newNotes.mapNotNull { it.event as? UserAssertionEvent })
                    newNotesPreProcessor.runNew(newNotes)
                    followSets.newNotes(newNotes)
                    starterPacks.newNotes(newNotes)
                    bookmarkSets.newNotes(newNotes)
                    interestSets.newNotes(newNotes)
                    ownedEmojiPacks.newNotes(newNotes)
                    drss.newNotes(newNotes)
                }
            }
        }

        scope.launch {
            cache.live.deletedEventBundles.collect { deletedNotes ->
                logTime("Account ${userProfile().toBestDisplayName()} deletedEventBundle Update with ${deletedNotes.size} new notes") {
                    newNotesPreProcessor.runDeleted(deletedNotes)
                    followSets.deletedNotes(deletedNotes)
                    starterPacks.deletedNotes(deletedNotes)
                    bookmarkSets.deletedNotes(deletedNotes)
                    interestSets.deletedNotes(deletedNotes)
                    ownedEmojiPacks.deletedNotes(deletedNotes)
                    drss.deletedNotes(deletedNotes)
                }
            }
        }

        // The NIP-78 settings that live in AccountSettings publish on their own, so their many
        // setters (and the screens calling them) need no Account wrapper: a second after the last
        // change, or 30 s for top-bar feed filters. One wait serves both, so a feed switch never
        // holds back a setting changed just before it, and a pending feed switch rides along with
        // the next publish. See syncedSettingsPublishes. The publish runs on its own so a new
        // change cannot cancel it halfway through a remote signer's round trip.
        scope.launch(Dispatchers.IO) {
            syncedSettingsPublishes(settings.syncedChanges, settings.slowSyncedChanges).collect {
                if (!settings.isWriteable()) return@collect
                scope.launch(Dispatchers.IO) {
                    try {
                        sendNewAppSpecificData()
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.w("Account", "Could not publish the synced settings", e)
                    }
                }
            }
        }

        scope.launch(Dispatchers.IO) {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            settings.saveable.debounce(1000).collect {
                if (it.accountSettings != null) {
                    // Caught here: one failed write (a full disk, a locked keychain) escaping would
                    // end this collector, and nothing would be saved again until a restart.
                    try {
                        saveSettings(it.accountSettings)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.w("Account", "Could not save the account settings", e)
                    }
                }
            }
        }
    }
}
