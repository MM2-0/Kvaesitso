package de.mm20.launcher2.ui.settings.advanced

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.comms.graphics.HwuiRenderer
import de.mm20.launcher2.preferences.comms.CommsSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
fun GraphicsApiPreferenceCategory() {
    val context = LocalContext.current
    val comms: CommsSettings = koinInject()
    val scope = rememberCoroutineScope()
    val hwuiRenderer by comms.hwuiRenderer.collectAsStateWithLifecycle("default")
    val hwuiRestoreOnBoot by comms.hwuiRestoreOnBoot.collectAsStateWithLifecycle(true)
    val restartNotice by comms.hwuiShowRestartNotice.collectAsStateWithLifecycle(true)
    var explainRestart by remember { mutableStateOf(false) }
    val hwuiWidgetSwitch by comms.hwuiWidgetSwitch.collectAsStateWithLifecycle(false)
    var liveRenderer by remember { mutableStateOf<String?>(null) }
    var privileged by remember { mutableStateOf(false) }
    var backendRevision by remember { mutableStateOf(0) }
    de.mm20.launcher2.ui.component.PrivilegedConnectionEffect { backendRevision++ }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { backendRevision++ }
    LaunchedEffect(backendRevision) {
        liveRenderer = withContext(Dispatchers.IO) { HwuiRenderer.current() }
        privileged = HwuiRenderer.privilegedAvailable()
    }
    if (explainRestart) {
        RestartNoticeDialog(onDismiss = { explainRestart = false })
    }
    PreferenceCategory(title = stringResource(I18nR.string.hc_graphics_api)) {
        Preference(
            title = stringResource(I18nR.string.hc_graphics_api_about),
            summary = stringResource(I18nR.string.hc_graphics_api_about_summary),
            onClick = {},
        )
        ListPreference(
            title = stringResource(I18nR.string.hc_graphics_renderer),
            summary = stringResource(I18nR.string.hc_graphics_renderer_summary),
            items = listOf(
                stringResource(I18nR.string.hc_graphics_default) to HwuiRenderer.MODE_DEFAULT,
                stringResource(I18nR.string.hc_graphics_vulkan) to HwuiRenderer.MODE_VULKAN,
                stringResource(I18nR.string.hc_graphics_opengl) to HwuiRenderer.MODE_OPENGL,
            ),
            value = hwuiRenderer,
            onValueChanged = { mode ->
                if (mode == null) return@ListPreference
                comms.setHwuiRenderer(mode)
                scope.launch {
                    val ok = HwuiRenderer.apply(mode)
                    liveRenderer = HwuiRenderer.current()
                    privileged = HwuiRenderer.privilegedAvailable()
                    if (ok && restartNotice) explainRestart = true
                    val msg = if (ok) {
                        context.getString(I18nR.string.hc_graphics_applied)
                    } else {
                        context.getString(I18nR.string.hc_graphics_need_privilege, HwuiRenderer.adbCommand(mode))
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            },
        )
        Preference(
            title = stringResource(I18nR.string.hc_graphics_current),
            summary = when (liveRenderer) {
                HwuiRenderer.MODE_VULKAN -> stringResource(I18nR.string.hc_graphics_vulkan)
                HwuiRenderer.MODE_OPENGL -> stringResource(I18nR.string.hc_graphics_opengl)
                HwuiRenderer.MODE_DEFAULT -> stringResource(I18nR.string.hc_graphics_default)
                null -> stringResource(I18nR.string.hc_graphics_unknown)
                else -> liveRenderer
            } + if (privileged) {
                " · " + stringResource(I18nR.string.hc_graphics_priv_ok)
            } else {
                " · " + stringResource(I18nR.string.hc_graphics_priv_missing)
            },
            onClick = {
                scope.launch {
                    liveRenderer = HwuiRenderer.current()
                    privileged = HwuiRenderer.privilegedAvailable()
                }
            },
        )
        SwitchPreference(
            title = stringResource(I18nR.string.hc_graphics_restore_boot),
            summary = stringResource(I18nR.string.hc_graphics_restore_boot_summary),
            value = hwuiRestoreOnBoot,
            onValueChanged = { comms.setHwuiRestoreOnBoot(it) },
        )
        SwitchPreference(
            title = stringResource(I18nR.string.app_polish_restart_notice),
            summary = stringResource(I18nR.string.app_polish_renderer_restart),
            value = restartNotice,
            onValueChanged = comms::setHwuiShowRestartNotice,
        )
        SwitchPreference(
            title = stringResource(I18nR.string.hc_graphics_widget_switch),
            summary = stringResource(I18nR.string.hc_graphics_widget_switch_summary),
            value = hwuiWidgetSwitch,
            onValueChanged = { comms.setHwuiWidgetSwitch(it) },
        )
        Preference(
            title = stringResource(I18nR.string.hc_graphics_copy_adb),
            summary = HwuiRenderer.adbCommand(hwuiRenderer),
            onClick = {
                val clip = android.content.ClipData.newPlainText("adb", HwuiRenderer.adbCommand(hwuiRenderer))
                context.getSystemService(android.content.ClipboardManager::class.java)?.setPrimaryClip(clip)
                Toast.makeText(context, I18nR.string.hc_graphics_adb_copied, Toast.LENGTH_SHORT).show()
            },
        )
    }
}


@Composable
private fun RestartNoticeDialog(onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            TelosCyberHeading {
                androidx.compose.material3.Text(text = stringResource(I18nR.string.hc_graphics_api))
            }
        },
        text = {
            androidx.compose.material3.Text(text = stringResource(I18nR.string.app_polish_renderer_restart))
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                androidx.compose.material3.Text(text = stringResource(android.R.string.ok))
            }
        },
    )
}
