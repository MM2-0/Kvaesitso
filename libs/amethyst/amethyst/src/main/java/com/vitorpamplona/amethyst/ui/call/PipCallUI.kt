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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.nipACWebRtcCalls.CallState
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.ui.call.session.CallSession
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay
import org.webrtc.VideoTrack

@Composable
fun PipCallUI(
    peerPubKeys: Set<String>,
    statusText: String,
    accountViewModel: AccountViewModel,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            GroupCallPictures(
                peerPubKeys = peerPubKeys,
                size = 48.dp,
                accountViewModel = accountViewModel,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = statusText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
fun PipConnectedCallUI(
    state: CallState.Connected,
    callSession: CallSession?,
    accountViewModel: AccountViewModel,
) {
    var elapsed by remember { mutableLongStateOf(0L) }

    LaunchedEffect(state.startedAtEpoch) {
        while (true) {
            elapsed = TimeUtils.now() - state.startedAtEpoch
            delay(1000)
        }
    }

    val emptyTracksFlow = remember { kotlinx.coroutines.flow.MutableStateFlow<Map<String, VideoTrack>>(emptyMap()) }
    val emptySetFlow = remember { kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet()) }
    val remoteVideoTracks by (callSession?.remoteVideoTracks ?: emptyTracksFlow).collectAsState()
    val activePeerVideos by (callSession?.activePeerVideos ?: emptySetFlow).collectAsState()
    val defaultFalse = remember { kotlinx.coroutines.flow.MutableStateFlow(false) }
    val isVideoEnabled by (callSession?.isVideoEnabled ?: defaultFalse).collectAsState()
    val hasActiveVideo =
        state.callType == com.vitorpamplona.quartz.nipACWebRtcCalls.tags.CallType.VIDEO ||
            isVideoEnabled ||
            remoteVideoTracks.isNotEmpty()

    val otherMembers =
        remember(state.allPeerPubKeys) {
            state.allPeerPubKeys - accountViewModel.account.signer.pubKey
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        if (hasActiveVideo) {
            val firstActivePeer = otherMembers.firstOrNull { it in activePeerVideos }
            val activeTrack = firstActivePeer?.let { remoteVideoTracks[it] }
            if (activeTrack != null) {
                VideoRenderer(
                    videoTrack = activeTrack,
                    eglBase = callSession?.getEglBase(),
                    modifier = Modifier.fillMaxSize(),
                    mirror = false,
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    GroupCallPictures(
                        peerPubKeys = otherMembers,
                        size = 48.dp,
                        accountViewModel = accountViewModel,
                    )
                }
            }
            // Show timer overlay on top of video
            Text(
                text = formatDuration(elapsed),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 10.sp,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp),
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                GroupCallPictures(
                    peerPubKeys = otherMembers,
                    size = 48.dp,
                    accountViewModel = accountViewModel,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatDuration(elapsed),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                )
            }
        }
    }
}
