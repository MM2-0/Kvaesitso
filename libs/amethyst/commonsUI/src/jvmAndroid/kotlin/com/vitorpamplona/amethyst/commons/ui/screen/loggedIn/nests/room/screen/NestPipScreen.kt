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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nests.room.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nest_audio_dropped
import com.vitorpamplona.amethyst.commons.resources.nest_lower_hand
import com.vitorpamplona.amethyst.commons.resources.nest_mic_mute
import com.vitorpamplona.amethyst.commons.resources.nest_mic_unmute
import com.vitorpamplona.amethyst.commons.resources.nest_reconnecting
import com.vitorpamplona.amethyst.commons.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.BroadcastUiState
import com.vitorpamplona.amethyst.commons.viewmodels.ConnectionUiState
import com.vitorpamplona.amethyst.commons.viewmodels.NestUiState
import com.vitorpamplona.amethyst.commons.viewmodels.NestViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.RoomMember

/**
 * Compact PIP layout. Renders three things, in priority order:
 *
 *   1. Whoever is talking right now (or — if no one is — the first
 *      handful of on-stage participants so the user still sees who
 *      is on stage). Active speakers carry a green ring whose width
 *      tracks live audio level, mirroring the full-screen
 *      [com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.stage.StageGrid].
 *      In a 30-speaker room this means PIP focuses on whoever is
 *      actually delivering audio rather than showing the same four
 *      faces while someone else talks off-screen.
 *   2. The user's own status (broadcasting / muted / hand-raised),
 *      so glancing at the PIP answers "am I muted?" without
 *      expanding back to the full screen.
 *   3. Connection drops — if the wrapper is reconnecting or the
 *      session failed, the avatars dim and a centered status caption
 *      surfaces the new state instead of leaving the user staring at
 *      frozen avatars.
 *
 * Mute / leave actions live in the system PIP overlay as
 * [android.app.RemoteAction]s — see
 * [com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.activity.NestActivity.buildPipActions].
 */
@Composable
fun NestPipScreen(
    title: String?,
    onStage: List<RoomMember>,
    ui: NestUiState,
    viewModel: NestViewModel,
    handRaised: Boolean,
    accountViewModel: AccountViewModel,
) {
    val audioLevels by viewModel.audioLevels.collectAsState()

    // Active speakers in stage order. We deliberately *don't* show
    // off-stage speakers in PIP — host kicks / role drops would race
    // with the speakingNow set and produce ghost avatars.
    val onStageNowSpeaking =
        remember(onStage, ui.speakingNow) {
            onStage.filter { it.pubkey in ui.speakingNow }
        }
    val focused =
        if (onStageNowSpeaking.isNotEmpty()) {
            onStageNowSpeaking.take(MAX_PIP_AVATARS)
        } else {
            // Nothing is being delivered right now. Keep the PIP visible
            // (rather than blank) by falling back to the first on-stage
            // participants — so the user still sees who's on stage
            // during silence.
            onStage.take(MAX_PIP_AVATARS)
        }
    val isFocusMode = onStageNowSpeaking.size == 1
    val avatarSize = if (isFocusMode) FOCUS_AVATAR_SIZE else GRID_AVATAR_SIZE

    val connection = ui.connection
    val showReconnecting = connection is ConnectionUiState.Reconnecting
    val showFailed = connection is ConnectionUiState.Failed
    val dimBody = showReconnecting || showFailed

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(8.dp).align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.alpha(if (dimBody) DIMMED_ALPHA else 1f),
            ) {
                focused.forEach { participant ->
                    PipAvatar(
                        pubkey = participant.pubkey,
                        size = avatarSize,
                        isSpeaking = participant.pubkey in ui.speakingNow,
                        isConnecting = participant.pubkey in ui.connectingSpeakers,
                        audioLevel = audioLevels[participant.pubkey] ?: 0f,
                        accountViewModel = accountViewModel,
                    )
                }
            }
            PipSelfStatus(
                broadcast = ui.broadcast,
                handRaised = handRaised,
            )
        }

        // Connection-state caption sits above the dimmed body so a
        // dropped session is obvious without expanding the PIP. Failed
        // uses the error palette; Reconnecting reads as transient.
        if (showReconnecting) {
            Text(
                text = stringRes(Res.string.nest_reconnecting),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.Center),
            )
        } else if (showFailed) {
            Text(
                text = stringRes(Res.string.nest_audio_dropped),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/**
 * Compact speaker tile. Same tri-state ring contract as the full-screen
 * stage cell ([com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.stage.StageGrid])
 * but tuned for ~35-56dp avatars: smaller ring, no outer glow halo
 * (which would extend past the PIP bounds), no badges (no room).
 */
@Composable
private fun PipAvatar(
    pubkey: String,
    size: Dp,
    isSpeaking: Boolean,
    isConnecting: Boolean,
    audioLevel: Float,
    accountViewModel: AccountViewModel,
) {
    val clampedLevel = audioLevel.coerceIn(0f, 1f)
    val targetRingColor = if (isSpeaking) PIP_SPEAKING_COLOR else Color.Transparent
    val targetRingWidth =
        if (isSpeaking) {
            PIP_RING_WIDTH + (clampedLevel * (PIP_MAX_RING_WIDTH - PIP_RING_WIDTH).value).dp
        } else {
            0.dp
        }
    val animatedRingWidth by animateDpAsState(targetValue = targetRingWidth, label = "pip-ring-width")
    val animatedRingColor by animateColorAsState(targetValue = targetRingColor, label = "pip-ring-color")

    Box(contentAlignment = Alignment.Center) {
        ClickableUserPicture(
            baseUserHex = pubkey,
            size = size,
            accountViewModel = accountViewModel,
            modifier = Modifier.border(animatedRingWidth, animatedRingColor, CircleShape),
        )
        if (isConnecting) {
            // Pre-roll spinner: same contract as the stage cell — the
            // speaker has an open subscription but no audio frame has
            // arrived yet. Sized inside the avatar so it doesn't push
            // the row layout.
            CircularProgressIndicator(
                modifier = Modifier.size(size - 6.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Self-state strip rendered below the speaker row. Kept invisible
 * unless there's something the user might want to know — pure-audience
 * listeners with no hand raised see no row at all (no clutter).
 *
 * Mic state mirrors the full-screen action bar: green when actively
 * publishing, error red when broadcasting muted. Hand-raised is shown
 * in the primary palette so it doesn't compete with the mute warning.
 */
@Composable
private fun PipSelfStatus(
    broadcast: BroadcastUiState,
    handRaised: Boolean,
) {
    val broadcasting = broadcast as? BroadcastUiState.Broadcasting
    if (broadcasting == null && !handRaised) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (broadcasting != null) {
            val isMuted = broadcasting.isMuted
            Icon(
                symbol = if (isMuted) MaterialSymbols.MicOff else MaterialSymbols.Mic,
                contentDescription =
                    stringRes(if (isMuted) Res.string.nest_mic_mute else Res.string.nest_mic_unmute),
                tint = if (isMuted) MaterialTheme.colorScheme.error else PIP_SPEAKING_COLOR,
                modifier = Modifier.size(STATUS_ICON_SIZE),
            )
        }
        if (handRaised) {
            Icon(
                symbol = MaterialSymbols.PanTool,
                contentDescription = stringRes(Res.string.nest_lower_hand),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(STATUS_ICON_SIZE),
            )
        }
    }
}

private const val MAX_PIP_AVATARS = 4

// Avatar diameter for the typical multi-speaker / silence case. 35dp
// matches the previous PIP layout so the visual change is "rings start
// pulsing" rather than a layout jump.
private val GRID_AVATAR_SIZE = 35.dp

// Used when exactly one person is talking — bumps the avatar up so
// the focus mode reads as "this person, right now." Kept under
// half the typical PIP height so the title + status row still fit.
private val FOCUS_AVATAR_SIZE = 56.dp

private val PIP_RING_WIDTH = 2.dp
private val PIP_MAX_RING_WIDTH = 4.dp

// Same green as NEST_SPEAKING_COLOR in the full-screen StageGrid
// (tailwind green-500). Duplicated here rather than exporting the
// other constant so the two screens stay decoupled.
private val PIP_SPEAKING_COLOR = Color(0xFF22C55E)

private val STATUS_ICON_SIZE = 14.dp

private const val DIMMED_ALPHA = 0.4f
