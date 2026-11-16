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
package com.vitorpamplona.amethyst.commons.buzz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzPresenceState
import com.vitorpamplona.quartz.buzz.presence.PresenceStatus
import com.vitorpamplona.quartz.nip01Core.core.HexKey

private val PresenceOnline = Color(0xFF4CAF50)
private val PresenceAway = Color(0xFFFFB300)

/** The latest Buzz presence for [pubkey], mapped to the curated enum, or null when none is known. */
@Composable
fun rememberBuzzPresence(pubkey: HexKey): PresenceStatus? {
    val map by BuzzPresenceState.flow.collectAsStateWithLifecycle()
    return remember(map, pubkey) { map[pubkey]?.let { PresenceStatus.fromWire(it) } }
}

/**
 * A small status dot for a Buzz workspace member — green online, amber away — rendered nowhere for
 * offline/unknown so the UI doesn't imply presence tracking where there is none. Meant to be
 * overlaid on the bottom-end of an avatar inside a [Box] (pass `Modifier.align(...)`).
 */
@Composable
fun PresenceDot(
    pubkey: HexKey,
    modifier: Modifier = Modifier,
    size: Dp = 11.dp,
    ringColor: Color = Color.Unspecified,
) {
    val color =
        when (rememberBuzzPresence(pubkey)) {
            PresenceStatus.ONLINE -> PresenceOnline
            PresenceStatus.AWAY -> PresenceAway
            // Offline / unknown: render nothing rather than a grey dot implying we track them.
            else -> return
        }
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .then(if (ringColor != Color.Unspecified) Modifier.border(2.dp, ringColor, CircleShape) else Modifier)
                .background(color, CircleShape),
    )
}
