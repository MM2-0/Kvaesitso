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
@file:OptIn(ExperimentalUuidApi::class)

package com.vitorpamplona.amethyst.commons.model

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.audio.VisualizerStyle
import com.vitorpamplona.amethyst.commons.cashu.CashuKeysetCounterStore
import com.vitorpamplona.amethyst.commons.cashu.UnavailableCashuKeysetCounterStore
import com.vitorpamplona.amethyst.commons.feeds.custom.FeedDefinition
import com.vitorpamplona.amethyst.commons.model.HomeFeedType
import com.vitorpamplona.amethyst.commons.model.backups.BackupConflictGuard
import com.vitorpamplona.amethyst.commons.model.backups.backupSlotOf
import com.vitorpamplona.amethyst.commons.model.cache.filter
import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedType
import com.vitorpamplona.amethyst.commons.model.clink.ClinkDebitWalletEntryNorm
import com.vitorpamplona.amethyst.commons.model.concord.ConcordListRepository
import com.vitorpamplona.amethyst.commons.model.concord.ConcordViewMode
import com.vitorpamplona.amethyst.commons.model.deck.DeckLayout
import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatRepository
import com.vitorpamplona.amethyst.commons.model.mediaServers.DEFAULT_MEDIA_SERVERS
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.model.mergeMutedPublicChats
import com.vitorpamplona.amethyst.commons.model.navigation.BottomBarEntry
import com.vitorpamplona.amethyst.commons.model.navigation.DrawerItemVisibility
import com.vitorpamplona.amethyst.commons.model.navigation.NavBarItem
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatListRepository
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupRepository
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupViewMode
import com.vitorpamplona.amethyst.commons.model.nip47WalletConnect.NwcBackupPayload
import com.vitorpamplona.amethyst.commons.model.nip47WalletConnect.NwcWalletEntryNorm
import com.vitorpamplona.amethyst.commons.model.payments.PaymentSource
import com.vitorpamplona.amethyst.commons.model.payments.PaymentSourceResolver
import com.vitorpamplona.amethyst.commons.model.preferences.FollowListSlot
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPolicy
import com.vitorpamplona.amethyst.commons.service.pow.PoWCategory
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEvent
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListFragmentEvent
import com.vitorpamplona.quartz.experimental.ephemChat.list.EphemeralChatListEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRelayListEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.settings.DmRelayListEvent
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
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip60Cashu.wallet.CashuWalletEvent
import com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip72ModCommunities.follow.CommunityListEvent
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nipA3PaymentTargets.PaymentTargetsEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.offer.Bolt12OfferListEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Stable
class AccountSettings(
    val keyPair: KeyPair,
    val transientAccount: Boolean = false,
    /**
     * Whether this device keeps the private key. False: the account and its settings are saved,
     * the key is not, and it is asked for at the next launch. Set by the login screen's
     * "Remember my key on this device", and by the session store when it loads such an account.
     */
    var rememberKey: Boolean = true,
    var externalSignerPackageName: String? = null,
    /**
     * NIP-46 login: the `bunker://` address (secret stripped) of the remote signer that holds this
     * account's key. Set together with [remoteSignerTransportKey]; desktop logs in this way, Android
     * does not (it is a bunker, never a bunker's client).
     */
    var remoteSignerBunkerUri: String? = null,
    /** NIP-46 login: this client's transport private key (hex) for [remoteSignerBunkerUri]. */
    var remoteSignerTransportKey: String? = null,
    var localRelayServers: MutableStateFlow<Set<String>> = MutableStateFlow(setOf()),
    var defaultFileServer: ServerName = DEFAULT_MEDIA_SERVERS[0],
    var stripLocationOnUpload: Boolean = true,
    val useLocalBlossomCache: MutableStateFlow<Boolean> = MutableStateFlow(true),
    val localBlossomCacheProfilePicturesOnly: MutableStateFlow<Boolean> = MutableStateFlow(false),
    /**
     * BUD-04: after uploading a blob to the primary Blossom server, replicate it to
     * the user's other configured servers (kind 10063) for redundancy.
     */
    val mirrorUploadsToAllServers: MutableStateFlow<Boolean> = MutableStateFlow(true),
    /**
     * BUD-05: upload media through the server's `/media` endpoint so the server may
     * strip metadata and optimize it, instead of the bit-exact `/upload`.
     */
    val optimizeMediaOnUpload: MutableStateFlow<Boolean> = MutableStateFlow(false),
    /**
     * NIP-46: when true, this account acts as a remote signer (a "bunker") for
     * other apps, listening on the user's inbox relays for kind:24133 requests.
     * See [com.vitorpamplona.amethyst.commons.model.nip46Signer.Nip46SignerState].
     */
    val nip46SignerEnabled: MutableStateFlow<Boolean> = MutableStateFlow(false),
    /**
     * The active pairing secret advertised in this account's `bunker://` URI. An
     * app that connects with this secret is accepted and registered as a
     * connected app; regenerating it revokes the ability of not-yet-connected
     * apps to pair with an old string.
     */
    val nip46BunkerSecret: MutableStateFlow<String> = MutableStateFlow(""),
    /**
     * A dedicated per-account transport keypair (hex private key) for the NIP-46
     * bunker. The kind-24133 envelope is wrapped with THIS key, not the account's
     * identity key, so the bunker address and on-relay traffic don't reveal which
     * user the bunker belongs to (the identity is disclosed only to a connected
     * app via `get_public_key`). Generated once and kept stable so the advertised
     * `bunker://` address doesn't change.
     */
    val nip46TransportKey: MutableStateFlow<String> = MutableStateFlow(""),
    /**
     * The kind-24133 **event ids** this signer recently serviced. Persisted so that a relay replaying
     * stored ephemeral requests across an app restart doesn't make it sign the same request twice —
     * matched by exact event id, so it is immune to client clock skew (unlike a timestamp watermark,
     * a global timestamp would wrongly drop a second app whose clock lags). Bounded to a recent window.
     */
    val nip46SeenRequestIds: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet()),
    /**
     * NIP-9B opt-in: when true, community feeds drop events whose latest cached
     * `kind:34551` rules document fails [com.vitorpamplona.quartz.nip72ModCommunities.rules.CommunityRulesValidator].
     * Default false preserves pre-9A behaviour.
     */
    val hideCommunityRulesViolations: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val defaultHomeFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.AllFollows),
    val defaultStoriesFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultNotificationFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Selected),
    val defaultDiscoveryFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultPollsFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultPicturesFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultNappletsFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultNsitesFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultWorkoutsFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultGitRepositoriesFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultHighlightsFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultCalendarsFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultProductsFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.AroundMe),
    val defaultGeocachesFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.AroundMe),
    val defaultShortsFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultPublicChatsFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultLiveStreamsFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultNestsFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultLongsFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultArticlesFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.AllFollows),
    val defaultMusicTracksFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultMusicPlaylistsFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultPodcastEpisodesFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultPodcastsFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultSoftwareAppsFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultBadgesFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Mine),
    val defaultBrowseEmojiSetsFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultCommunitiesFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.AllFollows),
    val defaultFollowPacksFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultAppRecommendationsFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultChessFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Mine),
    val defaultRelayGroupsDiscoveryFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Mine),
    val nwcWallets: MutableStateFlow<List<NwcWalletEntryNorm>> = MutableStateFlow(emptyList()),
    val clinkDebitWallets: MutableStateFlow<List<ClinkDebitWalletEntryNorm>> = MutableStateFlow(emptyList()),
    // The unified default spend rail (an NWC wallet OR a CLINK debit). Persisted under a
    // new key, migrated from the legacy NWC-only `defaultNwcWalletId`.
    val defaultPaymentSourceId: MutableStateFlow<String?> = MutableStateFlow(null),
    // Local-only: synced to a device without the wallets, it could overwrite the good backup.
    val backupNwcWallets: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val backupNwcCardDismissed: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val backedUpNwcWalletIds: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet()),
    val nwcBackupRelays: MutableStateFlow<List<String>> = MutableStateFlow(emptyList()),
    var hideDeleteRequestDialog: Boolean = false,
    var hideBlockAlertDialog: Boolean = false,
    var hideNIP17WarningDialog: Boolean = false,
    val alwaysOnNotificationService: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val splitNotificationsEnabled: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val showMessagesInNotifications: MutableStateFlow<Boolean> = MutableStateFlow(true),
    var backupUserMetadata: MetadataEvent? = null,
    var backupContactList: ContactListEvent? = null,
    var backupDMRelayList: DmRelayListEvent? = null,
    var backupKeyPackageRelayList: KeyPackageRelayListEvent? = null,
    var backupNIP65RelayList: AdvertisedRelayListEvent? = null,
    var backupSearchRelayList: SearchRelayListEvent? = null,
    var backupIndexRelayList: IndexerRelayListEvent? = null,
    var backupFavoriteRelayList: FavoriteRelayListEvent? = null,
    var backupBlockedRelayList: BlockedRelayListEvent? = null,
    var backupTrustedRelayList: TrustedRelayListEvent? = null,
    var backupMuteList: MuteListEvent? = null,
    var backupPrivateHomeRelayList: PrivateOutboxRelayListEvent? = null,
    var backupAppSpecificData: AppSpecificDataEvent? = null,
    var backupPublicChatList: PublicChatListEvent? = null,
    var backupCommunityList: CommunityListEvent? = null,
    var backupInterestList: InterestListEvent? = null,
    var backupFavoriteAlgoFeedsList: FavoriteAlgoFeedsListEvent? = null,
    var backupGeohashList: GeohashListEvent? = null,
    var backupEphemeralChatList: EphemeralChatListEvent? = null,
    var backupRelayGroupList: SimpleGroupListEvent? = null,
    var backupConcordList: ConcordCommunityListEvent? = null,
    /** The newest kind-33302 Community List fragment per index (CORD-02 §8). */
    var backupConcordListFragments: List<ConcordCommunityListFragmentEvent> = emptyList(),
    var backupTrustProviderList: TrustProviderListEvent? = null,
    var backupCashuWallet: CashuWalletEvent? = null,
    var backupNutzapInfo: NutzapInfoEvent? = null,
    /**
     * NUT-13 deterministic-secret counter map, keyed by keyset id. The
     * wallet derives every blind message from `(seed, keysetId, counter)`,
     * incrementing the counter every time it consumes one; reusing a
     * counter would expose the secret. Persisted here so the counter
     * survives app restart even though the wallet's seed is also stored
     * in kind:17375 (which would otherwise be the only persistence).
     *
     * Empty map = no NUT-13 usage yet (e.g. wallet created before this
     * feature shipped). Per-keyset; the same counter under different
     * keysets is fine because the derivation includes the keyset id.
     */
    var cashuKeysetCounters: MutableMap<String, Long> = mutableMapOf(),
    /**
     * Durable NUT-13 counter store for this account, supplied by the host.
     *
     * Defaulted to the failing stand-in rather than an in-memory map: the preview
     * and test factories below never mint, and a host that does mint must wire a
     * real store or hear about it on the first reservation.
     */
    val cashuCounters: CashuKeysetCounterStore = UnavailableCashuKeysetCounterStore,
    val lastReadPerRoute: MutableStateFlow<Map<String, MutableStateFlow<Long>>> = MutableStateFlow(mapOf()),
    val hasDonatedInVersion: MutableStateFlow<Set<String>> = MutableStateFlow(setOf()),
    /** Until when (unix seconds) the web of trust card stays hidden from Home; 0 when it was never hidden. */
    val webOfTrustNudgeHiddenUntil: MutableStateFlow<Long> = MutableStateFlow(0L),
    val dismissedPollNoteIds: MutableStateFlow<Set<String>> = MutableStateFlow(setOf()),
    /**
     * Channel ids the viewer chose NOT to show on Messages after somebody added them to the channel
     * (kind-44100). Local-only: it records a display preference, not membership — the relay roster
     * still lists you, and Leave (kind 9022) is the separate action that actually removes you.
     */
    val dismissedChannelInvites: MutableStateFlow<Set<String>> = MutableStateFlow(setOf()),
    /**
     * NIP-28 channel ids the user has silenced. Local device state ON PURPOSE, even
     * though it also syncs via NIP-78: the push dispatcher must answer "is this muted?"
     * during a cold start, before (or without) the settings blob having been decrypted —
     * for a NIP-55 account that decrypt is an Amber IPC round-trip that may never
     * complete in the background. See AppSpecificState.kt:70-75.
     */
    val mutedPublicChats: MutableStateFlow<Set<String>> = MutableStateFlow(setOf()),
    val viewedPollResultNoteIds: MutableStateFlow<Map<String, Long>> = MutableStateFlow(mapOf()),
    /** The feeds this user built, offered in the Home feed picker as [TopFilter.CustomFeed]. */
    val customFeeds: MutableStateFlow<List<FeedDefinition>> = MutableStateFlow(emptyList()),
    /** The deck's workspaces and columns, shown beside the main screen on wide windows when the deck is on. */
    val deck: MutableStateFlow<DeckLayout> = MutableStateFlow(DeckLayout()),
    /** The open tabs or windows of the tabs and windows layouts (`NavBackStacks.encode()`), restored on the next start. */
    val openScreens: MutableStateFlow<String?> = MutableStateFlow(null),
    val pendingAttestations: MutableStateFlow<Map<HexKey, String>> = MutableStateFlow(mapOf()),
    var backupNipA3PaymentTargets: PaymentTargetsEvent? = null,
    var backupBolt12Offers: Bolt12OfferListEvent? = null,
    var callTurnServers: List<CallTurnServer> = emptyList(),
    var callVideoResolution: CallVideoResolution = CallVideoResolution.HD_720,
    var callMaxBitrateBps: Int = 1_500_000,
    val callsEnabled: MutableStateFlow<Boolean> = MutableStateFlow(true),
    /**
     * The text appended to this account's new posts. Null until the account sets one, which leaves
     * the app-wide [UiSettings.composeSignature] in charge: that is where the signature lived before
     * it became per account, so an upgrade keeps it.
     */
    val composeSignature: MutableStateFlow<String?> = MutableStateFlow(null),
    // New accounts authenticate with every relay that asks. Existing accounts keep whatever they
    // had saved (LocalPreferences falls back to CUSTOM for prefs written before this key existed).
    val defaultRelayAuthPolicy: MutableStateFlow<RelayAuthPolicy> = MutableStateFlow(RelayAuthPolicy.ALWAYS),
    val relayGroupViewMode: MutableStateFlow<RelayGroupViewMode> = MutableStateFlow(RelayGroupViewMode.DEFAULT),
    val concordViewMode: MutableStateFlow<ConcordViewMode> = MutableStateFlow(ConcordViewMode.DEFAULT),
    /** This account's accent color; null follows the app-wide one in [UiSettings.accentColor]. */
    val accentColor: MutableStateFlow<AccentColorType?> = MutableStateFlow(null),
    // Which conversation protocols the Messages inbox loads and shows. A disabled type is both hidden
    // from the inbox and dropped from the always-on downloading routes. Defaults to everything on.
    val enabledChatFeeds: MutableStateFlow<Set<ChatFeedType>> = MutableStateFlow(ChatFeedType.ALL),
    // Which event-kind groups the Home feed downloads (assembler) and renders (DAL). A disabled group
    // is both dropped from the always-on home relay filters and hidden from the tabs. Everything on by default.
    val enabledHomeFeedTypes: MutableStateFlow<Set<HomeFeedType>> = MutableStateFlow(HomeFeedType.ALL),
    // The per-situation toggles applied under RelayAuthPolicy.CUSTOM.
    val relayAuthTrustMyRelaysAndVenues: MutableStateFlow<Boolean> = MutableStateFlow(true),
    val relayAuthTrustReadFollows: MutableStateFlow<Boolean> = MutableStateFlow(true),
    val relayAuthTrustMessageFollows: MutableStateFlow<Boolean> = MutableStateFlow(true),
    val relayAuthTrustMessageStrangers: MutableStateFlow<Boolean> = MutableStateFlow(false),
) : EphemeralChatRepository,
    RelayGroupRepository,
    ConcordListRepository,
    PublicChatListRepository {
    val saveable = MutableStateFlow(AccountSettingsUpdater(null))
    val syncedSettings: AccountSyncedSettings = AccountSyncedSettings(AccountSyncedSettingsInternal())

    class AccountSettingsUpdater(
        val accountSettings: AccountSettings?,
    )

    fun saveAccountSettings() {
        saveable.update { AccountSettingsUpdater(this) }
    }

    /**
     * Fires when a setting that syncs through NIP-78 but lives here (see [SyncedLocalSettings])
     * is changed on this device. [Account] publishes the blob a second after the last one. Values
     * that arrive from another device are applied without it, so they are not echoed back.
     */
    val syncedChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * Like [syncedChanges], for the feed filter each screen's top bar shows. It only remembers
     * where the user was on that screen, and switching it is part of browsing, so [Account] waits
     * 30 s before publishing it: a session of switches costs one signature, not one per switch.
     */
    val slowSyncedChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private fun saveAndSync() {
        saveAccountSettings()
        syncedChanges.tryEmit(Unit)
    }

    private fun saveAndSyncLater() {
        saveAccountSettings()
        slowSyncedChanges.tryEmit(Unit)
    }

    fun isWriteable(): Boolean = keyPair.privKey != null || externalSignerPackageName != null || usesRemoteSigner()

    /** Whether this account signs through a NIP-46 remote signer it logged in with. */
    fun usesRemoteSigner(): Boolean = remoteSignerBunkerUri != null && remoteSignerTransportKey != null

    fun updateRelayGroupViewMode(mode: RelayGroupViewMode) {
        if (relayGroupViewMode.value != mode) {
            relayGroupViewMode.tryEmit(mode)
            saveAndSync()
        }
    }

    fun updateConcordViewMode(mode: ConcordViewMode) {
        if (concordViewMode.value != mode) {
            concordViewMode.tryEmit(mode)
            saveAndSync()
        }
    }

    fun updateAccentColor(color: AccentColorType?) {
        if (accentColor.value != color) {
            accentColor.tryEmit(color)
            saveAndSync()
        }
    }

    fun setChatFeedEnabled(
        type: ChatFeedType,
        enabled: Boolean,
    ) {
        val current = enabledChatFeeds.value
        val next = if (enabled) current + type else current - type
        if (next != current) {
            enabledChatFeeds.tryEmit(next)
            saveAndSync()
        }
    }

    fun isHomeFeedTypeEnabled(type: HomeFeedType): Boolean = type in enabledHomeFeedTypes.value

    fun setHomeFeedTypeEnabled(
        type: HomeFeedType,
        enabled: Boolean,
    ) {
        val current = enabledHomeFeedTypes.value
        val next = if (enabled) current + type else current - type
        if (next != current) {
            enabledHomeFeedTypes.tryEmit(next)
            saveAndSync()
        }
    }

    // ---
    // Always-on Notification Service
    // ---

    fun toggleAlwaysOnNotificationService(): Boolean {
        val newValue = !alwaysOnNotificationService.value
        alwaysOnNotificationService.tryEmit(newValue)
        saveAccountSettings()
        return newValue
    }

    fun toggleSplitNotificationsEnabled(): Boolean {
        val newValue = !splitNotificationsEnabled.value
        splitNotificationsEnabled.tryEmit(newValue)
        saveAccountSettings()
        return newValue
    }

    fun toggleShowMessagesInNotifications(): Boolean {
        val newValue = !showMessagesInNotifications.value
        showMessagesInNotifications.tryEmit(newValue)
        saveAccountSettings()
        return newValue
    }

    // ---
    // Zaps and Reactions
    // ---

    fun changeDefaultZapType(zapType: ZapReceiptEvent.ZapType): Boolean {
        if (syncedSettings.zaps.defaultZapType.value != zapType) {
            syncedSettings.zaps.defaultZapType.tryEmit(zapType)
            saveAccountSettings()
            return true
        }
        return false
    }

    fun changeZapAmounts(newAmounts: List<Long>): Boolean {
        if (syncedSettings.zaps.zapAmountChoices.value != newAmounts) {
            syncedSettings.zaps.zapAmountChoices.tryEmit(newAmounts.toImmutableList())
            saveAccountSettings()
            return true
        }
        return false
    }

    fun changeReactionTypes(newTypes: List<String>): Boolean {
        if (syncedSettings.reactions.reactionChoices.value != newTypes) {
            syncedSettings.reactions.reactionChoices.tryEmit(newTypes.toImmutableList())
            saveAccountSettings()
            return true
        }
        return false
    }

    fun changeReactionRowItems(newItems: List<ReactionRowItem>): Boolean {
        if (syncedSettings.reactions.reactionRowItems.value != newItems) {
            syncedSettings.reactions.reactionRowItems.tryEmit(newItems.toImmutableList())
            saveAccountSettings()
            return true
        }
        return false
    }

    fun changeVideoPlayerButtonItems(newItems: List<VideoPlayerButtonItem>): Boolean {
        if (syncedSettings.videoPlayer.buttonItems.value != newItems) {
            syncedSettings.videoPlayer.buttonItems.tryEmit(newItems.toImmutableList())
            saveAccountSettings()
            return true
        }
        return false
    }

    fun changeCaptionsEnabled(enabled: Boolean): Boolean {
        if (syncedSettings.videoPlayer.captionsEnabled.value != enabled) {
            syncedSettings.videoPlayer.captionsEnabled.tryEmit(enabled)
            saveAccountSettings()
            return true
        }
        return false
    }

    fun changeAudioVisualizer(style: VisualizerStyle): Boolean {
        if (syncedSettings.media.audioVisualizer.value != style) {
            syncedSettings.media.audioVisualizer.tryEmit(style)
            saveAccountSettings()
            return true
        }
        return false
    }

    fun changeBottomBarItems(newItems: List<BottomBarEntry>): Boolean {
        if (syncedSettings.navigation.bottomBarItems.value != newItems) {
            syncedSettings.navigation.bottomBarItems.tryEmit(newItems)
            saveAccountSettings()
            return true
        }
        return false
    }

    fun changeHiddenDrawerItems(newItems: Set<NavBarItem>): Boolean {
        // Sanitize on the way in as well as on the way out: a caller must never be able to persist
        // Settings as hidden, which would leave no route back to the screen that hides rows.
        val sanitized = DrawerItemVisibility.sanitize(newItems)
        if (syncedSettings.navigation.hiddenDrawerItems.value != sanitized) {
            syncedSettings.navigation.hiddenDrawerItems.tryEmit(sanitized)
            saveAccountSettings()
            return true
        }
        return false
    }

    /** The selected default spend rail across both NWC wallets and CLINK debits. */
    fun defaultPaymentSource(): PaymentSource? = PaymentSourceResolver.resolveDefault(nwcWallets.value, clinkDebitWallets.value, defaultPaymentSourceId.value)

    /**
     * The NWC wallet to use for NWC-only flows (balance display, mint top-up). Resolves
     * the unified default when it points at an NWC wallet, otherwise falls back to the
     * first NWC wallet so those flows keep working even when a debit is the zap default.
     */
    fun defaultNwcWallet(): NwcWalletEntryNorm? {
        val wallets = nwcWallets.value
        return wallets.firstOrNull { it.id == defaultPaymentSourceId.value } ?: wallets.firstOrNull()
    }

    fun defaultZapPaymentRequest(): Nip47WalletConnect.Nip47URINorm? = defaultNwcWallet()?.uri

    fun addNwcWallet(wallet: NwcWalletEntryNorm): Boolean {
        val existing = nwcWallets.value.indexOfFirst { it.id == wallet.id }
        if (existing >= 0) {
            nwcWallets.tryEmit(nwcWallets.value.toMutableList().apply { set(existing, wallet) })
        } else {
            if (nwcWallets.value.any { it.uri == wallet.uri }) return false
            nwcWallets.tryEmit(nwcWallets.value + wallet)
            // First configured source of any kind becomes the default; adding more never
            // silently changes an existing default.
            if (defaultPaymentSourceId.value == null) {
                defaultPaymentSourceId.tryEmit(wallet.id)
            }
        }
        saveAccountSettings()
        return true
    }

    fun removeNwcWallet(walletId: String): Boolean {
        val wallets = nwcWallets.value.filter { it.id != walletId }
        nwcWallets.tryEmit(wallets)
        if (backedUpNwcWalletIds.value.contains(walletId)) {
            backedUpNwcWalletIds.tryEmit(backedUpNwcWalletIds.value - walletId)
        }
        // With no NWC wallet left, a dismissed backup card would leave restore unreachable.
        if (wallets.isEmpty() && backupNwcCardDismissed.value) {
            backupNwcCardDismissed.tryEmit(false)
        }
        reassignDefaultIfRemoved(walletId)
        saveAccountSettings()
        return true
    }

    fun changeBackupNwcWallets(enabled: Boolean) {
        if (backupNwcWallets.value != enabled) {
            backupNwcWallets.tryEmit(enabled)
            saveAccountSettings()
        }
    }

    fun updateNwcBackupRelays(relays: List<String>) {
        if (nwcBackupRelays.value != relays) {
            nwcBackupRelays.tryEmit(relays)
            saveAccountSettings()
        }
    }

    fun updateBackedUpNwcWalletIds(walletIds: Set<String>) {
        if (backedUpNwcWalletIds.value != walletIds) {
            backedUpNwcWalletIds.tryEmit(walletIds)
            saveAccountSettings()
        }
    }

    fun dismissBackupNwcCard() {
        if (!backupNwcCardDismissed.value) {
            backupNwcCardDismissed.tryEmit(true)
            saveAccountSettings()
        }
    }

    /** Dedupes by connection URI; returns how many wallets were new. */
    fun mergeRestoredNwcWallets(payload: NwcBackupPayload): Int {
        val existing = nwcWallets.value
        val existingUris = existing.mapTo(HashSet()) { it.uri }
        val restored = payload.wallets.mapNotNull { it.normalize() }
        val restoredUris = restored.mapTo(HashSet()) { it.uri }
        val newWallets = restored.filter { it.uri !in existingUris }
        val inBackup = (existing.filter { it.uri in restoredUris } + newWallets).mapTo(HashSet()) { it.id }
        if (newWallets.isNotEmpty()) {
            nwcWallets.tryEmit(existing + newWallets)
            if (defaultPaymentSourceId.value == null) {
                defaultPaymentSourceId.tryEmit(payload.defaultId ?: newWallets.first().id)
            }
        }
        backedUpNwcWalletIds.tryEmit(backedUpNwcWalletIds.value + inBackup)
        saveAccountSettings()
        return newWallets.size
    }

    fun addClinkDebitWallet(wallet: ClinkDebitWalletEntryNorm): Boolean {
        val existing = clinkDebitWallets.value.indexOfFirst { it.id == wallet.id }
        if (existing >= 0) {
            clinkDebitWallets.tryEmit(clinkDebitWallets.value.toMutableList().apply { set(existing, wallet) })
        } else {
            clinkDebitWallets.tryEmit(clinkDebitWallets.value + wallet)
            if (defaultPaymentSourceId.value == null) {
                defaultPaymentSourceId.tryEmit(wallet.id)
            }
        }
        saveAccountSettings()
        return true
    }

    fun removeClinkDebitWallet(walletId: String): Boolean {
        clinkDebitWallets.tryEmit(clinkDebitWallets.value.filter { it.id != walletId })
        reassignDefaultIfRemoved(walletId)
        saveAccountSettings()
        return true
    }

    fun renameClinkDebitWallet(
        walletId: String,
        newName: String,
    ): Boolean {
        val wallets = clinkDebitWallets.value.toMutableList()
        val index = wallets.indexOfFirst { it.id == walletId }
        if (index >= 0) {
            wallets[index] = wallets[index].copy(name = newName)
            clinkDebitWallets.tryEmit(wallets)
            saveAccountSettings()
            return true
        }
        return false
    }

    /** When the removed source was the default, fall back to the first remaining source. */
    private fun reassignDefaultIfRemoved(walletId: String) {
        if (defaultPaymentSourceId.value == walletId) {
            defaultPaymentSourceId.tryEmit(PaymentSourceResolver.resolveDefault(nwcWallets.value, clinkDebitWallets.value, null)?.id)
        }
    }

    /** Resets the default to the first remaining source if it no longer points at anything. */
    private fun reassignDefaultIfMissing() {
        val id = defaultPaymentSourceId.value ?: return
        val exists = nwcWallets.value.any { it.id == id } || clinkDebitWallets.value.any { it.id == id }
        if (!exists) {
            defaultPaymentSourceId.tryEmit(PaymentSourceResolver.resolveDefault(nwcWallets.value, clinkDebitWallets.value, null)?.id)
        }
    }

    /** Selects the unified default across both NWC wallets and CLINK debits. */
    fun setDefaultPaymentSource(sourceId: String): Boolean {
        val exists = nwcWallets.value.any { it.id == sourceId } || clinkDebitWallets.value.any { it.id == sourceId }
        if (defaultPaymentSourceId.value != sourceId && exists) {
            defaultPaymentSourceId.tryEmit(sourceId)
            saveAccountSettings()
            return true
        }
        return false
    }

    fun renameNwcWallet(
        walletId: String,
        newName: String,
    ): Boolean {
        val wallets = nwcWallets.value.toMutableList()
        val index = wallets.indexOfFirst { it.id == walletId }
        if (index >= 0) {
            wallets[index] = wallets[index].copy(name = newName)
            nwcWallets.tryEmit(wallets)
            saveAccountSettings()
            return true
        }
        return false
    }

    fun changeZapPaymentRequest(newServer: Nip47WalletConnect.Nip47URINorm?): Boolean {
        if (newServer == null) {
            if (nwcWallets.value.isNotEmpty()) {
                nwcWallets.tryEmit(emptyList())
                reassignDefaultIfMissing()
                saveAccountSettings()
                return true
            }
            return false
        }
        val current = defaultZapPaymentRequest()
        if (current != newServer) {
            val defaultWallet = defaultNwcWallet()
            if (defaultWallet != null) {
                val updated = defaultWallet.copy(uri = newServer)
                addNwcWallet(updated)
            } else {
                val entry =
                    NwcWalletEntryNorm(
                        id =
                            Uuid.random().toString(),
                        name = "Lightning Wallet",
                        uri = newServer,
                    )
                addNwcWallet(entry)
            }
            return true
        }
        return false
    }

    // ---
    // file servers
    // ---

    fun changeDefaultFileServer(server: ServerName) {
        if (defaultFileServer != server) {
            defaultFileServer = server
            saveAndSync()
        }
    }

    fun changeStripLocationOnUpload(strip: Boolean) {
        if (stripLocationOnUpload != strip) {
            stripLocationOnUpload = strip
            saveAndSync()
        }
    }

    fun changeUseLocalBlossomCache(enabled: Boolean) {
        if (useLocalBlossomCache.value != enabled) {
            useLocalBlossomCache.tryEmit(enabled)
            saveAccountSettings()
        }
    }

    fun changeHideCommunityRulesViolations(enabled: Boolean) {
        if (hideCommunityRulesViolations.value != enabled) {
            hideCommunityRulesViolations.tryEmit(enabled)
            saveAndSync()
        }
    }

    fun changeNip46SignerEnabled(enabled: Boolean) {
        if (nip46SignerEnabled.value != enabled) {
            nip46SignerEnabled.tryEmit(enabled)
            saveAccountSettings()
        }
    }

    fun changeNip46BunkerSecret(secret: String) {
        if (nip46BunkerSecret.value != secret) {
            nip46BunkerSecret.tryEmit(secret)
            saveAccountSettings()
        }
    }

    fun changeNip46TransportKey(hexPrivKey: String) {
        if (nip46TransportKey.value != hexPrivKey) {
            nip46TransportKey.tryEmit(hexPrivKey)
            saveAccountSettings()
        }
    }

    /** Replaces the recent serviced-request id set (already bounded by the caller). */
    fun changeNip46SeenRequestIds(ids: Set<String>) {
        if (nip46SeenRequestIds.value != ids) {
            nip46SeenRequestIds.tryEmit(ids)
            saveAccountSettings()
        }
    }

    fun changeLocalBlossomCacheProfilePicturesOnly(enabled: Boolean) {
        if (localBlossomCacheProfilePicturesOnly.value != enabled) {
            localBlossomCacheProfilePicturesOnly.tryEmit(enabled)
            saveAccountSettings()
        }
    }

    fun changeMirrorUploadsToAllServers(enabled: Boolean) {
        if (mirrorUploadsToAllServers.value != enabled) {
            mirrorUploadsToAllServers.tryEmit(enabled)
            saveAndSync()
        }
    }

    fun changeOptimizeMediaOnUpload(enabled: Boolean) {
        if (optimizeMediaOnUpload.value != enabled) {
            optimizeMediaOnUpload.tryEmit(enabled)
            saveAndSync()
        }
    }

    fun updateAddClientTag(add: Boolean): Boolean =
        if (syncedSettings.security.updateAddClientTag(add)) {
            saveAccountSettings()
            true
        } else {
            false
        }

    fun updatePowDifficulty(difficulty: Int): Boolean =
        if (syncedSettings.proofOfWork.updateDifficulty(difficulty)) {
            saveAccountSettings()
            true
        } else {
            false
        }

    fun updatePowCategory(
        category: PoWCategory,
        enabled: Boolean,
    ): Boolean =
        if (syncedSettings.proofOfWork.updateCategory(category, enabled)) {
            saveAccountSettings()
            true
        } else {
            false
        }

    // ---
    // list names
    // ---

    /**
     * All per-screen persisted feed filters paired with their factory default.
     * Deleting a list (NIP-51 people list / follow pack) must reset any screen whose
     * filter still points at the deleted address — otherwise the screen keeps a
     * dangling [TopFilter.PeopleList] that re-creates an empty AddressableNote shell
     * on every start and shows the list's dTag/UUID in the top bar instead of a name.
     */
    private val feedFiltersWithDefaults: List<Pair<MutableStateFlow<TopFilter>, TopFilter>> =
        listOf(
            defaultHomeFollowList to TopFilter.AllFollows,
            defaultStoriesFollowList to TopFilter.Global,
            defaultNotificationFollowList to TopFilter.Selected,
            defaultDiscoveryFollowList to TopFilter.Global,
            defaultPollsFollowList to TopFilter.Global,
            defaultPicturesFollowList to TopFilter.Global,
            defaultNappletsFollowList to TopFilter.Global,
            defaultNsitesFollowList to TopFilter.Global,
            defaultWorkoutsFollowList to TopFilter.Global,
            defaultGitRepositoriesFollowList to TopFilter.Global,
            defaultHighlightsFollowList to TopFilter.Global,
            defaultCalendarsFollowList to TopFilter.Global,
            defaultProductsFollowList to TopFilter.AroundMe,
            defaultGeocachesFollowList to TopFilter.AroundMe,
            defaultShortsFollowList to TopFilter.Global,
            defaultPublicChatsFollowList to TopFilter.Global,
            defaultLiveStreamsFollowList to TopFilter.Global,
            defaultNestsFollowList to TopFilter.Global,
            defaultLongsFollowList to TopFilter.Global,
            defaultArticlesFollowList to TopFilter.AllFollows,
            defaultMusicTracksFollowList to TopFilter.Global,
            defaultMusicPlaylistsFollowList to TopFilter.Global,
            defaultPodcastEpisodesFollowList to TopFilter.Global,
            defaultPodcastsFollowList to TopFilter.Global,
            defaultSoftwareAppsFollowList to TopFilter.Global,
            defaultBadgesFollowList to TopFilter.Mine,
            defaultBrowseEmojiSetsFollowList to TopFilter.Global,
            defaultCommunitiesFollowList to TopFilter.AllFollows,
            defaultFollowPacksFollowList to TopFilter.Global,
            defaultAppRecommendationsFollowList to TopFilter.Global,
            defaultChessFollowList to TopFilter.Mine,
            defaultRelayGroupsDiscoveryFollowList to TopFilter.Mine,
        )

    /** Resets every persisted feed filter that points at the deleted list's address. */
    fun resetFeedFiltersPointingTo(address: Address) {
        var changed = false

        feedFiltersWithDefaults.forEach { (flow, default) ->
            val current = flow.value
            if (current is TopFilter.AddressableTopFilter && current.address == address) {
                flow.tryEmit(default)
                changed = true
            }
        }

        if (changed) saveAndSyncLater()
    }

    /**
     * Points every feed set to Global at [filter] instead. Notifications keep their choice: there
     * Global means every event that mentions the user, not a feed of the network.
     */
    fun moveGlobalFeedsTo(filter: TopFilter) {
        var changed = false

        feedFiltersWithDefaults.forEach { (flow, _) ->
            if (flow !== defaultNotificationFollowList && flow.value == TopFilter.Global) {
                flow.tryEmit(filter)
                changed = true
            }
        }

        if (changed) saveAndSyncLater()
    }

    // ---
    // NIP-78 sync of the settings that live here (see SyncedLocalSettings)
    // ---

    private fun feedFilterFlow(slot: FollowListSlot): MutableStateFlow<TopFilter> =
        when (slot) {
            FollowListSlot.HOME -> defaultHomeFollowList
            FollowListSlot.STORIES -> defaultStoriesFollowList
            FollowListSlot.NOTIFICATION -> defaultNotificationFollowList
            FollowListSlot.DISCOVERY -> defaultDiscoveryFollowList
            FollowListSlot.POLLS -> defaultPollsFollowList
            FollowListSlot.PICTURES -> defaultPicturesFollowList
            FollowListSlot.RELAY_GROUPS_DISCOVERY -> defaultRelayGroupsDiscoveryFollowList
            FollowListSlot.NAPPLETS -> defaultNappletsFollowList
            FollowListSlot.NSITES -> defaultNsitesFollowList
            FollowListSlot.WORKOUTS -> defaultWorkoutsFollowList
            FollowListSlot.GIT_REPOSITORIES -> defaultGitRepositoriesFollowList
            FollowListSlot.HIGHLIGHTS -> defaultHighlightsFollowList
            FollowListSlot.CALENDARS -> defaultCalendarsFollowList
            FollowListSlot.PRODUCTS -> defaultProductsFollowList
            FollowListSlot.GEOCACHES -> defaultGeocachesFollowList
            FollowListSlot.SHORTS -> defaultShortsFollowList
            FollowListSlot.PUBLIC_CHATS -> defaultPublicChatsFollowList
            FollowListSlot.LIVE_STREAMS -> defaultLiveStreamsFollowList
            FollowListSlot.NESTS -> defaultNestsFollowList
            FollowListSlot.LONGS -> defaultLongsFollowList
            FollowListSlot.ARTICLES -> defaultArticlesFollowList
            FollowListSlot.MUSIC_TRACKS -> defaultMusicTracksFollowList
            FollowListSlot.MUSIC_PLAYLISTS -> defaultMusicPlaylistsFollowList
            FollowListSlot.PODCAST_EPISODES -> defaultPodcastEpisodesFollowList
            FollowListSlot.PODCASTS -> defaultPodcastsFollowList
            FollowListSlot.SOFTWARE_APPS -> defaultSoftwareAppsFollowList
            FollowListSlot.BADGES -> defaultBadgesFollowList
            FollowListSlot.BROWSE_EMOJI_SETS -> defaultBrowseEmojiSetsFollowList
            FollowListSlot.COMMUNITIES -> defaultCommunitiesFollowList
            FollowListSlot.FOLLOW_PACKS -> defaultFollowPacksFollowList
            FollowListSlot.APP_RECOMMENDATIONS -> defaultAppRecommendationsFollowList
            FollowListSlot.CHESS -> defaultChessFollowList
        }

    fun syncedLocalSettings() =
        SyncedLocalSettings(
            accentColor = accentColor.value,
            relayGroupViewMode = relayGroupViewMode.value,
            concordViewMode = concordViewMode.value,
            enabledChatFeeds = enabledChatFeeds.value,
            enabledHomeFeedTypes = enabledHomeFeedTypes.value,
            feedFilters = FollowListSlot.entries.associateWith { feedFilterFlow(it).value },
            customFeeds = customFeeds.value,
            hideCommunityRulesViolations = hideCommunityRulesViolations.value,
            relayAuthPolicy = defaultRelayAuthPolicy.value,
            relayAuthTrustMyRelaysAndVenues = relayAuthTrustMyRelaysAndVenues.value,
            relayAuthTrustReadFollows = relayAuthTrustReadFollows.value,
            relayAuthTrustMessageFollows = relayAuthTrustMessageFollows.value,
            relayAuthTrustMessageStrangers = relayAuthTrustMessageStrangers.value,
            defaultFileServer = defaultFileServer,
            stripLocationOnUpload = stripLocationOnUpload,
            mirrorUploadsToAllServers = mirrorUploadsToAllServers.value,
            optimizeMediaOnUpload = optimizeMediaOnUpload.value,
            callsEnabled = callsEnabled.value,
            composeSignature = composeSignature.value,
        )

    /** The whole NIP-78 blob: [syncedSettings] plus the groups that live here. */
    fun toSyncedSettingsInternal(): AccountSyncedSettingsInternal = syncedSettings.toInternal(mutedPublicChats.value).withLocalGroups(syncedLocalSettings())

    /**
     * Applies the groups [remote] carries. A group it lacks was written by a version that did not
     * know it, so this device keeps its own values for it.
     */
    private fun applyLocalGroups(remote: AccountSyncedSettingsInternal) {
        remote.appearance?.let { group -> accentColor.update { decodeAccentColor(group.accentColor, it) } }

        remote.messages?.let { group ->
            relayGroupViewMode.update { enumByName(group.relayGroupViewMode, it) }
            concordViewMode.update { enumByName(group.concordViewMode, it) }
            enabledChatFeeds.value = ChatFeedType.ALL - ChatFeedType.decode(group.disabledChatFeeds)
        }

        remote.feeds?.let { group ->
            enabledHomeFeedTypes.value = HomeFeedType.ALL - HomeFeedType.decode(group.disabledHomeFeedTypes)
            hideCommunityRulesViolations.value = group.hideCommunityRulesViolations
            decodeCustomFeeds(group.customFeeds)?.let { applyCustomFeeds(it) }
            // By JSON: a decoded filter that carries an id or address never equals the current one,
            // and assigning it would reload that screen's feed although nothing changed.
            readableFeedFilters(group.feedFilters).forEach { (slot, json) ->
                val flow = feedFilterFlow(slot)
                if (JsonMapper.toJson(flow.value) != json) decodeFeedFilter(json)?.let { flow.value = it }
            }
        }

        remote.relayAuth?.let { group ->
            defaultRelayAuthPolicy.update { enumByName(group.policy, it) }
            relayAuthTrustMyRelaysAndVenues.value = group.trustMyRelaysAndVenues
            relayAuthTrustReadFollows.value = group.trustReadFollows
            relayAuthTrustMessageFollows.value = group.trustMessageFollows
            relayAuthTrustMessageStrangers.value = group.trustMessageStrangers
        }

        remote.uploads?.let { group ->
            decodeFileServer(group.defaultFileServer)?.let { defaultFileServer = it }
            stripLocationOnUpload = group.stripLocation
            mirrorUploadsToAllServers.value = group.mirrorToAllServers
            optimizeMediaOnUpload.value = group.optimizeMedia
        }

        remote.calls?.let { group -> callsEnabled.value = group.callsEnabled }

        remote.compose?.let { group -> composeSignature.value = group.signature }
    }

    /**
     * Replaces the custom feeds with [feeds] from another device, as [saveCustomFeed] and
     * [deleteCustomFeed] would: a screen showing a feed that changed gets a fresh
     * [TopFilter.CustomFeed], and one showing a feed that is gone goes back to its default.
     */
    private fun applyCustomFeeds(feeds: List<FeedDefinition>) {
        val before = customFeeds.value
        if (before == feeds) return
        customFeeds.value = feeds
        val byId = feeds.associateBy { it.id }
        feedFiltersWithDefaults.forEach { (flow, default) ->
            val current = flow.value as? TopFilter.CustomFeed ?: return@forEach
            val now = byId[current.id]
            when {
                now == null -> flow.value = default
                before.firstOrNull { it.id == current.id } != now -> flow.value = TopFilter.CustomFeed(current.id)
            }
        }
    }

    /** Keeps the open tabs or windows for the next start, saving when they changed. */
    fun updateOpenScreens(encoded: String?) {
        if (openScreens.value == encoded) return
        openScreens.value = encoded
        saveAccountSettings()
    }

    /** Applies [transform] to the deck layout and saves it when it changed. */
    fun updateDeck(transform: (DeckLayout) -> DeckLayout) {
        val before = deck.value
        deck.update(transform)
        if (deck.value != before) saveAccountSettings()
    }

    /**
     * Adds [feed], or replaces the one with its id. A screen showing the feed gets a fresh
     * [TopFilter.CustomFeed]: the feeds key their contents and relay EOSE times by that instance, so
     * keeping the old one would leave the edited feed showing its old notes and asking relays only
     * for what is newer than the old definition's last load.
     */
    fun saveCustomFeed(feed: FeedDefinition) {
        customFeeds.update { feeds ->
            if (feeds.any { it.id == feed.id }) feeds.map { if (it.id == feed.id) feed else it } else feeds + feed
        }
        feedFiltersWithDefaults.forEach { (flow, _) ->
            val current = flow.value
            if (current is TopFilter.CustomFeed && current.id == feed.id) flow.tryEmit(TopFilter.CustomFeed(feed.id))
        }
        saveAndSync()
    }

    /** Deletes the custom feed [id] and sends any screen that showed it back to its default feed. */
    fun deleteCustomFeed(id: String) {
        customFeeds.update { feeds -> feeds.filterNot { it.id == id } }
        feedFiltersWithDefaults.forEach { (flow, default) ->
            val current = flow.value
            if (current is TopFilter.CustomFeed && current.id == id) flow.tryEmit(default)
        }
        saveAndSync()
    }

    fun changeDefaultHomeFollowList(name: TopFilter) {
        if (defaultHomeFollowList.value != name) {
            defaultHomeFollowList.tryEmit(name)
            saveAndSyncLater()
        }
    }

    fun changeDefaultStoriesFollowList(name: TopFilter) {
        if (defaultStoriesFollowList.value != name) {
            defaultStoriesFollowList.tryEmit(name)
            saveAndSyncLater()
        }
    }

    fun changeDefaultNotificationFollowList(name: TopFilter) {
        if (defaultNotificationFollowList.value != name) {
            defaultNotificationFollowList.tryEmit(name)
            saveAndSyncLater()
        }
    }

    fun changeDefaultDiscoveryFollowList(name: TopFilter) {
        if (defaultDiscoveryFollowList.value != name) {
            defaultDiscoveryFollowList.tryEmit(name)
            saveAndSyncLater()
        }
    }

    fun changeDefaultPollsFollowList(name: TopFilter) {
        if (defaultPollsFollowList.value != name) {
            defaultPollsFollowList.tryEmit(name)
            saveAndSyncLater()
        }
    }

    fun changeDefaultCommunitiesFollowList(name: TopFilter) {
        if (defaultCommunitiesFollowList.value != name) {
            defaultCommunitiesFollowList.tryEmit(name)
            saveAndSyncLater()
        }
    }

    fun changeDefaultPicturesFollowList(name: TopFilter) {
        if (defaultPicturesFollowList.value != name) {
            defaultPicturesFollowList.tryEmit(name)
            saveAndSyncLater()
        }
    }

    fun changeDefaultRelayGroupsDiscoveryFollowList(name: TopFilter) {
        if (defaultRelayGroupsDiscoveryFollowList.value != name) {
            defaultRelayGroupsDiscoveryFollowList.tryEmit(name)
            saveAndSyncLater()
        }
    }

    fun changeDefaultNappletsFollowList(name: TopFilter) {
        if (defaultNappletsFollowList.value != name) {
            defaultNappletsFollowList.tryEmit(name)
            saveAndSyncLater()
        }
    }

    fun changeDefaultNsitesFollowList(name: TopFilter) {
        if (defaultNsitesFollowList.value != name) {
            defaultNsitesFollowList.tryEmit(name)
            saveAndSyncLater()
        }
    }

    fun changeDefaultChessFollowList(name: TopFilter) {
        if (defaultChessFollowList.value != name) {
            defaultChessFollowList.tryEmit(name)
            saveAndSyncLater()
        }
    }

    fun changeDefaultWorkoutsFollowList(name: TopFilter) {
        if (defaultWorkoutsFollowList.value != name) {
            defaultWorkoutsFollowList.tryEmit(name)
            saveAndSyncLater()
        }
    }

    fun changeDefaultGitRepositoriesFollowList(name: TopFilter) {
        if (defaultGitRepositoriesFollowList.value != name) {
            defaultGitRepositoriesFollowList.tryEmit(name)
            saveAndSyncLater()
        }
    }

    fun changeDefaultHighlightsFollowList(name: TopFilter) {
        if (defaultHighlightsFollowList.value != name) {
            defaultHighlightsFollowList.tryEmit(name)
            saveAndSyncLater()
        }
    }

    fun changeDefaultCalendarsFollowList(name: TopFilter) {
        if (defaultCalendarsFollowList.value != name) {
            defaultCalendarsFollowList.tryEmit(name)
            saveAndSyncLater()
        }
    }

    fun changeDefaultProductsFollowList(name: TopFilter) {
        if (defaultProductsFollowList.value != name) {
            defaultProductsFollowList.tryEmit(name)
            saveAndSyncLater()
        }
    }

    fun changeDefaultGeocachesFollowList(name: TopFilter) {
        if (defaultGeocachesFollowList.value != name) {
            defaultGeocachesFollowList.tryEmit(name)
            saveAndSyncLater()
        }
    }

    fun changeDefaultShortsFollowList(name: TopFilter) {
        if (defaultShortsFollowList.value != name) {
            defaultShortsFollowList.tryEmit(name)
            saveAndSyncLater()
        }
    }

    fun changeDefaultPublicChatsFollowList(name: TopFilter) {
        if (defaultPublicChatsFollowList.value != name) {
            defaultPublicChatsFollowList.tryEmit(name)
            saveAndSyncLater()
        }
    }

    fun changeDefaultLiveStreamsFollowList(name: TopFilter) {
        if (defaultLiveStreamsFollowList.value != name) {
            defaultLiveStreamsFollowList.tryEmit(name)
            saveAndSyncLater()
        }
    }

    fun changeDefaultNestsFollowList(name: TopFilter) {
        if (defaultNestsFollowList.value != name) {
            defaultNestsFollowList.tryEmit(name)
            saveAndSyncLater()
        }
    }

    fun changeDefaultLongsFollowList(name: TopFilter) {
        if (defaultLongsFollowList.value != name) {
            defaultLongsFollowList.tryEmit(name)
            saveAndSyncLater()
        }
    }

    fun changeDefaultArticlesFollowList(name: TopFilter) {
        if (defaultArticlesFollowList.value != name) {
            defaultArticlesFollowList.tryEmit(name)
            saveAndSyncLater()
        }
    }

    fun changeDefaultMusicTracksFollowList(name: TopFilter) {
        if (defaultMusicTracksFollowList.value != name) {
            defaultMusicTracksFollowList.tryEmit(name)
            saveAndSyncLater()
        }
    }

    fun changeDefaultMusicPlaylistsFollowList(name: TopFilter) {
        if (defaultMusicPlaylistsFollowList.value != name) {
            defaultMusicPlaylistsFollowList.tryEmit(name)
            saveAndSyncLater()
        }
    }

    fun changeDefaultPodcastEpisodesFollowList(name: TopFilter) {
        if (defaultPodcastEpisodesFollowList.value != name) {
            defaultPodcastEpisodesFollowList.tryEmit(name)
            saveAndSyncLater()
        }
    }

    fun changeDefaultPodcastsFollowList(name: TopFilter) {
        if (defaultPodcastsFollowList.value != name) {
            defaultPodcastsFollowList.tryEmit(name)
            saveAndSyncLater()
        }
    }

    fun changeDefaultSoftwareAppsFollowList(name: TopFilter) {
        if (defaultSoftwareAppsFollowList.value != name) {
            defaultSoftwareAppsFollowList.tryEmit(name)
            saveAndSyncLater()
        }
    }

    fun changeDefaultBadgesFollowList(name: TopFilter) {
        if (defaultBadgesFollowList.value != name) {
            defaultBadgesFollowList.tryEmit(name)
            saveAndSyncLater()
        }
    }

    fun changeDefaultBrowseEmojiSetsFollowList(name: TopFilter) {
        if (defaultBrowseEmojiSetsFollowList.value != name) {
            defaultBrowseEmojiSetsFollowList.tryEmit(name)
            saveAndSyncLater()
        }
    }

    fun changeDefaultFollowPacksFollowList(name: TopFilter) {
        if (defaultFollowPacksFollowList.value != name) {
            defaultFollowPacksFollowList.tryEmit(name)
            saveAndSyncLater()
        }
    }

    fun changeDefaultAppRecommendationsFollowList(name: TopFilter) {
        if (defaultAppRecommendationsFollowList.value != name) {
            defaultAppRecommendationsFollowList.tryEmit(name)
            saveAndSyncLater()
        }
    }

    // ---
    // language services
    // ---
    fun toggleDontTranslateFrom(languageCode: String) {
        syncedSettings.languages.toggleDontTranslateFrom(languageCode)
        saveAccountSettings()
    }

    fun addDontTranslateFrom(languageCode: String) {
        syncedSettings.languages.addDontTranslateFrom(languageCode)
        saveAccountSettings()
    }

    fun removeDontTranslateFrom(languageCode: String) {
        syncedSettings.languages.removeDontTranslateFrom(languageCode)
        saveAccountSettings()
    }

    fun translateToContains(languageCode: String) =
        syncedSettings.languages.translateTo.value
            .contains(languageCode)

    fun updateTranslateTo(languageCode: String): Boolean {
        if (syncedSettings.languages.updateTranslateTo(languageCode)) {
            saveAccountSettings()
            return true
        }
        return false
    }

    fun prefer(
        source: String,
        target: String,
        preference: String,
    ) {
        syncedSettings.languages.prefer(source, target, preference)
        saveAccountSettings()
    }

    fun preferenceBetween(
        source: String,
        target: String,
    ): String? = syncedSettings.languages.preferenceBetween(source, target)

    // ----
    // Backup Lists
    // ----

    /**
     * Holds back external rewrites that dropped data from a backup, see [BackupConflictGuard].
     * Home shows a card for each open conflict until the user keeps the new version or
     * restores the saved one. Open conflicts are saved with the backups they hold back.
     */
    val backupGuard =
        BackupConflictGuard(
            onChange = { saveAccountSettings() },
            reapply = { reapplyBackup(it) },
        )

    val backupConflicts get() = backupGuard.conflicts

    /** Re-seeds the open conflicts read back from storage, see [BackupConflictGuard.restore]. */
    fun restoreBackupConflicts(saved: List<Triple<Event, Event, Event>>) = backupGuard.restore(saved)

    /** The open conflicts, as the three events storage needs to rebuild each one. */
    fun openBackupConflicts(): List<Triple<Event, Event, Event>> = backupGuard.open()

    /**
     * Re-runs the update that a restored conflict no longer carries.
     *
     * The guard's retries are closures, so they cannot be written to disk; a conflict read
     * back after a restart has none until its version happens to arrive again. Keeping the new
     * version must work regardless, and every update is the same shape — hand the event to the
     * function that owns its slot — so the slot can be recovered from the event's own type.
     */
    private fun reapplyBackup(incoming: Event) {
        when (incoming) {
            is MetadataEvent -> updateUserMetadata(incoming)
            is ContactListEvent -> updateContactListTo(incoming)
            is DmRelayListEvent -> updateDMRelayList(incoming)
            is KeyPackageRelayListEvent -> updateKeyPackageRelayList(incoming)
            is AdvertisedRelayListEvent -> updateNIP65RelayList(incoming)
            is CashuWalletEvent -> updateCashuWallet(incoming)
            is NutzapInfoEvent -> updateNutzapInfo(incoming)
            is PaymentTargetsEvent -> updateNIPA3PaymentTargets(incoming)
            is Bolt12OfferListEvent -> updateBolt12Offers(incoming)
            is SearchRelayListEvent -> updateSearchRelayList(incoming)
            is IndexerRelayListEvent -> updateIndexRelayList(incoming)
            is FavoriteRelayListEvent -> updateFavoriteRelayList(incoming)
            is BlockedRelayListEvent -> updateBlockedRelayList(incoming)
            is TrustedRelayListEvent -> updateTrustedRelayList(incoming)
            is PrivateOutboxRelayListEvent -> updatePrivateHomeRelayList(incoming)
            is PublicChatListEvent -> updatePublicChatListTo(incoming)
            is GeohashListEvent -> updateGeohashListTo(incoming)
            is InterestListEvent -> updateInterestListTo(incoming)
            is FavoriteAlgoFeedsListEvent -> updateFavoriteAlgoFeedsListTo(incoming)
            is CommunityListEvent -> updateCommunityListTo(incoming)
            is EphemeralChatListEvent -> updateEphemeralChatListTo(incoming)
            is SimpleGroupListEvent -> updateRelayGroupListTo(incoming)
            is ConcordCommunityListEvent -> updateConcordListTo(incoming)
            is TrustProviderListEvent -> updateTrustProviderListTo(incoming)
            is MuteListEvent -> updateMuteList(incoming)
            // NIP-78 only raises a conflict when its blob was emptied, and an empty blob is
            // stored without merging the settings handed with it, so defaults are safe here.
            // A non-empty one needs the decrypted settings only the caller has; it lands when
            // the event next arrives, already recorded as accepted.
            is AppSpecificDataEvent -> if (incoming.content.isBlank()) updateAppSpecificData(incoming, AccountSyncedSettingsInternal())
            else -> Unit
        }
    }

    fun updateLocalRelayServers(servers: Set<String>) {
        if (localRelayServers.value != servers) {
            localRelayServers.update { servers }
            saveAccountSettings()
        }
    }

    fun changeSendKind0EventsToLocalRelay(send: Boolean): Boolean {
        if (syncedSettings.security.updateSendKind0EventsToLocalRelay(send)) {
            saveAccountSettings()
            return true
        }
        return false
    }

    fun updateUserMetadata(newMetadata: MetadataEvent?) {
        if (newMetadata == null) return

        if (backupGuard.accept(backupUserMetadata, newMetadata) { updateUserMetadata(newMetadata) }) {
            backupUserMetadata = newMetadata
            saveAccountSettings()
        }
    }

    fun updateContactListTo(newContactList: ContactListEvent?) {
        if (newContactList == null) return

        if (backupGuard.accept(backupContactList, newContactList, isEmpty = newContactList.tags.isEmpty()) { updateContactListTo(newContactList) }) {
            backupContactList = newContactList
            saveAccountSettings()
        }
    }

    fun updateDMRelayList(newDMRelayList: DmRelayListEvent?) {
        if (newDMRelayList == null) return

        if (backupGuard.accept(backupDMRelayList, newDMRelayList, isEmpty = newDMRelayList.tags.isEmpty()) { updateDMRelayList(newDMRelayList) }) {
            backupDMRelayList = newDMRelayList
            saveAccountSettings()
        }
    }

    fun updateKeyPackageRelayList(newKeyPackageRelayList: KeyPackageRelayListEvent?) {
        if (newKeyPackageRelayList == null) return

        if (backupGuard.accept(backupKeyPackageRelayList, newKeyPackageRelayList, isEmpty = newKeyPackageRelayList.tags.isEmpty()) { updateKeyPackageRelayList(newKeyPackageRelayList) }) {
            backupKeyPackageRelayList = newKeyPackageRelayList
            saveAccountSettings()
        }
    }

    fun updateNIP65RelayList(newNIP65RelayList: AdvertisedRelayListEvent?) {
        if (newNIP65RelayList == null) return

        if (backupGuard.accept(backupNIP65RelayList, newNIP65RelayList, isEmpty = newNIP65RelayList.tags.isEmpty()) { updateNIP65RelayList(newNIP65RelayList) }) {
            backupNIP65RelayList = newNIP65RelayList
            saveAccountSettings()
        }
    }

    fun updateCashuWallet(newWallet: CashuWalletEvent?) {
        if (newWallet == null) return
        // Replaceable: keep the latest by id (id changes on each re-sign).
        if (backupGuard.accept(backupCashuWallet, newWallet) { updateCashuWallet(newWallet) }) {
            backupCashuWallet = newWallet
            saveAccountSettings()
        }
    }

    fun updateNutzapInfo(newNutzapInfo: NutzapInfoEvent?) {
        if (newNutzapInfo == null) return
        // The guard runs first, so another app wiping the nutzap info is questioned like any
        // other lossy rewrite instead of silently clearing the backup.
        if (!backupGuard.accept(backupNutzapInfo, newNutzapInfo) { updateNutzapInfo(newNutzapInfo) }) return
        // A mints-less kind:10019 is the "stop receiving nutzaps" tombstone
        // (an empty replacement carrying only an `alt` tag). Don't restore it
        // on next launch — backing it up would undo clearNutzapInfo() once the
        // empty event round-trips back through LocalCache.
        if (newNutzapInfo.mints().isEmpty()) {
            clearNutzapInfo()
            return
        }
        backupNutzapInfo = newNutzapInfo
        saveAccountSettings()
    }

    /**
     * Drop the cached kind:17375 so a relaunch doesn't restore a wallet the
     * user just deleted. Called when the wallet event is NIP-09 deleted —
     * without this the [backupCashuWallet] would be re-consumed into
     * LocalCache on next launch and resurrect the deleted wallet.
     */
    fun clearCashuWallet() {
        // A deleted wallet must not be restorable from an open conflict.
        backupGuard.drop(backupSlotOf(CashuWalletEvent.KIND))
        if (backupCashuWallet != null) {
            backupCashuWallet = null
            saveAccountSettings()
        }
    }

    /** Drop the cached kind:10019. Mirror of [clearCashuWallet] for the nutzap info. */
    fun clearNutzapInfo() {
        backupGuard.drop(backupSlotOf(NutzapInfoEvent.KIND))
        if (backupNutzapInfo != null) {
            backupNutzapInfo = null
            saveAccountSettings()
        }
    }

    /**
     * Reserve [count] consecutive NUT-13 counters for [keysetId],
     * returning the first one. Caller derives `(secret, r)` from
     * `(seed, keysetId, i)` for `i in [returned .. returned+count-1]`.
     * Persisted before returning — see [CashuKeysetCounterStore]. Suspends
     * because that write is what stands between a crash and a reused counter.
     *
     * One-time migration: when this keyset has a non-zero value in the
     * legacy [cashuKeysetCounters] map (from a build that persisted
     * counters inside AccountSettings) and the dedicated store is
     * still at zero, the legacy value is copied over before we reserve
     * so an upgrade doesn't reset the counter.
     */
    suspend fun reserveCashuCounters(
        keysetId: String,
        count: Int,
    ): Long {
        migrateLegacyCashuCounter(keysetId)
        return cashuCounters.reserve(keysetId, count)
    }

    /** Inspect the next counter for [keysetId] without consuming any. */
    suspend fun peekCashuCounter(keysetId: String): Long {
        migrateLegacyCashuCounter(keysetId)
        return cashuCounters.peek(keysetId)
    }

    private suspend fun migrateLegacyCashuCounter(keysetId: String) {
        val legacy = cashuKeysetCounters[keysetId] ?: return
        cashuCounters.seedIfMissing(keysetId, legacy)
    }

    fun updateNIPA3PaymentTargets(newNIPA3PaymentTargets: PaymentTargetsEvent?) {
        if (newNIPA3PaymentTargets == null) return

        if (backupGuard.accept(backupNipA3PaymentTargets, newNIPA3PaymentTargets, isEmpty = newNIPA3PaymentTargets.tags.isEmpty()) { updateNIPA3PaymentTargets(newNIPA3PaymentTargets) }) {
            backupNipA3PaymentTargets = newNIPA3PaymentTargets
            saveAccountSettings()
        }
    }

    fun updateBolt12Offers(newBolt12Offers: Bolt12OfferListEvent?) {
        if (newBolt12Offers == null) return

        if (backupGuard.accept(backupBolt12Offers, newBolt12Offers, isEmpty = newBolt12Offers.tags.isEmpty()) { updateBolt12Offers(newBolt12Offers) }) {
            backupBolt12Offers = newBolt12Offers
            saveAccountSettings()
        }
    }

    fun updateSearchRelayList(newSearchRelayList: SearchRelayListEvent?) {
        if (newSearchRelayList == null) return

        if (backupGuard.accept(backupSearchRelayList, newSearchRelayList, isEmpty = newSearchRelayList.tags.isEmpty()) { updateSearchRelayList(newSearchRelayList) }) {
            backupSearchRelayList = newSearchRelayList
            saveAccountSettings()
        }
    }

    fun updateIndexRelayList(newIndexRelayList: IndexerRelayListEvent?) {
        if (newIndexRelayList == null) return

        if (backupGuard.accept(backupIndexRelayList, newIndexRelayList, isEmpty = newIndexRelayList.tags.isEmpty()) { updateIndexRelayList(newIndexRelayList) }) {
            backupIndexRelayList = newIndexRelayList
            saveAccountSettings()
        }
    }

    fun updateFavoriteRelayList(newFavoriteRelayList: FavoriteRelayListEvent?) {
        if (newFavoriteRelayList == null) return

        if (backupGuard.accept(backupFavoriteRelayList, newFavoriteRelayList, isEmpty = newFavoriteRelayList.tags.isEmpty()) { updateFavoriteRelayList(newFavoriteRelayList) }) {
            backupFavoriteRelayList = newFavoriteRelayList
            saveAccountSettings()
        }
    }

    fun updateBlockedRelayList(newBlockedRelayList: BlockedRelayListEvent?) {
        if (newBlockedRelayList == null) return

        if (backupGuard.accept(backupBlockedRelayList, newBlockedRelayList, isEmpty = newBlockedRelayList.tags.isEmpty()) { updateBlockedRelayList(newBlockedRelayList) }) {
            backupBlockedRelayList = newBlockedRelayList
            saveAccountSettings()
        }
    }

    fun updateTrustedRelayList(newTrustedRelayList: TrustedRelayListEvent?) {
        if (newTrustedRelayList == null) return

        if (backupGuard.accept(backupTrustedRelayList, newTrustedRelayList, isEmpty = newTrustedRelayList.tags.isEmpty()) { updateTrustedRelayList(newTrustedRelayList) }) {
            backupTrustedRelayList = newTrustedRelayList
            saveAccountSettings()
        }
    }

    fun updatePrivateHomeRelayList(newPrivateHomeRelayList: PrivateOutboxRelayListEvent?) {
        if (newPrivateHomeRelayList == null) return

        if (backupGuard.accept(backupPrivateHomeRelayList, newPrivateHomeRelayList, isEmpty = newPrivateHomeRelayList.tags.isEmpty()) { updatePrivateHomeRelayList(newPrivateHomeRelayList) }) {
            backupPrivateHomeRelayList = newPrivateHomeRelayList
            saveAccountSettings()
        }
    }

    override fun publicChatList() = backupPublicChatList

    override fun updatePublicChatListTo(newPublicChatList: PublicChatListEvent?) {
        if (newPublicChatList == null) return

        if (backupGuard.accept(backupPublicChatList, newPublicChatList, isEmpty = newPublicChatList.tags.isEmpty()) { updatePublicChatListTo(newPublicChatList) }) {
            backupPublicChatList = newPublicChatList
            saveAccountSettings()
        }
    }

    fun updateGeohashListTo(newGeohashList: GeohashListEvent?) {
        if (newGeohashList == null) return

        if (backupGuard.accept(backupGeohashList, newGeohashList, isEmpty = newGeohashList.tags.isEmpty()) { updateGeohashListTo(newGeohashList) }) {
            backupGeohashList = newGeohashList
            saveAccountSettings()
        }
    }

    fun updateInterestListTo(newInterestList: InterestListEvent?) {
        if (newInterestList == null) return

        if (backupGuard.accept(backupInterestList, newInterestList, isEmpty = newInterestList.tags.isEmpty()) { updateInterestListTo(newInterestList) }) {
            backupInterestList = newInterestList
            saveAccountSettings()
        }
    }

    fun updateFavoriteAlgoFeedsListTo(newFavoriteDvmList: FavoriteAlgoFeedsListEvent?) {
        if (newFavoriteDvmList == null) return

        if (backupGuard.accept(backupFavoriteAlgoFeedsList, newFavoriteDvmList, isEmpty = newFavoriteDvmList.tags.isEmpty()) { updateFavoriteAlgoFeedsListTo(newFavoriteDvmList) }) {
            backupFavoriteAlgoFeedsList = newFavoriteDvmList
            saveAccountSettings()
        }
    }

    fun updateCommunityListTo(newCommunityList: CommunityListEvent?) {
        if (newCommunityList == null) return

        if (backupGuard.accept(backupCommunityList, newCommunityList, isEmpty = newCommunityList.tags.isEmpty()) { updateCommunityListTo(newCommunityList) }) {
            backupCommunityList = newCommunityList
            saveAccountSettings()
        }
    }

    override fun ephemeralChatList() = backupEphemeralChatList

    override fun updateEphemeralChatListTo(newEphemeralChatList: EphemeralChatListEvent?) {
        if (newEphemeralChatList == null) return

        if (backupGuard.accept(backupEphemeralChatList, newEphemeralChatList, isEmpty = newEphemeralChatList.tags.isEmpty()) { updateEphemeralChatListTo(newEphemeralChatList) }) {
            backupEphemeralChatList = newEphemeralChatList
            saveAccountSettings()
        }
    }

    override fun relayGroupList() = backupRelayGroupList

    override fun updateRelayGroupListTo(newRelayGroupList: SimpleGroupListEvent?) {
        // Joined groups can live in the NIP-44 private items (encrypted content),
        // so an empty `tags` is NOT an empty list — guard only on null.
        if (newRelayGroupList == null) return

        if (backupGuard.accept(backupRelayGroupList, newRelayGroupList) { updateRelayGroupListTo(newRelayGroupList) }) {
            backupRelayGroupList = newRelayGroupList
            saveAccountSettings()
        }
    }

    override fun concordList() = backupConcordList

    override fun concordListFragments() = backupConcordListFragments

    override fun updateConcordListFragmentTo(fragment: ConcordCommunityListFragmentEvent) {
        val index = fragment.index() ?: return
        val prior = backupConcordListFragments.firstOrNull { it.index() == index }
        // Newest copy per index, as a relay resolves the coordinate (ties to the lower id).
        if (prior != null && (prior.createdAt > fragment.createdAt || (prior.createdAt == fragment.createdAt && prior.id <= fragment.id))) return
        backupConcordListFragments = backupConcordListFragments.filterNot { it.index() == index } + fragment
        saveAccountSettings()
    }

    override fun updateConcordListTo(newConcordList: ConcordCommunityListEvent?) {
        // The joined list lives entirely in NIP-44-encrypted content (secrets),
        // so an empty `tags` is NOT an empty list — guard only on null.
        if (newConcordList == null) return

        if (backupGuard.accept(backupConcordList, newConcordList) { updateConcordListTo(newConcordList) }) {
            backupConcordList = newConcordList
            saveAccountSettings()
        }
    }

    fun updateTrustProviderListTo(trustProviderList: TrustProviderListEvent?) {
        if (trustProviderList == null) return

        if (backupGuard.accept(backupTrustProviderList, trustProviderList, isEmpty = trustProviderList.tags.isEmpty()) { updateTrustProviderListTo(trustProviderList) }) {
            backupTrustProviderList = trustProviderList
            saveAccountSettings()
        }
    }

    fun updateMuteList(newMuteList: MuteListEvent?) {
        if (newMuteList == null) return

        if (backupGuard.accept(backupMuteList, newMuteList, isEmpty = newMuteList.tags.isEmpty()) { updateMuteList(newMuteList) }) {
            backupMuteList = newMuteList
            saveAccountSettings()
        }
    }

    /**
     * A version built from these live settings, back from the relays: kept as the backup but not
     * merged, since the live settings may have moved on while it was out (see
     * `AppSpecificState.snapshotsSignedHere`).
     */
    fun keepOwnAppSpecificData(appSettings: AppSpecificDataEvent) {
        if (backupGuard.accept(backupAppSpecificData, appSettings, isEmpty = appSettings.content.isBlank()) { keepOwnAppSpecificData(appSettings) }) {
            backupAppSpecificData = appSettings
            saveAccountSettings()
        }
    }

    fun updateAppSpecificData(
        appSettings: AppSpecificDataEvent?,
        newSyncedSettings: AccountSyncedSettingsInternal,
    ) {
        if (appSettings == null) return

        if (backupGuard.accept(backupAppSpecificData, appSettings, isEmpty = appSettings.content.isBlank()) { updateAppSpecificData(appSettings, newSyncedSettings) }) {
            backupAppSpecificData = appSettings
            // An emptied blob (signed here or accepted by the user) has no settings to merge.
            if (appSettings.content.isBlank()) {
                saveAccountSettings()
                return
            }
            syncedSettings.updateFrom(newSyncedSettings)
            applyLocalGroups(newSyncedSettings)

            // Null means an older client rewrote the blob without this key — leave the
            // local set alone rather than treating "absent" as "unmute everything".
            // The decision lives in mergeMutedPublicChats so it is unit-testable; this
            // class cannot be constructed in a JVM test.
            mutedPublicChats.tryEmit(
                mergeMutedPublicChats(mutedPublicChats.value, newSyncedSettings.chats.mutedPublicChats),
            )

            saveAccountSettings()
        }
    }

    // ----
    // Warning dialogs
    // ----

    fun setHideDeleteRequestDialog() {
        if (!hideDeleteRequestDialog) {
            hideDeleteRequestDialog = true
            saveAccountSettings()
        }
    }

    fun setHideNIP17WarningDialog() {
        if (!hideNIP17WarningDialog) {
            hideNIP17WarningDialog = true
            saveAccountSettings()
        }
    }

    fun setHideBlockAlertDialog() {
        if (!hideBlockAlertDialog) {
            hideBlockAlertDialog = true
            saveAccountSettings()
        }
    }

    // ---
    // web of trust card
    // ---

    fun hideWebOfTrustNudgeUntil(time: Long) {
        if (webOfTrustNudgeHiddenUntil.value != time) {
            webOfTrustNudgeHiddenUntil.value = time
            saveAccountSettings()
        }
    }

    // ---
    // donations
    // ---

    fun hasDonatedInVersion(versionName: String) = hasDonatedInVersion.value.contains(versionName)

    fun observeDonatedInVersion(versionName: String) =
        hasDonatedInVersion
            .map {
                it.contains(versionName)
            }

    fun markDonatedInThisVersion(versionName: String): Boolean {
        if (!hasDonatedInVersion.value.contains(versionName)) {
            hasDonatedInVersion.update {
                it + versionName
            }
            saveAccountSettings()
            return true
        }
        return false
    }

    // ---
    // dismissed polls
    // ---

    fun isDismissedPoll(noteId: String) = dismissedPollNoteIds.value.contains(noteId)

    fun dismissPollNotification(noteId: String) {
        if (!dismissedPollNoteIds.value.contains(noteId)) {
            dismissedPollNoteIds.update {
                it + noteId
            }
            saveAccountSettings()
        }
    }

    // ---
    // dismissed channel invites (somebody added me to a channel; I don't want it on Messages)
    // ---

    fun isDismissedChannelInvite(channelId: String) = dismissedChannelInvites.value.contains(channelId)

    fun dismissChannelInvite(channelId: String) {
        if (!dismissedChannelInvites.value.contains(channelId)) {
            dismissedChannelInvites.update { it + channelId }
            saveAccountSettings()
        }
    }

    /** Undo a dismissal — used when the viewer accepts the channel after all, so it can re-prompt later. */
    fun undismissChannelInvite(channelId: String) {
        if (dismissedChannelInvites.value.contains(channelId)) {
            dismissedChannelInvites.update { it - channelId }
            saveAccountSettings()
        }
    }

    // ---
    // pinned chatrooms
    // ---

    fun toggleChatroomPin(room: ChatroomKey) {
        syncedSettings.chats.pinnedChatrooms.update {
            if (room in it) it - room else it + room
        }
        saveAccountSettings()
    }

    // ---
    // muted public chats
    // ---

    fun toggleMutedPublicChat(channelId: String) {
        mutedPublicChats.update {
            if (channelId in it) it - channelId else it + channelId
        }
        saveAccountSettings()
    }

    // ---
    // viewed poll results
    // ---

    fun hasViewedPollResults(noteId: String): Boolean {
        val expiresAt = viewedPollResultNoteIds.value[noteId] ?: return false
        return expiresAt > TimeUtils.now()
    }

    fun markPollResultsViewed(
        noteId: String,
        pollEndsAt: Long?,
    ) {
        if (noteId !in viewedPollResultNoteIds.value) {
            val expiresAt =
                if (pollEndsAt != null && pollEndsAt > TimeUtils.now()) {
                    pollEndsAt
                } else {
                    TimeUtils.now() + TimeUtils.ONE_DAY
                }
            viewedPollResultNoteIds.update {
                pruneExpiredViews(it) + (noteId to expiresAt)
            }
            saveAccountSettings()
        }
    }

    private fun pruneExpiredViews(views: Map<String, Long>): Map<String, Long> {
        val now = TimeUtils.now()
        return views.filterValues { it > now }
    }

    // ----
    // last read flows
    // ----

    fun getLastReadFlow(route: String): StateFlow<Long> = lastReadPerRoute.value[route] ?: lastReadFlowFor(route)

    /**
     * The route's flow, created at 0 when absent. Created inside the map's atomic update so two threads meeting a new
     * route share one flow: the loser of a plain check-then-put would replace the winner's,
     * dropping its timestamp or leaving a row observing a flow nothing updates.
     *
     * Nothing is saved here: a route at 0 reads back the same as no route at all.
     */
    private fun lastReadFlowFor(route: String): MutableStateFlow<Long> =
        lastReadPerRoute
            .updateAndGet { if (route in it) it else it + Pair(route, MutableStateFlow(0L)) }
            .getValue(route)

    /** Forgets the last-read markers of the routes [route] matches, for markers another one supersedes. */
    fun dropLastRead(route: (String) -> Boolean) {
        val before = lastReadPerRoute.value
        val after = lastReadPerRoute.updateAndGet { map -> map.filterKeys { !route(it) } }
        if (after.size != before.size) saveAccountSettings()
    }

    fun markAsRead(
        route: String,
        timestampInSecs: Long,
    ): Boolean {
        val lastTime = lastReadPerRoute.value[route] ?: lastReadFlowFor(route)
        // Compare-and-set: rows mark themselves read from parallel coroutines, and a plain
        // check-then-emit lets an older timestamp land after a newer one.
        while (true) {
            val current = lastTime.value
            if (timestampInSecs <= current) return false
            if (lastTime.compareAndSet(current, timestampInSecs)) break
        }
        saveAccountSettings()
        return true
    }

    // ---
    // attestations
    // ---

    fun addPendingAttestation(
        id: HexKey,
        stamp: String,
    ) {
        val current = pendingAttestations.value.get(id)
        if (current == null) {
            pendingAttestations.update {
                it + Pair(id, stamp)
            }
            saveAccountSettings()
        } else {
            if (current != stamp) {
                pendingAttestations.update {
                    it + Pair(id, stamp)
                }
                saveAccountSettings()
            }
        }
    }

    // ---
    // filters
    // ---
    fun updateShowSensitiveContent(show: Boolean?): Boolean {
        if (syncedSettings.security.updateShowSensitiveContent(show)) {
            saveAccountSettings()
            return true
        }
        return false
    }

    fun updateWarnReports(warnReports: Boolean): Boolean =
        if (syncedSettings.security.updateWarnReports(warnReports)) {
            saveAccountSettings()
            true
        } else {
            false
        }

    fun updateReportWarningThreshold(threshold: Int): Boolean =
        if (syncedSettings.security.updateReportWarningThreshold(threshold)) {
            saveAccountSettings()
            true
        } else {
            false
        }

    fun updateFilterSpam(filterSpam: Boolean): Boolean =
        if (syncedSettings.security.updateFilterSpam(filterSpam)) {
            saveAccountSettings()
            true
        } else {
            false
        }

    fun updateMaxHashtagLimit(limit: Int): Boolean =
        if (syncedSettings.security.updateMaxHashtagLimit(limit)) {
            saveAccountSettings()
            true
        } else {
            false
        }

    fun updateMinTrustScore(score: Int): Boolean =
        if (syncedSettings.security.updateMinTrustScore(score)) {
            saveAccountSettings()
            true
        } else {
            false
        }

    // ---
    // Call settings
    // ---

    fun changeCallTurnServers(servers: List<CallTurnServer>) {
        callTurnServers = servers
        saveAccountSettings()
    }

    fun changeCallVideoResolution(resolution: CallVideoResolution) {
        callVideoResolution = resolution
        saveAccountSettings()
    }

    fun changeCallMaxBitrateBps(bitrate: Int) {
        callMaxBitrateBps = bitrate
        saveAccountSettings()
    }

    fun changeCallsEnabled(enabled: Boolean) {
        if (callsEnabled.value != enabled) {
            callsEnabled.tryEmit(enabled)
            saveAndSync()
        }
    }

    fun changeComposeSignature(signature: String) {
        if (composeSignature.value != signature) {
            composeSignature.tryEmit(signature)
            saveAndSync()
        }
    }

    fun changeDefaultRelayAuthPolicy(policy: RelayAuthPolicy) {
        if (defaultRelayAuthPolicy.value != policy) {
            defaultRelayAuthPolicy.tryEmit(policy)
            saveAndSync()
        }
    }

    private fun changeToggle(
        flow: MutableStateFlow<Boolean>,
        enabled: Boolean,
    ) {
        if (flow.value != enabled) {
            flow.tryEmit(enabled)
            saveAndSync()
        }
    }

    fun changeRelayAuthTrustMyRelaysAndVenues(enabled: Boolean) = changeToggle(relayAuthTrustMyRelaysAndVenues, enabled)

    fun changeRelayAuthTrustReadFollows(enabled: Boolean) = changeToggle(relayAuthTrustReadFollows, enabled)

    fun changeRelayAuthTrustMessageFollows(enabled: Boolean) = changeToggle(relayAuthTrustMessageFollows, enabled)

    fun changeRelayAuthTrustMessageStrangers(enabled: Boolean) = changeToggle(relayAuthTrustMessageStrangers, enabled)
}

@Serializable
data class CallTurnServer(
    val url: String,
    val username: String,
    val credential: String,
)

@Serializable
enum class CallVideoResolution(
    val width: Int,
    val height: Int,
    val fps: Int,
    val label: String,
) {
    SD_480(640, 480, 30, "480p"),
    HD_720(1280, 720, 30, "720p (default)"),
    FHD_1080(1920, 1080, 30, "1080p"),
}
