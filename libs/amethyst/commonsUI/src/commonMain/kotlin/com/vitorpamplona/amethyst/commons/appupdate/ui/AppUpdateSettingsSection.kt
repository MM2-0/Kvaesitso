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
package com.vitorpamplona.amethyst.commons.appupdate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.appupdate.AppUpdateController
import com.vitorpamplona.amethyst.commons.appupdate.AppUpdateState
import com.vitorpamplona.amethyst.commons.appupdate.UpdateMethod
import com.vitorpamplona.amethyst.commons.appupdate.canInstall
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.app_update_auto_check
import com.vitorpamplona.amethyst.commons.resources.app_update_auto_check_description
import com.vitorpamplona.amethyst.commons.resources.app_update_available_title
import com.vitorpamplona.amethyst.commons.resources.app_update_check_now
import com.vitorpamplona.amethyst.commons.resources.app_update_checking
import com.vitorpamplona.amethyst.commons.resources.app_update_copy_command
import com.vitorpamplona.amethyst.commons.resources.app_update_current_version
import com.vitorpamplona.amethyst.commons.resources.app_update_downloading
import com.vitorpamplona.amethyst.commons.resources.app_update_install
import com.vitorpamplona.amethyst.commons.resources.app_update_last_checked
import com.vitorpamplona.amethyst.commons.resources.app_update_never_checked
import com.vitorpamplona.amethyst.commons.resources.app_update_open_page
import com.vitorpamplona.amethyst.commons.resources.app_update_ready
import com.vitorpamplona.amethyst.commons.resources.app_update_restart
import com.vitorpamplona.amethyst.commons.resources.app_update_retry
import com.vitorpamplona.amethyst.commons.resources.app_update_section
import com.vitorpamplona.amethyst.commons.resources.app_update_skipped
import com.vitorpamplona.amethyst.commons.resources.app_update_up_to_date
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.note.timeAbsoluteNoDot
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsDivider
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsSection
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsSwitchTile
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlinx.coroutines.launch

/** Settings › App › Updates: the version, the auto-check switch, and what the updater is doing. */
@Composable
fun AppUpdateSettingsSection(controller: AppUpdateController) {
    val autoCheck by controller.autoCheck.collectAsStateWithLifecycle()

    SettingsSection(Res.string.app_update_section) {
        UpdateStatusRow(controller)
        SettingsDivider()
        SettingsSwitchTile(
            icon = MaterialSymbols.Sync,
            title = Res.string.app_update_auto_check,
            description = Res.string.app_update_auto_check_description,
            checked = autoCheck,
            onCheckedChange = controller::setAutoCheck,
        )
    }
}

@Composable
private fun UpdateStatusRow(controller: AppUpdateController) {
    val state by controller.state.collectAsStateWithLifecycle()
    val skipped by controller.skippedVersion.collectAsStateWithLifecycle()
    val lastCheckAt by controller.lastCheckAt.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringRes(Res.string.app_update_current_version, controller.currentVersion),
            style = MaterialTheme.typography.titleMedium,
        )

        val status: String? =
            when (val s = state) {
                AppUpdateState.Idle ->
                    if (lastCheckAt > 0) {
                        stringRes(Res.string.app_update_last_checked, timeAbsoluteNoDot(lastCheckAt))
                    } else {
                        stringRes(Res.string.app_update_never_checked)
                    }
                AppUpdateState.Checking -> stringRes(Res.string.app_update_checking)
                is AppUpdateState.UpToDate -> stringRes(Res.string.app_update_up_to_date)
                is AppUpdateState.Available ->
                    if (s.update.version == skipped) {
                        stringRes(Res.string.app_update_skipped, s.update.version)
                    } else {
                        stringRes(Res.string.app_update_available_title, s.update.version)
                    }
                is AppUpdateState.Downloading -> stringRes(Res.string.app_update_downloading, s.update.version)
                is AppUpdateState.ReadyToRestart -> stringRes(Res.string.app_update_ready, s.update.version)
                is AppUpdateState.Failed -> stringRes(failureText(s.failure))
            }
        status?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        val pending =
            when (val s = state) {
                is AppUpdateState.Available -> s.update
                is AppUpdateState.Failed -> s.update
                else -> null
            }
        if (pending != null || controller.method !is UpdateMethod.SelfInstall && controller.method !is UpdateMethod.SystemInstaller) {
            Text(
                text = updateMethodText(controller.method, pending?.canInstall ?: true),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val method = controller.method
        if (method is UpdateMethod.PackageManager) {
            CommandRow(method.command)
        }

        (state as? AppUpdateState.Downloading)?.let { DownloadProgress(it) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (val s = state) {
                is AppUpdateState.Available ->
                    if (s.update.canInstall && method.canInstall) {
                        OutlinedButton(onClick = controller::install) { Text(stringRes(Res.string.app_update_install)) }
                    } else {
                        OutlinedButton(onClick = { uriHandler.openUri(s.update.pageUrl) }) { Text(stringRes(Res.string.app_update_open_page)) }
                    }
                is AppUpdateState.ReadyToRestart ->
                    OutlinedButton(onClick = controller::restartToUpdate) { Text(stringRes(Res.string.app_update_restart)) }
                is AppUpdateState.Failed ->
                    if (s.update != null) {
                        OutlinedButton(onClick = controller::install) { Text(stringRes(Res.string.app_update_retry)) }
                    } else {
                        OutlinedButton(onClick = controller::checkNow) { Text(stringRes(Res.string.app_update_check_now)) }
                    }
                AppUpdateState.Checking, is AppUpdateState.Downloading -> Unit
                else -> OutlinedButton(onClick = controller::checkNow) { Text(stringRes(Res.string.app_update_check_now)) }
            }
        }
    }
}

@Composable
private fun CommandRow(command: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectionContainer(modifier = Modifier.weight(1f)) {
            Text(text = command, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
        }
        OutlinedButton(onClick = { scope.launch { clipboard.setText(command) } }) {
            Text(stringRes(Res.string.app_update_copy_command))
        }
    }
}
