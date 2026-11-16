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

import android.content.ComponentCallbacks2
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import androidx.security.crypto.EncryptedSharedPreferences
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import com.vitorpamplona.amethyst.commons.account.AccountCacheState
import com.vitorpamplona.amethyst.commons.account.AccountSessionHooks
import com.vitorpamplona.amethyst.commons.account.AccountSessionManager
import com.vitorpamplona.amethyst.commons.account.AccountState
import com.vitorpamplona.amethyst.commons.browser.BrowserHistoryRegistry
import com.vitorpamplona.amethyst.commons.browser.BrowserIconRegistry
import com.vitorpamplona.amethyst.commons.connectedApps.DataStoreNostrSignerPermissionStore
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.DataStoreNip46ClientStore
import com.vitorpamplona.amethyst.commons.cordn.KeyStoreCordnBlobCipher
import com.vitorpamplona.amethyst.commons.domain.nip46.RemoteSignerAuthPrompts
import com.vitorpamplona.amethyst.commons.favorites.FavoriteAppsRegistry
import com.vitorpamplona.amethyst.commons.frontpage.DataStoreFrontPageInstallStore
import com.vitorpamplona.amethyst.commons.frontpage.DataStoreFrontPageModelMemory
import com.vitorpamplona.amethyst.commons.frontpage.DataStoreFrontPageScheduleStore
import com.vitorpamplona.amethyst.commons.frontpage.DataStoreObserverPrefsStore
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageEditionStore
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageInstallInfo
import com.vitorpamplona.amethyst.commons.frontpage.FrontPagePrinters
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageScheduleWatcher
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.UiSettings
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.MemoryTrimmingService
import com.vitorpamplona.amethyst.commons.model.nip03Timestamp.BitcoinExplorerEndpoint
import com.vitorpamplona.amethyst.commons.model.nip03Timestamp.IncomingOtsEventVerifier
import com.vitorpamplona.amethyst.commons.model.nip03Timestamp.TorAwareOkHttpOtsResolverBuilder
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.CalendarReminderScanner
import com.vitorpamplona.amethyst.commons.model.preferences.AppPreferenceStores
import com.vitorpamplona.amethyst.commons.model.preferences.BuzzAttestationStore
import com.vitorpamplona.amethyst.commons.model.preferences.BuzzChannelStarStore
import com.vitorpamplona.amethyst.commons.model.preferences.BuzzWorkspaceStore
import com.vitorpamplona.amethyst.commons.model.preferences.CALENDAR_REMINDER_LOG_STORE
import com.vitorpamplona.amethyst.commons.model.preferences.CALENDAR_REMINDER_SETTINGS_STORE
import com.vitorpamplona.amethyst.commons.model.preferences.ConcordDirectInviteDeclineStore
import com.vitorpamplona.amethyst.commons.model.preferences.DrawerSectionCollapsePreferences
import com.vitorpamplona.amethyst.commons.model.preferences.DrssListenRateStore
import com.vitorpamplona.amethyst.commons.model.preferences.NamecoinSettingsStore
import com.vitorpamplona.amethyst.commons.model.preferences.NotificationPanelCollapsePreferences
import com.vitorpamplona.amethyst.commons.model.preferences.NowPlayingSettingsStore
import com.vitorpamplona.amethyst.commons.model.preferences.OtsSettingsStore
import com.vitorpamplona.amethyst.commons.model.preferences.PaneWidthPreferences
import com.vitorpamplona.amethyst.commons.model.preferences.RelayGroupAdminCacheStore
import com.vitorpamplona.amethyst.commons.model.preferences.RelayGroupDeletionStore
import com.vitorpamplona.amethyst.commons.model.preferences.TorSettingsStore
import com.vitorpamplona.amethyst.commons.model.preferences.UiSettingsStore
import com.vitorpamplona.amethyst.commons.napplet.permissions.DataStoreNappletPermissionStore
import com.vitorpamplona.amethyst.commons.napplet.permissions.NappletPermissionLedger
import com.vitorpamplona.amethyst.commons.nests.room.activity.NestBridge
import com.vitorpamplona.amethyst.commons.privacylock.DataStorePrivacyLockSettings
import com.vitorpamplona.amethyst.commons.relayClient.BlockedRelayFilteringClient
import com.vitorpamplona.amethyst.commons.relayClient.CacheClientConnector
import com.vitorpamplona.amethyst.commons.relayClient.RelayProxyClientConnector
import com.vitorpamplona.amethyst.commons.relayClient.auth.AuthCoordinator
import com.vitorpamplona.amethyst.commons.relayClient.diagnostics.BootRelayDiagnostics
import com.vitorpamplona.amethyst.commons.relayClient.diagnostics.relayFrameDecoder
import com.vitorpamplona.amethyst.commons.relayClient.event.EventFinderQueryState
import com.vitorpamplona.amethyst.commons.relayClient.notify.NotifyCoordinator
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.RelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.AccountSubscriptionRegistry
import com.vitorpamplona.amethyst.commons.relayClient.speedLogger.RelaySpeedLogger
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.UntaggedFilterWarningClient
import com.vitorpamplona.amethyst.commons.relayClient.user.UserFinderQueryState
import com.vitorpamplona.amethyst.commons.relays.health.FileRelayHealthPersistence
import com.vitorpamplona.amethyst.commons.relays.health.RelayLatencyMonitor
import com.vitorpamplona.amethyst.commons.relays.health.TorCircuitHealthTracker
import com.vitorpamplona.amethyst.commons.relays.nip11RelayInfo.Nip11CachedRetriever
import com.vitorpamplona.amethyst.commons.richtext.CachedAsciiDocToMarkdown
import com.vitorpamplona.amethyst.commons.richtext.CachedRichTextParser
import com.vitorpamplona.amethyst.commons.robohash.CachedRobohash
import com.vitorpamplona.amethyst.commons.routstr.DataStoreRoutstrPurchaseStore
import com.vitorpamplona.amethyst.commons.routstr.RoutstrService
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPostStore
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPostWorkGate
import com.vitorpamplona.amethyst.commons.service.call.CallSessionBridge
import com.vitorpamplona.amethyst.commons.service.connectivity.ConnectivityStatus
import com.vitorpamplona.amethyst.commons.service.crashreports.CrashReportCache
import com.vitorpamplona.amethyst.commons.service.crashreports.ReportAssembler
import com.vitorpamplona.amethyst.commons.service.crashreports.UnexpectedCrashSaver
import com.vitorpamplona.amethyst.commons.service.georelay.GeoRelayCsvLoader
import com.vitorpamplona.amethyst.commons.service.georelay.GeohashRelays
import com.vitorpamplona.amethyst.commons.service.http.BlossomReadAuthInterceptor
import com.vitorpamplona.amethyst.commons.service.http.BlossomReadAuthTokenProvider
import com.vitorpamplona.amethyst.commons.service.http.DualHttpClientManager
import com.vitorpamplona.amethyst.commons.service.http.DualHttpClientManagerForRelays
import com.vitorpamplona.amethyst.commons.service.http.EncryptionKeyCache
import com.vitorpamplona.amethyst.commons.service.http.LocalBlossomMediaCallFactory
import com.vitorpamplona.amethyst.commons.service.http.OnionLocationCache
import com.vitorpamplona.amethyst.commons.service.http.RoleBasedHttpClientBuilder
import com.vitorpamplona.amethyst.commons.service.images.ImageDiskCacheReconciler
import com.vitorpamplona.amethyst.commons.service.lnurl.OkHttpLnurlEndpointResolver
import com.vitorpamplona.amethyst.commons.service.namecoin.NamecoinServices
import com.vitorpamplona.amethyst.commons.service.pow.PoWJobStore
import com.vitorpamplona.amethyst.commons.service.pow.PoWPublishQueue
import com.vitorpamplona.amethyst.commons.service.pow.PowJobRestorer
import com.vitorpamplona.amethyst.commons.service.pow.platformMinerWorkers
import com.vitorpamplona.amethyst.commons.service.resourceusage.BatteryDrainSampler
import com.vitorpamplona.amethyst.commons.service.resourceusage.ForegroundTimeIntegrator
import com.vitorpamplona.amethyst.commons.service.resourceusage.HttpUsageMeter
import com.vitorpamplona.amethyst.commons.service.resourceusage.RadioBurstEstimator
import com.vitorpamplona.amethyst.commons.service.resourceusage.RefCountedSession
import com.vitorpamplona.amethyst.commons.service.resourceusage.RelayConnectionTimeIntegrator
import com.vitorpamplona.amethyst.commons.service.resourceusage.RelayUsageListener
import com.vitorpamplona.amethyst.commons.service.resourceusage.ResourceUsageAccountant
import com.vitorpamplona.amethyst.commons.service.resourceusage.ResourceUsageStore
import com.vitorpamplona.amethyst.commons.service.resourceusage.ScreenTimeIntegrator
import com.vitorpamplona.amethyst.commons.service.resourceusage.SessionTimeIntegrator
import com.vitorpamplona.amethyst.commons.service.resourceusage.UsageCountingInterceptor
import com.vitorpamplona.amethyst.commons.service.resourceusage.UsageKeys
import com.vitorpamplona.amethyst.commons.service.upload.BlossomClient
import com.vitorpamplona.amethyst.commons.service.upload.blossom.BlossomMirrorQueue
import com.vitorpamplona.amethyst.commons.service.upload.blossom.bud10.BlossomServerResolver
import com.vitorpamplona.amethyst.commons.service.upload.blossom.bud10.LocalBlossomCacheProbe
import com.vitorpamplona.amethyst.commons.state.UiSettingsState
import com.vitorpamplona.amethyst.commons.tor.AccountsTorStateConnector
import com.vitorpamplona.amethyst.commons.tor.TorRelayState
import com.vitorpamplona.amethyst.commons.tor.TorSettings
import com.vitorpamplona.amethyst.connectedApps.consent.Nip46ConsentBridge
import com.vitorpamplona.amethyst.model.accountsCache.defaultMarmotStreamTransport
import com.vitorpamplona.amethyst.model.nip60Cashu.CashuPreferences
import com.vitorpamplona.amethyst.model.preferences.UiSharedPreferences
import com.vitorpamplona.amethyst.service.calendar.CalendarReminderWorker
import com.vitorpamplona.amethyst.service.calendar.calendarReminderLogMigrations
import com.vitorpamplona.amethyst.service.calendar.calendarReminderSettings
import com.vitorpamplona.amethyst.service.calendar.calendarReminderSettingsMigrations
import com.vitorpamplona.amethyst.service.cast.CastRegistry
import com.vitorpamplona.amethyst.service.connectivity.ConnectivityManager
import com.vitorpamplona.amethyst.service.frontpage.AndroidFrontPageNotifier
import com.vitorpamplona.amethyst.service.frontpage.AndroidFrontPagePrintHost
import com.vitorpamplona.amethyst.service.frontpage.AndroidFrontPageScheduler
import com.vitorpamplona.amethyst.service.images.ImageCacheFactory
import com.vitorpamplona.amethyst.service.images.ImageLoaderSetup
import com.vitorpamplona.amethyst.service.images.ThumbnailDiskCache
import com.vitorpamplona.amethyst.service.location.LocationState
import com.vitorpamplona.amethyst.service.notifications.AlwaysOnNotificationServiceManager
import com.vitorpamplona.amethyst.service.notifications.ConversationShortcuts
import com.vitorpamplona.amethyst.service.notifications.NotificationDispatcher
import com.vitorpamplona.amethyst.service.notifications.NwcPaymentNotificationWatcher
import com.vitorpamplona.amethyst.service.notifications.PokeyReceiver
import com.vitorpamplona.amethyst.service.nowPlaying.NowPlayingStatusCoordinator
import com.vitorpamplona.amethyst.service.nowPlaying.OtherAppsNowPlayingService
import com.vitorpamplona.amethyst.service.payments.PayToAppAvailability
import com.vitorpamplona.amethyst.service.playback.diskCache.VideoCache
import com.vitorpamplona.amethyst.service.playback.diskCache.VideoCacheFactory
import com.vitorpamplona.amethyst.service.playback.diskCache.VideoPrefetcher
import com.vitorpamplona.amethyst.service.playback.pip.BackgroundMedia
import com.vitorpamplona.amethyst.service.playback.playerPool.mediaDataSourceFactory
import com.vitorpamplona.amethyst.service.playback.service.PlaybackServiceClient
import com.vitorpamplona.amethyst.service.pow.PowMiningForegroundService
import com.vitorpamplona.amethyst.service.resourceusage.ForegroundTracker
import com.vitorpamplona.amethyst.service.resourceusage.MeteringNostrSigner
import com.vitorpamplona.amethyst.service.resourceusage.ProcessCpuSampler
import com.vitorpamplona.amethyst.service.safeCacheDir
import com.vitorpamplona.amethyst.service.scheduledposts.ScheduledPostWorker
import com.vitorpamplona.amethyst.service.uploads.blossom.BlossomSyncForegroundService
import com.vitorpamplona.amethyst.service.uploads.nip95.Nip95CacheFactory
import com.vitorpamplona.amethyst.service.wot.TrustNetworkSyncWorker
import com.vitorpamplona.amethyst.ui.resourceCacheInit
import com.vitorpamplona.amethyst.ui.tor.TorManager
import com.vitorpamplona.amethyst.ui.tor.TorProgressNotifier
import com.vitorpamplona.amethyst.ui.tor.TorService
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.RelayLogger
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.RelayOfflineTracker
import com.vitorpamplona.quartz.nip01Core.relay.client.limits.RelayLimitsTracker
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.stats.RelayReqStats
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.RelayStats
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.SurgeDns
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.SurgeDnsStore
import com.vitorpamplona.quartz.nip03Timestamp.okhttp.OkHttpBitcoinExplorer
import com.vitorpamplona.quartz.nip03Timestamp.ots.OtsBlockHeightCache
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Client
import com.vitorpamplona.quartz.nip05DnsIdentifiers.OkHttpNip05Fetcher
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerClientMetadata
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.tags.RSVPStatusTag
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent
import com.vitorpamplona.quartz.nip55AndroidSigner.client.NostrSignerExternal
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.CachingOnchainBackend
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.EsploraBackend
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath
import java.io.File

class AppModules(
    val appContext: Context,
) {
    val appAgent = "Amethyst/${BuildConfig.VERSION_NAME}"

    // Exists to avoid exceptions stopping the coroutine
    val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            Log.e("AmethystCoroutine", "Caught exception: ${throwable.message}", throwable)
        }

    val applicationIOScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)

    /**
     * Mints and caches BUD-01 read-auth tokens for auth-gated Blossom hosts.
     * Shared by the OkHttp interceptor (which only reads the cache) and Coil's
     * [com.vitorpamplona.amethyst.commons.service.image.BlossomReadAuthFetcher] (which
     * awaits a signature), so both see one token and one in-flight signature per
     * host. Signing runs on [applicationIOScope], never on an OkHttp thread.
     */
    val blossomReadAuthTokens =
        BlossomReadAuthTokenProvider(
            signerProvider = { sessionManager.loggedInAccount()?.signer },
            scope = applicationIOScope,
        )

    private val _trimLevelEvents = MutableSharedFlow<Int>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val trimLevelEvents = _trimLevelEvents.asSharedFlow()

    /**
     * Fires when memory is tight enough that feeds should drop the strong Note references they
     * hold: the process is on the system LRU list, the strongest level the OS still delivers
     * since API 34, or the heap watchdog raised the same level itself.
     */
    val memoryPressureEvents: Flow<Unit> = trimLevelEvents.filter { it >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND }.map { }

    /**
     * The app-wide DataStore files — the ones that belong to the install rather
     * than to an account. [AccountPreferenceStores] is the same idea keyed by
     * npub.
     *
     * This replaces the `Context.preferencesDataStore` delegate these stores
     * used to share. Same paths — `AppPreferenceStores.file` reproduces
     * `filesDir/datastore/<name>.preferences_pb` exactly — so nothing migrates
     * and a rollback finds its data where it left it. What it buys is that the
     * stores themselves live in `commonMain`, where a desktop or CLI front end
     * can say where its data lives instead of needing a `Context`.
     *
     * The shared_settings migration is attached here, to the file, because
     * eight stores share it and DataStore runs a file's migrations once, on
     * whichever store opens it first.
     */
    val appStores by lazy {
        AppPreferenceStores(
            rootFilesDir = { appContext.filesDir.toOkioPath() },
            migrations = { name ->
                when {
                    name == AppPreferenceStores.SHARED_SETTINGS -> UiSettingsStore.migrations { LocalPreferences.loadSharedSettings() }
                    // One file per account, so the migration is per name rather than a constant.
                    name.startsWith(CashuPreferences.FILE_PREFIX) ->
                        listOf(CashuPreferences.legacyMigration(appContext, name.removePrefix(CashuPreferences.FILE_PREFIX)))
                    name == CALENDAR_REMINDER_SETTINGS_STORE -> calendarReminderSettingsMigrations(appContext)
                    name == CALENDAR_REMINDER_LOG_STORE -> calendarReminderLogMigrations(appContext)
                    else -> emptyList()
                }
            },
        )
    }

    /** The file UI, Tor, Namecoin, OTS and the Buzz stores all share. */
    val sharedSettingsStore get() = appStores.sharedSettings()

    // Pre-load both preference DataStores in parallel on IO threads.
    // Both constructors use runBlocking internally, so starting them concurrently
    // reduces total blocking time from (torPrefs + uiPrefs) to ~max(torPrefs, uiPrefs).
    private val uiPrefsDeferred =
        applicationIOScope.async {
            val prefs = UiSharedPreferences.uiPreferences(sharedSettingsStore) ?: UiSettings()
            UiSharedPreferences(prefs, sharedSettingsStore, appContext, applicationIOScope)
        }

    private val torPrefsDeferred =
        applicationIOScope.async {
            val prefs = TorSettingsStore.torPreferences(sharedSettingsStore) ?: TorSettings()
            TorSettingsStore(prefs, sharedSettingsStore, applicationIOScope)
        }

    // Blocking load of UI Preferences to avoid theme/language blinking
    val uiPrefs by lazy {
        Log.d("AppModules", "UiSharedPreferences Init")
        runBlocking { uiPrefsDeferred.await() }
    }

    private val privacyLockDeferred =
        applicationIOScope.async {
            DataStorePrivacyLockSettings(DataStorePrivacyLockSettings.load(sharedSettingsStore), sharedSettingsStore, applicationIOScope)
        }

    // Blocking load, so a locked app never shows a frame of its content.
    val privacyLockSettings by lazy {
        runBlocking { privacyLockDeferred.await() }
    }

    // Blocking load of Tor Settings to avoid connection leaks
    val torPrefs by lazy {
        Log.d("AppModules", "TorSettingsStore Init")
        runBlocking { torPrefsDeferred.await() }
    }

    // Namecoin ElectrumX server preferences (global, like Tor settings)
    val namecoinPrefs by lazy {
        Log.d("AppModules", "NamecoinSettingsStore Init")
        NamecoinSettingsStore(sharedSettingsStore, applicationIOScope)
    }

    // OTS blockchain explorer preferences (global, like Tor settings)
    //
    // The blocking load is the one the store used to do inside its own
    // constructor: `current` has to answer synchronously for the resolver
    // builder, so somebody has to wait. It is explicit here rather than hidden
    // in commonMain, which has no runBlocking to hide it behind.
    val otsPrefs by lazy {
        Log.d("AppModules", "OtsSettingsStore Init")
        val store = sharedSettingsStore
        OtsSettingsStore(store, runBlocking { OtsSettingsStore.load(store) })
    }

    // App services that should be run as soon as there are subscribers to their
    // flows. Location additionally releases its OS registration whenever no
    // activity is started — see the foreground gate inside LocationState.
    val locationManager by lazy {
        Log.d("AppModules", "LocationManager Init")
        LocationState(
            appContext,
            applicationIOScope,
            isForeground = foregroundTracker.isForeground,
            onListening = { locationRefCount.setActive(it) },
        )
    }
    val connManager = ConnectivityManager(appContext, applicationIOScope)

    val uiState by lazy {
        Log.d("AppModules", "UiSettingsState Init")
        UiSettingsState(uiPrefs.value, connManager.isMobileOrFalse, applicationIOScope)
    }

    private val torService = TorService(appContext, applicationIOScope)
    val torManager = TorManager(torPrefs, torService, applicationIOScope)

    // Opt-in Tor startup-progress notification (TorSettings.progressNotification). Watches the
    // backend's status, not torManager.status, so it never starts Tor by itself.
    private val torProgressNotifier =
        TorProgressNotifier(
            context = appContext,
            scope = applicationIOScope,
            enabled = torPrefs.value.progressNotification,
            status = torService.status,
            bootstrapProgress = torService.bootstrapProgress,
        ).also { it.start() }

    // Network identity change (wifi↔cellular, regained from offline, captive portal
    // cleared) — the old network's guards/circuits are dead, and Arti's in-memory
    // client + on-disk state/ both need a fresh start. onNetworkChange drops the
    // TorClient, clears the bypass + persisted approval, and triggers a full re-init.
    init {
        applicationIOScope.launch {
            connManager.status
                .map { (it as? ConnectivityStatus.Active)?.networkId }
                .filterNotNull()
                .distinctUntilChanged()
                .drop(1)
                .collect { torManager.onNetworkChange() }
        }
    }

    // Restore + persist the set of relay-group channels deleted (kind-9008) on this device, so a
    // deleted channel stays hidden across a restart even if the host relay re-announces a stale
    // kind-44100 for it (device-global; a delete is authoritative and terminal for everyone).
    val relayGroupDeletionPrefs =
        RelayGroupDeletionStore(sharedSettingsStore, applicationIOScope)

    // Restore + persist the last-known admins of joined NIP-29 groups, so the migration/fork check
    // can read their kind-10009 lists even while a group's host relay is down.
    val relayGroupAdminCachePrefs = RelayGroupAdminCacheStore(sharedSettingsStore, applicationIOScope)

    // Restore + persist which drawer section headings the user has folded away, so the side menu
    // opens the way they left it (device-global: a collapsed heading is a per-device view choice,
    // not an account setting worth syncing, unlike the hidden rows beside it in the drawer).
    val drawerSectionCollapsePrefs = DrawerSectionCollapsePreferences(sharedSettingsStore, applicationIOScope)

    // Device-global: the widths the user dragged the panes of a wide window to.
    val paneWidthPrefs = PaneWidthPreferences(sharedSettingsStore, applicationIOScope)

    // Device-global: whether the docked notification panel of a wide window is folded away to
    // its bell. The fold rides window width, not the platform, so every front end keeps it.
    val notificationPanelCollapsePrefs = NotificationPanelCollapsePreferences(sharedSettingsStore, applicationIOScope)

    // Service that will run at all times to receive events from Pokey
    val pokeyReceiver = PokeyReceiver()

    // Key cache service to download and decrypt encrypted files before caching them.
    val keyCache = EncryptionKeyCache()

    // Concurrent, caching DNS resolver shared by every OkHttp client built below — a host
    // resolved for an image fetch is reused when a relay handshake or NIP-05 lookup hits the
    // same host.
    val surgeDns = SurgeDns()

    // Persists [surgeDns]'s positive cache across process restarts so cold starts don't pay
    // ~700 sync getaddrinfo calls. Restored entries fall through to the stale-while-revalidate
    // path on first lookup. Stored in cacheDir — pure perf data, OK if the OS evicts it.
    val dnsStore = SurgeDnsStore(File(appContext.safeCacheDir(), SurgeDnsStore.FILE_NAME), surgeDns)

    // Network identity change (same trigger as Tor's onNetworkChange above), but for DNS
    // the response is deliberately SOFT: most cached answers are still correct on the new
    // network, so staleAll() keeps serving every one of them and merely re-verifies —
    // positives revalidate in the background on next use, negatives (e.g. hosts that only
    // failed because the OLD network / captive portal couldn't resolve them) get re-tried
    // on first touch instead of waiting out their TTL. Nothing is dropped, nothing blocks.
    init {
        applicationIOScope.launch {
            connManager.status
                .map { (it as? ConnectivityStatus.Active)?.networkId }
                .filterNotNull()
                .distinctUntilChanged()
                .drop(1)
                .collect { surgeDns.staleAll() }
        }
    }

    // Runs for the whole process lifetime (main process only — the sandbox never builds AppModules).
    // See [startHeapPressureWatchdog] for why the OS trim callbacks cannot be relied on.
    init {
        startHeapPressureWatchdog()
    }

    // Shared cache populated by OnionLocationInterceptor from any HTTP/WebSocket
    // response carrying an Onion-Location header. Consulted by OnionUrlRewriteInterceptor
    // on Tor-enabled clients to transparently redirect to .onion addresses.
    val onionLocationCache = OnionLocationCache()

    // ---- Resource-usage ledger (battery/data accounting) ----
    // Passive on-device counters (bytes per subsystem x network x visibility,
    // relay connection-time, wakelock time, worker runs). Never transmitted;
    // the user can review them in Settings and explicitly DM a report to the
    // developers. See amethyst/plans/2026-07-12-resource-usage-ledger.md.
    val foregroundTracker = ForegroundTracker()

    val resourceUsageStore = ResourceUsageStore(File(appContext.filesDir, ResourceUsageStore.FILE_NAME))

    val resourceUsage = ResourceUsageAccountant(resourceUsageStore, applicationIOScope)

    // Estimates radio wake-ups from HTTP burst patterns — bytes alone don't
    // predict battery; scattered small requests each pay the radio ramp+tail.
    private val radioBurstEstimator =
        RadioBurstEstimator(
            accountant = resourceUsage,
            isMobile = { connManager.isMobileOrFalse.value },
            isForeground = { foregroundTracker.isForeground.value },
        )

    // Single catch-all counter on the shared non-relay HTTP client: role
    // wrappers only relabel via request tags, so no HTTP traffic (including
    // direct getHttpClient users like the napplet broker) escapes the ledger.
    private val httpUsageInterceptor =
        UsageCountingInterceptor(
            accountant = resourceUsage,
            isMobile = { connManager.isMobileOrFalse.value },
            isForeground = { foregroundTracker.isForeground.value },
            bursts = radioBurstEstimator,
        )

    private val httpUsageMeter = HttpUsageMeter()

    // Session-time counters for the app's long-running battery consumers.
    // All timer-free segment integrators: services and status flows flip them
    // on/off, so tracking costs one counter write per transition.
    val alwaysOnSession = SessionTimeIntegrator(resourceUsage, UsageKeys.ALWAYS_ON_MS, UsageKeys.ALWAYS_ON_STARTS).also { it.registerFlushHook() }
    val callSession = SessionTimeIntegrator(resourceUsage, UsageKeys.CALL_MS, UsageKeys.CALL_SESSIONS).also { it.registerFlushHook() }
    val nestsSession = SessionTimeIntegrator(resourceUsage, UsageKeys.NESTS_MS, UsageKeys.NESTS_SESSIONS).also { it.registerFlushHook() }
    private val powSession = SessionTimeIntegrator(resourceUsage, UsageKeys.POW_MS, UsageKeys.POW_SESSIONS).also { it.registerFlushHook() }
    private val torSession = SessionTimeIntegrator(resourceUsage, UsageKeys.TOR_MS, UsageKeys.TOR_STARTS).also { it.registerFlushHook() }
    private val locationSession = SessionTimeIntegrator(resourceUsage, UsageKeys.LOCATION_MS).also { it.registerFlushHook() }

    // LocationState exposes two independent flows that can both be listening at
    // once (the "Around Me" feed plus an open geohash chat). Refcounting keeps
    // either one stopping from closing the other's segment.
    private val locationRefCount = RefCountedSession(locationSession::setActive)

    // Time-per-screen (route base names only — arguments never reach the
    // ledger). Fed by the navigation listener in AppNavigation; foreground
    // gating means backgrounding on a screen closes its segment.
    val screenTime = ScreenTimeIntegrator(resourceUsage)

    init {
        screenTime.start(applicationIOScope, foregroundTracker.isForeground)
    }

    // In-app (Arti) Tor uptime. Watches the raw TorService status — NOT
    // TorManager.status, whose upstream is WhileSubscribed and calls
    // service.start() when collected, so a permanent ledger subscription
    // there would keep Tor's control flow alive on its own. External Tor
    // (Orbot) is deliberately untracked: its battery belongs to Orbot.
    init {
        applicationIOScope.launch {
            torService.status
                // Battery ledger: Tor is doing work from the moment the client exists — the
                // directory download is the most expensive part of a launch — so this tracks
                // "running", not "bootstrapped". Keying it on Active alone would silently omit the
                // 12-34s download from every cold start.
                .map { it.socksPort != null }
                .distinctUntilChanged()
                .collect { torSession.setActive(it) }
        }
    }

    // Measured battery drain (percent while discharging, fg/bg) — the ground
    // truth the app counters get correlated against. One binder read per
    // ledger flush, nothing while idle.
    init {
        val batteryManager = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        if (batteryManager != null) {
            BatteryDrainSampler(
                accountant = resourceUsage,
                capacityPct = { batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 1..100 } },
                isCharging = { batteryManager.isCharging },
                isForeground = { foregroundTracker.isForeground.value },
            ).register()
        }
    }

    // manages all the other connections separately from relays.
    val okHttpClients: DualHttpClientManager =
        DualHttpClientManager(
            userAgent = appAgent,
            proxyPortProvider = torManager.activePortOrNull,
            isMobileDataProvider = connManager.isMobileOrNull,
            keyCache = keyCache,
            scope = applicationIOScope,
            dns = surgeDns,
            // Transparently rewrites sha256-keyed HTTP requests to the local
            // Blossom cache when the master toggle is on, the probe sees
            // 127.0.0.1:24242 as available, and either the profile-pictures-only
            // restriction is off or the request is a profile picture.
            shouldBridgeBlossomCache = { profilePicture ->
                val settings = sessionManager.loggedInAccount()?.settings
                val master = settings?.useLocalBlossomCache?.value ?: false
                val profileOnly = settings?.localBlossomCacheProfilePicturesOnly?.value ?: false
                master && (profilePicture || !profileOnly) && localBlossomCacheProbe.available.value
            },
            onionCache = onionLocationCache,
            usageInterceptor = httpUsageInterceptor,
            // Retries auth-gated Blossom blob downloads (e.g. Buzz's private
            // media relay) with a BUD-01 read-auth token signed by the current
            // account on a 401. Reads the signer at call time so it always
            // tracks the logged-in account.
            blossomReadAuth =
                BlossomReadAuthInterceptor(
                    cachedHeaderProvider = blossomReadAuthTokens::cachedHeader,
                    onAuthRequired = blossomReadAuthTokens::warm,
                ),
            onLocalBlossomCacheUnreachable = { localBlossomCacheProbe.markUnavailable() },
        )

    // Offers easy methods to know when connections are happening through Tor or not
    val roleBasedHttpClientBuilder = RoleBasedHttpClientBuilder(okHttpClients, torPrefs.value, httpUsageMeter::counted)

    // The ElectrumX and Namecoin Core clients and the `.bit` resolver composed from the settings.
    val namecoinServices by lazy {
        Log.d("AppModules", "NamecoinServices Init")
        NamecoinServices(namecoinPrefs, roleBasedHttpClientBuilder, applicationIOScope)
    }

    val namecoinResolver get() = namecoinServices.resolver

    val nip05Client by
        lazy {
            Log.d("AppModules", "NIP05Client Init")
            Nip05Client(
                fetcher = OkHttpNip05Fetcher(roleBasedHttpClientBuilder::okHttpClientForNip05),
                namecoinResolverBuilder = { namecoinResolver },
            )
        }

    val otsResolverBuilder by
        lazy {
            Log.d("AppModules", "OtsResolverBuilder Init")
            TorAwareOkHttpOtsResolverBuilder(
                roleBasedHttpClientBuilder::okHttpClientForMoney,
                roleBasedHttpClientBuilder::shouldUseTorForMoneyOperations,
                OtsBlockHeightCache(),
                customExplorerUrl = { otsPrefs.current.normalizedUrl() },
            )
        }

    val torEvaluatorFlow =
        TorRelayState(
            okHttpClients,
            torPrefs.value,
            applicationIOScope,
        )

    // manages all relay connections
    val okHttpClientForRelays =
        DualHttpClientManagerForRelays(
            userAgent = appAgent,
            proxyPortProvider = torManager.activePortOrNull,
            isMobileDataProvider = connManager.isMobileOrNull,
            scope = applicationIOScope,
            dns = surgeDns,
            onionCache = onionLocationCache,
        )

    // Drops pooled connections once per real Tor route change. When the user switches
    // Tor on, the direct clients' idle sockets to real hosts would otherwise sit in the
    // pool for its 5-minute keepalive after the user has asked for everything to go
    // through Tor. No request could use them either way -- OkHttp keys the pool by
    // `Address`, which includes the proxy, so a connection on a dead route is already
    // unreachable -- which is why this is hygiene and not correctness, and why it is
    // fine for it to be a little late.
    //
    // Every source here is a plain StateFlow, so subscribing costs nothing. Deliberately
    // NOT torManager.activePortOrNull: that chains to TorManager.status, whose upstream
    // is WhileSubscribed and calls service.start() when collected, so a process-lifetime
    // subscription there would hold Arti's control flow open forever -- the same hazard
    // the battery ledger above documents and sidesteps the same way.
    //
    // Also deliberately not the per-feature Tor switches (imagesViaTor, videosViaTor, ...):
    // those change which of the two existing clients a request picks, not the route either
    // one uses, so no pooled connection goes stale.
    init {
        applicationIOScope.launch {
            combine(
                torPrefs.torType,
                torPrefs.externalSocksPort,
                torService.status.map { it.socksPort },
            ) { torType, externalPort, artiPort -> Triple(torType, externalPort, artiPort) }
                .distinctUntilChanged()
                // Only later moves count; the route in force at process construction is the
                // status quo, and nothing is pooled yet to evict.
                .drop(1)
                .collect {
                    okHttpClients.factory.evictPooledConnections()
                    okHttpClientForRelays.factory.evictPooledConnections()
                }
        }
    }

    // Connects the INostrClient class with okHttp
    val websocketBuilder =
        BasicOkHttpWebSocket.Builder(
            httpClient = { url ->
                val useTor = torEvaluatorFlow.shouldUseTorForRelay(url)
                okHttpClientForRelays.getHttpClient(useTor)
            },
            // Don't dial Tor-routed relays until Tor's SOCKS port is up. Otherwise the
            // whole Tor-routed relay set is hammered with doomed dials against the dead
            // proxy during bootstrap. RelayProxyClientConnector reconnects them (with
            // ignoreRetryDelays=true) the instant Tor flips to Active.
            // A session bypass ("continue without Tor") drops the SOCKS port, so the Tor client
            // above falls back to a direct one: let those relays dial, or they never connect.
            canDial = { url ->
                !torEvaluatorFlow.shouldUseTorForRelay(url) || torManager.isTorReady() || torManager.sessionBypass.value
            },
        )

    // Caches all events in Memory
    val cache: LocalCache = LocalCache

    // The cache lives in `commons` and knows nothing about this app. Hand it the pieces it
    // cannot supply for itself — a scope, the NIP-95 blob directory, the relay identity and
    // stats sinks, the debug flag and the main-thread assertion — before anything consumes an
    // event, which is why this sits next to the cache rather than in a later init block.
    init {
        cache.appHost = AmethystLocalCacheHost(this, isDebug)
        // Geohash chats route through the live georelays directory, fetched over this app's clients.
        GeohashRelays.liveRelayLoader = { GeoRelayCsvLoader { okHttpClients.getHttpClient(false) }.fetch() }
    }

    // NIP-BC onchain zap verification backend. Wired up once at app init so
    // LocalCache.consume(OnchainZapEvent) can sum the on-chain output values
    // that pay the recipient's derived Taproot address. Wrapped in a caching
    // decorator so a feed full of onchain zaps doesn't fan out into one HTTP
    // request per event.
    //
    // The explorer endpoint is shared with OpenTimestamps: it honours the same
    // user-configured server (OTS settings) and the same Tor-aware default
    // selection, via BitcoinExplorerEndpoint — onchain zaps must not silently
    // bypass the user's Tor preference.
    init {
        cache.onchainBackend =
            CachingOnchainBackend(
                EsploraBackend(
                    baseUrl = {
                        BitcoinExplorerEndpoint.resolveNormalized(
                            customExplorerUrl = otsPrefs.current.normalizedUrl(),
                            usingTor =
                                roleBasedHttpClientBuilder.shouldUseTorForMoneyOperations(
                                    OkHttpBitcoinExplorer.MEMPOOL_API_URL,
                                ),
                        )
                    },
                    client = roleBasedHttpClientBuilder.okHttpClientForMoney(OkHttpBitcoinExplorer.MEMPOOL_API_URL),
                ),
            )

        // NIP-57 Appendix F: validates incoming zap receipts against the
        // recipient's LNURL provider's advertised `nostrPubkey`. Reuses the
        // money-tier http client so Tor preferences and proxy settings apply.
        cache.lnurlEndpointResolver =
            OkHttpLnurlEndpointResolver(roleBasedHttpClientBuilder::okHttpClientForMoney)
    }

    // Provides a relay pool. The caching decoder skips re-parsing EVENT frames
    // that arrive again via another subscription or relay (14-57% of frames in
    // production measurements).
    //
    // Wrapped in BlockedRelayFilteringClient so the active account's NIP-51
    // kind:10006 blocked relay list is enforced centrally on every REQ, COUNT
    // and publish (relay targeting is otherwise distributed across dozens of
    // feed/loader/finder/broadcast sites, most of which don't subtract it).
    // The blocked set is read per-call from the logged-in account.
    //
    // Debug builds also warn, once per producer, about any REQ/COUNT whose filters carry no
    // purpose: those keep relays connected that the always-on notification cannot explain.
    val client: INostrClient =
        BlockedRelayFilteringClient(
            NostrClient(websocketBuilder, applicationIOScope, relayFrameDecoder()).let {
                if (BuildConfig.DEBUG) UntaggedFilterWarningClient(it) else it
            },
            blockedRelays = {
                sessionManager
                    .loggedInAccount()
                    ?.blockedRelayList
                    ?.flow
                    ?.value ?: emptySet()
            },
        )

    // Self-heals the "Tor Active but every circuit dead" state the lifecycle watchdogs can't
    // see (they only arm while Connecting). Watches Tor-routed relay outcomes and, when enough
    // fail with zero successes in the window, pokes TorManager to drop + re-init Arti.
    val torCircuitHealthTracker =
        TorCircuitHealthTracker(
            client = client,
            isTorRouted = { torEvaluatorFlow.shouldUseTorForRelay(it) },
            isTorActive = { torManager.isTorReady() },
            isConnectivityActive = { connManager.status.value is ConnectivityStatus.Active },
            onCircuitsDead = { torManager.onTorCircuitsDead() },
        ).also { it.register() }

    // Watches for changes on Tor and Relay List Settings
    val relayProxyClientConnector =
        RelayProxyClientConnector(
            torEvaluatorFlow.flow,
            okHttpClientForRelays.defaultHttpClient,
            okHttpClientForRelays.defaultHttpClientWithoutProxy,
            connManager.status,
            torManager.status.map { it.isFullyBootstrapped },
            client,
            applicationIOScope,
            onTrigger = { cause -> resourceUsage.add(UsageKeys.relayTrigger(cause), 1) },
        )

    // Verifies and inserts in the cache from all relays, all subscriptions
    val cacheClientConnector = CacheClientConnector(client, cache)

    // Show messages from the Relay and controls their dismissal. Attributes each NOTIFY to the
    // account whose AUTH the relay rejected (accountsCache is declared below; the lambda reads it
    // lazily at NOTIFY time, long after init).
    val notifyCoordinator = NotifyCoordinator(client) { pubkey -> accountsCache.accounts.value[pubkey] }

    // Per-relay NIP-42 ALLOW/DENY overrides are now per-account (Account.relayAuthPermissions,
    // backed by a file under accounts/<pubkey>/), so there is no app-wide store here anymore.

    /**
     * The account every napplet/web-app grant and byte of storage is scoped to. Read lazily on each
     * call (never captured) so an account switch immediately moves embedded apps to the new account's
     * namespace: an app authorized by one npub is never authorized under another.
     */
    val nappletAccountScope: () -> String = { sessionManager.loggedInAccount()?.pubKey ?: "" }

    // Singleton stores for napplet permissions. The holder is what enforces
    // DataStore's one-instance-per-file rule now; this stays a lazy val so the
    // ledger below and the broker share one object.
    val nappletPermissionStore by lazy { DataStoreNappletPermissionStore(appStores.getDataStore("napplet_permissions"), nappletAccountScope) }

    /**
     * The one napplet permission ledger for the main process. Its persistent half is just the store
     * above, but it also holds the in-memory ALLOW_SESSION grants — and *those* only work if every
     * caller shares this instance. The broker service and the Connected Apps screens used to build
     * a ledger each, so a "Forget"/revoke tapped in the UI cleared the screen's own (always empty)
     * session map while the grants the broker was actually consulting lived on untouched.
     *
     * Session lifetime is bounded by [com.vitorpamplona.amethyst.napplet.NappletBrokerService]'s
     * onDestroy (all applet/browser surfaces gone), which calls `endSession()`.
     */
    val nappletPermissionLedger by lazy { NappletPermissionLedger(nappletPermissionStore, nappletAccountScope) }

    // NOT account-scoped here on purpose: this store is shared with NIP-46, whose coordinates already
    // carry their owning account (`nip46:<signer>:<client>`) and whose sessions run for a specific
    // account rather than the active one. The napplet path namespaces its own coordinate the same way
    // (see NappletBroker.signerCoordinateFor) instead.
    val signerPermissionStore by lazy { DataStoreNostrSignerPermissionStore(appStores) }

    // Display + relay info for connected NIP-46 remote-signer clients.
    val nip46ClientStore by lazy { DataStoreNip46ClientStore(appStores.getDataStore(DataStoreNip46ClientStore.FILE_NAME)) }

    // The device-local favorite-apps list behind the bottom bar, the Favorite Apps grid and the
    // browser launcher, plus the browser's visit history behind the omnibox suggestions. Both live in
    // commons and take their store and scope from here — that is the whole of their Android binding.
    //
    // Main process only: the keyless `:napplet` sandbox never builds AppModules, so it never builds
    // these either. One instance each, so DataStore only ever sees one live reader per file.
    val favoriteApps by lazy { FavoriteAppsRegistry(appStores.getDataStore(FavoriteAppsRegistry.FILE_NAME), applicationIOScope) }

    val browserHistory by lazy { BrowserHistoryRegistry(appStores.getDataStore(BrowserHistoryRegistry.FILE_NAME), applicationIOScope) }

    // Favicons captured by the browser host, one PNG per host. Not a DataStore — it takes the directory
    // to keep them in, the same way AppPreferenceStores takes rootFilesDir.
    val browserIcons by lazy {
        BrowserIconRegistry({ appContext.filesDir.toOkioPath() / BrowserIconRegistry.DIR }, applicationIOScope)
    }

    // Authenticates with relays.
    val authCoordinator = AuthCoordinator(client, applicationIOScope)

    // Tries to verify new OTS events when they arrive.
    val otsEventVerifier =
        IncomingOtsEventVerifier(
            otsResolverBuilder = otsResolverBuilder,
            cache = cache,
            scope = applicationIOScope,
        )

    // Tracks if it is possible to connect to relays.
    val failureTracker = RelayOfflineTracker(client)

    // Captures statistics about relays
    val relayStats = RelayStats(client)

    // How fast each relay answers (a post's OK, a query's EOSE and first result) and which relays
    // are slow next to the others, for the relay screens. Measured off the relay client's traffic
    // and kept across restarts; built off the main thread, since it reads its file on creation.
    val relayLatencyMonitor by lazy {
        RelayLatencyMonitor(
            client = client,
            persistence = FileRelayHealthPersistence(File(appContext.filesDir, RELAY_HEALTH_FILE)),
            scope = applicationIOScope,
            isTorRouted = { torEvaluatorFlow.shouldUseTorForRelay(it) },
            nip11 = { nip11Cache.getFromCache(it) },
        )
    }

    init {
        applicationIOScope.launch { relayLatencyMonitor }
    }

    // Caches the latest LIMITS (rights + limits) each relay advertises.
    val relayLimits = RelayLimitsTracker(client)

    // Resource-usage ledger: relay traffic/reconnect + connection-time,
    // foreground-time, process-CPU, and signature-verification collectors.
    init {
        client.addConnectionListener(
            RelayUsageListener(
                accountant = resourceUsage,
                isMobile = { connManager.isMobileOrFalse.value },
                isForeground = { foregroundTracker.isForeground.value },
            ),
        )
        RelayConnectionTimeIntegrator(
            connectedCount = client.connectedRelaysFlow().map { it.size },
            isMobile = connManager.isMobileOrNull,
            isForeground = foregroundTracker.isForeground,
            accountant = resourceUsage,
        ).start(applicationIOScope)
        ForegroundTimeIntegrator(
            isForeground = foregroundTracker.isForeground,
            accountant = resourceUsage,
        ).start(applicationIOScope)
        ProcessCpuSampler(resourceUsage).register()
        cache.verifyMeter = { elapsedNanos, _ ->
            resourceUsage.add(UsageKeys.VERIFY_COUNT, 1)
            resourceUsage.add(UsageKeys.VERIFY_US, elapsedNanos / 1_000)
        }
    }

    // Logs debug messages when needed
    val detailedLogger = if (isDebug) RelayLogger(client, debugSending = false, debugReceiving = false) else null
    val relayReqStats = if (isDebug) RelayReqStats(client) else null
    val logger = if (isDebug) RelaySpeedLogger(client) else null

    // Focused timeline for the DM / gift-wrap loading path (tag: DMPagination).
    // val dmDiagnostics = if (isDebug) DmRelayDiagnosticsLogger(client) else null

    // Per-relay cold-start census: connection outcome by cause, REQ/EOSE/CLOSED accounting,
    // and which relays actually carried the boot (tag: BootRelayDiag).
    val bootDiagnostics = if (isDebug) BootRelayDiagnostics(client) else null

    // Coordinates all subscriptions for the Nostr Client
    val sources: RelaySubscriptionsCoordinator =
        RelaySubscriptionsCoordinator(
            LocalCache,
            client,
            authCoordinator.receiver,
            failureTracker,
            applicationIOScope,
        )

    // fire-and-forget NIP-13 mining: posts queue here and publish when mined.
    // One job mines at a time, racing half the cores over disjoint nonce
    // slices — same total CPU budget as the old 2-job pool, but each post
    // finishes ~minerThreads× sooner and the other half of the cores stays
    // free for the UI. Template jobs checkpoint to disk (restored on login)
    // and every enqueue raises the shortService shield so backgrounding
    // doesn't freeze a miner.
    val powJobStore by lazy {
        PoWJobStore(File(appContext.filesDir, PoWJobStore.FILE_NAME), applicationIOScope)
    }

    val powPublishQueue by lazy {
        PoWPublishQueue(
            scope = applicationIOScope,
            maxConcurrent = 1,
            minerThreads = platformMinerWorkers(),
            persistence = powJobStore,
            onQueueActive = { PowMiningForegroundService.start(appContext) },
        ).also { queue ->
            // Resource ledger: mining burns half the cores flat-out for as
            // long as it runs — without this, PoW shows up in cpu.ms as an
            // unattributed mystery. Wired inside the lazy so the ledger never
            // forces the queue to initialize.
            applicationIOScope.launch {
                queue.jobs
                    .map { jobs -> jobs.any { it.isMining } }
                    .distinctUntilChanged()
                    .collect { powSession.setActive(it) }
            }
        }
    }

    /** A Blossom client for [serverBaseUrl], on the upload role's HTTP client (Tor-aware). */
    fun blossomClient(serverBaseUrl: String) = BlossomClient(roleBasedHttpClientBuilder.okHttpClientForUploads(serverBaseUrl))

    /** Which installed apps open a `payto` hand-off; cached across zap pickers for the app's life. */
    val payToApps by lazy { PayToAppAvailability(appContext) }

    /** App-level BUD-04 mirror sweep, so "sync all" keeps running as the user navigates. */
    val blossomMirrorQueue by lazy {
        BlossomMirrorQueue(
            scope = applicationIOScope,
            clientFor = ::blossomClient,
            onActive = { BlossomSyncForegroundService.start(appContext) },
        )
    }

    val powJobRestorer by lazy {
        PowJobRestorer(powPublishQueue, powJobStore, scheduledPostStore)
    }

    // A NIP-46 signer's auth_url challenges, from logins and from the accounts' signers alike.
    val remoteSignerAuthPrompts = RemoteSignerAuthPrompts()

    // keeps all accounts live
    val accountsCache =
        AccountCacheState(
            geolocationFlow = { locationManager.geohashStateFlow },
            nwcFilterAssembler = { sources.nwc },
            cashuMintDirectoryFilterAssembler = { sources.cashuMintDirectory },
            okHttpClientForMoney = roleBasedHttpClientBuilder::okHttpClientForMoney,
            externalSignerFactory = { pubKey, packageName -> NostrSignerExternal(pubKey, packageName, appContext.contentResolver) },
            marmotStreamTransportFactory = ::defaultMarmotStreamTransport,
            cordnBlobCipher = { KeyStoreCordnBlobCipher() },
            otsResolverBuilder = { otsResolverBuilder.build() },
            cache = cache,
            client = client,
            appVersion = BuildConfig.VERSION_NAME,
            encryptionKeyCache = keyCache,
            saveSettings = { LocalPreferences.saveToEncryptedStorage(it) },
            // Who this app is to a NIP-46 remote signer an account logs in with.
            remoteSignerMetadata = REMOTE_SIGNER_METADATA,
            // A provider: notificationDispatcher is declared further down this class.
            marmotNotifier = { notificationDispatcher },
            nip46Consent = Nip46ConsentBridge,
            geohashIdentityStore = { AndroidGeohashIdentityStore(it) },
            rootFilesDir = { appContext.filesDir },
            powQueue = { powPublishQueue },
            meterSigner = { MeteringNostrSigner(it, resourceUsage) },
            signerPermissionStore = signerPermissionStore,
            nip46ClientStore = nip46ClientStore,
            // Same sockets as the app (Tor, blocked relays), but never filed into LocalCache.
            trustNetworkClientBuilder = { NostrClient(websocketBuilder) },
            remoteSignerAuthPrompts = remoteSignerAuthPrompts,
            // Unknown (a cold start before the first callback) waits, like mobile data.
            canDownloadLargeFiles = { connManager.isMobileOrNull.value == false },
            // Restore + persist the Buzz bookkeeping that has no Nostr event to rebuild from: the
            // joined workspace relays (so the app knows which relays to sync as workspaces on cold
            // start — Buzz membership is server-side) and the starred channels. Per account: the
            // joined set makes a relay first-party for NIP-42, and a star is personal.
            startBuzzPersistence = { account ->
                BuzzWorkspaceStore(sharedSettingsStore, account.scope, account.pubKey, account.buzzWorkspaces)
                BuzzChannelStarStore(sharedSettingsStore, account.scope, account.pubKey, account.buzzChannelStars)
                // Eager like the rest, so a held NIP-OA attestation is loaded before this account's
                // first Buzz-relay AUTH rather than after it.
                BuzzAttestationStore(sharedSettingsStore, account.scope, account.pubKey, account.buzzAttestation)
                // Concord Direct Invites the user declined (CORD-05 §6) stay declined across restarts.
                ConcordDirectInviteDeclineStore(sharedSettingsStore, account.scope, account.pubKey, account.concord.directInviteInbox)
                // Whether this account shares what it listens to as its music status, and from which apps.
                NowPlayingSettingsStore(sharedSettingsStore, account.scope, account.pubKey, account.nowPlayingSettings)
                // The playback speed picked for each drss feed, local as drss.io keeps it.
                DrssListenRateStore(sharedSettingsStore, account.scope, account.pubKey, account.drss.rates)
            },
        )

    val sessionManager =
        AccountSessionManager(
            accountsCache = accountsCache,
            nip05ClientBuilder = { nip05Client },
            clientBuilder = { client },
            localPreferences = LocalPreferences,
            scope = applicationIOScope,
            hooks = AndroidAccountSessionHooks(),
            remoteSignerMetadata = REMOTE_SIGNER_METADATA,
            signerRelayRouting = torEvaluatorFlow,
            remoteSignerAuthPrompts = remoteSignerAuthPrompts,
        )

    // Web of Trust: whenever the app comes to the foreground, or the network changes under it (a
    // first download waiting for Wi-Fi), bring the active account's network index up to date. A
    // no-op unless an update, a full check or the first download is due.
    init {
        applicationIOScope.launch {
            combine(foregroundTracker.isForeground, sessionManager.accountContent, connManager.isMobileOrNull) { foreground, state, _ ->
                if (foreground) (state as? AccountState.LoggedIn)?.account else null
            }.filterNotNull()
                .collect { it.trustNetwork.syncIfStale() }
        }
    }

    private inner class AndroidAccountSessionHooks : AccountSessionHooks {
        override fun onSessionEnding() {
            // The Nest audio-room activity reads the active AccountViewModel through a process-singleton
            // bridge: drop the ref so it cannot survive into the next session. See [NestBridge].
            NestBridge.clear()
            // A call belongs to the account that placed it. See [CallSessionBridge].
            CallSessionBridge.clear()
        }

        override suspend fun onAccountRemoving(npub: String) {
            // TODO: also drop this account's WebView storage profile — the cookies/localStorage of every
            // site it visited survive here, keyed by NappletWebViewProfiles.forPubKey(hex). It CANNOT be
            // done from this process: WebView profiles live in the WebView data directory, which belongs
            // to the `:napplet` process (nothing calls setDataDirectorySuffix, so booting WebView here
            // too would collide on the same directory). Deleting it needs a broker message that has
            // `:napplet` call ProfileStore.deleteProfile(name) — and that must refuse a profile still in
            // use by a live WebView. Not wired for this release; there is no existing hook that reaches
            // the sandbox on account deletion.
            // The launcher is the one place an account's contacts live outside our own storage, so the
            // conversation shortcuts go first. See [ConversationShortcuts].
            ConversationShortcuts.removeForAccount(appContext, npub)
        }

        /**
         * Drops everything a deleted account left in the publish pipelines: parked scheduled posts,
         * checkpointed PoW mining jobs, and any of its jobs still queued or mining (a post must not
         * publish after its account is gone), and its front page's job, papers and settings.
         */
        override suspend fun onAccountRemoved(pubkey: HexKey) {
            scheduledPostStore.removeForAccount(pubkey)
            powPublishQueue.cancelForOwner(pubkey)
            powJobStore.removeForAccount(pubkey)
            // Its morning edition must not wake for an account that is gone, nor its papers stay.
            frontPageScheduler.forget(pubkey)
            frontPagePrinters.forget(pubkey)
            routstr.forget(pubkey)
        }

        override fun cashuCountersFor(npub: String) = CashuPreferences.forAccount(npub)
    }

    // Surfaces non-zap Lightning payments reported by the logged-in account's NWC
    // wallet(s) as tray notifications (zaps are already shown via ZapNotification).
    // The relay subscription lives in the always-on AccountFilterAssembler; this
    // only drains the decoded-payment flow into an OS notification.
    val nwcPaymentNotificationWatcher =
        NwcPaymentNotificationWatcher(
            context = appContext,
            scope = applicationIOScope,
            accountFlow = sessionManager.accountContent.map { (it as? AccountState.LoggedIn)?.account },
        ).also { it.start() }

    // Shares what the user listens to (in the app, or in other apps once notification access is
    // granted) as the logged-in account's NIP-38 music status. Off until the account opts in.
    val nowPlayingStatusCoordinator =
        NowPlayingStatusCoordinator(
            scope = applicationIOScope,
            // Still loading is not logged off: that would read as "not sharing" on every start.
            accountFlow =
                sessionManager.accountContent
                    .filter { it !is AccountState.Loading }
                    .map { (it as? AccountState.LoggedIn)?.account },
            relayServices = relayProxyClientConnector.relayServices,
            setOtherAppsWanted = { OtherAppsNowPlayingService.setWanted(appContext, it) },
        ).also { it.start() }

    fun subscribedFlow(
        address: Address,
        account: Account,
    ): Flow<NoteState> {
        val note = cache.getOrCreateAddressableNote(address)

        val userSub = UserFinderQueryState(note.author ?: cache.getOrCreateUser(address.pubKeyHex), account)
        val noteSub = EventFinderQueryState(note, account)

        return note
            .flow()
            .metadata.stateFlow
            .onStart {
                sources.userFinder.subscribe(userSub)
                sources.eventFinder.subscribe(noteSub)
            }.onCompletion {
                sources.eventFinder.unsubscribe(noteSub)
                sources.userFinder.unsubscribe(userSub)
            }
    }

    val localBlossomCacheProbe by lazy {
        LocalBlossomCacheProbe(roleBasedHttpClientBuilder)
    }

    val blossomResolver by lazy {
        Log.d("AppModules", "BlossomServerResolver Init")
        BlossomServerResolver(
            loggedInUsers = { listOfNotNull(sessionManager.loggedInAccount()?.pubKey) },
            blossomServers = { addressesToSubscribe ->
                val account = sessionManager.loggedInAccount() ?: return@BlossomServerResolver listOf()
                addressesToSubscribe.map { address ->
                    subscribedFlow(address, account).transform {
                        val event = it.note.event as? BlossomServersEvent
                        if (event != null) {
                            emit(event)
                        }
                    }
                }
            },
            httpClientBuilder = roleBasedHttpClientBuilder,
            // Same gate as the interceptor: the profile-pictures-only restriction keeps note
            // media (including native `blossom:` URIs) off the local cache.
            useLocalBlossomCache = {
                val settings = sessionManager.loggedInAccount()?.settings
                val master = settings?.useLocalBlossomCache?.value ?: false
                val profileOnly = settings?.localBlossomCacheProfilePicturesOnly?.value ?: false
                master && !profileOnly
            },
            localCacheProbe = localBlossomCacheProbe,
            scope = applicationIOScope,
        )
    }

    // Gives every account that opted into running in the background its own
    // account-level subscriptions (notifications, DMs, gift wraps, wallet), with no
    // AccountViewModel behind it. Driven by alwaysOnNotificationServiceManager below,
    // which already tracks which accounts opted in.
    val accountSubscriptions = AccountSubscriptionRegistry(sources.account)

    // Manages always-on notification service lifecycle. Preloads every saved
    // writable account while enabled so GiftWraps for non-active accounts still
    // get unwrapped by their owning account's newNotesPreProcessor.
    val alwaysOnNotificationServiceManager =
        AlwaysOnNotificationServiceManager(
            context = appContext,
            scope = applicationIOScope,
            accountsCache = accountsCache,
            localPreferences = LocalPreferences,
            subscriptions = accountSubscriptions,
            isForeground = foregroundTracker.isForeground,
            activePubKeyProvider = { sessionManager.loggedInAccount()?.pubKey },
        )

    // Observes LocalCache for notification-relevant events and routes them to
    // EventNotificationConsumer. Sources: FCM, UnifiedPush, Pokey, active relay
    // subscriptions, and NotificationRelayService.
    val notificationDispatcher =
        NotificationDispatcher(appContext, applicationIOScope) { heldMs ->
            resourceUsage.add(UsageKeys.WAKELOCK_NOTIF_MS, heldMs)
            resourceUsage.add(UsageKeys.WAKELOCK_NOTIF_COUNT, 1)
        }

    // Local store for posts the user has scheduled to publish later. Backed by a
    // single JSON file under the app's private filesDir; read by ScheduledPostWorker.
    val scheduledPostStore =
        ScheduledPostStore(File(appContext.filesDir, ScheduledPostStore.FILE_NAME).path)

    // Each account's front page: prints that outlive the screen, the last week of editions in
    // filesDir, and the morning schedule (WorkManager).
    val frontPageNotifier by lazy { AndroidFrontPageNotifier(appContext) }
    val frontPageScheduler by lazy { AndroidFrontPageScheduler(appContext) }
    val frontPagePrinters by lazy {
        FrontPagePrinters(
            http = roleBasedHttpClientBuilder,
            memory = DataStoreFrontPageModelMemory(appStores),
            store = FrontPageEditionStore(File(appContext.filesDir, FrontPageEditionStore.FOLDER).path),
            schedules = DataStoreFrontPageScheduleStore(appStores),
            notifier = frontPageNotifier,
            observerPrefs = DataStoreObserverPrefsStore(appStores),
            installs = DataStoreFrontPageInstallStore(appStores),
            // How this phone appears in the reader's synced schedule ("Printed each morning by Pixel 8").
            installInfo = FrontPageInstallInfo(name = Build.MODEL?.trim()?.ifEmpty { null } ?: "Android", app = "amethyst-android"),
            // A print the reader starts runs in a foreground service, so leaving the app doesn't stop it.
            host = AndroidFrontPagePrintHost(appContext),
        )
    }

    // Routstr, the sats-paid AI nodes: each account's purchases, kept in DataStore until the key is
    // saved to kind 12000, and followed again on launch.
    val routstr by lazy {
        RoutstrService(
            http = roleBasedHttpClientBuilder,
            store = DataStoreRoutstrPurchaseStore(appStores),
            torEnabled = { RoutstrService.onionsViaTor(torPrefs.value) },
            scope = applicationIOScope,
        )
    }

    // Organizes cache clearing
    val trimmingService by
        lazy {
            MemoryTrimmingService(cache)
        }

    // as new accounts are loaded, updates the state of the TorRelaySettings, which produces new TorRelayEvaluator
    // and reconnects relays if the configuration has been changed.
    val accountsTorStateConnector = AccountsTorStateConnector(accountsCache, torEvaluatorFlow, applicationIOScope)

    // saves the .content of NIP-95 blobs in disk to save memory
    val nip95cache: File by lazy {
        Log.d("AppModules", "NIP95 Cache Init")
        Nip95CacheFactory.new(appContext)
    }

    // LAN cast registry — Chromecast only (play flavor real, fdroid no-op
    // stub). Discovery starts only when the picker dialog opens; idle by
    // default to keep multicast traffic off.
    val castRegistry: CastRegistry by lazy {
        Log.d("AppModules", "CastRegistry Init")
        CastRegistry(appContext)
    }

    // local video cache with disk + memory
    val videoCache: VideoCache by lazy {
        Log.d("AppModules", "VideoCache Init")
        VideoCacheFactory.new(appContext)
    }

    // downloads the start of the videos a feed is about to show into videoCache
    val videoPrefetcher: VideoPrefetcher by lazy {
        VideoPrefetcher(
            videoCache = { videoCache },
            upstream = { useProxy -> mediaDataSourceFactory(okHttpClients.getDynamicCallFactory(useProxy), blossomResolver) },
            scope = applicationIOScope,
        )
    }

    // image cache in disk for coil
    val diskCache: DiskCache by lazy {
        Log.d("AppModules", "ImageCacheFactory Init")
        ImageCacheFactory.newDisk(appContext, applicationIOScope)
    }

    // image cache in memory for coil
    val memoryCache: MemoryCache by lazy {
        Log.d("AppModules", "MemoryCache Init")
        ImageCacheFactory.newMemory(appContext)
    }

    // thumbnail disk cache for profile pictures
    val thumbnailDiskCache: ThumbnailDiskCache by lazy {
        Log.d("AppModules", "ThumbnailDiskCache Init")
        // One-shot reclaim of the v1 cache dir, which held squashed thumbnails.
        appContext.safeCacheDir().resolve("profile_thumbnails").deleteRecursively()
        ThumbnailDiskCache(appContext.safeCacheDir().resolve("profile_thumbnails_v2"))
    }

    // crash report storage
    val crashReportCache = CrashReportCache(appContext.filesDir)

    // cache for NIP-11 documents
    val nip11Cache: Nip11CachedRetriever by lazy {
        Log.d("AppModules", "Nip11CachedRetriever Init")
        Nip11CachedRetriever(torEvaluatorFlow::okHttpClientForRelay)
    }

    fun setImageLoader() {
        Log.d("AppModules", "ImageLoaderSetup Init")
        ImageLoaderSetup.setup(
            app = appContext,
            diskCache = { diskCache },
            memoryCache = { memoryCache },
            blossomServerResolver = { blossomResolver },
            // Through the role builder (not raw getHttpClient) so Coil's image
            // traffic carries the "image" ledger tag. Same Tor decision inside.
            // Marked as media so the local Blossom cache may serve these downloads.
            callFactory = { LocalBlossomMediaCallFactory(roleBasedHttpClientBuilder.okHttpClientForImage(it)) },
            thumbnailCache = thumbnailDiskCache,
            backgroundScope = applicationIOScope,
            readAuth = blossomReadAuthTokens,
        )
    }

    // androidx.security.crypto's EncryptedSharedPreferences is deprecated with no drop-in successor.
    @Suppress("DEPRECATION")
    fun encryptedStorage(npub: String? = null): EncryptedSharedPreferences = EncryptedStorage.preferences(appContext, npub)

    // UnexpectedCrashSaver captures the previous default handler and hands every crash on to it;
    // lint cannot see that from here because the class lives in commons.
    @Suppress("DefaultUncaughtExceptionDelegation")
    fun initiate(appContext: Context) {
        Thread.setDefaultUncaughtExceptionHandler(UnexpectedCrashSaver(crashReportCache, androidCrashReportAssembler()))

        // Ledger: count process starts — high counts reveal WorkManager/restart
        // churn that cold-starts the whole app graph repeatedly.
        resourceUsage.add(UsageKeys.APP_STARTS, 1)

        // Restore the persisted DNS cache before any networking starts. Lookups that fire
        // before this completes fall through to the sync resolver path (existing behavior);
        // once restored, every previously-seen host hits the stale-while-revalidate path
        // instead of blocking on getaddrinfo.
        applicationIOScope.launch {
            dnsStore.load()
        }

        // Periodically flush the DNS cache. Saves are skipped when nothing has changed.
        applicationIOScope.launch {
            while (true) {
                delay(5 * 60 * 1000L)
                dnsStore.save()
            }
        }

        // Reclaim image-cache files orphaned by a process death that lost DeferredDeleteFileSystem's
        // queued unlinks — Coil cannot see them, so without this the directory keeps every killed
        // process's residue and drifts past its own cap forever. See ImageDiskCacheReconciler.
        //
        // Rate-limited to once a day: drift accrues over process deaths, not over startups, and this
        // runs on every one of them — including the WorkManager wake-ups that cold-start the graph.
        //
        // Also the one place that forces the `diskCache` lazy, so its build (a statvfs for the size
        // budget) and the check both land on IO rather than on whichever thread loads an image first.
        applicationIOScope.launch {
            val result = ImageDiskCacheReconciler.reconcileIfDue(diskCache)
            if (result != null && result.wasOverBudget) {
                Log.i("AppModules") { "Image cache was over budget: wiped ${result.reclaimedFiles} files (${result.bytesOnDisk} bytes on disk, ${result.budgetBytes} budget)" }
            }
        }

        applicationIOScope.launch {
            // loads main account quickly.
            LocalPreferences.loadAccountConfigFromEncryptedStorage()
            sessionManager.loginWithDefaultAccountIfLoggedOff()
        }

        // One-time hygiene: remove per-account directories (MLS/Marmot stores) left behind
        // by accounts that are no longer saved — account deletion historically didn't clean
        // them up, so they leaked disk across every add/remove.
        applicationIOScope.launch {
            val keep =
                LocalPreferences
                    .allSavedAccounts()
                    .mapNotNull { decodePublicKeyAsHexOrNull(it.npub) }
                    .toSet()
            accountsCache.pruneOrphanAccountDirs(keep)
        }

        // forces initialization of uiPrefs in the main thread to avoid blinking themes
        uiPrefs

        // initializes diskcache on an IO thread.
        applicationIOScope.launch {
            // Sets Coil - Tor - OkHttp link
            setImageLoader()
        }

        // initializes diskcache on an IO thread.
        applicationIOScope.launch {
            // Sets Coil - Tor - OkHttp link
            uiState
        }

        // LRUCache should not be instanciated in the Main thread due to blocking.
        // trimToSize is a no-op on the empty cache; it just forces the object's
        // (blocking) initialization to happen off the main thread.
        applicationIOScope.launch {
            CachedRobohash.trimToSize(100)
            resourceCacheInit()
        }

        // Initialize napplet permission stores on an IO thread to avoid StrictMode violations
        // when ConnectedAppsScreen first accesses them on the main thread.
        applicationIOScope.launch {
            nappletPermissionStore
            signerPermissionStore
        }

        // registers to receive events
        pokeyReceiver.register(appContext)

        // starts observing LocalCache for notification-worthy events
        notificationDispatcher.start()

        // Keeps each account's morning edition job on the one device the synced schedule names.
        FrontPageScheduleWatcher(frontPagePrinters.morning, accountsCache.accounts, frontPageScheduler, applicationIOScope).start()

        // A Routstr purchase left on its way (an invoice out, a key not saved yet) is picked up again.
        routstr.start(accountsCache.accounts)

        // Keep the scheduled-posts worker (15-min periodic + one-time catch-up)
        // enqueued exactly while the store holds a PENDING post — see
        // ScheduledPostWorkGate. Runs independently of the always-on
        // notification setting so scheduled posts still fire when always-on
        // notifications are disabled.
        ScheduledPostWorkGate(
            store = scheduledPostStore,
            scope = applicationIOScope,
            onPendingWork = {
                ScheduledPostWorker.schedule(appContext)
                ScheduledPostWorker.scheduleCatchUp(appContext)
            },
            onNoPendingWork = { ScheduledPostWorker.cancelPeriodic(appContext) },
        ).start()

        // Daily Web of Trust upkeep (Wi-Fi only) for accounts that only get push notifications.
        TrustNetworkSyncWorker.schedule(appContext)

        // "Starting soon" reminders for NIP-52 appointments the user RSVP'd to as
        // ACCEPTED. The 15-min periodic scanner is only scheduled while it can
        // plausibly fire: this observer enqueues it when an accepted RSVP lands in
        // LocalCache, and the worker cancels its own chain when the cache holds
        // nothing that could still start. LocalCache is memory-only, so the
        // unconditional schedule this replaces could never fire from a WorkManager
        // cold start anyway — it only cost battery.
        applicationIOScope.launch {
            LocalCache
                .observeNewEvents<CalendarRSVPEvent>(Filter(kinds = listOf(CalendarRSVPEvent.KIND)))
                .collect { rsvp ->
                    // Only this device's accounts' RSVPs: the feeds load every follow's too.
                    if (rsvp.status() == RSVPStatusTag.STATUS.ACCEPTED && rsvp.pubKey in accountsCache.accounts.value) {
                        CalendarReminderWorker.schedule(appContext)
                    }
                }
        }

        // A rescheduled appointment must also re-arm the chain: the worker
        // cancels itself when every known target is in the past, and a
        // kind-31922/31923 update (the organizer moving the event) arrives
        // WITHOUT any new RSVP — the user's existing RSVP still points at the
        // same address, and observeNewEvents never re-fires for it. conflate +
        // delay bounds the cache rescan to one per 30s while event feeds
        // stream slot events; the scan only runs for users with reminders on.
        applicationIOScope.launch {
            LocalCache
                .observeNewEvents<Event>(
                    Filter(kinds = listOf(CalendarDateSlotEvent.KIND, CalendarTimeSlotEvent.KIND)),
                ).conflate()
                .collect {
                    if (calendarReminderSettings().load().enabled &&
                        CalendarReminderScanner.couldStillFire(CalendarReminderScanner.acceptedRsvpsInCache(accountsCache.accounts.value.keys), TimeUtils.now())
                    ) {
                        CalendarReminderWorker.schedule(appContext)
                    }
                    delay(30_000)
                }
        }

        // Watch for account login and start/stop always-on notification service.
        // The manager gates on the global master switch + each account's participation
        // (not the active account), so it only needs to run while someone is logged in.
        applicationIOScope.launch {
            sessionManager.accountContent.collectLatest { state ->
                if (state is AccountState.LoggedIn) {
                    alwaysOnNotificationServiceManager.start()
                } else {
                    alwaysOnNotificationServiceManager.stop()
                }
            }
        }

        // Resume PoW mining jobs that were checkpointed before a process death,
        // for EVERY loaded account (the always-on service preloads non-active
        // accounts, whose pending posts must not stay stranded on disk).
        // Idempotent (the queue dedupes by job id), so re-emissions are safe.
        applicationIOScope.launch {
            accountsCache.accounts.collect { loaded ->
                loaded.values.forEach { powJobRestorer.restore(it) }
            }
        }

        // Evict the BlossomServerResolver URL cache whenever either local-cache
        // toggle flips or the probe transitions up/down so stale entries don't
        // outlive the underlying decision.
        applicationIOScope.launch {
            sessionManager.accountContent.collectLatest { state ->
                if (state is AccountState.LoggedIn) {
                    merge(
                        state.account.settings.useLocalBlossomCache
                            .drop(1),
                        state.account.settings.localBlossomCacheProfilePicturesOnly
                            .drop(1),
                    ).collect {
                        blossomResolver.clearCaches()
                        localBlossomCacheProbe.invalidate()
                        // Re-probe immediately so enabling the feature activates it
                        // this session. Otherwise `available` only advances when a
                        // `blossom:` URI is resolved, and the common feed-image path
                        // never routes through the resolver until `available` is
                        // already true — so a freshly-enabled toggle (or a cache that
                        // came up after launch) would stay dormant and the settings
                        // "detected" chip would read stale.
                        localBlossomCacheProbe.isAvailable()
                    }
                }
            }
        }
        applicationIOScope.launch {
            localBlossomCacheProbe.available.drop(1).collect {
                blossomResolver.clearCaches()
            }
        }
        // A resolution to the local cache made while a media type was not Tor-routed must not
        // survive the user switching it to Tor: the cache would keep fetching it outside Tor.
        applicationIOScope.launch {
            torPrefs.value.propertyWatchFlow.drop(1).collect {
                blossomResolver.clearCaches()
            }
        }
        // Warm the local-cache probe so the very first image load doesn't pay
        // the loopback round-trip cost.
        applicationIOScope.launch {
            localBlossomCacheProbe.isAvailable()
        }
        // Re-checks the local cache about once a minute while the feature is on, so the bridge
        // comes back when the cache app is restarted and turns off when it is closed, even while
        // no `blossom:` URI is being resolved. A loopback HEAD never wakes the radio.
        applicationIOScope.launch {
            while (true) {
                delay(LOCAL_BLOSSOM_CACHE_RECHECK_MS)
                if (sessionManager
                        .loggedInAccount()
                        ?.settings
                        ?.useLocalBlossomCache
                        ?.value == true
                ) {
                    localBlossomCacheProbe.isAvailable()
                }
            }
        }

        // Warms the video cache off the main thread. SimpleCache's constructor opens a SQLite
        // index over StandaloneDatabaseProvider and walks every cached span on disk — up to a
        // few hundred ms on a populated 4 GB cache — so leaving it for the first session's
        // onGetSession would do that work on the main thread. The short delay keeps the IO
        // dispatcher free for the urgent first-paint work above (account load, image loader,
        // ui state, robohash) while still landing the warmup well before a typical user can
        // scroll to and tap a video. The previous 10 s delay was long enough that a fast user
        // (or a deep link) could lose the lazy { } race and trigger main-thread init.
        applicationIOScope.launch {
            delay(1_500)
            videoCache
        }
    }

    fun terminate(appContext: Context) {
        pokeyReceiver.unregister(appContext)
        notificationDispatcher.stop()
        BackgroundMedia.removeBackgroundControllerAndReleaseIt()
        PlaybackServiceClient.shutdown()
        alwaysOnNotificationServiceManager.stop()
        // Best-effort flush before the scope is cancelled. Android rarely calls onTerminate in
        // production, but when it does we get one last chance to persist the cache.
        runCatching { dnsStore.save() }
        applicationIOScope.cancel("Application onTerminate $appContext")
        accountsCache.clear()
    }

    /**
     * Self-triggered reclaim, because the OS-driven path cannot fire when we need it most.
     *
     * `onTrimMemory` is the ONLY caller of [trim], and since API 34 the OS delivers just two levels,
     * both of which require the app to be backgrounded:
     *  - `UI_HIDDEN(20)` — activities stopped. Only trims images; never touches [LocalCache].
     *  - `BACKGROUND(40)` — the process is on the system LRU list, which is what gates every bulk
     *    reclaim we have (Tier 2 pruning, feed trimming, the hard cache trims).
     *
     * Two independent situations therefore get NO reclaim at all:
     *  1. **Foreground use.** The deprecated `RUNNING_*` levels are never delivered, so a long session
     *     simply grows until the heap is full.
     *  2. **The always-on notification service.** A process hosting a foreground service can never enter
     *     the cached state, so `BACKGROUND` is unreachable *even while backgrounded* — ActivityManager
     *     refuses it outright ("Unable to set a background trim level on a foreground process").
     *
     * Measured consequence: a 3.4-day session sat at 492 MB of a 512 MB heap (3% free), paying 685 ms
     * mark-compact GCs every ~10 s with dozens of threads blocked in `WaitForGcToComplete`, until an
     * input-dispatch ANR. Reproduced independently on a second device with no foreground service at all.
     *
     * So we watch our own occupancy instead of waiting to be told. Above [HEAP_HIGH_WATER] we run the
     * app's existing `BACKGROUND` reclaim — deliberately the same path, not a parallel policy, because at
     * this occupancy "real reclaim pressure" is simply true. [MIN_RECLAIM_INTERVAL_MS] keeps a prune that
     * frees little from spinning.
     */
    private fun startHeapPressureWatchdog() {
        applicationIOScope.launch {
            var lastRunAt = 0L
            while (isActive) {
                delay(HEAP_CHECK_INTERVAL_MS)
                val runtime = Runtime.getRuntime()
                val max = runtime.maxMemory()
                val used = runtime.totalMemory() - runtime.freeMemory()
                val ratio = used.toDouble() / max
                val now = SystemClock.elapsedRealtime()
                if (ratio >= HEAP_HIGH_WATER && now - lastRunAt >= MIN_RECLAIM_INTERVAL_MS) {
                    lastRunAt = now
                    Log.w("AppModules") {
                        "Heap at ${(ratio * 100).toInt()}% (${used / (1024 * 1024)}MB of ${max / (1024 * 1024)}MB) — " +
                            "self-triggering BACKGROUND reclaim; the OS will not deliver one here."
                    }
                    trim(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
                }
            }
        }
    }

    fun trim(level: Int) {
        _trimLevelEvents.tryEmit(level)
        // Backgrounding is a natural moment to flush the usage ledger too.
        resourceUsage.flushAsync()
        applicationIOScope.launch {
            // Backgrounding is a natural moment to flush the DNS cache.
            dnsStore.save()
            val loggedIn = accountsCache.accounts.value.values
            trimmingService.run(loggedIn, LocalPreferences.allSavedAccounts(), underPressure = level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
            // Trim in-process caches proportional to OS memory pressure.
            //
            // Since API 34 the OS only ever delivers two trim levels (the foreground
            // RUNNING_* levels and the deeper MODERATE/COMPLETE background tiers were
            // deprecated because apps are no longer notified of them):
            //   BACKGROUND(40) — process is on the system LRU list: real reclaim
            //                    pressure, and the strongest signal we still get.
            //   UI_HIDDEN (20) — just backgrounded, no pressure yet. Fires on EVERY
            //                    app switch.
            //
            // So we key off exactly those two. UI_HIDDEN is frequent, so it only trims
            // images (bitmaps are the largest allocations) and keeps the CPU-heavy
            // caches (Robohash SVG assembly, rich-text parsing) warm — clearing them
            // would force a full rebuild on every resume and cause visible jank.
            // BACKGROUND trims hard but keeps a small working set: it means "on the LRU
            // list" (real reclaim pressure), not the imminent kill that COMPLETE used to
            // signal — so leave just enough warm to redraw the screen the user left on.
            when {
                level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                    // On the LRU list under real pressure: trim hard, but keep a small
                    // working set so a returning user doesn't rebuild the visible screen
                    // from scratch. memoryCache is byte-sized (Coil), the rest are entry counts.
                    memoryCache.trimToSize(memoryCache.maxSize / 10)
                    CachedRichTextParser.trimToSize(10)
                    CachedAsciiDocToMarkdown.trimToSize(4)
                    CachedRobohash.trimToSize(20)
                    nip11Cache.trimToSize(10)
                }
                level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                    // Just backgrounded, no pressure yet: trim images but keep the
                    // parsed-text and avatar caches warm so resuming is instant.
                    memoryCache.trimToSize(memoryCache.maxSize / 2)
                }
            }
        }
    }

    companion object {
        /**
         * Fraction of `Runtime.maxMemory()` above which we stop waiting for an OS trim that is never
         * coming and reclaim ourselves. 70% leaves real headroom: the ANR-producing session was pinned at
         * 96% (492 MB of 512 MB, 3% free), where every allocation already stalls behind a GC.
         */
        private const val HEAP_HIGH_WATER = 0.70

        /** Three `Runtime` reads; cheap enough to run often, slow enough to be invisible. */
        private const val HEAP_CHECK_INTERVAL_MS = 60_000L

        /**
         * Floor between self-triggered reclaims. Pruning cannot free events the UI still holds, so a busy
         * screen can sit above the high-water mark for a while; without this we would re-prune every
         * check and burn CPU on a heap that has nothing left to give.
         */
        private const val MIN_RECLAIM_INTERVAL_MS = 120_000L

        /** How often the local Blossom cache is re-probed while the feature is enabled. */
        private const val LOCAL_BLOSSOM_CACHE_RECHECK_MS = 60_000L
    }
}

/** Crash reports name the build and the device: what a maintainer needs to reproduce one. */
private fun androidCrashReportAssembler() =
    ReportAssembler(
        versionLine = BuildConfig.VERSION_NAME + "-" + BuildConfig.FLAVOR.uppercase(),
        deviceRows =
            listOf(
                "Manuf" to Build.MANUFACTURER,
                "Model" to Build.MODEL,
                "Prod" to Build.PRODUCT,
                "Android" to Build.VERSION.RELEASE,
                "SDK Int" to Build.VERSION.SDK_INT.toString(),
                "Brand" to Build.BRAND,
                "Hardware" to Build.HARDWARE,
                "Device" to Build.DEVICE,
                "Host" to Build.HOST,
                "User" to Build.USER,
            ),
    )

private const val RELAY_HEALTH_FILE = "relay_health.json"

/** How Amethyst for Android introduces itself to a NIP-46 remote signer (its approval screen). */
private val REMOTE_SIGNER_METADATA = BunkerClientMetadata(name = "Amethyst", url = "https://amethyst.social", image = "https://raw.githubusercontent.com/vitorpamplona/amethyst/refs/heads/main/icon.png")
