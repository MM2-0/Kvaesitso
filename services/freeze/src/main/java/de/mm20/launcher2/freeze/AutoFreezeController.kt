package de.mm20.launcher2.freeze
import de.mm20.launcher2.i18n.R as I18nR

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.SystemClock
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import de.mm20.launcher2.network.NetState
import de.mm20.launcher2.network.NetworkEngine
import de.mm20.launcher2.network.api.AppDirectory
import de.mm20.launcher2.network.api.AppRule
import de.mm20.launcher2.network.api.FirewallController
import de.mm20.launcher2.preferences.freeze.FreezeSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Watches for screen-off, idle timeout, and battery-saver events and freezes the apps the user
 * explicitly opted in via settings ([FreezeSettings.candidates]) - minus whatever
 * [FreezeExclusionChecker] vetoes (foreground, active notification/media session/foreground
 * service, Android Auto, or a per-app "never freeze" override).
 *
 * Trigger enablement and the idle timeout come from [FreezeProfileManager.resolvedSettings], i.e.
 * whichever [de.mm20.launcher2.preferences.FreezeProfile] the user has selected.
 *
 * It only ever acts on the explicit opt-in candidate list, never on "whatever's running in the
 * background".
 *
 * Also keeps a "N apps ready to freeze" notification in sync (see [updateUnfrozenNotification]),
 * so the user can freeze on demand without waiting for screen-off/idle/battery-saver to trigger.
 *
 * Registered as a Koin singleton so it's created (and starts listening) once, at app start.
 */
class AutoFreezeController internal constructor(
    private val context: Context,
    private val freezeManager: FreezeManager,
    private val settings: FreezeSettings,
    private val exclusionChecker: FreezeExclusionChecker,
    private val profileManager: FreezeProfileManager,
    private val firewall: FirewallController,
    private val apps: AppDirectory,
    private val networkEngine: NetworkEngine,
    private val usageStatsProvider: AppUsageStatsProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var idleJob: Job? = null
    private val screenCycle = java.util.concurrent.atomic.AtomicLong(0)
    private val screenOffMutex = kotlinx.coroutines.sync.Mutex()
    private fun screenOffAlarm() = PendingIntent.getBroadcast(context, 4830,
        Intent(context, ScreenOffFreezeReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    private fun bootCount() = android.provider.Settings.Global.getInt(
        context.contentResolver, android.provider.Settings.Global.BOOT_COUNT, -1)
    private fun isScreenOn() = context.getSystemService<PowerManager>()?.isInteractive != false

    private suspend fun clearScreenOffPlan() {
        context.getSystemService<AlarmManager>()?.cancel(screenOffAlarm())
        settings.setScreenOffPending(emptyMap(), bootCount())
    }

    /** Also used by the manifest receiver when Android recreates the launcher for its alarm. */
    suspend fun dispatchScreenOffAlarm() {
        screenOffMutex.lock()
        try {
            if (!screenOffPlanIsValid(isScreenOn(), settings.autoFreezeEnabled.first(),
                settings.screenOffBoot.first(), bootCount())) {
                clearScreenOffPlan()
                return
            }
            val pending = settings.screenOffPending.first()
            val now = SystemClock.elapsedRealtime()
            val due = pending.filterValues { it <= now }.keys
            // Consume this deadline even if an app is excluded. Never bypass safety/maintenance.
            val remaining = pending - due
            settings.setScreenOffPending(remaining, bootCount())
            if (due.isNotEmpty() && !isScreenOn()) freezeCandidates(due)
            remaining.values.minOrNull()?.let { deadline ->
                context.getSystemService<AlarmManager>()?.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, screenOffAlarm())
            }
        } finally { screenOffMutex.unlock() }
    }
    private var playWatchJob: Job? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
                Intent.ACTION_SCREEN_ON -> onScreenOn()
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> onPowerSaveModeChanged()
            }
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshNotificationLater()
            val pkg = intent.data?.schemeSpecificPart ?: return
            if (intent.action == Intent.ACTION_PACKAGE_REPLACED ||
                intent.action == Intent.ACTION_PACKAGE_ADDED ||
                intent.action == Intent.ACTION_PACKAGE_CHANGED
            ) {
                scope.launch { maybeRefreezeUpdated(pkg) }
            }
        }
    }

    private val freezeNowReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            freezeAllCandidatesNow()
        }
    }

    init {
        freezeManager.onUserUnfreeze = { pkg -> scope.launch { onUserOpenedApp(pkg) } }
        ContextCompat.registerReceiver(context, packageReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addDataScheme("package")
            }, ContextCompat.RECEIVER_EXPORTED)
        ContextCompat.registerReceiver(context, packageReceiver,
            IntentFilter().apply { addAction(Intent.ACTION_PACKAGES_SUSPENDED); addAction(Intent.ACTION_PACKAGES_UNSUSPENDED) }, ContextCompat.RECEIVER_EXPORTED)
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
        ContextCompat.registerReceiver(
            context,
            freezeNowReceiver,
            IntentFilter(ACTION_FREEZE_NOW),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        ContextCompat.registerReceiver(
            context,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    refreezeAfterPlayUpdates()
                }
            },
            IntentFilter(ACTION_PLAY_UPDATE_REFREEZE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        scope.launch {
            val until = runCatching { settings.playUpdatePauseUntil.first() }.getOrDefault(0L)
            if (until > System.currentTimeMillis()) {
                startPlayWatch(until)
            } else if (until > 0L) {
                refreezeAfterPlayUpdates()
            }
        }

        scope.launch {
            try {
                freezeManager.refreshBackendState()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("AutoFreezeController", "backend refresh failed", e)
            }
        }

        // Keep the "ready to freeze" notification in sync with the candidate list itself (e.g.
        // the user just added/removed a candidate in settings), on top of the trigger-driven
        // refreshes below. Deliberately not polled continuously - that would work against the
        // whole point of a battery-saving feature; it's refreshed at every point this class
        // already wakes up for, which covers the common cases.
        scope.launch {
            combine(settings.candidates, settings.autoFreezeEnabled, settings.stats, settings.maintenance, settings.playUpdatePauseUntil) { a, b, c, d, e ->
                listOf(a, b, c, d, e)
            }.collectLatest {
                try {
                    updateUnfrozenNotification()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w("AutoFreezeController", "notification update failed", e)
                }
            }
        }
    }

    private fun refreshNotificationLater() {
        scope.launch {
            try { updateUnfrozenNotification() }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) {
                context.getSystemService<NotificationManager>()?.cancel(NOTIFICATION_ID)
                android.util.Log.w("AutoFreezeController", "notification refresh failed", error)
            }
        }
    }

    private fun onScreenOff() {
        val cycle = screenCycle.incrementAndGet()
        idleJob?.cancel()
        idleJob = scope.launch {
            screenOffMutex.lock()
            try {
                if (screenCycle.get() != cycle) return@launch
                clearScreenOffPlan()
                if (!settings.autoFreezeEnabled.first() || isScreenOn()) return@launch
                exclusionChecker.pinnedScreenOffPackage =
                    usageStatsProvider.currentForegroundPackage() ?: usageStatsProvider.lastForegroundPackage()
                val resolved = profileManager.resolvedSettings.first()
                val now = SystemClock.elapsedRealtime()
                val pending = screenOffFreezeDelays(settings.candidates.first(), settings.screenOffDelays.first(),
                    resolved.freezeOnScreenOff, resolved.freezeOnIdle, resolved.idleTimeoutMinutes)
                    .mapValues { (_, delay) -> now + delay }
                settings.setScreenOffPending(pending, bootCount())
            } finally { screenOffMutex.unlock() }
            dispatchScreenOffAlarm()

            // Continuous ticker while screen remains off to guarantee auto-freeze
            // even if AlarmManager exact alarms are throttled or delayed by Doze mode.
            while (!isScreenOn() && screenCycle.get() == cycle) {
                delay(10_000L)
                if (!isScreenOn() && screenCycle.get() == cycle) {
                    dispatchScreenOffAlarm()
                }
            }
        }
    }

    private fun onScreenOn() {
        val cycle = screenCycle.incrementAndGet()
        idleJob?.cancel()
        idleJob = null
        scope.launch {
            screenOffMutex.lock()
            try {
                if (screenCycle.get() == cycle) {
                    exclusionChecker.pinnedScreenOffPackage = null
                    clearScreenOffPlan()
                }
            } finally { screenOffMutex.unlock() }
        }
        refreshNotificationLater()
    }

    private fun onPowerSaveModeChanged() {
        val powerManager = context.getSystemService<PowerManager>() ?: return
        if (!powerManager.isPowerSaveMode) return
        scope.launch {
            if (!settings.autoFreezeEnabled.first()) return@launch
            if (profileManager.resolvedSettings.first().freezeOnBatterySaver) {
                freezeCandidates()
            }
        }
    }

    /**
     * Manual trigger (e.g. the "Freeze now" widget button, or the "ready to freeze" notification):
     * freezes every candidate the user opted in, same exclusion checks as the automatic triggers
     * above - it does not bypass them.
     */
    fun freezeAllCandidatesNow() {
        scope.launch { freezeCandidates() }
    }

    /**
     * Thaw [packages] for Play Store updates and pause auto-freeze for [durationMs] as a fallback.
     * Each package is frozen again as soon as its installed versionCode rises.
     * When [blockNetwork] is true and Telos Network VPN is on, those apps cannot use the internet
     * until the user opens them.
     */
    fun pauseForPlayUpdates(
        packages: Collection<String>,
        durationMs: Long = PLAY_UPDATE_PAUSE_MS,
        blockNetwork: Boolean = false,
    ) {
        scope.launch {
            val vending = CompanionRules.PLAY_STORE
            val playWasFrozen = freezeManager.isFrozen(vending)
            if (playWasFrozen) freezeManager.unfreeze(vending, fromUser = false)
            val thawed = packages.filter { freezeManager.isFrozen(it) }.toSet()
            thawed.forEach { freezeManager.unfreeze(it, fromUser = false) }
            val watch = thawed.ifEmpty { emptySet() }
            val baselines = watch.associateWith { versionCode(it) }
            val until = System.currentTimeMillis() + durationMs
            val netBlocked = if (blockNetwork && networkEngine.state.value is NetState.On && watch.isNotEmpty()) {
                applyPlayNetworkBlock(watch)
            } else emptyMap()
            settings.startPlayUpdatePause(
                packages = watch,
                untilMillis = until,
                baselines = baselines,
                netBlocked = netBlocked,
                playStoreHeld = playWasFrozen,
            )
            val am = context.getSystemService<AlarmManager>()
            val pi = PendingIntent.getBroadcast(
                context,
                1,
                Intent(ACTION_PLAY_UPDATE_REFREEZE).setPackage(context.packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            if (watch.isNotEmpty() || playWasFrozen) {
                am?.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + durationMs, pi)
                notifyPlayUpdatePause(watch.size + if (playWasFrozen) 1 else 0, durationMs)
                startPlayWatch(until)
            }
        }
    }

    private fun startPlayWatch(untilMillis: Long) {
        playWatchJob?.cancel()
        playWatchJob = scope.launch {
            while (System.currentTimeMillis() < untilMillis) {
                val thawed = settings.playUpdateThawed.first()
                if (thawed.isEmpty() && !settings.playUpdatePlayStoreHeld.first()) return@launch
                thawed.forEach { maybeRefreezeUpdated(it) }
                delay(5_000L)
            }
            refreezeAfterPlayUpdates()
        }
    }

    private suspend fun maybeRefreezeUpdated(packageName: String) {
        val thawed = settings.playUpdateThawed.first()
        if (packageName !in thawed) return
        val baseline = settings.playUpdateBaselines.first()[packageName] ?: 0L
        if (versionCode(packageName) <= baseline) return
        refreezePackages(listOf(packageName))
    }

    fun refreezeAfterPlayUpdates() {
        scope.launch {
            val thawed = settings.playUpdateThawed.first()
            refreezePackages(thawed)
        }
    }

    private suspend fun refreezePackages(packages: Collection<String>) {
        val candidates = settings.candidates.first()
        if (packages.isNotEmpty()) {
            val toFreeze = packages.filter { it in candidates }
            settings.removePlayUpdateThawed(packages)
            if (toFreeze.isNotEmpty()) {
                freezeManager.refreshBackendState()
                freezeManager.freezeInBackground(toFreeze)
            }
        }
        if (packages.isEmpty() || settings.playUpdateThawed.first().isEmpty()) {
            playWatchJob?.cancel()
            context.getSystemService<NotificationManager>()?.cancel(PLAY_PAUSE_NOTIFICATION_ID)
            val am = context.getSystemService<AlarmManager>()
            val pi = PendingIntent.getBroadcast(
                context,
                1,
                Intent(ACTION_PLAY_UPDATE_REFREEZE).setPackage(context.packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            am?.cancel(pi)
            val holdPlay = settings.playUpdatePlayStoreHeld.first()
            settings.clearPlayUpdatePause()
            if (holdPlay) {
                val vending = CompanionRules.PLAY_STORE
                val held = settings.companionHolds.first()
                val candidatesNow = settings.candidates.first()
                if (vending in candidatesNow && vending !in held && !freezeManager.isFrozen(vending)) {
                    freezeManager.freeze(vending)
                }
            }
        }
    }

    private suspend fun applyPlayNetworkBlock(packages: Set<String>): Map<String, Boolean> {
        val result = HashMap<String, Boolean>()
        for (pkg in packages) {
            val appId = apps.byPackage(pkg)?.appId ?: runCatching {
                context.packageManager.getApplicationInfo(pkg, 0).uid % 100000
            }.getOrNull() ?: continue
            val existing = firewall.appRules.value[appId] ?: AppRule(appId)
            result[pkg] = existing.blockAll
            if (!existing.blockAll) {
                firewall.setAppRule(existing.copy(blockAll = true))
            }
        }
        return result
    }

    private suspend fun onUserOpenedApp(packageName: String) {
        val blocked = settings.playUpdateNetBlocked.first()
        val hadBlockAll = blocked[packageName] ?: return
        if (!hadBlockAll) {
            val appId = apps.byPackage(packageName)?.appId ?: runCatching {
                context.packageManager.getApplicationInfo(packageName, 0).uid % 100000
            }.getOrNull()
            if (appId != null) {
                val existing = firewall.appRules.value[appId]
                if (existing != null && existing.blockAll) {
                    firewall.setAppRule(existing.copy(blockAll = false))
                }
            }
        }
        settings.clearPlayUpdateNetBlocked(listOf(packageName))
    }

    private fun versionCode(packageName: String): Long {
        return runCatching {
            context.packageManager.getPackageInfo(
                packageName,
                PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS,
            ).longVersionCode
        }.getOrDefault(0L)
    }

    @SuppressLint("MissingPermission")
    private fun notifyPlayUpdatePause(thawedCount: Int, durationMs: Long) {
        val nm = context.getSystemService<NotificationManager>() ?: return
        if (nm.getNotificationChannel(PLAY_PAUSE_CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    PLAY_PAUSE_CHANNEL,
                    context.getString(I18nR.string.play_update_pause_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
        }
        val hours = (durationMs / 3_600_000L).coerceAtLeast(1L)
        val text = context.getString(I18nR.string.play_update_pause_text, hours.toInt(), thawedCount)
        val notification = NotificationCompat.Builder(context, PLAY_PAUSE_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(I18nR.string.play_update_pause_title))
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(PLAY_PAUSE_NOTIFICATION_ID, notification)
        }
    }

    /** A confirmed last-task removal; independent of screen-off timing and the master auto switch. */
    suspend fun freezeAfterRecentsRemoval(packageName: String, stillRemoved: suspend () -> Boolean = { true }) {
        if (!settings.freezeOnRecentsRemoval.first() || freezeManager.isFrozen(packageName) || !stillRemoved()) return
        if (packageName !in settings.candidates.first() && packageName !in settings.recentsThawed.first()) return
        if (packageName in settings.neverFreezeApps.first() ||
            packageName in settings.companionHolds.first() ||
            settings.maintenance.first().values.any { it.packageName == packageName } ||
            settings.playUpdatePauseUntil.first() > System.currentTimeMillis() ||
            exclusionChecker.isExcluded(packageName, confirmedTaskRemoval = true)) return
        freezeManager.refreshBackendState()
        if (!stillRemoved()) return
        freezeManager.freezeAfterTaskRemoval(packageName) {
            settings.freezeOnRecentsRemoval.first() && stillRemoved()
        }
    }

    private suspend fun freezableCandidates(): List<String> {
        val candidates = settings.candidates.first()
        if (candidates.isEmpty()) return emptyList()
        val held = settings.maintenance.first().values.map { it.packageName }.toSet()
        val companionHolds = settings.companionHolds.first().keys
        return candidates.filter { pkg -> runCatching {
            val info = context.packageManager.getApplicationInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_UNINSTALLED_PACKAGES)
            info.flags and android.content.pm.ApplicationInfo.FLAG_INSTALLED != 0
        }.getOrDefault(false) }.filterNot { it in held }
            .filterNot { it in companionHolds }
            .filterNot { freezeManager.isFrozen(it) }
            .filterNot { exclusionChecker.isExcluded(it) }
    }

    private suspend fun freezeCandidates(onlyPackages: Set<String>? = null) {
        val pauseUntil = settings.playUpdatePauseUntil.first()
        if (pauseUntil > System.currentTimeMillis()) return
        // A backend that dies (Shizuku binder gone, root denied, ...) must not crash the launcher
        // from this background scope.
        try {
            val freezable = freezableCandidates().filter { onlyPackages == null || it in onlyPackages }
            if (freezable.isEmpty()) { updateUnfrozenNotification(); return }
            freezeManager.refreshBackendState()
            // Not freeze(): this runs from a background trigger (screen-off/idle/battery-saver, no
            // foreground activity), and Island's freeze mechanism needs a foreground context to
            // launch its Activity - see FreezeManager.freezeInBackground.
            freezeManager.freezeInBackground(freezable)
            updateUnfrozenNotification()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("AutoFreezeController", "auto freeze failed", e)
        }
    }

    /**
     * Shows (or updates, or cancels) a notification listing how many freeze candidates aren't
     * currently frozen, so the user can freeze them on demand rather than waiting for the next
     * screen-off/idle/battery-saver trigger. Gated by [FreezeSettings.autoFreezeEnabled] - this is
     * part of the auto-freeze feature, not a standalone thing, so it stays quiet if that's off.
     */
    @SuppressLint("MissingPermission")
    private suspend fun updateUnfrozenNotification() {
        val nm = context.getSystemService<NotificationManager>() ?: return
        if (!settings.autoFreezeEnabled.first()) {
            nm.cancel(NOTIFICATION_ID)
            return
        }
        val freezable = freezableCandidates()
        if (freezable.isEmpty()) {
            nm.cancel(NOTIFICATION_ID)
            return
        }

        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(I18nR.string.freeze_ready_notification_channel),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }

        val freezeNowIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_FREEZE_NOW).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val pm = context.packageManager
        val appLabels = freezable.map { pkg ->
            runCatching {
                pm.getApplicationInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_UNINSTALLED_PACKAGES)
                    .loadLabel(pm).toString()
            }.getOrDefault(pkg)
        }.sorted()

        val labelsSummary = appLabels.joinToString(", ")

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(
                context.resources.getQuantityString(
                    I18nR.plurals.freeze_ready_notification_title, freezable.size, freezable.size
                )
            )
            .setContentText(labelsSummary)
            .setStyle(NotificationCompat.InboxStyle().also { style ->
                style.setBigContentTitle(
                    context.resources.getQuantityString(
                        I18nR.plurals.freeze_ready_notification_title, freezable.size, freezable.size
                    )
                )
                style.setSummaryText(context.getString(I18nR.string.freeze_ready_notification_text))
                appLabels.forEach { style.addLine(it) }
            })
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(freezeNowIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // areNotificationsEnabled() covers POST_NOTIFICATIONS on API 33+ and the per-app switch below it
        // (checkSelfPermission(POST_NOTIFICATIONS) reports "denied" on API 26-32 even when notifications work).
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "freeze_ready"
        private const val NOTIFICATION_ID = 4822
        private const val PLAY_PAUSE_CHANNEL = "play_update_pause"
        private const val PLAY_PAUSE_NOTIFICATION_ID = 4823
        private const val ACTION_FREEZE_NOW = "de.mm20.launcher2.freeze.ACTION_FREEZE_NOW"
        const val ACTION_PLAY_UPDATE_REFREEZE = "de.mm20.launcher2.freeze.ACTION_PLAY_UPDATE_REFREEZE"
        const val PLAY_UPDATE_PAUSE_MS = 60L * 60L * 1000L
    }
}
