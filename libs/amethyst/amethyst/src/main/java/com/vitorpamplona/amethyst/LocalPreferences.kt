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
package com.vitorpamplona.amethyst

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.vitorpamplona.amethyst.commons.account.AccountInfo
import com.vitorpamplona.amethyst.commons.account.AccountSessionStore
import com.vitorpamplona.amethyst.commons.fitness.FitnessGoalsStore
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.UiSettings
import com.vitorpamplona.amethyst.commons.model.backups.BackupConflictStorage
import com.vitorpamplona.amethyst.commons.model.preferences.AccountIdentity
import com.vitorpamplona.amethyst.commons.model.preferences.AccountIdentityStore
import com.vitorpamplona.amethyst.commons.model.preferences.AccountPreferenceStores
import com.vitorpamplona.amethyst.commons.model.preferences.AccountSettingsSource
import com.vitorpamplona.amethyst.commons.model.preferences.AccountSettingsStores
import com.vitorpamplona.amethyst.commons.model.preferences.AccountStoreRecords
import com.vitorpamplona.amethyst.commons.model.preferences.ComposePrefsStore
import com.vitorpamplona.amethyst.commons.model.preferences.CopyOnceMigration
import com.vitorpamplona.amethyst.commons.model.preferences.DialogDismissalStore
import com.vitorpamplona.amethyst.commons.model.preferences.FeedVisibilityStore
import com.vitorpamplona.amethyst.commons.model.preferences.FollowListSlot
import com.vitorpamplona.amethyst.commons.model.preferences.LatestEventCacheStore
import com.vitorpamplona.amethyst.commons.model.preferences.LegacyPreferenceSource
import com.vitorpamplona.amethyst.commons.model.preferences.NotificationPrefsStore
import com.vitorpamplona.amethyst.commons.model.preferences.NwcBackupPrefsStore
import com.vitorpamplona.amethyst.commons.model.preferences.RelayAuthStore
import com.vitorpamplona.amethyst.commons.model.preferences.TopNavFollowListStore
import com.vitorpamplona.amethyst.commons.model.preferences.UploadSettingsStore
import com.vitorpamplona.amethyst.commons.model.preferences.orIfUnusable
import com.vitorpamplona.amethyst.commons.model.preferences.readLegacyAccountSecrets
import com.vitorpamplona.amethyst.commons.model.preferences.readLegacyGeohashIdentity
import com.vitorpamplona.amethyst.commons.model.preferences.toAccountSecrets
import com.vitorpamplona.amethyst.commons.model.preferences.toAccountSettings
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.amethyst.model.nip60Cashu.CashuPreferences
import com.vitorpamplona.amethyst.model.preferences.UiSharedPreferences
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.Path.Companion.toOkioPath
import java.io.File

// Release mode (!BuildConfig.DEBUG) always uses encrypted preferences
// To use plaintext SharedPreferences for debugging, set this to true
// It will only apply in Debug builds
private const val DEBUG_PLAINTEXT_PREFERENCES = false
private const val DEBUG_PREFERENCES_NAME = "debug_prefs"

internal object PrefKeys {
    const val CURRENT_ACCOUNT = "currently_logged_in_account"

    // Global (non-account) master switch for the always-on notification service.
    // When off, the service is suppressed for every account regardless of each
    // account's own participation flag. Persisted so it survives restarts/crashes.
    const val NOTIFICATION_SERVICE_ENABLED = "notification_service_enabled"
    const val SAVED_ACCOUNTS = "all_saved_accounts"
    const val NOSTR_PRIVKEY = "nostr_privkey"
    const val NOSTR_PUBKEY = "nostr_pubkey"
    const val LOCAL_RELAY_SERVERS = "localRelayServers"
    const val DEFAULT_FILE_SERVER = "defaultFileServer"
    const val STRIP_LOCATION_ON_UPLOAD = "stripLocationOnUpload"
    const val USE_LOCAL_BLOSSOM_CACHE = "useLocalBlossomCache"
    const val LOCAL_BLOSSOM_CACHE_PROFILE_PICTURES_ONLY = "localBlossomCacheProfilePicturesOnly"
    const val MIRROR_UPLOADS_TO_ALL_SERVERS = "mirrorUploadsToAllServers"
    const val OPTIMIZE_MEDIA_ON_UPLOAD = "optimizeMediaOnUpload"
    const val HIDE_COMMUNITY_RULES_VIOLATIONS = "hideCommunityRulesViolations"
    const val NIP46_SIGNER_ENABLED = "nip46SignerEnabled"
    const val NIP46_BUNKER_SECRET = "nip46BunkerSecret"
    const val NIP46_TRANSPORT_KEY = "nip46TransportKey"
    const val NIP46_SEEN_IDS = "nip46SeenRequestIds"
    const val DEFAULT_HOME_FOLLOW_LIST = "defaultHomeFollowList"
    const val DEFAULT_STORIES_FOLLOW_LIST = "defaultStoriesFollowList"
    const val DEFAULT_NOTIFICATION_FOLLOW_LIST = "defaultNotificationFollowList"
    const val DEFAULT_DISCOVERY_FOLLOW_LIST = "defaultDiscoveryFollowList"
    const val DEFAULT_POLLS_FOLLOW_LIST = "defaultPollsFollowList"
    const val DEFAULT_PICTURES_FOLLOW_LIST = "defaultPicturesFollowList"
    const val DEFAULT_RELAY_GROUPS_DISCOVERY_FOLLOW_LIST = "defaultRelayGroupsDiscoveryFollowList"
    const val DEFAULT_NAPPLETS_FOLLOW_LIST = "defaultNappletsFollowList"
    const val DEFAULT_NSITES_FOLLOW_LIST = "defaultNsitesFollowList"
    const val DEFAULT_WORKOUTS_FOLLOW_LIST = "defaultWorkoutsFollowList"
    const val DEFAULT_GIT_REPOSITORIES_FOLLOW_LIST = "defaultGitRepositoriesFollowList"
    const val DEFAULT_HIGHLIGHTS_FOLLOW_LIST = "defaultHighlightsFollowList"
    const val DEFAULT_CALENDARS_FOLLOW_LIST = "defaultCalendarsFollowList"
    const val DEFAULT_PRODUCTS_FOLLOW_LIST = "defaultProductsFollowList"
    const val DEFAULT_GEOCACHES_FOLLOW_LIST = "defaultGeocachesFollowList"
    const val DEFAULT_SHORTS_FOLLOW_LIST = "defaultShortsFollowList"
    const val DEFAULT_PUBLIC_CHATS_FOLLOW_LIST = "defaultPublicChatsFollowList"
    const val DEFAULT_LIVE_STREAMS_FOLLOW_LIST = "defaultLiveStreamsFollowList"
    const val DEFAULT_NESTS_FOLLOW_LIST = "defaultNestsFollowList"
    const val DEFAULT_LONGS_FOLLOW_LIST = "defaultLongsFollowList"
    const val DEFAULT_ARTICLES_FOLLOW_LIST = "defaultArticlesFollowList"
    const val DEFAULT_MUSIC_TRACKS_FOLLOW_LIST = "defaultMusicTracksFollowList"
    const val DEFAULT_MUSIC_PLAYLISTS_FOLLOW_LIST = "defaultMusicPlaylistsFollowList"
    const val DEFAULT_PODCAST_EPISODES_FOLLOW_LIST = "defaultPodcastEpisodesFollowList"
    const val DEFAULT_PODCASTS_FOLLOW_LIST = "defaultPodcastsFollowList"
    const val DEFAULT_SOFTWARE_APPS_FOLLOW_LIST = "defaultSoftwareAppsFollowList"
    const val DEFAULT_BADGES_FOLLOW_LIST = "defaultBadgesFollowList"
    const val DEFAULT_BROWSE_EMOJI_SETS_FOLLOW_LIST = "defaultBrowseEmojiSetsFollowList"
    const val DEFAULT_COMMUNITIES_FOLLOW_LIST = "defaultCommunitiesFollowList"
    const val DEFAULT_FOLLOW_PACKS_FOLLOW_LIST = "defaultFollowPacksFollowList"
    const val DEFAULT_APP_RECOMMENDATIONS_FOLLOW_LIST = "defaultAppRecommendationsFollowList"
    const val DEFAULT_CHESS_FOLLOW_LIST = "defaultChessFollowList"
    const val ZAP_PAYMENT_REQUEST_SERVER = "zapPaymentServer" // legacy, kept for migration
    const val NWC_WALLETS = "nwcWallets"
    const val DEFAULT_NWC_WALLET_ID = "defaultNwcWalletId" // legacy, migrated into DEFAULT_PAYMENT_SOURCE_ID
    const val CLINK_DEBIT_WALLETS = "clinkDebitWallets"
    const val DEFAULT_PAYMENT_SOURCE_ID = "defaultPaymentSourceId"
    const val OPEN_BACKUP_CONFLICTS = "openBackupConflicts"
    const val LATEST_USER_METADATA = "latestUserMetadata"
    const val LATEST_CONTACT_LIST = "latestContactList"
    const val LATEST_DM_RELAY_LIST = "latestDMRelayList"
    const val LATEST_NIP65_RELAY_LIST = "latestNIP65RelayList"
    const val LATEST_SEARCH_RELAY_LIST = "latestSearchRelayList"
    const val LATEST_INDEX_RELAY_LIST = "latestIndexRelayList"
    const val LATEST_RELAY_FEEDS_LIST = "latestRelayFeedsList"
    const val LATEST_BLOCKED_RELAY_LIST = "latestBlockedRelayList"
    const val LATEST_TRUSTED_RELAY_LIST = "latestTrustedRelayList"
    const val LATEST_MUTE_LIST = "latestMuteList"
    const val LATEST_PRIVATE_HOME_RELAY_LIST = "latestPrivateHomeRelayList"
    const val LATEST_APP_SPECIFIC_DATA = "latestAppSpecificData"
    const val LATEST_CHANNEL_LIST = "latestChannelList"
    const val LATEST_COMMUNITY_LIST = "latestCommunityList"
    const val LATEST_HASHTAG_LIST = "latestHashtagList"
    const val LATEST_GEOHASH_LIST = "latestGeohashList"
    const val LATEST_EPHEMERAL_LIST = "latestEphemeralChatList"
    const val LATEST_RELAY_GROUP_LIST = "latestRelayGroupList"
    const val LATEST_CONCORD_LIST = "latestConcordList"
    const val LATEST_TRUST_PROVIDER_LIST = "latestTrustProviderList"
    const val LATEST_KEY_PACKAGE_RELAY_LIST = "latestKeyPackageRelayList"
    const val LATEST_FAVORITE_ALGO_FEEDS_LIST = "latestFavoriteAlgoFeedsList"
    const val CALLS_ENABLED = "calls_enabled"
    const val HIDE_DELETE_REQUEST_DIALOG = "hide_delete_request_dialog"
    const val HIDE_BLOCK_ALERT_DIALOG = "hide_block_alert_dialog"
    const val HIDE_NIP_17_WARNING_DIALOG = "hide_nip24_warning_dialog" // delete later
    const val ALWAYS_ON_NOTIFICATION_SERVICE = "always_on_notification_service"
    const val DEFAULT_RELAY_AUTH_POLICY = "default_relay_auth_policy"
    const val RELAY_GROUP_VIEW_MODE = "relay_group_view_mode"
    const val CONCORD_VIEW_MODE = "concord_view_mode"

    // Stores the DISABLED chat feed types (comma-joined codes) so absence = all-on and any newly
    // added type defaults enabled for accounts that customized before it existed.
    const val DISABLED_CHAT_FEEDS = "disabled_chat_feeds"

    // Same convention as DISABLED_CHAT_FEEDS but for the Home feed's event-kind groups: stores the
    // DISABLED codes so absence = all-on and any newly added group defaults enabled.
    const val DISABLED_HOME_FEED_TYPES = "disabled_home_feed_types"
    const val RELAY_AUTH_TRUST_MY_RELAYS = "relay_auth_trust_my_relays_and_venues"
    const val RELAY_AUTH_TRUST_READ_FOLLOWS = "relay_auth_trust_read_follows"
    const val RELAY_AUTH_TRUST_MESSAGE_FOLLOWS = "relay_auth_trust_message_follows"
    const val RELAY_AUTH_TRUST_MESSAGE_STRANGERS = "relay_auth_trust_message_strangers"
    const val SPLIT_NOTIFICATIONS_ENABLED = "split_notifications_enabled"
    const val SHOW_MESSAGES_IN_NOTIFICATIONS = "show_messages_in_notifications"

    // One-shot stamp: set once an account has gone through the notifications
    // Global -> Selected (Curated) migration (or was created after it shipped).
    const val NOTIF_GLOBAL_TO_CURATED_MIGRATED = "notif_global_to_curated_migrated"
    const val TOR_SETTINGS = "tor_settings"
    const val USE_PROXY = "use_proxy"
    const val PROXY_PORT = "proxy_port"
    const val LAST_READ_PER_ROUTE = "last_read_route_per_route"
    const val LOGIN_WITH_EXTERNAL_SIGNER = "login_with_external_signer"
    const val SIGNER_PACKAGE_NAME = "signer_package_name"
    const val HAS_DONATED_IN_VERSION = "has_donated_in_version"
    const val DISMISSED_POLL_NOTE_IDS = "dismissed_poll_note_ids"
    const val DISMISSED_CHANNEL_INVITES = "dismissed_channel_invites"
    const val MUTED_PUBLIC_CHATS = "muted_public_chats"
    const val VIEWED_POLL_RESULT_NOTE_IDS = "viewed_poll_result_note_ids"
    const val PENDING_ATTESTATIONS = "pending_attestations"

    // Per-account one-shot flag: false only for freshly-GENERATED accounts that
    // haven't yet backed up their secret key. Absent (defaults to true) for every
    // account logged in via an existing nsec/bunker/external signer — those already
    // hold their key elsewhere and must not be nudged.
    const val HAS_BACKED_UP_KEYS = "has_backed_up_keys"

    const val ALL_ACCOUNT_INFO = "all_saved_accounts_info"
    const val SHARED_SETTINGS = "shared_settings"
    const val LATEST_PAYMENT_TARGETS = "latestPaymentTargets"
    const val LATEST_BOLT12_OFFERS = "latestBolt12Offers"
    const val LATEST_CASHU_WALLET = "latestCashuWallet"
    const val LATEST_NUTZAP_INFO = "latestNutzapInfo"
}

object LocalPreferences : AccountSessionStore {
    private const val COMMA = ","

    private var currentAccount: String? = null
    private val savedAccounts: MutableStateFlow<List<AccountInfo>?> = MutableStateFlow(null)

    // Guards the one-time lazy population of [savedAccounts]. Without it, concurrent callers
    // of savedAccounts() (e.g. the account-load path, the always-on notification service, and
    // the orphan-dir sweep, all launched at startup) would each see a null value, run the IO
    // read in parallel, and the migration branch could double-write ALL_ACCOUNT_INFO.
    private val savedAccountsMutex = Mutex()
    private val cachedAccounts: MutableMap<String, AccountSettings?> = mutableMapOf()

    /**
     * The per-account DataStore: every non-secret setting this account has.
     *
     * Each account's store carries one [CopyOnceMigration] per group in
     * [LegacyAccountKeys.tables], lifting that group out of the account's legacy
     * encrypted SharedPreferences the first time the store is read. The copies
     * leave the legacy keys in place, so a build that reads the old location
     * still works — see [CopyOnceMigration].
     */
    private val accountSettingsStores by lazy { AccountSettingsStores(accountStores) }

    private val accountStores: AccountPreferenceStores by lazy {
        AccountPreferenceStores(
            rootFilesDir = {
                Amethyst.instance.appContext.filesDir
                    .toOkioPath()
            },
            migrations = { npub ->
                LegacyAccountKeys.tables.map { it.migration { legacySource(npub) } }
            },
        )
    }

    private fun followListStore(npub: String) = TopNavFollowListStore(accountStores.getDataStore(npub))

    /** My Fitness weekly goals, in the account's own preference store. */
    fun fitnessGoalsStore(npub: String) = FitnessGoalsStore(accountStores.getDataStore(npub))

    private fun latestEventStore(npub: String) = LatestEventCacheStore(accountStores.getDataStore(npub))

    private fun uploadSettingsStore(npub: String) = UploadSettingsStore(accountStores.getDataStore(npub))

    private fun dialogDismissalStore(npub: String) = DialogDismissalStore(accountStores.getDataStore(npub))

    private fun relayAuthStore(npub: String) = RelayAuthStore(accountStores.getDataStore(npub))

    private fun feedVisibilityStore(npub: String) = FeedVisibilityStore(accountStores.getDataStore(npub))

    private fun notificationPrefsStore(npub: String) = NotificationPrefsStore(accountStores.getDataStore(npub))

    private fun nwcBackupPrefsStore(npub: String) = NwcBackupPrefsStore(accountStores.getDataStore(npub))

    private fun composePrefsStore(npub: String) = ComposePrefsStore(accountStores.getDataStore(npub))

    private fun identityStore(npub: String) = AccountIdentityStore(accountStores.getDataStore(npub))

    private fun legacySource(npub: String): LegacyPreferenceSource = LegacySharedPreferences(encryptedPreferences(npub))

    /**
     * Whether the app has stopped mirroring into `secret_keeper_<npub>`.
     *
     * False, and deliberately so: the private key, the secrets and the identity
     * group are all still written there, so that a build rolled back to reading
     * only the legacy file still finds a complete account. Deleting the file
     * while that is true would achieve nothing — the next save recreates it —
     * so [legacyCleanup] refuses to.
     *
     * Flipping this is a release of its own, and it ends the rollback window.
     * It waits on the device pass in
     * `amethyst/plans/2026-09-23-encrypted-storage-retirement.md`.
     *
     * `internal` rather than private because the mirror is not all in this
     * file: [com.vitorpamplona.amethyst.commons.model.GeohashChatIdentityState] writes
     * the location-chat identity into its own legacy file and reads this to
     * know when to stop. Private, it would have kept writing after the flip
     * and the switch would only half work.
     */
    internal const val LEGACY_WRITES_RETIRED = false

    private val legacyCleanup: LegacyPreferenceCleanup by lazy {
        LegacyPreferenceCleanup(
            tables = LegacyAccountKeys.tables,
            accepted = LegacyAccountKeys.accepted,
            files =
                object : LegacyAccountFiles {
                    override fun source(npub: String) = legacySource(npub)

                    // Either file: once the npub one is gone, the hex one still
                    // has to be reachable or it can never be removed.
                    override fun exists(npub: String) = legacyAccountFile(npub).exists() || legacyAccountFile(geohashLegacyKey(npub)).exists()

                    override fun geohashSource(npub: String) = LegacySharedPreferences(encryptedPreferences(geohashLegacyKey(npub)))

                    override suspend fun delete(npub: String): Boolean {
                        // Clear before unlinking, as deleteAccount does: the live
                        // SharedPreferences still holds the values in memory and
                        // would write them straight back out.
                        encryptedPreferences(npub).edit(commit = true) { clear() }
                        val removedAccountFile = legacyAccountFile(npub).delete()

                        // The location-chat identity is in a SECOND file, keyed by the
                        // pubkey hex rather than the npub, because that is the key its
                        // writer passed. Nothing else would ever remove it, so it goes
                        // with the account's own file rather than being left as an
                        // orphan holding a seed forever.
                        val removedGeohashFile = deleteGeohashLegacyFile(npub)

                        return removedAccountFile || removedGeohashFile
                    }
                },
            currentStore = { npub -> accountStores.getDataStore(npub).data.first() },
            secrets =
                object : MigratedSecrets {
                    override suspend fun secrets(npub: String) = accountSecretsStore.stored(npub)

                    override suspend fun privateKey(npub: String) = accountKeyStore.stored(npub)

                    override suspend fun geohashIdentity(npub: String) = accountSecretsStore.storedGeohashIdentity(npub)
                },
            legacyWritesRetired = LEGACY_WRITES_RETIRED,
        )
    }

    /**
     * The file behind [encryptedPreferences], following the same branch it
     * does — a name taken from the other side of that `if` would have the
     * cleanup checking for, and deleting, a file that is not the one being
     * read.
     */
    private fun legacyAccountFile(npub: String): File {
        val name = if (BuildConfig.DEBUG && DEBUG_PLAINTEXT_PREFERENCES) "${DEBUG_PREFERENCES_NAME}_$npub" else EncryptedStorage.prefsFileName(npub)
        return File(prefsDirPath, "$name.xml")
    }

    /**
     * The key the location-chat identity's legacy file is named by.
     *
     * [GeohashChatIdentityState] passed `signer.pubKey` — hex — where every
     * other caller of [encryptedPreferences] passes an npub, so that material
     * sits in `secret_keeper_<hex>`, a different file from the account's own
     * `secret_keeper_<npub>`. Converting here keeps that quirk in one place.
     */
    private fun geohashLegacyKey(npub: String): String = npub.bechToBytes("npub").toHexKey()

    /**
     * Clears and unlinks `secret_keeper_<pubkey hex>`.
     *
     * Clear before unlinking, as everything else here does: the live
     * SharedPreferences still holds the values in memory and would write them
     * straight back out.
     */
    private fun deleteGeohashLegacyFile(npub: String): Boolean {
        val hex = geohashLegacyKey(npub)
        encryptedPreferences(hex).edit(commit = true) { clear() }
        return legacyAccountFile(hex).delete()
    }

    /**
     * Copies the location-chat identity out of `secret_keeper_<pubkey hex>` on
     * the first load after the upgrade.
     *
     * Eager, not lazy. [GeohashChatIdentityState] also copies on first use, but
     * only a user who opens a location chat ever reaches it — and the cleanup
     * refuses to delete an account's legacy files while that file still holds
     * an identity the current store does not. Left to the lazy path alone, a
     * user who never opens another location chat would keep both files
     * forever, which is the opposite of what the migration is for.
     *
     * Idempotent, and cheap after the first run: the store's marker short-circuits
     * it, so re-running on each load cannot overwrite a later edit and — because
     * the legacy read is a lambda — does not open `secret_keeper_<pubkey hex>`
     * either. That matters beyond speed: opening an `EncryptedSharedPreferences`
     * writes its Tink keyset, so an eager read would recreate the file on the
     * load right after the cleanup deleted it, permanently.
     */
    private suspend fun copyGeohashIdentity(npub: String) {
        accountSecretsStore.readGeohashIdentity(npub) {
            readLegacyGeohashIdentity(LegacySharedPreferences(encryptedPreferences(geohashLegacyKey(npub))))
        }
    }

    /**
     * Everything the account's DataStore holds, read in one hop.
     *
     * Loaded as a group rather than store by store because
     * [innerLoadCurrentAccountFromEncryptedStorage] is already near the JVM's
     * 64KB method limit: every suspend call inside it adds a state to the
     * generated coroutine state machine, and seven separate loads pushed it
     * over. One call, one state.
     */

    private suspend fun loadAccountStores(
        npub: String,
        legacy: SharedPreferences,
    ) = AccountStoreRecords(
        identity =
            identityStore(npub).load().orIfUnusable {
                AccountIdentity(
                    pubKeyHex = legacy.getString(PrefKeys.NOSTR_PUBKEY, null),
                    loginWithExternalSigner = legacy.getBoolean(PrefKeys.LOGIN_WITH_EXTERNAL_SIGNER, false),
                    externalSignerPackageName = legacy.getString(PrefKeys.SIGNER_PACKAGE_NAME, null),
                    localRelayServers = legacy.getStringSet(PrefKeys.LOCAL_RELAY_SERVERS, null) ?: setOf(),
                    openBackupConflictsJson = legacy.getString(PrefKeys.OPEN_BACKUP_CONFLICTS, null),
                )
            },
        followLists = migrateNotificationFilter(npub, legacy, followListStore(npub).load()),
        latestEvents = latestEventStore(npub).load(),
        uploadSettings = uploadSettingsStore(npub).load(),
        dialogDismissal = dialogDismissalStore(npub).load(),
        relayAuth = relayAuthStore(npub).load(),
        feedVisibility = feedVisibilityStore(npub).load(),
        notificationPrefs = notificationPrefsStore(npub).load(),
        nwcBackupPrefs = nwcBackupPrefsStore(npub).load(),
        composePrefs = composePrefsStore(npub).load(),
    )

    // NOT migrated to DataStore, and cannot be: DataStore is suspend-only, while
    // NotificationRelayService.isEnabled(context) is a synchronous Boolean read
    // from Service and BroadcastReceiver entry points in freshly started
    // processes (boot, watchdog, WorkManager). Making it suspend would mean the
    // restart layers could not consult it at all, and a saved OFF would be
    // missed on cold boot — the service would resurrect itself. Plain
    // SharedPreferences is the only store here that answers synchronously on
    // any thread, so this key stays on it deliberately.
    //
    // Global master switch for the always-on notification service ("Background
    // notification service"). Default ON: existing users keep current behavior, and
    // per-account participation decides who actually stays active.
    //
    // Stored in PLAIN (non-encrypted) SharedPreferences on purpose. It is a non-sensitive
    // global boolean, and — unlike encryptedPreferences(), which asserts non-main — plain
    // prefs can be read synchronously on ANY thread. The restart-layer gate
    // (NotificationRelayService.isEnabled) is synchronous and runs in fresh processes (boot
    // receiver, WorkManager), so it MUST read the persisted value without a suspend hop;
    // otherwise a saved OFF would be missed on cold boot and the service would resurrect.
    // The flow is lazily seeded from disk once (synchronous, main-safe) and is thereafter
    // the source of truth, so there is no async hydrate that could clobber a user toggle.
    private fun globalSettingsPrefs(): SharedPreferences = Amethyst.instance.appContext.getSharedPreferences("amethyst_global_settings", Context.MODE_PRIVATE)

    /**
     * Loads the global-settings prefs file into SharedPreferences' in-memory cache, off the main
     * thread, so the first synchronous read below hits memory rather than disk.
     *
     * The read itself is deliberately synchronous — see [setNotificationServiceEnabled]: an async
     * hydrate reintroduces a window where a late disk read clobbers a user's toggle. So this warms
     * the cache instead of deferring the read. Best-effort: if a main-thread reader wins the race it
     * simply pays the disk hit once, exactly as before.
     */
    fun warmGlobalSettings() {
        globalSettingsPrefs().getBoolean(PrefKeys.NOTIFICATION_SERVICE_ENABLED, true)
    }

    private val notificationServiceEnabled: MutableStateFlow<Boolean> by lazy {
        MutableStateFlow(globalSettingsPrefs().getBoolean(PrefKeys.NOTIFICATION_SERVICE_ENABLED, true))
    }

    fun notificationServiceEnabledFlow(): StateFlow<Boolean> = notificationServiceEnabled

    fun isNotificationServiceEnabled(): Boolean = notificationServiceEnabled.value

    fun setNotificationServiceEnabled(enabled: Boolean) {
        // In-memory update is the source of truth (main-safe); plain-prefs edit{} persists
        // asynchronously via apply(), also main-safe. No suspend/hydrate hop, so no window
        // where a late disk read can clobber this write.
        notificationServiceEnabled.value = enabled
        globalSettingsPrefs().edit { putBoolean(PrefKeys.NOTIFICATION_SERVICE_ENABLED, enabled) }
    }

    private fun legacyCurrentAccount(): String? = encryptedPreferences().getString(PrefKeys.CURRENT_ACCOUNT, null)

    private fun legacyAllAccountInfo(): String? = encryptedPreferences().getString(PrefKeys.ALL_ACCOUNT_INFO, null)

    override suspend fun currentAccount(): String? {
        if (currentAccount == null) {
            currentAccount =
                withContext(Dispatchers.IO) {
                    accountRoster.currentAccount(::legacyCurrentAccount, ::legacyAllAccountInfo)
                }
        }
        return currentAccount
    }

    private suspend fun updateCurrentAccount(info: AccountInfo?) {
        if (info == null) {
            currentAccount = null
            withContext(Dispatchers.IO) {
                encryptedPreferences().edit { clear() }
                accountRoster.clear()
            }
        } else if (currentAccount != info.npub) {
            currentAccount = info.npub
            if (!info.isTransient) {
                withContext(Dispatchers.IO) {
                    encryptedPreferences().edit { putString(PrefKeys.CURRENT_ACCOUNT, info.npub) }
                    accountRoster.mirrorCurrentAccount(info.npub)
                }
            }
        }
    }

    private suspend fun savedAccounts(): List<AccountInfo> {
        // Fast path: already populated, no lock needed.
        savedAccounts.value?.let { return it }

        return savedAccountsMutex.withLock {
            // Re-check under the lock: another coroutine may have populated it while we waited.
            savedAccounts.value ?: loadSavedAccountsFromStorage().also { savedAccounts.emit(it) }
        }
    }

    private suspend fun loadSavedAccountsFromStorage(): List<AccountInfo> =
        withContext(Dispatchers.IO) {
            with(encryptedPreferences()) {
                val newSystemOfAccounts =
                    (accountRoster.allAccountInfoJson(::legacyCurrentAccount, ::legacyAllAccountInfo) ?: "[]").let {
                        JsonMapper.fromJson<List<AccountInfo>>(it)
                    }

                if (!newSystemOfAccounts.isNullOrEmpty()) {
                    // How many accounts are in play is the first thing you need when reading any
                    // boot log: nearly every per-account subsystem below multiplies by this number.
                    Log.i("LocalPreferences") { "Found ${newSystemOfAccounts.size} saved account(s)" }
                    newSystemOfAccounts
                } else {
                    val oldAccounts = getString(PrefKeys.SAVED_ACCOUNTS, null)?.split(COMMA) ?: listOf()

                    val migrated =
                        oldAccounts.map { npub ->
                            AccountInfo(
                                npub,
                                encryptedPreferences(npub).getBoolean(PrefKeys.LOGIN_WITH_EXTERNAL_SIGNER, false),
                                (encryptedPreferences(npub).getString(PrefKeys.NOSTR_PRIVKEY, "") ?: "").isNotBlank(),
                                false,
                            )
                        }

                    val json = JsonMapper.toJson(migrated)
                    edit {
                        putString(PrefKeys.ALL_ACCOUNT_INFO, json)
                    }
                    // Mirrored as well, exactly as updateSavedAccounts does. The
                    // roster's own copy has already run by this point, against an
                    // ALL_ACCOUNT_INFO that did not exist yet, and its marker is
                    // set — so without this the roster store stays permanently
                    // empty for these installs and they open as a fresh install
                    // the moment the legacy write goes.
                    accountRoster.mirrorAllAccountInfoJson(json)

                    migrated
                }
            }
        }

    override fun accountsFlow(): StateFlow<List<AccountInfo>?> = savedAccounts

    private suspend fun updateSavedAccounts(accounts: List<AccountInfo>) =
        withContext(Dispatchers.IO) {
            // .value, not the flow: StateFlow does not override equals, so
            // comparing the holder to a List was unconditionally true and every
            // call rewrote both stores.
            if (savedAccounts.value != accounts) {
                savedAccounts.emit(accounts)

                val json = JsonMapper.toJson(accounts.filter { !it.isTransient })
                encryptedPreferences().edit { putString(PrefKeys.ALL_ACCOUNT_INFO, json) }
                accountRoster.mirrorAllAccountInfoJson(json)
            }
        }

    private val prefsDirPath: String
        get() = "${Amethyst.instance.appContext.filesDir.parent}/shared_prefs/"

    private suspend fun addAccount(accInfo: AccountInfo) {
        val accounts = savedAccounts().filter { it.npub != accInfo.npub }.plus(accInfo)
        updateSavedAccounts(accounts)
    }

    private suspend fun setCurrentAccount(accountSettings: AccountSettings) {
        val npub = accountSettings.keyPair.pubKey.toNpub()
        val accInfo =
            AccountInfo(
                npub,
                accountSettings.isWriteable(),
                accountSettings.externalSignerPackageName != null,
                accountSettings.transientAccount,
                loggedInWithRemoteSigner = accountSettings.usesRemoteSigner(),
            )
        updateCurrentAccount(accInfo)
        addAccount(accInfo)
    }

    override suspend fun switchToAccount(accountInfo: AccountInfo) = updateCurrentAccount(accountInfo)

    /** Removes the account from the app level shared preferences */
    private suspend fun removeAccount(accountInfo: AccountInfo) {
        updateSavedAccounts(savedAccounts().filter { it.npub != accountInfo.npub })
    }

    /** Deletes the npub-specific shared preference file */
    private suspend fun deleteUserPreferenceFile(npub: String) {
        withContext(Dispatchers.IO) {
            val prefsDir = File(prefsDirPath)
            prefsDir.list()?.forEach {
                if (it.contains(npub) && !File(prefsDir, it).delete()) {
                    Log.w("LocalPreferences") { "Failed to delete preference file: $it" }
                }
            }
        }
    }

    private fun encryptedPreferences(npub: String? = null): SharedPreferences =
        if (BuildConfig.DEBUG && DEBUG_PLAINTEXT_PREFERENCES) {
            val preferenceFile =
                if (npub == null) DEBUG_PREFERENCES_NAME else "${DEBUG_PREFERENCES_NAME}_$npub"
            Amethyst.instance.appContext.getSharedPreferences(preferenceFile, Context.MODE_PRIVATE)
        } else {
            Amethyst.instance.encryptedStorage(npub)
        }

    /**
     * Clears the preferences for a given npub, deletes the preferences xml file, and switches the
     * user to the first account in the list if it exists
     *
     * We need to use `commit()` to write changes to disk and release the file lock so that it can be
     * deleted. If we use `apply()` there is a race condition and the file will probably not be
     * deleted
     */
    @Suppress("ApplySharedPref")
    override suspend fun deleteAccount(accountInfo: AccountInfo) {
        Log.d("LocalPreferences") { "Saving to encrypted storage updatePrefsForLogout ${accountInfo.npub}" }
        withContext(Dispatchers.IO) {
            // Drop the in-memory copy as well; otherwise re-adding the same account later
            // would resurrect the deleted settings from this cache.
            mutex.withLock { cachedAccounts.remove(accountInfo.npub) }
            encryptedPreferences(accountInfo.npub).edit(commit = true) { clear() }
            // The location-chat identity's own file, keyed by pubkey hex. Without
            // this the anonymous device seed outlives the account that owned it
            // and comes back if the same npub is re-added — the opposite of what
            // an unlinkable per-cell identity is for.
            deleteGeohashLegacyFile(accountInfo.npub)
            accountKeyStore.delete(accountInfo.npub)
            accountSecretsStore.delete(accountInfo.npub)
            // The account's plain DataStore, which deleteUserPreferenceFile cannot
            // reach: that sweeps shared_prefs/, this lives in filesDir/datastore/.
            // Left behind it would keep the deleted account's pubkey, signer and
            // cached events on disk — and re-adding the same npub would find a
            // live identity there and resurrect the account that was just deleted.
            accountStores.removeAccount(accountInfo.npub)
            removeAccount(accountInfo)
            deleteUserPreferenceFile(accountInfo.npub)

            if (savedAccounts().isEmpty()) {
                updateCurrentAccount(null)
            } else if (currentAccount() == accountInfo.npub) {
                updateCurrentAccount(savedAccounts().elementAt(0))
            }
        }
    }

    /**
     * Make [accountSettings] the current account, persisting + caching it. Returns the settings that
     * actually became current — normally [accountSettings] itself, but see the downgrade guard below.
     */
    override suspend fun setDefaultAccount(accountSettings: AccountSettings): AccountSettings {
        val npub = accountSettings.keyPair.pubKey.toNpub()

        // Downgrade guard: adding a read-only npub for a pubkey we already hold a SIGNING account for
        // must not clobber that account. Accounts dedup by npub, so saving fresh read-only settings
        // here would overwrite the signing account's per-npub file — wiping its cached follow/relay/
        // mute lists and flipping hasPrivKey off, which silently disables its push notifications. A
        // signing account already does everything the read-only one would, so keep it and just make
        // it current instead of degrading it.
        if (!accountSettings.isWriteable()) {
            val existing = loadAccountConfigFromEncryptedStorage(npub)
            if (existing != null && existing.isWriteable()) {
                setCurrentAccount(existing)
                return existing
            }
        }

        // Save the per-npub file before emitting onto the savedAccounts flow.
        // Otherwise a collector (e.g. AlwaysOnNotificationServiceManager) can race in
        // and call loadAccountConfigFromEncryptedStorage(npub) before NOSTR_PUBKEY is
        // written, get null back, and poison `cachedAccounts[npub] = null` for the
        // rest of the session — making every later switch to this account land on
        // LoggedOff instead of LoggedIn.
        saveToEncryptedStorage(accountSettings)
        mutex.withLock { cachedAccounts.put(npub, accountSettings) }
        setCurrentAccount(accountSettings)
        return accountSettings
    }

    override suspend fun allSavedAccounts(): List<AccountInfo> = savedAccounts()

    suspend fun saveToEncryptedStorage(settings: AccountSettings) {
        Log.d("LocalPreferences", "Saving to encrypted storage")
        if (!settings.transientAccount) {
            withContext(Dispatchers.IO) {
                val prefs = encryptedPreferences(settings.keyPair.pubKey.toNpub())
                prefs.edit {
                    putBoolean(PrefKeys.LOGIN_WITH_EXTERNAL_SIGNER, settings.externalSignerPackageName != null)
                    if (settings.externalSignerPackageName != null) {
                        remove(PrefKeys.NOSTR_PRIVKEY)
                        putString(PrefKeys.SIGNER_PACKAGE_NAME, settings.externalSignerPackageName)
                    } else {
                        remove(PrefKeys.SIGNER_PACKAGE_NAME)
                        settings.keyPair.privKey?.let { putString(PrefKeys.NOSTR_PRIVKEY, it.toHexKey()) }
                    }
                    settings.keyPair.pubKey.let { putString(PrefKeys.NOSTR_PUBKEY, it.toHexKey()) }

                    putBoolean(PrefKeys.NIP46_SIGNER_ENABLED, settings.nip46SignerEnabled.value)
                    putString(PrefKeys.NIP46_BUNKER_SECRET, settings.nip46BunkerSecret.value)
                    putString(PrefKeys.NIP46_TRANSPORT_KEY, settings.nip46TransportKey.value)
                    putStringSet(PrefKeys.NIP46_SEEN_IDS, settings.nip46SeenRequestIds.value)

                    // top-nav filters now live in the account's DataStore; written below,
                    // outside this edit block, because that write is suspend.

                    val walletEntries = settings.nwcWallets.value.mapNotNull { it.denormalize() }
                    if (walletEntries.isNotEmpty()) {
                        putString(PrefKeys.NWC_WALLETS, JsonMapper.toJson(walletEntries))
                    } else {
                        remove(PrefKeys.NWC_WALLETS)
                    }

                    val debitEntries = settings.clinkDebitWallets.value.map { it.denormalize() }
                    if (debitEntries.isNotEmpty()) {
                        putString(PrefKeys.CLINK_DEBIT_WALLETS, JsonMapper.toJson(debitEntries))
                    } else {
                        remove(PrefKeys.CLINK_DEBIT_WALLETS)
                    }

                    settings.defaultPaymentSourceId.value?.let {
                        putString(PrefKeys.DEFAULT_PAYMENT_SOURCE_ID, it)
                    } ?: remove(PrefKeys.DEFAULT_PAYMENT_SOURCE_ID)
                    // Legacy NWC-only default key is superseded by DEFAULT_PAYMENT_SOURCE_ID.
                    remove(PrefKeys.DEFAULT_NWC_WALLET_ID)

                    // Remove legacy key after migration
                    remove(PrefKeys.ZAP_PAYMENT_REQUEST_SERVER)

                    // The undecided conflicts themselves, not just the backups they hold back.
                    // Without these the card vanishes on the next launch and the user never
                    // answers the question the backup is still waiting on.
                    val openConflicts = settings.openBackupConflicts()
                    if (openConflicts.isEmpty()) {
                        remove(PrefKeys.OPEN_BACKUP_CONFLICTS)
                    } else {
                        putString(PrefKeys.OPEN_BACKUP_CONFLICTS, BackupConflictStorage.encode(openConflicts))
                    }

                    if (settings.localRelayServers.value.isNotEmpty()) {
                        putStringSet(PrefKeys.LOCAL_RELAY_SERVERS, settings.localRelayServers.value)
                    } else {
                        remove(PrefKeys.LOCAL_RELAY_SERVERS)
                    }

                    // Any account that reaches a save has its notification filter in its
                    // post-split meaning, so stamp it as migrated. This keeps the one-shot
                    // Global -> Selected rewrite from ever touching it again and preserves a
                    // deliberate raw-Global choice (including on brand-new accounts).
                    putBoolean(PrefKeys.NOTIF_GLOBAL_TO_CURATED_MIGRATED, true)

                    // migrating from previous design
                    remove(PrefKeys.USE_PROXY)
                    remove(PrefKeys.PROXY_PORT)

                    val regularMap =
                        settings.lastReadPerRoute.value.mapValues {
                            it.value.value
                        }

                    putString(
                        PrefKeys.LAST_READ_PER_ROUTE,
                        JsonMapper.toJson(regularMap),
                    )

                    putString(
                        PrefKeys.PENDING_ATTESTATIONS,
                        JsonMapper.toJson(settings.pendingAttestations.value),
                    )
                }

                // Mirrored into the key store after the legacy write, not
                // instead of it: both stores carry the key during the
                // transition so a rollback still loads the account.
                accountSecretsStore.mirror(
                    npub = settings.keyPair.pubKey.toNpub(),
                    value = settings.toAccountSecrets(),
                )
                accountKeyStore.mirrorSave(
                    npub = settings.keyPair.pubKey.toNpub(),
                    usesExternalSigner = settings.externalSignerPackageName != null,
                    privKeyHex = settings.keyPair.privKey?.toHexKey(),
                )
            }
            // Mirrored, not moved: NOSTR_PUBKEY is the one key whose loss empties
            // the app, so the legacy write above stays until a release has
            // proved this one — see [EncryptedStorage].
            accountSettingsStores.save(settings)
        }
        Log.d("LocalPreferences", "Saved to encrypted storage")
    }

    override suspend fun loadAccountConfigFromEncryptedStorage(): AccountSettings? = currentAccount()?.let { loadAccountConfigFromEncryptedStorage(it) }

    /**
     * The UI settings as the global `secret_keeper` file holds them.
     *
     * A migration source only: [UiSharedPreferences] owns these now and writes
     * them to its own DataStore, which carries a one-shot copy out of this blob
     * for installs that predate it. Nothing writes here any more — the matching
     * `saveSharedSettings` was removed once it had no callers — but the read
     * stays for good, like every other legacy reader; see [EncryptedStorage].
     */
    fun loadSharedSettings(prefs: SharedPreferences = encryptedPreferences()): UiSettings? {
        Log.d("LocalPreferences", "Load shared settings")
        with(prefs) {
            return try {
                getString(PrefKeys.SHARED_SETTINGS, "{}")?.let {
                    JsonMapper.fromJson<UiSettings>(it)
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Log.w(
                    "LocalPreferences",
                    "Unable to decode shared preferences: ${getString(PrefKeys.SHARED_SETTINGS, null)}",
                    e,
                )
                null
            }
        }
    }

    // Reactive, per-account cache of the "has backed up keys" flag so the home-screen
    // nudge updates the instant the user backs up or dismisses it, without a full
    // account reload. Keyed by npub. Seeded lazily from encrypted storage.
    private val hasBackedUpKeysFlows: MutableMap<String, MutableStateFlow<Boolean>> = mutableMapOf()
    private val hasBackedUpKeysMutex = Mutex()

    private suspend fun hasBackedUpKeysFlow(npub: String): MutableStateFlow<Boolean> =
        hasBackedUpKeysMutex.withLock {
            hasBackedUpKeysFlows.getOrPut(npub) {
                // Absent reads as true in both stores, so a store that cannot be
                // read leaves the nudge off rather than showing it to everyone.
                val stored = withContext(Dispatchers.IO) { identityStore(npub).hasBackedUpKeys() }
                MutableStateFlow(stored)
            }
        }

    /** Reactive flag: true (default) unless a freshly-generated account still needs to back up its key. */
    suspend fun hasBackedUpKeys(npub: String): MutableStateFlow<Boolean> = hasBackedUpKeysFlow(npub)

    override suspend fun setHasBackedUpKeys(
        value: Boolean,
        npub: String,
    ) {
        withContext(Dispatchers.IO) {
            // Legacy write kept alongside the new one, as for the rest of the
            // identity group — see [EncryptedStorage].
            encryptedPreferences(npub).edit { putBoolean(PrefKeys.HAS_BACKED_UP_KEYS, value) }
            identityStore(npub).setHasBackedUpKeys(value)
        }
        hasBackedUpKeysFlow(npub).value = value
    }

    val mutex = Mutex()

    override suspend fun loadAccountConfigFromEncryptedStorage(npub: String): AccountSettings? {
        // if already loaded, return right away
        cachedAccounts[npub]?.let { return it }

        return withContext(Dispatchers.IO) {
            var loadedHere = false

            val accountSettings =
                mutex.withLock {
                    cachedAccounts[npub]?.let { return@withLock it }

                    val loaded = innerLoadCurrentAccountFromEncryptedStorage(npub)

                    // Only cache successful loads. Caching null would leave the account
                    // permanently unreachable for the rest of the session if a reader
                    // raced in before the per-npub file finished being written.
                    if (loaded != null) {
                        cachedAccounts.put(npub, loaded)
                        loadedHere = true
                    }

                    loaded
                }

            // Outside the lock, and only for the call that did the loading.
            // Verifying decrypts the whole legacy file and reads three stores,
            // while `mutex` serialises every account load — under the lock, each
            // account on a multi-account cold start would wait for the previous
            // one's full cleanup pass. Nothing here feeds the load.
            if (loadedHere) {
                // Before the cleanup, which refuses to delete this account's files
                // while the location-chat identity has not been copied. Here rather
                // than inside the loader for the reason [AccountStoreData] gives:
                // that method is at the JVM's 64KB limit and one more suspend call
                // inside it does not fit.
                // Never fatal, like every other legacy step here: this runs on
                // the account-load path, and an EncryptedSharedPreferences that
                // cannot be opened must not take the whole load — and with it the
                // per-account loops in the notification consumers — down with it.
                try {
                    copyGeohashIdentity(npub)
                } catch (e: Exception) {
                    Log.w("LocalPreferences", "Could not copy the location-chat identity for $npub", e)
                }
                legacyCleanup.deleteIfVerified(npub)
            }

            accountSettings
        }
    }

    private suspend fun innerLoadCurrentAccountFromEncryptedStorage(npub: String): AccountSettings? {
        Log.d("LocalPreferences") { "Load account from file $npub" }
        val startedAtMs = TimeUtils.nowMillis()
        val result =
            withContext(Dispatchers.IO) {
                return@withContext with(encryptedPreferences(npub)) {
                    Log.d("LocalPreferences") { "Load account from file $npub - opened file" }
                    // Every store this account has, read in one hop — including
                    // the identity the rest of this function is derived from, so
                    // that read does not cost its own state in the generated
                    // coroutine state machine (see [AccountStoreData]).
                    //
                    // Keyed by the npub handed in, which is the npub the save side
                    // writes under and the name of the legacy file just opened. The
                    // identity falls back to that file when its store cannot
                    // produce a pubkey: an account without one vanishes from the
                    // app entirely, private key intact.
                    val stores = loadAccountStores(npub, this)
                    val identity = stores.identity
                    val pubKey = identity.pubKeyHex ?: return@with null
                    val privKey =
                        accountKeyStore.read(
                            npub = pubKey.hexToByteArray().toNpub(),
                            legacyValue = getString(PrefKeys.NOSTR_PRIVKEY, null),
                        )
                    val externalSignerPackageName = identity.externalSignerPackageName ?: if (identity.loginWithExternalSigner) "com.greenart7c3.nostrsigner" else null

                    val keyPair = KeyPair(privKey = privKey?.hexToByteArray(), pubKey = pubKey.hexToByteArray())

                    // The npub handed in names the file just read, and the save
                    // side writes every store under the npub derived from the
                    // pubkey inside it, so the two are the same by construction.
                    // Say so if they ever are not: it would mean this load is
                    // reading stores that a save never wrote.
                    if (keyPair.pubKey.toNpub() != npub) {
                        Log.e("LocalPreferences", "Account file $npub holds pubkey ${keyPair.pubKey.toNpub()}; its stores were read under the file's name", null)
                    }

                    Log.d("LocalPreferences") { "Load account from file $npub - keys ready" }

                    // The secrets that used to live in this file now come from the
                    // encrypted DataStore, falling back to what is still here.
                    val secrets =
                        accountSecretsStore.read(
                            npub = keyPair.pubKey.toNpub(),
                            // Through the shared reader, so the loader and the check
                            // that gates deleting this file read the same keys.
                            legacy = readLegacyAccountSecrets(LegacySharedPreferences(this)),
                        )

                    return@with AccountSettingsSource(
                        keyPair = keyPair,
                        externalSignerPackageName = externalSignerPackageName,
                        stores = stores,
                        secrets = secrets,
                        pendingAttestationsJson = getString(PrefKeys.PENDING_ATTESTATIONS, null),
                        lastReadPerRouteJson = getString(PrefKeys.LAST_READ_PER_ROUTE, null),
                        cashuCounters = CashuPreferences.forAccount(keyPair.pubKey.toNpub()),
                    ).toAccountSettings()
                }
            }
        // Milestone with its cost attached. Decrypting and parsing one account's settings is one of
        // the most expensive things a cold start does (it resolves a fan of backup events), it runs
        // once per account, and "which account was slow" is the first question when a boot drags.
        // The six intermediate steps above stay at DEBUG.
        Log.i("LocalPreferences") { "Loaded account $npub in ${TimeUtils.nowMillis() - startedAtMs}ms" }
        return result
    }

    private fun parseTopFilterOrDefault(
        value: String?,
        default: TopFilter,
    ): TopFilter {
        if (value.isNullOrEmpty() || value == "null") return default
        return try {
            JsonMapper.fromJson<TopFilter>(value)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Log.w("LocalPreferences", "Error Decoding TopFilter from Preferences", e)
            default
        }
    }

    /**
     * One-shot migration of the notifications filter.
     *
     * The notifications "Global" mode was split into a raw [TopFilter.Global]
     * (every event that p-tags the user) and a curated [TopFilter.Selected].
     * Existing users who had selected the old, curated "Global" keep a value
     * that now deserializes to the much-more-permissive raw Global. Move them to
     * [TopFilter.Selected] exactly once, then stamp the account so a later,
     * deliberate raw-Global choice is never reverted. Accounts created after the
     * split are stamped at save time, so they are never touched here.
     */
    private suspend fun migrateNotificationFilter(
        npub: String,
        legacy: SharedPreferences,
        filters: Map<FollowListSlot, TopFilter>,
    ): Map<FollowListSlot, TopFilter> {
        if (legacy.getBoolean(PrefKeys.NOTIF_GLOBAL_TO_CURATED_MIGRATED, false)) return filters

        val current = filters.getValue(FollowListSlot.NOTIFICATION)
        val migrated = if (current is TopFilter.Global) TopFilter.Selected else current

        // Into the store the loader reads, not the legacy key it no longer does.
        // Writing it to the legacy file and stamping anyway left the account on
        // raw Global for good: the stamp survives, the corrected value does not,
        // and the next launch reads Global back out of the DataStore.
        if (migrated !== current) followListStore(npub).save(FollowListSlot.NOTIFICATION, migrated)

        // Stamped only once the value is actually stored, so a failed write
        // means the migration runs again rather than being lost.
        legacy.edit { putBoolean(PrefKeys.NOTIF_GLOBAL_TO_CURATED_MIGRATED, true) }

        return if (migrated === current) filters else filters + (FollowListSlot.NOTIFICATION to migrated)
    }

    fun SharedPreferences.Editor.putOrRemove(
        key: String,
        event: Any?,
    ) {
        if (event != null) {
            putString(key, JsonMapper.toJson(event))
        } else {
            remove(key)
        }
    }

    fun SharedPreferences.Editor.putOrRemove(
        key: String,
        event: Event?,
    ) {
        if (event != null) {
            putString(key, OptimizedJsonMapper.toJson(event))
        } else {
            remove(key)
        }
    }

    fun SharedPreferences.Editor.putOrRemove(
        key: String,
        nwc: Nip47WalletConnect.Nip47URI?,
    ) {
        if (nwc != null) {
            putString(key, JsonMapper.toJson(nwc))
        } else {
            remove(key)
        }
    }
}
