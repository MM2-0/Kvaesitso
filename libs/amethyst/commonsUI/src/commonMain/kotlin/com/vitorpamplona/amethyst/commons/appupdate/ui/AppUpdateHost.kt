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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.appupdate.AppUpdateController
import com.vitorpamplona.amethyst.commons.appupdate.AppUpdateState
import com.vitorpamplona.amethyst.commons.appupdate.UpdateFailure
import com.vitorpamplona.amethyst.commons.appupdate.UpdateMethod
import com.vitorpamplona.amethyst.commons.appupdate.canInstall
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.app_update_available_body
import com.vitorpamplona.amethyst.commons.resources.app_update_available_title
import com.vitorpamplona.amethyst.commons.resources.app_update_downloading
import com.vitorpamplona.amethyst.commons.resources.app_update_failed_check
import com.vitorpamplona.amethyst.commons.resources.app_update_failed_download
import com.vitorpamplona.amethyst.commons.resources.app_update_failed_install
import com.vitorpamplona.amethyst.commons.resources.app_update_failed_not_writable
import com.vitorpamplona.amethyst.commons.resources.app_update_failed_verification
import com.vitorpamplona.amethyst.commons.resources.app_update_hide
import com.vitorpamplona.amethyst.commons.resources.app_update_install
import com.vitorpamplona.amethyst.commons.resources.app_update_later
import com.vitorpamplona.amethyst.commons.resources.app_update_method_download
import com.vitorpamplona.amethyst.commons.resources.app_update_method_move
import com.vitorpamplona.amethyst.commons.resources.app_update_method_package_manager
import com.vitorpamplona.amethyst.commons.resources.app_update_method_self
import com.vitorpamplona.amethyst.commons.resources.app_update_method_system
import com.vitorpamplona.amethyst.commons.resources.app_update_open_page
import com.vitorpamplona.amethyst.commons.resources.app_update_ready
import com.vitorpamplona.amethyst.commons.resources.app_update_ready_title
import com.vitorpamplona.amethyst.commons.resources.app_update_restart
import com.vitorpamplona.amethyst.commons.resources.app_update_retry
import com.vitorpamplona.amethyst.commons.resources.app_update_skip
import com.vitorpamplona.amethyst.commons.resources.app_update_unsigned
import com.vitorpamplona.amethyst.commons.resources.app_update_whats_new
import com.vitorpamplona.amethyst.commons.ui.components.PromptDialog
import com.vitorpamplona.amethyst.commons.ui.stringRes
import org.jetbrains.compose.resources.StringResource

/** The platform's updater, where the app updates itself (the desktop); null otherwise. */
val LocalAppUpdates = compositionLocalOf<AppUpdateController?> { null }

/**
 * Provides [controller] to the app and tells the user about a new version: once per version and
 * stage (available, downloaded, failed), unless they skipped that version. Settings › App ›
 * Updates shows the same state at any time. With no [controller] it only renders [content].
 */
@Composable
fun AppUpdateHost(
    controller: AppUpdateController?,
    content: @Composable () -> Unit,
) {
    // Always the same tree, so the app's UI is not rebuilt when the controller arrives after startup.
    CompositionLocalProvider(LocalAppUpdates provides controller) {
        content()
        if (controller != null) AppUpdatePrompt(controller)
    }
}

@Composable
private fun AppUpdatePrompt(controller: AppUpdateController) {
    val state by controller.state.collectAsStateWithLifecycle()
    val skipped by controller.skippedVersion.collectAsStateWithLifecycle()
    // What the user closed, by stage and version: a later stage of the same update shows again.
    var dismissed by remember { mutableStateOf<String?>(null) }

    val key = promptKey(state) ?: return
    if (key == dismissed) return
    val close = { dismissed = key }

    when (val s = state) {
        is AppUpdateState.Available -> {
            if (s.update.version == skipped) return
            AvailableDialog(controller, s, onClose = close)
        }
        is AppUpdateState.Downloading -> DownloadingDialog(s, onClose = close)
        is AppUpdateState.ReadyToRestart ->
            PromptDialog(
                symbol = MaterialSymbols.Download,
                title = stringRes(Res.string.app_update_ready_title),
                description = stringRes(Res.string.app_update_ready, s.update.version),
                confirmText = stringRes(Res.string.app_update_restart),
                onConfirm = controller::restartToUpdate,
                dismissText = stringRes(Res.string.app_update_later),
                onDismiss = close,
                dismissible = true,
            )
        is AppUpdateState.Failed -> {
            // A failed background check is not worth a dialog; settings say it.
            val update = s.update ?: return
            PromptDialog(
                symbol = MaterialSymbols.Warning,
                title = stringRes(Res.string.app_update_available_title, update.version),
                description = stringRes(failureText(s.failure)),
                confirmText = stringRes(Res.string.app_update_retry),
                onConfirm = controller::install,
                dismissText = stringRes(Res.string.app_update_later),
                onDismiss = close,
                isError = true,
                dismissible = true,
            )
        }
        else -> Unit
    }
}

private fun promptKey(state: AppUpdateState): String? =
    when (state) {
        is AppUpdateState.Available -> "available:${state.update.version}"
        is AppUpdateState.Downloading -> "downloading:${state.update.version}"
        is AppUpdateState.ReadyToRestart -> "ready:${state.update.version}"
        is AppUpdateState.Failed -> state.update?.let { "failed:${it.version}:${state.failure}" }
        else -> null
    }

@Composable
private fun AvailableDialog(
    controller: AppUpdateController,
    state: AppUpdateState.Available,
    onClose: () -> Unit,
) {
    val update = state.update
    val uriHandler = LocalUriHandler.current
    val installable = update.canInstall && controller.method.canInstall

    PromptDialog(
        symbol = MaterialSymbols.Download,
        title = stringRes(Res.string.app_update_available_title, update.version),
        description = stringRes(Res.string.app_update_available_body, controller.currentVersion),
        confirmText = stringRes(if (installable) Res.string.app_update_install else Res.string.app_update_open_page),
        onConfirm = {
            if (installable) {
                controller.install()
            } else {
                uriHandler.openUri(update.pageUrl)
                onClose()
            }
        },
        dismissText = stringRes(Res.string.app_update_later),
        onDismiss = onClose,
        dismissible = true,
    ) {
        if (update.releaseNotes.isNotBlank()) ReleaseNotes(update.releaseNotes)
        Text(
            text = updateMethodText(controller.method, update.canInstall),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = {
            controller.skip(update.version)
            onClose()
        }) {
            Text(stringRes(Res.string.app_update_skip))
        }
    }
}

/** The release's notes, as written (often markdown), in a box that scrolls when they run long. */
@Composable
private fun ReleaseNotes(notes: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringRes(Res.string.app_update_whats_new),
            style = MaterialTheme.typography.titleSmall,
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SelectionContainer {
                Text(
                    text = notes.trim(),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState()).padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun DownloadingDialog(
    state: AppUpdateState.Downloading,
    onClose: () -> Unit,
) {
    PromptDialog(
        symbol = MaterialSymbols.Download,
        title = stringRes(Res.string.app_update_downloading, state.update.version),
        description = null,
        confirmText = stringRes(Res.string.app_update_hide),
        onConfirm = onClose,
        dismissText = "",
        onDismiss = onClose,
        dismissible = true,
    ) {
        DownloadProgress(state)
    }
}

@Composable
internal fun DownloadProgress(state: AppUpdateState.Downloading) {
    val total = state.totalBytes
    if (total != null && total > 0) {
        LinearProgressIndicator(
            progress = { (state.bytesRead.toFloat() / total).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

/** How this install gets the update, in a sentence; for an update the app cannot install, why. */
@Composable
internal fun updateMethodText(
    method: UpdateMethod,
    updateHasFile: Boolean,
): String =
    when (method) {
        UpdateMethod.SelfInstall -> stringRes(if (updateHasFile) Res.string.app_update_method_self else Res.string.app_update_unsigned)
        UpdateMethod.SystemInstaller -> stringRes(if (updateHasFile) Res.string.app_update_method_system else Res.string.app_update_unsigned)
        is UpdateMethod.PackageManager -> stringRes(Res.string.app_update_method_package_manager, method.name, method.command)
        UpdateMethod.MoveToApplications -> stringRes(Res.string.app_update_method_move)
        UpdateMethod.DownloadPage -> stringRes(Res.string.app_update_method_download)
    }

internal fun failureText(failure: UpdateFailure): StringResource =
    when (failure) {
        UpdateFailure.CHECK_FAILED -> Res.string.app_update_failed_check
        UpdateFailure.DOWNLOAD_FAILED -> Res.string.app_update_failed_download
        UpdateFailure.VERIFICATION_FAILED -> Res.string.app_update_failed_verification
        UpdateFailure.NOT_WRITABLE -> Res.string.app_update_failed_not_writable
        UpdateFailure.INSTALL_FAILED -> Res.string.app_update_failed_install
    }
