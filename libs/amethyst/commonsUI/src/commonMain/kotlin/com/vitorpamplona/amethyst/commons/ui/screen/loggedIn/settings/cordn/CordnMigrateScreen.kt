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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.cordn.ui.BusyLabel
import com.vitorpamplona.amethyst.commons.cordn.ui.SettingsFormBlock
import com.vitorpamplona.amethyst.commons.model.cordn.CordnRuntime
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.taggedAs
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cordn_group_unavailable
import com.vitorpamplona.amethyst.commons.resources.cordn_group_unavailable_detail
import com.vitorpamplona.amethyst.commons.resources.cordn_migrate_cancel
import com.vitorpamplona.amethyst.commons.resources.cordn_migrate_cancel_desc
import com.vitorpamplona.amethyst.commons.resources.cordn_migrate_code_note
import com.vitorpamplona.amethyst.commons.resources.cordn_migrate_done
import com.vitorpamplona.amethyst.commons.resources.cordn_migrate_explainer
import com.vitorpamplona.amethyst.commons.resources.cordn_migrate_export
import com.vitorpamplona.amethyst.commons.resources.cordn_migrate_exporting
import com.vitorpamplona.amethyst.commons.resources.cordn_migrate_exposure_body
import com.vitorpamplona.amethyst.commons.resources.cordn_migrate_exposure_title
import com.vitorpamplona.amethyst.commons.resources.cordn_migrate_handed_off
import com.vitorpamplona.amethyst.commons.resources.cordn_migrate_handed_off_desc
import com.vitorpamplona.amethyst.commons.resources.cordn_migrate_import
import com.vitorpamplona.amethyst.commons.resources.cordn_migrate_importing
import com.vitorpamplona.amethyst.commons.resources.cordn_migrate_no_groups
import com.vitorpamplona.amethyst.commons.resources.cordn_migrate_no_server
import com.vitorpamplona.amethyst.commons.resources.cordn_migrate_paste
import com.vitorpamplona.amethyst.commons.resources.cordn_migrate_receive
import com.vitorpamplona.amethyst.commons.resources.cordn_migrate_replaces_warning
import com.vitorpamplona.amethyst.commons.resources.cordn_migrate_scan
import com.vitorpamplona.amethyst.commons.resources.cordn_migrate_scan_this
import com.vitorpamplona.amethyst.commons.resources.cordn_migrate_section_receive
import com.vitorpamplona.amethyst.commons.resources.cordn_migrate_section_send
import com.vitorpamplona.amethyst.commons.resources.cordn_migrate_sign_in_first
import com.vitorpamplona.amethyst.commons.resources.cordn_migrate_title
import com.vitorpamplona.amethyst.commons.ui.components.EmptyState
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode.QrCodeDrawer
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode.SimpleQrCodeScanner
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsSection
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.cordn.appMultiDevice.CordnHandoffCode
import com.vitorpamplona.cordn.appMultiDevice.CordnMigration
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Moving this account's cordn groups to a phone the user is switching to.
 *
 * ## Why this is a handoff and not sync
 *
 * `spec/applications/multi-device.md` §10 leaves one case unresolved: two
 * devices of one identity committing inside a single delivery round-trip reach
 * the same epoch with different states, and §15 concedes that equal-epoch MLS
 * states have no merge function. This screen is buildable because a migration
 * has one writer — which is only true if this device stops afterwards, which
 * is what the handed-off state below is.
 *
 * ## What the user is told, and where
 *
 * Two things are said at the moment they are decided rather than in a help
 * page. Before exporting: the encrypted documents leave the device for a
 * storage server. Before importing: this replaces whatever cordn groups are
 * already here, because MLS state cannot be merged. Both are irreversible in
 * the ways that matter, and neither is discoverable afterwards.
 */
@Composable
fun CordnMigrateScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val runtime = accountViewModel.account.cordnRuntime

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.cordn_migrate_title), nav) },
    ) { padding ->
        if (runtime == null) {
            EmptyState(
                title = stringRes(Res.string.cordn_group_unavailable),
                description = stringRes(Res.string.cordn_group_unavailable_detail),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        val handedOff by runtime.handoff.handedOff.collectAsStateWithLifecycle()

        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (handedOff) {
                HandedOff(runtime)
                return@Column
            }

            Text(
                text = stringRes(Res.string.cordn_migrate_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Two boxes, because they are two different devices' jobs and
            // only one of them is yours today. A rule between them said they
            // were separate without saying which was which — and doing the
            // wrong one here hands your account to another phone.
            SettingsSection(Res.string.cordn_migrate_section_send) {
                SettingsFormBlock {
                    SendSide(runtime, accountViewModel)
                }
            }

            SettingsSection(Res.string.cordn_migrate_section_receive) {
                SettingsFormBlock {
                    ReceiveSide(runtime, accountViewModel)
                }
            }
        }
    }
}

/**
 * The state that makes the rest of this safe.
 *
 * Deliberately not phrased as an error. Nothing is broken and nothing was
 * deleted — the device stood down on purpose, and the groups are still on
 * disk so taking it back is possible.
 */
@Composable
private fun HandedOff(runtime: CordnRuntime) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringRes(Res.string.cordn_migrate_handed_off), style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringRes(Res.string.cordn_migrate_handed_off_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Text(
        text = stringRes(Res.string.cordn_migrate_cancel_desc),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    OutlinedButton(
        onClick = {
            busy = true
            scope.launch {
                try {
                    runtime.cancelHandOff()
                } finally {
                    busy = false
                }
            }
        },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringRes(Res.string.cordn_migrate_cancel))
    }
}

@Composable
private fun SendSide(
    runtime: CordnRuntime,
    accountViewModel: AccountViewModel,
) {
    val scope = rememberCoroutineScope()
    val services = LocalAppServices.current
    var code by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val noServer = stringRes(Res.string.cordn_migrate_no_server)
    val noGroups = stringRes(Res.string.cordn_migrate_no_groups)

    Text(
        text = stringRes(Res.string.cordn_migrate_sign_in_first),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // Said before the button, because this is the moment the decision is made
    // and the upload cannot be taken back afterwards.
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringRes(Res.string.cordn_migrate_exposure_title), style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringRes(Res.string.cordn_migrate_exposure_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    code?.let {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringRes(Res.string.cordn_migrate_scan_this), style = MaterialTheme.typography.titleSmall)
            QrCodeDrawer(it, Modifier.size(260.dp))
            SelectionContainer { Text(it, style = MaterialTheme.typography.labelSmall) }
            Text(
                text = stringRes(Res.string.cordn_migrate_code_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }

    Button(
        onClick = {
            busy = true
            error = null
            scope.launch {
                try {
                    val servers = listOfNotNull(accountViewModel.account.settings.defaultFileServer.baseUrl)
                    if (servers.isEmpty()) {
                        error = noServer
                        return@launch
                    }
                    if (runtime.migrationSnapshot().groups.isEmpty()) {
                        error = noGroups
                        return@launch
                    }

                    val migration =
                        CordnMigration(
                            accountViewModel.account.client.taggedAs(SubPurpose.COORDINATOR_GROUPS),
                            accountViewModel.account.signer,
                            services.cordnBlobStore(servers),
                        )
                    code = runtime.handOff(migration, accountViewModel.account.outboxRelays.flow.value).encode()
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    error = e.message
                } finally {
                    busy = false
                }
            }
        },
        enabled = !busy && code == null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        // The label already says "Exporting"; the spinner says it is still
        // going, which a static word cannot.
        BusyLabel(busy, stringRes(if (busy) Res.string.cordn_migrate_exporting else Res.string.cordn_migrate_export))
    }
}

@Composable
private fun ReceiveSide(
    runtime: CordnRuntime,
    accountViewModel: AccountViewModel,
) {
    val scope = rememberCoroutineScope()
    val services = LocalAppServices.current
    var typed by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf<Int?>(null) }
    val badCode = stringRes(Res.string.cordn_migrate_scan)

    Text(stringRes(Res.string.cordn_migrate_receive), style = MaterialTheme.typography.titleSmall)

    // Said before the button, for the same reason as the exposure card: an
    // import replaces, and MLS state cannot be merged back afterwards.
    Text(
        text = stringRes(Res.string.cordn_migrate_replaces_warning),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (scanning) {
        SimpleQrCodeScanner {
            scanning = false
            if (it != null) typed = it
        }
    }

    OutlinedButton(onClick = { scanning = true }, modifier = Modifier.fillMaxWidth()) {
        Text(stringRes(Res.string.cordn_migrate_scan))
    }

    OutlinedTextField(
        value = typed,
        onValueChange = {
            typed = it
            error = null
        },
        label = { Text(stringRes(Res.string.cordn_migrate_paste)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    done?.let { Text(pluralStringRes(Res.plurals.cordn_migrate_done, it, it), style = MaterialTheme.typography.bodyMedium) }
    error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }

    Button(
        onClick = {
            val parsed = CordnHandoffCode.decodeOrNull(typed.trim())
            if (parsed == null) {
                error = badCode
                return@Button
            }
            busy = true
            error = null
            scope.launch {
                try {
                    val migration =
                        CordnMigration(
                            accountViewModel.account.client.taggedAs(SubPurpose.COORDINATOR_GROUPS),
                            accountViewModel.account.signer,
                            services.cordnBlobStore(emptyList()),
                        )
                    val snapshot = migration.fetch(parsed)
                    runtime.adoptMigration(snapshot)
                    done = snapshot.groups.size
                    typed = ""
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    error = e.message
                } finally {
                    busy = false
                }
            }
        },
        enabled = !busy && typed.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        BusyLabel(busy, stringRes(if (busy) Res.string.cordn_migrate_importing else Res.string.cordn_migrate_import))
    }
}
