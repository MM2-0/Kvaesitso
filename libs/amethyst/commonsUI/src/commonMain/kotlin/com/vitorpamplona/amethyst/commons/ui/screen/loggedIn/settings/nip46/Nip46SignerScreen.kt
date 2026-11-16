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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.nip46

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46PermissionAuthorizer
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.nip46Signer.Nip46ActivityEntry
import com.vitorpamplona.amethyst.commons.model.nip46Signer.Nip46SignerState
import com.vitorpamplona.amethyst.commons.nip46RemoteSigner.ui.Nip46ActivityCard
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_activity_title
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_background_hint
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_cancel
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_connect_button
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_connect_declined
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_connect_failed
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_connect_hint
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_connect_invalid
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_connect_label
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_connect_link_confirm
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_connect_link_repair
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_connect_no_relays
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_connect_unnamed_app
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_connected_count
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_connected_named
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_connected_ok
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_copied
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_copy
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_explainer
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_hero_title
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_live
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_manage_apps
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_paste_link
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_readonly
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_regenerate
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_regenerated
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_relays_all_live
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_relays_some_down
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_rotate_confirm_button
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_rotate_confirm_message
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_rotate_confirm_title
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_scan_caption
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_scan_connect
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_status_no_relays
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_title
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_turn_on
import com.vitorpamplona.amethyst.commons.ui.components.rememberLongNotice
import com.vitorpamplona.amethyst.commons.ui.components.rememberShortNotice
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode.QrCodeDrawer
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode.SimpleQrCodeScanner
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.util.toShortDisplay
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectURI
import kotlinx.coroutines.launch

private val LiveGreen = Color(0xFF3DDC84)

@Composable
fun Nip46SignerScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
    connectUri: String? = null,
) {
    val account = accountViewModel.account
    val signer = account.nip46Signer
    val scope = rememberCoroutineScope()
    val shortNotice = rememberShortNotice()
    val longNotice = rememberLongNotice()
    val clipboard = LocalClipboard.current
    val copiedStr = stringRes(Res.string.nip46_signer_copied)
    val regeneratedStr = stringRes(Res.string.nip46_signer_regenerated)

    val enabled by account.settings.nip46SignerEnabled.collectAsStateWithLifecycle()
    val secret by account.settings.nip46BunkerSecret.collectAsStateWithLifecycle()
    val relays by signer.listeningRelays.collectAsStateWithLifecycle()
    val connectedRelays by account.client.connectedRelaysFlow().collectAsStateWithLifecycle()
    val liveRelayCount = remember(relays, connectedRelays) { relays.count { it in connectedRelays } }
    val activity by signer.activityLog.entries.collectAsStateWithLifecycle()
    val writeable = remember { account.signer.isWriteable() }

    var connectedCount by remember { mutableIntStateOf(0) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var scanning by remember { mutableStateOf(false) }
    var confirmRotate by remember { mutableStateOf(false) }

    var bunkerUri by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(enabled, secret, relays) {
        bunkerUri = if (enabled && writeable && relays.isNotEmpty()) signer.bunkerUri() else null
    }
    LaunchedEffect(enabled, refreshKey) {
        connectedCount =
            account.signerPermissionLedger.store
                .allPolicies()
                .keys
                .count { Nip46PermissionAuthorizer.belongsTo(it, account.signer.pubKey) }
    }

    fun onConnect(uri: String) {
        scope.launch {
            val result = signer.connectViaNostrConnect(uri.trim())
            longNotice.show(describe(result))
            refreshKey++
        }
    }

    // Opened from a nostrconnect:// link: ask before pairing (which also turns the signer on). Any web
    // page can fire that link, and pairing an app approved before skips the consent prompt, so pairing
    // straight away would let a link rewrite a paired app's relays and re-enable a signer turned off.
    var linkOffer by rememberSaveable(connectUri) { mutableStateOf(connectUri?.takeIf { it.isNotBlank() }) }

    if (scanning) {
        SimpleQrCodeScanner { contents ->
            scanning = false
            contents?.let { onConnect(it) }
        }
    }

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.nip46_signer_title), nav) },
        bottomBar = {
            AppBottomBar(Route.Nip46Signer(), nav, accountViewModel) { route ->
                if (route != Route.Nip46Signer()) nav.navBottomBar(route)
            }
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (!writeable) {
                ReadOnlyNotice()
                return@Column
            }

            if (enabled) {
                LiveStatusCard(
                    relayCount = relays.size,
                    liveRelayCount = liveRelayCount,
                    connectedCount = connectedCount,
                    onToggleOff = { signer.setEnabled(false) },
                )

                if (relays.isEmpty()) {
                    WarningCard(stringRes(Res.string.nip46_signer_status_no_relays))
                }

                bunkerUri?.let { uri ->
                    QrHeroCard(
                        uri = uri,
                        onCopy = {
                            scope.launch { clipboard.setText(uri) }
                            shortNotice.show(copiedStr)
                        },
                        onRegenerate = { confirmRotate = true },
                    )
                }
            } else {
                DisabledHero(onEnable = { signer.setEnabled(true) })
            }

            // The Connect section stays reachable even while off: scanning an app's nostrconnect://
            // code pairs it and enables the signer as part of connecting (no separate "enable" step).
            ConnectSection(
                onScan = { scanning = true },
                onPaste = { onConnect(it) },
            )

            if (enabled || connectedCount > 0) {
                ConnectedAppsRow(
                    count = connectedCount,
                    onClick = { nav.nav(Route.Nip46ConnectedApps) },
                )
            }

            if (enabled && activity.isNotEmpty()) {
                ActivitySection(activity)
            }

            if (enabled) {
                Text(
                    stringRes(Res.string.nip46_signer_background_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    linkOffer?.let { uri ->
        val offer = remember(uri) { NostrConnectURI.parseNostrConnect(uri.trim()) }
        if (offer == null) {
            // Not a valid offer: let the connect path report it.
            LaunchedEffect(uri) {
                linkOffer = null
                onConnect(uri)
            }
        } else {
            // A link names itself, so the name alone could be any paired app's: show the key, and say
            // when this one is already paired (that pairing would be rewritten, without the consent prompt).
            val alreadyPaired by produceState(false, offer.clientPubKey) { value = signer.isPaired(offer.clientPubKey) }
            ConnectFromLinkDialog(
                appName = offer.name?.ifBlank { null } ?: stringRes(Res.string.nip46_signer_connect_unnamed_app),
                appKey = NPub.create(offer.clientPubKey).toShortDisplay(),
                alreadyPaired = alreadyPaired,
                onConfirm = {
                    linkOffer = null
                    onConnect(uri)
                },
                onDismiss = { linkOffer = null },
            )
        }
    }

    if (confirmRotate) {
        RotateAddressDialog(
            onConfirm = {
                confirmRotate = false
                signer.rotateAddress()
                shortNotice.show(regeneratedStr)
            },
            onDismiss = { confirmRotate = false },
        )
    }
}

@Composable
private fun ConnectFromLinkDialog(
    appName: String,
    appKey: String,
    alreadyPaired: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.nip46_signer_connect_label)) },
        text = {
            if (alreadyPaired) {
                Text(stringRes(Res.string.nip46_signer_connect_link_repair, appName, appKey))
            } else {
                Text(stringRes(Res.string.nip46_signer_connect_link_confirm, appName, appKey))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringRes(Res.string.nip46_signer_connect_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(Res.string.nip46_signer_cancel))
            }
        },
    )
}

@Composable
private fun RotateAddressDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(MaterialSymbols.Refresh, contentDescription = null, modifier = Modifier.size(24.dp)) },
        title = { Text(stringRes(Res.string.nip46_signer_rotate_confirm_title)) },
        text = { Text(stringRes(Res.string.nip46_signer_rotate_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringRes(Res.string.nip46_signer_rotate_confirm_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(Res.string.nip46_signer_cancel))
            }
        },
    )
}

// ----------------------------------------------------------------------------

@Composable
private fun DisabledHero(onEnable: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                MaterialSymbols.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(52.dp),
            )
        }
        Text(
            stringRes(Res.string.nip46_signer_hero_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            stringRes(Res.string.nip46_signer_explainer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onEnable,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(MaterialSymbols.Key, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringRes(Res.string.nip46_signer_turn_on), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun LiveStatusCard(
    relayCount: Int,
    liveRelayCount: Int,
    connectedCount: Int,
    onToggleOff: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LiveDot()
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    stringRes(Res.string.nip46_signer_live),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                val relayStatus =
                    if (liveRelayCount < relayCount) {
                        stringRes(Res.string.nip46_signer_relays_some_down, liveRelayCount, relayCount)
                    } else {
                        pluralStringRes(Res.plurals.nip46_signer_relays_all_live, relayCount, relayCount)
                    }
                Text(
                    buildString {
                        append(pluralStringRes(Res.plurals.nip46_signer_connected_count, connectedCount, connectedCount))
                        append(" · ")
                        append(relayStatus)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
            Switch(checked = true, onCheckedChange = { onToggleOff() })
        }
    }
}

@Composable
private fun LiveDot() {
    val transition = rememberInfiniteTransition(label = "live")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(18.dp)) {
        Canvas(Modifier.size(18.dp)) { drawCircle(color = LiveGreen, alpha = alpha * 0.35f) }
        Canvas(Modifier.size(9.dp)) { drawCircle(color = LiveGreen) }
    }
}

@Composable
private fun QrHeroCard(
    uri: String,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
            ) {
                QrCodeDrawer(
                    contents = uri,
                    modifier =
                        Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                            .aspectRatio(1f),
                )
            }
            Text(
                stringRes(Res.string.nip46_signer_scan_caption),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(onClick = onCopy, modifier = Modifier.weight(1f)) {
                    Icon(MaterialSymbols.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringRes(Res.string.nip46_signer_copy))
                }
                FilledTonalButton(onClick = onRegenerate, modifier = Modifier.weight(1f)) {
                    Icon(MaterialSymbols.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringRes(Res.string.nip46_signer_regenerate))
                }
            }
        }
    }
}

@Composable
private fun ConnectSection(
    onScan: () -> Unit,
    onPaste: (String) -> Unit,
) {
    var showPaste by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringRes(Res.string.nip46_signer_connect_label),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Button(
            onClick = onScan,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(MaterialSymbols.CameraAlt, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringRes(Res.string.nip46_signer_scan_connect), fontWeight = FontWeight.SemiBold)
        }

        TextButton(onClick = { showPaste = !showPaste }, modifier = Modifier.fillMaxWidth()) {
            Text(stringRes(Res.string.nip46_signer_paste_link))
        }

        AnimatedVisibility(visible = showPaste) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    placeholder = { Text(stringRes(Res.string.nip46_signer_connect_hint)) },
                )
                Button(
                    onClick = {
                        if (input.isNotBlank()) {
                            onPaste(input)
                            input = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = input.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                ) {
                    Text(stringRes(Res.string.nip46_signer_connect_button))
                }
            }
        }
    }
}

@Composable
private fun ConnectedAppsRow(
    count: Int,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                MaterialSymbols.Apps,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringRes(Res.string.nip46_signer_manage_apps),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    pluralStringRes(Res.plurals.nip46_signer_connected_count, count, count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                MaterialSymbols.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun ActivitySection(entries: List<Nip46ActivityEntry>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringRes(Res.string.nip46_signer_activity_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Nip46ActivityCard(entries)
    }
}

@Composable
private fun WarningCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(
            message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun ReadOnlyNotice() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(MaterialSymbols.Key, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
            Text(
                stringRes(Res.string.nip46_signer_readonly),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private suspend fun describe(result: Nip46SignerState.ConnectResult): String =
    when (result) {
        is Nip46SignerState.ConnectResult.Connected ->
            result.name?.let { loadStringRes(Res.string.nip46_signer_connected_named, it) }
                ?: loadStringRes(Res.string.nip46_signer_connected_ok)
        Nip46SignerState.ConnectResult.InvalidUri -> loadStringRes(Res.string.nip46_signer_connect_invalid)
        Nip46SignerState.ConnectResult.NoRelays -> loadStringRes(Res.string.nip46_signer_connect_no_relays)
        Nip46SignerState.ConnectResult.NotWriteable -> loadStringRes(Res.string.nip46_signer_readonly)
        Nip46SignerState.ConnectResult.Declined -> loadStringRes(Res.string.nip46_signer_connect_declined)
        is Nip46SignerState.ConnectResult.Failed -> loadStringRes(Res.string.nip46_signer_connect_failed, result.reason)
    }
