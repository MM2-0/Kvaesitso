package de.mm20.launcher2.ui.settings.freeze
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.content.Intent
import de.mm20.launcher2.ui.settings.SettingsActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.freeze.FreezeBackendType
import de.mm20.launcher2.preferences.FreezeBackendPreference
import de.mm20.launcher2.preferences.FreezeExclusionStrictness
import de.mm20.launcher2.preferences.FreezeMethod
import de.mm20.launcher2.preferences.FreezeProfile
import de.mm20.launcher2.preferences.FrozenAppStyle
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SliderPreference
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.component.preferences.TextPreference
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.serialization.Serializable
import androidx.appcompat.app.AppCompatActivity

@Serializable
data object FreezeSettingsRoute : NavKey

@Composable
fun FreezeSettingsScreen() {
    val rootVm: FreezeSettingsScreenVM = viewModel()
    LaunchedEffect(Unit) { rootVm.enterFreezeManagement() }
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    val tabs: @Composable () -> Unit = { FreezeManagementTabs(tab) { tab = it } }
    val holder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    holder.SaveableStateProvider(tab) {
        when (tab) {
            0 -> SmartFreezeDashboardScreen(tabs)
            else -> FreezeSettingsContent(tab, tabs)
        }
    }
}

@Composable
private fun FreezeSettingsContent(section: Int, tabs: @Composable () -> Unit) {
    val context = LocalContext.current
    val viewModel: FreezeSettingsScreenVM = viewModel()

    // Re-check on every resume: the user may have just started Shizuku/Dhizuku, granted root or
    // usage access in another app or system screen and come back.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshBackendState()
        viewModel.refreshFrozenInventory()
        onPauseOrDispose { }
    }

    val activeBackend by viewModel.activeBackend.collectAsStateWithLifecycle()
    val backend by viewModel.backend.collectAsStateWithLifecycle()
    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle()

    val autoFreezeEnabled by viewModel.autoFreezeEnabled.collectAsStateWithLifecycle()
    val importFrozen by viewModel.autoFreezeImports.collectAsStateWithLifecycle()
    val enrollmentBusy by viewModel.autoFreezeEnrollmentBusy.collectAsStateWithLifecycle()
    importFrozen?.let { pending ->
        AutoFreezeImportDialog(pending.size,
            onImport = { viewModel.confirmAutoFreezeEnrollment(true) },
            onSkip = { viewModel.confirmAutoFreezeEnrollment(false) },
            onCancel = viewModel::cancelAutoFreezeEnrollment)
    }

    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val exclusionStrictness by viewModel.exclusionStrictness.collectAsStateWithLifecycle()
    val freezeOnScreenOff by viewModel.freezeOnScreenOff.collectAsStateWithLifecycle()
    val freezeOnIdle by viewModel.freezeOnIdle.collectAsStateWithLifecycle()
    val idleTimeoutMinutes by viewModel.idleTimeoutMinutes.collectAsStateWithLifecycle()
    val freezeOnBatterySaver by viewModel.freezeOnBatterySaver.collectAsStateWithLifecycle()

    val excludeForeground by viewModel.excludeForeground.collectAsStateWithLifecycle()
    val excludeMusic by viewModel.excludeMusic.collectAsStateWithLifecycle()
    val excludeNetwork by viewModel.excludeNetwork.collectAsStateWithLifecycle()
    val networkThresholdKb by viewModel.networkThresholdKb.collectAsStateWithLifecycle()

    val freezeMethods by viewModel.freezeMethods.collectAsStateWithLifecycle()
    val advancedFeaturesEnabled by viewModel.advancedFeaturesEnabled.collectAsStateWithLifecycle()
    val hideFromLauncher by viewModel.hideFromLauncher.collectAsStateWithLifecycle()
    val gameBoosterPackage by viewModel.gameBoosterPackage.collectAsStateWithLifecycle()
    val gameBoosterApps by viewModel.gameBoosterApps.collectAsStateWithLifecycle()
    // === TELOS_PENDING_REVIEW_START: ui_frozen_apps_style ===
    val frozenAppStyle by viewModel.frozenAppStyle.collectAsStateWithLifecycle(FrozenAppStyle.SnowflakeBadge)
    // === TELOS_PENDING_REVIEW_END: ui_frozen_apps_style ===

    val usageAccessGranted by viewModel.usageAccessGranted.collectAsStateWithLifecycle()

    val apps by viewModel.allApps.collectAsStateWithLifecycle()
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()
    val neverFreezeApps by viewModel.neverFreezeApps.collectAsStateWithLifecycle()
    val showSystemApps by viewModel.showSystemApps.collectAsStateWithLifecycle()
    val showIconlessApps by viewModel.showIconlessApps.collectAsStateWithLifecycle()

    val frozenInventory by viewModel.frozenInventory.collectAsStateWithLifecycle()
    val maintenanceEntries by viewModel.maintenanceEntries.collectAsStateWithLifecycle()
    val maintenanceBusy by viewModel.maintenanceBusy.collectAsStateWithLifecycle()
    var includeUsers by remember { mutableStateOf(true) }
    var includeSystem by remember { mutableStateOf(false) }
    var includeIconless by remember { mutableStateOf(false) }
    val backStack = LocalBackStack.current

    PreferenceScreen(
        title = stringResource(I18nR.string.preference_screen_freeze),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item { tabs() }
        item {
            androidx.compose.material3.Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Column(modifier = androidx.compose.ui.Modifier.padding(12.dp)) {
                    Text(
                        text = "⚠️ Προειδοποίηση / Warning",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "Αυτό το εργαλείο τροποποιεί την κατάσταση εφαρμογών συστήματος (Freeze/Disable). Η εσφαλμένη χρήση μπορεί να προκαλέσει αστάθεια ή bootloops. Απευθύνεται σε προχωρημένους χρήστες.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
                    )
                }
            }
        }
        if (section == 2) item {
            val autoUpdate by viewModel.bloatAutoUpdate.collectAsStateWithLifecycle()
            val updateStatus by viewModel.bloatUpdateStatus.collectAsStateWithLifecycle()
            SwitchPreference(title = stringResource(I18nR.string.freeze_hub_auto_update),
                summary = stringResource(I18nR.string.freeze_hub_auto_update_help), value = autoUpdate,
                onValueChanged = viewModel::setBloatAutoUpdate)
            if (updateStatus == de.mm20.launcher2.freeze.BloatUpdateStatus.Failed)
                Text(stringResource(I18nR.string.freeze_hub_update_failed))
            if (updateStatus == de.mm20.launcher2.freeze.BloatUpdateStatus.Updating)
                androidx.compose.material3.LinearProgressIndicator()
            PreferenceCategory(title = stringResource(I18nR.string.freeze_maintenance_title)) {
                SwitchPreference(title = stringResource(I18nR.string.netfw_filter_user), value = includeUsers,
                    onValueChanged = { includeUsers = it }, enabled = !maintenanceBusy)
                SwitchPreference(title = stringResource(I18nR.string.netfw_filter_system), value = includeSystem,
                    onValueChanged = { includeSystem = it }, enabled = !maintenanceBusy)
                SwitchPreference(title = stringResource(I18nR.string.freeze_system_iconless), value = includeIconless,
                    onValueChanged = { includeIconless = it }, enabled = !maintenanceBusy)
                val groups = buildSet {
                    if (includeUsers) add(de.mm20.launcher2.freeze.FrozenAppGroup.User)
                    if (includeSystem) add(de.mm20.launcher2.freeze.FrozenAppGroup.System)
                    if (includeIconless) add(de.mm20.launcher2.freeze.FrozenAppGroup.SystemWithoutLauncher)
                }
                val selectedCount = frozenInventory.count { it.group in groups }
                Preference(title = stringResource(I18nR.string.freeze_unfreeze_all),
                    summary = stringResource(I18nR.string.hf_freeze_frozen_apps) + ": " + selectedCount,
                    enabled = !maintenanceBusy && selectedCount > 0 && hasPermission == true && activeBackend != FreezeBackendType.Island,
                    onClick = { viewModel.unfreezeAll(groups) },
                    controls = { if (maintenanceBusy) androidx.compose.material3.CircularProgressIndicator(
                        modifier = androidx.compose.ui.Modifier) })
                Preference(title = stringResource(I18nR.string.freeze_restore_all),
                    summary = stringResource(I18nR.string.hc_restore) + ": " + maintenanceEntries.size,
                    enabled = !maintenanceBusy && maintenanceEntries.isNotEmpty(),
                    onClick = { viewModel.restoreFrozenApps() })
            }
        }
        if (section == 2) item {
            Preference(
                icon = BaseR.drawable.bar_chart_24px,
                title = stringResource(I18nR.string.preference_freeze_dashboard),
                summary = stringResource(I18nR.string.preference_freeze_dashboard_summary),
                onClick = { backStack.add(FreezeDashboardRoute) }
            )
        }
        if (section == 2) item {
            PreferenceCategory(title = stringResource(I18nR.string.preference_freeze_backend_category)) {
                ListPreference(
                    title = stringResource(I18nR.string.preference_freeze_backend),
                    items = listOf(
                        stringResource(I18nR.string.preference_value_system_default) to FreezeBackendPreference.Auto,
                        stringResource(I18nR.string.freeze_backend_shizuku) to FreezeBackendPreference.ShizukuOnly,
                        stringResource(I18nR.string.telos_freeze_backend_dhizuku) to FreezeBackendPreference.DhizukuOnly,
                        stringResource(I18nR.string.freeze_backend_root) to FreezeBackendPreference.RootOnly,
                        stringResource(I18nR.string.freeze_backend_island) to FreezeBackendPreference.Island,
                        stringResource(I18nR.string.freeze_backend_device_owner) to FreezeBackendPreference.DeviceOwnerOnly,
                    ),
                    value = backend,
                    onValueChanged = { viewModel.setBackend(it) }
                )
                if (activeBackend == null) {
                    Banner(
                        text = stringResource(I18nR.string.freeze_no_backend_available),
                        icon = BaseR.drawable.error_24px,
                    )
                } else {
                    Preference(
                        icon = BaseR.drawable.ac_unit_24px,
                        title = stringResource(
                            when (activeBackend) {
                                FreezeBackendType.Shizuku -> I18nR.string.freeze_backend_shizuku
                                // === TELOS_PENDING_REVIEW_START: thor_freezer_features ===
                                FreezeBackendType.Dhizuku -> I18nR.string.telos_freeze_backend_dhizuku
                                // === TELOS_PENDING_REVIEW_END: thor_freezer_features ===
                                FreezeBackendType.Root -> I18nR.string.freeze_backend_root
                                FreezeBackendType.Island -> I18nR.string.freeze_backend_island
                                FreezeBackendType.DeviceOwner -> I18nR.string.freeze_backend_device_owner
                                null -> I18nR.string.freeze_backend_none
                            }
                        ),
                        summary = stringResource(
                            if (hasPermission == true) I18nR.string.freeze_permission_granted
                            else I18nR.string.freeze_permission_not_granted
                        ),
                        onClick = {
                            (context as? AppCompatActivity)?.let { viewModel.requestPermission(it) }
                        }
                    )
                }
                Preference(
                    icon = BaseR.drawable.download_24px,
                    title = stringResource(I18nR.string.freeze_download_external_apps),
                    summary = stringResource(I18nR.string.freeze_download_external_apps_summary),
                    onClick = {
                        context.startActivity(
                            Intent(context, SettingsActivity::class.java)
                                .putExtra(SettingsActivity.EXTRA_ROUTE, SettingsActivity.ROUTE_STORE)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                )
                Preference(
                    icon = BaseR.drawable.lock_24px,
                    title = stringResource(I18nR.string.freeze_device_owner_setup_title),
                    onClick = { backStack.add(DeviceOwnerSetupRoute) },
                )
            }
        }
        if (section == 1) item {
            val recentsEnabled by viewModel.freezeOnRecentsRemoval.collectAsStateWithLifecycle()
            val recentsConnected by viewModel.recentsConnected.collectAsStateWithLifecycle()
            SwitchPreference(title = stringResource(I18nR.string.freeze_hub_recents),
                summary = stringResource(I18nR.string.freeze_hub_recents_help), value = recentsEnabled,
                onValueChanged = viewModel::setFreezeOnRecentsRemoval)
            if (recentsEnabled && !recentsConnected) Text(stringResource(I18nR.string.freeze_hub_recents_unavailable))
            PreferenceCategory(title = stringResource(I18nR.string.preference_freeze_auto_category)) {
                SwitchPreference(
                    title = stringResource(I18nR.string.preference_freeze_auto_enabled),
                    summary = stringResource(I18nR.string.preference_freeze_auto_enabled_summary),
                    value = autoFreezeEnabled == true,
                    enabled = !enrollmentBusy && importFrozen == null,
                    onValueChanged = { viewModel.setAutoFreezeEnabled(it) }
                )
                if (enrollmentBusy) LinearProgressIndicator(androidx.compose.ui.Modifier.fillMaxWidth())
                AnimatedVisibility(autoFreezeEnabled == true) {
                    Column {
                        ListPreference(
                            title = stringResource(I18nR.string.preference_freeze_profile),
                            items = listOf(
                                stringResource(I18nR.string.freeze_profile_battery_saver) to FreezeProfile.BatterySaver,
                                stringResource(I18nR.string.freeze_profile_balanced) to FreezeProfile.Balanced,
                                stringResource(I18nR.string.freeze_profile_aggressive) to FreezeProfile.Aggressive,
                                stringResource(I18nR.string.freeze_profile_ultra_aggressive) to FreezeProfile.UltraAggressive,
                                stringResource(I18nR.string.freeze_profile_custom) to FreezeProfile.Custom,
                            ),
                            value = profile ?: FreezeProfile.Balanced,
                            summary = when (profile) {
                                FreezeProfile.BatterySaver -> stringResource(I18nR.string.freeze_profile_battery_saver_summary)
                                FreezeProfile.Balanced -> stringResource(I18nR.string.freeze_profile_balanced_summary)
                                FreezeProfile.Aggressive -> stringResource(I18nR.string.freeze_profile_aggressive_summary)
                                FreezeProfile.UltraAggressive -> stringResource(I18nR.string.freeze_profile_ultra_aggressive_summary)
                                else -> null
                            },
                            onValueChanged = { viewModel.setProfile(it) }
                        )
                        AnimatedVisibility(profile == FreezeProfile.Custom) {
                            Column {
                                SwitchPreference(
                                    title = stringResource(I18nR.string.preference_freeze_on_screen_off),
                                    value = freezeOnScreenOff == true,
                                    onValueChanged = { viewModel.setFreezeOnScreenOff(it) }
                                )
                                SwitchPreference(
                                    title = stringResource(I18nR.string.preference_freeze_on_idle),
                                    value = freezeOnIdle == true,
                                    onValueChanged = { viewModel.setFreezeOnIdle(it) }
                                )
                                AnimatedVisibility(freezeOnIdle == true) {
                                    SliderPreference(
                                        title = stringResource(I18nR.string.preference_freeze_idle_timeout),
                                        value = idleTimeoutMinutes ?: 15,
                                        min = 1,
                                        max = 120,
                                        step = 1,
                                        onValueChanged = { viewModel.setIdleTimeoutMinutes(it) },
                                        label = { Text(stringResource(I18nR.string.au3_commsfreeze_minutes, it)) }
                                    )
                                }
                                SwitchPreference(
                                    title = stringResource(I18nR.string.preference_freeze_on_battery_saver),
                                    value = freezeOnBatterySaver == true,
                                    onValueChanged = { viewModel.setFreezeOnBatterySaver(it) }
                                )
                                ListPreference(
                                    title = stringResource(I18nR.string.preference_freeze_exclusion_strictness),
                                    items = listOf(
                                        stringResource(I18nR.string.freeze_exclusion_strictness_strict) to FreezeExclusionStrictness.Strict,
                                        stringResource(I18nR.string.freeze_exclusion_strictness_relaxed) to FreezeExclusionStrictness.Relaxed,
                                    ),
                                    value = exclusionStrictness ?: FreezeExclusionStrictness.Strict,
                                    onValueChanged = { viewModel.setExclusionStrictness(it) }
                                )
                            }
                        }
                        SwitchPreference(
                            title = stringResource(I18nR.string.freeze_exclude_foreground),
                            summary = stringResource(I18nR.string.freeze_exclude_foreground_summary),
                            value = excludeForeground,
                            onValueChanged = {
                                viewModel.setExcludeForeground(it)
                                if (it && !usageAccessGranted) (context as? AppCompatActivity)?.let(viewModel::requestUsageAccess)
                            },
                        )
                        if (excludeForeground) Preference(
                            title = stringResource(I18nR.string.preference_freeze_usage_access),
                            summary = stringResource(if (usageAccessGranted) I18nR.string.preference_freeze_usage_access_summary_granted else I18nR.string.preference_freeze_usage_access_summary_not_granted),
                            onClick = { (context as? AppCompatActivity)?.let(viewModel::requestUsageAccess) },
                        )
                        ListPreference(
                            title = stringResource(I18nR.string.preference_freeze_exclusion_strictness),
                            summary = stringResource(I18nR.string.preference_freeze_exclusion_strictness_summary),
                            items = listOf(
                                de.mm20.launcher2.ui.component.preferences.ListPreferenceItem(stringResource(I18nR.string.preference_freeze_on_idle), 0),
                                de.mm20.launcher2.ui.component.preferences.ListPreferenceItem(stringResource(I18nR.string.preference_freeze_on_battery_saver), 1),
                                de.mm20.launcher2.ui.component.preferences.ListPreferenceItem(stringResource(I18nR.string.preference_freeze_on_screen_off), 2)
                            ),
                            value = 1,
                            onValueChanged = { } // Just visual UI integration placeholder to show the key
                        )
                        SwitchPreference(
                            title = stringResource(I18nR.string.preference_freeze_exclude_music),
                            summary = stringResource(I18nR.string.preference_freeze_exclude_music_summary),
                            value = excludeMusic == true,
                            onValueChanged = { viewModel.setExcludeMusic(it) }
                        )
                        SwitchPreference(
                            title = stringResource(I18nR.string.preference_freeze_exclude_network),
                            summary = stringResource(I18nR.string.preference_freeze_exclude_network_summary),
                            value = excludeNetwork == true,
                            onValueChanged = { viewModel.setExcludeNetwork(it) }
                        )
                        AnimatedVisibility(excludeNetwork == true) {
                            SliderPreference(
                                title = stringResource(I18nR.string.preference_freeze_network_threshold),
                                value = networkThresholdKb ?: 100,
                                min = 10,
                                max = 1000,
                                step = 10,
                                onValueChanged = { viewModel.setNetworkThresholdKb(it) },
                                label = { Text(stringResource(I18nR.string.preference_freeze_network_threshold_summary) + " (" + stringResource(I18nR.string.au_freeze_kbps, it) + ")") }
                            )
                        }

                    }
                }
            }
        }
        if (section == 2) item {
            PreferenceCategory(title = stringResource(I18nR.string.preference_freeze_apps_category)) {
                Text(
                    text = stringResource(I18nR.string.preference_freeze_apps_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.preference_freeze_show_system_apps),
                    summary = stringResource(I18nR.string.preference_freeze_show_system_apps_summary),
                    value = showSystemApps,
                    onValueChanged = { viewModel.setShowSystemApps(it) }
                )
                AnimatedVisibility(showSystemApps) {
                    Banner(
                        text = stringResource(I18nR.string.freeze_system_apps_warning),
                        icon = BaseR.drawable.error_24px,
                    )
                }
                SwitchPreference(
                    title = stringResource(I18nR.string.preference_freeze_show_iconless_apps),
                    summary = stringResource(I18nR.string.preference_freeze_show_iconless_apps_summary),
                    value = showIconlessApps,
                    onValueChanged = { viewModel.setShowIconlessApps(it) }
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.preference_freeze_advanced_features),
                    summary = stringResource(I18nR.string.preference_freeze_advanced_features_summary),
                    value = advancedFeaturesEnabled,
                    onValueChanged = { viewModel.setAdvancedFeaturesEnabled(it) }
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.preference_freeze_hide_from_launcher),
                    summary = stringResource(I18nR.string.preference_freeze_hide_from_launcher_summary),
                    value = hideFromLauncher,
                    onValueChanged = { viewModel.setHideFromLauncher(it) }
                )
                // === TELOS_PENDING_REVIEW_START: ui_frozen_apps_style ===
                ListPreference(
                    title = stringResource(I18nR.string.preference_frozen_app_style),
                    items = listOf(
                        stringResource(I18nR.string.preference_frozen_app_style_grayscale) to FrozenAppStyle.Grayscale,
                        stringResource(I18nR.string.preference_frozen_app_style_snowflake) to FrozenAppStyle.SnowflakeBadge
                    ),
                    value = frozenAppStyle,
                    onValueChanged = { if (it != null) viewModel.setFrozenAppStyle(it) }
                )
                // === TELOS_PENDING_REVIEW_END: ui_frozen_apps_style ===
            }
        }
    }
}

@Composable
internal fun AutoFreezeImportDialog(count: Int, onImport: () -> Unit, onSkip: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(I18nR.string.preference_freeze_auto_category)) },
        text = { Text(stringResource(I18nR.string.freeze_auto_import_existing, count)) },
        confirmButton = { TextButton(onClick = onImport) { Text(stringResource(android.R.string.yes)) } },
        dismissButton = { TextButton(onClick = onSkip) { Text(stringResource(android.R.string.no)) } },
        containerColor = de.mm20.launcher2.ui.component.virtualAppPopup(),
    )
}
