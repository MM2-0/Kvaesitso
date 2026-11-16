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
package com.vitorpamplona.amethyst.commons.ui.keystorage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.keystorage.KeyStorageStatus
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.key_storage_save_keys
import com.vitorpamplona.amethyst.commons.resources.key_storage_try_again
import com.vitorpamplona.amethyst.commons.resources.key_storage_unlock
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.CardSurfaces
import kotlinx.coroutines.launch

/**
 * Where this device keeps private keys and whether that works, for Key Backup: an icon and a
 * short title, the system's error folded away, and the fix (try the keyring again, or unlock the
 * key file, or take back this run's "Don't save"). Renders nothing where the platform keeps keys without the user (Android).
 */
@Composable
fun KeyStorageStatusCard(modifier: Modifier = Modifier) {
    val controller = LocalKeyStorage.current ?: return
    val status by controller.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val isError = status is KeyStorageStatus.KeyringUnreachable

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardSurfaces.color,
        border = CardSurfaces.border,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val badge = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                val onBadge = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                Box(Modifier.size(40.dp).background(badge, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(keyStorageSymbol(status), contentDescription = null, tint = onBadge, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = keyStorageDescription(status),
                    // One line beside its button on a 360 dp phone.
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                when (val current = status) {
                    is KeyStorageStatus.KeyringUnreachable ->
                        FilledTonalButton(onClick = { scope.launch { controller.retry(Route.AccountBackup) } }, contentPadding = ActionPadding) {
                            Text(stringRes(Res.string.key_storage_try_again))
                        }
                    is KeyStorageStatus.PasswordFile ->
                        if (current.hasPassword && !current.unlocked) {
                            FilledTonalButton(onClick = { scope.launch { controller.unlock(Route.AccountBackup) } }, contentPadding = ActionPadding) {
                                Text(stringRes(Res.string.key_storage_unlock))
                            }
                        }
                    KeyStorageStatus.NotKept ->
                        FilledTonalButton(onClick = { scope.launch { controller.saveKeys() } }, contentPadding = ActionPadding) {
                            Text(stringRes(Res.string.key_storage_save_keys))
                        }
                    else -> Unit
                }
            }
            (status as? KeyStorageStatus.KeyringUnreachable)?.let { KeyStorageErrorDetails(it.detail) }
        }
    }
}

private val ActionPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
