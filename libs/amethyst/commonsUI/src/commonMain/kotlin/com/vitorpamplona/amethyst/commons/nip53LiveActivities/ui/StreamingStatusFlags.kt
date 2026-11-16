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
package com.vitorpamplona.amethyst.commons.nip53LiveActivities.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.live_stream_ended_tag
import com.vitorpamplona.amethyst.commons.resources.live_stream_live_tag
import com.vitorpamplona.amethyst.commons.resources.live_stream_offline_tag
import com.vitorpamplona.amethyst.commons.resources.live_stream_planned_tag
import com.vitorpamplona.amethyst.commons.resources.live_stream_private_tag
import com.vitorpamplona.amethyst.commons.ui.note.DateTimeStyle
import com.vitorpamplona.amethyst.commons.ui.note.formatDateTime
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.SmallBorder
import com.vitorpamplona.amethyst.commons.ui.theme.liveStreamTag
import com.vitorpamplona.quartz.utils.TimeUtils

@Composable
fun LiveFlag() {
    Text(
        text = stringRes(id = Res.string.live_stream_live_tag),
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        modifier =
            remember {
                Modifier
                    .clip(SmallBorder)
                    .background(Color.Red)
                    .padding(horizontal = 5.dp)
            },
    )
}

@Composable
fun EndedFlag() {
    Text(
        text = stringRes(id = Res.string.live_stream_ended_tag),
        color = Color.White,
        fontWeight = FontWeight.Bold,
        modifier =
            remember {
                Modifier
                    .clip(SmallBorder)
                    .background(Color.Black)
                    .padding(horizontal = 5.dp)
            },
    )
}

@Composable
fun PrivateFlag() {
    Text(
        text = stringRes(id = Res.string.live_stream_private_tag),
        color = Color.White,
        fontWeight = FontWeight.Bold,
        modifier =
            remember {
                Modifier
                    .clip(SmallBorder)
                    .background(Color.Black)
                    .padding(horizontal = 5.dp)
            },
    )
}

@Composable
fun OfflineFlag() {
    Text(
        text = stringRes(id = Res.string.live_stream_offline_tag),
        color = Color.White,
        fontWeight = FontWeight.Bold,
        modifier =
            remember {
                Modifier
                    .clip(SmallBorder)
                    .background(Color.Black)
                    .padding(horizontal = 5.dp)
            },
    )
}

@Composable
fun ScheduledFlag(starts: Long?) {
    // Remembered: this sits on feed cards, and building a DateFormat loads locale data each time.
    val startsIn =
        remember(starts) {
            starts?.let {
                if (it > TimeUtils.now()) {
                    formatDateTime(starts * 1000, DateTimeStyle.SHORT, DateTimeStyle.SHORT)
                } else {
                    null
                }
            }
        }

    Text(
        text = startsIn ?: stringRes(id = Res.string.live_stream_planned_tag),
        color = Color.White,
        fontWeight = FontWeight.Bold,
        modifier = liveStreamTag,
    )
}
