package de.mm20.launcher2.ui.settings.freeze
import de.mm20.launcher2.i18n.R as I18nR

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.freeze.FreezeBackendType
import de.mm20.launcher2.freeze.FreezeManager
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.FreezeBackendPreference
import de.mm20.launcher2.preferences.FreezeExclusionStrictness
import de.mm20.launcher2.preferences.FreezeMethod
import de.mm20.launcher2.preferences.FreezeProfile
import de.mm20.launcher2.preferences.freeze.FreezeSettings
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.SavableSearchable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import de.mm20.launcher2.preferences.FrozenAppStyle

class FreezeSettingsScreenVM : ViewModel(), KoinComponent {
    private val context: Context by inject()
    private val freezeManager: FreezeManager by inject()
    private val freezeSettings: FreezeSettings by inject()
    private val appRepository: AppRepository by inject()
    private val iconService: IconService by inject()
    private val permissionsManager: PermissionsManager by inject()

    private val maintenanceLock = kotlinx.coroutines.sync.Mutex()
    private val _maintenanceBusy = MutableStateFlow(false)
    val maintenanceBusy = _maintenanceBusy.asStateFlow()
    private val _frozenInventory = MutableStateFlow<List<de.mm20.launcher2.freeze.FrozenAppInventory>>(emptyList())
    val frozenInventory = _frozenInventory.asStateFlow()
    val maintenanceEntries = freezeManager.maintenanceEntries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun refreshFrozenInventory() = maintenanceAction { 0 }
    fun unfreezeAll(groups: Set<de.mm20.launcher2.freeze.FrozenAppGroup>) =
        maintenanceAction { freezeManager.unfreezeAll(groups) }
    fun restoreFrozenApps() = maintenanceAction {
        freezeManager.restoreFrozenApps() + freezeManager.recoverFrozenUpdates()
    }
    private fun maintenanceAction(action: suspend () -> Int) {
        viewModelScope.launch {
            withContext(kotlinx.coroutines.NonCancellable) {
                maintenanceLock.lock()
                try {
                    _maintenanceBusy.value = true
                    if (action() != 0) showPolicyError()
                    _frozenInventory.value = freezeManager.scanFrozenApps()
                    // Refresh iconless entries each time Settings is opened, including other freezers.
                    _iconlessApps.value = appRepository.findIconlessApps().sorted()
                } catch (error: Exception) {
                    android.util.Log.e("FreezeSettings", "Maintenance operation failed", error)
                    showPolicyError()
                } finally {
                    _maintenanceBusy.value = false
                    maintenanceLock.unlock()
                }
            }
        }
    }

    val usageAccessGranted = permissionsManager.hasPermission(PermissionGroup.UsageAccess)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun requestUsageAccess(activity: AppCompatActivity) {
        permissionsManager.requestPermission(activity, PermissionGroup.UsageAccess)
    }

    val activeBackend: StateFlow<FreezeBackendType?> = freezeManager.activeBackend

    val backend = freezeSettings.backend
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FreezeBackendPreference.Auto)

    // === TELOS_PENDING_REVIEW_START: ui_frozen_apps_style ===
    val frozenAppStyle = freezeSettings.frozenAppStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setFrozenAppStyle(style: FrozenAppStyle) {
        freezeSettings.setFrozenAppStyle(style)
    }
    // === TELOS_PENDING_REVIEW_END: ui_frozen_apps_style ===

    fun setBackend(backend: FreezeBackendPreference) {
        freezeSettings.setBackend(backend)
        refreshBackendState()
    }

    private val _hasPermission = MutableStateFlow<Boolean?>(null)
    val hasPermission = _hasPermission.asStateFlow()

    private val sortedApps = appRepository.findMany().map {
        withContext(Dispatchers.Default) { it.sorted() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _showSystemApps = MutableStateFlow(false)
    val showSystemApps = _showSystemApps.asStateFlow()

    fun setShowSystemApps(show: Boolean) {
        _showSystemApps.value = show
    }

    private val _showIconlessApps = MutableStateFlow(false)
    val showIconlessApps = _showIconlessApps.asStateFlow()

    private val _iconlessApps = MutableStateFlow<List<Application>>(emptyList())

    fun setShowIconlessApps(show: Boolean) {
        _showIconlessApps.value = show
        if (show) {
            viewModelScope.launch {
                _iconlessApps.value = appRepository.findIconlessApps().sorted()
            }
        }
    }

    val allApps = combine(
        sortedApps, _showSystemApps, _showIconlessApps, _iconlessApps,
    ) { apps, showSystem, showIconless, iconlessApps ->
        withContext(Dispatchers.Default) {
            // Filter out Telos Virtual Apps since freezing launcher built-in apps makes no sense
            val realApps = apps.filterNot {
                it.key.startsWith("telos_") ||
                it.componentName.packageName.startsWith("de.mm20.launcher2.tools") ||
                it.componentName.packageName == context.packageName
            }
            val filteredApps = if (showSystem) realApps else realApps.filterNot { isSystemApp(it.componentName.packageName) }
            if (showIconless) {
                val result = ArrayList<Application>(filteredApps.size + iconlessApps.size)
                result.addAll(filteredApps)
                result.addAll(iconlessApps.filterNot {
                    it.key.startsWith("telos_") || it.componentName.packageName == context.packageName
                })
                result.sort()
                result
            } else {
                filteredApps
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Live OS-level frozen state, regardless of source - reflects apps frozen by Icebox,
     * Island, adb, or anything else, not just this app's own auto-freeze candidates. */
    fun isFrozen(packageName: String): Boolean = freezeManager.isFrozen(packageName)

    private fun isSystemApp(packageName: String): Boolean {
        return try {
            context.packageManager.getApplicationInfo(packageName, 0)
                .flags and ApplicationInfo.FLAG_SYSTEM != 0
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    val candidates = freezeSettings.candidates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val neverFreezeApps = freezeSettings.neverFreezeApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val profile = freezeSettings.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setProfile(profile: FreezeProfile) = freezeSettings.setProfile(profile)

    val exclusionStrictness = freezeSettings.exclusionStrictness
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setExclusionStrictness(strictness: FreezeExclusionStrictness) =
        freezeSettings.setExclusionStrictness(strictness)

    val autoFreezeEnabled = freezeSettings.autoFreezeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val freezeOnScreenOff = freezeSettings.freezeOnScreenOff
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val freezeOnIdle = freezeSettings.freezeOnIdle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val idleTimeoutMinutes = freezeSettings.idleTimeoutMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val freezeOnBatterySaver = freezeSettings.freezeOnBatterySaver
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val excludeForeground = freezeSettings.excludeForeground
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    fun setExcludeForeground(value: Boolean) = freezeSettings.setExcludeForeground(value)

    val excludeMusic = freezeSettings.excludeMusic
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val excludeNetwork = freezeSettings.excludeNetwork
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val networkThresholdKb = freezeSettings.networkThresholdKb
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val freezeMethods = freezeSettings.freezeMethods
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val advancedFeaturesEnabled = freezeSettings.advancedFeaturesEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hideFromLauncher = freezeSettings.hideFromLauncher
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val gameBoosterPackage = freezeSettings.gameBoosterPackage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    fun setGameBoosterPackage(value: String) = freezeSettings.setGameBoosterPackage(value)
    val gameBoosterApps = freezeSettings.gameBoosterApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    fun setGameBoosterAppsCsv(csv: String) {
        freezeSettings.setGameBoosterApps(csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet())
    }

    fun refreshBackendState() {
        viewModelScope.launch {
            freezeManager.refreshBackendState()
            _hasPermission.value = freezeManager.hasPermission()
        }
    }

    fun requestPermission(activity: AppCompatActivity) {
        viewModelScope.launch {
            _hasPermission.value = freezeManager.requestPermission()
        }
    }

    private val _autoFreezeImports = MutableStateFlow<Set<String>?>(null)
    val autoFreezeImports = _autoFreezeImports.asStateFlow()
    private val _autoFreezeEnrollmentBusy = MutableStateFlow(false)
    val autoFreezeEnrollmentBusy = _autoFreezeEnrollmentBusy.asStateFlow()
    private var enrollmentJob: kotlinx.coroutines.Job? = null

    fun setAutoFreezeEnabled(enabled: Boolean) {
        if (!enabled) {
            enrollmentJob?.cancel()
            _autoFreezeImports.value = null
            freezeSettings.setAutoFreezeEnabled(false)
            return
        }
        if (_autoFreezeEnrollmentBusy.value || _autoFreezeImports.value != null) return
        enrollmentJob = viewModelScope.launch {
            _autoFreezeEnrollmentBusy.value = true
            try {
                val inventory = freezeManager.scanFrozenApps()
                val imports = de.mm20.launcher2.freeze.autoFreezeImportCandidates(
                    inventory, android.os.Process.myUid() / 100000,
                    freezeSettings.candidates.first(), freezeSettings.neverFreezeApps.first(), context.packageName,
                )
                if (imports.isEmpty()) freezeSettings.enableAutoFreezeWithImports(emptySet())
                else _autoFreezeImports.value = imports
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { showPolicyError() }
            finally { _autoFreezeEnrollmentBusy.value = false }
        }
    }

    fun cancelAutoFreezeEnrollment() { _autoFreezeImports.value = null }

    fun confirmAutoFreezeEnrollment(importFrozen: Boolean) {
        val pending = _autoFreezeImports.value ?: return
        _autoFreezeImports.value = null
        _autoFreezeEnrollmentBusy.value = true
        enrollmentJob = viewModelScope.launch {
            try { freezeSettings.enableAutoFreezeWithImports(if (importFrozen) pending else emptySet()) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { showPolicyError() }
            finally { _autoFreezeEnrollmentBusy.value = false }
        }
    }
    fun setFreezeOnScreenOff(enabled: Boolean) = freezeSettings.setFreezeOnScreenOff(enabled)
    fun setFreezeOnIdle(enabled: Boolean) = freezeSettings.setFreezeOnIdle(enabled)
    fun setIdleTimeoutMinutes(minutes: Int) = freezeSettings.setIdleTimeoutMinutes(minutes)
    fun setFreezeOnBatterySaver(enabled: Boolean) = freezeSettings.setFreezeOnBatterySaver(enabled)
    fun setExcludeMusic(enabled: Boolean) = freezeSettings.setExcludeMusic(enabled)
    fun setExcludeNetwork(enabled: Boolean) = freezeSettings.setExcludeNetwork(enabled)
    fun setNetworkThresholdKb(threshold: Int) = freezeSettings.setNetworkThresholdKb(threshold)
    fun setAdvancedFeaturesEnabled(enabled: Boolean) = freezeSettings.setAdvancedFeaturesEnabled(enabled)
    fun setHideFromLauncher(hide: Boolean) = freezeSettings.setHideFromLauncher(hide)

    fun setAppFreezeState(app: Application, state: AppFreezeState, method: FreezeMethod? = null) {
        setPackageFreezeState(app.componentName.packageName, app.user, state, method)
    }

    fun setPackageFreezeState(packageName: String, user: android.os.UserHandle, state: AppFreezeState, method: FreezeMethod? = null) {
        viewModelScope.launch {
            try {
                val changed = freezeManager.updateAppPolicy(packageName, user,
                    state == AppFreezeState.Candidate, state == AppFreezeState.NeverFreeze, method)
                if (!changed) showPolicyError()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.e("FreezeSettings", "Could not apply app freeze policy", error)
                showPolicyError()
            }
        }
    }

    val companionChoices = kotlinx.coroutines.flow.flow {
        emit(withContext(Dispatchers.IO) {
            context.packageManager.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_UNINSTALLED_PACKAGES)
                .filter { it.packageName != context.packageName && it.flags and ApplicationInfo.FLAG_INSTALLED != 0 }
                .map { it.packageName to it.loadLabel(context.packageManager).toString() }
                .sortedBy { it.second.lowercase() }
        })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val freezeOnRecentsRemoval = freezeSettings.freezeOnRecentsRemoval
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    private val recentsMonitor: de.mm20.launcher2.freeze.RecentsFreezeMonitor by inject()
    val recentsConnected = recentsMonitor.connected
    fun setFreezeOnRecentsRemoval(value: Boolean) = freezeSettings.setFreezeOnRecentsRemoval(value)
    val screenOffDelays = freezeSettings.screenOffDelays.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    val companionEnabled = freezeSettings.companionEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    val companionExtras = freezeSettings.companionExtras.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    val riskApproved = freezeSettings.systemRiskApproved.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    private val descriptions: de.mm20.launcher2.freeze.BloatDescriptions by inject()
    val bloatDescriptions = descriptions.data
    val bloatUpdateStatus = descriptions.status
    val bloatAutoUpdate = freezeSettings.bloatAutoUpdate.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    fun setBloatAutoUpdate(value: Boolean) = freezeSettings.setBloatAutoUpdate(value)
    fun enterFreezeManagement() {
        viewModelScope.launch {
            descriptions.entries()
            if (freezeSettings.bloatAutoUpdate.first()) descriptions.refresh()
        }
    }
    fun setDelay(pkg: String, minutes: Int?) = freezeSettings.setScreenOffDelay(pkg, minutes)
    fun setCompanionRule(pkg: String, enabled: Boolean, packages: Set<String>) =
        freezeSettings.setCompanionRule(pkg, enabled, packages)
    fun setRiskApproved(pkg: String, value: Boolean) = freezeSettings.setSystemRiskApproved(pkg, value)
    private val refresh = MutableStateFlow(0)
    val frozenStates = combine(allApps, refresh) { apps, _ -> withContext(Dispatchers.IO) {
        apps.associate { app -> app.key to if (app.user == android.os.Process.myUserHandle()) {
            freezeManager.isFrozen(app.componentName.packageName)
        } else runCatching {
            val service = context.getSystemService(android.content.pm.LauncherApps::class.java)
            val info = service.getApplicationInfo(app.componentName.packageName, PackageManager.MATCH_DISABLED_COMPONENTS, app.user)
            !info.enabled || info.flags and ApplicationInfo.FLAG_SUSPENDED != 0
        }.getOrDefault(false) }
    } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    private val _bulkBusy = MutableStateFlow(false)
    val bulkBusy = _bulkBusy.asStateFlow()
    private val _bulkResult = MutableStateFlow<String?>(null)
    val bulkResult = _bulkResult.asStateFlow()
    fun bulkAction(apps: List<Application>, action: Int) {
        if (_bulkBusy.value) return
        _bulkBusy.value = true
        viewModelScope.launch {
            var done = 0
            try {
                freezeManager.refreshBackendState()
                for (app in apps) {
                    val pkg = app.componentName.packageName
                    val ok = runCatching {
                        when (action) {
                            0 -> pkg in freezeManager.freeze(pkg, app.user.hashCode())
                            1 -> { freezeManager.unfreezeCompanions(pkg, app.user.hashCode())
                                val ok = pkg in freezeManager.unfreeze(pkg, app.user.hashCode())
                                if (!ok) freezeManager.releaseCompanions(pkg, app.user.hashCode())
                                ok }
                            else -> app.user == android.os.Process.myUserHandle() &&
                                freezeManager.updateAppPolicy(pkg, app.user, true, false, appFreezeMethod(pkg, freezeMethods.value))
                        }
                    }.getOrElse { error ->
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        false
                    }
                    if (ok) done++
                }
                _bulkResult.value = context.getString(I18nR.string.freeze_hub_result, done, apps.size)
            } finally { _bulkBusy.value = false; refresh.value++ }
        }
    }
    fun refreshAppStates() { refresh.value++ }

    private fun showPolicyError() {
        android.widget.Toast.makeText(context, I18nR.string.pdft_error_title,
            android.widget.Toast.LENGTH_LONG).show()
    }

    fun getIcon(searchable: SavableSearchable, size: Int): Flow<LauncherIcon?> {
        return iconService.getIcon(searchable, size)
    }
}

enum class AppFreezeState { None, Candidate, NeverFreeze }

fun appFreezeState(packageName: String, candidates: Set<String>, neverFreeze: Set<String>): AppFreezeState {
    return when {
        neverFreeze.contains(packageName) -> AppFreezeState.NeverFreeze
        candidates.contains(packageName) -> AppFreezeState.Candidate
        else -> AppFreezeState.None
    }
}

fun appFreezeMethod(packageName: String, methods: Map<String, FreezeMethod>): FreezeMethod {
    return methods[packageName] ?: FreezeMethod.Suspend
}
