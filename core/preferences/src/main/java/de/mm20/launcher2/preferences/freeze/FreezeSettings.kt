package de.mm20.launcher2.preferences.freeze

import de.mm20.launcher2.preferences.FreezeAppStats
import de.mm20.launcher2.preferences.FreezeBackendPreference
import de.mm20.launcher2.preferences.FreezeExclusionStrictness
import de.mm20.launcher2.preferences.FreezeMethod
import de.mm20.launcher2.preferences.FreezeProfile
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.FrozenAppStyle
import kotlinx.coroutines.flow.map

class FreezeSettings internal constructor(
    private val dataStore: LauncherDataStore,
) {
    // === TELOS_PENDING_REVIEW_START: ui_frozen_apps_style ===
    val frozenAppStyle
        get() = dataStore.data.map { it.freeze.frozenAppStyle }
    
    fun setFrozenAppStyle(style: FrozenAppStyle) {
        dataStore.update { it.copy(freeze = it.freeze.copy(frozenAppStyle = style)) }
    }
    // === TELOS_PENDING_REVIEW_END: ui_frozen_apps_style ===

    val backend
        get() = dataStore.data.map { it.freeze.freezeBackend }

    fun setBackend(backend: FreezeBackendPreference) {
        dataStore.update { it.copy(freeze = it.freeze.copy(freezeBackend = backend)) }
    }

    val freezeOnRecentsRemoval get() = dataStore.data.map { it.freeze.freezeOnRecentsRemoval }
    val recentsThawed get() = dataStore.data.map { it.freeze.freezeRecentsThawed }
    fun setFreezeOnRecentsRemoval(enabled: Boolean) {
        dataStore.update { it.copy(freeze = it.freeze.copy(freezeOnRecentsRemoval = enabled,
            freezeRecentsThawed = if (enabled) it.freeze.freezeRecentsThawed else emptySet())) }
    }
    suspend fun trackRecentsThaw(pkg: String) {
        dataStore.updateAndWait { current ->
            if (!current.freeze.freezeOnRecentsRemoval) current else current.copy(freeze = current.freeze.copy(
                freezeRecentsThawed = (current.freeze.freezeRecentsThawed + pkg).takeLastBounded()))
        }
    }
    suspend fun forgetRecentsThaw(packages: Set<String>) {
        dataStore.updateAndWait { it.copy(freeze = it.freeze.copy(freezeRecentsThawed = it.freeze.freezeRecentsThawed - packages)) }
    }

    val autoFreezeEnabled
        get() = dataStore.data.map { it.freeze.freezeAutoFreezeEnabled }

    fun setAutoFreezeEnabled(enabled: Boolean) {
        dataStore.update { it.copy(freeze = it.freeze.copy(freezeAutoFreezeEnabled = enabled)) }
    }

    val freezeOnScreenOff
        get() = dataStore.data.map { it.freeze.freezeOnScreenOff }

    fun setFreezeOnScreenOff(enabled: Boolean) {
        dataStore.update { it.copy(freeze = it.freeze.copy(freezeOnScreenOff = enabled)) }
    }

    val freezeOnIdle
        get() = dataStore.data.map { it.freeze.freezeOnIdle }

    fun setFreezeOnIdle(enabled: Boolean) {
        dataStore.update { it.copy(freeze = it.freeze.copy(freezeOnIdle = enabled)) }
    }

    val idleTimeoutMinutes
        get() = dataStore.data.map { it.freeze.freezeIdleTimeoutMinutes }

    fun setIdleTimeoutMinutes(minutes: Int) {
        dataStore.update { it.copy(freeze = it.freeze.copy(freezeIdleTimeoutMinutes = minutes)) }
    }

    val freezeOnBatterySaver
        get() = dataStore.data.map { it.freeze.freezeOnBatterySaver }

    fun setFreezeOnBatterySaver(enabled: Boolean) {
        dataStore.update { it.copy(freeze = it.freeze.copy(freezeOnBatterySaver = enabled)) }
    }

    /** Enrol only after confirmation, in the same transaction that enables the master switch. */
    suspend fun enableAutoFreezeWithImports(packages: Set<String>) {
        dataStore.updateAndWait { current ->
            current.copy(freeze = current.freeze.withAutoFreezeImports(packages))
        }
    }

    val candidates
        get() = dataStore.data.map { it.freeze.freezeCandidates }

    fun setCandidateEnabled(packageName: String, enabled: Boolean) {
        dataStore.update {
            if (enabled) {
                it.copy(freeze = it.freeze.copy(freezeCandidates = it.freeze.freezeCandidates + packageName))
            } else {
                it.copy(freeze = it.freeze.copy(freezeCandidates = it.freeze.freezeCandidates - packageName))
            }
        }
    }

    val profile
        get() = dataStore.data.map { it.freeze.freezeProfile }

    fun setProfile(profile: FreezeProfile) {
        dataStore.update { it.copy(freeze = it.freeze.copy(freezeProfile = profile)) }
    }

    val exclusionStrictness
        get() = dataStore.data.map { it.freeze.freezeExclusionStrictness }

    fun setExclusionStrictness(strictness: FreezeExclusionStrictness) {
        dataStore.update { it.copy(freeze = it.freeze.copy(freezeExclusionStrictness = strictness)) }
    }

    val neverFreezeApps
        get() = dataStore.data.map { it.freeze.freezeNeverFreezeApps }

    fun setNeverFreeze(packageName: String, never: Boolean) {
        dataStore.update {
            if (never) {
                it.copy(freeze = it.freeze.copy(freezeNeverFreezeApps = it.freeze.freezeNeverFreezeApps + packageName))
            } else {
                it.copy(freeze = it.freeze.copy(freezeNeverFreezeApps = it.freeze.freezeNeverFreezeApps - packageName))
            }
        }
    }

    val excludeForeground get() = dataStore.data.map { it.freeze.freezeExcludeForeground }

    fun setExcludeForeground(value: Boolean) {
        dataStore.update { it.copy(freeze = it.freeze.copy(freezeExcludeForeground = value)) }
    }

    val excludeMusic
        get() = dataStore.data.map { it.freeze.freezeExcludeMusic }

    fun setExcludeMusic(exclude: Boolean) {
        dataStore.update { it.copy(freeze = it.freeze.copy(freezeExcludeMusic = exclude)) }
    }

    val excludeNetwork
        get() = dataStore.data.map { it.freeze.freezeExcludeNetwork }

    fun setExcludeNetwork(exclude: Boolean) {
        dataStore.update { it.copy(freeze = it.freeze.copy(freezeExcludeNetwork = exclude)) }
    }

    val networkThresholdKb
        get() = dataStore.data.map { it.freeze.freezeNetworkThresholdKb }

    fun setNetworkThresholdKb(threshold: Int) {
        dataStore.update { it.copy(freeze = it.freeze.copy(freezeNetworkThresholdKb = threshold)) }
    }

    /** Save one app policy atomically, with no independently launched writes racing each other. */
    suspend fun setAppPolicy(packageName: String, candidate: Boolean, neverFreeze: Boolean, method: FreezeMethod?) {
        dataStore.updateAndWait { current ->
            val freeze = current.freeze
            current.copy(freeze = freeze.copy(
                freezeCandidates = if (candidate) freeze.freezeCandidates + packageName else freeze.freezeCandidates - packageName,
                freezeNeverFreezeApps = if (neverFreeze) freeze.freezeNeverFreezeApps + packageName else freeze.freezeNeverFreezeApps - packageName,
                freezeMethods = if (!candidate) freeze.freezeMethods else
                    freeze.freezeMethods + (packageName to (method ?: FreezeMethod.Suspend)),
            ))
        }
    }

    val maintenance get() = dataStore.data.map { it.freeze.freezeMaintenance }

    suspend fun saveMaintenance(key: String, entry: de.mm20.launcher2.preferences.FrozenAppRestore?) {
        dataStore.updateAndWait { current ->
            val entries = current.freeze.freezeMaintenance
            require(entry == null || entries.size < 10_000 || key in entries)
            val updated = if (entry == null) entries - key else entries + (key to entry)
            val bytes = updated.values.sumOf { row ->
                192L + 2L * (row.packageName.length + row.backend.length + row.operationId.length) +
                    row.components.keys.sumOf { 32L + it.length * 2L }
            }
            require(entry == null || bytes <= 4L * 1024 * 1024)
            current.copy(freeze = current.freeze.copy(freezeMaintenance = updated))
        }
    }

    val freezeMethods
        get() = dataStore.data.map { it.freeze.freezeMethods }

    fun setFreezeMethod(packageName: String, method: FreezeMethod?) {
        dataStore.update {
            if (method == null) {
                it.copy(freeze = it.freeze.copy(freezeMethods = it.freeze.freezeMethods - packageName))
            } else {
                it.copy(freeze = it.freeze.copy(freezeMethods = it.freeze.freezeMethods + (packageName to method)))
            }
        }
    }

    val advancedFeaturesEnabled
        get() = dataStore.data.map { it.freeze.freezeAdvancedFeaturesEnabled }

    fun setAdvancedFeaturesEnabled(enabled: Boolean) {
        dataStore.update { it.copy(freeze = it.freeze.copy(freezeAdvancedFeaturesEnabled = enabled)) }
    }

    val hideFromLauncher
        get() = dataStore.data.map { it.freeze.freezeHideFromLauncher }

    fun setHideFromLauncher(hide: Boolean) {
        dataStore.update { it.copy(freeze = it.freeze.copy(freezeHideFromLauncher = hide)) }
    }

    val stats
        get() = dataStore.data.map { it.freeze.freezeStats }

    fun recordFrozen(packageName: String, timestamp: Long) {
        dataStore.update {
            val current = it.freeze.freezeStats[packageName] ?: FreezeAppStats()
            it.copy(
                freeze = it.freeze.copy(
                    freezeStats = it.freeze.freezeStats + (packageName to current.copy(
                        freezeCount = current.freezeCount + 1,
                        lastFrozenAt = timestamp,
                    ))
                )
            )
        }
    }

    fun recordUnfrozen(packageName: String, timestamp: Long) {
        dataStore.update {
            val current = it.freeze.freezeStats[packageName] ?: FreezeAppStats()
            it.copy(
                freeze = it.freeze.copy(
                    freezeStats = it.freeze.freezeStats + (packageName to current.copy(
                        unfreezeCount = current.unfreezeCount + 1,
                        lastUnfrozenAt = timestamp,
                    ))
                )
            )
        }
    }

    val playUpdatePauseUntil
        get() = dataStore.data.map { it.freeze.playUpdatePauseUntil }

    val playUpdateThawed
        get() = dataStore.data.map { it.freeze.playUpdateThawed }

    fun startPlayUpdatePause(
        packages: Set<String>,
        untilMillis: Long,
        baselines: Map<String, Long>,
        netBlocked: Map<String, Boolean>,
        playStoreHeld: Boolean = false,
    ) {
        dataStore.update {
            it.copy(
                freeze = it.freeze.copy(
                    playUpdatePauseUntil = untilMillis,
                    playUpdateThawed = it.freeze.playUpdateThawed + packages,
                    playUpdateBaselines = it.freeze.playUpdateBaselines + baselines,
                    playUpdateNetBlocked = it.freeze.playUpdateNetBlocked + netBlocked,
                    playUpdatePlayStoreHeld = it.freeze.playUpdatePlayStoreHeld || playStoreHeld,
                )
            )
        }
    }

    val playUpdateBaselines
        get() = dataStore.data.map { it.freeze.playUpdateBaselines }

    val playUpdateNetBlocked
        get() = dataStore.data.map { it.freeze.playUpdateNetBlocked }

    fun removePlayUpdateThawed(packages: Collection<String>) {
        dataStore.update {
            val left = it.freeze.playUpdateThawed - packages.toSet()
            val bases = it.freeze.playUpdateBaselines - packages.toSet()
            it.copy(
                freeze = it.freeze.copy(
                    playUpdateThawed = left,
                    playUpdateBaselines = bases,
                    playUpdatePauseUntil = if (left.isEmpty()) 0L else it.freeze.playUpdatePauseUntil,
                )
            )
        }
    }

    fun clearPlayUpdateNetBlocked(packages: Collection<String>) {
        dataStore.update {
            it.copy(freeze = it.freeze.copy(playUpdateNetBlocked = it.freeze.playUpdateNetBlocked - packages.toSet()))
        }
    }

    fun clearPlayUpdatePause() {
        dataStore.update {
            it.copy(
                freeze = it.freeze.copy(
                    playUpdatePauseUntil = 0L,
                    playUpdateThawed = emptySet(),
                    playUpdateBaselines = emptyMap(),
                    playUpdatePlayStoreHeld = false,
                )
            )
        }
    }

    val playUpdatePlayStoreHeld
        get() = dataStore.data.map { it.freeze.playUpdatePlayStoreHeld }

    fun setPlayUpdatePlayStoreHeld(held: Boolean) {
        dataStore.update { it.copy(freeze = it.freeze.copy(playUpdatePlayStoreHeld = held)) }
    }

    val bloatAutoUpdate get() = dataStore.data.map { it.freeze.freezeBloatAutoUpdate }
    fun setBloatAutoUpdate(value: Boolean) {
        dataStore.update { it.copy(freeze = it.freeze.copy(freezeBloatAutoUpdate = value)) }
    }
    val systemRiskApproved get() = dataStore.data.map { it.freeze.freezeSystemRiskApproved }
    fun setSystemRiskApproved(pkg: String, approved: Boolean) {
        dataStore.update { it.copy(freeze = it.freeze.copy(freezeSystemRiskApproved =
            if (approved) it.freeze.freezeSystemRiskApproved + pkg else it.freeze.freezeSystemRiskApproved - pkg)) }
    }
    val screenOffDelays get() = dataStore.data.map { it.freeze.freezeScreenOffDelays }
    val screenOffPending get() = dataStore.data.map { it.freeze.freezeScreenOffPending }
    val screenOffBoot get() = dataStore.data.map { it.freeze.freezeScreenOffBoot }
    suspend fun setScreenOffPending(pending: Map<String, Long>, boot: Int) {
        dataStore.updateAndWait { it.copy(freeze = it.freeze.copy(
            freezeScreenOffPending = pending, freezeScreenOffBoot = boot)) }
    }
    fun setScreenOffDelay(packageName: String, minutes: Int?) {
        require(minutes == null || minutes in 0..1440)
        dataStore.update { it.copy(freeze = it.freeze.copy(freezeScreenOffDelays =
            if (minutes == null) it.freeze.freezeScreenOffDelays - packageName
            else it.freeze.freezeScreenOffDelays + (packageName to minutes))) }
    }
    val companionEnabled get() = dataStore.data.map { it.freeze.freezeCompanionEnabled }
    fun setCompanionRule(master: String, enabled: Boolean, companions: Set<String>) {
        dataStore.update { it.copy(freeze = it.freeze.copy(
            freezeCompanionEnabled = if (enabled) it.freeze.freezeCompanionEnabled + master
                else it.freeze.freezeCompanionEnabled - master,
            freezeCompanionExtras = it.freeze.freezeCompanionExtras +
                (master to companions.filter { pkg -> pkg != master }.sorted().joinToString(",")))) }
    }
    fun setCandidates(packages: Set<String>, enabled: Boolean) {
        dataStore.update { it.copy(freeze = it.freeze.copy(freezeCandidates =
            if (enabled) it.freeze.freezeCandidates + packages else it.freeze.freezeCandidates - packages)) }
    }

    val companionExtras
        get() = dataStore.data.map { it.freeze.freezeCompanionExtras }

    fun setCompanionExtras(master: String, packagesCsv: String) {
        dataStore.update {
            val map = if (packagesCsv.isBlank()) it.freeze.freezeCompanionExtras - master
            else it.freeze.freezeCompanionExtras + (master to packagesCsv)
            it.copy(freeze = it.freeze.copy(freezeCompanionExtras = map))
        }
    }

    val companionHolds
        get() = dataStore.data.map { it.freeze.freezeCompanionHolds }

    suspend fun addCompanionHolds(master: String, companions: Collection<String>) {
        if (companions.isEmpty()) return
        dataStore.updateAndWait {
            val holds = addCompanionOwners(it.freeze.freezeCompanionHolds, master, companions)
            it.copy(freeze = it.freeze.copy(freezeCompanionHolds = holds))
        }
    }

    suspend fun clearCompanionHoldsForMaster(master: String): Set<String> {
        var removed = emptySet<String>()
        dataStore.updateAndWait {
            val (remaining, released) = removeCompanionOwner(it.freeze.freezeCompanionHolds, master)
            removed = released
            it.copy(freeze = it.freeze.copy(freezeCompanionHolds = remaining))
        }
        return removed
    }

    val gameBoosterPackage
        get() = dataStore.data.map { it.freeze.freezeGameBoosterPackage }

    fun setGameBoosterPackage(packageName: String) {
        dataStore.update { it.copy(freeze = it.freeze.copy(freezeGameBoosterPackage = packageName.trim())) }
    }

    val gameBoosterApps
        get() = dataStore.data.map { it.freeze.freezeGameBoosterApps }

    fun setGameBoosterApp(packageName: String, enabled: Boolean) {
        dataStore.update {
            val set = if (enabled) it.freeze.freezeGameBoosterApps + packageName
            else it.freeze.freezeGameBoosterApps - packageName
            it.copy(freeze = it.freeze.copy(freezeGameBoosterApps = set))
        }
    }

    fun setGameBoosterApps(packages: Set<String>) {
        dataStore.update { it.copy(freeze = it.freeze.copy(freezeGameBoosterApps = packages)) }
    }
}

private fun Set<String>.takeLastBounded(): Set<String> = if (size <= 1000) this else toList().takeLast(1000).toSet()

internal fun de.mm20.launcher2.preferences.FreezeGroup.withAutoFreezeImports(packages: Set<String>) = copy(
    freezeAutoFreezeEnabled = true,
    freezeCandidates = freezeCandidates + (packages - freezeNeverFreezeApps),
)
