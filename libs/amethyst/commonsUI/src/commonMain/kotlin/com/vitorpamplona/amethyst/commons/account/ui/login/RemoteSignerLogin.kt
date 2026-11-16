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
package com.vitorpamplona.amethyst.commons.account.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.commons.account.ui.AmethystBlue
import com.vitorpamplona.amethyst.commons.account.ui.AmethystPurple
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.add_a_relay
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.copy
import com.vitorpamplona.amethyst.commons.resources.login_with_remote_signer
import com.vitorpamplona.amethyst.commons.resources.relay_url_not_valid
import com.vitorpamplona.amethyst.commons.resources.remote_signer_default_relays
import com.vitorpamplona.amethyst.commons.resources.remote_signer_no_app
import com.vitorpamplona.amethyst.commons.resources.remote_signer_open
import com.vitorpamplona.amethyst.commons.resources.remote_signer_relay_apply
import com.vitorpamplona.amethyst.commons.resources.remote_signer_relay_hint
import com.vitorpamplona.amethyst.commons.resources.remote_signer_relay_label
import com.vitorpamplona.amethyst.commons.resources.remote_signer_scan_title
import com.vitorpamplona.amethyst.commons.resources.remote_signer_waiting
import com.vitorpamplona.amethyst.commons.ui.components.LocalAnimationsEnabled
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppPlatform
import com.vitorpamplona.amethyst.commons.ui.platform.NostrConnectApp
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode.QrCodeDrawer
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.CardSurfaces
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Logs in through a NIP-46 remote signer: opens a `nostrconnect://` code for the signer to scan.
 * (A `bunker://` address the signer hands out goes in the key field instead.)
 */
@Composable
fun RemoteSignerLoginButton(loginViewModel: LoginViewModel) {
    LoggedOffSecondaryButton(
        text = stringRes(Res.string.login_with_remote_signer),
        // A key locked in another device. Not a QR: the key field's QR button scans one.
        symbol = MaterialSymbols.PhonelinkLock,
        // Gated by the terms like every other way in.
        enabled = loginViewModel.acceptedTerms && !loginViewModel.processingLogin,
        onClick = loginViewModel::loginWithNostrConnect,
    )

    if (loginViewModel.nostrConnectPending) {
        NostrConnectDialog(loginViewModel)
    }
}

/**
 * The code, the signal that Amethyst is listening for the signer, and the ways to hand the code
 * over (scan, copy, open the signer). The relays it names sit at the bottom as chips.
 */
@Composable
private fun NostrConnectDialog(loginViewModel: LoginViewModel) {
    val uri = loginViewModel.nostrConnectUri
    val platform = LocalAppPlatform.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    // Set when no app took the address: the button says so instead of doing nothing.
    var noSignerApp by remember(uri) { mutableStateOf(false) }

    // Several signer apps take the address: the user picks one here rather than in the system's
    // chooser, which on Android 16 restarts the phone when a target is picked.
    var signerApps by remember(uri) { mutableStateOf<List<NostrConnectApp>>(emptyList()) }

    fun openIn(
        uri: String,
        appId: String?,
    ) {
        // Off the UI thread: the desktop's browse() can block for seconds.
        scope.launch {
            noSignerApp = !withContext(Dispatchers.IO) { platform.openNostrConnect(uri, appId) }
        }
    }

    Dialog(
        onDismissRequest = loginViewModel::cancelNostrConnect,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            // The code gives up height first on a short window (a small phone, an open keyboard), so
            // the actions and relays below it stay in view; it stays big enough to scan.
            val qrSize = (maxHeight - DialogChromeHeight).coerceIn(QrMinSize, QrMaxSize)
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = CardSurfaces.color,
                border = CardSurfaces.border,
                modifier = Modifier.padding(16.dp).widthIn(max = 400.dp),
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringRes(Res.string.remote_signer_scan_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = loginViewModel::cancelNostrConnect) {
                            Icon(MaterialSymbols.Close, contentDescription = stringRes(Res.string.cancel))
                        }
                    }

                    ListeningQrCode(uri, qrSize)

                    WaitingPill()

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        FilledTonalButton(
                            onClick = { uri?.let { scope.launch { clipboard.setText(it) } } },
                            enabled = uri != null,
                            contentPadding = ActionPadding,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(MaterialSymbols.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringRes(Res.string.copy), maxLines = 1)
                        }
                        Box(Modifier.weight(1f)) {
                            FilledTonalButton(
                                onClick = {
                                    uri?.let {
                                        scope.launch {
                                            val apps = withContext(Dispatchers.IO) { platform.nostrConnectApps(it) }
                                            if (apps.size > 1) signerApps = apps else openIn(it, apps.singleOrNull()?.id)
                                        }
                                    }
                                },
                                enabled = uri != null && !noSignerApp,
                                contentPadding = ActionPadding,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(
                                    if (noSignerApp) MaterialSymbols.Warning else MaterialSymbols.OpenInBrowser,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringRes(if (noSignerApp) Res.string.remote_signer_no_app else Res.string.remote_signer_open), maxLines = 1)
                            }
                            DropdownMenu(expanded = signerApps.isNotEmpty(), onDismissRequest = { signerApps = emptyList() }) {
                                signerApps.forEach { app ->
                                    DropdownMenuItem(
                                        text = { Text(app.label) },
                                        onClick = {
                                            signerApps = emptyList()
                                            uri?.let { openIn(it, app.id) }
                                        },
                                    )
                                }
                            }
                        }
                    }

                    NostrConnectRelays(loginViewModel)
                }
            }
        }
    }
}

/** Everything in the dialog but the code, at its tallest on a 360 dp phone: what the code leaves room for. */
private val DialogChromeHeight = 384.dp
private val QrMinSize = 176.dp
private val QrMaxSize = 264.dp

private val ActionPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)

private val SweepColors = listOf(AmethystPurple.copy(alpha = 0.15f), AmethystPurple, AmethystBlue, AmethystPurple.copy(alpha = 0.15f))

/**
 * The code in a white frame (scanners need the contrast, dark theme or not) ringed by the logo's
 * colors, which sweep around it while Amethyst waits for the signer.
 */
@Composable
private fun ListeningQrCode(
    uri: String?,
    codeSize: Dp,
) {
    val sweep = rememberSweepAngle()

    Box(
        modifier =
            Modifier
                .size(codeSize)
                .clip(RoundedCornerShape(32.dp))
                .drawWithCache {
                    // Built once per size; each frame of the sweep only rotates it.
                    val ring = Brush.sweepGradient(SweepColors)
                    val radius = size.maxDimension
                    onDrawBehind {
                        rotate(sweep?.value ?: 0f) { drawCircle(brush = ring, radius = radius) }
                    }
                }.padding(4.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White)
                .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (uri != null) {
            QrCodeDrawer(uri, Modifier.fillMaxSize())
        } else {
            CircularProgressIndicator(color = AmethystPurple)
        }
    }
}

/** One turn every four seconds; null, and a still ring, where the user turned animations off. */
@Composable
private fun rememberSweepAngle(): State<Float>? {
    if (!LocalAnimationsEnabled.current) return null
    return rememberInfiniteTransition(label = "nostrconnect-sweep").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 4000, easing = LinearEasing)),
        label = "nostrconnect-sweep-angle",
    )
}

@Composable
private fun WaitingPill() {
    val pulse =
        if (LocalAnimationsEnabled.current) {
            rememberInfiniteTransition(label = "nostrconnect-pulse").animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
                label = "nostrconnect-pulse-alpha",
            )
        } else {
            null
        }
    val dotColor = MaterialTheme.colorScheme.primary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Box(
            Modifier.size(8.dp).drawBehind { drawCircle(dotColor.copy(alpha = pulse?.value ?: 1f)) },
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringRes(Res.string.remote_signer_waiting),
            // One line on a 360 dp phone.
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/**
 * The relays the code names, as chips, then a chip to name one of the account's inbox relays
 * instead: a signer that can't reach the defaults never sees the request.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NostrConnectRelays(loginViewModel: LoginViewModel) {
    var editing by remember { mutableStateOf(false) }
    var relay by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }

    val apply = {
        if (loginViewModel.useNostrConnectRelay(relay)) {
            relay = ""
            invalid = false
            editing = false
        } else {
            invalid = true
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            loginViewModel.nostrConnectRelays.forEach { RelayPill(it) }

            // Stays while the field is open, as its way back out: a relay field opened by mistake
            // otherwise had no exit but closing the whole dialog.
            Pill(
                onClick = {
                    editing = !editing
                    relay = ""
                    invalid = false
                },
            ) {
                Icon(
                    if (editing) MaterialSymbols.Close else MaterialSymbols.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(stringRes(Res.string.add_a_relay), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            if (!loginViewModel.usesDefaultNostrConnectRelays) {
                Pill(onClick = loginViewModel::useDefaultNostrConnectRelays) {
                    Icon(MaterialSymbols.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(stringRes(Res.string.remote_signer_default_relays), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        AnimatedVisibility(editing) {
            OutlinedTextField(
                value = relay,
                onValueChange = {
                    relay = it
                    invalid = false
                },
                label = { Text(stringRes(Res.string.remote_signer_relay_label)) },
                placeholder = { Text("wss://") },
                singleLine = true,
                isError = invalid,
                shape = LoggedOffShape,
                supportingText = {
                    Text(stringRes(if (invalid) Res.string.relay_url_not_valid else Res.string.remote_signer_relay_hint))
                },
                trailingIcon = {
                    IconButton(onClick = apply, enabled = relay.isNotBlank()) {
                        Icon(MaterialSymbols.Check, contentDescription = stringRes(Res.string.remote_signer_relay_apply))
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                // Like the check button, which is off for an empty field.
                keyboardActions = KeyboardActions(onDone = { if (relay.isNotBlank()) apply() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A relay the code names: its host, after a dot that reads as "live". */
@Composable
private fun RelayPill(url: String) {
    val dotColor = MaterialTheme.colorScheme.primary
    Pill {
        Box(Modifier.size(6.dp).drawBehind { drawCircle(dotColor) })
        Text(
            text = url.removePrefix("wss://").removePrefix("ws://").removeSuffix("/"),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** The outlined capsule the relays and their actions share, so the row lines up. */
@Composable
private fun Pill(
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            Modifier
                .height(30.dp)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(horizontal = 10.dp),
    ) {
        content()
    }
}
