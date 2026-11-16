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
package com.vitorpamplona.amethyst.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.nipACWebRtcCalls.CallState
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.call_add_participant
import com.vitorpamplona.amethyst.commons.resources.call_bluetooth
import com.vitorpamplona.amethyst.commons.resources.call_camera_off
import com.vitorpamplona.amethyst.commons.resources.call_camera_on
import com.vitorpamplona.amethyst.commons.resources.call_dismiss
import com.vitorpamplona.amethyst.commons.resources.call_earpiece
import com.vitorpamplona.amethyst.commons.resources.call_hangup
import com.vitorpamplona.amethyst.commons.resources.call_mute
import com.vitorpamplona.amethyst.commons.resources.call_screen_share_start
import com.vitorpamplona.amethyst.commons.resources.call_screen_share_stop
import com.vitorpamplona.amethyst.commons.resources.call_screen_sharing
import com.vitorpamplona.amethyst.commons.resources.call_search_users
import com.vitorpamplona.amethyst.commons.resources.call_speaker
import com.vitorpamplona.amethyst.commons.resources.call_switch_camera
import com.vitorpamplona.amethyst.commons.resources.call_unmute
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.service.call.AudioRoute
import com.vitorpamplona.amethyst.ui.call.session.CallSession
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay
import org.webrtc.VideoTrack

@Composable
fun ConnectedCallUI(
    state: CallState.Connected,
    callSession: CallSession?,
    accountViewModel: AccountViewModel,
    onHangup: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleVideo: () -> Unit,
    onStartScreenShare: () -> Unit,
    onStopScreenShare: () -> Unit,
    onCycleAudioRoute: () -> Unit,
    onInvitePeer: (String) -> Unit = {},
) {
    var elapsed by remember { mutableLongStateOf(0L) }

    LaunchedEffect(state.startedAtEpoch) {
        while (true) {
            elapsed = TimeUtils.now() - state.startedAtEpoch
            delay(1000)
        }
    }

    val emptyVideoFlow = remember { kotlinx.coroutines.flow.MutableStateFlow<VideoTrack?>(null) }
    val emptyTracksFlow = remember { kotlinx.coroutines.flow.MutableStateFlow<Map<String, VideoTrack>>(emptyMap()) }
    val emptySetFlow = remember { kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet()) }
    val remoteVideoTracks by (callSession?.remoteVideoTracks ?: emptyTracksFlow).collectAsState()
    val activePeerVideos by (callSession?.activePeerVideos ?: emptySetFlow).collectAsState()
    val localVideoTrack by (callSession?.localVideoTrack ?: emptyVideoFlow).collectAsState()
    val defaultFalse = remember { kotlinx.coroutines.flow.MutableStateFlow(false) }
    val defaultTrue = remember { kotlinx.coroutines.flow.MutableStateFlow(true) }
    val isAudioMuted by (callSession?.isAudioMuted ?: defaultFalse).collectAsState()
    val isVideoEnabled by (callSession?.isVideoEnabled ?: defaultTrue).collectAsState()
    val isScreenSharing by (callSession?.isScreenSharing ?: defaultFalse).collectAsState()
    val isFrontCamera by (callSession?.isFrontCamera ?: defaultTrue).collectAsState()
    val currentAudioRoute by (callSession?.audioRoute ?: remember { kotlinx.coroutines.flow.MutableStateFlow(AudioRoute.EARPIECE) }).collectAsState()
    val hasActiveVideo =
        state.callType == com.vitorpamplona.quartz.nipACWebRtcCalls.tags.CallType.VIDEO ||
            isVideoEnabled ||
            isScreenSharing ||
            remoteVideoTracks.isNotEmpty()

    var showAddParticipant by remember { mutableStateOf(false) }

    if (showAddParticipant) {
        AddParticipantDialog(
            accountViewModel = accountViewModel,
            existingPeers = state.allPeerPubKeys,
            onInvite = { peerPubKey ->
                onInvitePeer(peerPubKey)
                showAddParticipant = false
            },
            onDismiss = { showAddParticipant = false },
        )
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        val otherMembers =
            remember(state.allPeerPubKeys) {
                state.allPeerPubKeys - accountViewModel.account.signer.pubKey
            }

        if (hasActiveVideo) {
            PeerVideoGrid(
                peerPubKeys = otherMembers,
                pendingPeerPubKeys = state.pendingPeerPubKeys,
                remoteVideoTracks = remoteVideoTracks,
                activePeerVideos = activePeerVideos,
                eglBase = callSession?.getEglBase(),
                accountViewModel = accountViewModel,
                modifier = Modifier.fillMaxSize(),
            )

            if (isVideoEnabled && !isScreenSharing) {
                localVideoTrack?.let { track ->
                    VideoRenderer(
                        videoTrack = track,
                        eglBase = callSession?.getEglBase(),
                        modifier =
                            Modifier
                                .size(120.dp, 160.dp)
                                .align(Alignment.TopEnd)
                                .windowInsetsPadding(WindowInsets.statusBars)
                                .padding(16.dp),
                        mirror = isFrontCamera,
                        zOrderMediaOverlay = true,
                    )
                }
            }

            Column(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (isScreenSharing) {
                    Text(
                        text = stringRes(Res.string.call_screen_sharing),
                        color = Color.Cyan,
                        fontSize = 14.sp,
                    )
                }
                Text(
                    text = formatDuration(elapsed),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                )
            }
        } else {
            // Voice-only path: render the same peer grid so each pending
            // callee shows their individual "Calling…" status rather than a
            // single shared banner. Tracks and active-video sets are empty
            // here, so every cell falls through to PeerAvatarCell.
            val emptyTracks = remember { emptyMap<String, VideoTrack>() }
            val emptyActive = remember { emptySet<String>() }

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                PeerVideoGrid(
                    peerPubKeys = otherMembers,
                    pendingPeerPubKeys = state.pendingPeerPubKeys,
                    remoteVideoTracks = emptyTracks,
                    activePeerVideos = emptyActive,
                    eglBase = null,
                    accountViewModel = accountViewModel,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = formatDuration(elapsed),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Controls at bottom
        CallControls(
            isAudioMuted = isAudioMuted,
            isVideoEnabled = isVideoEnabled,
            isScreenSharing = isScreenSharing,
            isFrontCamera = isFrontCamera,
            currentAudioRoute = currentAudioRoute,
            onToggleMute = onToggleMute,
            onToggleVideo = onToggleVideo,
            onStartScreenShare = onStartScreenShare,
            onStopScreenShare = onStopScreenShare,
            onSwitchCamera = { callSession?.switchCamera() },
            onCycleAudioRoute = onCycleAudioRoute,
            onAddParticipant = { showAddParticipant = true },
            onHangup = onHangup,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 24.dp),
        )
    }
}

@Composable
private fun CallControls(
    isAudioMuted: Boolean,
    isVideoEnabled: Boolean,
    isScreenSharing: Boolean,
    isFrontCamera: Boolean,
    currentAudioRoute: AudioRoute,
    onToggleMute: () -> Unit,
    onToggleVideo: () -> Unit,
    onStartScreenShare: () -> Unit,
    onStopScreenShare: () -> Unit,
    onSwitchCamera: () -> Unit,
    onCycleAudioRoute: () -> Unit,
    onAddParticipant: () -> Unit,
    onHangup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            IconButton(onClick = onToggleMute, modifier = Modifier.size(56.dp)) {
                Icon(
                    symbol = if (isAudioMuted) MaterialSymbols.MicOff else MaterialSymbols.Mic,
                    contentDescription = stringRes(if (isAudioMuted) Res.string.call_unmute else Res.string.call_mute),
                    tint = if (isAudioMuted) Color.Red else Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
            IconButton(
                onClick = onToggleVideo,
                enabled = !isScreenSharing,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    symbol = if (isVideoEnabled) MaterialSymbols.Videocam else MaterialSymbols.VideocamOff,
                    contentDescription = stringRes(if (isVideoEnabled) Res.string.call_camera_off else Res.string.call_camera_on),
                    tint =
                        when {
                            isScreenSharing -> Color.Gray
                            !isVideoEnabled -> Color.Red
                            else -> Color.White
                        },
                    modifier = Modifier.size(28.dp),
                )
            }
            IconButton(
                onClick = if (isScreenSharing) onStopScreenShare else onStartScreenShare,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    symbol = MaterialSymbols.Cast,
                    contentDescription =
                        stringRes(
                            if (isScreenSharing) Res.string.call_screen_share_stop else Res.string.call_screen_share_start,
                        ),
                    tint = if (isScreenSharing) Color.Cyan else Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
            if (isVideoEnabled && !isScreenSharing) {
                IconButton(onClick = onSwitchCamera, modifier = Modifier.size(56.dp)) {
                    Icon(
                        symbol =
                            if (isFrontCamera) {
                                MaterialSymbols.CameraRear
                            } else {
                                MaterialSymbols.CameraFront
                            },
                        contentDescription = stringRes(Res.string.call_switch_camera),
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            IconButton(onClick = onCycleAudioRoute, modifier = Modifier.size(56.dp)) {
                Icon(
                    symbol =
                        when (currentAudioRoute) {
                            AudioRoute.EARPIECE -> MaterialSymbols.Hearing
                            AudioRoute.SPEAKER -> MaterialSymbols.AutoMirrored.VolumeUp
                            AudioRoute.BLUETOOTH -> MaterialSymbols.BluetoothAudio
                        },
                    contentDescription =
                        stringRes(
                            when (currentAudioRoute) {
                                AudioRoute.EARPIECE -> Res.string.call_earpiece
                                AudioRoute.SPEAKER -> Res.string.call_speaker
                                AudioRoute.BLUETOOTH -> Res.string.call_bluetooth
                            },
                        ),
                    tint =
                        when (currentAudioRoute) {
                            AudioRoute.EARPIECE -> Color.White
                            AudioRoute.SPEAKER -> Color.Cyan
                            AudioRoute.BLUETOOTH -> Color(0xFF2196F3)
                        },
                    modifier = Modifier.size(28.dp),
                )
            }
            IconButton(onClick = onAddParticipant, modifier = Modifier.size(56.dp)) {
                Icon(
                    symbol = MaterialSymbols.PersonAdd,
                    contentDescription = stringRes(Res.string.call_add_participant),
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        FloatingActionButton(
            onClick = onHangup,
            containerColor = Color.Red,
            shape = CircleShape,
            modifier = Modifier.size(64.dp),
        ) {
            Icon(
                MaterialSymbols.CallEnd,
                contentDescription = stringRes(Res.string.call_hangup),
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
private fun AddParticipantDialog(
    accountViewModel: AccountViewModel,
    existingPeers: Set<String>,
    onInvite: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val userSuggestions =
        remember {
            UserSuggestionState(accountViewModel.account, accountViewModel.nip05ClientBuilder())
        }
    var searchText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.call_add_participant)) },
        text = {
            Column {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = {
                        searchText = it
                        userSuggestions.processCurrentWord(it)
                    },
                    label = { Text(stringRes(Res.string.call_search_users)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(modifier = Modifier.height(300.dp)) {
                    ShowUserSuggestionList(
                        userSuggestions = userSuggestions,
                        onSelect = { user ->
                            if (user.pubkeyHex !in existingPeers) {
                                onInvite(user.pubkeyHex)
                            }
                        },
                        accountViewModel = accountViewModel,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(Res.string.call_dismiss))
            }
        },
    )
}
