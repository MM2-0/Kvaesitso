package de.mm20.launcher2.freeze

import kotlinx.coroutines.ensureActive

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserHandle
import android.content.pm.LauncherApps
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.util.Log
import de.mm20.launcher2.freeze.providers.RootProvider
import de.mm20.launcher2.freeze.providers.ShizukuProvider
import de.mm20.launcher2.freeze.providers.IslandProvider
import de.mm20.launcher2.freeze.providers.DeviceOwnerProvider
import de.mm20.launcher2.freeze.providers.DeviceOwnerAdminReceiver
import de.mm20.launcher2.freeze.providers.DhizukuProvider
import de.mm20.launcher2.preferences.FreezeBackendPreference
import de.mm20.launcher2.preferences.FreezeMethod
import de.mm20.launcher2.preferences.freeze.FreezeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

class FreezeManager internal constructor(
    private val context: Context,
    private val settings: FreezeSettings,
    private val usageStats: AppUsageStatsProvider,
    private val descriptions: BloatDescriptions,
) {
    // === TELOS_PENDING_REVIEW_START: thor_freezer_features ===
    private val dhizukuProvider = DhizukuProvider(context)
    
    // Critical system packages to protect from freezing
    private val PROTECTED_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.keyguard",
        "com.android.server.telecom",
        "com.google.android.gms",
        "com.android.vending",
        "moe.shizuku.privileged.api",
        "io.github.muntashirakon.dhizuku",
        context.packageName
    )
    // === TELOS_PENDING_REVIEW_END: thor_freezer_features ===

    private val operationMutex = Mutex()
    private val companionMutex = Mutex()

    private val shizukuProvider = ShizukuProvider(context.packageName)
    private val rootProvider = RootProvider()
    private val islandProvider = IslandProvider(context)
    private val deviceOwnerProvider = DeviceOwnerProvider(context)

    private val _activeBackend = MutableStateFlow<FreezeBackendType?>(null)
    val activeBackend: StateFlow<FreezeBackendType?> = _activeBackend.asStateFlow()

    private val maintenance by lazy {
        FreezeMaintenance(context, settings, operationMutex, { it in PROTECTED_PACKAGES }, ::maintenanceProvider, usageStats::currentForegroundPackage)
    }
    val maintenanceEntries get() = settings.maintenance
    suspend fun scanFrozenApps() = maintenance.scan()
    suspend fun unfreezeAll(groups: Set<FrozenAppGroup>) = maintenance.unfreezeAll(groups)
    suspend fun restoreFrozenApps() = maintenance.restoreAll()
    suspend fun recoverFrozenUpdates() = maintenance.recoverInstalls()
    suspend fun beginFrozenUpdate(packageName: String, backend: String, thawFirst: Boolean = true) = maintenance.beginInstall(packageName, backend, thawFirst)
    suspend fun finishFrozenUpdate(packageName: String, token: String) = maintenance.finishInstall(packageName, token)
    suspend fun hasPendingFrozenUpdate(packageName: String) = settings.maintenance.first().values.any {
        it.packageName == packageName && it.userId == Process.myUid() / 100000 && it.operationId.isNotEmpty()
    }
    suspend fun launchThawed(packageName: String, user: UserHandle, preferred: ComponentName,
        launch: (ComponentName) -> Boolean): Boolean {
        val wasFrozen = user == Process.myUserHandle() && withContext(kotlinx.coroutines.Dispatchers.IO) { isFrozen(packageName) }
        val started = maintenance.launchThawed(packageName, user, preferred, launch)
        if (started && wasFrozen) {
            settings.trackRecentsThaw(packageName)
            onUserUnfreeze?.invoke(packageName)
        }
        return started
    }

    private suspend fun maintenanceProvider(name: String?): Pair<String, PrivilegedAccessProvider>? {
        if (name == null) refreshBackendState()
        val type = if (name.isNullOrEmpty()) _activeBackend.value else
            FreezeBackendType.entries.firstOrNull { it.name == name }
        val provider = when (type) {
            FreezeBackendType.Root -> rootProvider
            FreezeBackendType.Shizuku -> shizukuProvider
            FreezeBackendType.DeviceOwner -> deviceOwnerProvider
            FreezeBackendType.Dhizuku -> dhizukuProvider
            else -> return null
        }
        return if (provider.isAvailable() && provider.hasPermission()) type.name to provider else null
    }

    /** Re-evaluates which backend (if any) is currently usable, based on the user's preference. */
    suspend fun refreshBackendState() {
        _activeBackend.value = when (settings.backend.first()) {
            FreezeBackendPreference.ShizukuOnly ->
                FreezeBackendType.Shizuku.takeIf { shizukuProvider.isAvailable() }

            // === TELOS_PENDING_REVIEW_START: thor_freezer_features ===
            FreezeBackendPreference.DhizukuOnly ->
                FreezeBackendType.Dhizuku.takeIf { dhizukuProvider.isAvailable() }
            // === TELOS_PENDING_REVIEW_END: thor_freezer_features ===

            FreezeBackendPreference.RootOnly ->
                FreezeBackendType.Root.takeIf { rootProvider.isAvailable() }

            FreezeBackendPreference.Island ->
                FreezeBackendType.Island.takeIf { islandProvider.isAvailable() }

            FreezeBackendPreference.DeviceOwnerOnly ->
                FreezeBackendType.DeviceOwner.takeIf { deviceOwnerProvider.isAvailable() }

            FreezeBackendPreference.Auto -> when {
                shizukuProvider.isAvailable() -> FreezeBackendType.Shizuku
                dhizukuProvider.isAvailable() -> FreezeBackendType.Dhizuku
                rootProvider.isAvailable() -> FreezeBackendType.Root
                deviceOwnerProvider.isAvailable() -> FreezeBackendType.DeviceOwner
                islandProvider.isAvailable() -> FreezeBackendType.Island
                else -> null
            }
        }
    }

    suspend fun hasPermission(): Boolean = when (_activeBackend.value) {
        FreezeBackendType.Shizuku -> shizukuProvider.hasPermission()
        FreezeBackendType.Dhizuku -> dhizukuProvider.hasPermission()
        FreezeBackendType.Root -> rootProvider.hasPermission()
        FreezeBackendType.Island -> true // Intent based, no runtime permission for us
        FreezeBackendType.DeviceOwner -> deviceOwnerProvider.hasPermission()
        null -> false
    }

    /** Prompts for permission on the active backend, if any. Suspends until the user responds. */
    suspend fun requestPermission(): Boolean = when (_activeBackend.value) {
        FreezeBackendType.Shizuku -> shizukuProvider.requestPermission()
        FreezeBackendType.Dhizuku -> dhizukuProvider.requestPermission()
        FreezeBackendType.Root -> rootProvider.requestPermission()
        FreezeBackendType.Island -> true
        FreezeBackendType.DeviceOwner -> deviceOwnerProvider.requestPermission()
        null -> false
    }

    // === TELOS_PENDING_REVIEW_START: multi_user_freeze ===
    suspend fun freeze(packageName: String, userId: Int = Process.myUid() / 100000): Set<String> {
        if (_activeBackend.value == null) refreshBackendState()
        val result = setSuspended(listOf(packageName), true, userId)
        freezeHeldCompanions(result, userId)
        return result
    }

    /** Explicit dependencies only; disabled by default. Shared companions keep all active owners. */
    suspend fun unfreezeCompanions(master: String, userId: Int): Set<String> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        companionMutex.withLock {
        if (userId != Process.myUid() / 100000 || master !in settings.companionEnabled.first()) return@withLock emptySet()
        val packages = settings.companionExtras.first()[master].orEmpty().split(',')
            .map { it.trim() }.filter { it != master && VALID_PACKAGE_NAME.matches(it) }.distinct()
        val existingHolds = settings.companionHolds.first().keys
        val thawed = linkedSetOf<String>()
        for (pkg in packages) {
            if (pkg in existingHolds) thawed += pkg
            else if (isFrozen(pkg)) {
                if (pkg !in unfreeze(pkg, userId, fromUser = false)) {
                    settings.addCompanionHolds(master, thawed)
                    releaseCompanionsLocked(master, userId)
                    error("Could not unfreeze companion $pkg")
                }
                thawed += pkg
            }
        }
        settings.addCompanionHolds(master, thawed)
        thawed
        }
    }

    suspend fun releaseCompanions(master: String, userId: Int) = withContext(kotlinx.coroutines.Dispatchers.IO) {
        companionMutex.withLock { releaseCompanionsLocked(master, userId) }
    }

    private suspend fun releaseCompanionsLocked(master: String, userId: Int) {
        if (userId != Process.myUid() / 100000) return
        val released = settings.clearCompanionHoldsForMaster(master)
        if (released.isNotEmpty()) setSuspended(released.toList(), true, userId)
    }

    /** Called after a user-driven unfreeze (launcher tap), not after Play-update thaw. */
    var onUserUnfreeze: ((String) -> Unit)? = null

    suspend fun unfreeze(
        packageName: String,
        userId: Int = Process.myUid() / 100000,
        fromUser: Boolean = true,
    ): Set<String> {
        if (_activeBackend.value == null) refreshBackendState()
        val result = setSuspended(listOf(packageName), false, userId)
        if (fromUser && packageName in result) {
            if (userId == Process.myUid() / 100000) settings.trackRecentsThaw(packageName)
            onUserUnfreeze?.invoke(packageName)
        }
        return result
    }

    suspend fun freeze(packageNames: List<String>, userId: Int = Process.myUid() / 100000): Set<String> {
        if (_activeBackend.value == null) refreshBackendState()
        val result = setSuspended(packageNames, true, userId)
        freezeHeldCompanions(result, userId)
        return result
    }

    /**
     * Same as [freeze], except when the active backend is [FreezeBackendType.Island]: Island's
     * freeze mechanism is an Activity intent, and Android silently drops Activity launches from
     * a background process (no foreground UI) since API 29 - calling [freeze] directly from
     * AutoFreezeController's screen-off/idle/battery-saver triggers would report success and
     * freeze nothing. This posts a notification instead; the user's tap on it is exempt from that
     * restriction. Shizuku/Root go through the same binder/shell path as [freeze] - no background
     * restriction applies to them, so they freeze immediately as normal.
     */
    suspend fun freezeInBackground(packageNames: List<String>, userId: Int = Process.myUid() / 100000) {
        if (packageNames.isEmpty()) return
        if (_activeBackend.value == null) refreshBackendState()
        if (_activeBackend.value == FreezeBackendType.Island) {
            val approved = settings.systemRiskApproved.first()
            val safe = packageNames.filter { VALID_PACKAGE_NAME.matches(it) && it !in PROTECTED_PACKAGES &&
                descriptions.allowsFreeze(it, it in approved, userId) }
            if (safe.isNotEmpty()) islandProvider.postFreezeNotification(safe)
        } else {
            val changed = operationMutex.withLock { applySuspended(packageNames, true, userId, respectMaintenance = true) }
            freezeHeldCompanions(changed, userId)
        }
    }

    /** Only a current, privileged task snapshot may replace the stale UsageStats foreground hint. */
    internal suspend fun freezeAfterTaskRemoval(packageName: String, stillRemoved: suspend () -> Boolean) {
        if (_activeBackend.value == FreezeBackendType.Island) return // Recents monitoring requires Shizuku/root.
        val changed = operationMutex.withLock {
            if (!stillRemoved()) return@withLock emptySet<String>()
            applySuspended(listOf(packageName), true, Process.myUid() / 100000,
                respectMaintenance = true, taskRemovalProof = stillRemoved)
        }
        freezeHeldCompanions(changed, Process.myUid() / 100000)
    }

    private suspend fun freezeHeldCompanions(masters: Set<String>, userId: Int) {
        if (masters.isEmpty() || userId != Process.myUid() / 100000) return
        companionMutex.withLock {
        val extras = mutableListOf<String>()
        for (master in masters) {
            extras += settings.clearCompanionHoldsForMaster(master)
        }
        if (extras.isNotEmpty()) setSuspended(extras, true, userId)
        }
    }

    /** Apply a method change to an already frozen app before committing its new policy. */
    suspend fun updateAppPolicy(
        packageName: String, user: UserHandle, candidate: Boolean, neverFreeze: Boolean,
        method: FreezeMethod? = null,
    ): Boolean = withContext(NonCancellable) {
        // Once started, finish the policy transaction even if its settings screen closes.
        operationMutex.withLock {
            if (!VALID_PACKAGE_NAME.matches(packageName)) return@withLock false
            val userId = user.hashCode()
            val frozen = candidate && if (userId == Process.myUid() / 100000) isFrozen(packageName) else {
                // PackageManager on the launcher context describes only the current user.
                val launcherApps = requireNotNull(context.getSystemService(LauncherApps::class.java))
                val info = launcherApps.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS, user)
                !launcherApps.isPackageEnabled(packageName, user) || !info.enabled ||
                        (info.flags and ApplicationInfo.FLAG_SUSPENDED) != 0
            }
            // PackageManager on the launcher context describes only the current user.
            if (candidate && frozen) {
                if (_activeBackend.value == null) refreshBackendState()
                // Island exposes a hide intent, not a synchronous suspend/disable conversion.
                if (_activeBackend.value == FreezeBackendType.Island) return@withLock false
                val changed = applySuspended(listOf(packageName), true, userId,
                    mapOf(packageName to (method ?: FreezeMethod.Suspend)))
                if (packageName !in changed || !methodApplied(packageName, user, method ?: FreezeMethod.Suspend))
                    return@withLock false
            }
            settings.setAppPolicy(packageName, candidate, neverFreeze, method)
            true
        }
    }

    /** Do not treat a successful shell exit as proof that Android accepted a mode change. */
    private fun methodApplied(packageName: String, user: UserHandle, method: FreezeMethod): Boolean {
        if (user == Process.myUserHandle()) {
            val info = context.packageManager.getApplicationInfo(packageName,
                PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_UNINSTALLED_PACKAGES)
            val suspended = (info.flags and ApplicationInfo.FLAG_SUSPENDED) != 0
            val state = freezeState(packageName)
            return if (method == FreezeMethod.Suspend) state == AppFreezeState.Suspended
                else !suspended && (state == AppFreezeState.Disabled || state == AppFreezeState.Hidden)
        }
        val apps = requireNotNull(context.getSystemService(LauncherApps::class.java))
        val info = apps.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS, user)
        val suspended = (info.flags and ApplicationInfo.FLAG_SUSPENDED) != 0
        val enabled = info.enabled && apps.isPackageEnabled(packageName, user)
        return if (method == FreezeMethod.Suspend) suspended && enabled else !suspended && !enabled
    }

    /** @return the subset of [packageNames] that were actually toggled successfully. */
    private suspend fun setSuspended(packageNames: List<String>, suspended: Boolean, userId: Int = Process.myUid() / 100000): Set<String> =
        operationMutex.withLock { applySuspended(packageNames, suspended, userId) }

    private suspend fun applySuspended(
        packageNames: List<String>, suspended: Boolean, userId: Int,
        methodOverride: Map<String, FreezeMethod>? = null,
        respectMaintenance: Boolean = false,
        taskRemovalProof: (suspend () -> Boolean)? = null,
    ): Set<String> {
        if (packageNames.isEmpty()) return emptySet()

        // === TELOS_PENDING_REVIEW_START: thor_freezer_features ===
        // Package names end up in shell commands (root / Shizuku fallbacks), so anything that is not a
        // well-formed package name is dropped here rather than trusted.
        val held = if (respectMaintenance) settings.maintenance.first().values.map { it.packageName }.toSet() else emptySet()
        val companionHolds = if (suspended) settings.companionHolds.first().keys else emptySet()
        val foreground = if (taskRemovalProof == null && suspended && userId == Process.myUid() / 100000 && settings.excludeForeground.first()) usageStats.currentForegroundPackage() else null
        val safePackages = packageNames.filter { VALID_PACKAGE_NAME.matches(it) }
            .filterNot { (suspended && PROTECTED_PACKAGES.contains(it)) || it in held || it in companionHolds || it == foreground }
        if (safePackages.isEmpty()) {
            Log.w(TAG, "All targeted packages are protected system packages. Aborting freeze.")
            return emptySet()
        }
        val approved = if (suspended) settings.systemRiskApproved.first() else emptySet()
        val targetPackages = if (suspended) safePackages.filter { descriptions.allowsFreeze(it, it in approved, userId) } else safePackages
        if (targetPackages.isEmpty()) return emptySet()
        // === TELOS_PENDING_REVIEW_END: thor_freezer_features ===

        val backend = _activeBackend.value
        if (backend == null) {
            Log.w(TAG, "setSuspended($targetPackages, $suspended, user $userId): no active backend")
            return emptySet()
        }
        if (userId != Process.myUid() / 100000 &&
            (backend == FreezeBackendType.DeviceOwner || backend == FreezeBackendType.Dhizuku)) {
            Log.w(TAG, "$backend cannot change packages for another user")
            return emptySet()
        }
        if (!hasPermission()) {
            Log.w(TAG, "setSuspended($targetPackages, $suspended, user $userId): $backend has no permission")
            return emptySet()
        }
        val methods = methodOverride ?: settings.freezeMethods.first()
        kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.ensureActive()
        if (taskRemovalProof != null && (!taskRemovalProof() ||
            targetPackages.any { it in settings.neverFreezeApps.first() })) return emptySet()
        val succeeded = if (backend == FreezeBackendType.Island) {
            if (islandProvider.setPackagesSuspended(targetPackages, suspended, userId)) targetPackages.toSet()
            else emptySet()
        } else {
            val provider: PrivilegedAccessProvider = when (backend) {
                FreezeBackendType.Shizuku -> shizukuProvider
                FreezeBackendType.Dhizuku -> dhizukuProvider
                FreezeBackendType.Root -> rootProvider
                FreezeBackendType.DeviceOwner -> deviceOwnerProvider
                FreezeBackendType.Island -> error("Island uses its intent provider")
            }
            suspend fun transition(packages: List<String>, disable: Boolean = false) = transitionFreezeState(
                packages, suspended, disable,
                suspendPackages = { names, value -> provider.setPackagesSuspended(names, value, userId) },
                enablePackages = { names, value -> provider.setPackagesEnabled(names, value, userId) },
            )
            if (suspended) {
                transition(targetPackages.filter { methods[it] != FreezeMethod.Disable }) +
                    transition(targetPackages.filter { methods[it] == FreezeMethod.Disable }, disable = true)
            } else transition(targetPackages)
        }

        // A successful shell exit or empty binder failure array is not sufficient evidence.
        val verified = succeeded.filterTo(linkedSetOf()) { pkg ->
            var confirmed = false
            repeat(3) { attempt ->
                if (!confirmed) {
                    if (attempt > 0) kotlinx.coroutines.delay(125)
                    confirmed = withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching {
                            val user = UserHandle.getUserHandleForUid(userId * 100000)
                            val flags = PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_UNINSTALLED_PACKAGES
                            val info = if (userId == Process.myUid() / 100000) context.packageManager.getApplicationInfo(pkg, flags)
                                else requireNotNull(context.getSystemService(LauncherApps::class.java)).getApplicationInfo(pkg, flags, user)
                            info.flags and ApplicationInfo.FLAG_INSTALLED != 0 && if (suspended && backend == FreezeBackendType.Island) freezeState(pkg) != AppFreezeState.Normal
                            else if (suspended) methodApplied(pkg, user, methods[pkg] ?: FreezeMethod.Suspend)
                            else info.enabled && info.flags and ApplicationInfo.FLAG_SUSPENDED == 0 &&
                                    (userId != Process.myUid() / 100000 || freezeState(pkg) == AppFreezeState.Normal)
                        }.getOrDefault(false)
                    }
                }
            }
            confirmed
        }
        if (suspended && userId == Process.myUid() / 100000) settings.forgetRecentsThaw(verified)
        val now = System.currentTimeMillis()
        for (packageName in verified) {
            if (suspended) settings.recordFrozen(packageName, now) else settings.recordUnfrozen(packageName, now)
        }
        return verified
    }

    /** Read Android's actual per-package app-op state; no preference is treated as applied. */
    suspend fun audioModes(packageName: String): Map<String, String?> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        operationMutex.withLock {
            AudioOperation.entries.associate { op -> op.name to runAudioCommand(packageName, op)?.let { parseAudioMode(it, op) } }
        }
    }

    suspend fun setAudioMode(packageName: String, operation: String, mode: String): Boolean =
        withContext(NonCancellable + kotlinx.coroutines.Dispatchers.IO) {
            operationMutex.withLock {
                val op = AudioOperation.entries.firstOrNull { it.name == operation } ?: return@withLock false
                if (packageName in PROTECTED_PACKAGES || packageName == "com.android.phone" ||
                    mode !in setOf("allow", "ignore", "default")) return@withLock false
                // Refuse writes unless this backend can read this app-op on this device.
                val before = runAudioCommand(packageName, op)?.let { parseAudioMode(it, op) } ?: return@withLock false
                if (before == mode) return@withLock true
                if (runAudioCommand(packageName, op, mode) == null) return@withLock false
                val after = runAudioCommand(packageName, op)?.let { parseAudioMode(it, op) }
                if (after == mode) true else {
                    // A shared-UID override may prevent the package setting from taking effect.
                    runAudioCommand(packageName, op, before)
                    false
                }
            }
        }

    private suspend fun runAudioCommand(pkg: String, op: AudioOperation, mode: String? = null): String? {
        if (!VALID_PACKAGE_NAME.matches(pkg)) return null
        val args = audioCommand(pkg, Process.myUid() / 100000, op, mode)
        val command = args.joinToString(" ") { "'" + it.replace("'", "'\\''") + "'" }
        val result = de.mm20.launcher2.ktx.PrivilegedShell.run(command) ?: return null
        return result.output.takeIf { result.exitCode == 0 && it.length <= 8192 }
    }

    /** Root capability is independent of the provider selected for ordinary freezing. */
    suspend fun canEditComponents(): Boolean = withContext(kotlinx.coroutines.Dispatchers.IO) { rootProvider.hasPermission() }

    /** Explicit component edits; root only, scoped to this user's declared package components. */
    suspend fun setComponentState(component: ComponentName, state: Int): Boolean =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            operationMutex.withLock {
                val pkg = component.packageName
                if (!validComponentEdit(pkg, component.className, state, PROTECTED_PACKAGES)) return@withLock false
                if (!rootProvider.hasPermission()) return@withLock false
                val pm = context.packageManager
                val info = runCatching { pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES or
                    PackageManager.GET_RECEIVERS or PackageManager.GET_SERVICES or PackageManager.GET_PROVIDERS or
                    PackageManager.MATCH_DISABLED_COMPONENTS) }.getOrNull() ?: return@withLock false
                val declared = info.activities.orEmpty().map { it.name } + info.receivers.orEmpty().map { it.name } +
                    info.services.orEmpty().map { it.name } + info.providers.orEmpty().map { it.name }
                if (component.className !in declared) return@withLock false
                if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED &&
                    !descriptions.allowsFreeze(pkg, pkg in settings.systemRiskApproved.first(), Process.myUid() / 100000)) return@withLock false
                if (!rootProvider.setComponentSetting(component.flattenToString(), state, Process.myUid() / 100000)) return@withLock false
                runCatching { pm.getComponentEnabledSetting(component) == state }.getOrDefault(false)
            }
        }

    suspend fun forceStop(packageName: String, userId: Int = Process.myUid() / 100000): Boolean {
        // === TELOS_PENDING_REVIEW_START: thor_freezer_features ===
        if (PROTECTED_PACKAGES.contains(packageName) || !VALID_PACKAGE_NAME.matches(packageName)) return false
        // === TELOS_PENDING_REVIEW_END: thor_freezer_features ===
        return when (_activeBackend.value) {
            FreezeBackendType.Shizuku -> shizukuProvider.forceStopPackage(packageName, userId)
            // === TELOS_PENDING_REVIEW_START: thor_freezer_features ===
            FreezeBackendType.Dhizuku -> dhizukuProvider.forceStopPackage(packageName, userId)
            // === TELOS_PENDING_REVIEW_END: thor_freezer_features ===
            FreezeBackendType.Root -> rootProvider.forceStopPackage(packageName, userId)
            else -> false
        }
    }

    suspend fun clearCache(packageName: String, userId: Int = Process.myUid() / 100000): Boolean {
        // === TELOS_PENDING_REVIEW_START: thor_freezer_features ===
        if (PROTECTED_PACKAGES.contains(packageName) || !VALID_PACKAGE_NAME.matches(packageName)) return false
        // === TELOS_PENDING_REVIEW_END: thor_freezer_features ===
        return when (_activeBackend.value) {
            FreezeBackendType.Shizuku -> shizukuProvider.clearCache(packageName, userId)
            // === TELOS_PENDING_REVIEW_START: thor_freezer_features ===
            FreezeBackendType.Dhizuku -> dhizukuProvider.clearCache(packageName, userId)
            // === TELOS_PENDING_REVIEW_END: thor_freezer_features ===
            FreezeBackendType.Root -> rootProvider.clearCache(packageName, userId)
            else -> false
        }
    }
    // === TELOS_PENDING_REVIEW_END: multi_user_freeze ===

    /** Live read of the OS-level frozen state (suspended OR disabled). */
    fun isFrozen(packageName: String): Boolean {
        return freezeState(packageName) != AppFreezeState.Normal
    }

    /**
     * Live read of the OS-level state, distinguishing *how* an app is frozen - unlike [isFrozen],
     * which collapses suspended and disabled into one boolean. Reads PackageManager directly each
     * call, not settings.freezeMethods (the user's configured intent), so this reflects reality
     * even if the two have drifted apart (e.g. the method setting was changed after freezing,
     * without re-freezing).
     */
    fun freezeState(packageName: String): AppFreezeState {
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(
                packageName,
                PackageManager.MATCH_DISABLED_COMPONENTS
            )
            // Some freeze tools disable only the launcher activity component rather than the
            // whole package, in which case ApplicationInfo.enabled stays true - resolve that
            // component explicitly rather than relying on the package-level flag alone.
            val launcherIntent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(packageName)
            val launcherActivity = pm.resolveActivity(
                launcherIntent,
                PackageManager.MATCH_DISABLED_COMPONENTS,
            )?.activityInfo
            val hasEnabledLauncher = pm.queryIntentActivities(launcherIntent, 0).any { it.activityInfo?.enabled == true }
            val isComponentDisabled = isComponentFreeze(hasEnabledLauncher, launcherActivity != null && (
                !launcherActivity.enabled ||
                    pm.getComponentEnabledSetting(
                        ComponentName(launcherActivity.packageName, launcherActivity.name)
                    ) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                ))
            when {
                !info.enabled || isComponentDisabled -> AppFreezeState.Disabled
                (info.flags and ApplicationInfo.FLAG_SUSPENDED) != 0 -> AppFreezeState.Suspended
                else -> AppFreezeState.Normal
            }
        } catch (e: PackageManager.NameNotFoundException) {
            // Not visible to a plain lookup - either truly uninstalled, or hidden via
            // setApplicationHidden (Icebox/Island). MATCH_UNINSTALLED_PACKAGES surfaces both;
            // distinguish by checking whether package data still exists on disk.
            try {
                context.packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.MATCH_UNINSTALLED_PACKAGES
                )
                AppFreezeState.Hidden
            } catch (e: PackageManager.NameNotFoundException) {
                AppFreezeState.Normal
            }
        }
    }

    /** Whether this app currently holds device owner status - only ever true after the user has
     * run the `adb shell dpm set-device-owner` command from the guided setup screen. */
    suspend fun isDeviceOwner(): Boolean = deviceOwnerProvider.isAvailable()

    /** Fully-qualified admin component, for the `adb dpm set-device-owner`/`remove-active-admin`
     * commands shown in the guided setup screen. */
    val deviceOwnerAdminComponent: String
        get() = ComponentName(context, DeviceOwnerAdminReceiver::class.java).flattenToString()

    val autoFreezeEnabled = settings.autoFreezeEnabled
    val autoFreezeCandidates = settings.candidates

    fun setAutoFreezeCandidate(packageName: String, enabled: Boolean) {
        settings.setCandidateEnabled(packageName, enabled)
    }

    /** Freeze/unfreeze counters and last-toggled timestamps, keyed by package name. */
    val stats = settings.stats

    companion object {
        private const val TAG = "FreezeManager"
        private val VALID_PACKAGE_NAME = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*")
    }
}
