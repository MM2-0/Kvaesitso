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
package com.vitorpamplona.amethyst.commons.model.preferences

import com.vitorpamplona.amethyst.commons.cashu.CashuKeysetCounterStore
import com.vitorpamplona.amethyst.commons.feeds.custom.FeedDefinition
import com.vitorpamplona.amethyst.commons.feeds.custom.FeedDefinitionSerializer
import com.vitorpamplona.amethyst.commons.model.AccentColorType
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.HomeFeedType
import com.vitorpamplona.amethyst.commons.model.backups.BackupConflictStorage
import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedType
import com.vitorpamplona.amethyst.commons.model.clink.ClinkDebitWalletEntry
import com.vitorpamplona.amethyst.commons.model.concord.ConcordViewMode
import com.vitorpamplona.amethyst.commons.model.deck.DeckLayout
import com.vitorpamplona.amethyst.commons.model.mediaServers.DEFAULT_MEDIA_SERVERS
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupViewMode
import com.vitorpamplona.amethyst.commons.model.nip47WalletConnect.NwcWalletEntry
import com.vitorpamplona.amethyst.commons.model.nip47WalletConnect.NwcWalletEntryNorm
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPolicy
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEvent
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListFragmentEvent
import com.vitorpamplona.quartz.experimental.ephemChat.list.EphemeralChatListEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRelayListEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip17Dm.settings.DmRelayListEvent
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip28PublicChat.list.PublicChatListEvent
import com.vitorpamplona.quartz.nip37Drafts.privateOutbox.PrivateOutboxRelayListEvent
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.favoriteAlgoFeedsList.FavoriteAlgoFeedsListEvent
import com.vitorpamplona.quartz.nip51Lists.geohashList.GeohashListEvent
import com.vitorpamplona.quartz.nip51Lists.interestList.InterestListEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BlockedRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.FavoriteRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.IndexerRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.TrustedRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.SimpleGroupListEvent
import com.vitorpamplona.quartz.nip60Cashu.wallet.CashuWalletEvent
import com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip72ModCommunities.follow.CommunityListEvent
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nipA3PaymentTargets.PaymentTargetsEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.offer.Bolt12OfferListEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

/**
 * An account's per-account DataStore records, read in one hop. Android reads them through
 * `LocalPreferences`, which falls back to its legacy encrypted file where a store is still empty;
 * a platform with no legacy file reads them straight through [AccountSettingsStores.load].
 */
class AccountStoreRecords(
    val identity: AccountIdentity,
    val followLists: Map<FollowListSlot, TopFilter>,
    val latestEvents: Map<LatestEventSlot, String>,
    val uploadSettings: UploadSettings,
    val dialogDismissal: DialogDismissal,
    val relayAuth: RelayAuth,
    val feedVisibility: FeedVisibility,
    val notificationPrefs: NotificationPrefs,
    val nwcBackupPrefs: NwcBackupPrefs = NwcBackupPrefs(),
    val composePrefs: ComposePrefs = ComposePrefs(),
)

/** The per-account stores [AccountSettings] is saved to and rebuilt from, all in the account's DataStore. */
class AccountSettingsStores(
    private val stores: AccountPreferenceStores,
) {
    fun identity(npub: String) = AccountIdentityStore(stores.getDataStore(npub))

    fun followLists(npub: String) = TopNavFollowListStore(stores.getDataStore(npub))

    fun latestEvents(npub: String) = LatestEventCacheStore(stores.getDataStore(npub))

    fun uploadSettings(npub: String) = UploadSettingsStore(stores.getDataStore(npub))

    fun dialogDismissal(npub: String) = DialogDismissalStore(stores.getDataStore(npub))

    fun relayAuth(npub: String) = RelayAuthStore(stores.getDataStore(npub))

    fun feedVisibility(npub: String) = FeedVisibilityStore(stores.getDataStore(npub))

    fun notificationPrefs(npub: String) = NotificationPrefsStore(stores.getDataStore(npub))

    fun nwcBackupPrefs(npub: String) = NwcBackupPrefsStore(stores.getDataStore(npub))

    fun composePrefs(npub: String) = ComposePrefsStore(stores.getDataStore(npub))

    /** Every store [npub] has, with no fallback: an empty store reads as its defaults. */
    suspend fun load(npub: String) =
        AccountStoreRecords(
            identity = identity(npub).load(),
            followLists = followLists(npub).load(),
            latestEvents = latestEvents(npub).load(),
            uploadSettings = uploadSettings(npub).load(),
            dialogDismissal = dialogDismissal(npub).load(),
            relayAuth = relayAuth(npub).load(),
            feedVisibility = feedVisibility(npub).load(),
            notificationPrefs = notificationPrefs(npub).load(),
            nwcBackupPrefs = nwcBackupPrefs(npub).load(),
            composePrefs = composePrefs(npub).load(),
        )

    /** Writes every store-backed part of [settings]. Keys and secrets have stores of their own. */
    suspend fun save(settings: AccountSettings) {
        val npub = settings.keyPair.pubKey.toNpub()
        identity(npub).save(
            AccountIdentity(
                pubKeyHex = settings.keyPair.pubKey.toHexKey(),
                loginWithExternalSigner = settings.externalSignerPackageName != null,
                externalSignerPackageName = settings.externalSignerPackageName,
                localRelayServers = settings.localRelayServers.value,
                openBackupConflictsJson = settings.openBackupConflicts().takeIf { it.isNotEmpty() }?.let { BackupConflictStorage.encode(it) },
            ),
        )
        uploadSettings(npub).save(
            UploadSettings(
                stripLocationOnUpload = settings.stripLocationOnUpload,
                optimizeMediaOnUpload = settings.optimizeMediaOnUpload.value,
                mirrorUploadsToAllServers = settings.mirrorUploadsToAllServers.value,
                useLocalBlossomCache = settings.useLocalBlossomCache.value,
                localBlossomCacheProfilePicturesOnly = settings.localBlossomCacheProfilePicturesOnly.value,
                defaultFileServerJson = JsonMapper.toJson(settings.defaultFileServer),
            ),
        )
        dialogDismissal(npub).save(
            DialogDismissal(
                hideDeleteRequestDialog = settings.hideDeleteRequestDialog,
                hideBlockAlertDialog = settings.hideBlockAlertDialog,
                hideNip17WarningDialog = settings.hideNIP17WarningDialog,
                hideCommunityRulesViolations = settings.hideCommunityRulesViolations.value,
                dismissedPollNoteIds = settings.dismissedPollNoteIds.value,
                dismissedChannelInvites = settings.dismissedChannelInvites.value,
                mutedPublicChats = settings.mutedPublicChats.value,
                hasDonatedInVersion = settings.hasDonatedInVersion.value,
                webOfTrustNudgeHiddenUntil = settings.webOfTrustNudgeHiddenUntil.value,
                viewedPollResultNoteIdsJson = JsonMapper.toJson(settings.viewedPollResultNoteIds.value),
            ),
        )
        relayAuth(npub).save(
            RelayAuth(
                policyName = settings.defaultRelayAuthPolicy.value.name,
                trustMyRelays = settings.relayAuthTrustMyRelaysAndVenues.value,
                trustReadFollows = settings.relayAuthTrustReadFollows.value,
                trustMessageFollows = settings.relayAuthTrustMessageFollows.value,
                trustMessageStrangers = settings.relayAuthTrustMessageStrangers.value,
            ),
        )
        feedVisibility(npub).save(
            FeedVisibility(
                disabledChatFeeds = ChatFeedType.encode(ChatFeedType.ALL - settings.enabledChatFeeds.value),
                disabledHomeFeedTypes = HomeFeedType.encode(HomeFeedType.ALL - settings.enabledHomeFeedTypes.value),
                relayGroupViewMode = settings.relayGroupViewMode.value.name,
                concordViewMode = settings.concordViewMode.value.name,
                callsEnabled = settings.callsEnabled.value,
                customFeedsJson =
                    settings.customFeeds.value
                        .ifEmpty { null }
                        ?.let { FeedDefinitionSerializer.serializeList(it) },
                deckLayoutJson = settings.deck.value.toJson(),
                accentColor = settings.accentColor.value?.name,
                openScreensJson = settings.openScreens.value,
            ),
        )
        notificationPrefs(npub).save(
            NotificationPrefs(
                alwaysOnService = settings.alwaysOnNotificationService.value,
                showMessagesInNotifications = settings.showMessagesInNotifications.value,
                splitNotificationsEnabled = settings.splitNotificationsEnabled.value,
            ),
        )
        nwcBackupPrefs(npub).save(
            NwcBackupPrefs(
                autoBackup = settings.backupNwcWallets.value,
                cardDismissed = settings.backupNwcCardDismissed.value,
                relays = settings.nwcBackupRelays.value.toSet(),
                walletIds = settings.backedUpNwcWalletIds.value,
            ),
        )
        composePrefs(npub).save(ComposePrefs(signature = settings.composeSignature.value))
        latestEvents(npub).saveAll(
            mapOf(
                LatestEventSlot.CONTACT_LIST to settings.backupContactList?.let { OptimizedJsonMapper.toJson(it) },
                LatestEventSlot.USER_METADATA to settings.backupUserMetadata?.let { OptimizedJsonMapper.toJson(it) },
                LatestEventSlot.DM_RELAY_LIST to settings.backupDMRelayList?.let { OptimizedJsonMapper.toJson(it) },
                LatestEventSlot.NIP65_RELAY_LIST to settings.backupNIP65RelayList?.let { OptimizedJsonMapper.toJson(it) },
                LatestEventSlot.SEARCH_RELAY_LIST to settings.backupSearchRelayList?.let { OptimizedJsonMapper.toJson(it) },
                LatestEventSlot.INDEX_RELAY_LIST to settings.backupIndexRelayList?.let { OptimizedJsonMapper.toJson(it) },
                LatestEventSlot.RELAY_FEEDS_LIST to settings.backupFavoriteRelayList?.let { OptimizedJsonMapper.toJson(it) },
                LatestEventSlot.BLOCKED_RELAY_LIST to settings.backupBlockedRelayList?.let { OptimizedJsonMapper.toJson(it) },
                LatestEventSlot.TRUSTED_RELAY_LIST to settings.backupTrustedRelayList?.let { OptimizedJsonMapper.toJson(it) },
                LatestEventSlot.MUTE_LIST to settings.backupMuteList?.let { OptimizedJsonMapper.toJson(it) },
                LatestEventSlot.PRIVATE_HOME_RELAY_LIST to settings.backupPrivateHomeRelayList?.let { OptimizedJsonMapper.toJson(it) },
                LatestEventSlot.APP_SPECIFIC_DATA to settings.backupAppSpecificData?.let { OptimizedJsonMapper.toJson(it) },
                LatestEventSlot.CHANNEL_LIST to settings.backupPublicChatList?.let { OptimizedJsonMapper.toJson(it) },
                LatestEventSlot.COMMUNITY_LIST to settings.backupCommunityList?.let { OptimizedJsonMapper.toJson(it) },
                LatestEventSlot.HASHTAG_LIST to settings.backupInterestList?.let { OptimizedJsonMapper.toJson(it) },
                LatestEventSlot.GEOHASH_LIST to settings.backupGeohashList?.let { OptimizedJsonMapper.toJson(it) },
                LatestEventSlot.EPHEMERAL_LIST to settings.backupEphemeralChatList?.let { OptimizedJsonMapper.toJson(it) },
                LatestEventSlot.RELAY_GROUP_LIST to settings.backupRelayGroupList?.let { OptimizedJsonMapper.toJson(it) },
                LatestEventSlot.CONCORD_LIST to settings.backupConcordList?.let { OptimizedJsonMapper.toJson(it) },
                // Event JSON never holds a raw newline, so one event per line is unambiguous.
                LatestEventSlot.CONCORD_LIST_FRAGMENTS to
                    settings.backupConcordListFragments
                        .takeIf { it.isNotEmpty() }
                        ?.joinToString("\n") { OptimizedJsonMapper.toJson(it) },
                LatestEventSlot.TRUST_PROVIDER_LIST to settings.backupTrustProviderList?.let { OptimizedJsonMapper.toJson(it) },
                LatestEventSlot.KEY_PACKAGE_RELAY_LIST to settings.backupKeyPackageRelayList?.let { OptimizedJsonMapper.toJson(it) },
                LatestEventSlot.FAVORITE_ALGO_FEEDS_LIST to settings.backupFavoriteAlgoFeedsList?.let { OptimizedJsonMapper.toJson(it) },
                LatestEventSlot.PAYMENT_TARGETS to settings.backupNipA3PaymentTargets?.let { OptimizedJsonMapper.toJson(it) },
                LatestEventSlot.BOLT12_OFFERS to settings.backupBolt12Offers?.let { OptimizedJsonMapper.toJson(it) },
                LatestEventSlot.CASHU_WALLET to settings.backupCashuWallet?.let { OptimizedJsonMapper.toJson(it) },
                LatestEventSlot.NUTZAP_INFO to settings.backupNutzapInfo?.let { OptimizedJsonMapper.toJson(it) },
            ),
        )
        followLists(npub).saveAll(
            mapOf(
                FollowListSlot.HOME to settings.defaultHomeFollowList.value,
                FollowListSlot.STORIES to settings.defaultStoriesFollowList.value,
                FollowListSlot.NOTIFICATION to settings.defaultNotificationFollowList.value,
                FollowListSlot.DISCOVERY to settings.defaultDiscoveryFollowList.value,
                FollowListSlot.POLLS to settings.defaultPollsFollowList.value,
                FollowListSlot.PICTURES to settings.defaultPicturesFollowList.value,
                FollowListSlot.RELAY_GROUPS_DISCOVERY to settings.defaultRelayGroupsDiscoveryFollowList.value,
                FollowListSlot.NAPPLETS to settings.defaultNappletsFollowList.value,
                FollowListSlot.NSITES to settings.defaultNsitesFollowList.value,
                FollowListSlot.WORKOUTS to settings.defaultWorkoutsFollowList.value,
                FollowListSlot.GIT_REPOSITORIES to settings.defaultGitRepositoriesFollowList.value,
                FollowListSlot.HIGHLIGHTS to settings.defaultHighlightsFollowList.value,
                FollowListSlot.CALENDARS to settings.defaultCalendarsFollowList.value,
                FollowListSlot.PRODUCTS to settings.defaultProductsFollowList.value,
                FollowListSlot.GEOCACHES to settings.defaultGeocachesFollowList.value,
                FollowListSlot.SHORTS to settings.defaultShortsFollowList.value,
                FollowListSlot.PUBLIC_CHATS to settings.defaultPublicChatsFollowList.value,
                FollowListSlot.LIVE_STREAMS to settings.defaultLiveStreamsFollowList.value,
                FollowListSlot.NESTS to settings.defaultNestsFollowList.value,
                FollowListSlot.LONGS to settings.defaultLongsFollowList.value,
                FollowListSlot.ARTICLES to settings.defaultArticlesFollowList.value,
                FollowListSlot.MUSIC_TRACKS to settings.defaultMusicTracksFollowList.value,
                FollowListSlot.MUSIC_PLAYLISTS to settings.defaultMusicPlaylistsFollowList.value,
                FollowListSlot.PODCAST_EPISODES to settings.defaultPodcastEpisodesFollowList.value,
                FollowListSlot.PODCASTS to settings.defaultPodcastsFollowList.value,
                FollowListSlot.SOFTWARE_APPS to settings.defaultSoftwareAppsFollowList.value,
                FollowListSlot.BADGES to settings.defaultBadgesFollowList.value,
                FollowListSlot.BROWSE_EMOJI_SETS to settings.defaultBrowseEmojiSetsFollowList.value,
                FollowListSlot.COMMUNITIES to settings.defaultCommunitiesFollowList.value,
                FollowListSlot.FOLLOW_PACKS to settings.defaultFollowPacksFollowList.value,
                FollowListSlot.APP_RECOMMENDATIONS to settings.defaultAppRecommendationsFollowList.value,
                FollowListSlot.CHESS to settings.defaultChessFollowList.value,
            ),
        )
    }
}

/**
 * The secret group of [AccountSettings] as its encrypted store keeps it. Wallets are stored in their
 * denormalized shape, which is the shape [toAccountSettings] parses back.
 */
fun AccountSettings.toAccountSecrets() =
    AccountSecrets(
        nip46SignerEnabled = nip46SignerEnabled.value,
        nip46BunkerSecret = nip46BunkerSecret.value,
        nip46TransportKey = nip46TransportKey.value,
        nip46SeenRequestIds = nip46SeenRequestIds.value,
        nwcWalletsJson =
            nwcWallets.value
                .mapNotNull { it.denormalize() }
                .takeIf { it.isNotEmpty() }
                ?.let { JsonMapper.toJson(it) },
        clinkDebitWalletsJson =
            clinkDebitWallets.value
                .map { it.denormalize() }
                .takeIf { it.isNotEmpty() }
                ?.let { JsonMapper.toJson(it) },
        defaultPaymentSourceId = defaultPaymentSourceId.value,
        remoteSignerBunkerUri = remoteSignerBunkerUri,
        remoteSignerTransportKey = remoteSignerTransportKey,
    )

/**
 * What an account's [AccountSettings] are rebuilt from: its [stores], the [secrets] and private key
 * kept apart from them, the two values still kept outside any store ([pendingAttestationsJson],
 * [lastReadPerRouteJson]) and the platform's Cashu [cashuCounters].
 */
class AccountSettingsSource(
    val keyPair: KeyPair,
    val externalSignerPackageName: String?,
    val stores: AccountStoreRecords,
    val secrets: AccountSecrets,
    val pendingAttestationsJson: String?,
    val lastReadPerRouteJson: String?,
    val cashuCounters: CashuKeysetCounterStore,
) {
    val identity: AccountIdentity get() = stores.identity
}

/** Rebuilds the account's settings, parsing its saved events in parallel. */
suspend fun AccountSettingsSource.toAccountSettings(): AccountSettings =
    coroutineScope {
        val npub = keyPair.pubKey.toNpub()

        val stripLocationOnUpload = stores.uploadSettings.stripLocationOnUpload
        val useLocalBlossomCache = stores.uploadSettings.useLocalBlossomCache
        val localBlossomCacheProfilePicturesOnly = stores.uploadSettings.localBlossomCacheProfilePicturesOnly
        val mirrorUploadsToAllServers = stores.uploadSettings.mirrorUploadsToAllServers
        val optimizeMediaOnUpload = stores.uploadSettings.optimizeMediaOnUpload
        val hideCommunityRulesViolations = stores.dialogDismissal.hideCommunityRulesViolations
        val hideDeleteRequestDialog = stores.dialogDismissal.hideDeleteRequestDialog
        val hideBlockAlertDialog = stores.dialogDismissal.hideBlockAlertDialog
        val hideNIP17WarningDialog = stores.dialogDismissal.hideNip17WarningDialog
        val callsEnabled = stores.feedVisibility.callsEnabled
        val alwaysOnNotificationService = stores.notificationPrefs.alwaysOnService
        // Read as a group via a helper: this load lambda sits right at the JVM's
        // per-method bytecode limit (see the note above the awaits below), so keeping
        // these heavy string/enum decodes out of it preserves headroom.
        val inboxPrefs = readInboxPrefs(stores.relayAuth, stores.feedVisibility)
        val splitNotificationsEnabled = stores.notificationPrefs.splitNotificationsEnabled
        val showMessagesInNotifications = stores.notificationPrefs.showMessagesInNotifications
        val hasDonatedInVersion = stores.dialogDismissal.hasDonatedInVersion
        val webOfTrustNudgeHiddenUntil = stores.dialogDismissal.webOfTrustNudgeHiddenUntil
        val dismissedPollNoteIds = stores.dialogDismissal.dismissedPollNoteIds
        val dismissedChannelInvites = stores.dialogDismissal.dismissedChannelInvites
        val mutedPublicChats = stores.dialogDismissal.mutedPublicChats
        val viewedPollResultNoteIdsStr = stores.dialogDismissal.viewedPollResultNoteIdsJson
        val localRelayServers = identity.localRelayServers

        val followListPrefs = toFollowListPrefs(stores.followLists)

        val nip46SignerEnabled = secrets.nip46SignerEnabled
        val nip46BunkerSecret = secrets.nip46BunkerSecret
        val nip46TransportKey = secrets.nip46TransportKey
        val nip46SeenRequestIds = secrets.nip46SeenRequestIds
        val zapPaymentRequestServerStr = secrets.legacyZapPaymentRequestServer
        val nwcWalletsStr = secrets.nwcWalletsJson
        val defaultNwcWalletIdStr = secrets.legacyDefaultNwcWalletId
        val clinkDebitWalletsStr = secrets.clinkDebitWalletsJson
        val defaultPaymentSourceIdStr = secrets.defaultPaymentSourceId
        val defaultFileServerStr = stores.uploadSettings.defaultFileServerJson

        val pendingAttestationsStr = pendingAttestationsJson
        val openBackupConflictsStr = identity.openBackupConflictsJson
        val latestUserMetadataStr = stores.latestEvents[LatestEventSlot.USER_METADATA]
        val latestContactListStr = stores.latestEvents[LatestEventSlot.CONTACT_LIST]
        val latestDmRelayListStr = stores.latestEvents[LatestEventSlot.DM_RELAY_LIST]
        val latestNip65RelayListStr = stores.latestEvents[LatestEventSlot.NIP65_RELAY_LIST]
        val latestSearchRelayListStr = stores.latestEvents[LatestEventSlot.SEARCH_RELAY_LIST]
        val latestIndexRelayListStr = stores.latestEvents[LatestEventSlot.INDEX_RELAY_LIST]
        val latestFavoriteRelayListStr = stores.latestEvents[LatestEventSlot.RELAY_FEEDS_LIST]
        val latestBlockedRelayListStr = stores.latestEvents[LatestEventSlot.BLOCKED_RELAY_LIST]
        val latestTrustedRelayListStr = stores.latestEvents[LatestEventSlot.TRUSTED_RELAY_LIST]
        val latestMuteListStr = stores.latestEvents[LatestEventSlot.MUTE_LIST]
        val latestPrivateHomeRelayListStr = stores.latestEvents[LatestEventSlot.PRIVATE_HOME_RELAY_LIST]
        val latestAppSpecificDataStr = stores.latestEvents[LatestEventSlot.APP_SPECIFIC_DATA]
        val latestPublicChatListStr = stores.latestEvents[LatestEventSlot.CHANNEL_LIST]
        val latestCommunityListStr = stores.latestEvents[LatestEventSlot.COMMUNITY_LIST]
        val latestInterestListStr = stores.latestEvents[LatestEventSlot.HASHTAG_LIST]
        val latestGeohashListStr = stores.latestEvents[LatestEventSlot.GEOHASH_LIST]
        val latestEphemeralListStr = stores.latestEvents[LatestEventSlot.EPHEMERAL_LIST]
        val latestRelayGroupListStr = stores.latestEvents[LatestEventSlot.RELAY_GROUP_LIST]
        val latestConcordListStr = stores.latestEvents[LatestEventSlot.CONCORD_LIST]
        val latestTrustProviderListStr = stores.latestEvents[LatestEventSlot.TRUST_PROVIDER_LIST]
        val latestKeyPackageRelayListStr = stores.latestEvents[LatestEventSlot.KEY_PACKAGE_RELAY_LIST]
        val latestFavoriteAlgoFeedsListStr = stores.latestEvents[LatestEventSlot.FAVORITE_ALGO_FEEDS_LIST]
        val latestPaymentTargetsStr = stores.latestEvents[LatestEventSlot.PAYMENT_TARGETS]
        val latestBolt12OffersStr = stores.latestEvents[LatestEventSlot.BOLT12_OFFERS]
        val latestCashuWalletStr = stores.latestEvents[LatestEventSlot.CASHU_WALLET]
        val latestNutzapInfoStr = stores.latestEvents[LatestEventSlot.NUTZAP_INFO]
        val lastReadPerRouteStr = lastReadPerRouteJson

        Log.d("LocalPreferences") { "Rebuild account $npub - before parsing events" }

        val nwcWalletsLoaded =
            async {
                val nwcWalletEntries = parseOrNull<List<NwcWalletEntry>>(nwcWalletsStr)
                if (nwcWalletEntries != null && nwcWalletEntries.isNotEmpty()) {
                    val wallets = nwcWalletEntries.mapNotNull { it.normalize() }
                    val defaultId = defaultNwcWalletIdStr ?: wallets.firstOrNull()?.id
                    Pair(wallets, defaultId)
                } else {
                    val legacyUri = parseOrNull<Nip47WalletConnect.Nip47URI>(zapPaymentRequestServerStr)
                    val legacyNorm = legacyUri?.normalize()
                    if (legacyNorm != null) {
                        val migrated =
                            NwcWalletEntryNorm(
                                id =
                                    UUID.randomUUID().toString(),
                                name = "Wallet",
                                uri = legacyNorm,
                            )
                        Pair(listOf(migrated), migrated.id)
                    } else {
                        Pair(emptyList(), null)
                    }
                }
            }
        val clinkDebitsLoaded =
            async {
                parseOrNull<List<ClinkDebitWalletEntry>>(clinkDebitWalletsStr)?.mapNotNull { it.normalize() } ?: emptyList()
            }
        val defaultFileServer = async { parseOrNull<ServerName>(defaultFileServerStr) ?: DEFAULT_MEDIA_SERVERS[0] }

        val viewedPollResultNoteIds = async { parseOrNull<Map<String, Long>>(viewedPollResultNoteIdsStr) ?: mapOf() }
        val pendingAttestations = async { parseOrNull<Map<HexKey, String>>(pendingAttestationsStr) ?: mapOf() }
        val latestUserMetadata = async { parseEventOrNull<MetadataEvent>(latestUserMetadataStr) }
        val latestContactList = async { parseEventOrNull<ContactListEvent>(latestContactListStr) }
        val latestDmRelayList = async { parseEventOrNull<DmRelayListEvent>(latestDmRelayListStr) }
        val latestNip65RelayList = async { parseEventOrNull<AdvertisedRelayListEvent>(latestNip65RelayListStr) }
        val latestSearchRelayList = async { parseEventOrNull<SearchRelayListEvent>(latestSearchRelayListStr) }
        val latestIndexRelayList = async { parseEventOrNull<IndexerRelayListEvent>(latestIndexRelayListStr) }
        val latestFavoriteRelayList = async { parseEventOrNull<FavoriteRelayListEvent>(latestFavoriteRelayListStr) }
        val latestBlockedRelayList = async { parseEventOrNull<BlockedRelayListEvent>(latestBlockedRelayListStr) }
        val latestTrustedRelayList = async { parseEventOrNull<TrustedRelayListEvent>(latestTrustedRelayListStr) }
        val latestMuteList = async { parseEventOrNull<MuteListEvent>(latestMuteListStr) }
        val latestPrivateHomeRelayList = async { parseEventOrNull<PrivateOutboxRelayListEvent>(latestPrivateHomeRelayListStr) }
        val latestAppSpecificData = async { parseEventOrNull<AppSpecificDataEvent>(latestAppSpecificDataStr) }
        val latestPublicChatList = async { parseEventOrNull<PublicChatListEvent>(latestPublicChatListStr) }
        val latestCommunityList = async { parseEventOrNull<CommunityListEvent>(latestCommunityListStr) }
        val latestInterestList = async { parseEventOrNull<InterestListEvent>(latestInterestListStr) }
        val latestGeohashList = async { parseEventOrNull<GeohashListEvent>(latestGeohashListStr) }
        val latestEphemeralList = async { parseEventOrNull<EphemeralChatListEvent>(latestEphemeralListStr) }
        val latestRelayGroupList = async { parseEventOrNull<SimpleGroupListEvent>(latestRelayGroupListStr) }
        val latestConcordList = async { parseEventOrNull<ConcordCommunityListEvent>(latestConcordListStr) }
        val latestTrustProviderList = async { parseEventOrNull<TrustProviderListEvent>(latestTrustProviderListStr) }
        val latestKeyPackageRelayList = async { parseEventOrNull<KeyPackageRelayListEvent>(latestKeyPackageRelayListStr) }
        val latestFavoriteAlgoFeedsList = async { parseEventOrNull<FavoriteAlgoFeedsListEvent>(latestFavoriteAlgoFeedsListStr) }
        val latestPaymentTargets = async { parseEventOrNull<PaymentTargetsEvent>(latestPaymentTargetsStr) }
        val latestBolt12Offers = async { parseEventOrNull<Bolt12OfferListEvent>(latestBolt12OffersStr) }
        val latestCashuWallet =
            async {
                parseEventOrNull<CashuWalletEvent>(latestCashuWalletStr)
            }
        val latestNutzapInfo =
            async {
                parseEventOrNull<NutzapInfoEvent>(latestNutzapInfoStr)
            }

        val lastReadPerRoute =
            async {
                parseOrNull<Map<String, Long>>(lastReadPerRouteStr)?.mapValues {
                    MutableStateFlow(it.value)
                } ?: mapOf()
            }

        Log.d("LocalPreferences") { "Rebuild account $npub - asyncs created" }

        // Resolve every parallel parse into a local before constructing AccountSettings.
        // Awaiting inside the 70-argument constructor expression below would place ~27
        // suspension points in the middle of a single huge operand stack, forcing the
        // coroutine state machine to spill/restore every partially-evaluated argument at
        // each point. That bloats the generated method past the compiler's per-method
        // instruction limit ("Method exceeds compiler instruction limit"). Awaiting into
        // vals first keeps each suspension point at a statement boundary (near-empty
        // operand stack) and leaves the constructor as straight-line, suspension-free code.
        val nwcWalletsResolved = nwcWalletsLoaded.await()
        val clinkDebitsResolved = clinkDebitsLoaded.await()
        val defaultFileServerResolved = defaultFileServer.await()
        val viewedPollResultNoteIdsResolved = viewedPollResultNoteIds.await()
        val pendingAttestationsResolved = pendingAttestations.await()
        val lastReadPerRouteResolved = lastReadPerRoute.await()
        val latestUserMetadataResolved = latestUserMetadata.await()
        val latestContactListResolved = latestContactList.await()
        val latestDmRelayListResolved = latestDmRelayList.await()
        val latestNip65RelayListResolved = latestNip65RelayList.await()
        val latestSearchRelayListResolved = latestSearchRelayList.await()
        val latestIndexRelayListResolved = latestIndexRelayList.await()
        val latestFavoriteRelayListResolved = latestFavoriteRelayList.await()
        val latestBlockedRelayListResolved = latestBlockedRelayList.await()
        val latestTrustedRelayListResolved = latestTrustedRelayList.await()
        val latestMuteListResolved = latestMuteList.await()
        val latestPrivateHomeRelayListResolved = latestPrivateHomeRelayList.await()
        val latestAppSpecificDataResolved = latestAppSpecificData.await()
        val latestPublicChatListResolved = latestPublicChatList.await()
        val latestCommunityListResolved = latestCommunityList.await()
        val latestInterestListResolved = latestInterestList.await()
        val latestGeohashListResolved = latestGeohashList.await()
        val latestEphemeralListResolved = latestEphemeralList.await()
        val latestRelayGroupListResolved = latestRelayGroupList.await()
        val latestConcordListResolved = latestConcordList.await()
        val latestTrustProviderListResolved = latestTrustProviderList.await()
        val latestKeyPackageRelayListResolved = latestKeyPackageRelayList.await()
        val latestFavoriteAlgoFeedsListResolved = latestFavoriteAlgoFeedsList.await()
        val latestPaymentTargetsResolved = latestPaymentTargets.await()
        val latestBolt12OffersResolved = latestBolt12Offers.await()
        val latestCashuWalletResolved = latestCashuWallet.await()
        val latestNutzapInfoResolved = latestNutzapInfo.await()

        Log.d("LocalPreferences") { "Rebuild account $npub - asyncs resolved" }

        return@coroutineScope AccountSettings(
            keyPair = keyPair,
            transientAccount = false,
            cashuCounters = cashuCounters,
            externalSignerPackageName = externalSignerPackageName,
            remoteSignerBunkerUri = secrets.remoteSignerBunkerUri,
            remoteSignerTransportKey = secrets.remoteSignerTransportKey,
            localRelayServers = MutableStateFlow(localRelayServers),
            defaultFileServer = defaultFileServerResolved,
            stripLocationOnUpload = stripLocationOnUpload,
            useLocalBlossomCache = MutableStateFlow(useLocalBlossomCache),
            localBlossomCacheProfilePicturesOnly = MutableStateFlow(localBlossomCacheProfilePicturesOnly),
            mirrorUploadsToAllServers = MutableStateFlow(mirrorUploadsToAllServers),
            optimizeMediaOnUpload = MutableStateFlow(optimizeMediaOnUpload),
            hideCommunityRulesViolations = MutableStateFlow(hideCommunityRulesViolations),
            nip46SignerEnabled = MutableStateFlow(nip46SignerEnabled),
            nip46BunkerSecret = MutableStateFlow(nip46BunkerSecret),
            nip46TransportKey = MutableStateFlow(nip46TransportKey),
            nip46SeenRequestIds = MutableStateFlow(nip46SeenRequestIds),
            defaultHomeFollowList = MutableStateFlow(followListPrefs.home),
            defaultStoriesFollowList = MutableStateFlow(followListPrefs.stories),
            defaultNotificationFollowList = MutableStateFlow(followListPrefs.notification),
            defaultDiscoveryFollowList = MutableStateFlow(followListPrefs.discovery),
            defaultPollsFollowList = MutableStateFlow(followListPrefs.polls),
            defaultPicturesFollowList = MutableStateFlow(followListPrefs.pictures),
            defaultRelayGroupsDiscoveryFollowList = MutableStateFlow(followListPrefs.relayGroupsDiscovery),
            defaultNappletsFollowList = MutableStateFlow(followListPrefs.napplets),
            defaultNsitesFollowList = MutableStateFlow(followListPrefs.nsites),
            defaultWorkoutsFollowList = MutableStateFlow(followListPrefs.workouts),
            defaultGitRepositoriesFollowList = MutableStateFlow(followListPrefs.gitRepositories),
            defaultHighlightsFollowList = MutableStateFlow(followListPrefs.highlights),
            defaultCalendarsFollowList = MutableStateFlow(followListPrefs.calendars),
            defaultProductsFollowList = MutableStateFlow(followListPrefs.products),
            defaultGeocachesFollowList = MutableStateFlow(followListPrefs.geocaches),
            defaultShortsFollowList = MutableStateFlow(followListPrefs.shorts),
            defaultPublicChatsFollowList = MutableStateFlow(followListPrefs.publicChats),
            defaultLiveStreamsFollowList = MutableStateFlow(followListPrefs.liveStreams),
            defaultNestsFollowList = MutableStateFlow(followListPrefs.nests),
            defaultLongsFollowList = MutableStateFlow(followListPrefs.longs),
            defaultMusicTracksFollowList = MutableStateFlow(followListPrefs.musicTracks),
            defaultMusicPlaylistsFollowList = MutableStateFlow(followListPrefs.musicPlaylists),
            defaultPodcastEpisodesFollowList = MutableStateFlow(followListPrefs.podcastEpisodes),
            defaultPodcastsFollowList = MutableStateFlow(followListPrefs.podcasts),
            defaultArticlesFollowList = MutableStateFlow(followListPrefs.articles),
            defaultSoftwareAppsFollowList = MutableStateFlow(followListPrefs.softwareApps),
            defaultBadgesFollowList = MutableStateFlow(followListPrefs.badges),
            defaultBrowseEmojiSetsFollowList = MutableStateFlow(followListPrefs.browseEmojiSets),
            defaultCommunitiesFollowList = MutableStateFlow(followListPrefs.communities),
            defaultFollowPacksFollowList = MutableStateFlow(followListPrefs.followPacks),
            defaultAppRecommendationsFollowList = MutableStateFlow(followListPrefs.appRecommendations),
            defaultChessFollowList = MutableStateFlow(followListPrefs.chess),
            nwcWallets = MutableStateFlow(nwcWalletsResolved.first),
            clinkDebitWallets = MutableStateFlow(clinkDebitsResolved),
            // Prefer the new unified default; migrate from the legacy NWC default;
            // else fall back to the first configured source (NWC before debits).
            defaultPaymentSourceId =
                MutableStateFlow(
                    defaultPaymentSourceIdStr
                        ?: nwcWalletsResolved.second
                        ?: clinkDebitsResolved.firstOrNull()?.id,
                ),
            hideDeleteRequestDialog = hideDeleteRequestDialog,
            hideBlockAlertDialog = hideBlockAlertDialog,
            hideNIP17WarningDialog = hideNIP17WarningDialog,
            alwaysOnNotificationService = MutableStateFlow(alwaysOnNotificationService),
            defaultRelayAuthPolicy = MutableStateFlow(inboxPrefs.defaultRelayAuthPolicy),
            relayGroupViewMode = MutableStateFlow(inboxPrefs.relayGroupViewMode),
            customFeeds = MutableStateFlow(inboxPrefs.customFeeds),
            deck = MutableStateFlow(inboxPrefs.deck),
            openScreens = MutableStateFlow(inboxPrefs.openScreens),
            concordViewMode = MutableStateFlow(inboxPrefs.concordViewMode),
            accentColor = MutableStateFlow(inboxPrefs.accentColor),
            enabledChatFeeds = MutableStateFlow(inboxPrefs.enabledChatFeeds),
            enabledHomeFeedTypes = MutableStateFlow(inboxPrefs.enabledHomeFeedTypes),
            relayAuthTrustMyRelaysAndVenues = MutableStateFlow(inboxPrefs.relayAuthTrustMyRelays),
            relayAuthTrustReadFollows = MutableStateFlow(inboxPrefs.relayAuthTrustReadFollows),
            relayAuthTrustMessageFollows = MutableStateFlow(inboxPrefs.relayAuthTrustMessageFollows),
            relayAuthTrustMessageStrangers = MutableStateFlow(inboxPrefs.relayAuthTrustMessageStrangers),
            splitNotificationsEnabled = MutableStateFlow(splitNotificationsEnabled),
            showMessagesInNotifications = MutableStateFlow(showMessagesInNotifications),
            backupUserMetadata = latestUserMetadataResolved,
            backupContactList = latestContactListResolved,
            backupNIP65RelayList = latestNip65RelayListResolved,
            backupDMRelayList = latestDmRelayListResolved,
            backupSearchRelayList = latestSearchRelayListResolved,
            backupIndexRelayList = latestIndexRelayListResolved,
            backupFavoriteRelayList = latestFavoriteRelayListResolved,
            backupBlockedRelayList = latestBlockedRelayListResolved,
            backupTrustedRelayList = latestTrustedRelayListResolved,
            backupPrivateHomeRelayList = latestPrivateHomeRelayListResolved,
            backupMuteList = latestMuteListResolved,
            backupAppSpecificData = latestAppSpecificDataResolved,
            backupPublicChatList = latestPublicChatListResolved,
            backupCommunityList = latestCommunityListResolved,
            backupInterestList = latestInterestListResolved,
            backupGeohashList = latestGeohashListResolved,
            backupEphemeralChatList = latestEphemeralListResolved,
            backupRelayGroupList = latestRelayGroupListResolved,
            backupConcordList = latestConcordListResolved,
            backupConcordListFragments = parseConcordListFragments(stores.latestEvents[LatestEventSlot.CONCORD_LIST_FRAGMENTS]),
            backupTrustProviderList = latestTrustProviderListResolved,
            backupKeyPackageRelayList = latestKeyPackageRelayListResolved,
            backupFavoriteAlgoFeedsList = latestFavoriteAlgoFeedsListResolved,
            lastReadPerRoute = MutableStateFlow(lastReadPerRouteResolved),
            hasDonatedInVersion = MutableStateFlow(hasDonatedInVersion),
            webOfTrustNudgeHiddenUntil = MutableStateFlow(webOfTrustNudgeHiddenUntil),
            dismissedPollNoteIds = MutableStateFlow(dismissedPollNoteIds),
            dismissedChannelInvites = MutableStateFlow(dismissedChannelInvites),
            mutedPublicChats = MutableStateFlow(mutedPublicChats),
            viewedPollResultNoteIds = MutableStateFlow(viewedPollResultNoteIdsResolved),
            pendingAttestations = MutableStateFlow(pendingAttestationsResolved),
            backupNipA3PaymentTargets = latestPaymentTargetsResolved,
            backupBolt12Offers = latestBolt12OffersResolved,
            backupCashuWallet = latestCashuWalletResolved,
            backupNutzapInfo = latestNutzapInfoResolved,
            callsEnabled = MutableStateFlow(callsEnabled),
        ).also {
            it.restoreBackupConflicts(BackupConflictStorage.decode(openBackupConflictsStr))
            it.restoreNwcBackupPrefs(stores.nwcBackupPrefs)
            it.composeSignature.value = stores.composePrefs.signature
        }
    }

/** Applied after construction, which keeps the rebuild lambda clear of the JVM's per-method bytecode limit. */
private fun AccountSettings.restoreNwcBackupPrefs(prefs: NwcBackupPrefs) {
    backupNwcWallets.value = prefs.autoBackup
    backupNwcCardDismissed.value = prefs.cardDismissed
    nwcBackupRelays.value = prefs.relays.sorted()
    backedUpNwcWalletIds.value = prefs.walletIds
}

private data class FollowListPrefs(
    val home: TopFilter,
    val stories: TopFilter,
    val notification: TopFilter,
    val discovery: TopFilter,
    val polls: TopFilter,
    val pictures: TopFilter,
    val relayGroupsDiscovery: TopFilter,
    val napplets: TopFilter,
    val nsites: TopFilter,
    val workouts: TopFilter,
    val gitRepositories: TopFilter,
    val highlights: TopFilter,
    val calendars: TopFilter,
    val products: TopFilter,
    val geocaches: TopFilter,
    val shorts: TopFilter,
    val publicChats: TopFilter,
    val liveStreams: TopFilter,
    val nests: TopFilter,
    val longs: TopFilter,
    val articles: TopFilter,
    val musicTracks: TopFilter,
    val musicPlaylists: TopFilter,
    val podcastEpisodes: TopFilter,
    val podcasts: TopFilter,
    val softwareApps: TopFilter,
    val badges: TopFilter,
    val browseEmojiSets: TopFilter,
    val communities: TopFilter,
    val followPacks: TopFilter,
    val appRecommendations: TopFilter,
    val chess: TopFilter,
)

/**
 * Maps the store's slot table onto the named fields [AccountSettings]
 * still expects. `getValue` is intentional: [TopNavFollowListStore.load]
 * returns every slot, so a missing one is a bug in this mapping rather
 * than a user with no saved filter, and should fail loudly.
 */
private fun toFollowListPrefs(filters: Map<FollowListSlot, TopFilter>): FollowListPrefs =
    FollowListPrefs(
        home = filters.getValue(FollowListSlot.HOME),
        stories = filters.getValue(FollowListSlot.STORIES),
        notification = filters.getValue(FollowListSlot.NOTIFICATION),
        discovery = filters.getValue(FollowListSlot.DISCOVERY),
        polls = filters.getValue(FollowListSlot.POLLS),
        pictures = filters.getValue(FollowListSlot.PICTURES),
        relayGroupsDiscovery = filters.getValue(FollowListSlot.RELAY_GROUPS_DISCOVERY),
        napplets = filters.getValue(FollowListSlot.NAPPLETS),
        nsites = filters.getValue(FollowListSlot.NSITES),
        workouts = filters.getValue(FollowListSlot.WORKOUTS),
        gitRepositories = filters.getValue(FollowListSlot.GIT_REPOSITORIES),
        highlights = filters.getValue(FollowListSlot.HIGHLIGHTS),
        calendars = filters.getValue(FollowListSlot.CALENDARS),
        products = filters.getValue(FollowListSlot.PRODUCTS),
        geocaches = filters.getValue(FollowListSlot.GEOCACHES),
        shorts = filters.getValue(FollowListSlot.SHORTS),
        publicChats = filters.getValue(FollowListSlot.PUBLIC_CHATS),
        liveStreams = filters.getValue(FollowListSlot.LIVE_STREAMS),
        nests = filters.getValue(FollowListSlot.NESTS),
        longs = filters.getValue(FollowListSlot.LONGS),
        articles = filters.getValue(FollowListSlot.ARTICLES),
        musicTracks = filters.getValue(FollowListSlot.MUSIC_TRACKS),
        musicPlaylists = filters.getValue(FollowListSlot.MUSIC_PLAYLISTS),
        podcastEpisodes = filters.getValue(FollowListSlot.PODCAST_EPISODES),
        podcasts = filters.getValue(FollowListSlot.PODCASTS),
        softwareApps = filters.getValue(FollowListSlot.SOFTWARE_APPS),
        badges = filters.getValue(FollowListSlot.BADGES),
        browseEmojiSets = filters.getValue(FollowListSlot.BROWSE_EMOJI_SETS),
        communities = filters.getValue(FollowListSlot.COMMUNITIES),
        followPacks = filters.getValue(FollowListSlot.FOLLOW_PACKS),
        appRecommendations = filters.getValue(FollowListSlot.APP_RECOMMENDATIONS),
        chess = filters.getValue(FollowListSlot.CHESS),
    )

private inline fun <reified T : Any> parseOrNull(value: String?): T? {
    if (value.isNullOrEmpty() || value == "null") {
        return null
    }
    return try {
        if (T::class.java.isInstance(Event::class.java)) {
            Event.fromJson(value) as T?
        } else {
            JsonMapper.fromJson<T>(value)
        }
    } catch (e: Throwable) {
        if (e is CancellationException) throw e
        Log.w("AccountSettingsStorage", "Error Decoding ${T::class.simpleName} from Preferences", e)
        null
    }
}

/**
 * The saved kind-33302 Community List fragments, one event JSON per line. Kept out of the
 * account loader's coroutine body, which already sits at the JVM's method-size limit.
 */
private fun parseConcordListFragments(value: String?): List<ConcordCommunityListFragmentEvent> =
    value
        ?.split('\n')
        ?.mapNotNull { parseEventOrNull<ConcordCommunityListFragmentEvent>(it) }
        .orEmpty()

private inline fun <reified T> parseEventOrNull(value: String?): T? {
    if (value.isNullOrEmpty() || value == "null") {
        return null
    }
    return try {
        Event.fromJson(value) as T?
    } catch (e: Throwable) {
        if (e is CancellationException) throw e
        Log.w("AccountSettingsStorage", "Error Decoding ${T::class.simpleName} from Preferences", e)
        null
    }
}

/**
 * The inbox / relay-auth / feed-type preferences, read as one group. Kept out of
 * [toAccountSettings], whose generated coroutine method is large, so these enum/set decodes
 * do not count against its budget.
 */
private class InboxPrefs(
    val defaultRelayAuthPolicy: RelayAuthPolicy,
    val relayGroupViewMode: RelayGroupViewMode,
    val concordViewMode: ConcordViewMode,
    val enabledChatFeeds: Set<ChatFeedType>,
    val enabledHomeFeedTypes: Set<HomeFeedType>,
    val relayAuthTrustMyRelays: Boolean,
    val relayAuthTrustReadFollows: Boolean,
    val relayAuthTrustMessageFollows: Boolean,
    val relayAuthTrustMessageStrangers: Boolean,
    val customFeeds: List<FeedDefinition>,
    val deck: DeckLayout,
    val accentColor: AccentColorType?,
    val openScreens: String?,
)

private fun readInboxPrefs(
    relayAuth: RelayAuth,
    feedVisibility: FeedVisibility,
) = InboxPrefs(
    // Missing key = an account saved before this setting existed. Those keep CUSTOM; only
    // brand-new logins get the ALWAYS default from AccountSettings' constructor.
    defaultRelayAuthPolicy =
        relayAuth.policyName
            ?.let { runCatching { RelayAuthPolicy.valueOf(it) }.getOrNull() }
            ?: RelayAuthPolicy.CUSTOM,
    relayGroupViewMode = RelayGroupViewMode.fromName(feedVisibility.relayGroupViewMode),
    concordViewMode = ConcordViewMode.fromName(feedVisibility.concordViewMode),
    enabledChatFeeds = ChatFeedType.ALL - ChatFeedType.decode(feedVisibility.disabledChatFeeds),
    enabledHomeFeedTypes = HomeFeedType.ALL - HomeFeedType.decode(feedVisibility.disabledHomeFeedTypes),
    relayAuthTrustMyRelays = relayAuth.trustMyRelays,
    relayAuthTrustReadFollows = relayAuth.trustReadFollows,
    relayAuthTrustMessageFollows = relayAuth.trustMessageFollows,
    relayAuthTrustMessageStrangers = relayAuth.trustMessageStrangers,
    customFeeds = feedVisibility.customFeedsJson?.let { FeedDefinitionSerializer.deserializeList(it) }.orEmpty(),
    deck = feedVisibility.deckLayoutJson?.let { DeckLayout.fromJson(it) } ?: DeckLayout(),
    accentColor = feedVisibility.accentColor?.let { name -> AccentColorType.entries.firstOrNull { it.name == name } },
    openScreens = feedVisibility.openScreensJson,
)
