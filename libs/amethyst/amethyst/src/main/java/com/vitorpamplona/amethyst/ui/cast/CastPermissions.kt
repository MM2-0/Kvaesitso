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
package com.vitorpamplona.amethyst.ui.cast

import android.Manifest
import android.content.Context
import android.os.Build
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
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.cast_local_network_permission_message
import com.vitorpamplona.amethyst.commons.resources.cast_local_network_permission_open_settings
import com.vitorpamplona.amethyst.commons.resources.cast_local_network_permission_title
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.ui.call.hasPermission
import com.vitorpamplona.amethyst.ui.call.openAppSettings

// Android 17 (API 37) Local Network Protection gates the mDNS/multicast device
// discovery the Cast SDK relies on behind the dangerous ACCESS_LOCAL_NETWORK
// runtime permission. Without it the picker silently finds zero receivers.
// Older OSes have no LNP and never define the permission, so the gate is a
// no-op there.
private const val LOCAL_NETWORK_PROTECTION_SDK = 37

private fun needsLocalNetworkPermission(): Boolean = Build.VERSION.SDK_INT >= LOCAL_NETWORK_PROTECTION_SDK

fun hasLocalNetworkPermission(context: Context): Boolean = !needsLocalNetworkPermission() || hasPermission(context, Manifest.permission.ACCESS_LOCAL_NETWORK)

/**
 * Returns a click handler that ensures [Manifest.permission.ACCESS_LOCAL_NETWORK]
 * is granted before running [onGranted] (which opens the Cast device picker).
 *
 * On Android 17+ the permission is requested on first tap; if the user denies
 * it, a dialog deep-links to app settings. On older OSes the permission does
 * not exist, so [onGranted] runs immediately.
 */
@Composable
fun rememberCastWithLocalNetworkPermission(
    context: Context,
    onGranted: () -> Unit,
): () -> Unit {
    var showDeniedDialog by remember { mutableStateOf(false) }

    val launcher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            // A silent deny (permanently-denied, where Android skips the dialog)
            // also lands here with granted=false, so surface the deep-link
            // dialog rather than failing silently with an empty picker.
            if (granted) onGranted() else showDeniedDialog = true
        }

    if (showDeniedDialog) {
        LocalNetworkPermissionDeniedDialog(
            onDismiss = { showDeniedDialog = false },
            onOpenSettings = {
                showDeniedDialog = false
                openAppSettings(context)
            },
        )
    }

    return remember(onGranted) {
        {
            if (hasLocalNetworkPermission(context)) {
                onGranted()
            } else {
                launcher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
            }
        }
    }
}

@Composable
private fun LocalNetworkPermissionDeniedDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.cast_local_network_permission_title)) },
        text = { Text(stringRes(Res.string.cast_local_network_permission_message)) },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(stringRes(Res.string.cast_local_network_permission_open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(Res.string.cancel))
            }
        },
    )
}
