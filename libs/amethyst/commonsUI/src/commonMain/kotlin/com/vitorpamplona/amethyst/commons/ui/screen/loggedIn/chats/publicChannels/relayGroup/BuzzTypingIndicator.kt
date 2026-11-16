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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzTypingState
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserInfo
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.buzz_typing_many
import com.vitorpamplona.amethyst.commons.resources.buzz_typing_one
import com.vitorpamplona.amethyst.commons.resources.buzz_typing_two
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay

/**
 * A live "… is typing" row for a Buzz workspace channel, driven by the ephemeral
 * kind-20002 heartbeats collected into [BuzzTypingState]. Placed just above the composer,
 * it slides in when someone starts typing and out when they stop (heartbeats age out of
 * the freshness window). Own typing is filtered by [myPubkey].
 *
 * The three animated dots are a small modern flourish; the wording collapses to a count
 * once more than two people type at once so the row never overflows.
 */
@Composable
fun BuzzTypingIndicator(
    channelId: String,
    myPubkey: HexKey,
    accountViewModel: AccountViewModel,
) {
    val typingByChannel by BuzzTypingState.flow.collectAsStateWithLifecycle()

    // Re-evaluate on a light timer so typists fade out when their heartbeats go stale,
    // but only while this channel actually has any — an idle channel never wakes the loop.
    var nowSecs by remember { mutableLongStateOf(TimeUtils.now()) }
    val raw = typingByChannel[channelId]
    LaunchedEffect(channelId, raw) {
        if (raw.isNullOrEmpty()) return@LaunchedEffect
        while (true) {
            nowSecs = TimeUtils.now()
            if (raw.values.none { nowSecs - it <= BuzzTypingState.TYPING_STALE_SECS }) break
            delay(2000L)
        }
    }

    val typers =
        remember(raw, myPubkey, nowSecs) {
            (raw ?: emptyMap())
                .filterKeys { it != myPubkey }
                .filterValues { nowSecs - it <= BuzzTypingState.TYPING_STALE_SECS }
                .keys
                .sorted()
        }

    AnimatedVisibility(
        visible = typers.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TypingDots()
            Spacer(Modifier.width(8.dp))
            Text(
                text = typingLabel(typers, accountViewModel),
                style = MaterialTheme.typography.labelSmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.placeholderText,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun typingLabel(
    typers: List<HexKey>,
    accountViewModel: AccountViewModel,
): String =
    when (typers.size) {
        1 -> stringRes(Res.string.buzz_typing_one, rememberTypistName(typers[0], accountViewModel))
        2 ->
            stringRes(
                Res.string.buzz_typing_two,
                rememberTypistName(typers[0], accountViewModel),
                rememberTypistName(typers[1], accountViewModel),
            )
        else -> stringRes(Res.string.buzz_typing_many)
    }

/** Three dots that bounce in a staggered wave — a lightweight, always-on micro-animation. */
@Composable
private fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "buzz-typing")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = 900, delayMillis = index * 150),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "dot-$index",
            )
            Spacer(
                modifier =
                    Modifier
                        .size(5.dp)
                        .graphicsLayer {
                            translationY = -3f * phase
                            val s = 0.7f + 0.3f * phase
                            scaleX = s
                            scaleY = s
                        }.alpha(0.4f + 0.6f * phase)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

/** Resolves [hex] to its best display name, reactively, falling back to a short hex. */
@Composable
private fun rememberTypistName(
    hex: HexKey,
    accountViewModel: AccountViewModel,
): String {
    val user = remember(hex) { LocalCache.checkGetOrCreateUser(hex) } ?: return remember(hex) { hex.take(8) }
    val info by observeUserInfo(user, accountViewModel)
    return info?.info?.bestName() ?: remember(user) { user.pubkeyDisplayHex() }
}
