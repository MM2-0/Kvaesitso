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
package com.vitorpamplona.amethyst.ui.call

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.call_permission_denied_cancel
import com.vitorpamplona.amethyst.commons.resources.call_permission_denied_open_settings
import com.vitorpamplona.amethyst.commons.resources.call_permission_denied_title
import com.vitorpamplona.amethyst.commons.resources.call_permission_denied_video
import com.vitorpamplona.amethyst.commons.resources.call_permission_denied_voice
import com.vitorpamplona.amethyst.commons.ui.stringRes

fun hasPermission(
    context: Context,
    permission: String,
) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

fun hasCallPermissions(
    context: Context,
    isVideo: Boolean,
): Boolean {
    if (!hasPermission(context, Manifest.permission.RECORD_AUDIO)) return false
    if (isVideo && !hasPermission(context, Manifest.permission.CAMERA)) return false
    return true
}

fun buildCallPermissions(isVideo: Boolean): Array<String> {
    val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
    if (isVideo) {
        permissions.add(Manifest.permission.CAMERA)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
    }
    return permissions.toTypedArray()
}

fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
        )
    }
}

@Composable
fun rememberCallWithPermission(
    context: Context,
    isVideo: Boolean = false,
    onCall: () -> Unit,
): () -> Unit {
    val permissions = remember(isVideo) { buildCallPermissions(isVideo) }
    var showDeniedDialog by remember { mutableStateOf(false) }

    val launcher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { _ ->
            // Bluetooth is optional — proceed if core permissions are granted.
            // If core permissions are still denied (including the silent
            // permanently-denied case where Android skips the dialog), surface
            // the deep-link dialog instead of failing silently.
            if (hasCallPermissions(context, isVideo)) {
                onCall()
            } else {
                showDeniedDialog = true
            }
        }

    val bluetoothLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { _ ->
            // BT result is best-effort and never blocks the call.
        }

    if (showDeniedDialog) {
        CallPermissionDeniedDialog(
            isVideo = isVideo,
            onDismiss = { showDeniedDialog = false },
            onOpenSettings = {
                showDeniedDialog = false
                openAppSettings(context)
            },
        )
    }

    return remember(onCall, isVideo) {
        {
            if (hasCallPermissions(context, isVideo)) {
                // Core permissions granted; request BT separately so the
                // result callback doesn't double-fire onCall().
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    !hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                ) {
                    bluetoothLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
                }
                onCall()
            } else {
                launcher.launch(permissions)
            }
        }
    }
}

@Composable
private fun CallPermissionDeniedDialog(
    isVideo: Boolean,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.call_permission_denied_title)) },
        text = {
            Text(
                stringRes(
                    if (isVideo) {
                        Res.string.call_permission_denied_video
                    } else {
                        Res.string.call_permission_denied_voice
                    },
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(stringRes(Res.string.call_permission_denied_open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(Res.string.call_permission_denied_cancel))
            }
        },
    )
}
