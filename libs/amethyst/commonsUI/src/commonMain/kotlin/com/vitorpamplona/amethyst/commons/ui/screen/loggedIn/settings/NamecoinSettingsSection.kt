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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.browser.OmniboxInput
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.namecoin.DEFAULT_ELECTRUMX_SERVERS
import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.namecoin.NamecoinSettings
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.action_save
import com.vitorpamplona.amethyst.commons.resources.namecoin_active_servers
import com.vitorpamplona.amethyst.commons.resources.namecoin_add_electrumx_server
import com.vitorpamplona.amethyst.commons.resources.namecoin_add_server
import com.vitorpamplona.amethyst.commons.resources.namecoin_backend_core_rpc_description
import com.vitorpamplona.amethyst.commons.resources.namecoin_backend_core_rpc_label
import com.vitorpamplona.amethyst.commons.resources.namecoin_backend_electrumx_description
import com.vitorpamplona.amethyst.commons.resources.namecoin_backend_electrumx_label
import com.vitorpamplona.amethyst.commons.resources.namecoin_badge_custom
import com.vitorpamplona.amethyst.commons.resources.namecoin_badge_default
import com.vitorpamplona.amethyst.commons.resources.namecoin_cert_fingerprint
import com.vitorpamplona.amethyst.commons.resources.namecoin_custom_servers_exclusive
import com.vitorpamplona.amethyst.commons.resources.namecoin_device_info
import com.vitorpamplona.amethyst.commons.resources.namecoin_diagnostics
import com.vitorpamplona.amethyst.commons.resources.namecoin_fallback_custom_description
import com.vitorpamplona.amethyst.commons.resources.namecoin_fallback_custom_label
import com.vitorpamplona.amethyst.commons.resources.namecoin_fallback_custom_none
import com.vitorpamplona.amethyst.commons.resources.namecoin_fallback_default_already
import com.vitorpamplona.amethyst.commons.resources.namecoin_fallback_default_custom_failed
import com.vitorpamplona.amethyst.commons.resources.namecoin_fallback_default_everything_fails
import com.vitorpamplona.amethyst.commons.resources.namecoin_fallback_default_label
import com.vitorpamplona.amethyst.commons.resources.namecoin_fallback_policy
import com.vitorpamplona.amethyst.commons.resources.namecoin_fallback_policy_description
import com.vitorpamplona.amethyst.commons.resources.namecoin_last_test
import com.vitorpamplona.amethyst.commons.resources.namecoin_no_custom_servers
import com.vitorpamplona.amethyst.commons.resources.namecoin_no_test_yet
import com.vitorpamplona.amethyst.commons.resources.namecoin_pin_cert_accept
import com.vitorpamplona.amethyst.commons.resources.namecoin_pin_cert_body
import com.vitorpamplona.amethyst.commons.resources.namecoin_pin_cert_reject
import com.vitorpamplona.amethyst.commons.resources.namecoin_pin_cert_title
import com.vitorpamplona.amethyst.commons.resources.namecoin_remove_server
import com.vitorpamplona.amethyst.commons.resources.namecoin_reset_to_defaults
import com.vitorpamplona.amethyst.commons.resources.namecoin_resolution_backend
import com.vitorpamplona.amethyst.commons.resources.namecoin_resolution_subtitle
import com.vitorpamplona.amethyst.commons.resources.namecoin_resolution_title
import com.vitorpamplona.amethyst.commons.resources.namecoin_response_time
import com.vitorpamplona.amethyst.commons.resources.namecoin_rpc_cert_sha256
import com.vitorpamplona.amethyst.commons.resources.namecoin_rpc_cert_sha256_pinned
import com.vitorpamplona.amethyst.commons.resources.namecoin_rpc_connected_ms
import com.vitorpamplona.amethyst.commons.resources.namecoin_rpc_endpoint_description
import com.vitorpamplona.amethyst.commons.resources.namecoin_rpc_endpoint_title
import com.vitorpamplona.amethyst.commons.resources.namecoin_rpc_failed
import com.vitorpamplona.amethyst.commons.resources.namecoin_rpc_password
import com.vitorpamplona.amethyst.commons.resources.namecoin_rpc_test
import com.vitorpamplona.amethyst.commons.resources.namecoin_rpc_tls_rejected
import com.vitorpamplona.amethyst.commons.resources.namecoin_rpc_url
import com.vitorpamplona.amethyst.commons.resources.namecoin_rpc_url_hint
import com.vitorpamplona.amethyst.commons.resources.namecoin_rpc_username
import com.vitorpamplona.amethyst.commons.resources.namecoin_server_address_required
import com.vitorpamplona.amethyst.commons.resources.namecoin_server_input_hint
import com.vitorpamplona.amethyst.commons.resources.namecoin_server_invalid_format
import com.vitorpamplona.amethyst.commons.resources.namecoin_settings_explanation
import com.vitorpamplona.amethyst.commons.resources.namecoin_test_connection
import com.vitorpamplona.amethyst.commons.resources.namecoin_test_results
import com.vitorpamplona.amethyst.commons.resources.namecoin_test_success
import com.vitorpamplona.amethyst.commons.resources.namecoin_testing
import com.vitorpamplona.amethyst.commons.resources.namecoin_testing_next_server
import com.vitorpamplona.amethyst.commons.resources.namecoin_tls_info
import com.vitorpamplona.amethyst.commons.ui.note.DateTimeStyle
import com.vitorpamplona.amethyst.commons.ui.note.formatDateTime
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.util.deviceDescription
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumxServer
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinBackend
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinCoreRpcConfig
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.RpcProbeResult
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ServerTestResult
import com.vitorpamplona.quartz.utils.currentTimeMillis
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

/**
 * Complete settings section for Namecoin ElectrumX server configuration.
 *
 * Designed to sit in the Privacy / Settings screen alongside existing
 * Tor settings.
 *
 * @param settings      Current [NamecoinSettings] state
 * @param onToggleEnabled Called when user toggles the master switch
 * @param onAddServer     Called with `host:port[:tcp]` when user adds a server
 * @param onRemoveServer  Called with the server string to remove
 * @param onReset         Called when user resets to defaults
 * @param onTestServer    Suspend function to test a single server
 * @param onPinCert       Called with PEM string to persist a TOFU-pinned cert
 */
@Composable
fun NamecoinSettingsSection(
    settings: NamecoinSettings,
    onToggleEnabled: (Boolean) -> Unit,
    onAddServer: (String) -> Unit,
    onRemoveServer: (String) -> Unit,
    onReset: () -> Unit,
    onTestServer: suspend (ElectrumxServer) -> ServerTestResult,
    onPinCert: (String) -> Unit = {},
    onSetBackend: (NamecoinBackend) -> Unit = {},
    onSetCoreRpcConfig: (NamecoinCoreRpcConfig) -> Unit = {},
    onSetFallbackToCustomElectrumx: (Boolean) -> Unit = {},
    onSetFallbackToDefaultElectrumx: (Boolean) -> Unit = {},
    onTestCoreRpc: suspend (NamecoinCoreRpcConfig) -> RpcProbeResult = { _ ->
        RpcProbeResult(success = false, elapsedMs = 0, error = "not wired")
    },
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp)) {
        // ── Section header ─────────────────────────────────────────
        SectionHeader(enabled = settings.enabled, onToggle = onToggleEnabled)

        AnimatedVisibility(
            visible = settings.enabled,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column {
                Spacer(Modifier.height(12.dp))

                // ── Explanation ─────────────────────────────────────
                Text(
                    stringRes(Res.string.namecoin_settings_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(16.dp))

                BackendSelector(
                    selected = settings.backend,
                    onSelect = onSetBackend,
                )

                Spacer(Modifier.height(12.dp))

                AnimatedVisibility(
                    visible = settings.backend == NamecoinBackend.NAMECOIN_CORE_RPC,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column {
                        NamecoinCoreRpcSection(
                            config = settings.namecoinCoreRpc,
                            onConfigChange = onSetCoreRpcConfig,
                            onTestCoreRpc = onTestCoreRpc,
                            onPinCert = onPinCert,
                        )
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }

                FallbacksSection(
                    settings = settings,
                    onSetFallbackToCustomElectrumx = onSetFallbackToCustomElectrumx,
                    onSetFallbackToDefaultElectrumx = onSetFallbackToDefaultElectrumx,
                )

                Spacer(Modifier.height(16.dp))

                // ── Active servers display ─────────────────────────
                ActiveServersDisplay(settings = settings)

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                Spacer(Modifier.height(12.dp))

                // ── Custom servers list ────────────────────────────
                CustomServersList(
                    servers = settings.customServers,
                    onRemove = onRemoveServer,
                )

                // ── Add server input ───────────────────────────────
                AddServerInput(onAdd = onAddServer)

                Spacer(Modifier.height(8.dp))

                // ── Reset button ───────────────────────────────────
                if (settings.hasCustomServers) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onReset) {
                            Icon(
                                MaterialSymbols.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringRes(Res.string.namecoin_reset_to_defaults))
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                Spacer(Modifier.height(16.dp))

                // ── Test Connection ────────────────────────────────
                TestConnectionSection(
                    settings = settings,
                    onTestServer = onTestServer,
                    onPinCert = onPinCert,
                )
            }
        }
    }
}

// ── Test Connection ────────────────────────────────────────────────────

/**
 * Holds a cert pending user confirmation before pinning (TOFU).
 */
private data class PendingCertPin(
    val serverHost: String,
    val fingerprint: String,
    val pem: String,
)

@Composable
private fun TestConnectionSection(
    settings: NamecoinSettings,
    onTestServer: suspend (ElectrumxServer) -> ServerTestResult,
    onPinCert: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var isTesting by remember { mutableStateOf(false) }
    var testResults by remember { mutableStateOf<List<ServerTestResult>>(emptyList()) }
    var lastTestTimestamp by remember { mutableStateOf<Long?>(null) }
    // Certs discovered during testing that need user confirmation
    var pendingCerts by remember { mutableStateOf<List<PendingCertPin>>(emptyList()) }
    // Which cert is currently shown in the confirmation dialog
    var confirmingCert by remember { mutableStateOf<PendingCertPin?>(null) }

    val servers = settings.toElectrumxServers() ?: DEFAULT_ELECTRUMX_SERVERS

    // ── Cert confirmation dialog ───────────────────────────────
    confirmingCert?.let { pending ->
        AlertDialog(
            onDismissRequest = {
                // Remove from pending list and move to next (or close)
                pendingCerts = pendingCerts.drop(1)
                confirmingCert = pendingCerts.firstOrNull()
            },
            title = { Text(stringRes(Res.string.namecoin_pin_cert_title)) },
            text = {
                Column {
                    Text(
                        stringRes(Res.string.namecoin_pin_cert_body, pending.serverHost),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "SHA-256:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = pending.fingerprint,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onPinCert(pending.pem)
                    pendingCerts = pendingCerts.drop(1)
                    confirmingCert = pendingCerts.firstOrNull()
                }) {
                    Text(stringRes(Res.string.namecoin_pin_cert_accept))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingCerts = pendingCerts.drop(1)
                    confirmingCert = pendingCerts.firstOrNull()
                }) {
                    Text(stringRes(Res.string.namecoin_pin_cert_reject))
                }
            },
        )
    }

    Column {
        // ── Test button ────────────────────────────────────────
        Button(
            onClick = {
                if (!isTesting) {
                    isTesting = true
                    testResults = emptyList()
                    pendingCerts = emptyList()
                    scope.launch {
                        val results = mutableListOf<ServerTestResult>()
                        val newCerts = mutableListOf<PendingCertPin>()
                        for (server in servers) {
                            val result = onTestServer(server)
                            results.add(result)
                            testResults = results.toList()
                            // Collect certs for user confirmation (not auto-pinned)
                            val pem = result.serverCertPem
                            val fp = result.certFingerprint
                            if (result.success && pem != null && fp != null) {
                                newCerts.add(
                                    PendingCertPin(
                                        serverHost = "${server.host}:${server.port}",
                                        fingerprint = fp,
                                        pem = pem,
                                    ),
                                )
                            }
                        }
                        lastTestTimestamp = currentTimeMillis()
                        isTesting = false
                        // Show confirmation dialog for each new cert
                        if (newCerts.isNotEmpty()) {
                            pendingCerts = newCerts
                            confirmingCert = newCerts.first()
                        }
                    }
                }
            },
            enabled = !isTesting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isTesting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
                Text(stringRes(Res.string.namecoin_testing))
            } else {
                Text(stringRes(Res.string.namecoin_test_connection))
            }
        }

        // ── Per-server results ─────────────────────────────────
        if (testResults.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))

            Text(
                stringRes(Res.string.namecoin_test_results),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(6.dp))

            testResults.forEach { result ->
                ServerTestResultRow(result)
            }

            if (isTesting && testResults.size < servers.size) {
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringRes(Res.string.namecoin_testing_next_server),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ── Diagnostic card ────────────────────────────────────
        if (testResults.isNotEmpty() || lastTestTimestamp != null) {
            Spacer(Modifier.height(16.dp))
            DiagnosticCard(
                testResults = testResults,
                lastTestTimestamp = lastTestTimestamp,
            )
        }
    }
}

@Composable
private fun ServerTestResultRow(result: ServerTestResult) {
    val serverLabel = "${result.server.host}:${result.server.port}"
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = if (result.success) "✅" else "❌",
            fontSize = 14.sp,
            modifier = Modifier.padding(end = 6.dp, top = 1.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = serverLabel,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringRes(Res.string.namecoin_response_time, result.responseTimeMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (result.success) {
                Text(
                    text = stringRes(Res.string.namecoin_test_success),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF2E8B57),
                )
                val fp = result.certFingerprint
                if (fp != null) {
                    Text(
                        text = stringRes(Res.string.namecoin_cert_fingerprint, fp.take(23)),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                val errorText = result.error
                if (errorText != null) {
                    // Selectable: the user can paste the exact failure into a
                    // bug report or a message to the server operator.
                    SelectionContainer {
                        Text(
                            text = errorText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

// ── Diagnostic Card ────────────────────────────────────────────────────

@Composable
private fun DiagnosticCard(
    testResults: List<ServerTestResult>,
    lastTestTimestamp: Long?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                stringRes(Res.string.namecoin_diagnostics),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))

            // Last test timestamp
            if (lastTestTimestamp != null) {
                val formatted =
                    remember(lastTestTimestamp) {
                        formatDateTime(lastTestTimestamp, DateTimeStyle.MEDIUM, DateTimeStyle.SHORT)
                    }
                val successCount = testResults.count { it.success }
                val totalCount = testResults.size
                DiagnosticRow(
                    label = stringRes(Res.string.namecoin_last_test),
                    value = "$formatted ($successCount/$totalCount OK)",
                )
            } else {
                DiagnosticRow(
                    label = stringRes(Res.string.namecoin_last_test),
                    value = stringRes(Res.string.namecoin_no_test_yet),
                )
            }

            Spacer(Modifier.height(4.dp))

            // Device info
            DiagnosticRow(
                label = stringRes(Res.string.namecoin_device_info),
                value = deviceDescription(),
            )

            Spacer(Modifier.height(4.dp))

            // TLS info from test results
            val tlsVersions =
                testResults
                    .mapNotNull { it.tlsVersion }
                    .distinct()
            val tlsDisplay =
                if (tlsVersions.isNotEmpty()) {
                    tlsVersions.joinToString(", ")
                } else {
                    "—"
                }
            DiagnosticRow(
                label = stringRes(Res.string.namecoin_tls_info),
                value = tlsDisplay,
            )
        }
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(0.65f),
        )
    }
}

// ── Original Sub-composables ───────────────────────────────────────────

@Composable
private fun SectionHeader(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                MaterialSymbols.Lock,
                contentDescription = null,
                tint = Color(0xFF4A90D9), // Namecoin blue
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    stringRes(Res.string.namecoin_resolution_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringRes(Res.string.namecoin_resolution_subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
        )
    }
}

@Composable
private fun ActiveServersDisplay(settings: NamecoinSettings) {
    val servers = settings.toElectrumxServers() ?: DEFAULT_ELECTRUMX_SERVERS
    val isCustom = settings.hasCustomServers

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringRes(Res.string.namecoin_active_servers),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
            if (isCustom) {
                Text(
                    stringRes(Res.string.namecoin_badge_custom),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4A90D9),
                    modifier =
                        Modifier
                            .background(
                                Color(0xFF4A90D9).copy(alpha = 0.1f),
                                RoundedCornerShape(4.dp),
                            ).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            } else {
                Text(
                    stringRes(Res.string.namecoin_badge_default),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        servers.forEach { server ->
            ServerRow(
                displayText =
                    "${server.host}:${server.port}" +
                        if (!server.useSsl) " (tcp)" else " (tls)",
                isActive = true,
            )
        }
    }
}

@Composable
private fun CustomServersList(
    servers: List<String>,
    onRemove: (String) -> Unit,
) {
    if (servers.isEmpty()) {
        Text(
            stringRes(Res.string.namecoin_no_custom_servers),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(vertical = 4.dp),
        )
    } else {
        Text(
            stringRes(Res.string.namecoin_custom_servers_exclusive),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        servers.forEach { server ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = server,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { onRemove(server) },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        MaterialSymbols.Close,
                        contentDescription = stringRes(Res.string.namecoin_remove_server),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun AddServerInput(onAdd: (String) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    val kb = LocalSoftwareKeyboardController.current
    val serverAddressRequired = stringRes(Res.string.namecoin_server_address_required)
    val serverInvalidFormat = stringRes(Res.string.namecoin_server_invalid_format)

    fun tryAdd() {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            validationError = serverAddressRequired
            return
        }
        val parsed = NamecoinSettings.parseServerString(trimmed)
        if (parsed == null) {
            validationError = serverInvalidFormat
            return
        }
        validationError = null
        onAdd(trimmed)
        input = ""
        kb?.hide()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it
                validationError = null
            },
            label = { Text(stringRes(Res.string.namecoin_add_electrumx_server)) },
            placeholder = { Text(stringRes(Res.string.namecoin_server_input_hint)) },
            singleLine = true,
            isError = validationError != null,
            supportingText =
                validationError?.let { err ->
                    { Text(err, color = MaterialTheme.colorScheme.error) }
                },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { tryAdd() }),
            textStyle =
                MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
        )
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = { tryAdd() },
            modifier =
                Modifier
                    .padding(top = 8.dp)
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        RoundedCornerShape(8.dp),
                    ),
        ) {
            Icon(
                MaterialSymbols.Add,
                contentDescription = stringRes(Res.string.namecoin_add_server),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ServerRow(
    displayText: String,
    isActive: Boolean,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "•",
            fontSize = 10.sp,
            color =
                if (isActive) {
                    Color(0xFF2E8B57)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.padding(end = 6.dp),
        )
        Text(
            text = displayText,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ── Backend selector ───────────────────────────────────────────────────

@Composable
private fun BackendSelector(
    selected: NamecoinBackend,
    onSelect: (NamecoinBackend) -> Unit,
) {
    Column {
        Text(
            stringRes(Res.string.namecoin_resolution_backend),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))

        BackendRadioRow(
            label = stringRes(Res.string.namecoin_backend_electrumx_label),
            description = stringRes(Res.string.namecoin_backend_electrumx_description),
            selected = selected == NamecoinBackend.ELECTRUMX,
            onClick = { onSelect(NamecoinBackend.ELECTRUMX) },
        )
        BackendRadioRow(
            label = stringRes(Res.string.namecoin_backend_core_rpc_label),
            description = stringRes(Res.string.namecoin_backend_core_rpc_description),
            selected = selected == NamecoinBackend.NAMECOIN_CORE_RPC,
            onClick = { onSelect(NamecoinBackend.NAMECOIN_CORE_RPC) },
        )
    }
}

@Composable
private fun BackendRadioRow(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Namecoin Core RPC section ──────────────────────────────────────────

@Composable
private fun NamecoinCoreRpcSection(
    config: NamecoinCoreRpcConfig,
    onConfigChange: (NamecoinCoreRpcConfig) -> Unit,
    onTestCoreRpc: suspend (NamecoinCoreRpcConfig) -> RpcProbeResult,
    onPinCert: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var url by rememberSaveable(config.url) { mutableStateOf(config.url) }
    var user by rememberSaveable(config.username) { mutableStateOf(config.username) }
    var pass by rememberSaveable(config.password) { mutableStateOf(config.password) }
    var passVisible by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var lastProbe by remember { mutableStateOf<RpcProbeResult?>(null) }
    // Cert that the probe captured but the user hasn't yet accepted/rejected.
    var pendingPin by remember { mutableStateOf<PendingCertPin?>(null) }

    // ── Cert confirmation dialog (TOFU) ─────────────────────────────────────
    pendingPin?.let { pin ->
        AlertDialog(
            onDismissRequest = { pendingPin = null },
            title = { Text(stringRes(Res.string.namecoin_pin_cert_title)) },
            text = {
                Column {
                    Text(
                        stringRes(Res.string.namecoin_pin_cert_body, pin.serverHost),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "SHA-256:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = pin.fingerprint,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    // Persist the PEM to the shared trust store AND flip
                    // usePinnedTrustStore on the config so future calls
                    // route through the pinned factory.
                    onPinCert(pin.pem)
                    onConfigChange(
                        config.copy(
                            url = url.trim(),
                            username = user.trim(),
                            password = pass,
                            usePinnedTrustStore = true,
                        ),
                    )
                    pendingPin = null
                }) {
                    Text(stringRes(Res.string.namecoin_pin_cert_accept))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPin = null }) {
                    Text(stringRes(Res.string.namecoin_pin_cert_reject))
                }
            },
        )
    }

    fun commit() {
        onConfigChange(
            config.copy(
                url = url.trim(),
                username = user.trim(),
                password = pass,
            ),
        )
    }

    Column {
        Text(
            stringRes(Res.string.namecoin_rpc_endpoint_title),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringRes(Res.string.namecoin_rpc_endpoint_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text(stringRes(Res.string.namecoin_rpc_url)) },
            placeholder = { Text(stringRes(Res.string.namecoin_rpc_url_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onAny = { commit() }),
            textStyle =
                MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = user,
            onValueChange = { user = it },
            label = { Text(stringRes(Res.string.namecoin_rpc_username)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onAny = { commit() }),
            textStyle =
                MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it },
            label = { Text(stringRes(Res.string.namecoin_rpc_password)) },
            singleLine = true,
            visualTransformation =
                if (passVisible) {
                    androidx.compose.ui.text.input.VisualTransformation.None
                } else {
                    androidx.compose.ui.text.input
                        .PasswordVisualTransformation()
                },
            trailingIcon = {
                IconButton(onClick = { passVisible = !passVisible }) {
                    Icon(
                        if (passVisible) MaterialSymbols.VisibilityOff else MaterialSymbols.Visibility,
                        contentDescription = null,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            keyboardOptions =
                KeyboardOptions(
                    imeAction = ImeAction.Done,
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                ),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            textStyle =
                MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
        )

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    commit()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringRes(Res.string.action_save))
            }
            Button(
                onClick = {
                    commit()
                    if (testing) return@Button
                    testing = true
                    lastProbe = null
                    val candidate =
                        config.copy(
                            url = url.trim(),
                            username = user.trim(),
                            password = pass,
                        )
                    scope.launch {
                        try {
                            val probe = onTestCoreRpc(candidate)
                            lastProbe = probe
                            // Offer TOFU when the probe captured a leaf cert and
                            // either: (a) the handshake failed (so the user has
                            // to pin to get further), or (b) the user isn't
                            // pinning yet but the cert isn't already in their
                            // trust store — we surface the option pre-emptively
                            // so they can lock the identity in. Skip when
                            // already pinned to this exact PEM.
                            val pem = probe.serverCertPem
                            val fp = probe.certFingerprint
                            if (
                                pem != null && fp != null && (
                                    probe.tlsHandshakeFailed ||
                                        !candidate.usePinnedTrustStore
                                )
                            ) {
                                val host = OmniboxInput.hostOf(candidate.url) ?: candidate.url
                                pendingPin =
                                    PendingCertPin(
                                        serverHost = host,
                                        fingerprint = fp,
                                        pem = pem,
                                    )
                            }
                        } finally {
                            testing = false
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !testing && url.isNotBlank(),
            ) {
                if (testing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringRes(Res.string.namecoin_rpc_test))
                }
            }
        }

        lastProbe?.let { probe ->
            Spacer(Modifier.height(10.dp))
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            if (probe.success) {
                                Color(0x222E8B57)
                            } else {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                            },
                    ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    if (probe.success) {
                        Text(
                            stringRes(Res.string.namecoin_rpc_connected_ms, probe.elapsedMs),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        val chain = probe.chain ?: "?"
                        val blocks = probe.blocks?.toString() ?: "?"
                        val pct =
                            probe.verificationProgress?.let { percentTwoDecimals(it) }
                                ?: "?"
                        Text(
                            "chain=$chain  height=$blocks  sync=$pct" +
                                (if (probe.initialBlockDownload == true) "  (IBD)" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    } else {
                        Text(
                            stringRes(Res.string.namecoin_rpc_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        SelectionContainer {
                            Text(
                                probe.error.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        if (probe.tlsHandshakeFailed && probe.certFingerprint != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringRes(Res.string.namecoin_rpc_tls_rejected),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    val fp = probe.certFingerprint
                    if (fp != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text =
                                if (config.usePinnedTrustStore) {
                                    stringRes(Res.string.namecoin_rpc_cert_sha256_pinned, fp.take(23))
                                } else {
                                    stringRes(Res.string.namecoin_rpc_cert_sha256, fp.take(23))
                                },
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// ── Fallback policy section ────────────────────────────────────────────

@Composable
private fun FallbacksSection(
    settings: NamecoinSettings,
    onSetFallbackToCustomElectrumx: (Boolean) -> Unit,
    onSetFallbackToDefaultElectrumx: (Boolean) -> Unit,
) {
    Column {
        Text(
            stringRes(Res.string.namecoin_fallback_policy),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringRes(Res.string.namecoin_fallback_policy_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        if (settings.backend == NamecoinBackend.NAMECOIN_CORE_RPC) {
            ToggleRow(
                label = stringRes(Res.string.namecoin_fallback_custom_label),
                description =
                    if (settings.hasCustomServers) {
                        pluralStringRes(Res.plurals.namecoin_fallback_custom_description, settings.customServers.size, settings.customServers.size)
                    } else {
                        stringRes(Res.string.namecoin_fallback_custom_none)
                    },
                checked = settings.fallbackToCustomElectrumx,
                onCheckedChange = onSetFallbackToCustomElectrumx,
                enabled = settings.hasCustomServers,
            )
        }

        ToggleRow(
            label = stringRes(Res.string.namecoin_fallback_default_label),
            description =
                if (settings.backend == NamecoinBackend.ELECTRUMX) {
                    if (settings.hasCustomServers) {
                        stringRes(Res.string.namecoin_fallback_default_custom_failed)
                    } else {
                        stringRes(Res.string.namecoin_fallback_default_already)
                    }
                } else {
                    stringRes(Res.string.namecoin_fallback_default_everything_fails)
                },
            checked = settings.fallbackToDefaultElectrumx,
            onCheckedChange = onSetFallbackToDefaultElectrumx,
            enabled =
                settings.backend == NamecoinBackend.NAMECOIN_CORE_RPC ||
                    settings.hasCustomServers,
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

/** [fraction] as a percentage with exactly two decimals and a `.` mark ("99.50%"), as Core RPC reports it. */
private fun percentTwoDecimals(fraction: Double): String {
    val hundredths = (fraction * 10_000).roundToLong()
    return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}%"
}
