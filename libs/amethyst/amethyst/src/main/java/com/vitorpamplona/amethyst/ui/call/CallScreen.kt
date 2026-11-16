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

import android.app.Activity
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.nipACWebRtcCalls.CallManager
import com.vitorpamplona.amethyst.commons.nipACWebRtcCalls.CallState
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.call_accept
import com.vitorpamplona.amethyst.commons.resources.call_calling
import com.vitorpamplona.amethyst.commons.resources.call_connecting
import com.vitorpamplona.amethyst.commons.resources.call_dismiss
import com.vitorpamplona.amethyst.commons.resources.call_ended
import com.vitorpamplona.amethyst.commons.resources.call_hangup
import com.vitorpamplona.amethyst.commons.resources.call_incoming
import com.vitorpamplona.amethyst.commons.resources.call_incoming_video
import com.vitorpamplona.amethyst.commons.resources.call_incoming_voice
import com.vitorpamplona.amethyst.commons.resources.call_reject
import com.vitorpamplona.amethyst.commons.ui.components.PlatformBackHandler
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.ui.call.session.CallSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CallScreen(
    callManager: CallManager,
    callSession: CallSession?,
    accountViewModel: AccountViewModel,
    onCallEnded: () -> Unit,
    isInPipMode: Boolean = false,
) {
    val callState by callManager.state.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val mediaProjectionManager = remember(context) { context.getSystemService(MediaProjectionManager::class.java) }
    val screenShareLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.let { callSession?.startScreenShare(it) }
            }
        }
    val emptyStringFlow = remember { kotlinx.coroutines.flow.MutableStateFlow<String?>(null) }
    val errorMessage by (callSession?.errorMessage ?: emptyStringFlow).collectAsState()

    PlatformBackHandler(enabled = callState !is CallState.Idle && callState !is CallState.Ended) {
        scope.launch { callManager.hangup() }
    }

    KeepScreenOn()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = callState) {
            is CallState.Idle -> {
                LaunchedEffect(Unit) {
                    delay(500)
                    if (callManager.state.value is CallState.Idle) {
                        onCallEnded()
                    }
                }
            }

            is CallState.Offering -> {
                val otherMembers =
                    remember(state.peerPubKeys) {
                        state.peerPubKeys - accountViewModel.account.signer.pubKey
                    }
                if (isInPipMode) {
                    PipCallUI(peerPubKeys = otherMembers, statusText = stringRes(Res.string.call_calling), accountViewModel = accountViewModel)
                } else {
                    CallInProgressUI(
                        peerPubKeys = otherMembers,
                        statusText = stringRes(Res.string.call_calling),
                        accountViewModel = accountViewModel,
                        onHangup = { scope.launch { callManager.hangup() } },
                    )
                }
            }

            is CallState.IncomingCall -> {
                val otherMembers =
                    remember(state.groupMembers) {
                        state.groupMembers - accountViewModel.account.signer.pubKey
                    }
                if (isInPipMode) {
                    PipCallUI(peerPubKeys = otherMembers, statusText = stringRes(Res.string.call_incoming), accountViewModel = accountViewModel)
                } else {
                    val isVideoCall = state.callType == com.vitorpamplona.quartz.nipACWebRtcCalls.tags.CallType.VIDEO
                    val acceptWithPermission =
                        rememberCallWithPermission(context, isVideo = isVideoCall) {
                            callSession?.accept(state.sdpOffer)
                        }
                    IncomingCallUI(
                        groupMembers = otherMembers,
                        callType = state.callType,
                        accountViewModel = accountViewModel,
                        onAccept = acceptWithPermission,
                        onReject = { scope.launch { callManager.rejectCall() } },
                    )
                }
            }

            is CallState.Connecting -> {
                val otherMembers =
                    remember(state.peerPubKeys) {
                        state.peerPubKeys - accountViewModel.account.signer.pubKey
                    }
                if (isInPipMode) {
                    PipCallUI(peerPubKeys = otherMembers, statusText = stringRes(Res.string.call_connecting), accountViewModel = accountViewModel)
                } else {
                    CallInProgressUI(
                        peerPubKeys = otherMembers,
                        statusText = stringRes(Res.string.call_connecting),
                        accountViewModel = accountViewModel,
                        onHangup = { scope.launch { callManager.hangup() } },
                    )
                }
            }

            is CallState.Connected -> {
                if (isInPipMode) {
                    PipConnectedCallUI(state = state, callSession = callSession, accountViewModel = accountViewModel)
                } else {
                    ConnectedCallUI(
                        state = state,
                        callSession = callSession,
                        accountViewModel = accountViewModel,
                        onHangup = { scope.launch { callManager.hangup() } },
                        onToggleMute = { callSession?.toggleMute() },
                        onToggleVideo = { callSession?.toggleVideo() },
                        onStartScreenShare = {
                            mediaProjectionManager?.createScreenCaptureIntent()?.let(screenShareLauncher::launch)
                        },
                        onStopScreenShare = { callSession?.stopScreenShare() },
                        onCycleAudioRoute = { callSession?.cycleAudioRoute() },
                        onInvitePeer = { peerPubKey -> callSession?.invitePeer(peerPubKey) },
                    )
                }
            }

            is CallState.Ended -> {
                val otherMembers =
                    remember(state.peerPubKeys) {
                        state.peerPubKeys - accountViewModel.account.signer.pubKey
                    }
                if (!isInPipMode) {
                    CallInProgressUI(
                        peerPubKeys = otherMembers,
                        statusText = stringRes(Res.string.call_ended),
                        accountViewModel = accountViewModel,
                        onHangup = { onCallEnded() },
                    )
                }
                LaunchedEffect(Unit) {
                    delay(2000)
                    onCallEnded()
                }
            }
        }

        if (!isInPipMode) {
            errorMessage?.let { error ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    action = {
                        androidx.compose.material3.TextButton(
                            onClick = { callSession?.clearError() },
                        ) {
                            Text(
                                stringRes(Res.string.call_dismiss),
                                color = MaterialTheme.colorScheme.inversePrimary,
                            )
                        }
                    },
                ) {
                    Text(error)
                }
            }
        }
    }
}

@Composable
private fun CallInProgressUI(
    peerPubKeys: Set<String>,
    statusText: String,
    accountViewModel: AccountViewModel,
    onHangup: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            GroupCallPictures(
                peerPubKeys = peerPubKeys,
                size = 120.dp,
                accountViewModel = accountViewModel,
            )
            Spacer(modifier = Modifier.height(16.dp))
            GroupCallNames(
                peerPubKeys = peerPubKeys,
                accountViewModel = accountViewModel,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = statusText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
            )
            Spacer(modifier = Modifier.height(48.dp))
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
}

@Composable
private fun IncomingCallUI(
    groupMembers: Set<String>,
    callType: com.vitorpamplona.quartz.nipACWebRtcCalls.tags.CallType,
    accountViewModel: AccountViewModel,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            GroupCallPictures(
                peerPubKeys = groupMembers,
                size = 120.dp,
                accountViewModel = accountViewModel,
            )
            Spacer(modifier = Modifier.height(16.dp))
            GroupCallNames(
                peerPubKeys = groupMembers,
                accountViewModel = accountViewModel,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text =
                    stringRes(
                        if (callType == com.vitorpamplona.quartz.nipACWebRtcCalls.tags.CallType.VIDEO) {
                            Res.string.call_incoming_video
                        } else {
                            Res.string.call_incoming_voice
                        },
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
            )
            Spacer(modifier = Modifier.height(48.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(48.dp),
            ) {
                FloatingActionButton(
                    onClick = onReject,
                    containerColor = Color.Red,
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(
                        MaterialSymbols.CallEnd,
                        contentDescription = stringRes(Res.string.call_reject),
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
                FloatingActionButton(
                    onClick = onAccept,
                    containerColor = Color(0xFF4CAF50),
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(
                        MaterialSymbols.Call,
                        contentDescription = stringRes(Res.string.call_accept),
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
}
