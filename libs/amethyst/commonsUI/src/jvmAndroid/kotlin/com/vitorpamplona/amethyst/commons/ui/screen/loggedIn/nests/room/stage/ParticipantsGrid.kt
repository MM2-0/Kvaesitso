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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nests.room.stage

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nest_audience_empty
import com.vitorpamplona.amethyst.commons.resources.nest_listener_count
import com.vitorpamplona.amethyst.commons.resources.nest_live_chip
import com.vitorpamplona.amethyst.commons.resources.nest_raise_hand
import com.vitorpamplona.amethyst.commons.resources.nest_role_host
import com.vitorpamplona.amethyst.commons.resources.nest_role_moderator
import com.vitorpamplona.amethyst.commons.resources.nest_stage
import com.vitorpamplona.amethyst.commons.resources.nest_stage_empty
import com.vitorpamplona.amethyst.commons.resources.nest_state_mic_open
import com.vitorpamplona.amethyst.commons.resources.nest_state_muted
import com.vitorpamplona.amethyst.commons.resources.nest_state_speaking
import com.vitorpamplona.amethyst.commons.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.RoomMember
import com.vitorpamplona.amethyst.commons.viewmodels.RoomReaction
import com.vitorpamplona.amethyst.commons.viewmodels.RoomZap
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ROLE
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

private val STAGE_CELL_MIN = 80.dp
private val STAGE_AVATAR = 75.dp
private val AUDIENCE_CELL_MIN = 80.dp
private val AUDIENCE_AVATAR = 75.dp
private val GRID_SPACING = 8.dp
private val AVATAR_RING_WIDTH = 3.dp

// Upper bound for the live "voice" halo. Speaking with a peak audio
// level of 1.0 grows the ring from AVATAR_RING_WIDTH to this value;
// quieter voices land somewhere in between.
private val MAX_RING_WIDTH = 7.dp

// Soft outer glow that scales with audio level. Drawn behind the
// avatar via drawBehind so it doesn't change layout bounds; alpha
// fades to 0 when not speaking.
private val MAX_GLOW_RADIUS = 12.dp

// Detached green ring drawn around a speaking avatar with a clear
// gap to the profile picture, so the "is talking" signal reads from
// across the room without overpowering the face. Only rendered while
// `isSpeaking` is true; pulses with the live audio level. Drawn via
// drawBehind to avoid affecting layout bounds — the gap + ring fit
// inside the GRID_SPACING between cells.
private val OUTER_RING_GAP = 5.dp
private val OUTER_RING_WIDTH = 2.5.dp
private val OUTER_RING_MAX_WIDTH = 4.dp

// Cap badge sizes so they stay legible without dominating the avatar
// at 100.dp. The 0.42 ratio was tuned for ~48.dp avatars (giving
// ~20.dp badges); without a cap, scaling to 100.dp produces 42.dp
// badges that compete with the face for attention.
private val MAX_BADGE_SIZE = 22.dp

private val STAGE_CARD_SHAPE = RoundedCornerShape(20.dp)
private val STAGE_CARD_PADDING_HORIZONTAL = 12.dp
private val STAGE_CARD_PADDING_VERTICAL = 12.dp

// Cell min sits 4.dp under the avatar diameter so 4 columns fit on a
// 411.dp phone (Pixel 6/7/8). The 100.dp avatar overflows ~2.dp into
// the 8.dp horizontal arrangement on each side, leaving a visible
// gap between neighbors. Names below ellipsize to the cell width,
// which is fine since they're short display handles.

// Two rows visible by default. Each cell is roughly avatar + name +
// reaction headroom; with a 100dp avatar one row lifts to ~140dp, so
// two rows + label/padding lands near 320dp — tall enough to show a
// handful of speakers without scrolling but small enough to leave the
// chat / audience tab the majority of the viewport.
private val STAGE_MAX_HEIGHT = 320.dp

/**
 * Vertical adaptive grid for the on-stage section. Used as the
 * always-visible header strip in the room layout: speakers flow into
 * as many columns as the screen width allows, wrapping to a new row
 * once full. Capped at [STAGE_MAX_HEIGHT] so a 30-speaker room can
 * scroll inside the strip without pushing the tabs / chat below the
 * fold.
 *
 * When [members] is empty, renders an "Waiting for speakers…" hint
 * instead so the strip stays visually anchored.
 *
 * [bottomBar] is rendered inside the same Surface, below the grid —
 * used to anchor speaker controls to the stage card so they only
 * appear when the local user is on stage.
 */
@Composable
fun StageGrid(
    members: List<RoomMember>,
    speakingNow: ImmutableSet<String>,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
    audioLevels: Map<String, Float> = emptyMap(),
    reactionsByPubkey: Map<String, List<RoomReaction>> = emptyMap(),
    zapsByPubkey: Map<String, List<RoomZap>> = emptyMap(),
    connectingSpeakers: ImmutableSet<String> = persistentSetOf(),
    onLongPressParticipant: ((String) -> Unit)? = null,
    onTapParticipant: ((String) -> Unit)? = null,
    myPubkey: String? = null,
    onTapSelf: (() -> Unit)? = null,
    listenerCount: Int = 0,
    bottomBar: (@Composable () -> Unit)? = null,
) {
    // Keep members in their stable arrival order. Reordering on the
    // speaking state made a member jump to the front when they started
    // talking and slide back ~250ms after they stopped — a constant
    // shuffle in any active room. The speaking state is already shown
    // in-place via MemberCell's mic badge / audio-level ring, so no
    // reordering is needed.
    // Wrap the strip in a tonal card so the active speakers visually
    // live in their own zone, separated from the chat / audience tab
    // below. surfaceContainerLow is a quiet step up from the screen
    // background — readable in both light and dark.
    Surface(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = STAGE_CARD_SHAPE,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = STAGE_CARD_PADDING_HORIZONTAL,
                        vertical = STAGE_CARD_PADDING_VERTICAL,
                    ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringRes(Res.string.nest_stage),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text =
                        pluralStringRes(
                            Res.plurals.nest_listener_count,
                            listenerCount,
                            listenerCount,
                        ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
                LiveChip()
            }
            if (members.isEmpty()) {
                EmptyStageHint()
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(STAGE_CELL_MIN),
                    modifier = Modifier.fillMaxWidth().heightIn(max = STAGE_MAX_HEIGHT),
                    horizontalArrangement = Arrangement.spacedBy(GRID_SPACING),
                    verticalArrangement = Arrangement.spacedBy(GRID_SPACING),
                ) {
                    items(items = members, key = { it.pubkey }) { member ->
                        val isSelf = myPubkey != null && member.pubkey == myPubkey
                        MemberCell(
                            member = member,
                            avatarSize = STAGE_AVATAR,
                            isSpeaking = member.pubkey in speakingNow,
                            audioLevel = audioLevels[member.pubkey] ?: 0f,
                            isConnecting = member.pubkey in connectingSpeakers,
                            showMicBadge = true,
                            reactions = reactionsByPubkey[member.pubkey].orEmpty(),
                            zaps = zapsByPubkey[member.pubkey].orEmpty(),
                            accountViewModel = accountViewModel,
                            onLongPressParticipant = onLongPressParticipant,
                            onTapParticipant = if (isSelf) null else onTapParticipant,
                            isSelf = isSelf,
                            onTapSelf = if (isSelf) onTapSelf else null,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
            bottomBar?.invoke()
        }
    }
}

@Composable
private fun LiveChip() {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Text(
            text = stringRes(Res.string.nest_live_chip),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/**
 * Idle "Waiting for speakers…" placeholder for [StageGrid]. Pairs an
 * hourglass glyph with the existing copy so the empty state reads as
 * intentional rather than as a layout glitch.
 */
@Composable
private fun EmptyStageHint() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            symbol = MaterialSymbols.HourglassEmpty,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(32.dp),
        )
        Text(
            text = stringRes(Res.string.nest_stage_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Vertical adaptive grid for the audience tab. Sized to fill the
 * remaining vertical space its parent gives it (`Modifier.weight(1f)`)
 * so a room with 1,000 listeners scrolls efficiently — the lazy grid
 * only composes the rows currently visible.
 *
 * Audience cells deliberately omit the speaking ring, mic badge and
 * connecting spinner: only members with a live broadcast subscription
 * earn those, and audience by definition aren't broadcasting. Hand
 * badge still renders because the audience is the hand-raise queue.
 */
@Composable
fun AudienceGrid(
    members: List<RoomMember>,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
    onLongPressParticipant: ((String) -> Unit)? = null,
    onTapParticipant: ((String) -> Unit)? = null,
    myPubkey: String? = null,
    reactionsByPubkey: Map<String, List<RoomReaction>> = emptyMap(),
    zapsByPubkey: Map<String, List<RoomZap>> = emptyMap(),
) {
    if (members.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringRes(Res.string.nest_audience_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    // Pull raised hands to the top of the audience so a moderator
    // approving the queue isn't scrolling. Stable sort keeps everyone
    // else in their incoming order.
    val sortedMembers =
        remember(members) {
            members.sortedBy { if (it.handRaised) 0 else 1 }
        }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(AUDIENCE_CELL_MIN),
        modifier = modifier.fillMaxSize().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(GRID_SPACING),
        verticalArrangement = Arrangement.spacedBy(GRID_SPACING),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(vertical = 8.dp),
    ) {
        items(items = sortedMembers, key = { it.pubkey }) { member ->
            MemberCell(
                member = member,
                avatarSize = AUDIENCE_AVATAR,
                isSpeaking = false,
                audioLevel = 0f,
                isConnecting = false,
                showMicBadge = false,
                reactions = reactionsByPubkey[member.pubkey].orEmpty(),
                zaps = zapsByPubkey[member.pubkey].orEmpty(),
                accountViewModel = accountViewModel,
                onLongPressParticipant = onLongPressParticipant,
                onTapParticipant = onTapParticipant,
                isSelf = myPubkey != null && member.pubkey == myPubkey,
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun MemberCell(
    member: RoomMember,
    avatarSize: Dp,
    isSpeaking: Boolean,
    audioLevel: Float,
    isConnecting: Boolean,
    showMicBadge: Boolean,
    reactions: List<RoomReaction>,
    accountViewModel: AccountViewModel,
    onLongPressParticipant: ((String) -> Unit)?,
    zaps: List<RoomZap> = emptyList(),
    isSelf: Boolean = false,
    onTapSelf: (() -> Unit)? = null,
    onTapParticipant: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val mutedRingColor = MaterialTheme.colorScheme.error
    val clampedLevel = audioLevel.coerceIn(0f, 1f)
    // Tri-state ring around the avatar so a glance distinguishes:
    //   - green ring: actively speaking right now
    //   - red ring:   on stage and broadcasting but mic flagged muted
    //   - no ring:    idle, audience, or unknown mute state
    //
    // Both color and width crossfade so going idle → speaking → idle
    // doesn't snap; the color animates from Transparent through the
    // speaking green and the width tracks the live peak amplitude.
    // A speaker on stage with mic-mute on emits kind-10312 with
    // `publishing=0, muted=1` (deployed nostrnests semantics — see
    // EGG-04 / NestRoomPresencePublisher), so the muted ring must NOT
    // gate on `publishing`; muting would otherwise hide the very
    // indicator it was supposed to surface.
    val targetRingColor =
        when {
            isSpeaking -> NEST_SPEAKING_COLOR
            showMicBadge && member.muted == true -> mutedRingColor
            else -> Color.Transparent
        }
    val targetRingWidth =
        when {
            isSpeaking -> AVATAR_RING_WIDTH + (clampedLevel * (MAX_RING_WIDTH - AVATAR_RING_WIDTH).value).dp
            targetRingColor != Color.Transparent -> AVATAR_RING_WIDTH
            else -> 0.dp
        }
    val animatedRingWidth by animateDpAsState(targetValue = targetRingWidth, label = "speaker-ring-width")
    val animatedRingColor by animateColorAsState(targetValue = targetRingColor, label = "speaker-ring-color")
    // Soft outer halo behind the avatar, scaled by audio level. Drawn
    // via drawBehind on a transparent overlay so it doesn't push the
    // cell layout out — the glow extends past the circle but never
    // affects neighbour measurement. Capped so a loud peak doesn't
    // bleed into the next column.
    val animatedGlowAlpha by animateFloatAsState(
        targetValue = if (isSpeaking) (clampedLevel * 0.45f) else 0f,
        label = "speaker-ring-glow-alpha",
    )
    // Detached outer ring: stays at full opacity while speaking so the
    // signal is unmistakable, fades out when speech stops. Stroke width
    // tracks audio level for an extra liveness cue on top of the inner
    // border ring.
    val animatedOuterRingAlpha by animateFloatAsState(
        targetValue = if (isSpeaking) 1f else 0f,
        label = "speaker-outer-ring-alpha",
    )
    val animatedOuterRingWidth by animateDpAsState(
        targetValue =
            if (isSpeaking) {
                OUTER_RING_WIDTH + (clampedLevel * (OUTER_RING_MAX_WIDTH - OUTER_RING_WIDTH).value).dp
            } else {
                0.dp
            },
        label = "speaker-outer-ring-width",
    )
    // Reserve enough space around the avatar Box to fit the outer ring
    // and glow halo. Without this margin the rings clip against the
    // surrounding Surface / LazyVerticalGrid bounds (most visibly at
    // the top edge for the first row, where the stage card's rounded
    // corner cuts into the glow). The glow extends up to MAX_GLOW_RADIUS
    // past the avatar; the outer ring extends OUTER_RING_GAP +
    // OUTER_RING_MAX_WIDTH past it. Applied as an explicit outer-Box
    // size below rather than as inner padding so a width-constrained
    // grid cell doesn't squish the avatar horizontally into an oval
    // (cells are tight on width but free on height).
    val ringPadding =
        maxOf(MAX_GLOW_RADIUS.value, (OUTER_RING_GAP + OUTER_RING_MAX_WIDTH).value).dp
    val outerBoxSize = avatarSize + ringPadding * 2
    val avatarModifier =
        Modifier
            .border(animatedRingWidth, animatedRingColor, CircleShape)
            .let { if (member.absent) it.alpha(0.5f) else it }
    val user =
        remember(member.pubkey) {
            com.vitorpamplona.amethyst.commons.model.cache.LocalCache
                .getOrCreateUser(member.pubkey)
        }
    // Cache the long-click adapter per (pubkey, callback) tuple so a
    // recompose during a connectingSpeakers / speakingNow flip doesn't
    // allocate a fresh lambda for every cell.
    val onLongClick =
        remember(member.pubkey, onLongPressParticipant) {
            onLongPressParticipant?.let { cb -> { hex: String -> cb(hex) } }
        }
    // Self-cell tap shortcut: tap your own avatar to toggle mic-mute
    // when broadcasting. For non-self cells, [onTapParticipant] (if
    // provided) routes the tap to the per-participant context sheet —
    // used by the audience grid so a host can promote an audience
    // member to the stage with a single tap, matching the existing
    // long-press affordance.
    val onClick =
        when {
            isSelf && onTapSelf != null -> {
                remember(onTapSelf) { { _: String -> onTapSelf() } }
            }

            !isSelf && onTapParticipant != null -> {
                remember(member.pubkey, onTapParticipant) { { hex: String -> onTapParticipant(hex) } }
            }

            else -> {
                null
            }
        }
    val selfTint = if (isSelf) MaterialTheme.colorScheme.primary else Color.Unspecified
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        // Outer Box paints the glow halo + detached outer ring on a
        // canvas that's bigger than the avatar by [ringPadding]. Size
        // is set explicitly so the AvatarAndBadges child (which sizes
        // tight to the 75dp picture, keeping badge corner alignment
        // against the avatar circle rather than the padded area) is
        // centered without inheriting a width-only constraint from the
        // grid cell — that asymmetry was rendering the avatar as an
        // oval on cells narrower than [outerBoxSize].
        Box(
            modifier =
                Modifier
                    .size(outerBoxSize)
                    .drawBehind {
                        val avatarRadiusPx = avatarSize.toPx() / 2f
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        if (animatedGlowAlpha > 0.001f) {
                            val extra = MAX_GLOW_RADIUS.toPx() * clampedLevel
                            drawCircle(
                                color = NEST_SPEAKING_COLOR.copy(alpha = animatedGlowAlpha),
                                radius = avatarRadiusPx + extra,
                                center = Offset(cx, cy),
                            )
                        }
                        if (animatedOuterRingAlpha > 0.001f && animatedOuterRingWidth > 0.dp) {
                            val strokePx = animatedOuterRingWidth.toPx()
                            val ringRadius = avatarRadiusPx + OUTER_RING_GAP.toPx() + strokePx / 2f
                            drawCircle(
                                color = NEST_SPEAKING_COLOR.copy(alpha = animatedOuterRingAlpha),
                                radius = ringRadius,
                                center = Offset(cx, cy),
                                style = Stroke(width = strokePx),
                            )
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {
            AvatarAndBadges(
                member = member,
                avatarSize = avatarSize,
                accountViewModel = accountViewModel,
                avatarModifier = avatarModifier,
                onClick = onClick,
                onLongClick = onLongClick,
                isConnecting = isConnecting,
                showMicBadge = showMicBadge,
                isSpeaking = isSpeaking,
            )
            // Reactions overlay sits as a SIBLING of AvatarAndBadges
            // inside this fixed-size outer Box, NOT inside
            // AvatarAndBadges itself. AvatarAndBadges' inner Box
            // wraps content, so any change to the overlay's measured
            // size (chip appears / animates / disappears) would shift
            // the inner Box's centre and the badges aligned to its
            // corners would drift with it. The outer Box has an
            // explicit `.size(outerBoxSize)`, so adding the overlay
            // here can't change layout dimensions.
            //
            // Anchor the chip's bottom-RIGHT to the avatar's
            // bottom-right corner (small inward nudge from the outer
            // Box edge so it clears the ring/glow padding). The chip
            // extends LEFTWARD inside the cell as content widens
            // (single emoji vs `×N` count). Earlier `BottomStart`
            // experiment anchored the chip's left edge at the same
            // corner — looked great for a fixed-width chip but bled
            // straight into the next column once a real emoji was
            // rendered, because the cell was only ~100 dp wide and
            // the chip extended out 40+ dp to the right.
            if (reactions.isNotEmpty()) {
                SpeakerReactionOverlay(
                    reactions = reactions,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = -ringPadding + 6.dp, y = -ringPadding + 6.dp),
                )
            }
            // Zaps animate just above the avatar, TOP-CENTER — the
            // only badge-free anchor: the role badge sits TopStart,
            // the hand-raise badge TopEnd, the mic badge BottomCenter
            // and the reaction overlay BottomEnd. Anchoring TopEnd
            // would collide with the hand-raise badge, and now that
            // zaps float from the *zapper's* avatar (a likely
            // hand-raiser), that overlap would be common. Center-
            // anchored so the `⚡ Nsats` chip extends symmetrically
            // and stays within the cell rather than bleeding into the
            // neighbouring column. Same outer-Box sibling placement
            // and layout-stability reasoning as the reaction overlay.
            if (zaps.isNotEmpty()) {
                SpeakerZapOverlay(
                    zaps = zaps,
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = ringPadding - 6.dp),
                )
            }
        }
        UsernameDisplay(
            baseUser = user,
            weight = Modifier.fillMaxWidth().padding(top = 4.dp),
            textColor = selfTint,
            textAlign = TextAlign.Center,
            accountViewModel = accountViewModel,
        )
    }
}

@Composable
private fun AvatarAndBadges(
    member: RoomMember,
    avatarSize: Dp,
    accountViewModel: AccountViewModel,
    avatarModifier: Modifier,
    onClick: ((String) -> Unit)?,
    onLongClick: ((String) -> Unit)?,
    isConnecting: Boolean,
    showMicBadge: Boolean,
    isSpeaking: Boolean,
) {
    Box(contentAlignment = Alignment.Center) {
        ClickableUserPicture(
            baseUserHex = member.pubkey,
            size = avatarSize,
            accountViewModel = accountViewModel,
            modifier = avatarModifier,
            onClick = onClick,
            onLongClick = onLongClick,
        )
        if (isConnecting) {
            CircularProgressIndicator(
                modifier = Modifier.size(avatarSize - 8.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        val role = member.role
        if (role == ROLE.HOST || role == ROLE.MODERATOR) {
            RoleBadge(
                role = role,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
        if (member.handRaised) {
            HandRaiseBadge(
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
        // Show the mic badge for any on-stage speaker that has
        // an audio state to surface — currently broadcasting
        // (`publishing=1`) OR mic-muted (`muted=1, publishing=0`).
        // Gating only on `publishing` would hide the muted icon
        // the moment the user mutes, which is exactly when it's
        // supposed to appear.
        if (showMicBadge && (member.publishing || member.muted == true)) {
            MicStateBadge(
                isSpeaking = isSpeaking,
                isMuted = member.muted == true,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * Hand-raise indicator overlaid on the avatar — yellow circle with
 * a hand glyph at the top-right, animated in a subtle vertical
 * bounce so the eye catches it across a busy room. Mirrors
 * nostrnests' `animate-bounce` Tailwind utility on
 * `ParticipantAvatar`.
 */
@Composable
private fun HandRaiseBadge(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "hand-raise-bounce")
    val offsetY by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = -3f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "hand-raise-bounce-y",
        )
    Box(
        modifier =
            modifier
                .offset { IntOffset(2.dp.roundToPx(), ((-2).dp + offsetY.dp).roundToPx()) }
                .size(MAX_BADGE_SIZE)
                .background(MaterialTheme.colorScheme.tertiary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            symbol = MaterialSymbols.PanTool,
            contentDescription = stringRes(Res.string.nest_raise_hand),
            tint = MaterialTheme.colorScheme.onTertiary,
            modifier = Modifier.size(MAX_BADGE_SIZE - 4.dp),
        )
    }
}

/**
 * Role indicator overlaid on the top-left of the avatar — only
 * rendered for HOST and MODERATOR. Mirrors how nostrnests
 * surfaces room ownership/staff at a glance.
 */
@Composable
private fun RoleBadge(
    role: ROLE,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg, symbol, label) =
        when (role) {
            ROLE.HOST -> {
                BadgeStyle(
                    background = scheme.primary,
                    foreground = scheme.onPrimary,
                    symbol = MaterialSymbols.MilitaryTech,
                    label = stringRes(Res.string.nest_role_host),
                )
            }

            ROLE.MODERATOR -> {
                BadgeStyle(
                    background = scheme.secondary,
                    foreground = scheme.onSecondary,
                    symbol = MaterialSymbols.Shield,
                    label = stringRes(Res.string.nest_role_moderator),
                )
            }

            else -> {
                return
            }
        }
    Box(
        modifier =
            modifier
                .offset(x = (-2).dp, y = (-2).dp)
                .size(MAX_BADGE_SIZE)
                .background(bg, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            symbol = symbol,
            contentDescription = label,
            tint = fg,
            modifier = Modifier.size(MAX_BADGE_SIZE - 4.dp),
        )
    }
}

/** Tuple of resolved badge colors + content; trivially destructured by [RoleBadge]. */
private data class BadgeStyle(
    val background: Color,
    val foreground: Color,
    val symbol: MaterialSymbol,
    val label: String,
)

/**
 * Mic-state pill anchored to the bottom-center of an on-stage
 * speaker's avatar. Color encodes whether the mic is currently
 * carrying audio:
 *   - green   — speaker is speaking right now (in `speakingNow`)
 *   - red     — speaker is publishing but their `muted` flag is set
 *   - primary — speaker is publishing but silent (mic open, no audio)
 *
 * Audience members never get this badge (the parent gates render
 * with `showMicBadge`); a speaker who isn't `publishing` doesn't
 * get one either — they're on stage but not on air.
 */
@Composable
private fun MicStateBadge(
    isSpeaking: Boolean,
    isMuted: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) =
        when {
            isSpeaking -> NEST_SPEAKING_COLOR to Color.White
            isMuted -> scheme.error to scheme.onError
            else -> scheme.primary to scheme.onPrimary
        }
    val description =
        when {
            isMuted -> stringRes(Res.string.nest_state_muted)
            isSpeaking -> stringRes(Res.string.nest_state_speaking)
            else -> stringRes(Res.string.nest_state_mic_open)
        }
    Box(
        modifier =
            modifier
                .offset(y = 2.dp)
                .size(MAX_BADGE_SIZE)
                .background(bg, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            symbol = if (isMuted) MaterialSymbols.MicOff else MaterialSymbols.Mic,
            contentDescription = description,
            tint = fg,
            modifier = Modifier.size(MAX_BADGE_SIZE - 4.dp),
        )
    }
}

// Speaking-green is kept as a hardcoded brand-ish color rather than
// pulled from the theme — "green = mic active" is a near-universal
// convention (Spaces, Clubhouse, Discord) and a primary-tinted ring
// would lose that signal in user themes that recolour primary.
private val NEST_SPEAKING_COLOR = Color(0xFF22C55E) // tailwind green-500
