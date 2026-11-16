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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.cordn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.cordn.ui.BusyLabel
import com.vitorpamplona.amethyst.commons.cordn.ui.SettingsFormBlock
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.cordn_backup_contents_body
import com.vitorpamplona.amethyst.commons.resources.cordn_backup_contents_title
import com.vitorpamplona.amethyst.commons.resources.cordn_backup_explainer
import com.vitorpamplona.amethyst.commons.resources.cordn_backup_export
import com.vitorpamplona.amethyst.commons.resources.cordn_backup_exported
import com.vitorpamplona.amethyst.commons.resources.cordn_backup_failed
import com.vitorpamplona.amethyst.commons.resources.cordn_backup_passphrase
import com.vitorpamplona.amethyst.commons.resources.cordn_backup_restore
import com.vitorpamplona.amethyst.commons.resources.cordn_backup_restore_body
import com.vitorpamplona.amethyst.commons.resources.cordn_backup_restore_confirm_body
import com.vitorpamplona.amethyst.commons.resources.cordn_backup_restore_confirm_title
import com.vitorpamplona.amethyst.commons.resources.cordn_backup_restored
import com.vitorpamplona.amethyst.commons.resources.cordn_backup_section_export
import com.vitorpamplona.amethyst.commons.resources.cordn_backup_section_passphrase
import com.vitorpamplona.amethyst.commons.resources.cordn_backup_section_restore
import com.vitorpamplona.amethyst.commons.resources.cordn_backup_title
import com.vitorpamplona.amethyst.commons.resources.cordn_group_unavailable
import com.vitorpamplona.amethyst.commons.resources.cordn_group_unavailable_detail
import com.vitorpamplona.amethyst.commons.ui.components.EmptyState
import com.vitorpamplona.amethyst.commons.ui.components.rememberFileBytesAccess
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsSection
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Exporting and restoring cordn state.
 *
 * ## Why cordn has this when the rest of Amethyst does not
 *
 * Amethyst's "backup" is a key backup: your nsec, from which a relay-backed
 * account rebuilds itself. A cordn group does not work that way. It is MLS
 * state on **one device** plus an ordered stream on a coordinator that cannot
 * read a byte of it, so the key alone restores nothing. Lose the device and
 * the group is gone — not "until someone re-invites you", because a fresh
 * invitation begins at a new epoch and everything before it stays unreadable
 * forever.
 *
 * ## Restoring replaces a device, and the screen says so before the tap
 *
 * An MLS state export is a cloneable identity. Two devices holding one
 * group's state and both committing fork the ratchet tree, and MLS does not
 * recover: after the fork, messages silently fail to decrypt for somebody,
 * with nothing on screen to explain it. So restoring wipes this device's
 * cordn state and takes the file's — it is not a merge and it is not sync,
 * and presenting it as either would be selling multi-device support that
 * §5.3 deliberately does not exist.
 *
 * ## The file is as sensitive as the conversations
 *
 * It carries ratchet trees and KeyPackage private halves. The passphrase is
 * the only thing protecting it once it leaves the app, which is why there is
 * no "export without a passphrase" and why the warning names what is inside.
 */
@Composable
fun CordnBackupScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val runtime = accountViewModel.account.cordnRuntime
    val files = rememberFileBytesAccess()
    val scope = rememberCoroutineScope()

    var passphrase by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<ByteArray?>(null) }

    val exported = stringRes(Res.string.cordn_backup_exported)
    val restored = stringRes(Res.string.cordn_backup_restored)
    val failed = stringRes(Res.string.cordn_backup_failed)

    fun export() {
        val runtimeNow = runtime ?: return
        scope.launch {
            busy = true
            error = null
            status = null
            try {
                // The archive is only built once the user picked where it goes.
                val saved = files.save("cordn-backup.bin", "application/octet-stream") { runtimeNow.exportArchive(passphrase) }
                if (saved) {
                    status = exported
                    passphrase = ""
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error = e.message ?: failed
            } finally {
                busy = false
            }
        }
    }

    fun pickRestore() {
        scope.launch {
            try {
                pendingRestore = files.open(listOf("*/*"))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error = e.message ?: failed
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    // Outcomes go to a snackbar rather than two Text lines at the bottom of a
    // scrolling form. They were easy to scroll past, they never left once
    // shown, and an export that had just failed sat under a button that looked
    // ready to try again.
    LaunchedEffect(status) {
        status?.let {
            snackbarHostState.showSnackbar(it)
            status = null
        }
    }
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            error = null
        }
    }

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.cordn_backup_title), nav) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (runtime == null) {
            // The app's own empty state, centred and titled, rather than a
            // sentence stranded in the top-left corner.
            EmptyState(
                title = stringRes(Res.string.cordn_group_unavailable),
                description = stringRes(Res.string.cordn_group_unavailable_detail),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringRes(Res.string.cordn_backup_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // The passphrase is its own section because it governs both of the
            // two below: the same word exports and restores, and a field
            // floating above two unrelated-looking cards did not say so.
            SettingsSection(Res.string.cordn_backup_section_passphrase) {
                SettingsFormBlock {
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = {
                            passphrase = it
                            error = null
                        },
                        label = { Text(stringRes(Res.string.cordn_backup_passphrase)) },
                        visualTransformation = PasswordVisualTransformation(),
                        // The dots are only half of it. Without the password
                        // keyboard type the IME treats this as ordinary prose:
                        // it offers the passphrase in the suggestion strip, in
                        // the clear, and learns it into the personalised
                        // dictionary, where it outlives the app. Every other
                        // secret field in Amethyst already sets this; this one
                        // was the only one that did not.
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            SettingsSection(Res.string.cordn_backup_section_export) {
                SettingsFormBlock {
                    Text(stringRes(Res.string.cordn_backup_contents_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = stringRes(Res.string.cordn_backup_contents_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Button(
                        onClick = ::export,
                        // No passphrase, no export. The file carries ratchet
                        // trees and private key material; there is no version
                        // of it that is safe to write unprotected.
                        enabled = !busy && passphrase.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        // Key derivation here is scrypt, which is slow on
                        // purpose, so a greyed-out button was the only sign
                        // anything was happening for several seconds.
                        BusyLabel(busy, stringRes(Res.string.cordn_backup_export))
                    }
                }
            }

            SettingsSection(Res.string.cordn_backup_section_restore) {
                SettingsFormBlock {
                    Text(
                        text = stringRes(Res.string.cordn_backup_restore_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Button(
                        onClick = ::pickRestore,
                        enabled = !busy && passphrase.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        BusyLabel(busy, stringRes(Res.string.cordn_backup_restore))
                    }
                }
            }
        }
    }

    pendingRestore?.let { bytes ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringRes(Res.string.cordn_backup_restore_confirm_title)) },
            text = { Text(stringRes(Res.string.cordn_backup_restore_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    val runtimeNow = runtime ?: return@TextButton
                    scope.launch {
                        busy = true
                        error = null
                        status = null
                        try {
                            runtimeNow.importArchive(bytes, passphrase)
                            status = restored
                            passphrase = ""
                        } catch (e: Exception) {
                            currentCoroutineContext().ensureActive()
                            error = e.message ?: failed
                        } finally {
                            busy = false
                        }
                    }
                }) {
                    Text(stringRes(Res.string.cordn_backup_restore), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text(stringRes(Res.string.cancel)) }
            },
        )
    }
}
