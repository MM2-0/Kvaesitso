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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.lifecycle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nests.room.lifecycle.NestRoomEventCollectors
import com.vitorpamplona.amethyst.commons.viewmodels.BroadcastUiState
import com.vitorpamplona.amethyst.commons.viewmodels.ConnectionUiState
import com.vitorpamplona.amethyst.commons.viewmodels.NestUiState
import com.vitorpamplona.amethyst.commons.viewmodels.NestViewModel
import com.vitorpamplona.amethyst.service.nests.NestForegroundService
import com.vitorpamplona.nestsclient.NestsRoomConfig
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import kotlinx.coroutines.flow.SharedFlow

/**
 * Build (or recall) the [NestViewModel] for a given room. Keyed on
 * `<authBaseUrl>|<roomId>` so navigating between two rooms in the
 * same Activity (unlikely today, possible tomorrow) doesn't share
 * audio state.
 */
@Composable
internal fun rememberNestViewModel(
    room: NestsRoomConfig,
    signer: NostrSigner,
): NestViewModel =
    viewModel(
        key = "${room.authBaseUrl}|${room.roomId}",
        factory =
            remember(room, signer) {
                NestViewModelFactory(signer = signer, room = room)
            },
    )

/**
 * Connect to MoQ on entry and keep the listener's "who's on stage"
 * set in sync with the kind-30312's `p`-tags. Idempotent: the VM
 * guards against duplicate `connect()` calls if the screen
 * recomposes.
 */
@Composable
internal fun AutoConnectAndTrackSpeakers(
    viewModel: NestViewModel,
    onStageKeys: Set<String>,
) {
    LaunchedEffect(viewModel) { viewModel.connect() }
    LaunchedEffect(viewModel, onStageKeys) { viewModel.updateSpeakers(onStageKeys) }
}

/**
 * Bounce out of the room when the kick handler flips
 * [NestViewModel.wasKicked] to true. Authority check happens in
 * [NestRoomEventCollectors] before `onKick()` is invoked; this
 * composable just observes the boolean.
 */
@Composable
internal fun LeaveOnKick(
    viewModel: NestViewModel,
    onLeave: () -> Unit,
) {
    val wasKicked by viewModel.wasKicked.collectAsState()
    LaunchedEffect(wasKicked) { if (wasKicked) onLeave() }
}

/**
 * Bounce out of the room when the host flips the kind-30312
 * `status` tag to CLOSED (or when [MeetingSpaceEvent.checkStatus]
 * auto-closes a stale event past the 8 h cutoff). The relay
 * subscription in [NestRoomFilterAssemblerSubscription] feeds
 * LocalCache, `observeNoteEvent` upstream emits the new
 * [MeetingSpaceEvent] reference, and this LaunchedEffect's key
 * change triggers the leave.
 *
 * Without this, a host ending the room only takes effect for
 * users who back out manually — every other listener / speaker
 * stays connected to the relay until either the next JWT
 * expiry or `onCleared()` fires when they finally close the
 * activity. Users hear silence (no one's publishing) but the
 * UI shows them as still "in" the room.
 *
 * Triggers only when the live event's status is CLOSED; an
 * initial event in any other state (PLANNED, OPEN, PRIVATE)
 * is a no-op so the room can finish its connect flow first.
 */
@Composable
internal fun LeaveOnRoomClosed(
    event: MeetingSpaceEvent,
    onLeave: () -> Unit,
) {
    // `event.isLive()` returns false on CLOSED. Keying on that
    // boolean (rather than the event reference) means the effect
    // fires once per live→closed transition, regardless of how
    // many other tag changes the host pushes alongside.
    val isLive = event.isLive()
    var wasLive by remember { mutableStateOf(isLive) }

    LaunchedEffect(isLive) {
        if (wasLive && !isLive) {
            onLeave()
        }
        wasLive = isLive
    }
}

/**
 * Bridge between the in-Compose VM state and the Activity-level
 * Picture-in-Picture controller: pushes mute / connected state UP
 * so the Activity can rebuild PIP RemoteActions and gate
 * `enterPictureInPictureMode` on Connected, and forwards mute
 * taps from the system PIP overlay DOWN into the VM.
 *
 * `Reconnecting` counts as connected so a transient transport blip
 * doesn't drop the user out of PIP — the listener's hot
 * SubscribeHandle pump preserves audio across the cutover.
 */
@Composable
internal fun PipBridge(
    ui: NestUiState,
    pipMuteSignal: SharedFlow<Unit>,
    viewModel: NestViewModel,
    onMuteState: (Boolean) -> Unit,
    onConnectedChange: (Boolean) -> Unit,
) {
    LaunchedEffect(ui.isMuted) { onMuteState(ui.isMuted) }

    val isConnectedNow =
        ui.connection is ConnectionUiState.Connected ||
            ui.connection is ConnectionUiState.Reconnecting
    LaunchedEffect(isConnectedNow) { onConnectedChange(isConnectedNow) }

    LaunchedEffect(viewModel) {
        pipMuteSignal.collect {
            viewModel.setMuted(!viewModel.uiState.value.isMuted)
        }
    }
}

/**
 * Drive [NestForegroundService] state from the room's connection
 * + broadcast UI state. Service lifecycle is tied to the
 * Composable's lifetime: stop on dispose so the notification
 * doesn't outlive a finished room.
 *
 * Reconnecting counts as live (same reasoning as PipBridge).
 */
@Composable
internal fun NestForegroundServiceLifecycle(ui: NestUiState) {
    val context = LocalContext.current
    val isLive =
        ui.connection is ConnectionUiState.Connected ||
            ui.connection is ConnectionUiState.Reconnecting
    val isBroadcasting = ui.broadcast is BroadcastUiState.Broadcasting
    LaunchedEffect(isLive, isBroadcasting) {
        when {
            isLive && isBroadcasting -> NestForegroundService.promoteToMicrophone(context)
            isLive -> NestForegroundService.startListening(context)
            else -> NestForegroundService.stop(context)
        }
    }
    DisposableEffect(Unit) { onDispose { NestForegroundService.stop(context) } }
}
