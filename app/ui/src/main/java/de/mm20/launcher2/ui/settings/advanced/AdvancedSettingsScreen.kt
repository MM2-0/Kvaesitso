package de.mm20.launcher2.ui.settings.advanced

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.freeze.ShizukuSystemStatus
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.settings.applock.AppLockSettingsRoute
import de.mm20.launcher2.ui.settings.art.ArtOptimizerRoute
import de.mm20.launcher2.ui.settings.stealth.StealthCloakRoute
import de.mm20.launcher2.ui.settings.backup.BackupSettingsRoute
import de.mm20.launcher2.ui.settings.contextprofiles.ContextProfilesSettingsRoute
import de.mm20.launcher2.ui.settings.debug.DebugSettingsRoute
import de.mm20.launcher2.ui.settings.performance.PerformanceSettingsRoute
import de.mm20.launcher2.ui.settings.plugins.PluginsSettingsRoute
import de.mm20.launcher2.ui.settings.workprofile.WorkProfileSettingsRoute
import kotlinx.serialization.Serializable

@Serializable
data object AdvancedSettingsRoute : NavKey

@Composable
fun AdvancedSettingsScreen() {
    val backStack = LocalBackStack.current
    val vm: AdvancedSettingsScreenVM = viewModel()
    val context = LocalContext.current

    val shizukuAutoStart by vm.shizukuAutoStart.collectAsStateWithLifecycle(false)
    val systemStatusState by vm.systemStatus.collectAsStateWithLifecycle()
    val status = systemStatusState

    var showDiagnosticDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.refreshStatus(context)
    }

    PreferenceScreen(
        title = { Text(stringResource(I18nR.string.preference_screen_advanced)) },
    ) {
        item {
            PreferenceCategory(title = stringResource(I18nR.string.settings_system_automation)) {
                val currentStatus = status
                val statusText = when {
                    currentStatus?.hasWriteSecureSettings == true ->
                        stringResource(I18nR.string.telos_shizuku_diag_ready)
                    currentStatus?.isShizukuRunning == true ->
                        stringResource(I18nR.string.telos_shizuku_diag_grant)
                    else ->
                        stringResource(I18nR.string.telos_shizuku_diag_start)
                }
                SwitchPreference(
                    title = stringResource(I18nR.string.settings_shizuku_autostart),
                    summary = stringResource(I18nR.string.telos_shizuku_autostart_summary) + "\n" + statusText,
                    value = shizukuAutoStart,
                    onValueChanged = { enabled ->
                        vm.setShizukuAutoStart(
                            context = context,
                            enabled = enabled,
                            onVerified = {
                                Toast.makeText(
                                    context,
                                    context.getString(I18nR.string.telos_shizuku_autostart_ok),
                                    Toast.LENGTH_LONG,
                                ).show()
                            },
                            onFailed = {
                                showDiagnosticDialog = true
                            }
                        )
                    }
                )
                Preference(
                    title = stringResource(I18nR.string.telos_shizuku_title),
                    summary = stringResource(I18nR.string.security_hub_summary),
                    onClick = {
                        backStack.add(de.mm20.launcher2.ui.system.SystemDashboardRoute("shizuku"))
                    },
                )

                Preference(
                    icon = BaseR.drawable.settings_24px,
                    title = stringResource(I18nR.string.settings_shizuku_diagnostics),
                    summary = stringResource(I18nR.string.settings_shizuku_diagnostics_summary),
                    onClick = { showDiagnosticDialog = true }
                )
            }
        }

        item {
            GraphicsApiPreferenceCategory()
        }

        item {
            PreferenceCategory {
                Preference(
                    icon = BaseR.drawable.speed_24px,
                    title = stringResource(id = I18nR.string.preference_screen_performance),
                    summary = stringResource(id = I18nR.string.preference_screen_performance_summary),
                    onClick = {
                        backStack.add(PerformanceSettingsRoute)
                    }
                )
                Preference(
                    icon = BaseR.drawable.bolt_24px,
                    title = stringResource(id = I18nR.string.art_optimizer_title),
                    summary = stringResource(id = I18nR.string.art_optimizer_summary),
                    onClick = {
                        backStack.add(ArtOptimizerRoute)
                    }
                )
                Preference(
                    icon = BaseR.drawable.lock_24px,
                    title = stringResource(id = I18nR.string.stealth_cloak_title),
                    summary = stringResource(id = I18nR.string.stealth_cloak_summary),
                    onClick = {
                        backStack.add(StealthCloakRoute)
                    }
                )
                Preference(
                    icon = BaseR.drawable.encrypted_24px,
                    title = stringResource(id = I18nR.string.security_hub_title),
                    summary = stringResource(id = I18nR.string.security_hub_summary),
                    onClick = {
                        backStack.add(de.mm20.launcher2.ui.system.SystemDashboardRoute("shield"))
                    }
                )
                Preference(
                    icon = BaseR.drawable.dashboard_2_24px,
                    title = stringResource(id = I18nR.string.preference_screen_context_profiles),
                    summary = stringResource(id = I18nR.string.preference_screen_context_profiles_summary),
                    onClick = {
                        backStack.add(ContextProfilesSettingsRoute)
                    }
                )
                Preference(
                    icon = BaseR.drawable.extension_24px,
                    title = stringResource(id = I18nR.string.preference_screen_plugins),
                    summary = stringResource(id = I18nR.string.preference_screen_plugins_summary),
                    onClick = {
                        backStack.add(PluginsSettingsRoute)
                    }
                )

                Preference(
                    icon = BaseR.drawable.lock_24px,
                    title = stringResource(id = I18nR.string.preference_screen_app_lock),
                    summary = stringResource(id = I18nR.string.preference_screen_app_lock_summary),
                    onClick = {
                        backStack.add(AppLockSettingsRoute)
                    }
                )
                Preference(
                    icon = BaseR.drawable.enterprise_24px,
                    title = stringResource(id = I18nR.string.preference_screen_work_profile),
                    summary = stringResource(id = I18nR.string.preference_screen_work_profile_summary),
                    onClick = {
                        backStack.add(WorkProfileSettingsRoute)
                    }
                )
                Preference(
                    icon = BaseR.drawable.settings_backup_restore_24px,
                    title = stringResource(id = I18nR.string.preference_screen_backup),
                    summary = stringResource(id = I18nR.string.preference_screen_backup_summary),
                    onClick = {
                        backStack.add(BackupSettingsRoute)
                    }
                )
                Preference(
                    icon = BaseR.drawable.bug_report_24px,
                    title = stringResource(id = I18nR.string.preference_screen_debug),
                    summary = stringResource(id = I18nR.string.preference_screen_debug_summary),
                    onClick = {
                        backStack.add(DebugSettingsRoute)
                    }
                )
            }
        }
    }

    if (showDiagnosticDialog) {
        ShizukuDiagnosticDialog(
            status = status,
            vm = vm,
            onDismiss = {
                vm.clearError()
                showDiagnosticDialog = false
            }
        )
    }
}

@Composable
private fun ShizukuDiagnosticDialog(
    status: ShizukuSystemStatus?,
    vm: AdvancedSettingsScreenVM,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val adbCmd = "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.settings_shizuku_dialog_title))  } },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(stringResource(I18nR.string.settings_shizuku_checklist), fontWeight = FontWeight.Bold)

                ChecklistItem(stringResource(I18nR.string.settings_shizuku_check_android11), status?.isAndroid11OrHigher == true)
                ChecklistItem(stringResource(I18nR.string.telos_shizuku_diag_builtin), status?.isShizukuInstalled == true || status?.isShizukuRunning == true)
                ChecklistItem(stringResource(I18nR.string.settings_shizuku_check_dev_opts), status?.isDeveloperOptionsEnabled == true)
                ChecklistItem(stringResource(I18nR.string.settings_shizuku_check_wifi), status?.isWifiConnected == true)
                ChecklistItem(stringResource(I18nR.string.settings_shizuku_check_write_secure), status?.hasWriteSecureSettings == true)
                ChecklistItem(stringResource(I18nR.string.settings_shizuku_check_daemon), status?.isShizukuRunning == true && status.isShizukuAuthorized)

                HorizontalDivider(Modifier.padding(vertical = 4.dp))

                Text(stringResource(I18nR.string.settings_shizuku_step1_title), fontWeight = FontWeight.Bold)
                Text(stringResource(I18nR.string.settings_shizuku_step1_desc), style = MaterialTheme.typography.bodySmall)

                Button(
                    onClick = {
                        vm.trySelfGrant(context) { ok ->
                            Toast.makeText(
                                context,
                                if (ok) context.getString(I18nR.string.settings_shizuku_grant_ok)
                                else context.getString(I18nR.string.settings_shizuku_grant_fail),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(I18nR.string.settings_shizuku_grant_retry))
                }

                HorizontalDivider(Modifier.padding(vertical = 4.dp))

                Text(stringResource(I18nR.string.settings_shizuku_step2_title), fontWeight = FontWeight.Bold)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(10.dp)) {
                        Text(adbCmd, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("ADB Command", adbCmd))
                                Toast.makeText(context, context.getString(I18nR.string.settings_shizuku_adb_copied), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(stringResource(I18nR.string.settings_shizuku_copy_adb))
                        }
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 4.dp))

                Text(stringResource(I18nR.string.settings_shizuku_step3_title), fontWeight = FontWeight.Bold)
                Text(stringResource(I18nR.string.settings_shizuku_step3_boot), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(I18nR.string.settings_shizuku_step3_adb_timeout), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(I18nR.string.settings_shizuku_step3_battery), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(I18nR.string.hc_close))
            }
        }
    )
}

@Composable
private fun ChecklistItem(label: String, passed: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(if (passed) "✅" else "❌")
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
