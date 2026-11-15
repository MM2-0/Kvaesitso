package de.mm20.launcher2.ui.system

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.data.store.security.AppInureInspector
import de.mm20.launcher2.data.store.security.InureInspectionResult
import de.mm20.launcher2.freeze.FreezeManager
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.common.share.ShareActions
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.network.firewall.NetworkPackageFirewallRoute
import de.mm20.launcher2.ui.settings.freeze.FreezePackagePolicyDialog
import de.mm20.launcher2.ui.settings.freeze.FreezeSettingsRoute
import de.mm20.launcher2.ui.settings.freeze.FreezeSettingsScreenVM
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
data class SystemAppDetailsRoute(val packageName: String) : NavKey

private data class ComponentRow(val name: ComponentName, val state: Int, val manifestEnabled: Boolean)
private data class PackageDetails(val label: String, val version: String, val sdk: Int, val installed: String,
    val updated: String, val frozen: Boolean, val launch: Intent?, val inspection: InureInspectionResult?,
    val components: List<ComponentRow>, val audioModes: Map<String, String?>, val rootAvailable: Boolean)

/** Every installed package uses this surface, without creating a Store tracking entry. */
@Composable
fun SystemAppDetailsScreen(packageName: String) {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val scope = rememberCoroutineScope()
    val freeze: FreezeManager = koinInject()
    val policy: FreezeSettingsScreenVM = viewModel()
    val descriptions: de.mm20.launcher2.freeze.BloatDescriptions = koinInject()
    val uad by descriptions.data.collectAsStateWithLifecycle()
    var revision by remember { mutableIntStateOf(0) }
    var details by remember(packageName) { mutableStateOf<PackageDetails?>(null) }
    var loading by remember(packageName) { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<String?>(null) }
    var showPolicy by remember { mutableStateOf(false) }
    var showComponents by rememberSaveable(packageName) { mutableStateOf(false) }
    var componentQuery by rememberSaveable(packageName) { mutableStateOf("") }
    var pending by remember { mutableStateOf<Pair<ComponentName, Int>?>(null) }
    LaunchedEffect(packageName) { policy.enterFreezeManagement() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { revision++ }
    de.mm20.launcher2.ui.component.PrivilegedConnectionEffect { revision++ }
    LaunchedEffect(packageName, revision) {
        loading = true
        try {
            freeze.refreshBackendState()
            details = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val info = pm.getPackageInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS or
                    PackageManager.GET_ACTIVITIES or PackageManager.GET_RECEIVERS or PackageManager.GET_SERVICES or PackageManager.GET_PROVIDERS)
                val app = requireNotNull(info.applicationInfo)
                val components = (info.activities.orEmpty().map { it.name to it.enabled } +
                    info.receivers.orEmpty().map { it.name to it.enabled } + info.services.orEmpty().map { it.name to it.enabled } +
                    info.providers.orEmpty().map { it.name to it.enabled }).distinctBy { it.first }.sortedBy { it.first }.map { (name, enabled) ->
                    val component = ComponentName(packageName, name)
                    ComponentRow(component, pm.getComponentEnabledSetting(component), enabled)
                }
                val date = java.text.DateFormat.getDateTimeInstance()
                PackageDetails(app.loadLabel(pm).toString(), "${info.versionName.orEmpty()} (${androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info)})",
                    app.targetSdkVersion, date.format(java.util.Date(info.firstInstallTime)), date.format(java.util.Date(info.lastUpdateTime)),
                    freeze.isFrozen(packageName), pm.getLaunchIntentForPackage(packageName), AppInureInspector.inspectPackage(context, packageName),
                    components, freeze.audioModes(packageName), freeze.canEditComponents())
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { details = null; outcome = context.getString(I18nR.string.hc_failed_try_again) }
        finally { loading = false }
    }
    fun action(block: suspend () -> Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val ok = withContext(Dispatchers.IO) { block() }
                outcome = context.getString(I18nR.string.freeze_hub_result, if (ok) 1 else 0, 1)
                revision++
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { outcome = context.getString(I18nR.string.hc_failed_try_again) }
            finally { busy = false }
        }
    }
    val shownComponents = remember(details, componentQuery) { details?.components.orEmpty().filter { it.name.className.contains(componentQuery, true) } }
    PreferenceScreen(title = details?.label ?: packageName) {
        item {
            Text(packageName, style = MaterialTheme.typography.bodySmall)
            if (loading || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            outcome?.let { Text(it) }
        }
        details?.let { data ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(I18nR.string.file_meta_app_version, data.version))
                    Text("SDK: ${data.sdk}")
                    Text("${stringResource(I18nR.string.hf_store_installed)}: ${data.installed}")
                    Text("${stringResource(I18nR.string.hf_files_prop_modified)}: ${data.updated}")
                    Text(stringResource(I18nR.string.freeze_hub_uad_help), style = MaterialTheme.typography.bodySmall)
                    Text("UAD-NG · ${uad[packageName]?.recommendation ?: stringResource(I18nR.string.freeze_hub_unknown)}")
                    Text(uad[packageName]?.description ?: stringResource(I18nR.string.freeze_hub_unknown))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !busy, onClick = { action { packageName in if (data.frozen) freeze.unfreeze(packageName) else freeze.freeze(packageName) } }) {
                            Text(stringResource(if (data.frozen) I18nR.string.freeze_hub_unfreeze else I18nR.string.freeze_widget_freeze_now))
                        }
                        OutlinedButton(onClick = { showPolicy = true }) { Text(stringResource(I18nR.string.preference_freeze_auto_category)) }
                        OutlinedButton(onClick = { backStack.add(FreezeSettingsRoute) }) { Text(stringResource(I18nR.string.preference_screen_freeze)) }
                        OutlinedButton(onClick = { backStack.add(NetworkPackageFirewallRoute(packageName)) }) { Text(stringResource(I18nR.string.netfw_title)) }
                        AppOpsNextButton(packageName)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = data.launch != null && !data.frozen, onClick = {
                            runCatching { context.startActivity(requireNotNull(data.launch)) }.onFailure { outcome = context.getString(I18nR.string.hc_failed_try_again) }
                        }) { Text(stringResource(I18nR.string.gesture_action_launch_app)) }
                        OutlinedButton(onClick = {
                            runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
                                .onFailure { outcome = context.getString(I18nR.string.hc_failed_try_again) }
                        }) { Text(stringResource(I18nR.string.au_video_open_settings)) }
                        OutlinedButton(enabled = !busy, onClick = { action { ShareActions.shareApk(context, packageName) } }) { Text("APK · ${stringResource(I18nR.string.hc_share)}") }
                        OutlinedButton(onClick = { ShareActions.shareText(context, "https://play.google.com/store/apps/details?id=$packageName") }) { Text(stringResource(I18nR.string.hc_share_link)) }
                    }
                }
            }
            data.inspection?.let { inspector ->
                item {
                    Text("Inure", style = MaterialTheme.typography.titleMedium)
                    Text("SHA-256 · SHA-1", style = MaterialTheme.typography.titleSmall)
                    inspector.certIssuer?.let { Text(it) }
                    Text("SHA-256: ${inspector.sha256Fingerprint}", style = MaterialTheme.typography.bodySmall)
                    Text("SHA-1: ${inspector.sha1Fingerprint}", style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(I18nR.string.dl_t_tab_trackers), style = MaterialTheme.typography.titleSmall)
                    // Manifest-component matches only; an empty set is not an antivirus verdict.
                    Text(inspector.detectedTrackers.joinToString("\n").ifBlank { stringResource(I18nR.string.freeze_hub_unknown) })
                    Text(stringResource(I18nR.string.hf_appinfo_permissions), style = MaterialTheme.typography.titleSmall)
                }
                items(inspector.dangerousPermissions + inspector.normalPermissions, key = { "permission:$it" }) { permission ->
                    Text(if (permission in inspector.dangerousPermissions) "⚠ $permission" else permission, style = MaterialTheme.typography.bodySmall,
                        color = if (permission in inspector.dangerousPermissions) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                }
            }
            item {
                Text(stringResource(I18nR.string.system_audio_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(I18nR.string.system_audio_help), style = MaterialTheme.typography.bodySmall)
                for (op in listOf("TAKE_AUDIO_FOCUS", "PLAY_AUDIO")) {
                    val mode = data.audioModes[op]
                    de.mm20.launcher2.ui.component.preferences.ListPreference(
                        title = stringResource(if (op == "TAKE_AUDIO_FOCUS") I18nR.string.system_audio_title else I18nR.string.system_audio_playback), value = mode,
                        enabled = !busy && mode != null,
                        items = listOf(stringResource(I18nR.string.icon_picker_default_icon) to "default",
                            stringResource(I18nR.string.preference_category_enabled_plugins) to "allow",
                            stringResource(I18nR.string.preference_value_disabled) to "ignore") +
                            if (mode != null && mode !in listOf("default", "allow", "ignore")) listOf(mode to mode) else emptyList(),
                        onValueChanged = { value -> if (value != null && value != mode) action { freeze.setAudioMode(packageName, op, value) } })
                    if (mode == null) Text(stringResource(I18nR.string.hf_freeze_disconnected))
                }
            }
            item {
                TextButton(onClick = { showComponents = !showComponents }) { Text(stringResource(I18nR.string.store_component_blocker)) }
                if (showComponents) {
                    Text(stringResource(I18nR.string.store_component_blocker_root_required))
                    de.mm20.launcher2.ui.component.TelosSearchBar(componentQuery, { componentQuery = it }, stringResource(I18nR.string.hc_search))
                }
            }
            if (showComponents) items(shownComponents, key = { it.name.flattenToString() }) { component ->
                val enabled = when (component.state) {
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> component.manifestEnabled
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                    else -> false
                }
                Column {
                    de.mm20.launcher2.ui.component.preferences.SwitchPreference(
                        title = component.name.className, value = enabled,
                        enabled = !busy && data.rootAvailable,
                        onValueChanged = { value -> pending = component.name to if (value)
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED })
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(enabled = !busy && data.rootAvailable && component.state != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                            onClick = { pending = component.name to PackageManager.COMPONENT_ENABLED_STATE_DEFAULT }) { Text(stringResource(I18nR.string.icon_picker_default_icon)) }
                    }
                }
            }
        }
    }
    if (showPolicy) FreezePackagePolicyDialog(packageName, details?.label ?: packageName, Process.myUserHandle(), policy) { showPolicy = false }
    pending?.let { change ->
        AlertDialog(onDismissRequest = { pending = null }, title = { TelosCyberHeading {  Text(stringResource(I18nR.string.store_component_blocker))  } },
            text = { Text(change.first.flattenToString()) },
            confirmButton = { TextButton(onClick = { pending = null; action { freeze.setComponentState(change.first, change.second) } }) { Text(stringResource(I18nR.string.hc_apply)) } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(android.R.string.cancel)) } })
    }
}
