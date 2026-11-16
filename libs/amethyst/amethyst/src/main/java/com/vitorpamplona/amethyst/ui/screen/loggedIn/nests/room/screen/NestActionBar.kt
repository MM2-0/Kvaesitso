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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.screen

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.error_dialog_zap_error
import com.vitorpamplona.amethyst.commons.resources.nest_audio_failed
import com.vitorpamplona.amethyst.commons.resources.nest_broadcast_connecting
import com.vitorpamplona.amethyst.commons.resources.nest_broadcast_failed
import com.vitorpamplona.amethyst.commons.resources.nest_connect
import com.vitorpamplona.amethyst.commons.resources.nest_connecting_handshake
import com.vitorpamplona.amethyst.commons.resources.nest_connecting_resolving
import com.vitorpamplona.amethyst.commons.resources.nest_connecting_transport
import com.vitorpamplona.amethyst.commons.resources.nest_leave
import com.vitorpamplona.amethyst.commons.resources.nest_leave_stage
import com.vitorpamplona.amethyst.commons.resources.nest_lower_hand
import com.vitorpamplona.amethyst.commons.resources.nest_mic_mute
import com.vitorpamplona.amethyst.commons.resources.nest_mic_unmute
import com.vitorpamplona.amethyst.commons.resources.nest_mute_failed
import com.vitorpamplona.amethyst.commons.resources.nest_open_settings
import com.vitorpamplona.amethyst.commons.resources.nest_raise_hand
import com.vitorpamplona.amethyst.commons.resources.nest_reactions_button
import com.vitorpamplona.amethyst.commons.resources.nest_reconnecting
import com.vitorpamplona.amethyst.commons.resources.nest_talk
import com.vitorpamplona.amethyst.commons.resources.no_wallet_found
import com.vitorpamplona.amethyst.commons.resources.zap_description
import com.vitorpamplona.amethyst.commons.ui.components.toasts.multiline.UserBasedErrorMessage
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ZapAmountChoicePopup
import com.vitorpamplona.amethyst.commons.ui.note.ZapCustomDialog
import com.vitorpamplona.amethyst.commons.ui.note.zapClick
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nests.room.reactions.RoomReactionPopup
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet.navigateToReloadMint
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.BroadcastUiState
import com.vitorpamplona.amethyst.commons.viewmodels.ConnectionUiState
import com.vitorpamplona.amethyst.commons.viewmodels.NestUiState
import com.vitorpamplona.amethyst.commons.viewmodels.NestViewModel
import com.vitorpamplona.amethyst.ui.note.payViaIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Sticky bottom action bar for the room screen.
 *
 * Layout: `[ start cluster ] · · · [ end cluster ]` with an optional
 * red status strip above for connection / broadcast / mute failures.
 *
 * Start cluster — driven by connection state only:
 *
 * | State                              | Start cluster contents |
 * |------------------------------------|------------------------|
 * | Idle / Closed / Failed connection  | `[Connect]`            |
 * | Connecting                         | status chip            |
 * | Reconnecting                       | status chip            |
 * | Connected                          | empty                  |
 *
 * On-stage controls (Talk / MicMute / Leave the Stage) live in
 * [StageControlsBar], attached to the bottom of the Stage card.
 *
 * End cluster: hand-raise (audience + connected only), react, leave room.
 */
@Composable
internal fun NestActionBar(
    viewModel: NestViewModel,
    ui: NestUiState,
    isOnStage: Boolean,
    handRaised: Boolean,
    onHandRaisedChange: (Boolean) -> Unit,
    roomNote: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
    onLeave: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ActionBarStatusStrip(ui = ui)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(modifier = Modifier.weight(1f, fill = true)) {
                    StartCluster(viewModel = viewModel, ui = ui)
                }
                EndCluster(
                    isOnStage = isOnStage,
                    isConnected = ui.connection is ConnectionUiState.Connected,
                    handRaised = handRaised,
                    onHandRaisedChange = onHandRaisedChange,
                    roomNote = roomNote,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    onLeave = onLeave,
                )
            }
        }
    }
}

/**
 * Controls strip that attaches under the Stage card. Contains
 * everything a speaker needs while on stage: Talk / MicMute / Retry +
 * Leave the Stage. Renders nothing when the local user isn't on stage.
 *
 * Visibility uses two signals AND'd together:
 *   - [isOnStage] — derived from `participantGrid.onStage` (kind-30312
 *     role + presence `onstage` flag); flips on a real promote/demote
 *     after the round-trip lands. Connection blips, broadcast state
 *     churn, mute toggles, and permission flows do NOT change it.
 *   - [ui.onStageNow] — the LOCAL intent flag. Flips synchronously on
 *     `setOnStage(false)` so a Leave-the-Stage tap hides the bar on
 *     the next frame, instead of waiting for the presence event to
 *     sign, broadcast, and loop back through LocalCache (a delay made
 *     visible in scenario "broadcast → mute → unmute → leave", where
 *     the signer queues behind a pending mute frame + 500 ms debounce).
 *
 * AND-of-both also handles host demotion correctly: intent stays true
 * but the aggregator drops us, so the bar still hides.
 */
@Composable
internal fun StageControlsBar(
    viewModel: NestViewModel,
    ui: NestUiState,
    isOnStage: Boolean,
    canBroadcast: Boolean,
    speakerPubkeyHex: String,
    modifier: Modifier = Modifier,
) {
    if (!isOnStage || !ui.onStageNow) return
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (canBroadcast) {
            OnStageControls(
                viewModel = viewModel,
                broadcast = ui.broadcast,
                speakerPubkeyHex = speakerPubkeyHex,
            )
        } else {
            // No signing/permission — only thing we can do is step down.
            LeaveStageButton(onClick = { viewModel.setOnStage(false) })
        }
    }
}

/** Single-line red error strip. Surfaces the most relevant failure. */
@Composable
private fun ActionBarStatusStrip(ui: NestUiState) {
    val text = ui.statusStripText() ?: return
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun NestUiState.statusStripText(): String? {
    val connection = connection
    if (connection is ConnectionUiState.Failed) {
        return stringRes(Res.string.nest_audio_failed, connection.reason)
    }
    return when (val b = broadcast) {
        is BroadcastUiState.Failed -> stringRes(Res.string.nest_broadcast_failed, b.reason)
        is BroadcastUiState.Broadcasting -> b.muteError?.let { stringRes(Res.string.nest_mute_failed, it) }
        else -> null
    }
}

@Composable
private fun StartCluster(
    viewModel: NestViewModel,
    ui: NestUiState,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (val connection = ui.connection) {
            is ConnectionUiState.Idle,
            is ConnectionUiState.Closed,
            is ConnectionUiState.Failed,
            -> {
                ConnectButton(onClick = viewModel::connect)
            }

            is ConnectionUiState.Connecting -> {
                StatusChip(label = connectingLabel(connection))
            }

            is ConnectionUiState.Reconnecting -> {
                StatusChip(label = stringRes(Res.string.nest_reconnecting))
            }

            // On-stage controls live in [StageControlsBar]; audience
            // has nothing to do here (system volume keys are enough).
            is ConnectionUiState.Connected -> {}
        }
    }
}

@Composable
private fun OnStageControls(
    viewModel: NestViewModel,
    broadcast: BroadcastUiState,
    speakerPubkeyHex: String,
) {
    val leaveStage = {
        viewModel.stopBroadcast()
        viewModel.setOnStage(false)
    }
    when (broadcast) {
        BroadcastUiState.Idle -> {
            OnStageIdleControls(
                viewModel = viewModel,
                speakerPubkeyHex = speakerPubkeyHex,
            )
        }

        BroadcastUiState.Connecting -> {
            StatusChip(label = stringRes(Res.string.nest_broadcast_connecting))
            // stopBroadcast() cancels the in-flight speakerConnectJob,
            // so leaving mid-handshake is safe.
            LeaveStageButton(onClick = leaveStage)
        }

        is BroadcastUiState.Broadcasting -> {
            MicMuteToggle(isMuted = broadcast.isMuted, onToggle = viewModel::setMicMuted)
            LeaveStageButton(onClick = leaveStage)
        }

        is BroadcastUiState.Failed -> {
            // Reason is shown in the status strip; this button retries.
            // Same auto-muted semantics as the idle path: the retry
            // should bring the publisher back up without opening the
            // mic, then the user can unmute deliberately.
            TalkButton(
                onClick = { viewModel.startBroadcast(speakerPubkeyHex, initialMuted = true) },
                contentDescription = stringRes(Res.string.nest_talk),
            )
            LeaveStageButton(onClick = { viewModel.setOnStage(false) })
        }
    }
}

@Composable
private fun OnStageIdleControls(
    viewModel: NestViewModel,
    speakerPubkeyHex: String,
) {
    val context = LocalContext.current
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionDenied = !granted
            // Permission grant on a Talk-button tap should start a
            // *muted* broadcast — auto-start semantics. The host
            // taps Talk to claim the on-stage slot; unmute is a
            // separate, deliberate action below.
            if (granted) viewModel.startBroadcast(speakerPubkeyHex, initialMuted = true)
        }
    // The launcher callback never fires for Settings-deep-link grants, so
    // re-check on every recomposition to auto-clear the warning.
    val showDenialWarning =
        permissionDenied && !context.hasMicPermission()

    // Auto-start the muted broadcast as soon as the host (or any
    // on-stage speaker) lands on this composable WITH mic permission
    // already granted. Without this, a host who creates a room sees
    // a "Connecting..." spinner over their avatar until they tap Talk
    // — the publisher session only opens on tap, so listeners can't
    // subscribe yet, and the `connectingSpeakers` overlay sticks. By
    // pre-opening the publisher in muted state here, listeners get
    // an immediate Active announce and the host's avatar leaves the
    // spinner state on its own. Unmute is still a separate tap on
    // the mic toggle that appears once we transition to
    // `BroadcastUiState.Broadcasting(isMuted = true)`.
    //
    // Permission-denied case is intentionally left to the manual
    // Talk-button tap below: we don't want a host to be auto-prompted
    // for the mic the instant they enter the room. Tapping Talk is
    // the consent gesture that triggers `permissionLauncher`.
    LaunchedEffect(speakerPubkeyHex) {
        if (context.hasMicPermission()) {
            viewModel.startBroadcast(speakerPubkeyHex, initialMuted = true)
        }
    }

    TalkButton(
        onClick = {
            if (context.hasMicPermission()) {
                viewModel.startBroadcast(speakerPubkeyHex, initialMuted = false)
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        contentDescription = stringRes(Res.string.nest_talk),
    )
    LeaveStageButton(onClick = { viewModel.setOnStage(false) })
    if (showDenialWarning) {
        // After "Don't ask again" the launcher silently returns false;
        // expose a Settings deep-link.
        OutlinedButton(onClick = { context.openAppSettings() }) {
            Text(stringRes(Res.string.nest_open_settings))
        }
    }
}

@Composable
private fun EndCluster(
    isOnStage: Boolean,
    isConnected: Boolean,
    handRaised: Boolean,
    onHandRaisedChange: (Boolean) -> Unit,
    roomNote: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
    onLeave: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Hand-raise: only meaningful for connected audience. On stage
        // it's moot; disconnected it can't reach the room.
        if (!isOnStage && isConnected) {
            HandRaiseToggle(handRaised = handRaised, onToggle = onHandRaisedChange)
        }
        // Zap and React both work in any state — even disconnected
        // users can zap / react via the room note.
        NestZapButton(
            roomNote = roomNote,
            accountViewModel = accountViewModel,
            nav = nav,
        )
        // React works in any state — even disconnected users can react
        // via the room note. Reuses the same ReactionChoicePopup
        // NoteCompose's heart button drives (NIP-30 custom-emoji
        // support, user-configured reaction set). Earlier hand-rolled
        // bottom-sheet picker is gone — it only emitted a fixed set
        // of six unicode emojis with no path to custom packs.
        var wantsToReact by rememberSaveable { mutableStateOf(false) }
        FilledTonalIconButton(onClick = { wantsToReact = true }) {
            Icon(
                symbol = MaterialSymbols.EmojiEmotions,
                contentDescription = stringRes(Res.string.nest_reactions_button),
            )
            if (wantsToReact) {
                RoomReactionPopup(
                    roomNote = roomNote,
                    iconSize = 24.dp,
                    accountViewModel = accountViewModel,
                    onDismiss = { wantsToReact = false },
                    // No `Route.UpdateReactionType` from inside the
                    // standalone NestActivity (no NavController). The
                    // "change reactions" button still dismisses the
                    // popup; users edit their reaction set from the
                    // main app's settings.
                    onChangeAmount = { wantsToReact = false },
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        LeaveRoomButton(onClick = onLeave)
    }
}

/**
 * Round zap button styled to match [HandRaiseToggle] / the React
 * button — same `FilledTonalIconButton` shape and tonal palette, but
 * tinted with [BitcoinOrange] while a zap is in flight so the user
 * sees the progress without needing a separate amount label.
 *
 * Click behavior is delegated to NoteCompose's [zapClick], which
 * applies the user's configured zap amount choices the same way the
 * normal note ⚡ button does (single-tap fires the default amount;
 * multi-choice opens [ZapAmountChoicePopup]; an unconfigured account
 * opens [ZapCustomDialog]). Long-press routes to the
 * [Route.UpdateZapAmount] settings screen via the activity's
 * [BouncingIntentNav] (no-op when the route can't be expressed as a
 * `nostr:` URI — same fallback as the chat panel uses).
 */
@OptIn(ExperimentalUuidApi::class)
@Composable
private fun NestZapButton(
    roomNote: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noWalletFoundStr = stringRes(Res.string.no_wallet_found)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var wantsToZap by remember { mutableStateOf(false) }
    var wantsToSetCustomZap by remember { mutableStateOf(false) }

    // 0f when idle. Driven 0f → 1f by the zap flow's progress
    // callbacks; reset to 0f on error, on dismiss, and when the flow
    // hands off to an external wallet via `onPayViaIntent` (the
    // button can't observe payment completion past that handoff, so
    // it must not leave the spinner up — unlike NoteCompose's
    // ZapReaction, this button has no ObserveZapIconState fallback).
    var zappingProgress by remember { mutableFloatStateOf(0f) }

    val isZapping = zappingProgress > 0.00001f && zappingProgress < 0.99999f

    FilledTonalIconButton(
        onClick = {
            scope.launch {
                zapClick(
                    baseNote = roomNote,
                    accountViewModel = accountViewModel,
                    onZapStarts = {},
                    onZappingProgress = { progress -> scope.launch { zappingProgress = progress } },
                    onMultipleChoices = { scope.launch { wantsToZap = true } },
                    onError = { _, message, user ->
                        scope.launch {
                            zappingProgress = 0f
                            accountViewModel.toastManager.toast(Res.string.error_dialog_zap_error, message, user)
                        }
                    },
                    onPayViaIntent = {
                        // Handing off to an external wallet (or the
                        // manual-split screen) ends the in-app phase —
                        // clear the spinner since nothing downstream
                        // tracks payment completion for this button.
                        zappingProgress = 0f
                        if (it.size == 1) {
                            val payable = it.first()
                            payViaIntent(payable.invoice, context, noWalletFoundStr, { }) { error ->
                                zappingProgress = 0f
                                accountViewModel.toastManager.toast(
                                    Res.string.error_dialog_zap_error,
                                    UserBasedErrorMessage(error, payable.info.user),
                                )
                            }
                        } else {
                            val uid = Uuid.random().toString()
                            accountViewModel.tempManualPaymentCache.put(uid, it)
                            nav.nav(Route.ManualZapSplitPayment(uid))
                        }
                    },
                    onCustomAmount = { wantsToSetCustomZap = true },
                )
            }
        },
    ) {
        if (isZapping) {
            CircularProgressIndicator(
                progress = { zappingProgress },
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(
                symbol = MaterialSymbols.Bolt,
                contentDescription = stringRes(Res.string.zap_description),
            )
        }
    }

    if (wantsToZap) {
        ZapAmountChoicePopup(
            baseNote = roomNote,
            popupYOffset = 48.dp,
            accountViewModel = accountViewModel,
            onZapStarts = {},
            onDismiss = {
                wantsToZap = false
                zappingProgress = 0f
            },
            onChangeAmount = {
                scope.launch {
                    wantsToZap = false
                    nav.nav(Route.UpdateZapAmount())
                }
            },
            onError = { _, message, user ->
                scope.launch {
                    zappingProgress = 0f
                    accountViewModel.toastManager.toast(Res.string.error_dialog_zap_error, message, user)
                }
            },
            onProgress = { scope.launch(Dispatchers.Main) { zappingProgress = it } },
            onPayViaIntent = {
                // Handing off to an external wallet (or the manual-
                // split screen) ends the in-app phase — clear the
                // spinner since nothing downstream tracks payment
                // completion for this button.
                zappingProgress = 0f
                if (it.size == 1) {
                    val payable = it.first()
                    payViaIntent(payable.invoice, context, noWalletFoundStr, { }) { error ->
                        zappingProgress = 0f
                        accountViewModel.toastManager.toast(
                            Res.string.error_dialog_zap_error,
                            UserBasedErrorMessage(error, payable.info.user),
                        )
                    }
                } else {
                    val uid = Uuid.random().toString()
                    accountViewModel.tempManualPaymentCache.put(uid, it)
                    nav.nav(Route.ManualZapSplitPayment(uid))
                }
            },
            onReloadNutzap = { amount ->
                wantsToZap = false
                navigateToReloadMint(accountViewModel, nav, roomNote, amount)
            },
        )
    }

    if (wantsToSetCustomZap) {
        ZapCustomDialog(
            onZapStarts = {},
            onClose = { wantsToSetCustomZap = false },
            onError = { _, message, user ->
                scope.launch {
                    zappingProgress = 0f
                    accountViewModel.toastManager.toast(Res.string.error_dialog_zap_error, message, user)
                }
            },
            onProgress = { scope.launch(Dispatchers.Main) { zappingProgress = it } },
            onPayViaIntent = {
                // Handing off to an external wallet (or the manual-
                // split screen) ends the in-app phase — clear the
                // spinner since nothing downstream tracks payment
                // completion for this button.
                zappingProgress = 0f
                if (it.size == 1) {
                    val payable = it.first()
                    payViaIntent(payable.invoice, context, noWalletFoundStr, { }) { error ->
                        zappingProgress = 0f
                        accountViewModel.toastManager.toast(
                            Res.string.error_dialog_zap_error,
                            UserBasedErrorMessage(error, payable.info.user),
                        )
                    }
                } else {
                    val uid = Uuid.random().toString()
                    accountViewModel.tempManualPaymentCache.put(uid, it)
                    nav.nav(Route.ManualZapSplitPayment(uid))
                }
            },
            accountViewModel = accountViewModel,
            baseNote = roomNote,
        )
    }
}

// ── Reusable affordances ────────────────────────────────────────────────

@Composable
private fun ConnectButton(onClick: () -> Unit) {
    Button(onClick = onClick) {
        Text(stringRes(Res.string.nest_connect))
    }
}

/** Disabled assist chip used as a status indicator (no click target). */
@Composable
private fun StatusChip(label: String) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(label) },
    )
}

@Composable
private fun LeaveStageButton(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Text(stringRes(Res.string.nest_leave_stage))
    }
}

/**
 * Big primary 56dp mic button shown in the off-states (Idle, Failed)
 * to invite the user to start broadcasting. Larger than surrounding
 * 40dp icon buttons so the mic state is unmistakable.
 */
@Composable
private fun TalkButton(
    onClick: () -> Unit,
    contentDescription: String,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(56.dp),
        colors =
            IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
    ) {
        Icon(
            symbol = MaterialSymbols.MicOff,
            contentDescription = contentDescription,
            modifier = Modifier.size(28.dp),
        )
    }
}

/**
 * Cheap broadcast-side mute toggle. Keeps the MoQ session open and
 * just stops sending audio frames; unmute is sample-accurate.
 */
@Composable
private fun MicMuteToggle(
    isMuted: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    FilledTonalIconToggleButton(
        checked = isMuted,
        onCheckedChange = onToggle,
        modifier = Modifier.size(56.dp),
    ) {
        Icon(
            symbol = if (isMuted) MaterialSymbols.MicOff else MaterialSymbols.Mic,
            contentDescription = stringRes(if (isMuted) Res.string.nest_mic_unmute else Res.string.nest_mic_mute),
        )
    }
}

@Composable
private fun HandRaiseToggle(
    handRaised: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    FilledTonalIconToggleButton(
        checked = handRaised,
        onCheckedChange = onToggle,
    ) {
        Icon(
            symbol = MaterialSymbols.PanTool,
            contentDescription = stringRes(if (handRaised) Res.string.nest_lower_hand else Res.string.nest_raise_hand),
        )
    }
}

@Composable
private fun LeaveRoomButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        colors =
            ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
    ) {
        Icon(
            symbol = MaterialSymbols.AutoMirrored.Logout,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.width(6.dp))
        Text(stringRes(Res.string.nest_leave))
    }
}

@Composable
private fun connectingLabel(connection: ConnectionUiState.Connecting): String =
    when (connection.step) {
        ConnectionUiState.Step.ResolvingRoom -> stringRes(Res.string.nest_connecting_resolving)
        ConnectionUiState.Step.OpeningTransport -> stringRes(Res.string.nest_connecting_transport)
        ConnectionUiState.Step.MoqHandshake -> stringRes(Res.string.nest_connecting_handshake)
    }

// ── Permission helpers ──────────────────────────────────────────────────

private fun Context.hasMicPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    runCatching {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
        )
    }
}
