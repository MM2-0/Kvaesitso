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

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.call_calling
import com.vitorpamplona.amethyst.commons.resources.call_duration_hours_minutes_seconds
import com.vitorpamplona.amethyst.commons.resources.call_duration_minutes_seconds
import com.vitorpamplona.amethyst.commons.ui.note.BaseUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun VideoRenderer(
    videoTrack: VideoTrack,
    eglBase: org.webrtc.EglBase?,
    modifier: Modifier = Modifier,
    mirror: Boolean = false,
    zOrderMediaOverlay: Boolean = false,
) {
    // Track the current track so the update block can swap sinks when the
    // track reference changes (e.g. after renegotiation).
    val currentTrack = remember { mutableStateOf(videoTrack) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                setMirror(mirror)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setZOrderMediaOverlay(zOrderMediaOverlay)
                eglBase?.eglBaseContext?.let { init(it, null) }
                videoTrack.addSink(this)
            }
        },
        update = { renderer ->
            renderer.setMirror(mirror)
            if (currentTrack.value !== videoTrack) {
                try {
                    currentTrack.value.removeSink(renderer)
                } catch (_: Exception) {
                }
                videoTrack.addSink(renderer)
                currentTrack.value = videoTrack
            }
        },
        onRelease = { renderer ->
            try {
                currentTrack.value.removeSink(renderer)
            } catch (_: Exception) {
            }
            renderer.release()
        },
    )
}

@Composable
fun PeerVideoGrid(
    peerPubKeys: Set<String>,
    pendingPeerPubKeys: Set<String>,
    remoteVideoTracks: Map<String, VideoTrack>,
    activePeerVideos: Set<String>,
    eglBase: org.webrtc.EglBase?,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
) {
    val peers = remember(peerPubKeys) { peerPubKeys.toList() }

    if (peers.size == 1) {
        val peerKey = peers[0]
        val track = remoteVideoTracks[peerKey]
        val isPending = peerKey in pendingPeerPubKeys
        if (track != null && peerKey in activePeerVideos && !isPending) {
            VideoRenderer(
                videoTrack = track,
                eglBase = eglBase,
                modifier = modifier,
                mirror = false,
            )
        } else {
            PeerAvatarCell(
                peerPubKey = peerKey,
                accountViewModel = accountViewModel,
                statusText = if (isPending) stringRes(Res.string.call_calling) else null,
                modifier = modifier,
            )
        }
    } else {
        val columns =
            when {
                peers.size <= 2 -> 1
                else -> 2
            }

        Column(modifier = modifier) {
            peers.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    row.forEach { peerKey ->
                        val track = remoteVideoTracks[peerKey]
                        val isPending = peerKey in pendingPeerPubKeys
                        val cellModifier = Modifier.weight(1f).fillMaxHeight()
                        if (track != null && peerKey in activePeerVideos && !isPending) {
                            VideoRenderer(
                                videoTrack = track,
                                eglBase = eglBase,
                                modifier = cellModifier,
                                mirror = false,
                            )
                        } else {
                            PeerAvatarCell(
                                peerPubKey = peerKey,
                                accountViewModel = accountViewModel,
                                statusText = if (isPending) stringRes(Res.string.call_calling) else null,
                                modifier = cellModifier,
                            )
                        }
                    }
                    repeat(columns - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun PeerAvatarCell(
    peerPubKey: String,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
    statusText: String? = null,
) {
    Box(
        modifier = modifier.background(Color.DarkGray),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LoadUser(baseUserHex = peerPubKey) { user ->
                if (user != null) {
                    ClickableUserPicture(
                        baseUser = user,
                        size = 80.dp,
                        accountViewModel = accountViewModel,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    UsernameDisplay(
                        baseUser = user,
                        accountViewModel = accountViewModel,
                        fontWeight = FontWeight.Bold,
                        textColor = Color.White,
                    )
                }
            }
            if (statusText != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = statusText,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
fun GroupCallPictures(
    peerPubKeys: Set<String>,
    size: Dp,
    accountViewModel: AccountViewModel,
) {
    val userList = remember(peerPubKeys) { peerPubKeys.toList() }
    val displayCount = minOf(userList.size, 4)
    val remaining = userList.size - displayCount

    when (userList.size) {
        0 -> {}

        1 -> {
            LoadUser(baseUserHex = userList[0]) { user ->
                if (user != null) {
                    ClickableUserPicture(
                        baseUser = user,
                        size = size,
                        accountViewModel = accountViewModel,
                    )
                }
            }
        }

        else -> {
            Box(Modifier.size(size), contentAlignment = Alignment.TopEnd) {
                when (displayCount) {
                    2 -> {
                        BaseUserPicture(
                            baseUserHex = userList[0],
                            size = size.div(1.5f),
                            accountViewModel = accountViewModel,
                            outerModifier = Modifier.size(size.div(1.5f)).align(Alignment.CenterStart),
                        )
                        BaseUserPicture(
                            baseUserHex = userList[1],
                            size = size.div(1.5f),
                            accountViewModel = accountViewModel,
                            outerModifier = Modifier.size(size.div(1.5f)).align(Alignment.CenterEnd),
                        )
                    }

                    3 -> {
                        BaseUserPicture(
                            baseUserHex = userList[0],
                            size = size.div(1.8f),
                            accountViewModel = accountViewModel,
                            outerModifier = Modifier.size(size.div(1.8f)).align(Alignment.BottomStart),
                        )
                        BaseUserPicture(
                            baseUserHex = userList[1],
                            size = size.div(1.8f),
                            accountViewModel = accountViewModel,
                            outerModifier = Modifier.size(size.div(1.8f)).align(Alignment.TopCenter),
                        )
                        BaseUserPicture(
                            baseUserHex = userList[2],
                            size = size.div(1.8f),
                            accountViewModel = accountViewModel,
                            outerModifier = Modifier.size(size.div(1.8f)).align(Alignment.BottomEnd),
                        )
                    }

                    else -> {
                        BaseUserPicture(
                            baseUserHex = userList[0],
                            size = size.div(2f),
                            accountViewModel = accountViewModel,
                            outerModifier = Modifier.size(size.div(2f)).align(Alignment.BottomStart),
                        )
                        BaseUserPicture(
                            baseUserHex = userList[1],
                            size = size.div(2f),
                            accountViewModel = accountViewModel,
                            outerModifier = Modifier.size(size.div(2f)).align(Alignment.TopStart),
                        )
                        BaseUserPicture(
                            baseUserHex = userList[2],
                            size = size.div(2f),
                            accountViewModel = accountViewModel,
                            outerModifier = Modifier.size(size.div(2f)).align(Alignment.BottomEnd),
                        )
                        if (remaining > 0) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(size.div(2f))
                                        .align(Alignment.TopEnd)
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            CircleShape,
                                        ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "+$remaining",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = (size.value / 5).sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        } else {
                            BaseUserPicture(
                                baseUserHex = userList[3],
                                size = size.div(2f),
                                accountViewModel = accountViewModel,
                                outerModifier = Modifier.size(size.div(2f)).align(Alignment.TopEnd),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GroupCallNames(
    peerPubKeys: Set<String>,
    accountViewModel: AccountViewModel,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val userList = remember(peerPubKeys) { peerPubKeys.toList() }

    when (userList.size) {
        0 -> {}

        1 -> {
            LoadUser(baseUserHex = userList[0]) { user ->
                if (user != null) {
                    UsernameDisplay(
                        baseUser = user,
                        accountViewModel = accountViewModel,
                        fontWeight = FontWeight.Bold,
                        textColor = textColor,
                    )
                }
            }
        }

        else -> {
            val displayCount = minOf(userList.size, 2)
            val remaining = userList.size - displayCount

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (i in 0 until displayCount) {
                    if (i > 0) {
                        Text(
                            text = ", ",
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                        )
                    }
                    LoadUser(baseUserHex = userList[i]) { user ->
                        if (user != null) {
                            UsernameDisplay(
                                baseUser = user,
                                accountViewModel = accountViewModel,
                                fontWeight = FontWeight.Bold,
                                textColor = textColor,
                            )
                        }
                    }
                }
                if (remaining > 0) {
                    Text(
                        text = " +$remaining",
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * Elapsed call time as a clock: `05:23`, and `1:05:23` once the call passes an hour.
 *
 * Hours are not folded into the minutes field — the previous version rendered a 65-minute call
 * as "65:12".
 *
 * The pattern comes from a string resource rather than a literal so the separator and field
 * order stay the translator's, and so the numbers are formatted with the locale Compose is
 * configured with. `String.format` without an explicit locale follows the JVM-wide default,
 * which is not the same thing in an app that has its own language picker.
 */
@Composable
fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60

    return if (hours > 0) {
        stringRes(Res.string.call_duration_hours_minutes_seconds, hours, mins, secs)
    } else {
        stringRes(Res.string.call_duration_minutes_seconds, mins, secs)
    }
}

@Composable
fun KeepScreenOn() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
