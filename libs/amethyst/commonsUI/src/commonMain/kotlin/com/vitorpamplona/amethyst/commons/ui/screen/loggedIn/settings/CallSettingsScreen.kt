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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.CallTurnServer
import com.vitorpamplona.amethyst.commons.model.CallVideoResolution
import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedType
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.call_settings
import com.vitorpamplona.amethyst.commons.resources.call_settings_add_turn
import com.vitorpamplona.amethyst.commons.resources.call_settings_bitrate_default
import com.vitorpamplona.amethyst.commons.resources.call_settings_custom_turn
import com.vitorpamplona.amethyst.commons.resources.call_settings_default_servers
import com.vitorpamplona.amethyst.commons.resources.call_settings_enable_calls
import com.vitorpamplona.amethyst.commons.resources.call_settings_enable_calls_description
import com.vitorpamplona.amethyst.commons.resources.call_settings_max_bitrate
import com.vitorpamplona.amethyst.commons.resources.call_settings_no_custom_turn
import com.vitorpamplona.amethyst.commons.resources.call_settings_off_with_nip17
import com.vitorpamplona.amethyst.commons.resources.call_settings_remove_turn
import com.vitorpamplona.amethyst.commons.resources.call_settings_turn_credential
import com.vitorpamplona.amethyst.commons.resources.call_settings_turn_description
import com.vitorpamplona.amethyst.commons.resources.call_settings_turn_servers
import com.vitorpamplona.amethyst.commons.resources.call_settings_turn_url
import com.vitorpamplona.amethyst.commons.resources.call_settings_turn_username
import com.vitorpamplona.amethyst.commons.resources.call_settings_video_quality
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun CallSettingsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(id = Res.string.call_settings), nav)
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            CallSettingsContent(accountViewModel, nav)
        }
    }
}

@Composable
private fun CallSettingsContent(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val settings = accountViewModel.account.settings
    val callsEnabled by settings.callsEnabled.collectAsState()
    val chatFeeds by accountViewModel.account.chatFeedToggles.applied
        .collectAsStateWithLifecycle()

    // Calls signal over NIP-17's gift-wrap inbox (ChatFeedToggles.isCallingActive): with NIP-17 off
    // they stay off whatever the switch below says, so say why and where to turn it back on.
    if (ChatFeedType.NIP17 !in chatFeeds) {
        Nip17OffNotice(
            text = stringRes(Res.string.call_settings_off_with_nip17),
            onOpenMessagesSettings = { nav.nav(Route.MessagesSettings) },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
    }

    EnableCallsSection(
        enabled = callsEnabled,
        onEnabledChanged = { settings.changeCallsEnabled(it) },
    )

    if (!callsEnabled) {
        // When calls are disabled the remaining settings (video quality,
        // TURN servers, etc.) have no effect, so hide them to keep the
        // screen focused on the single meaningful toggle.
        Spacer(modifier = Modifier.height(16.dp))
        return
    }

    HorizontalDivider(thickness = 4.dp, modifier = Modifier.padding(vertical = 8.dp))

    SectionHeader(stringRes(Res.string.call_settings_video_quality))
    VideoResolutionSection(
        currentResolution = settings.callVideoResolution,
        onResolutionChanged = { settings.changeCallVideoResolution(it) },
    )

    Spacer(modifier = Modifier.height(8.dp))

    BitrateSection(
        currentBitrate = settings.callMaxBitrateBps,
        onBitrateChanged = { settings.changeCallMaxBitrateBps(it) },
    )

    HorizontalDivider(thickness = 4.dp, modifier = Modifier.padding(vertical = 8.dp))

    SectionHeader(stringRes(Res.string.call_settings_turn_servers))
    Text(
        text = stringRes(Res.string.call_settings_turn_description),
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
    )
    DefaultTurnServersSection()

    Spacer(modifier = Modifier.height(8.dp))

    CustomTurnServersSection(
        servers = settings.callTurnServers,
        onServersChanged = { settings.changeCallTurnServers(it) },
    )

    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun EnableCallsSection(
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringRes(Res.string.call_settings_enable_calls),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringRes(Res.string.call_settings_enable_calls_description),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChanged,
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun VideoResolutionSection(
    currentResolution: CallVideoResolution,
    onResolutionChanged: (CallVideoResolution) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        CallVideoResolution.entries.forEach { resolution ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                RadioButton(
                    selected = currentResolution == resolution,
                    onClick = { onResolutionChanged(resolution) },
                )
                Text(
                    text = resolution.label,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(start = 8.dp),
                )
                Text(
                    text = "${resolution.width}x${resolution.height} @ ${resolution.fps}fps",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun BitrateSection(
    currentBitrate: Int,
    onBitrateChanged: (Int) -> Unit,
) {
    val bitrateOptions =
        listOf(
            750_000 to "750 kbps",
            1_500_000 to stringRes(Res.string.call_settings_bitrate_default),
            3_000_000 to "3 Mbps",
        )

    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Text(
            text = stringRes(Res.string.call_settings_max_bitrate),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        bitrateOptions.forEach { (bitrate, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                RadioButton(
                    selected = currentBitrate == bitrate,
                    onClick = { onBitrateChanged(bitrate) },
                )
                Text(
                    text = label,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun DefaultTurnServersSection() {
    val defaults =
        listOf(
            "stun:stun.l.google.com:19302",
            "stun:stun1.l.google.com:19302",
            "stun:stun.cloudflare.com:3478",
            "turn:openrelay.metered.ca:80",
            "turn:openrelay.metered.ca:443",
            "turn:openrelay.metered.ca:443?transport=tcp",
        )
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
        Text(
            text = stringRes(Res.string.call_settings_default_servers),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        defaults.forEach { server ->
            Text(
                text = server,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun CustomTurnServersSection(
    servers: List<CallTurnServer>,
    onServersChanged: (List<CallTurnServer>) -> Unit,
) {
    val editableServers = remember(servers) { mutableStateListOf(*servers.toTypedArray()) }
    var showAddForm by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Text(
            text = stringRes(Res.string.call_settings_custom_turn),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        editableServers.forEachIndexed { index, server ->
            TurnServerRow(
                server = server,
                onDelete = {
                    editableServers.removeAt(index)
                    onServersChanged(editableServers.toList())
                },
            )
            if (index < editableServers.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
        }

        if (editableServers.isEmpty() && !showAddForm) {
            Text(
                text = stringRes(Res.string.call_settings_no_custom_turn),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (showAddForm) {
            AddTurnServerForm(
                onAdd = { server ->
                    editableServers.add(server)
                    onServersChanged(editableServers.toList())
                    showAddForm = false
                },
                onCancel = { showAddForm = false },
            )
        } else {
            Button(
                onClick = { showAddForm = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(MaterialSymbols.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringRes(Res.string.call_settings_add_turn))
            }
        }
    }
}

@Composable
private fun TurnServerRow(
    server: CallTurnServer,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = server.url, fontSize = 14.sp)
            Text(
                text = "${server.username} / ****",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                MaterialSymbols.Delete,
                contentDescription = stringRes(Res.string.call_settings_remove_turn),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun AddTurnServerForm(
    onAdd: (CallTurnServer) -> Unit,
    onCancel: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var credential by remember { mutableStateOf("") }

    Column {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text(stringRes(Res.string.call_settings_turn_url)) },
            placeholder = { Text("turn:your-server.com:443") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text(stringRes(Res.string.call_settings_turn_username)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = credential,
            onValueChange = { credential = it },
            label = { Text(stringRes(Res.string.call_settings_turn_credential)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.textButtonColors(),
            ) {
                Text(stringRes(Res.string.cancel))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (url.isNotBlank()) {
                        onAdd(CallTurnServer(url.trim(), username.trim(), credential.trim()))
                    }
                },
                enabled = url.isNotBlank(),
            ) {
                Text(stringRes(Res.string.call_settings_add_turn))
            }
        }
    }
}
