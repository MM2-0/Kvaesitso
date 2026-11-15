package de.mm20.launcher2.ui.network

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.network.speedtest.LibreSpeedServer
import de.mm20.launcher2.network.speedtest.LibreSpeedTestClient
import de.mm20.launcher2.network.speedtest.NetworkSpeedTestPhase
import de.mm20.launcher2.network.speedtest.NetworkSpeedTestResult
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.text.NumberFormat

@Serializable
data object NetworkSpeedTestRoute : NavKey

@Composable
fun NetworkSpeedTestScreen() {
    val client = remember { LibreSpeedTestClient() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var customUrl by rememberSaveable { mutableStateOf("") }
    var servers by remember { mutableStateOf(emptyList<LibreSpeedServer>()) }
    var selectedServer by remember { mutableStateOf<LibreSpeedServer?>(null) }
    var showServerPicker by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<NetworkSpeedTestResult?>(null) }
    var phase by remember { mutableStateOf<NetworkSpeedTestPhase?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadingServers by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val busy = loadingServers || phase != null
    val canStart = !busy && (customUrl.isNotBlank() || selectedServer != null)
    val numberFormat = remember { NumberFormat.getNumberInstance() }

    PreferenceScreen(title = {
        Text(stringResource(I18nR.string.nsp_title), Modifier.padding(horizontal = 16.dp))
    }) {
        item {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                painterResource(de.mm20.launcher2.base.R.drawable.speed_24px),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                            )
                            Text(stringResource(I18nR.string.nsp_intro), style = MaterialTheme.typography.titleSmall)
                        }
                        Text(
                            stringResource(I18nR.string.nsp_privacy),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                OutlinedTextField(
                    value = customUrl,
                    onValueChange = {
                        customUrl = it
                        result = null
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(I18nR.string.nsp_server_url)) },
                    placeholder = { Text("https://speed.example/backend/") },
                    singleLine = true,
                    enabled = !busy,
                )

                OutlinedButton(
                    onClick = {
                        loadingServers = true
                        error = null
                        job = scope.launch {
                            try {
                                servers = client.loadPublicServers()
                                if (servers.isEmpty()) error = "empty"
                                else showServerPicker = true
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                error = "load"
                            } finally {
                                loadingServers = false
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (loadingServers) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(painterResource(de.mm20.launcher2.base.R.drawable.language_24px), null)
                    }
                    Text(
                        stringResource(
                            if (selectedServer == null) I18nR.string.nsp_load_servers else I18nR.string.nsp_select_server,
                        ),
                        Modifier.padding(start = 8.dp),
                    )
                }

                selectedServer?.takeIf { customUrl.isBlank() }?.let { server ->
                    Text(
                        stringResource(I18nR.string.nsp_selected_server, server.name),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Button(
                    onClick = {
                        val server = if (customUrl.isNotBlank()) {
                            LibreSpeedTestClient.customServer(customUrl)
                        } else {
                            selectedServer
                        }
                        if (server == null) {
                            error = if (customUrl.isNotBlank()) "invalid" else "missing"
                            return@Button
                        }
                        result = null
                        error = null
                        job = scope.launch {
                            try {
                                result = client.run(server) { phase = it }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                error = "test"
                            } finally {
                                phase = null
                            }
                        }
                    },
                    enabled = canStart,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Icon(painterResource(de.mm20.launcher2.base.R.drawable.speed_24px), null)
                    Text(stringResource(I18nR.string.nsp_run), Modifier.padding(start = 8.dp))
                }

                phase?.let { runningPhase ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(phaseString(runningPhase)), Modifier.weight(1f))
                        TextButton(onClick = { job?.cancel() }) {
                            Text(stringResource(I18nR.string.nsp_cancel))
                        }
                    }
                }

                error?.let { reason ->
                    val message = when (reason) {
                        "invalid" -> I18nR.string.nsp_invalid_url
                        "missing" -> I18nR.string.nsp_invalid_url
                        else -> I18nR.string.nsp_error
                    }
                    Text(
                        stringResource(message),
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                result?.let { testResult ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                testResult.serverName,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                SpeedMetric(Modifier.weight(1f), stringResource(I18nR.string.nsp_ping), "${testResult.pingMs} ms")
                                SpeedMetric(Modifier.weight(1f), stringResource(I18nR.string.nsp_jitter), "${testResult.jitterMs} ms")
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                SpeedMetric(Modifier.weight(1f), stringResource(I18nR.string.nsp_download), "${numberFormat.format(testResult.downloadMbps)} Mbps")
                                SpeedMetric(Modifier.weight(1f), stringResource(I18nR.string.nsp_upload), "${numberFormat.format(testResult.uploadMbps)} Mbps")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showServerPicker) {
        AlertDialog(
            onDismissRequest = { showServerPicker = false },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.nsp_select_server))  } },
            text = {
                LazyColumn {
                    items(servers, key = { it.pingUrl.toString() }) { server ->
                        TextButton(
                            onClick = {
                                selectedServer = server
                                customUrl = ""
                                result = null
                                error = null
                                showServerPicker = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(server.name, Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showServerPicker = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun SpeedMetric(modifier: Modifier, label: String, value: String) {
    Column(modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

private fun phaseString(phase: NetworkSpeedTestPhase): Int = when (phase) {
    NetworkSpeedTestPhase.PING -> I18nR.string.nsp_ping
    NetworkSpeedTestPhase.DOWNLOAD -> I18nR.string.nsp_download
    NetworkSpeedTestPhase.UPLOAD -> I18nR.string.nsp_upload
}
