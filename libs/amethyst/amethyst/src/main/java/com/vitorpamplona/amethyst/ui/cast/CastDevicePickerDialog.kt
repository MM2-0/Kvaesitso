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

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cast_generic_receiver
import com.vitorpamplona.amethyst.commons.resources.cast_searching_for_devices
import com.vitorpamplona.amethyst.commons.resources.cast_to_device_dialog_title
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionDialog
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionRow
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionSection
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.service.cast.CastRegistry
import com.vitorpamplona.amethyst.service.cast.CastRequest
import com.vitorpamplona.amethyst.service.cast.CastSessionState
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.launch

private const val TAG = "CastPicker"

@Composable
fun CastDevicePickerDialog(
    request: CastRequest,
    onDismiss: () -> Unit,
    registry: CastRegistry = Amethyst.instance.castRegistry,
) {
    androidx.compose.runtime.DisposableEffect(registry) {
        Log.d(TAG) { "open url=${request.url} mime=${request.mimeType}" }
        registry.startDiscovery()
        onDispose {
            Log.d(TAG) { "dispose -> stopDiscovery" }
            registry.stopDiscovery()
        }
    }

    val devices by registry.devices.collectAsStateWithLifecycle()
    val sessionState by registry.sessionState.collectAsStateWithLifecycle()
    // Application-scoped so the cast survives dialog dismissal — the dialog
    // dismisses ~10ms after the device tap, which would cancel a composition-
    // scoped coroutine before remoteMediaClient.load() could run, leaving the
    // receiver showing the default-receiver splash with no media loaded.
    val castScope = Amethyst.instance.applicationIOScope

    M3ActionDialog(
        title = stringRes(Res.string.cast_to_device_dialog_title),
        onDismiss = onDismiss,
    ) {
        if (devices.isEmpty()) {
            Text(
                text = stringRes(Res.string.cast_searching_for_devices),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
        } else {
            M3ActionSection {
                devices.forEach { device ->
                    M3ActionRow(
                        icon = MaterialSymbols.Cast,
                        text = device.name,
                    ) {
                        Log.d(TAG) { "tap device=${device.name}" }
                        // Keep discovery alive across the dialog's onDispose so the caster's
                        // session listener stays registered until the cast attempt finishes.
                        // Without this the dialog's stopDiscovery (~10ms after tap) tears the
                        // SessionManagerListener down before onSessionStarted fires.
                        registry.startDiscovery()
                        castScope.launch {
                            try {
                                registry.cast(device, request)
                            } finally {
                                registry.stopDiscovery()
                            }
                        }
                        onDismiss()
                    }
                }
            }
        }

        val error = sessionState as? CastSessionState.Error
        if (error != null) {
            Text(
                text = castErrorText(error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun castErrorText(error: CastSessionState.Error): String =
    stringRes(
        error.message.text,
        error.device?.name ?: stringRes(Res.string.cast_generic_receiver),
        error.message.detail,
    )
