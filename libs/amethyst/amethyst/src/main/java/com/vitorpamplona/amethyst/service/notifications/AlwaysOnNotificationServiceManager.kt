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
package com.vitorpamplona.amethyst.service.notifications

import android.content.Context
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.commons.account.AccountCacheState
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.AccountSubscriptionRegistry
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Coordinates all 5 layers of the always-on notification system:
 *
 * L1 - NotificationRelayService (foreground service with persistent WebSocket)
 * L2 - FCM/UnifiedPush (existing push system, wakeup trigger)
 * L3 - NotificationCatchUpWorker (WorkManager, 15-min periodic catch-up)
 * L4 - BootCompletedReceiver (restart on boot)
 * L5 - ServiceWatchdogManager (AlarmManager, 5-min health check)
 *
 * It also decides **which accounts pull from relays**, which is a different question from
 * whether the service runs, and the two are deliberately not gated the same way.
 *
 * ## Who subscribes
 *
 * - **While a screen is up: every loaded account.** The user can switch accounts at any moment
 *   and expects the one they land on to be current, so all of them keep their own notifications,
 *   DMs and gift wraps live. This costs nothing once the app is away — it ends with the screen.
 * - **While the app is away: only the accounts that opted in**, via
 *   [com.vitorpamplona.amethyst.commons.model.AccountSettings.alwaysOnNotificationService] ("Keep this
 *   account active in the background") or their NIP-46 signer toggle
 *   ([com.vitorpamplona.amethyst.commons.model.AccountSettings.nip46SignerEnabled]).
 *
 * That is what the setting's name promises, and for a while it did not hold: participation gated
 * subscriptions everywhere, so an account you had not opted in for showed no notifications even
 * with the app open in front of you.
 *
 * ## Whether the service runs
 *
 * The five layers are a background concern, so they stay gated on **both** the global master
 * ([LocalPreferences.notificationServiceEnabledFlow], the "Background notification service"
 * toggle / Quick Settings tile — the battery-saver "airplane mode", persisted so an explicit off
 * survives restarts) **and** at least one account having opted in. A foreground-only account must
 * never start a foreground service that outlives the screen that wanted it.
 *
 * Every saved writable account is kept loaded in [AccountCacheState] whenever either condition
 * holds, so (a) participation flags are observable and (b) GiftWraps addressed to any of them get
 * unwrapped by the owning account's `newNotesPreProcessor`. Without this, wraps for non-active
 * accounts would sit in [com.vitorpamplona.amethyst.commons.model.cache.LocalCache] with no subscriber able to
 * decrypt them.
 */
class AlwaysOnNotificationServiceManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val accountsCache: AccountCacheState,
    private val localPreferences: LocalPreferences,
    private val subscriptions: AccountSubscriptionRegistry,
    /** True while any activity is STARTED — see [com.vitorpamplona.amethyst.service.resourceusage.ForegroundTracker]. */
    private val isForeground: StateFlow<Boolean>,
    private val activePubKeyProvider: () -> HexKey?,
) {
    companion object {
        private const val TAG = "AlwaysOnNotifManager"
    }

    private var watchJob: Job? = null
    private var preloadJob: Job? = null
    private var wasEnabled = false

    /**
     * Starts watching the global master switch and the participation flags of every
     * loaded writable account. The service layers run while the master is on AND at
     * least one account participates; the master overrides everything when off.
     *
     * An account "participates" when either its always-on setting **or** its NIP-46
     * signer toggle is on: the foreground service (and its restart layers) keep the
     * process + shared relay client alive, which is exactly what the background signer
     * needs to keep answering requests, so the signer toggle keeps the layers up too.
     *
     * Idempotent: safe to call again on account switch/login — it restarts the watch.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        watchJob?.cancel()
        wasEnabled = false
        watchJob =
            scope.launch {
                localPreferences
                    .notificationServiceEnabledFlow()
                    .combine(isForeground) { masterEnabled, foreground -> masterEnabled to foreground }
                    .collectLatest { (masterEnabled, foreground) ->
                        if (!masterEnabled && !foreground) {
                            // Nothing wants the accounts: the master is off and no screen is up.
                            // Suppress every layer and stop keeping accounts loaded.
                            if (wasEnabled) {
                                disableServiceLayers()
                                wasEnabled = false
                            }
                            stopMultiAccountPreload()
                            return@collectLatest
                        }

                        // Keep every writable account loaded — in the foreground so they can all
                        // pull, and with the master on so participation flags are observable and
                        // gift wraps can decrypt.
                        startMultiAccountPreload()

                        accountsAndParticipants()
                            .distinctUntilChanged()
                            .collectLatest { (all, participating) ->
                                // The rule the "keep this account active in the background" setting
                                // actually describes: while a screen is up, EVERY loaded account
                                // pulls its own notifications, DMs and gift wraps, because the user
                                // can switch to any of them and expects them current. The setting
                                // only decides which ones keep doing it once the app is away.
                                subscriptions.sync(if (foreground) all else participating)

                                // The service layers are a background concern, so they stay tied to
                                // the master switch and to somebody having opted in. A foreground-only
                                // account must not start a foreground service that outlives the screen.
                                //
                                // Edge-triggered, deliberately. This flow re-emits whenever the account
                                // map changes identity or the app crosses foreground — far more often
                                // than the old boolean did — and ServiceWatchdogManager.schedule()
                                // replaces its alarm with one starting `now + 5min`. Calling it on every
                                // emission pushed the watchdog's first fire past every screen-on, so the
                                // layer that exists to restart a dead service would never have run.
                                val shouldRun = masterEnabled && participating.isNotEmpty()
                                if (shouldRun != wasEnabled) {
                                    if (shouldRun) enableServiceLayers() else disableServiceLayers()
                                    wasEnabled = shouldRun
                                }
                            }
                    }
            }
    }

    /**
     * Every loaded account paired with the subset that opted into running in the background.
     *
     * Both come from one flow because they change together and the two decisions below — who
     * subscribes, and whether the service runs — must never be made from different snapshots.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun accountsAndParticipants(): Flow<Pair<List<Account>, List<Account>>> =
        accountsCache.accounts.flatMapLatest { accounts ->
            val all = accounts.values.toList()
            val flags =
                all.map { account ->
                    account.settings.alwaysOnNotificationService
                        .combine(account.settings.nip46SignerEnabled) { alwaysOn, signer ->
                            if (alwaysOn || signer) account else null
                        }
                }
            if (flags.isEmpty()) {
                flowOf(all to emptyList())
            } else {
                combine(flags) { values -> all to values.filterNotNull() }
            }
        }

    fun stop() {
        watchJob?.cancel()
        watchJob = null
        stopMultiAccountPreload()
        // Logout/terminate: tear the layers down explicitly. Otherwise the watchdog alarm
        // and periodic worker stay scheduled and would resurrect the service for a
        // logged-out user (nobody participating).
        disableServiceLayers()
        wasEnabled = false
    }

    private fun enableServiceLayers() {
        Log.d(TAG, "Enabling notification service layers")

        // L1: Start foreground service
        NotificationRelayService.start(context)

        // L3: Schedule periodic catch-up worker
        NotificationCatchUpWorker.schedule(context)

        // L5: Start watchdog alarm
        ServiceWatchdogManager.schedule(context)

        // L2 (FCM) and L4 (BOOT_COMPLETED) are always active via manifest
    }

    private fun disableServiceLayers() {
        Log.d(TAG, "Disabling notification service layers")

        // L1: Stop foreground service
        NotificationRelayService.stop(context)

        // L3: Cancel periodic catch-up worker
        NotificationCatchUpWorker.cancel(context)

        // L5: Cancel watchdog alarm
        ServiceWatchdogManager.cancel(context)
    }

    /**
     * Preloads every saved writable account into [AccountCacheState] and keeps the set
     * in sync by observing [LocalPreferences.accountsFlow]. New accounts added while
     * the service is enabled (login flow) are picked up automatically.
     *
     * Note: the first [LocalPreferences.accountsFlow] emission is `null` (lazily
     * populated). We still call [AccountCacheState.loadAllWritableAccounts] on every
     * emission — its suspend call to `allSavedAccounts()` triggers flow population,
     * and subsequent [loadAccount] calls are idempotent on already-loaded accounts.
     */
    private fun startMultiAccountPreload() {
        preloadJob?.cancel()
        preloadJob =
            scope.launch {
                localPreferences.accountsFlow().collect {
                    try {
                        accountsCache.loadAllWritableAccounts(localPreferences)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.w(TAG, "Multi-account preload failed: ${e.message}", e)
                    }
                }
            }
    }

    /**
     * Cancels the preload collector and releases every cached account except the
     * currently active one, so users with the master off return to single-account
     * memory/battery footprint.
     *
     * Unmounts the background subscriptions too: with the master off, the only
     * account that should be talking to relays is the one on screen, and its
     * subscription comes from the screen's own mount.
     */
    private fun stopMultiAccountPreload() {
        preloadJob?.cancel()
        preloadJob = null
        subscriptions.clear()
        // remove this because we don't know which other accounts might be getting used.
        // val active = activePubKeyProvider()
        // if (active != null) {
        //    accountsCache.retainOnly(setOf(active))
        // }
    }
}
