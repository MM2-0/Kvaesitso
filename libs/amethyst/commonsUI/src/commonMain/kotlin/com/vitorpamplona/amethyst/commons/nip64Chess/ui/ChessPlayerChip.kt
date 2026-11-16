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
package com.vitorpamplona.amethyst.commons.nip64Chess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.chess_color_black
import com.vitorpamplona.amethyst.commons.resources.chess_color_white
import com.vitorpamplona.amethyst.commons.resources.chess_versus
import com.vitorpamplona.amethyst.commons.ui.components.UserAvatar
import com.vitorpamplona.amethyst.commons.ui.stringRes
import androidx.compose.ui.graphics.Color as ComposeColor

private val ActiveBorderColor = ComposeColor(0xFF4CAF50)
private val AvatarOuterSize = 36.dp
private val AvatarInnerSize = 32.dp // 4dp inset for border

/**
 * Single player chip showing avatar and name.
 *
 * @param displayName Player's display name
 * @param userHex Player's public key hex (for Robohash fallback)
 * @param avatarUrl Optional URL for the player's profile picture
 * @param isActive Whether this player's turn is active (shows green border)
 * @param mirrored If true, reverses the layout so name appears left of avatar (for right-side player)
 * @param modifier Additional modifiers
 */
@Composable
fun ChessPlayerChip(
    displayName: String,
    userHex: String,
    avatarUrl: String?,
    isActive: Boolean = false,
    mirrored: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val avatar =
        @Composable {
            Box(
                modifier =
                    if (isActive) {
                        Modifier
                            .size(AvatarOuterSize)
                            .border(2.dp, ActiveBorderColor, CircleShape)
                            .clip(CircleShape)
                    } else {
                        Modifier
                            .size(AvatarOuterSize)
                            .clip(CircleShape)
                    },
            ) {
                UserAvatar(
                    userHex = userHex,
                    pictureUrl = avatarUrl,
                    size = AvatarInnerSize,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

    val name =
        @Composable {
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

    Row(
        modifier =
            modifier
                .alpha(if (isActive) 1f else 0.6f)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (mirrored) {
            name()
            avatar()
        } else {
            avatar()
            name()
        }
    }
}

/**
 * Full "White vs Black" header for the game board.
 *
 * Layout:
 * ```
 * [WhiteAvatar] WhiteName    vs    BlackName [BlackAvatar]
 *               White                 Black
 * ```
 *
 * Active player (based on isWhiteTurn) gets a green avatar border;
 * inactive player is slightly dimmed.
 *
 * @param whiteName Display name for the white player
 * @param whiteHex Public key hex for white player (Robohash fallback)
 * @param whiteAvatarUrl Optional avatar URL for white player
 * @param blackName Display name for the black player
 * @param blackHex Public key hex for black player (Robohash fallback)
 * @param blackAvatarUrl Optional avatar URL for black player
 * @param isWhiteTurn Whether it is currently white's turn
 * @param modifier Additional modifiers
 */
@Composable
fun ChessPlayerVsHeader(
    whiteName: String,
    whiteHex: String,
    whiteAvatarUrl: String?,
    blackName: String,
    blackHex: String,
    blackAvatarUrl: String?,
    isWhiteTurn: Boolean,
    onWhiteClick: (() -> Unit)? = null,
    onBlackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        // White player (left side)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f),
        ) {
            ChessPlayerChip(
                displayName = whiteName,
                userHex = whiteHex,
                avatarUrl = whiteAvatarUrl,
                isActive = isWhiteTurn,
                onClick = onWhiteClick,
            )
            Text(
                text = stringRes(Res.string.chess_color_white),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = stringRes(Res.string.chess_versus),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Black player (right side) — mirrored so avatar is on the right
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f),
        ) {
            ChessPlayerChip(
                displayName = blackName,
                userHex = blackHex,
                avatarUrl = blackAvatarUrl,
                isActive = !isWhiteTurn,
                mirrored = true,
                onClick = onBlackClick,
            )
            Text(
                text = stringRes(Res.string.chess_color_black),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Two overlapping circular avatars for compact game list cards.
 *
 * The second avatar is offset to overlap the first by 8dp.
 *
 * @param avatar1Hex Public key hex for first player (Robohash fallback)
 * @param avatar2Hex Public key hex for second player (Robohash fallback)
 * @param avatar1Url Optional avatar URL for first player
 * @param avatar2Url Optional avatar URL for second player
 * @param size Diameter of each avatar circle
 * @param modifier Additional modifiers
 */
@Composable
fun OverlappingAvatars(
    avatar1Hex: String,
    avatar2Hex: String,
    avatar1Url: String?,
    avatar2Url: String?,
    size: Dp = 28.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(width = size + size - 8.dp, height = size),
    ) {
        UserAvatar(
            userHex = avatar1Hex,
            pictureUrl = avatar1Url,
            size = size,
        )

        Box(
            modifier = Modifier.offset(x = size - 8.dp),
        ) {
            // Thin border to visually separate overlapping avatars
            Box(
                modifier =
                    Modifier
                        .size(size)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
            )
            UserAvatar(
                userHex = avatar2Hex,
                pictureUrl = avatar2Url,
                size = size,
            )
        }
    }
}
