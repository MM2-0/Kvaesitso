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
package com.vitorpamplona.amethyst.commons.nipACWebRtcCalls.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn

private const val LABEL_HANG_UP = "Hang up"
private const val LABEL_CALLING = "Calling…"

// ---- Shared building blocks for previews ----

@Composable
private fun PreviewAvatar(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Icon(
        MaterialSymbols.Person,
        contentDescription = null,
        modifier = modifier.size(120.dp),
        tint = tint,
    )
}

@Composable
private fun PreviewGroupAvatars(
    count: Int,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val halfSize = 60.dp
    Box(Modifier.size(120.dp)) {
        repeat(minOf(count, 4)) { i ->
            val alignment =
                when (i) {
                    0 -> Alignment.BottomStart
                    1 -> Alignment.TopStart
                    2 -> Alignment.BottomEnd
                    else -> Alignment.TopEnd
                }
            if (i < 3 || count <= 4) {
                Icon(
                    MaterialSymbols.Person,
                    contentDescription = null,
                    modifier = Modifier.size(halfSize).align(alignment),
                    tint = tint,
                )
            } else {
                Box(
                    modifier =
                        Modifier
                            .size(halfSize)
                            .align(alignment)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+${count - 3}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewCallControls(
    isMuted: Boolean = false,
    isVideoEnabled: Boolean = false,
    audioRoute: String = "earpiece",
    showAddParticipant: Boolean = false,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            IconButton(onClick = {}, modifier = Modifier.size(56.dp)) {
                Icon(
                    symbol = if (isMuted) MaterialSymbols.MicOff else MaterialSymbols.Mic,
                    contentDescription = "Mute",
                    tint = if (isMuted) Color.Red else Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
            IconButton(onClick = {}, modifier = Modifier.size(56.dp)) {
                Icon(
                    symbol = if (isVideoEnabled) MaterialSymbols.Videocam else MaterialSymbols.VideocamOff,
                    contentDescription = "Camera",
                    tint = if (!isVideoEnabled) Color.Red else Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
            IconButton(onClick = {}, modifier = Modifier.size(56.dp)) {
                Icon(
                    symbol =
                        when (audioRoute) {
                            "speaker" -> MaterialSymbols.AutoMirrored.VolumeUp
                            "bluetooth" -> MaterialSymbols.BluetoothAudio
                            else -> MaterialSymbols.AutoMirrored.VolumeOff
                        },
                    contentDescription = "Audio route",
                    tint =
                        when (audioRoute) {
                            "speaker" -> Color.Cyan
                            "bluetooth" -> Color(0xFF2196F3)
                            else -> Color.White
                        },
                    modifier = Modifier.size(28.dp),
                )
            }
            if (showAddParticipant) {
                IconButton(onClick = {}, modifier = Modifier.size(56.dp)) {
                    Icon(
                        MaterialSymbols.PersonAdd,
                        contentDescription = "Add participant",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        FloatingActionButton(
            onClick = {},
            containerColor = Color.Red,
            shape = CircleShape,
            modifier = Modifier.size(64.dp),
        ) {
            Icon(MaterialSymbols.CallEnd, LABEL_HANG_UP, tint = Color.White, modifier = Modifier.size(32.dp))
        }
    }
}

// ---- 1. Offering (Calling...) ----

@Composable
private fun PreviewCallInProgress(
    name: String,
    statusText: String,
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
            PreviewAvatar()
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = name, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = statusText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(48.dp))
            FloatingActionButton(
                onClick = {},
                containerColor = Color.Red,
                shape = CircleShape,
                modifier = Modifier.size(64.dp),
            ) {
                Icon(MaterialSymbols.CallEnd, LABEL_HANG_UP, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PreviewCallingScreen() {
    ThemeComparisonColumn {
        PreviewCallInProgress("Alice", LABEL_CALLING)
    }
}

// ---- 2. Offering group call ----

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PreviewCallingGroupScreen() {
    ThemeComparisonColumn {
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
                PreviewGroupAvatars(3)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Alice, Bob +1", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(LABEL_CALLING, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(48.dp))
                FloatingActionButton(
                    onClick = {},
                    containerColor = Color.Red,
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(MaterialSymbols.CallEnd, LABEL_HANG_UP, tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

// ---- 3. Connecting ----

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PreviewConnectingScreen() {
    ThemeComparisonColumn {
        PreviewCallInProgress("Alice", "Connecting\u2026")
    }
}

// ---- 4. Incoming voice call ----

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PreviewIncomingVoiceCallScreen() {
    ThemeComparisonColumn {
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
                PreviewAvatar()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Bob", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Incoming voice call\u2026", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(48.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                    FloatingActionButton(onClick = {}, containerColor = Color.Red, shape = CircleShape, modifier = Modifier.size(64.dp)) {
                        Icon(MaterialSymbols.CallEnd, "Reject", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    FloatingActionButton(onClick = {}, containerColor = Color(0xFF4CAF50), shape = CircleShape, modifier = Modifier.size(64.dp)) {
                        Icon(MaterialSymbols.Call, "Accept", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}

// ---- 5. Incoming video call ----

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PreviewIncomingVideoCallScreen() {
    ThemeComparisonColumn {
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
                PreviewAvatar()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Bob", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Incoming video call\u2026", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(48.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                    FloatingActionButton(onClick = {}, containerColor = Color.Red, shape = CircleShape, modifier = Modifier.size(64.dp)) {
                        Icon(MaterialSymbols.CallEnd, "Reject", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    FloatingActionButton(onClick = {}, containerColor = Color(0xFF4CAF50), shape = CircleShape, modifier = Modifier.size(64.dp)) {
                        Icon(MaterialSymbols.Call, "Accept", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}

// ---- 6. Incoming group call ----

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PreviewIncomingGroupCallScreen() {
    ThemeComparisonColumn {
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
                PreviewGroupAvatars(4)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Bob, Alice +2", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Incoming voice call\u2026", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(48.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                    FloatingActionButton(onClick = {}, containerColor = Color.Red, shape = CircleShape, modifier = Modifier.size(64.dp)) {
                        Icon(MaterialSymbols.CallEnd, "Reject", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    FloatingActionButton(onClick = {}, containerColor = Color(0xFF4CAF50), shape = CircleShape, modifier = Modifier.size(64.dp)) {
                        Icon(MaterialSymbols.Call, "Accept", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}

// ---- 7. Connected P2P voice call ----

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PreviewConnectedCallScreen() {
    ThemeComparisonColumn {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                PreviewAvatar(tint = Color.White.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Alice", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text("02:45", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
            }

            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PreviewCallControls(showAddParticipant = true)
            }
        }
    }
}

// ---- 8. Connected P2P muted + video + speaker ----

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PreviewConnectedCallMuted() {
    ThemeComparisonColumn {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                PreviewAvatar(tint = Color.White.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Alice", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text("05:12", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
            }

            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PreviewCallControls(
                    isMuted = true,
                    isVideoEnabled = true,
                    audioRoute = "speaker",
                    showAddParticipant = true,
                )
            }
        }
    }
}

// ---- 9. Connected group call with pending peers ----

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PreviewConnectedGroupCallWithPending() {
    ThemeComparisonColumn {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                PreviewGroupAvatars(3, tint = Color.White.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Alice, Bob +1", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text("01:30", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Waiting for others to join\u2026", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
            }

            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PreviewCallControls(showAddParticipant = true)
            }
        }
    }
}

// ---- 10. Connected with Bluetooth audio ----

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PreviewConnectedCallBluetooth() {
    ThemeComparisonColumn {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                PreviewAvatar(tint = Color.White.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Alice", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text("10:03", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
            }

            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PreviewCallControls(audioRoute = "bluetooth", showAddParticipant = true)
            }
        }
    }
}

// ---- 11. Call ended ----

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PreviewCallEndedScreen() {
    ThemeComparisonColumn {
        PreviewCallInProgress("Alice", "Call ended")
    }
}

// ---- 12. Group call with 5+ members (shows +N badge) ----

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PreviewGroupCallLargeGroup() {
    ThemeComparisonColumn {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                PreviewGroupAvatars(6, tint = Color.White.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Alice, Bob +4", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text("03:21", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
            }

            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PreviewCallControls(showAddParticipant = true)
            }
        }
    }
}

// ---- 13. PiP mode (small) ----

@Preview(showBackground = true, widthDp = 200, heightDp = 120)
@Composable
fun PreviewPipCallUI() {
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
            Icon(MaterialSymbols.Person, contentDescription = null, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(LABEL_CALLING, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---- 14. PiP connected (small) ----

@Preview(showBackground = true, widthDp = 200, heightDp = 120)
@Composable
fun PreviewPipConnectedCallUI() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(MaterialSymbols.Person, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.White.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(4.dp))
            Text("02:45", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
        }
    }
}
